package com.sq.bus.service.route;

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
 * 自定义增补路段：插入无照片途经点并写入高德真实折线。
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

        if (fromLat == null || fromLng == null || toLat == null || toLng == null) {
            throw new ServiceException("起终点坐标不能为空");
        }
        if (StringUtils.isEmpty(travelMode)) {
            throw new ServiceException("请选择出行方式");
        }
        travelMode = travelMode.trim().toLowerCase(Locale.ROOT);

        boolean gcj = coords == null || coords.isEmpty() || "gcj02".equalsIgnoreCase(coords);
        double fromLatWgs;
        double fromLngWgs;
        double toLatWgs;
        double toLngWgs;
        if (gcj) {
            double[] fromWgs = CoordTransformUtils.gcj02ToWgs84(fromLng, fromLat);
            double[] toWgs = CoordTransformUtils.gcj02ToWgs84(toLng, toLat);
            fromLngWgs = fromWgs[0];
            fromLatWgs = fromWgs[1];
            toLngWgs = toWgs[0];
            toLatWgs = toWgs[1];
        } else {
            fromLatWgs = fromLat;
            fromLngWgs = fromLng;
            toLatWgs = toLat;
            toLngWgs = toLng;
        }

        // routePath 始终按 GCJ 存（与现有路网折线一致）；未传则规划
        if (StringUtils.isEmpty(routePath)) {
            TrackRoutePlanResult planned = amapDirectionService.plan(
                    fromLatWgs, fromLngWgs, toLatWgs, toLngWgs, travelMode);
            if (!planned.hasPath()) {
                throw new ServiceException(planned.getMessage() == null ? "未能规划路线" : planned.getMessage());
            }
            routePath = amapDirectionService.toRoutePathJson(planned.getPath());
        }

        List<BizTrackPoint> existing = trackPointService.list(new LambdaQueryWrapper<BizTrackPoint>()
                .eq(BizTrackPoint::getTrackId, trackId)
                .orderByAsc(BizTrackPoint::getSequence)
                .orderByAsc(BizTrackPoint::getPointId));
        if (existing == null) {
            existing = new ArrayList<BizTrackPoint>();
        }

        int insertAt;
        if (append || existing.isEmpty()) {
            insertAt = existing.size();
        } else if (afterPointId == null) {
            insertAt = 0;
        } else {
            insertAt = -1;
            for (int i = 0; i < existing.size(); i++) {
                if (afterPointId.equals(existing.get(i).getPointId())) {
                    insertAt = i + 1;
                    break;
                }
            }
            if (insertAt < 0) {
                throw new ServiceException("插入位置点位不存在");
            }
        }

        Date now = new Date();
        BizTrackPoint fromPoint = new BizTrackPoint();
        fromPoint.setTrackId(trackId);
        fromPoint.setPhotoId(null);
        fromPoint.setLatitude(BigDecimal.valueOf(fromLatWgs));
        fromPoint.setLongitude(BigDecimal.valueOf(fromLngWgs));
        fromPoint.setPointTime(now);
        fromPoint.setDescription(StringUtils.isEmpty(fromName) ? "自定义起点" : fromName.trim());
        fromPoint.setTravelMode(travelMode);
        fromPoint.setRoutePath(routePath);

        BizTrackPoint toPoint = new BizTrackPoint();
        toPoint.setTrackId(trackId);
        toPoint.setPhotoId(null);
        toPoint.setLatitude(BigDecimal.valueOf(toLatWgs));
        toPoint.setLongitude(BigDecimal.valueOf(toLngWgs));
        toPoint.setPointTime(now);
        toPoint.setDescription(StringUtils.isEmpty(toName) ? "自定义终点" : toName.trim());

        List<BizTrackPoint> merged = new ArrayList<BizTrackPoint>(existing.size() + 2);
        merged.addAll(existing.subList(0, insertAt));
        merged.add(fromPoint);
        merged.add(toPoint);
        merged.addAll(existing.subList(insertAt, existing.size()));

        // 重排序号并保存新点
        int seq = 1;
        for (BizTrackPoint p : merged) {
            p.setSequence(seq++);
        }
        trackPointService.save(fromPoint);
        trackPointService.save(toPoint);
        for (BizTrackPoint p : existing) {
            trackPointService.updateById(p);
        }

        // 补邻接：prev→from、to→next
        int fromIdx = insertAt;
        int toIdx = insertAt + 1;
        if (fromIdx > 0) {
            BizTrackPoint prev = merged.get(fromIdx - 1);
            linkSegment(prev, fromPoint);
            trackPointService.updateById(prev);
        }
        if (toIdx + 1 < merged.size()) {
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
        result.put("insertAt", insertAt);
        return result;
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
