package com.sq.bus.service.route;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.sq.bus.domain.BizPhoto;
import com.sq.bus.domain.BizTrack;
import com.sq.bus.domain.BizTrackPoint;
import com.sq.bus.service.IBizPhotoService;
import com.sq.bus.service.IBizTrackPointService;
import com.sq.bus.service.IBizTrackService;
import com.sq.bus.utils.CoordTransformUtils;
import com.sq.bus.utils.GeoDistanceUtils;
import com.sq.common.exception.ServiceException;
import com.sq.common.utils.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 将照片/视频合并进已有增补路段：
 * 只调整轨迹顺序并拆分 routePath，使折线走增补路网；
 * <b>不修改</b>照片/视频及轨迹点的 GPS 坐标。
 */
@Service
public class TrackSegmentPhotoMergeService {

    @Autowired
    private IBizTrackService trackService;

    @Autowired
    private IBizTrackPointService trackPointService;

    @Autowired
    private IBizPhotoService photoService;

    @Autowired
    private AmapDirectionService amapDirectionService;

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> mergePhotosToSegment(Long trackId, Map<String, Object> body) {
        BizTrack track = trackService.getById(trackId);
        if (track == null || (track.getDeleted() != null && track.getDeleted() == 1)) {
            throw new ServiceException("轨迹不存在或已删除");
        }
        Long fromPointId = toLong(body.get("fromPointId"));
        Long toPointId = toLong(body.get("toPointId"));
        List<Long> photoIds = toLongList(body.get("photoIds"));
        if (fromPointId == null || toPointId == null) {
            throw new ServiceException("请指定路段起终点");
        }
        if (fromPointId.equals(toPointId)) {
            throw new ServiceException("起终点不能相同");
        }
        if (photoIds.isEmpty()) {
            throw new ServiceException("请选择要合并的照片/视频");
        }

        List<BizTrackPoint> existing = trackPointService.list(new LambdaQueryWrapper<BizTrackPoint>()
                .eq(BizTrackPoint::getTrackId, trackId)
                .orderByAsc(BizTrackPoint::getSequence)
                .orderByAsc(BizTrackPoint::getPointId));
        if (existing == null || existing.size() < 2) {
            throw new ServiceException("轨迹点不足");
        }

        BizTrackPoint fromPoint = findPoint(existing, fromPointId);
        BizTrackPoint toPoint = findPoint(existing, toPointId);
        if (fromPoint == null || toPoint == null) {
            throw new ServiceException("路段起终点不存在");
        }
        if (StringUtils.isEmpty(fromPoint.getRoutePath())) {
            throw new ServiceException("该路段还没有折线，请先增补或贴合路网");
        }

        List<double[]> fullPath = parsePath(fromPoint.getRoutePath());
        if (fullPath == null || fullPath.size() < 2) {
            throw new ServiceException("路段折线无效");
        }

        String travelMode = fromPoint.getTravelMode();
        if (StringUtils.isEmpty(travelMode)) {
            travelMode = "metro";
        } else {
            travelMode = travelMode.trim().toLowerCase(Locale.ROOT);
        }

        Map<Long, BizTrackPoint> photoPointByPhotoId = new HashMap<Long, BizTrackPoint>();
        for (BizTrackPoint p : existing) {
            if (p.getPhotoId() != null) {
                photoPointByPhotoId.put(p.getPhotoId(), p);
            }
        }

        List<BizTrackPoint> mergePoints = new ArrayList<BizTrackPoint>();
        Set<Long> seen = new HashSet<Long>();
        for (Long photoId : photoIds) {
            if (photoId == null || !seen.add(photoId)) {
                continue;
            }
            if (fromPoint.getPhotoId() != null && fromPoint.getPhotoId().equals(photoId)) {
                throw new ServiceException("不能合并路段起点本身的照片");
            }
            if (toPoint.getPhotoId() != null && toPoint.getPhotoId().equals(photoId)) {
                throw new ServiceException("不能合并路段终点本身的照片");
            }
            BizTrackPoint tp = photoPointByPhotoId.get(photoId);
            if (tp == null) {
                throw new ServiceException("照片/视频 #" + photoId + " 不在当前轨迹中");
            }
            mergePoints.add(tp);
        }
        if (mergePoints.isEmpty()) {
            throw new ServiceException("请选择要合并的照片/视频");
        }

        // 旧邻点折线：照片挪走后断开，避免残留虚线
        Set<Long> moveIds = new HashSet<Long>();
        for (BizTrackPoint p : mergePoints) {
            moveIds.add(p.getPointId());
        }
        for (int i = 0; i < existing.size(); i++) {
            BizTrackPoint cur = existing.get(i);
            if (!moveIds.contains(cur.getPointId())) {
                continue;
            }
            if (i > 0) {
                BizTrackPoint prev = existing.get(i - 1);
                if (!fromPointId.equals(prev.getPointId()) && !moveIds.contains(prev.getPointId())) {
                    clearRoutePath(prev.getPointId());
                    prev.setRoutePath(null);
                }
            }
        }

        List<BizTrackPoint> working = new ArrayList<BizTrackPoint>(existing);
        working.removeAll(mergePoints);

        int fromIdx = indexOf(working, fromPointId);
        int toIdx = indexOf(working, toPointId);
        if (fromIdx < 0 || toIdx < 0) {
            throw new ServiceException("路段起终点不存在");
        }
        if (toIdx < fromIdx) {
            throw new ServiceException("终点必须在起点之后，请调整轨迹顺序后再合并");
        }
        if (toIdx > fromIdx + 1) {
            throw new ServiceException("起终点之间还有 "
                    + (toIdx - fromIdx - 1)
                    + " 个点未勾选，请一并合并或先调整顺序");
        }
        if (toIdx != fromIdx + 1) {
            BizTrackPoint movedTo = working.remove(toIdx);
            fromIdx = indexOf(working, fromPointId);
            working.add(fromIdx + 1, movedTo);
            toIdx = fromIdx + 1;
        }

        // 仅用原 GPS 在折线上的投影决定「顺序 / 拆分位置」，绝不改写照片或轨迹点坐标
        Map<Long, BizPhoto> photoMap = loadPhotos(photoIds);
        double[] cum = pathCumMeters(fullPath);
        double totalMeters = cum[cum.length - 1];
        if (totalMeters <= 1e-3) {
            throw new ServiceException("路段折线过短，无法合并");
        }

        List<ProjectedPhoto> projected = new ArrayList<ProjectedPhoto>(mergePoints.size());
        for (BizTrackPoint tp : mergePoints) {
            double latWgs;
            double lngWgs;
            BizPhoto photo = photoMap.get(tp.getPhotoId());
            if (photo != null && photo.getLatitude() != null && photo.getLongitude() != null) {
                latWgs = photo.getLatitude().doubleValue();
                lngWgs = photo.getLongitude().doubleValue();
            } else if (tp.getLatitude() != null && tp.getLongitude() != null) {
                latWgs = tp.getLatitude().doubleValue();
                lngWgs = tp.getLongitude().doubleValue();
            } else {
                throw new ServiceException("照片/视频 #" + tp.getPhotoId() + " 缺少坐标，无法合并到路段");
            }
            double[] gcj = CoordTransformUtils.wgs84ToGcj02(lngWgs, latWgs);
            NearestOnPath nearest = nearestOnPath(fullPath, cum, gcj[1], gcj[0]);
            ProjectedPhoto row = new ProjectedPhoto();
            row.point = tp;
            row.alongMeters = nearest.alongMeters;
            row.fraction = nearest.alongMeters / totalMeters;
            projected.add(row);
        }

        Collections.sort(projected, new Comparator<ProjectedPhoto>() {
            @Override
            public int compare(ProjectedPhoto a, ProjectedPhoto b) {
                int c = Double.compare(a.alongMeters, b.alongMeters);
                if (c != 0) {
                    return c;
                }
                Date ta = pointShootTime(a.point, photoMap);
                Date tb = pointShootTime(b.point, photoMap);
                if (ta != null && tb != null) {
                    return ta.compareTo(tb);
                }
                int sa = a.point.getSequence() == null ? 0 : a.point.getSequence();
                int sb = b.point.getSequence() == null ? 0 : b.point.getSequence();
                return Integer.compare(sa, sb);
            }
        });

        // 投影点过近时略微错开切分比例，保证折线可拆；不改 GPS
        ensureStrictlyIncreasing(projected, totalMeters);

        mergePoints = new ArrayList<BizTrackPoint>(projected.size());
        for (ProjectedPhoto row : projected) {
            mergePoints.add(row.point);
        }

        working.addAll(fromIdx + 1, mergePoints);
        fromIdx = indexOf(working, fromPointId);
        toIdx = indexOf(working, toPointId);

        int n = projected.size();
        double[] fractions = new double[n];
        for (int i = 0; i < n; i++) {
            fractions[i] = clampInterior(projected.get(i).fraction);
        }

        List<List<double[]>> subPaths = splitPathAtFractions(fullPath, fractions);
        if (subPaths.size() != n + 1) {
            throw new ServiceException("拆分路段折线失败");
        }

        // 写入拆分折线：from → p1 → … → to（标记仍用原 GPS，折线走增补路网）
        List<BizTrackPoint> chain = working.subList(fromIdx, toIdx + 1);
        for (int i = 0; i < chain.size() - 1; i++) {
            BizTrackPoint legFrom = chain.get(i);
            legFrom.setTravelMode(travelMode);
            legFrom.setRoutePath(amapDirectionService.toRoutePathJson(subPaths.get(i)));
        }
        toPoint = working.get(toIdx);

        int seq = 1;
        for (BizTrackPoint p : working) {
            p.setSequence(seq++);
        }
        for (BizTrackPoint p : working) {
            trackPointService.updateById(p);
        }

        track.setPointCount(working.size());
        track.setUpdateTime(new Date());
        trackService.updateById(track);

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("mergedCount", n);
        result.put("fromPointId", fromPointId);
        result.put("toPointId", toPointId);
        result.put("travelMode", travelMode);
        result.put("pointCount", working.size());
        return result;
    }

