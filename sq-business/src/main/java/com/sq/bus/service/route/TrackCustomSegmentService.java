package com.sq.bus.service.route;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.sq.bus.domain.BizTrack;
import com.sq.bus.domain.BizTrackPoint;
import com.sq.bus.service.IBizTrackPointService;
import com.sq.bus.service.IBizTrackService;
import com.sq.bus.utils.CoordTransformUtils;
import com.sq.bus.utils.GeoDistanceUtils;
import com.sq.bus.utils.TravelModeInferUtils;
import com.sq.common.exception.ServiceException;
import com.sq.common.utils.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 自定义增补路段：支持地点 / 现有轨迹点互连；可选邻接连接。
 * 默认不自动贴「上一照片点↔新段」；仅当 linkPrev/linkNext 为 true 或起终点引用已有点时写入连接折线。
 */
@Service
public class TrackCustomSegmentService {

    @Autowired
    private IBizTrackService trackService;

    @Autowired
    private IBizTrackPointService trackPointService;

    @Autowired
    private AmapDirectionService amapDirectionService;

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> addCustomSegment(Long trackId, Map<String, Object> body) {
        BizTrack track = trackService.getById(trackId);
        if (track == null || track.getDeleted() != null && track.getDeleted() == 1) {
            throw new ServiceException("轨迹不存在或已删除");
        }
        String fromName = str(body.get("fromName"));
        String toName = str(body.get("toName"));
        Double fromLat = toDouble(body.get("fromLat"));
        Double fromLng = toDouble(body.get("fromLng"));
        Double toLat = toDouble(body.get("toLat"));
        Double toLng = toDouble(body.get("toLng"));
        String travelMode = str(body.get("travelMode"));
        String routePath = str(body.get("routePath"));
        String coords = str(body.get("coords"));
        boolean append = Boolean.TRUE.equals(body.get("append"))
                || "true".equalsIgnoreCase(str(body.get("append")));
        Long afterPointId = toLong(body.get("afterPointId"));
        Long fromPointId = toLong(body.get("fromPointId"));
        Long toPointId = toLong(body.get("toPointId"));
        boolean linkPrev = Boolean.TRUE.equals(body.get("linkPrev"))
                || "true".equalsIgnoreCase(str(body.get("linkPrev")));
        boolean linkNext = Boolean.TRUE.equals(body.get("linkNext"))
                || "true".equalsIgnoreCase(str(body.get("linkNext")));

        if (StringUtils.isEmpty(travelMode)) {
            throw new ServiceException("请选择出行方式");
        }
        travelMode = travelMode.trim().toLowerCase(Locale.ROOT);

        List<BizTrackPoint> existing = trackPointService.list(new LambdaQueryWrapper<BizTrackPoint>()
                .eq(BizTrackPoint::getTrackId, trackId)
                .orderByAsc(BizTrackPoint::getSequence)
                .orderByAsc(BizTrackPoint::getPointId));
        if (existing == null) {
            existing = new ArrayList<BizTrackPoint>();
        }

        BizTrackPoint reuseFrom = findPoint(existing, fromPointId);
        BizTrackPoint reuseTo = findPoint(existing, toPointId);
        if (fromPointId != null && reuseFrom == null) {
            throw new ServiceException("起点轨迹点不存在");
        }
        if (toPointId != null && reuseTo == null) {
            throw new ServiceException("终点轨迹点不存在");
        }
        if (reuseFrom != null && reuseTo != null && reuseFrom.getPointId().equals(reuseTo.getPointId())) {
            throw new ServiceException("起终点不能是同一个轨迹点");
        }

        // 复用已有点：坐标以库为准（WGS84）
        double fromLatWgs;
        double fromLngWgs;
        double toLatWgs;
        double toLngWgs;
        if (reuseFrom != null) {
            fromLatWgs = reuseFrom.getLatitude().doubleValue();
            fromLngWgs = reuseFrom.getLongitude().doubleValue();
            if (StringUtils.isEmpty(fromName)) {
                fromName = reuseFrom.getDescription();
            }
        } else {
            if (fromLat == null || fromLng == null) {
                throw new ServiceException("起点坐标不能为空");
            }
            double[] wgs = toWgs(fromLat, fromLng, coords);
            fromLatWgs = wgs[1];
            fromLngWgs = wgs[0];
        }
        if (reuseTo != null) {
            toLatWgs = reuseTo.getLatitude().doubleValue();
            toLngWgs = reuseTo.getLongitude().doubleValue();
            if (StringUtils.isEmpty(toName)) {
                toName = reuseTo.getDescription();
            }
        } else {
            if (toLat == null || toLng == null) {
                throw new ServiceException("终点坐标不能为空");
            }
            double[] wgs = toWgs(toLat, toLng, coords);
            toLatWgs = wgs[1];
            toLngWgs = wgs[0];
        }

        if (StringUtils.isEmpty(routePath)) {
            TrackRoutePlanResult planned = amapDirectionService.plan(
                    fromLatWgs, fromLngWgs, toLatWgs, toLngWgs, travelMode);
            if (!planned.hasPath()) {
                throw new ServiceException(planned.getMessage() == null ? "未能规划路线" : planned.getMessage());
            }
            routePath = amapDirectionService.toRoutePathJson(planned.getPath());
        }

        Date now = new Date();
        List<BizTrackPoint> merged = new ArrayList<BizTrackPoint>(existing);
        BizTrackPoint fromPoint;
        BizTrackPoint toPoint;
        int fromIdx;
        int toIdx;

        if (reuseFrom != null && reuseTo != null) {
            // 两端都是已有点：把终点挪到起点之后，写入连接折线
            fromIdx = indexOf(merged, reuseFrom.getPointId());
            toIdx = indexOf(merged, reuseTo.getPointId());
            if (fromIdx < 0 || toIdx < 0) {
                throw new ServiceException("起终点轨迹点不存在");
            }
            if (toIdx != fromIdx + 1) {
                BizTrackPoint moved = merged.remove(toIdx);
                if (toIdx < fromIdx) {
                    fromIdx--;
                }
                merged.add(fromIdx + 1, moved);
            }
            fromPoint = merged.get(fromIdx);
            toPoint = merged.get(fromIdx + 1);
            fromPoint.setTravelMode(travelMode);
            fromPoint.setRoutePath(routePath);
            toIdx = fromIdx + 1;
        } else if (reuseFrom != null) {
            fromIdx = indexOf(merged, reuseFrom.getPointId());
            if (fromIdx < 0) {
                throw new ServiceException("起点轨迹点不存在");
            }
            toPoint = newWaypoint(trackId, toLatWgs, toLngWgs, toName, now, null, null);
            merged.add(fromIdx + 1, toPoint);
            fromPoint = merged.get(fromIdx);
            fromPoint.setTravelMode(travelMode);
            fromPoint.setRoutePath(routePath);
            toIdx = fromIdx + 1;
        } else if (reuseTo != null) {
            toIdx = indexOf(merged, reuseTo.getPointId());
            if (toIdx < 0) {
                throw new ServiceException("终点轨迹点不存在");
            }
            fromPoint = newWaypoint(trackId, fromLatWgs, fromLngWgs, fromName, now, travelMode, routePath);
            merged.add(toIdx, fromPoint);
            toPoint = merged.get(toIdx + 1);
            fromIdx = toIdx;
            toIdx = toIdx + 1;
        } else {
            int insertAt;
            if (append || merged.isEmpty()) {
                insertAt = merged.size();
            } else if (afterPointId == null) {
                insertAt = 0;
            } else {
                insertAt = -1;
                for (int i = 0; i < merged.size(); i++) {
                    if (afterPointId.equals(merged.get(i).getPointId())) {
                        insertAt = i + 1;
                        break;
                    }
                }
                if (insertAt < 0) {
                    throw new ServiceException("插入位置点位不存在");
                }
            }
            fromPoint = newWaypoint(trackId, fromLatWgs, fromLngWgs, fromName, now, travelMode, routePath);
            toPoint = newWaypoint(trackId, toLatWgs, toLngWgs, toName, now, null, null);
            merged.add(insertAt, fromPoint);
            merged.add(insertAt + 1, toPoint);
            fromIdx = insertAt;
            toIdx = insertAt + 1;
        }

        int seq = 1;
        for (BizTrackPoint p : merged) {
            p.setSequence(seq++);
        }

        // 持久化：新点 save，已有点 update（含序号）
        for (BizTrackPoint p : merged) {
            if (p.getPointId() == null) {
                trackPointService.save(p);
            } else {
                trackPointService.updateById(p);
            }
        }
        // 确保 from 的折线/方式写入（updateById 对非 null 字段有效）
        fromPoint.setTravelMode(travelMode);
        fromPoint.setRoutePath(routePath);
        trackPointService.updateById(fromPoint);

        // 可选：连接插入段与前后邻点
        if (linkPrev && fromIdx > 0) {
            BizTrackPoint prev = merged.get(fromIdx - 1);
            linkSegment(prev, fromPoint);
            trackPointService.updateById(prev);
        } else if (fromIdx > 0 && reuseFrom == null && reuseTo == null) {
            // 全新两点插入且未勾选连接上一：断开前一点旧折线，避免误指向新段
            clearRoutePath(merged.get(fromIdx - 1).getPointId());
        }
        if (linkNext && toIdx + 1 < merged.size()) {
            BizTrackPoint next = merged.get(toIdx + 1);
            linkSegment(toPoint, next);
            trackPointService.updateById(toPoint);
        }

        track.setPointCount(merged.size());
        track.setUpdateTime(new Date());
        trackService.updateById(track);

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("fromPointId", fromPoint.getPointId());
        result.put("toPointId", toPoint.getPointId());
        result.put("pointCount", merged.size());
        result.put("fromIdx", fromIdx);
        result.put("toIdx", toIdx);
        return result;
    }

