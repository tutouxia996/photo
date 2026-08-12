package com.sq.bus.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sq.bus.config.AlbumProperties;
import com.sq.bus.constants.AlbumDeleted;
import com.sq.bus.constants.PhotoLocationSource;
import com.sq.bus.domain.BizPhoto;
import com.sq.bus.service.IBizPhotoService;
import com.sq.bus.service.IPhotoFallbackLocationService;
import com.sq.bus.utils.GeoDistanceUtils;
import com.sq.bus.utils.PhotoFieldUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 兜底 A：同相册权威 GPS + 拍摄时间插值。不覆盖 EXIF/视频/手工坐标，结果默认不进主轨迹。
 */
@Service
public class PhotoFallbackLocationServiceImpl implements IPhotoFallbackLocationService {

    private static final Logger log = LoggerFactory.getLogger(PhotoFallbackLocationServiceImpl.class);

    @Autowired
    private IBizPhotoService photoService;

    @Autowired
    private AlbumProperties albumProperties;

    @Override
    public int fillMissingByTimeInterp(Long albumId) {
        if (albumId == null) {
            return 0;
        }
        AlbumProperties.FallbackLocationConfig cfg = albumProperties.getFallbackLocation();
        if (cfg == null || !cfg.isEnabled()) {
            return 0;
        }

        List<BizPhoto> photos = photoService.list(new LambdaQueryWrapper<BizPhoto>()
                .eq(BizPhoto::getAlbumId, albumId)
                .eq(BizPhoto::getDeleted, AlbumDeleted.NORMAL)
                .orderByAsc(BizPhoto::getShootTime)
                .orderByAsc(BizPhoto::getPhotoId));
        if (photos == null || photos.isEmpty()) {
            return 0;
        }

        List<BizPhoto> anchors = new ArrayList<BizPhoto>();
        for (BizPhoto p : photos) {
            if (PhotoLocationSource.isAuthoritativeGps(p) && p.getShootTime() != null) {
                anchors.add(p);
            }
        }
        if (anchors.isEmpty()) {
            return 0;
        }

        int updated = 0;
        Date now = new Date();
        for (BizPhoto photo : photos) {
            if (photo.getShootTime() == null) {
                continue;
            }
            boolean hasCoords = photo.getLatitude() != null && photo.getLongitude() != null;
            if (hasCoords && !PhotoLocationSource.isFallback(photo.getLocationSource())) {
                continue;
            }

            Estimate est = estimate(photo.getShootTime(), anchors, cfg);
            if (est == null) {
                continue;
            }

            photo.setLatitude(BigDecimal.valueOf(est.lat).setScale(7, BigDecimal.ROUND_HALF_UP));
            photo.setLongitude(BigDecimal.valueOf(est.lng).setScale(7, BigDecimal.ROUND_HALF_UP));
            photo.setLocationSource(PhotoLocationSource.TIME_INTERP);
            photo.setLocationConfidence(BigDecimal.valueOf(est.confidence).setScale(3, BigDecimal.ROUND_HALF_UP));
            photo.setUpdateTime(now);
            PhotoFieldUtils.clamp(photo);
            if (photoService.updateById(photo)) {
                updated++;
            }
        }

        if (updated > 0) {
            log.info("相册 {} 时间插值兜底更新 {} 条媒体坐标", albumId, updated);
        }
        return updated;
    }

    private Estimate estimate(Date shootTime, List<BizPhoto> anchors,
                              AlbumProperties.FallbackLocationConfig cfg) {
        long t = shootTime.getTime();
        BizPhoto prev = null;
        BizPhoto next = null;
        for (BizPhoto a : anchors) {
            long at = a.getShootTime().getTime();
            if (at <= t) {
                prev = a;
            }
            if (at >= t && next == null) {
                next = a;
            }
        }

        if (prev != null && next != null && prev.getPhotoId().equals(next.getPhotoId())) {
            return new Estimate(
                    prev.getLatitude().doubleValue(),
                    prev.getLongitude().doubleValue(),
                    0.95);
        }

        if (prev != null && next != null) {
            return interpolate(prev, next, t, cfg);
        }
        if (prev != null) {
            return extrapolateOrSnap(prev, true, t, anchors, cfg);
        }
        if (next != null) {
            return extrapolateOrSnap(next, false, t, anchors, cfg);
        }
        return null;
    }