    private Map<Long, BizPhoto> loadPhotos(List<Long> photoIds) {
        Map<Long, BizPhoto> map = new HashMap<Long, BizPhoto>();
        if (photoIds == null || photoIds.isEmpty()) {
            return map;
        }
        List<BizPhoto> list = photoService.listByIds(photoIds);
        if (list != null) {
            for (BizPhoto p : list) {
                if (p != null && p.getPhotoId() != null) {
                    map.put(p.getPhotoId(), p);
                }
            }
        }
        return map;
    }

    private static Date pointShootTime(BizTrackPoint tp, Map<Long, BizPhoto> photoMap) {
        if (tp == null) {
            return null;
        }
        BizPhoto photo = tp.getPhotoId() == null ? null : photoMap.get(tp.getPhotoId());
        if (photo != null && photo.getShootTime() != null) {
            return photo.getShootTime();
        }
        return tp.getPointTime();
    }

    private static final class ProjectedPhoto {
        BizTrackPoint point;
        double alongMeters;
        double fraction;
    }

    private static final class NearestOnPath {
        double lat;
        double lng;
        double alongMeters;
        double offsetMeters;
    }

    /**
     * 将 GCJ 点投影到折线最近点（路径为 [[lat,lng],...]）。
     */
    private static NearestOnPath nearestOnPath(List<double[]> path, double[] cum, double lat, double lng) {
        NearestOnPath best = new NearestOnPath();
        best.lat = path.get(0)[0];
        best.lng = path.get(0)[1];
        best.alongMeters = 0;
        best.offsetMeters = GeoDistanceUtils.haversineKm(lat, lng, best.lat, best.lng) * 1000.0;

        for (int i = 0; i < path.size() - 1; i++) {
            double[] a = path.get(i);
            double[] b = path.get(i + 1);
            double[] proj = projectToSegment(lat, lng, a[0], a[1], b[0], b[1]);
            double offset = GeoDistanceUtils.haversineKm(lat, lng, proj[0], proj[1]) * 1000.0;
            if (offset < best.offsetMeters) {
                best.offsetMeters = offset;
                best.lat = proj[0];
                best.lng = proj[1];
                double segLen = cum[i + 1] - cum[i];
                best.alongMeters = cum[i] + segLen * proj[2];
            }
        }
        return best;
    }