    private BizTrackPoint newWaypoint(Long trackId, double latWgs, double lngWgs, String name,
                                      Date now, String travelMode, String routePath) {
        BizTrackPoint p = new BizTrackPoint();
        p.setTrackId(trackId);
        p.setPhotoId(null);
        p.setLatitude(BigDecimal.valueOf(latWgs));
        p.setLongitude(BigDecimal.valueOf(lngWgs));
        p.setPointTime(now);
        p.setDescription(StringUtils.isEmpty(name) ? "自定义途经点" : name.trim());
        if (StringUtils.isNotEmpty(travelMode)) {
            p.setTravelMode(travelMode);
        }
        if (StringUtils.isNotEmpty(routePath)) {
            p.setRoutePath(routePath);
        }
        return p;
    }

    private void linkSegment(BizTrackPoint from, BizTrackPoint to) {
        if (from.getLatitude() == null || from.getLongitude() == null
                || to.getLatitude() == null || to.getLongitude() == null) {
            return;
        }
        double km = GeoDistanceUtils.haversineKm(
                from.getLatitude().doubleValue(), from.getLongitude().doubleValue(),
                to.getLatitude().doubleValue(), to.getLongitude().doubleValue());
        String mode = from.getTravelMode();
        if (StringUtils.isEmpty(mode) || TravelModeInferUtils.isUnstableAutoMode(mode, km)) {
            mode = TravelModeInferUtils.infer(km, null);
            from.setTravelMode(mode);
        }
        if (TravelModeInferUtils.isTinyGap(km)) {
            double[] a = CoordTransformUtils.wgs84ToGcj02(
                    from.getLongitude().doubleValue(), from.getLatitude().doubleValue());
            double[] b = CoordTransformUtils.wgs84ToGcj02(
                    to.getLongitude().doubleValue(), to.getLatitude().doubleValue());
            List<double[]> path = new ArrayList<double[]>(2);
            path.add(new double[]{a[1], a[0]});
            path.add(new double[]{b[1], b[0]});
            from.setRoutePath(amapDirectionService.toRoutePathJson(path));
            return;
        }
        TrackRoutePlanResult planned = amapDirectionService.plan(
                from.getLatitude().doubleValue(), from.getLongitude().doubleValue(),
                to.getLatitude().doubleValue(), to.getLongitude().doubleValue(),
                mode);
        if (planned.hasPath()) {
            from.setRoutePath(amapDirectionService.toRoutePathJson(planned.getPath()));
        }
    }

