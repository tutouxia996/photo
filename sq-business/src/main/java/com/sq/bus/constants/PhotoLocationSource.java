package com.sq.bus.constants;

import com.sq.bus.domain.BizPhoto;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 照片坐标来源。权威来源可进主轨迹/地图；兜底来源默认隔离。
 */
public final class PhotoLocationSource {

    /** 图片 EXIF GPS */
    public static final String EXIF = "exif";
    /** 视频元数据 GPS */
    public static final String VIDEO = "video";
    /** 按拍摄时间匹配 GPX 后写回（预留） */
    public static final String GPX_MATCH = "gpx_match";
    /** 用户手工标点 */
    public static final String MANUAL = "manual";
    /** 同相册权威点按时间插值（兜底） */
    public static final String TIME_INTERP = "time_interp";
    /** AI 地标识别（兜底，后续） */
    public static final String AI_LANDMARK = "ai_landmark";
    /** 仅范围中心占位（兜底，后续） */
    public static final String REGION_CENTER = "region_center";

    private static final Set<String> AUTHORITATIVE = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            EXIF, VIDEO, GPX_MATCH, MANUAL)));

    private static final Set<String> FALLBACK = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            TIME_INTERP, AI_LANDMARK, REGION_CENTER)));

    private PhotoLocationSource() {
    }

    public static boolean isAuthoritative(String source) {
        // 历史数据：有坐标但无来源，视为权威（EXIF/视频时代入库）
        if (source == null || source.trim().isEmpty()) {
            return true;
        }
        return AUTHORITATIVE.contains(source.trim());
    }

    public static boolean isFallback(String source) {
        return source != null && FALLBACK.contains(source.trim());
    }

    /** 是否可作为主轨迹/默认地图锚点 */
    public static boolean isAuthoritativeGps(BizPhoto photo) {
        if (photo == null || photo.getLatitude() == null || photo.getLongitude() == null) {
            return false;
        }
        return isAuthoritative(photo.getLocationSource());
    }

    public static void applyDeviceGps(BizPhoto photo, BigDecimal latitude, BigDecimal longitude, int fileType) {
        if (photo == null) {
            return;
        }
        if (latitude != null && longitude != null) {
            photo.setLatitude(latitude);
            photo.setLongitude(longitude);
            photo.setLocationSource(fileType == 2 ? VIDEO : EXIF);
            photo.setLocationConfidence(BigDecimal.ONE);
            return;
        }
        photo.setLatitude(null);
        photo.setLongitude(null);
        photo.setLocationSource(null);
        photo.setLocationConfidence(null);
    }

    public static void applyManualGps(BizPhoto photo) {
        if (photo == null || photo.getLatitude() == null || photo.getLongitude() == null) {
            return;
        }
        if (photo.getLocationSource() == null || photo.getLocationSource().trim().isEmpty()
                || isFallback(photo.getLocationSource())) {
            photo.setLocationSource(MANUAL);
            if (photo.getLocationConfidence() == null) {
                photo.setLocationConfidence(BigDecimal.ONE);
            }
        }
    }
}