    private Estimate interpolate(BizPhoto prev, BizPhoto next, long t,
                                 AlbumProperties.FallbackLocationConfig cfg) {
        long t0 = prev.getShootTime().getTime();
        long t1 = next.getShootTime().getTime();
        if (t1 <= t0) {
            return new Estimate(
                    prev.getLatitude().doubleValue(),
                    prev.getLongitude().doubleValue(),
                    0.5);
        }
        double gapHours = (t1 - t0) / 3600000.0;
        if (gapHours > cfg.getMaxInterpGapHours()) {
            return null;
        }

        double lat0 = prev.getLatitude().doubleValue();
        double lng0 = prev.getLongitude().doubleValue();
        double lat1 = next.getLatitude().doubleValue();
        double lng1 = next.getLongitude().doubleValue();
        double distKm = GeoDistanceUtils.haversineKm(lat0, lng0, lat1, lng1);
        double hours = Math.max(gapHours, 1.0 / 3600.0);
        double speed = distKm / hours;
        if (speed > cfg.getMaxReasonableSpeedKmh()) {
            return null;
        }

        double ratio = (t - t0) / (double) (t1 - t0);
        double lat = lat0 + (lat1 - lat0) * ratio;
        double lng = lng0 + (lng1 - lng0) * ratio;
        double confidence;
        if (gapHours <= 1.0) {
            confidence = 0.85;
        } else if (gapHours <= 6.0) {
            confidence = 0.7;
        } else {
            confidence = 0.5;
        }
        return new Estimate(lat, lng, confidence);
    }

    private Estimate extrapolateOrSnap(BizPhoto anchor, boolean afterAnchor, long t,
                                       List<BizPhoto> anchors,
                                       AlbumProperties.FallbackLocationConfig cfg) {
        long dtMs = Math.abs(t - anchor.getShootTime().getTime());
        long snapMs = cfg.getMaxSnapGapMinutes() * 60L * 1000L;
        if (anchors.size() == 1 || dtMs <= snapMs) {
            if (dtMs > snapMs) {
                return null;
            }
            double conf = dtMs <= 5 * 60 * 1000L ? 0.75 : 0.55;
            return new Estimate(
                    anchor.getLatitude().doubleValue(),
                    anchor.getLongitude().doubleValue(),
                    conf);
        }

        BizPhoto other = findNeighborForVelocity(anchor, afterAnchor, anchors);
        if (other == null) {
            if (dtMs <= snapMs) {
                return new Estimate(
                        anchor.getLatitude().doubleValue(),
                        anchor.getLongitude().doubleValue(),
                        0.5);
            }
            return null;
        }

        long tA = other.getShootTime().getTime();
        long tB = anchor.getShootTime().getTime();
        if (tB == tA) {
            return null;
        }
        double latA = other.getLatitude().doubleValue();
        double lngA = other.getLongitude().doubleValue();
        double latB = anchor.getLatitude().doubleValue();
        double lngB = anchor.getLongitude().doubleValue();
        double segHours = Math.abs(tB - tA) / 3600000.0;
        if (segHours < 1.0 / 3600.0) {
            return null;
        }
        double dLat = (latB - latA) / segHours;
        double dLng = (lngB - lngA) / segHours;
        double hoursFromAnchor = (t - tB) / 3600000.0;
        double lat = latB + dLat * hoursFromAnchor;
        double lng = lngB + dLng * hoursFromAnchor;
        double moveKm = GeoDistanceUtils.haversineKm(latB, lngB, lat, lng);
        if (moveKm > cfg.getMaxExtrapolateKm()) {
            return null;
        }
        double speed = moveKm / Math.max(Math.abs(hoursFromAnchor), 1.0 / 3600.0);
        if (speed > cfg.getMaxReasonableSpeedKmh()) {
            return null;
        }
        return new Estimate(lat, lng, 0.4);
    }

    /** afterAnchor=true：锚点在目标之前，用「锚点前一点→锚点」速度向前外推 */
    private BizPhoto findNeighborForVelocity(BizPhoto anchor, boolean afterAnchor, List<BizPhoto> anchors) {
        int idx = -1;
        for (int i = 0; i < anchors.size(); i++) {
            if (anchors.get(i).getPhotoId().equals(anchor.getPhotoId())) {
                idx = i;
                break;
            }
        }
        if (idx < 0) {
            return null;
        }
        if (afterAnchor) {
            return idx > 0 ? anchors.get(idx - 1) : null;
        }
        return idx < anchors.size() - 1 ? anchors.get(idx + 1) : null;
    }

    private static final class Estimate {
        private final double lat;
        private final double lng;
        private final double confidence;

        private Estimate(double lat, double lng, double confidence) {
            this.lat = lat;
            this.lng = lng;
            this.confidence = confidence;
        }
    }
}
