package com.sq.bus.service.route;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sq.bus.domain.BizTrack;
import com.sq.bus.domain.BizTrackPoint;
import com.sq.bus.service.IBizTrackPointService;
import com.sq.bus.service.IBizTrackService;
import com.sq.bus.utils.CoordTransformUtils;
import com.sq.bus.utils.GeoDistanceUtils;
import com.sq.bus.utils.TravelModeInferUtils;
import com.sq.common.exception.ServiceException;
import com.sq.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 相邻照片 GPS 逐段调用高德导航贴路。
 * <p>不做全程折线切片：来回走的轨迹用最近点切片会串段，导致线路乱套。
 */
@Service
public class TrackRouteResolveService {

    private static final Logger log = LoggerFactory.getLogger(TrackRouteResolveService.class);

    /** 规划折线端点与照片点偏差超过该值（公里）则判定无效 */
    private static final double ENDPOINT_TOLERANCE_KM = 0.12;

    @Autowired
    private IBizTrackService trackService;

    @Autowired
    private IBizTrackPointService trackPointService;

    @Autowired
    private AmapDirectionService amapDirectionService;

    public Map<String, Object> resolveTrackRoutes(Long trackId, boolean force, String strategy) {
        BizTrack track = trackService.getById(trackId);
        if (track == null || track.getDeleted() != null && track.getDeleted() == 1) {
            throw new ServiceException("轨迹不存在或已删除");
        }
        List<BizTrackPoint> points = trackPointService.list(new LambdaQueryWrapper<BizTrackPoint>()
                .eq(BizTrackPoint::getTrackId, trackId)
                .orderByAsc(BizTrackPoint::getSequence)
                .orderByAsc(BizTrackPoint::getPointId));
        if (points == null || points.size() < 2) {
            return stats(0, 0, 0, 0);
        }
        return resolvePairwise(track, points, force);
    }

    public Map<String, Object> resolveTrackRoutes(Long trackId, boolean force) {
        return resolveTrackRoutes(trackId, force, "photo");
    }

    private Map<String, Object> resolvePairwise(BizTrack track, List<BizTrackPoint> points, boolean force) {
        int planned = 0;
        int skipped = 0;
        int failed = 0;
        for (int i = 0; i < points.size() - 1; i++) {
            BizTrackPoint from = points.get(i);
            BizTrackPoint to = points.get(i + 1);
            if (from.getLatitude() == null || from.getLongitude() == null
                    || to.getLatitude() == null || to.getLongitude() == null) {
                skipped++;
                continue;
            }

            double km = GeoDistanceUtils.haversineKm(
                    from.getLatitude().doubleValue(), from.getLongitude().doubleValue(),
                    to.getLatitude().doubleValue(), to.getLongitude().doubleValue());

            if (!force && !needsRepair(from, to, km)) {
                skipped++;
                continue;
            }

            Long sec = null;
            if (from.getPointTime() != null && to.getPointTime() != null) {
                sec = Math.max(0L, (to.getPointTime().getTime() - from.getPointTime().getTime()) / 1000L);
            }
            // 只在未设置时推断；地铁/高铁等用户手选方式绝不能被改回步行
            String mode = from.getTravelMode();
            if (StringUtils.isEmpty(mode)) {
                mode = TravelModeInferUtils.infer(km, sec);
            }
            mode = mode.toLowerCase(Locale.ROOT);
            from.setTravelMode(mode);

            // 极近点：直线即可
            if (TravelModeInferUtils.isTinyGap(km)) {
                from.setRoutePath(amapDirectionService.toRoutePathJson(directGcjPath(from, to)));
                trackPointService.updateById(from);
                planned++;
                continue;
            }

            TrackRoutePlanResult result = amapDirectionService.plan(
                    from.getLatitude().doubleValue(), from.getLongitude().doubleValue(),
                    to.getLatitude().doubleValue(), to.getLongitude().doubleValue(),
                    mode);

            List<double[]> path = result.hasPath() ? result.getPath() : null;
            // 规划失败时用直线，保留用户选择的出行方式（不再偷偷改回 walk）
            if (path == null || !endpointsMatch(path, from, to)) {
                path = directGcjPath(from, to);
                failed++;
            }

            // 钳制端点到照片 GCJ，避免导航起点偏离标记
            path = clampEndpoints(path, from, to);
            from.setRoutePath(amapDirectionService.toRoutePathJson(path));
            trackPointService.updateById(from);
            planned++;
        }
        track.setUpdateTime(new Date());
        trackService.updateById(track);
        log.info("pairwise route resolve trackId={} planned={} skipped={} failed={}",
                track.getTrackId(), planned, skipped, failed);
        return stats(planned, skipped, failed, 0);
    }