    private void clearRoutePath(Long pointId) {
        if (pointId == null) {
            return;
        }
        trackPointService.update(new LambdaUpdateWrapper<BizTrackPoint>()
                .eq(BizTrackPoint::getPointId, pointId)
                .set(BizTrackPoint::getRoutePath, null));
    }

    private static BizTrackPoint findPoint(List<BizTrackPoint> list, Long pointId) {
        if (pointId == null || list == null) {
            return null;
        }
        for (BizTrackPoint p : list) {
            if (pointId.equals(p.getPointId())) {
                return p;
            }
        }
        return null;
    }

    private static int indexOf(List<BizTrackPoint> list, Long pointId) {
        if (pointId == null || list == null) {
            return -1;
        }
        for (int i = 0; i < list.size(); i++) {
            if (pointId.equals(list.get(i).getPointId())) {
                return i;
            }
        }
        return -1;
    }

    /** @return [lngWgs, latWgs] */
    private static double[] toWgs(double lat, double lng, String coords) {
        boolean gcj = coords == null || coords.isEmpty() || "gcj02".equalsIgnoreCase(coords);
        if (gcj) {
            return CoordTransformUtils.gcj02ToWgs84(lng, lat);
        }
        return new double[]{lng, lat};
    }

    private String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private Double toDouble(Object v) {
        if (v == null) {
            return null;
        }
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }

    private Long toLong(Object v) {
        if (v == null || "".equals(String.valueOf(v).trim()) || "null".equalsIgnoreCase(String.valueOf(v))) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }
}