    /**
     * @return [lat, lng, t] t∈[0,1] 为段上比例
     */
    private static double[] projectToSegment(double lat, double lng,
                                            double aLat, double aLng,
                                            double bLat, double bLng) {
        // 局部平面近似（米）
        double midLat = ((aLat + bLat) / 2.0) * Math.PI / 180.0;
        double x0 = (lng - aLng) * Math.cos(midLat) * 111320.0;
        double y0 = (lat - aLat) * 110540.0;
        double x1 = (bLng - aLng) * Math.cos(midLat) * 111320.0;
        double y1 = (bLat - aLat) * 110540.0;
        double denom = x1 * x1 + y1 * y1;
        double t = 0;
        if (denom > 1e-6) {
            t = (x0 * x1 + y0 * y1) / denom;
            if (t < 0) {
                t = 0;
            } else if (t > 1) {
                t = 1;
            }
        }
        return new double[]{
                aLat + (bLat - aLat) * t,
                aLng + (bLng - aLng) * t,
                t
        };
    }

    /** 保证沿程严格递增，仅在重叠时微调几米，避免大幅移位 */
    private static void ensureStrictlyIncreasing(List<ProjectedPhoto> projected, double totalMeters) {
        if (projected == null || projected.isEmpty() || totalMeters <= 0) {
            return;
        }
        double minGap = Math.min(8.0, Math.max(1.0, totalMeters / (projected.size() + 2) * 0.02));
        for (int i = 0; i < projected.size(); i++) {
            ProjectedPhoto row = projected.get(i);
            double minAlong = minGap * (i + 1);
            double maxAlong = totalMeters - minGap * (projected.size() - i);
            if (maxAlong < minAlong) {
                maxAlong = minAlong;
            }
            if (i > 0) {
                double floor = projected.get(i - 1).alongMeters + minGap;
                if (row.alongMeters < floor) {
                    row.alongMeters = Math.min(maxAlong, floor);
                }
            }
            if (row.alongMeters < minAlong) {
                row.alongMeters = minAlong;
            }
            if (row.alongMeters > maxAlong) {
                row.alongMeters = maxAlong;
            }
            row.fraction = row.alongMeters / totalMeters;
        }
    }

