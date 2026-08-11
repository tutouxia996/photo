package com.sq.bus.service.gpx;

import com.alibaba.fastjson2.JSON;
import com.sq.bus.config.AlbumProperties;
import com.sq.bus.domain.BizPhoto;
import com.sq.bus.domain.BizTrackPoint;
import com.sq.bus.utils.CoordTransformUtils;
import com.sq.bus.utils.GeoDistanceUtils;
import com.sq.bus.utils.GpxParseUtils;
import com.sq.bus.utils.GpxParseUtils.GpxSample;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 将 GPX 采样点构建为独立轨迹：起点 + 终点 + 完整折线。
 * 不与相册照片/既有轨迹做自动匹配或拼接，后续可由用户手工调整。
 */
@Service
public class GpxTrackBuildService {

    public static final String GPX_DESC_PREFIX = "[GPX]";

    @Autowired
    private AlbumProperties albumProperties;

    public static final class BuildResult {
        public final List<BizTrackPoint> points;
        public final double totalDistanceKm;
        public final long durationSec;
        public final Date startTime;
        public final Date endTime;
        public final String sourceType;
        public final int matchedMedia;
        public final int photoGpsOnly;
        public final int unmatchedMedia;

        public BuildResult(List<BizTrackPoint> points, double totalDistanceKm, long durationSec,
                           Date startTime, Date endTime, String sourceType,
                           int matchedMedia, int photoGpsOnly, int unmatchedMedia) {
            this.points = points;
            this.totalDistanceKm = totalDistanceKm;
            this.durationSec = durationSec;
            this.startTime = startTime;
            this.endTime = endTime;
            this.sourceType = sourceType;
            this.matchedMedia = matchedMedia;
            this.photoGpsOnly = photoGpsOnly;
            this.unmatchedMedia = unmatchedMedia;
        }
    }

    /**
     * @param gpxSamples GPX 采样点（已按时间排序）
     * @param allMedia   保留参数以兼容旧调用，当前不参与构建
     */
    public BuildResult build(List<GpxSample> gpxSamples, List<BizPhoto> allMedia) {
        List<GpxSample> gpx = gpxSamples == null ? Collections.<GpxSample>emptyList() : gpxSamples;
        if (gpx.size() < 2) {
            return new BuildResult(Collections.<BizTrackPoint>emptyList(), 0D, 0L,
                    null, null, "gpx", 0, 0, 0);
        }

        AlbumProperties.GpxConfig cfg = albumProperties.getGpx() == null
                ? new AlbumProperties.GpxConfig() : albumProperties.getGpx();

        GpxSample first = gpx.get(0);
        GpxSample last = gpx.get(gpx.size() - 1);

        BizTrackPoint start = gpxWaypoint(first, GPX_DESC_PREFIX + "起点");
        start.setTravelMode("other");
        start.setRoutePath(pathJson(gpx, cfg));

        BizTrackPoint end = gpxWaypoint(last, GPX_DESC_PREFIX + "终点");

        List<BizTrackPoint> points = new ArrayList<BizTrackPoint>(2);
        points.add(start);
        points.add(end);
        start.setSequence(1);
        end.setSequence(2);

        double distKm = pathDistanceKm(gpx);
        Date startTime = GpxParseUtils.toDate(first.timeMs);
        Date endTime = GpxParseUtils.toDate(last.timeMs);
        long duration = Math.max(0L, (last.timeMs - first.timeMs) / 1000L);

        return new BuildResult(points, distKm, duration, startTime, endTime, "gpx", 0, 0, 0);
    }

    private static double pathDistanceKm(List<GpxSample> samples) {
        double dist = 0D;
        for (int i = 1; i < samples.size(); i++) {
            GpxSample a = samples.get(i - 1);
            GpxSample b = samples.get(i);
            dist += GeoDistanceUtils.haversineKm(a.latWgs, a.lngWgs, b.latWgs, b.lngWgs);
        }
        return dist;
    }

    private String pathJson(List<GpxSample> samples, AlbumProperties.GpxConfig cfg) {
        List<GpxSample> simplified = simplify(samples, cfg.getSimplifyMinMeters(), cfg.getMaxPathPoints());
        List<List<Double>> path = new ArrayList<List<Double>>();
        for (GpxSample s : simplified) {
            double[] gcj = CoordTransformUtils.wgs84ToGcj02(s.lngWgs, s.latWgs);
            List<Double> row = new ArrayList<Double>(2);
            row.add(gcj[1]); // lat
            row.add(gcj[0]); // lng
            path.add(row);
        }
        if (path.size() < 2) {
            return null;
        }
        return JSON.toJSONString(path);
    }

    private static List<GpxSample> simplify(List<GpxSample> samples, double minMeters, int maxPoints) {
        if (samples == null || samples.isEmpty()) {
            return Collections.<GpxSample>emptyList();
        }
        if (samples.size() <= 2) {
            return samples;
        }
        List<GpxSample> out = samples;
        if (minMeters > 0) {
            double minKm = minMeters / 1000.0;
            out = new ArrayList<GpxSample>();
            out.add(samples.get(0));
            GpxSample prev = samples.get(0);
            for (int i = 1; i < samples.size() - 1; i++) {
                GpxSample cur = samples.get(i);
                double d = GeoDistanceUtils.haversineKm(prev.latWgs, prev.lngWgs, cur.latWgs, cur.lngWgs);
                if (d >= minKm) {
                    out.add(cur);
                    prev = cur;
                }
            }
            out.add(samples.get(samples.size() - 1));
        }
        if (maxPoints <= 0 || out.size() <= maxPoints) {
            return out;
        }
        List<GpxSample> reduced = new ArrayList<GpxSample>();
        reduced.add(out.get(0));
        double step = (out.size() - 1) * 1.0 / (maxPoints - 1);
        for (int i = 1; i < maxPoints - 1; i++) {
            int idx = (int) Math.round(i * step);
            if (idx <= 0) {
                idx = 1;
            }
            if (idx >= out.size() - 1) {
                idx = out.size() - 2;
            }
            reduced.add(out.get(idx));
        }
        reduced.add(out.get(out.size() - 1));
        return reduced;
    }

    private static BizTrackPoint gpxWaypoint(GpxSample s, String desc) {
        BizTrackPoint p = new BizTrackPoint();
        p.setPhotoId(null);
        p.setLatitude(BigDecimal.valueOf(s.latWgs));
        p.setLongitude(BigDecimal.valueOf(s.lngWgs));
        p.setPointTime(GpxParseUtils.toDate(s.timeMs));
        if (s.ele != null) {
            p.setAltitude(BigDecimal.valueOf(s.ele));
        }
        p.setDescription(desc);
        return p;
    }

    public static boolean isGpxSynthetic(BizTrackPoint p) {
        if (p == null || p.getPhotoId() != null) {
            return false;
        }
        String d = p.getDescription();
        return d != null && d.startsWith(GPX_DESC_PREFIX);
    }
}
