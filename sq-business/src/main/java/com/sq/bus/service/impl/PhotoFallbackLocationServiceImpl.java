package com.sq.bus.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sq.bus.config.AlbumProperties;
import com.sq.bus.constants.AlbumDeleted;
import com.sq.bus.constants.PhotoLocationSource;
import com.sq.bus.domain.AiLandmarkRequest;
import com.sq.bus.domain.BizPhoto;
import com.sq.bus.domain.RegionLocateRequest;
import com.sq.bus.service.IBizAlbumService;
import com.sq.bus.service.IBizPhotoService;
import com.sq.bus.service.IPhotoFallbackLocationService;
import com.sq.bus.service.ai.VisionLandmarkClient;
import com.sq.bus.service.route.AmapPlaceSearchService;
import com.sq.bus.utils.GeoDistanceUtils;
import com.sq.bus.utils.PhotoFieldUtils;
import com.sq.common.exception.ServiceException;
import com.sq.common.utils.StringUtils;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 兜底：区域中心粗定位 + 同相册权威 GPS 时间插值。不覆盖 EXIF/视频/手工坐标，结果默认不进主轨迹。
 */
@Service
public class PhotoFallbackLocationServiceImpl implements IPhotoFallbackLocationService {

    private static final Logger log = LoggerFactory.getLogger(PhotoFallbackLocationServiceImpl.class);

    /** 同区域中心附近分散半径（米），随序号扩大，便于地图上分别拖选 */
    private static final double REGION_SPREAD_MIN_M = 30.0;
    private static final double REGION_SPREAD_MAX_M = 450.0;
    /** AI 地标附近分散（米） */
    private static final double AI_SPREAD_MIN_M = 20.0;
    private static final double AI_SPREAD_MAX_M = 180.0;
    /** 时间插值同锚点附近分散（米），避免橙色「估」完全重合 */
    private static final double TIME_SPREAD_MIN_M = 25.0;
    private static final double TIME_SPREAD_MAX_M = 380.0;

    @Autowired
    private IBizPhotoService photoService;

    @Autowired
    private IBizAlbumService albumService;

    @Autowired
    private AlbumProperties albumProperties;

    @Autowired
    private AmapPlaceSearchService amapPlaceSearchService;

    @Autowired
    private VisionLandmarkClient visionLandmarkClient;

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
        // 用户从地点搜索选了具体景点时，保留景点名（勿被街道 formattedAddress 覆盖）
        String requestAddress = trimToNull(request.getAddress());
        if (StringUtils.isNotEmpty(requestAddress)
                && request.getLatitude() != null && request.getLongitude() != null) {
            address = requestAddress;
        }