    private static double clampInterior(double v) {
        if (v < 0.001) {
            return 0.001;
        }
        if (v > 0.999) {
            return 0.999;
        }
        return v;
    }

    private static double clamp01(double v) {
        if (v < 0) {
            return 0;
        }
        if (v > 1) {
            return 1;
        }
        return v;
    }

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

    private static double[] pathCumMeters(List<double[]> path) {
        double[] cum = new double[path.size()];
        cum[0] = 0;
        for (int i = 1; i < path.size(); i++) {
            double[] a = path.get(i - 1);
            double[] b = path.get(i);
            // path 为 GCJ [lat,lng]，距离近似足够用于投影
            cum[i] = cum[i - 1] + GeoDistanceUtils.haversineKm(a[0], a[1], b[0], b[1]) * 1000.0;
        }
        return cum;
    }

    private static double[] pointAtFraction(List<double[]> path, double fraction) {
        double[] cum = pathCumMeters(path);
        double total = cum[cum.length - 1];
        if (total <= 1e-6) {
            return new double[]{path.get(0)[0], path.get(0)[1]};
        }
        double target = clamp01(fraction) * total;
        for (int i = 1; i < cum.length; i++) {
            if (target <= cum[i]) {
                double seg = cum[i] - cum[i - 1];
                double t = seg <= 1e-9 ? 0 : (target - cum[i - 1]) / seg;
                double[] a = path.get(i - 1);
                double[] b = path.get(i);
                return new double[]{
                        a[0] + (b[0] - a[0]) * t,
                        a[1] + (b[1] - a[1]) * t
                };
            }
        }
        double[] last = path.get(path.size() - 1);
        return new double[]{last[0], last[1]};
    }

    private static List<List<double[]>> splitPathAtFractions(List<double[]> path, double[] fractions) {
        List<Double> cuts = new ArrayList<Double>();
        cuts.add(0.0);
        for (double f : fractions) {
            cuts.add(clamp01(f));
        }
        cuts.add(1.0);
        // 单调
        for (int i = 1; i < cuts.size(); i++) {
            if (cuts.get(i) <= cuts.get(i - 1)) {
                cuts.set(i, Math.min(1.0, cuts.get(i - 1) + 1e-4));
            }
        }
        cuts.set(cuts.size() - 1, 1.0);

        List<List<double[]>> parts = new ArrayList<List<double[]>>();
        for (int i = 0; i < cuts.size() - 1; i++) {
            parts.add(extractSubPath(path, cuts.get(i), cuts.get(i + 1)));
        }
        return parts;
    }

    private static List<double[]> extractSubPath(List<double[]> path, double f0, double f1) {
        double[] a = pointAtFraction(path, f0);
        double[] b = pointAtFraction(path, f1);
        double[] cum = pathCumMeters(path);
        double total = cum[cum.length - 1];
        double d0 = clamp01(f0) * total;
        double d1 = clamp01(f1) * total;
        List<double[]> out = new ArrayList<double[]>();
        out.add(new double[]{a[0], a[1]});
        if (total > 1e-6) {
            for (int i = 1; i < path.size() - 1; i++) {
                if (cum[i] > d0 + 1e-6 && cum[i] < d1 - 1e-6) {
                    out.add(new double[]{path.get(i)[0], path.get(i)[1]});
                }
            }
        }
        out.add(new double[]{b[0], b[1]});
        return out;
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
        int i = indexOf(list, pointId);
        return i < 0 ? null : list.get(i);
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

    @SuppressWarnings("unchecked")
    private List<Long> toLongList(Object v) {
        List<Long> out = new ArrayList<Long>();
        if (v == null) {
            return out;
        }
        if (v instanceof List) {
            for (Object item : (List<?>) v) {
                Long id = toLong(item);
                if (id != null) {
                    out.add(id);
                }
            }
            return out;
        }
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) {
            return out;
        }
        for (String part : s.split("[,，\\s]+")) {
            Long id = toLong(part);
            if (id != null) {
                out.add(id);
            }
        }
        return out;
    }
}
