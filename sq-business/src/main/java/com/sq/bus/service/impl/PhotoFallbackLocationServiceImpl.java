package com.sq.bus.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sq.bus.config.AlbumProperties;
import com.sq.bus.constants.AlbumDeleted;
import com.sq.bus.constants.PhotoLocationSource;
import com.sq.bus.domain.BizPhoto;
import com.sq.bus.domain.RegionLocateRequest;
import com.sq.bus.service.IBizAlbumService;
import com.sq.bus.service.IBizPhotoService;
import com.sq.bus.service.IPhotoFallbackLocationService;
import com.sq.bus.service.route.AmapPlaceSearchService;
import com.sq.bus.utils.GeoDistanceUtils;
import com.sq.bus.utils.PhotoFieldUtils;
import com.sq.common.exception.ServiceException;
import com.sq.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 兜底：区域中心粗定位 + 同相册权威 GPS 时间插值。不覆盖 EXIF/视频/手工坐标，结果默认不进主轨迹。
 */
@Service
public class PhotoFallbackLocationServiceImpl implements IPhotoFallbackLocationService {

    private static final Logger log = LoggerFactory.getLogger(PhotoFallbackLocationServiceImpl.class);

    /** 同区域中心附近轻微分散半径（米），便于地图上分别拖动 */
    private static final double REGION_SPREAD_MIN_M = 60.0;
    private static final double REGION_SPREAD_MAX_M = 280.0;

    @Autowired
    private IBizPhotoService photoService;

    @Autowired
    private IBizAlbumService albumService;

    @Autowired
    private AlbumProperties albumProperties;

    @Autowired
    private AmapPlaceSearchService amapPlaceSearchService;

    @Override
    public Map<String, Object> fillMissingByRegionCenter(Long albumId, RegionLocateRequest request) {
        if (albumId == null) {
            throw new ServiceException("相册ID不能为空");
        }
        if (request == null) {
            throw new ServiceException("区域信息不能为空");
        }

        String province = trimToNull(request.getProvince());
        String city = trimToNull(request.getCity());
        String district = trimToNull(request.getDistrict());
        String country = trimToNull(request.getCountry());
        String address = trimToNull(request.getAddress());

        double centerLat;
        double centerLng;
        if (request.getLatitude() != null && request.getLongitude() != null) {
            centerLat = request.getLatitude().doubleValue();
            centerLng = request.getLongitude().doubleValue();
            if (StringUtils.isEmpty(province) || StringUtils.isEmpty(city) || StringUtils.isEmpty(district)
                    || StringUtils.isEmpty(address)) {
                try {
                    Map<String, Object> geo = amapPlaceSearchService.geocode(buildGeocodeKeyword(
                            country, province, city, district, address));
                    if (StringUtils.isEmpty(province)) {
                        province = asString(geo.get("province"));
                    }
                    if (StringUtils.isEmpty(city)) {
                        city = asString(geo.get("city"));
                    }
                    if (StringUtils.isEmpty(district)) {
                        district = asString(geo.get("district"));
                    }
                    if (StringUtils.isEmpty(address)) {
                        address = asString(geo.get("formattedAddress"));
                    }
                } catch (Exception e) {
                    log.debug("region locate reverse enrich skipped: {}", e.getMessage());
                }
            }
        } else {
            String keyword = buildGeocodeKeyword(country, province, city, district, address);
            if (StringUtils.isEmpty(keyword)) {
                throw new ServiceException("请填写国家/省/市/区，或搜索地点后选中，或直接指定坐标");
            }
            Map<String, Object> geo = amapPlaceSearchService.geocode(keyword);
            Object wgsLat = geo.get("wgsLat");
            Object wgsLng = geo.get("wgsLng");
            if (wgsLat == null || wgsLng == null) {
                throw new ServiceException("地理编码未返回有效坐标，请换更具体的地点名（如：地坛公园）");
            }
            centerLat = ((Number) wgsLat).doubleValue();
            centerLng = ((Number) wgsLng).doubleValue();
            if (StringUtils.isEmpty(province)) {
                province = asString(geo.get("province"));
            }
            if (StringUtils.isEmpty(city)) {
                city = asString(geo.get("city"));
            }
            if (StringUtils.isEmpty(district)) {
                district = asString(geo.get("district"));
            }
            if (StringUtils.isEmpty(address)) {
                address = asString(geo.get("formattedAddress"));
            }
        }

        if (StringUtils.isEmpty(address)) {
            address = joinRegion(country, province, city, district);
        }

        double confidence = confidenceForRegion(district, city, province, country);
        boolean overwriteRegion = request.getOverwriteRegionCenter() == null
                || Boolean.TRUE.equals(request.getOverwriteRegionCenter());

        List<BizPhoto> photos = photoService.list(new LambdaQueryWrapper<BizPhoto>()
                .eq(BizPhoto::getAlbumId, albumId)
                .eq(BizPhoto::getDeleted, AlbumDeleted.NORMAL)
                .orderByAsc(BizPhoto::getShootTime)
                .orderByAsc(BizPhoto::getPhotoId));
        if (photos == null || photos.isEmpty()) {
            throw new ServiceException("该相册没有可定位的照片/视频");
        }

        int updated = 0;
        int skipped = 0;
        Date now = new Date();
        int spreadIndex = 0;
        for (BizPhoto photo : photos) {
            boolean hasCoords = photo.getLatitude() != null && photo.getLongitude() != null;
            String source = photo.getLocationSource();
            if (hasCoords) {
                if (PhotoLocationSource.REGION_CENTER.equals(source)) {
                    if (!overwriteRegion) {
                        skipped++;
                        continue;
                    }
                } else {
                    // 有坐标且非区域中心：不覆盖（含权威与其它兜底）
                    skipped++;
                    continue;
                }
            }

            double[] spread = spreadAround(centerLat, centerLng, spreadIndex++,
                    photo.getPhotoId() == null ? 0L : photo.getPhotoId());
            photo.setLatitude(BigDecimal.valueOf(spread[0]).setScale(7, BigDecimal.ROUND_HALF_UP));
            photo.setLongitude(BigDecimal.valueOf(spread[1]).setScale(7, BigDecimal.ROUND_HALF_UP));
            photo.setLocationSource(PhotoLocationSource.REGION_CENTER);
            photo.setLocationConfidence(BigDecimal.valueOf(confidence).setScale(3, BigDecimal.ROUND_HALF_UP));
            photo.setProvince(province);
            photo.setCity(city);
            photo.setDistrict(district);
            photo.setAddress(address);
            photo.setUpdateTime(now);
            PhotoFieldUtils.clamp(photo);
            if (photoService.updateById(photo)) {
                updated++;
            }
        }

        if (updated > 0) {
            log.info("相册 {} 区域中心粗定位更新 {} 条媒体坐标 center={},{} addr={}",
                    albumId, updated, centerLat, centerLng, address);
            try {
                albumService.refreshAlbumStats(albumId);
            } catch (Exception e) {
                log.debug("refresh album stats after region locate: {}", e.getMessage());
            }
            // 若相册内已有权威 GPS，顺带用时间插值补其余盲点
            try {
                fillMissingByTimeInterp(albumId);
            } catch (Exception e) {
                log.debug("time interp after region locate: {}", e.getMessage());
            }
        } else if (skipped > 0) {
            throw new ServiceException("没有可写入的媒体（" + skipped
                    + " 条已有其它来源坐标被跳过）。可清空后重试，或到照片地图查看现有点位");
        } else {
            throw new ServiceException("区域定位未写入任何媒体，请检查相册是否有照片");
        }

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("updated", updated);
        result.put("skipped", skipped);
        result.put("photoCount", photos.size());
        result.put("centerLat", round7(centerLat));
        result.put("centerLng", round7(centerLng));
        result.put("address", address);
        result.put("province", province);
        result.put("city", city);
        result.put("district", district);
        return result;
    }