        double confidence = confidenceForRegion(district, city, province, country);
        if (request.getLatitude() != null && request.getLongitude() != null
                && StringUtils.isNotEmpty(requestAddress)) {
            // 具体 POI（如地坛公园）比纯行政区更可信
            confidence = Math.max(confidence, 0.55);
        }
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
                // 权威坐标（EXIF/视频/手工/GPX）永不覆盖
                if (PhotoLocationSource.isAuthoritativeGps(photo)) {
                    skipped++;
                    continue;
                }
                // 已有兜底坐标（区/AI/估）：默认允许覆盖，便于重新锚定到地坛公园等
                if (PhotoLocationSource.isFallback(source)) {
                    if (!overwriteRegion) {
                        skipped++;
                        continue;
                    }
                } else {
                    // 未知来源但有坐标：保守跳过
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
            // 顺带时间插值：仅「同景点附近」的 GPS 可精修「区」点；跨景点锚点会被距离门槛挡住
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

    @Override
    public Map<String, Object> fillMissingByAiLandmark(Long albumId, AiLandmarkRequest request) {
        if (albumId == null) {
            throw new ServiceException("相册ID不能为空");
        }
        if (request == null || request.getPhotoIds() == null || request.getPhotoIds().isEmpty()) {
            throw new ServiceException("请先在照片地图上点选要识别的照片（可多选）");
        }
        AlbumProperties.AiLandmarkConfig aiCfg = albumProperties.getAiLandmark();
        if (aiCfg == null || !aiCfg.isEnabled()) {
            throw new ServiceException("AI 地标识别未启用（album.aiLandmark.enabled=false）");
        }
        if (!visionLandmarkClient.isConfigured()) {
            throw new ServiceException("未配置 AI Key：请在 application-local.yml 设置 album.aiLandmark.apiKey，或环境变量 AI_LANDMARK_API_KEY");
        }

        // 去重并保持请求顺序
        List<Long> requestedIds = new ArrayList<Long>();
        for (Long id : request.getPhotoIds()) {
            if (id != null && !requestedIds.contains(id)) {
                requestedIds.add(id);
            }
        }
        int maxBatch = Math.max(1, aiCfg.getMaxSample());
        if (requestedIds.size() > maxBatch) {
            throw new ServiceException("单次最多识别 " + maxBatch
                    + " 张。可先识别这批，再点选下一批继续（支持多次识别）");
        }

        List<BizPhoto> photos = photoService.list(new LambdaQueryWrapper<BizPhoto>()
                .eq(BizPhoto::getAlbumId, albumId)
                .eq(BizPhoto::getDeleted, AlbumDeleted.NORMAL)
                .orderByAsc(BizPhoto::getShootTime)
                .orderByAsc(BizPhoto::getPhotoId));
        if (photos == null || photos.isEmpty()) {
            throw new ServiceException("该相册没有可识别的照片/视频");
        }

        Map<Long, BizPhoto> byId = new HashMap<Long, BizPhoto>();
        for (BizPhoto photo : photos) {
            if (photo.getPhotoId() != null) {
                byId.put(photo.getPhotoId(), photo);
            }
        }

        List<BizPhoto> samples = new ArrayList<BizPhoto>();
        for (Long id : requestedIds) {
            BizPhoto photo = byId.get(id);
            if (photo == null) {
                throw new ServiceException("照片不属于当前相册或不存在：photoId=" + id);
            }
            if (!canApplyAiLandmark(photo)) {
                throw new ServiceException("照片「"
                        + (StringUtils.isEmpty(photo.getFileName()) ? id : photo.getFileName())
                        + "」已有设备 GPS/手工坐标，不能用 AI 覆盖。请换选估计点（区/AI/估）");
            }
            samples.add(photo);
        }

        RegionAnchor region = buildRegionAnchor(albumId, photos);
        String regionHint = region.hint;
        double maxOffsetKm = aiCfg.getMaxOffsetKm() > 0 ? aiCfg.getMaxOffsetKm() : 0.45;
        List<WhitelistPoi> whitelist = loadRegionPoiWhitelist(region, aiCfg, maxOffsetKm);
        List<String> poiNames = new ArrayList<String>();
        for (WhitelistPoi p : whitelist) {
            poiNames.add(p.name);
        }
        if (StringUtils.isNotEmpty(region.address) && !poiNames.contains(region.address)) {
            poiNames.add(0, region.address);
        }

        // 第一遍：只识别，先不写库，便于同批投票
        List<AiRecognizeDraft> drafts = new ArrayList<AiRecognizeDraft>();
        for (BizPhoto photo : samples) {
            File imageFile = resolveImageFile(photo);
            if (imageFile == null) {
                log.debug("AI landmark skip no image photoId={}", photo.getPhotoId());
                continue;
            }
            try {
                String base64 = encodeImageBase64(imageFile, aiCfg.getMaxImageWidth());
                Map<String, Object> ai = visionLandmarkClient.recognize(
                        base64, "image/jpeg", regionHint, poiNames);
                String landmark = asString(ai.get("landmark"));
                String place = asString(ai.get("place"));
                double aiConf = ai.get("confidence") instanceof Number
                        ? ((Number) ai.get("confidence")).doubleValue() : 0.0;
                if (StringUtils.isEmpty(landmark) && StringUtils.isEmpty(place)) {
                    landmark = region.address != null ? region.address : regionHint;
                }
                String query = StringUtils.isNotEmpty(landmark) ? landmark : place;
                WhitelistPoi matched = matchWhitelistPoi(query, whitelist);
                AiRecognizeDraft draft = new AiRecognizeDraft();
                draft.photo = photo;
                draft.landmark = landmark;
                draft.place = place;
                draft.aiConf = aiConf;
                draft.matched = matched;
                drafts.add(draft);
            } catch (ServiceException e) {
                String msg = e.getMessage() == null ? "" : e.getMessage();
                if (msg.contains("未配置") || msg.contains("HTTP 401") || msg.contains("InvalidApiKey")
                        || msg.contains("Unauthorized")) {
                    throw e;
                }
                log.warn("AI landmark one photo failed photoId={}: {}", photo.getPhotoId(), msg);
            } catch (Exception e) {
                log.warn("AI landmark one photo failed photoId={}: {}", photo.getPhotoId(), e.getMessage());
            }
        }

        // 同批多图投票：出现次数最多的白名单 POI
        WhitelistPoi voted = voteWhitelistPoi(drafts, Math.max(2, aiCfg.getVoteMinCount()));
        int votedApplied = 0;
        if (voted != null) {
            for (AiRecognizeDraft d : drafts) {
                if (d.matched == null || d.aiConf < 0.45) {
                    d.matched = voted;
                    d.fromVote = true;
                    votedApplied++;
                }
            }
        }

        int sampled = samples.size();
        int recognized = 0;
        int updated = 0;
        int clamped = 0;
        List<Map<String, Object>> landmarkRows = new ArrayList<Map<String, Object>>();
        Date now = new Date();
        int spreadIndex = 0;

        for (AiRecognizeDraft draft : drafts) {
            BizPhoto photo = draft.photo;
            LandmarkGeo geo;
            boolean fromWhitelist = draft.matched != null;
            if (draft.matched != null) {
                geo = new LandmarkGeo(draft.matched.lat, draft.matched.lng, draft.matched.name,
                        draft.matched.address, draft.matched.city, draft.matched.district, false);
            } else {
                // 无白名单命中：落回区域中心（不再全城乱搜）
                if (!region.hasCenter) {
                    continue;
                }
                String name = StringUtils.isNotEmpty(draft.landmark) ? draft.landmark
                        : (region.address != null ? region.address : regionHint);
                geo = new LandmarkGeo(region.lat, region.lng, name, region.address,
                        region.city, region.district, true);
                clamped++;
            }
            recognized++;
            double conf = Math.max(0.40, Math.min(0.78, 0.42 + draft.aiConf * 0.35));
            if (fromWhitelist) {
                conf = Math.max(conf, 0.55);
            }
            if (draft.fromVote) {
                conf = Math.min(conf, 0.58);
            }
            if (geo.clamped) {
                conf = Math.min(conf, 0.45);
            }
            double[] spread = spreadAroundAi(geo.lat, geo.lng, spreadIndex++,
                    photo.getPhotoId() == null ? 0L : photo.getPhotoId());
            String landmarkName = draft.matched != null ? draft.matched.name : draft.landmark;
            applyAiLandmark(photo, spread[0], spread[1], conf, geo, landmarkName, draft.place, now);
            if (photoService.updateById(photo)) {
                updated++;
                Map<String, Object> row = new LinkedHashMap<String, Object>();
                row.put("photoId", photo.getPhotoId());
                row.put("landmark", landmarkName);
                row.put("place", draft.place);
                row.put("address", photo.getAddress());
                row.put("lat", photo.getLatitude());
                row.put("lng", photo.getLongitude());
                row.put("confidence", photo.getLocationConfidence());
                row.put("clamped", geo.clamped);
                row.put("fromWhitelist", fromWhitelist);
                row.put("fromVote", draft.fromVote);
                landmarkRows.add(row);
            }
        }

        boolean doInterp = request.getInterpolateOthers() == null
                || Boolean.TRUE.equals(request.getInterpolateOthers());
        int interpUpdated = 0;
        if (doInterp) {
            try {
                interpUpdated = fillMissingByTimeInterp(albumId);
            } catch (Exception e) {
                log.debug("time interp after ai landmark: {}", e.getMessage());
            }
            int pulled = clampFallbackToRegion(albumId, region, maxOffsetKm);
            clamped += pulled;
        }

        if (updated == 0 && recognized == 0) {
            throw new ServiceException("所选照片未能识别出可地理编码的地标。可换选有招牌/标志建筑的清晰照片，或手动拖点确认");
        }

        if (updated > 0) {
            log.info("相册 {} AI 地标定位更新 {} 条（点选 {} / 识别 {} / 白名单 {} / 投票吸附 {} / 钳制 {}），hint={}",
                    albumId, updated, sampled, recognized, whitelist.size(), votedApplied, clamped, regionHint);
            try {
                albumService.refreshAlbumStats(albumId);
            } catch (Exception e) {
                log.debug("refresh album stats after ai landmark: {}", e.getMessage());
            }
        }

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("updated", updated);
        result.put("sampled", sampled);
        result.put("recognized", recognized);
        result.put("interpUpdated", interpUpdated);
        result.put("clamped", clamped);
        result.put("votedApplied", votedApplied);
        result.put("poiWhitelistSize", whitelist.size());
        result.put("maxBatch", maxBatch);
        result.put("regionHint", regionHint);
        result.put("regionLat", region.hasCenter ? region.lat : null);
        result.put("regionLng", region.hasCenter ? region.lng : null);
        result.put("maxOffsetKm", maxOffsetKm);
        result.put("landmarks", landmarkRows);
        return result;
    }

    @Override
    public Map<String, Object> confirmAllPendingEstimated(Long albumId) {
        if (albumId == null) {
            throw new ServiceException("相册ID不能为空");
        }
        List<BizPhoto> photos = photoService.list(new LambdaQueryWrapper<BizPhoto>()
                .eq(BizPhoto::getAlbumId, albumId)
                .eq(BizPhoto::getDeleted, AlbumDeleted.NORMAL)
                .isNotNull(BizPhoto::getLatitude)
                .isNotNull(BizPhoto::getLongitude)
                .orderByAsc(BizPhoto::getShootTime)
                .orderByAsc(BizPhoto::getPhotoId));
        if (photos == null || photos.isEmpty()) {
            throw new ServiceException("该相册没有可确认的定位点");
        }
        int confirmed = 0;
        int skipped = 0;
        Date now = new Date();
        for (BizPhoto photo : photos) {
            if (!PhotoLocationSource.isFallback(photo.getLocationSource())) {
                skipped++;
                continue;
            }
            if (PhotoLocationSource.isConfirmedFallback(photo)) {
                skipped++;
                continue;
            }
            photo.setLocationConfidence(BigDecimal.ONE);
            photo.setUpdateTime(now);
            PhotoFieldUtils.clamp(photo);
            if (photoService.updateById(photo)) {
                confirmed++;
            }
        }
        if (confirmed == 0) {
            boolean alreadyAllConfirmed = false;
            for (BizPhoto photo : photos) {
                if (PhotoLocationSource.isConfirmedFallback(photo)) {
                    alreadyAllConfirmed = true;
                    break;
                }
            }
            // 点已确认过但轨迹草稿未转正时，允许再次走 promote；真正无可确认点才报错
            if (!alreadyAllConfirmed) {
                throw new ServiceException("没有待确认的估计点（区/AI/估）。可能已全部确认，或请先区域定位/AI 识别");
            }
        }
        try {
            fillMissingByTimeInterp(albumId);
        } catch (Exception e) {
            log.debug("time interp after confirm all: {}", e.getMessage());
        }
        try {
            albumService.refreshAlbumStats(albumId);
        } catch (Exception e) {
            log.debug("refresh album stats after confirm all: {}", e.getMessage());
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("confirmed", confirmed);
        result.put("skipped", skipped);
        result.put("albumId", albumId);
        return result;
    }

    private boolean canApplyAiLandmark(BizPhoto photo) {
        if (photo == null) {
            return false;
        }
        // 已确认上主轨迹的估计点视为正式点，不再被 AI 覆盖
        if (PhotoLocationSource.isConfirmedFallback(photo)) {
            return false;
        }
        boolean hasCoords = photo.getLatitude() != null && photo.getLongitude() != null;
        String source = photo.getLocationSource();
        if (!hasCoords) {
            return true;
        }
        return PhotoLocationSource.REGION_CENTER.equals(source)
                || PhotoLocationSource.AI_LANDMARK.equals(source)
                || PhotoLocationSource.TIME_INTERP.equals(source);
    }

    /**
     * 按相册粗定位中心动态拉取周边 POI 白名单（不写死某个景区）。
     */
    private List<WhitelistPoi> loadRegionPoiWhitelist(RegionAnchor region,
                                                      AlbumProperties.AiLandmarkConfig aiCfg,
                                                      double maxOffsetKm) {
        List<WhitelistPoi> out = new ArrayList<WhitelistPoi>();
        if (region == null || !region.hasCenter) {
            return out;
        }
        int radius = aiCfg.getPoiRadiusMeters() > 0 ? aiCfg.getPoiRadiusMeters() : 800;
        int maxCount = aiCfg.getPoiMaxCount() > 0 ? aiCfg.getPoiMaxCount() : 40;
        double maxKm = Math.max(maxOffsetKm, radius / 1000.0);
        Map<String, WhitelistPoi> byKey = new LinkedHashMap<String, WhitelistPoi>();

        // 1) 风景名胜类周边
        try {
            List<Map<String, Object>> scenic = amapPlaceSearchService.searchAround(
                    region.lng, region.lat, null, radius, "110000|110200|110201|110202", 25);
            mergeWhitelist(byKey, scenic, region, maxKm);
        } catch (Exception e) {
            log.debug("load scenic POI whitelist failed: {}", e.getMessage());
        }
        // 2) 用景区名再搜一轮周边（覆盖子景点）
        String kw = firstNonEmpty(region.address, region.albumName);
        if (StringUtils.isNotEmpty(kw)) {
            try {
                List<Map<String, Object>> named = amapPlaceSearchService.searchAround(
                        region.lng, region.lat, kw, radius, null, 25);
                mergeWhitelist(byKey, named, region, maxKm);
            } catch (Exception e) {
                log.debug("load named POI whitelist failed: {}", e.getMessage());
            }
            try {
                List<Map<String, Object>> text = amapPlaceSearchService.search(kw, region.city, 15, true);
                mergeWhitelist(byKey, text, region, maxKm * 1.2);
            } catch (Exception e) {
                log.debug("load text POI whitelist failed: {}", e.getMessage());
            }
        }
        // 确保主景区本身在白名单
        if (StringUtils.isNotEmpty(region.address)) {
            WhitelistPoi main = new WhitelistPoi();
            main.id = "region-main";
            main.name = region.address;
            main.address = region.address;
            main.city = region.city;
            main.district = region.district;
            main.lat = region.lat;
            main.lng = region.lng;
            byKey.putIfAbsent(normalizePoiKey(main.name), main);
        }

        out.addAll(byKey.values());
        if (out.size() > maxCount) {
            return new ArrayList<WhitelistPoi>(out.subList(0, maxCount));
        }
        return out;
    }

    private void mergeWhitelist(Map<String, WhitelistPoi> byKey, List<Map<String, Object>> pois,
                                RegionAnchor region, double maxKm) {
        if (pois == null || pois.isEmpty()) {
            return;
        }
        for (Map<String, Object> poi : pois) {
            String name = asString(poi.get("name"));
            Object wgsLat = poi.get("wgsLat");
            Object wgsLng = poi.get("wgsLng");
            if (StringUtils.isEmpty(name) || wgsLat == null || wgsLng == null) {
                continue;
            }
            double lat = ((Number) wgsLat).doubleValue();
            double lng = ((Number) wgsLng).doubleValue();
            double d = GeoDistanceUtils.haversineKm(region.lat, region.lng, lat, lng);
            if (d > maxKm) {
                continue;
            }
            String key = normalizePoiKey(name);
            if (byKey.containsKey(key)) {
                continue;
            }
            WhitelistPoi w = new WhitelistPoi();
            w.id = asString(poi.get("id"));
            w.name = name;
            w.address = asString(poi.get("address"));
            w.city = asString(poi.get("cityname"));
            w.district = asString(poi.get("adname"));
            w.lat = lat;
            w.lng = lng;
            byKey.put(key, w);
        }
    }

    private WhitelistPoi matchWhitelistPoi(String query, List<WhitelistPoi> whitelist) {
        if (StringUtils.isEmpty(query) || whitelist == null || whitelist.isEmpty()) {
            return null;
        }
        String q = normalizePoiKey(query);
        WhitelistPoi best = null;
        int bestScore = 0;
        for (WhitelistPoi p : whitelist) {
            String n = normalizePoiKey(p.name);
            int score = 0;
            if (n.equals(q) || q.equals(n)) {
                score = 100;
            } else if (n.contains(q) || q.contains(n)) {
                score = 80 + Math.min(n.length(), q.length());
            } else if (shareChars(n, q) >= 2 && (n.length() <= 8 || q.length() <= 8)) {
                score = 40 + shareChars(n, q);
            }
            if (score > bestScore) {
                bestScore = score;
                best = p;
            }
        }
        return bestScore >= 40 ? best : null;
    }

    private WhitelistPoi voteWhitelistPoi(List<AiRecognizeDraft> drafts, int minVotes) {
        if (drafts == null || drafts.isEmpty()) {
            return null;
        }
        Map<String, Integer> votes = new HashMap<String, Integer>();
        Map<String, WhitelistPoi> map = new HashMap<String, WhitelistPoi>();
        for (AiRecognizeDraft d : drafts) {
            if (d.matched == null) {
                continue;
            }
            String key = normalizePoiKey(d.matched.name);
            Integer c = votes.get(key);
            votes.put(key, c == null ? 1 : c + 1);
            map.put(key, d.matched);
        }
        String bestKey = null;
        int bestN = 0;
        for (Map.Entry<String, Integer> e : votes.entrySet()) {
            if (e.getValue() != null && e.getValue() > bestN) {
                bestN = e.getValue();
                bestKey = e.getKey();
            }
        }
        if (bestKey == null || bestN < minVotes) {
            return null;
        }
        return map.get(bestKey);
    }

    private static String normalizePoiKey(String name) {
        if (name == null) {
            return "";
        }
        return name.trim()
                .replace(" ", "")
                .replace("　", "")
                .toLowerCase();
    }

    private static int shareChars(String a, String b) {
        if (a == null || b == null) {
            return 0;
        }
        int n = 0;
        for (int i = 0; i < a.length(); i++) {
            char c = a.charAt(i);
            if (b.indexOf(c) >= 0) {
                n++;
            }
        }
        return n;
    }

    /**
     * 从相册名 + 区域粗定位照片提取锚定信息。
     * 优先用景点名（地坛公园）重新地理编码/地点搜索得到精确中心，而不是散落点的平均值。
     */
    private RegionAnchor buildRegionAnchor(Long albumId, List<BizPhoto> photos) {
        RegionAnchor a = new RegionAnchor();
        try {
            com.sq.bus.domain.BizAlbum album = albumService.getById(albumId);
            if (album != null) {
                a.albumName = trimToNull(album.getAlbumName());
            }
        } catch (Exception e) {
            log.debug("load album for region hint: {}", e.getMessage());
        }

        String province = null;
        String city = null;
        String district = null;
        String address = null;
        // 投票选最常见的 address（区域定位写入的景点名）
        Map<String, Integer> addressVotes = new HashMap<String, Integer>();
        double sumLat = 0;
        double sumLng = 0;
        int n = 0;
        for (BizPhoto p : photos) {
            if (!PhotoLocationSource.REGION_CENTER.equals(p.getLocationSource())) {
                continue;
            }
            if (province == null) {
                province = trimToNull(p.getProvince());
            }
            if (city == null) {
                city = trimToNull(p.getCity());
            }
            if (district == null) {
                district = trimToNull(p.getDistrict());
            }
            String addr = trimToNull(p.getAddress());
            if (addr != null) {
                Integer c = addressVotes.get(addr);
                addressVotes.put(addr, c == null ? 1 : c + 1);
            }
            if (p.getLatitude() != null && p.getLongitude() != null) {
                sumLat += p.getLatitude().doubleValue();
                sumLng += p.getLongitude().doubleValue();
                n++;
            }
        }
        if (addressVotes.isEmpty()) {
            for (BizPhoto p : photos) {
                if (!PhotoLocationSource.isFallback(p.getLocationSource())) {
                    continue;
                }
                String addr = trimToNull(p.getAddress());
                if (addr != null) {
                    Integer c = addressVotes.get(addr);
                    addressVotes.put(addr, c == null ? 1 : c + 1);
                }
                if (province == null) {
                    province = trimToNull(p.getProvince());
                }
                if (city == null) {
                    city = trimToNull(p.getCity());
                }
                if (district == null) {
                    district = trimToNull(p.getDistrict());
                }
                if (p.getLatitude() != null && p.getLongitude() != null && n == 0) {
                    // 仅在没有区域点时累计
                }
            }
            if (n == 0) {
                for (BizPhoto p : photos) {
                    if (p.getLatitude() == null || p.getLongitude() == null) {
                        continue;
                    }
                    if (!PhotoLocationSource.isFallback(p.getLocationSource())) {
                        continue;
                    }
                    sumLat += p.getLatitude().doubleValue();
                    sumLng += p.getLongitude().doubleValue();
                    n++;
                }
            }
        }
        address = topVoted(addressVotes);
        a.province = province;
        a.city = city;
        a.district = district;
        a.address = address;

        if (n > 0) {
            a.lat = sumLat / n;
            a.lng = sumLng / n;
            a.hasCenter = true;
        }

        // 用景点名重新锚定精确中心（解决只有街道名/散点均值偏移）
        String poiQuery = firstNonEmpty(address, a.albumName);
        if (StringUtils.isNotEmpty(poiQuery)) {
            try {
                List<Map<String, Object>> pois = amapPlaceSearchService.search(poiQuery, city, 8, true);
                LandmarkGeo best = pickNearestPoi(pois, a.hasCenter ? a : null, 5.0);
                if (best == null && pois != null && !pois.isEmpty()) {
                    Map<String, Object> first = pois.get(0);
                    Object wgsLat = first.get("wgsLat");
                    Object wgsLng = first.get("wgsLng");
                    if (wgsLat != null && wgsLng != null) {
                        best = new LandmarkGeo(((Number) wgsLat).doubleValue(), ((Number) wgsLng).doubleValue(),
                                asString(first.get("name")), asString(first.get("address")),
                                asString(first.get("cityname")), asString(first.get("adname")), false);
                    }
                }
                if (best != null) {
                    a.lat = best.lat;
                    a.lng = best.lng;
                    a.hasCenter = true;
                    if (StringUtils.isNotEmpty(best.name)) {
                        a.address = best.name;
                        address = best.name;
                    }
                    if (StringUtils.isEmpty(a.city) && StringUtils.isNotEmpty(best.city)) {
                        a.city = best.city;
                    }
                    if (StringUtils.isEmpty(a.district) && StringUtils.isNotEmpty(best.district)) {
                        a.district = best.district;
                    }
                }
            } catch (Exception e) {
                log.debug("resolve region POI center failed: {}", e.getMessage());
            }
        }

        String joined = joinRegion(null, a.province, a.city, a.district);
        StringBuilder hint = new StringBuilder();
        if (StringUtils.isNotEmpty(joined)) {
            hint.append(joined);
        }
        if (StringUtils.isNotEmpty(address)) {
            if (hint.length() == 0) {
                hint.append(address);
            } else if (!hint.toString().contains(address)) {
                hint.append(' ').append(address);
            }
        }
        if (StringUtils.isNotEmpty(a.albumName) && (hint.length() == 0 || !hint.toString().contains(a.albumName))) {
            if (hint.length() > 0) {
                hint.append(' ');
            }
            hint.append(a.albumName);
        }
        a.hint = hint.length() == 0 ? null : hint.toString().trim();
        a.address = address;
        return a;
    }

    private static String topVoted(Map<String, Integer> votes) {
        if (votes == null || votes.isEmpty()) {
            return null;
        }
        String best = null;
        int bestN = -1;
        for (Map.Entry<String, Integer> e : votes.entrySet()) {
            if (e.getValue() != null && e.getValue() > bestN) {
                bestN = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }

    private static String firstNonEmpty(String a, String b) {
        if (StringUtils.isNotEmpty(a)) {
            return a;
        }
        if (StringUtils.isNotEmpty(b)) {
            return b;
        }
        return null;
    }

    private LandmarkGeo resolveLandmarkGeo(String query, String place, RegionAnchor region,
                                           double maxOffsetKm, Map<String, LandmarkGeo> cache) {
        String regionHint = region == null ? null : region.hint;
        String cacheKey = (query + "|" + place + "|" + regionHint + "|" + maxOffsetKm).toLowerCase();
        if (cache.containsKey(cacheKey)) {
            return cache.get(cacheKey);
        }
        String city = region != null && StringUtils.isNotEmpty(region.city)
                ? region.city
                : extractCityHint(place, regionHint);

        LandmarkGeo geo = null;
        // 1) 带区域前缀搜索，并限制在城市内
        String scopedQuery = query;
        if (StringUtils.isNotEmpty(regionHint) && !query.contains(regionHint)
                && (region == null || StringUtils.isEmpty(region.address) || !query.contains(region.address))) {
            String prefix = StringUtils.isNotEmpty(region.address) ? region.address : regionHint;
            scopedQuery = prefix + " " + query;
        }
        try {
            List<Map<String, Object>> pois = amapPlaceSearchService.search(scopedQuery, city, 10, true);
            geo = pickNearestPoi(pois, region, maxOffsetKm);
            if (geo == null && !scopedQuery.equals(query)) {
                pois = amapPlaceSearchService.search(query, city, 10, true);
                geo = pickNearestPoi(pois, region, maxOffsetKm);
            }
        } catch (Exception e) {
            log.debug("place search for landmark failed: {}", e.getMessage());
        }

        // 2) 地理编码（同样做距离钳制）
        if (geo == null) {
            String keyword = scopedQuery;
            if (StringUtils.isNotEmpty(city) && !keyword.contains(city)) {
                keyword = city + keyword;
            }
            try {
                Map<String, Object> g = amapPlaceSearchService.geocode(keyword);
                Object wgsLat = g.get("wgsLat");
                Object wgsLng = g.get("wgsLng");
                if (wgsLat != null && wgsLng != null) {
                    double lat = ((Number) wgsLat).doubleValue();
                    double lng = ((Number) wgsLng).doubleValue();
                    geo = new LandmarkGeo(lat, lng,
                            asString(g.get("formattedAddress")),
                            asString(g.get("formattedAddress")),
                            asString(g.get("city")),
                            asString(g.get("district")),
                            false);
                    geo = clampToRegion(geo, region, maxOffsetKm, query);
                }
            } catch (Exception e) {
                log.debug("geocode landmark failed: {}", e.getMessage());
            }
        }

        // 3) 仍无结果：直接落在区域粗定位中心
        if (geo == null && region != null && region.hasCenter) {
            geo = new LandmarkGeo(region.lat, region.lng,
                    StringUtils.isNotEmpty(region.address) ? region.address : regionHint,
                    region.address, region.city, region.district, true);
        }

        cache.put(cacheKey, geo);
        return geo;
    }

    private LandmarkGeo pickNearestPoi(List<Map<String, Object>> pois, RegionAnchor region, double maxOffsetKm) {
        if (pois == null || pois.isEmpty()) {
            return null;
        }
        LandmarkGeo bestInRange = null;
        double bestDist = Double.MAX_VALUE;
        LandmarkGeo first = null;
        for (Map<String, Object> poi : pois) {
            Object wgsLat = poi.get("wgsLat");
            Object wgsLng = poi.get("wgsLng");
            if (wgsLat == null || wgsLng == null) {
                continue;
            }
            double lat = ((Number) wgsLat).doubleValue();
            double lng = ((Number) wgsLng).doubleValue();
            LandmarkGeo g = new LandmarkGeo(lat, lng,
                    asString(poi.get("name")),
                    asString(poi.get("address")),
                    asString(poi.get("cityname")),
                    asString(poi.get("adname")),
                    false);
            if (first == null) {
                first = g;
            }
            if (region == null || !region.hasCenter) {
                return g;
            }
            double d = GeoDistanceUtils.haversineKm(region.lat, region.lng, lat, lng);
            if (d <= maxOffsetKm && d < bestDist) {
                bestDist = d;
                bestInRange = g;
            }
        }
        if (bestInRange != null) {
            return bestInRange;
        }
        // 全部超距：钳制回区域中心，保留识别名称作 address
        if (first != null && region != null && region.hasCenter) {
            return clampToRegion(first, region, maxOffsetKm, first.name);
        }
        return first;
    }

    private LandmarkGeo clampToRegion(LandmarkGeo geo, RegionAnchor region, double maxOffsetKm, String keepName) {
        if (geo == null || region == null || !region.hasCenter) {
            return geo;
        }
        double d = GeoDistanceUtils.haversineKm(region.lat, region.lng, geo.lat, geo.lng);
        if (d <= maxOffsetKm) {
            return geo;
        }
        log.info("AI landmark clamped: {} 距区域中心 {}km > {}km，落回粗定位附近",
                keepName, String.format("%.2f", d), maxOffsetKm);
        String name = StringUtils.isNotEmpty(keepName) ? keepName
                : (StringUtils.isNotEmpty(geo.name) ? geo.name : region.address);
        return new LandmarkGeo(region.lat, region.lng, name,
                StringUtils.isNotEmpty(region.address) ? region.address : geo.address,
                region.city != null ? region.city : geo.city,
                region.district != null ? region.district : geo.district,
                true);
    }

    /** 把偏离区域过远的兜底坐标（估/AI）拉回粗定位中心附近 */
    private int clampFallbackToRegion(Long albumId, RegionAnchor region, double maxOffsetKm) {
        if (albumId == null || region == null || !region.hasCenter) {
            return 0;
        }
        List<BizPhoto> list = photoService.list(new LambdaQueryWrapper<BizPhoto>()
                .eq(BizPhoto::getAlbumId, albumId)
                .eq(BizPhoto::getDeleted, AlbumDeleted.NORMAL));
        if (list == null || list.isEmpty()) {
            return 0;
        }
        int n = 0;
        Date now = new Date();
        int spread = 0;
        for (BizPhoto photo : list) {
            if (photo.getLatitude() == null || photo.getLongitude() == null) {
                continue;
            }
            if (!PhotoLocationSource.isFallback(photo.getLocationSource())) {
                continue;
            }
            double d = GeoDistanceUtils.haversineKm(region.lat, region.lng,
                    photo.getLatitude().doubleValue(), photo.getLongitude().doubleValue());
            if (d <= maxOffsetKm) {
                continue;
            }
            double[] xy = spreadAroundAi(region.lat, region.lng, spread++,
                    photo.getPhotoId() == null ? 0L : photo.getPhotoId());
            photo.setLatitude(BigDecimal.valueOf(xy[0]).setScale(7, BigDecimal.ROUND_HALF_UP));
            photo.setLongitude(BigDecimal.valueOf(xy[1]).setScale(7, BigDecimal.ROUND_HALF_UP));
            if (StringUtils.isNotEmpty(region.address) && StringUtils.isEmpty(photo.getAddress())) {
                photo.setAddress(region.address);
            }
            photo.setUpdateTime(now);
            PhotoFieldUtils.clamp(photo);
            if (photoService.updateById(photo)) {
                n++;
            }
        }
        return n;
    }

    private File resolveImageFile(BizPhoto photo) {
        if (photo == null) {
            return null;
        }
        String thumbUrl = photo.getThumbUrl();
        if (StringUtils.isNotEmpty(thumbUrl) && thumbUrl.startsWith("/album/files/thumb/")) {
            String rel = thumbUrl.substring("/album/files/thumb/".length());
            File thumb = new File(albumProperties.getThumbPath(), rel);
            if (thumb.exists() && thumb.isFile()) {
                return thumb;
            }
        }
        if (StringUtils.isNotEmpty(photo.getFilePath())) {
            File raw = new File(photo.getFilePath());
            if (raw.exists() && raw.isFile()) {
                Integer ft = photo.getFileType();
                if (ft == null || ft != 2) {
                    return raw;
                }
            }
        }
        return null;
    }

    private String encodeImageBase64(File imageFile, int maxWidth) throws Exception {
        int width = maxWidth <= 0 ? 896 : maxWidth;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Thumbnails.of(imageFile)
                .width(width)
                .keepAspectRatio(true)
                .outputFormat("jpg")
                .outputQuality(0.82)
                .toOutputStream(baos);
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    private String extractCityHint(String place, String regionHint) {
        if (StringUtils.isNotEmpty(place)) {
            if (place.contains("市")) {
                int i = place.indexOf('市');
                return place.substring(0, Math.min(place.length(), i + 1));
            }
            return place;
        }
        if (StringUtils.isNotEmpty(regionHint) && regionHint.contains("市")) {
            int i = regionHint.indexOf('市');
            int start = Math.max(0, regionHint.lastIndexOf(' ', i));
            return regionHint.substring(start, i + 1).trim();
        }
        return null;
    }

    private void applyAiLandmark(BizPhoto photo, double lat, double lng, double confidence,
                                 LandmarkGeo geo, String landmark, String place, Date now) {
        photo.setLatitude(BigDecimal.valueOf(lat).setScale(7, BigDecimal.ROUND_HALF_UP));
        photo.setLongitude(BigDecimal.valueOf(lng).setScale(7, BigDecimal.ROUND_HALF_UP));
        photo.setLocationSource(PhotoLocationSource.AI_LANDMARK);
        photo.setLocationConfidence(BigDecimal.valueOf(confidence).setScale(3, BigDecimal.ROUND_HALF_UP));
        if (StringUtils.isNotEmpty(geo.city)) {
            photo.setCity(geo.city);
        }
        if (StringUtils.isNotEmpty(geo.district)) {
            photo.setDistrict(geo.district);
        }
        String addr = StringUtils.isNotEmpty(landmark) ? landmark
                : (StringUtils.isNotEmpty(geo.name) ? geo.name : place);
        if (StringUtils.isNotEmpty(geo.address) && StringUtils.isNotEmpty(addr)
                && !geo.address.contains(addr)) {
            photo.setAddress(addr + "（" + geo.address + "）");
        } else if (StringUtils.isNotEmpty(addr)) {
            photo.setAddress(addr);
        } else {
            photo.setAddress(geo.address);
        }
        photo.setUpdateTime(now);
        PhotoFieldUtils.clamp(photo);
    }

    private static double[] spreadAroundAi(double lat, double lng, int index, long photoId) {
        return spreadAroundBase(lat, lng, index, photoId, AI_SPREAD_MIN_M, AI_SPREAD_MAX_M);
    }

    private static final class RegionAnchor {
        private String hint;
        private String albumName;
        private String province;
        private String city;
        private String district;
        private String address;
        private double lat;
        private double lng;
        private boolean hasCenter;
    }

    private static final class WhitelistPoi {
        private String id;
        private String name;
        private String address;
        private String city;
        private String district;
        private double lat;
        private double lng;
    }

    private static final class AiRecognizeDraft {
        private BizPhoto photo;
        private String landmark;
        private String place;
        private double aiConf;
        private WhitelistPoi matched;
        private boolean fromVote;
    }

    private static final class LandmarkGeo {
        private final double lat;
        private final double lng;
        private final String name;
        private final String address;
        private final String city;
        private final String district;
        private final boolean clamped;

        private LandmarkGeo(double lat, double lng, String name, String address, String city, String district,
                            boolean clamped) {
            this.lat = lat;
            this.lng = lng;
            this.name = name;
            this.address = address;
            this.city = city;
            this.district = district;
            this.clamped = clamped;
        }
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
     * 在中心点附近按序号做确定性分散（螺旋扩大），避免估计点完全叠在一起无法拖选。
     */
    private static double[] spreadAround(double lat, double lng, int index, long photoId) {
        return spreadAroundBase(lat, lng, index, photoId, REGION_SPREAD_MIN_M, REGION_SPREAD_MAX_M);
    }

    private static double[] spreadAroundTime(double lat, double lng, int index, long photoId) {
        return spreadAroundBase(lat, lng, index, photoId, TIME_SPREAD_MIN_M, TIME_SPREAD_MAX_M);
    }

    private static double[] spreadAroundBase(double lat, double lng, int index, long photoId,
                                            double minM, double maxM) {
        if (index <= 0) {
            double angle0 = ((photoId * 37L) % 360 + 360) % 360 * Math.PI / 180.0;
            double r0 = Math.min(minM, 18.0);
            return offsetMeters(lat, lng, angle0, r0);
        }
        double golden = 2.399963229728653;
        double angle = ((photoId * 17L) % 360) * Math.PI / 180.0 + index * golden;
        double t = Math.min(1.0, Math.sqrt(index) / Math.sqrt(64.0));
        double radiusM = minM + (maxM - minM) * t;
        return offsetMeters(lat, lng, angle, radiusM);
    }

    private static double[] offsetMeters(double lat, double lng, double angle, double radiusM) {
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
        // 无权威 GPS 时，用 AI 地标作弱锚点，把仍停在区域粗定位的点往地标附近推
        if (anchors.isEmpty()) {
            for (BizPhoto p : photos) {
                if (p.getShootTime() == null || p.getLatitude() == null || p.getLongitude() == null) {
                    continue;
                }
                if (PhotoLocationSource.AI_LANDMARK.equals(p.getLocationSource())) {
                    anchors.add(p);
                }
            }
        }
        if (anchors.isEmpty()) {
            return 0;
        }

        int updated = 0;
        int spreadIndex = 0;
        Date now = new Date();
        for (BizPhoto photo : photos) {
            if (photo.getShootTime() == null) {
                continue;
            }
            boolean hasCoords = photo.getLatitude() != null && photo.getLongitude() != null;
            String source = photo.getLocationSource();
            if (hasCoords && !PhotoLocationSource.isFallback(source)) {
                continue;
            }
            // 保留已确认 / 已识别的 AI 点，不拿插值覆盖
            if (PhotoLocationSource.isConfirmedFallback(photo)
                    || PhotoLocationSource.AI_LANDMARK.equals(source)) {
                continue;
            }

            Estimate est = estimate(photo.getShootTime(), anchors, cfg);
            if (est == null) {
                continue;
            }

            // 已有蓝色「区」：只允许同景点附近的 GPS 精修，避免跨景点吸走
            if (PhotoLocationSource.REGION_CENTER.equals(source) && hasCoords) {
                double refineKm = cfg.getMaxRefineRegionKm() > 0 ? cfg.getMaxRefineRegionKm() : 3.0;
                double d = GeoDistanceUtils.haversineKm(
                        photo.getLatitude().doubleValue(), photo.getLongitude().doubleValue(),
                        est.lat, est.lng);
                if (d > refineKm) {
                    continue;
                }
            }

            double[] spread = spreadAroundTime(est.lat, est.lng, spreadIndex++,
                    photo.getPhotoId() == null ? 0L : photo.getPhotoId());
            photo.setLatitude(BigDecimal.valueOf(spread[0]).setScale(7, BigDecimal.ROUND_HALF_UP));
            photo.setLongitude(BigDecimal.valueOf(spread[1]).setScale(7, BigDecimal.ROUND_HALF_UP));
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
        double maxSpan = cfg.getMaxAnchorSpanKm() > 0 ? cfg.getMaxAnchorSpanKm() : 8.0;
        if (distKm > maxSpan) {
            // 同相册多景点：两端相距过远时不串线插值
            return null;
        }
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
