package com.sq.bus.service.route;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.sq.bus.domain.BizTrack;
import com.sq.bus.domain.BizTrackPoint;
import com.sq.bus.service.IBizTrackPointService;
import com.sq.bus.service.IBizTrackService;
import com.sq.bus.utils.CoordTransformUtils;
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
 * 按车次经停插入多站途经点，站间写入贴轨折线。
 */
@Service
public class TrainSegmentService {

    @Autowired
    private IBizTrackService trackService;

    @Autowired
    private IBizTrackPointService trackPointService;

    @Autowired
    private TrainRoutePlanService trainRoutePlanService;

    @Autowired
    private OsmRailwayRouteService osmRailwayRouteService;

    @Autowired
    private AmapDirectionService amapDirectionService;

    @Transactional(rollbackFor = Exception.class)
    @SuppressWarnings("unchecked")
    public Map<String, Object> addTrainSegment(Long trackId, Map<String, Object> body) {
        BizTrack track = trackService.getById(trackId);
        if (track == null || track.getDeleted() != null && track.getDeleted() == 1) {
            throw new ServiceException("轨迹不存在或已删除");
        }

        Map<String, Object> planned = trainRoutePlanService.plan(body);
        List<Map<String, Object>> stopViews = (List<Map<String, Object>>) planned.get("stops");
        if (stopViews == null || stopViews.size() < 2) {
            throw new ServiceException("经停站无效");
        }

        String trainNo = str(planned.get("trainNo"));
        String travelMode = str(planned.get("travelMode"));
        if (StringUtils.isEmpty(travelMode)) {
            travelMode = TrainRoutePlanService.inferMode(trainNo);
        }
        travelMode = travelMode.toLowerCase(Locale.ROOT);
        String baseDesc = str(planned.get("description"));

        boolean append = Boolean.TRUE.equals(body.get("append"))
                || "true".equalsIgnoreCase(str(body.get("append")));
        Long afterPointId = toLong(body.get("afterPointId"));

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
        List<BizTrackPoint> newPoints = new ArrayList<BizTrackPoint>();
        for (int i = 0; i < stopViews.size(); i++) {
            Map<String, Object> stop = stopViews.get(i);
            double latWgs = toDouble(stop.get("wgsLat"));
            double lngWgs = toDouble(stop.get("lngWgs"));
            if (Double.isNaN(latWgs) || Double.isNaN(lngWgs)) {
                throw new ServiceException("车站坐标缺失：" + stop.get("name"));
            }
            BizTrackPoint p = new BizTrackPoint();
            p.setTrackId(trackId);
            p.setPhotoId(null);
            p.setLatitude(BigDecimal.valueOf(latWgs));
            p.setLongitude(BigDecimal.valueOf(lngWgs));
            p.setPointTime(now);
            p.setDescription(stopDescription(baseDesc, stop, i, stopViews.size()));
            if (i < stopViews.size() - 1) {
                p.setTravelMode(travelMode);
            }
            newPoints.add(p);
        }

        // 站间贴轨
        for (int i = 0; i < newPoints.size() - 1; i++) {
            BizTrackPoint from = newPoints.get(i);
            BizTrackPoint to = newPoints.get(i + 1);
            from.setRoutePath(planSegmentJson(from, to, travelMode));
        }

        List<BizTrackPoint> merged = new ArrayList<BizTrackPoint>(existing.size() + newPoints.size());
        merged.addAll(existing.subList(0, insertAt));
        merged.addAll(newPoints);
        merged.addAll(existing.subList(insertAt, existing.size()));

        int seq = 1;
        for (BizTrackPoint p : merged) {
            p.setSequence(seq++);
        }
        for (BizTrackPoint p : newPoints) {
            trackPointService.save(p);
        }
        for (BizTrackPoint p : existing) {
            trackPointService.updateById(p);
        }

        // 不自动衔接前后照片点；清掉前一点旧折线（须显式 set null）
        if (insertAt > 0) {
            BizTrackPoint prev = merged.get(insertAt - 1);
            if (prev.getPointId() != null) {
                trackPointService.update(new LambdaUpdateWrapper<BizTrackPoint>()
                        .eq(BizTrackPoint::getPointId, prev.getPointId())
                        .set(BizTrackPoint::getRoutePath, null));
                prev.setRoutePath(null);
            }
        }

        track.setPointCount(merged.size());
        track.setUpdateTime(new Date());
        trackService.updateById(track);

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("pointCount", merged.size());
        result.put("insertedStops", newPoints.size());
        result.put("insertAt", insertAt);
        result.put("trainNo", trainNo);
        result.put("description", baseDesc);
        result.put("distanceMeters", planned.get("distanceMeters"));
        return result;
    }

    private String planSegmentJson(BizTrackPoint from, BizTrackPoint to, String mode) {
        TrackRoutePlanResult seg = osmRailwayRouteService.route(
                from.getLatitude().doubleValue(), from.getLongitude().doubleValue(),
                to.getLatitude().doubleValue(), to.getLongitude().doubleValue(),
                mode);
        if (!seg.hasPath()) {
            seg = amapDirectionService.plan(
                    from.getLatitude().doubleValue(), from.getLongitude().doubleValue(),
                    to.getLatitude().doubleValue(), to.getLongitude().doubleValue(),
                    mode);
        }
        if (!seg.hasPath()) {
            double[] a = CoordTransformUtils.wgs84ToGcj02(
                    from.getLongitude().doubleValue(), from.getLatitude().doubleValue());
            double[] b = CoordTransformUtils.wgs84ToGcj02(
                    to.getLongitude().doubleValue(), to.getLatitude().doubleValue());
            List<double[]> path = new ArrayList<double[]>(2);
            path.add(new double[]{a[1], a[0]});
            path.add(new double[]{b[1], b[0]});
            return amapDirectionService.toRoutePathJson(path);
        }
        return amapDirectionService.toRoutePathJson(seg.getPath());
    }

    private String stopDescription(String baseDesc, Map<String, Object> stop, int index, int total) {
        String name = str(stop.get("name"));
        String arrive = str(stop.get("arriveTime"));
        String depart = str(stop.get("departTime"));
        StringBuilder sb = new StringBuilder();
        if (index == 0 && StringUtils.isNotEmpty(baseDesc)) {
            sb.append(baseDesc);
            if (StringUtils.isNotEmpty(name)) {
                sb.append(" · ").append(name);
            }
        } else {
            sb.append(StringUtils.isEmpty(name) ? ("车站" + (index + 1)) : name);
        }
        if (StringUtils.isNotEmpty(arrive) || StringUtils.isNotEmpty(depart)) {
            sb.append(' ');
            if (StringUtils.isNotEmpty(arrive) && StringUtils.isNotEmpty(depart)
                    && !arrive.equals(depart)) {
                sb.append(arrive).append('/').append(depart);
            } else if (StringUtils.isNotEmpty(depart)) {
                sb.append(depart);
            } else {
                sb.append(arrive);
            }
        }
        if (index == total - 1 && total > 2) {
            sb.append("（终）");
        }
        String out = sb.toString();
        return out.length() > 500 ? out.substring(0, 500) : out;
    }

    private String str(Object v) {
        return v == null ? null : String.valueOf(v).trim();
    }

    private Double toDouble(Object v) {
        if (v == null) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (Exception e) {
            return Double.NaN;
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