    private static double round7(double v) {
        return Math.round(v * 1e7d) / 1e7d;
    }

    private static String buildGeocodeKeyword(String country, String province, String city,
                                              String district, String address) {
        if (StringUtils.isNotEmpty(address)
                && (StringUtils.isNotEmpty(province) || StringUtils.isNotEmpty(city)
                || StringUtils.isNotEmpty(district))) {
            // 已有行政区时优先用拼接，避免 address 过泛
        }
        String joined = joinRegion(country, province, city, district);
        if (StringUtils.isNotEmpty(joined)) {
            return joined;
        }
        return address;
    }

    private static String joinRegion(String country, String province, String city, String district) {
        StringBuilder sb = new StringBuilder();
        appendPart(sb, country);
        appendPart(sb, province);
        appendPart(sb, city);
        appendPart(sb, district);
        return sb.length() == 0 ? null : sb.toString();
    }

    private static void appendPart(StringBuilder sb, String part) {
        if (StringUtils.isEmpty(part)) {
            return;
        }
        if (sb.length() > 0) {
            sb.append(' ');
        }
        sb.append(part.trim());
    }

    private static double confidenceForRegion(String district, String city, String province, String country) {
        if (StringUtils.isNotEmpty(district)) {
            return 0.40;
        }
        if (StringUtils.isNotEmpty(city)) {
            return 0.30;
        }
        if (StringUtils.isNotEmpty(province)) {
            return 0.22;
        }
        if (StringUtils.isNotEmpty(country)) {
            return 0.15;
        }
        return 0.25;
    }

    /**
     * 在中心点附近按序号做确定性轻微分散（约 60~280m），避免估计点完全叠在一起无法拖选。
     */
    private static double[] spreadAround(double lat, double lng, int index, long photoId) {
        if (index <= 0) {
            return new double[]{lat, lng};
        }
        double angle = ((photoId * 37L + index * 47L) % 360 + 360) % 360 * Math.PI / 180.0;
        double t = (index % 8) / 7.0;
        double radiusM = REGION_SPREAD_MIN_M + (REGION_SPREAD_MAX_M - REGION_SPREAD_MIN_M) * t;
        double dLat = (radiusM / 111320.0) * Math.cos(angle);
        double cosLat = Math.cos(Math.toRadians(lat));
        double metersPerDegLng = 111320.0 * Math.max(0.2, Math.abs(cosLat));
        double dLng = (radiusM / metersPerDegLng) * Math.sin(angle);
        return new double[]{lat + dLat, lng + dLng};
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String asString(Object obj) {
        if (obj == null) {
            return null;
        }
        String s = String.valueOf(obj).trim();
        if (s.isEmpty() || "[]".equals(s) || "null".equalsIgnoreCase(s)) {
            return null;
        }
        return s;
    }

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