    private boolean needsRepair(BizTrackPoint from, BizTrackPoint to, double km) {
        if (TravelModeInferUtils.isTinyGap(km)) {
            return false;
        }
        List<double[]> path = parsePath(from.getRoutePath());
        if (path == null || path.size() < 2) {
            return true;
        }
        // 两点重合 / 仅直线两点：需要重新规划（短于 40m 的真实直线除外）
        if (path.size() <= 2) {
            double pathKm = GeoDistanceUtils.haversineKm(path.get(0)[0], path.get(0)[1],
                    path.get(1)[0], path.get(1)[1]);
            return pathKm < 0.001 || km > 0.04;
        }
        // 端点与照片对不上（旧版切片串段）→ 必须重算
        return !endpointsMatch(path, from, to);
    }

    private boolean endpointsMatch(List<double[]> path, BizTrackPoint from, BizTrackPoint to) {
        if (path == null || path.size() < 2) {
            return false;
        }
        double[] a = toGcjLatLng(from);
        double[] b = toGcjLatLng(to);
        double[] p0 = path.get(0);
        double[] p1 = path.get(path.size() - 1);
        double d0 = GeoDistanceUtils.haversineKm(p0[0], p0[1], a[0], a[1]);
        double d1 = GeoDistanceUtils.haversineKm(p1[0], p1[1], b[0], b[1]);
        // 允许方向反了（偶发），取较近端匹配
        double d0r = GeoDistanceUtils.haversineKm(p0[0], p0[1], b[0], b[1]);
        double d1r = GeoDistanceUtils.haversineKm(p1[0], p1[1], a[0], a[1]);
        boolean forward = d0 <= ENDPOINT_TOLERANCE_KM && d1 <= ENDPOINT_TOLERANCE_KM;
        boolean reverse = d0r <= ENDPOINT_TOLERANCE_KM && d1r <= ENDPOINT_TOLERANCE_KM;
        return forward || reverse;
    }

    private List<double[]> clampEndpoints(List<double[]> path, BizTrackPoint from, BizTrackPoint to) {
        double[] a = toGcjLatLng(from);
        double[] b = toGcjLatLng(to);
        if (path == null || path.size() < 2) {
            return directGcjPath(from, to);
        }
        List<double[]> out = new ArrayList<double[]>(path.size());
        // 若反向则先翻转
        double[] p0 = path.get(0);
        double dFwd = GeoDistanceUtils.haversineKm(p0[0], p0[1], a[0], a[1]);
        double dRev = GeoDistanceUtils.haversineKm(p0[0], p0[1], b[0], b[1]);
        if (dRev + 0.01 < dFwd) {
            for (int i = path.size() - 1; i >= 0; i--) {
                out.add(path.get(i));
            }
        } else {
            out.addAll(path);
        }
        out.set(0, a);
        out.set(out.size() - 1, b);
        return out;
    }

    private double[] toGcjLatLng(BizTrackPoint p) {
        double[] gcj = CoordTransformUtils.wgs84ToGcj02(
                p.getLongitude().doubleValue(), p.getLatitude().doubleValue());
        return new double[]{gcj[1], gcj[0]};
    }

    private List<double[]> directGcjPath(BizTrackPoint from, BizTrackPoint to) {
        List<double[]> path = new ArrayList<double[]>(2);
        path.add(toGcjLatLng(from));
        path.add(toGcjLatLng(to));
        return path;
    }

    @SuppressWarnings("unchecked")
    private List<double[]> parsePath(String routePath) {
        if (StringUtils.isEmpty(routePath)) {
            return null;
        }
        try {
            List<Object> raw = JSON.parseArray(routePath, Object.class);
            if (raw == null || raw.size() < 2) {
                return null;
            }
            List<double[]> path = new ArrayList<double[]>();
            for (Object item : raw) {
                if (item instanceof List) {
                    List<?> row = (List<?>) item;
                    if (row.size() >= 2) {
                        path.add(new double[]{
                                Double.parseDouble(String.valueOf(row.get(0))),
                                Double.parseDouble(String.valueOf(row.get(1)))
                        });
                    }
                }
            }
            return path.size() >= 2 ? path : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> stats(int planned, int skipped, int failed, int remaining) {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("planned", planned);
        map.put("skipped", skipped);
        map.put("failed", failed);
        map.put("remaining", remaining);
        return map;
    }
}
