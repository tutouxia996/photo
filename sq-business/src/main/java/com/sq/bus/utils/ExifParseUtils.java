package com.sq.bus.utils;

import com.drew.imaging.ImageMetadataReader;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import lombok.Data;

import java.io.File;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 图片 EXIF 解析工具
 */
public final class ExifParseUtils {

    private ExifParseUtils() {
    }

    public static ExifInfo parse(File file) {
        ExifInfo info = new ExifInfo();
        if (file == null || !file.exists() || !file.isFile()) {
            return info;
        }
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(file);
            GpsDirectory gpsDirectory = metadata.getFirstDirectoryOfType(GpsDirectory.class);
            if (gpsDirectory != null) {
                GeoLocation location = gpsDirectory.getGeoLocation();
                if (location != null && !location.isZero()) {
                    info.setLatitude(BigDecimal.valueOf(location.getLatitude()).setScale(7, BigDecimal.ROUND_HALF_UP));
                    info.setLongitude(BigDecimal.valueOf(location.getLongitude()).setScale(7, BigDecimal.ROUND_HALF_UP));
                }
            }
            ExifSubIFDDirectory subIfd = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            if (subIfd != null) {
                // 仍用 DateTimeOriginal（本地墙钟），与 GPX timeOffsetHours 模型一致；
                // 叠加 SubSecTimeOriginal 提升亚秒精度，便于与密集 GPX 采样对齐。
                Date shootTime = parseDateOriginalWithSubSec(subIfd);
                if (shootTime != null) {
                    info.setShootTime(shootTime);
                }
                if (subIfd.containsTag(ExifSubIFDDirectory.TAG_FNUMBER)) {
                    info.setAperture(trim(subIfd.getDescription(ExifSubIFDDirectory.TAG_FNUMBER), 20));
                    if (info.getAperture() != null && !info.getAperture().startsWith("f")) {
                        info.setAperture(trim("f/" + info.getAperture(), 20));
                    }
                }
                if (subIfd.containsTag(ExifSubIFDDirectory.TAG_EXPOSURE_TIME)) {
                    // 部分手机（如小米）会给出 "688813/1000000000 sec"，超过库字段长度
                    info.setShutterSpeed(normalizeShutter(subIfd.getDescription(ExifSubIFDDirectory.TAG_EXPOSURE_TIME)));
                }
                if (subIfd.containsTag(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT)) {
                    Integer iso = subIfd.getInteger(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT);
                    info.setIso(iso);
                }
                if (subIfd.containsTag(ExifSubIFDDirectory.TAG_FOCAL_LENGTH)) {
                    info.setFocalLength(trim(subIfd.getDescription(ExifSubIFDDirectory.TAG_FOCAL_LENGTH), 20));
                }
                if (subIfd.containsTag(ExifSubIFDDirectory.TAG_LENS_MODEL)) {
                    info.setLensInfo(trim(subIfd.getDescription(ExifSubIFDDirectory.TAG_LENS_MODEL), 100));
                }
            }
            ExifIFD0Directory ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (ifd0 != null) {
                String make = ifd0.containsTag(ExifIFD0Directory.TAG_MAKE)
                        ? ifd0.getDescription(ExifIFD0Directory.TAG_MAKE) : null;
                String model = ifd0.containsTag(ExifIFD0Directory.TAG_MODEL)
                        ? ifd0.getDescription(ExifIFD0Directory.TAG_MODEL) : null;
                if (make != null || model != null) {
                    info.setCameraModel(trim(((make == null ? "" : make) + " " + (model == null ? "" : model)).trim(), 100));
                }
                if (info.getShootTime() == null && ifd0.containsTag(ExifIFD0Directory.TAG_DATETIME)) {
                    info.setShootTime(ifd0.getDate(ExifIFD0Directory.TAG_DATETIME));
                }
            }
        } catch (Exception ignored) {
            // 解析失败不影响主流程，字段留空
        }
        return info;
    }

    /**
     * DateTimeOriginal + SubSecTimeOriginal（若有），保持原有时区解释方式。
     */
    private static Date parseDateOriginalWithSubSec(ExifSubIFDDirectory subIfd) {
        Date base = subIfd.getDateOriginal();
        if (base == null) {
            return null;
        }
        if (!subIfd.containsTag(ExifSubIFDDirectory.TAG_SUBSECOND_TIME_ORIGINAL)) {
            return base;
        }
        try {
            String sub = subIfd.getString(ExifSubIFDDirectory.TAG_SUBSECOND_TIME_ORIGINAL);
            if (sub == null || sub.trim().isEmpty()) {
                return base;
            }
            String digits = sub.trim().replaceAll("\\D", "");
            if (digits.isEmpty()) {
                return base;
            }
            // 取最多 3 位毫秒；不足右侧补 0
            if (digits.length() > 3) {
                digits = digits.substring(0, 3);
            }
            while (digits.length() < 3) {
                digits = digits + "0";
            }
            int ms = Integer.parseInt(digits);
            // getDateOriginal 通常已是整秒；若已有毫秒则不再叠加
            if (base.getTime() % 1000L != 0L) {
                return base;
            }
            return new Date(base.getTime() + ms);
        } catch (Exception ignored) {
            return base;
        }
    }

    private static String normalizeShutter(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if (text.isEmpty()) {
            return null;
        }
        // 超长分数曝光时间压缩为可读短格式，避免 varchar(20) 截断入库失败
        if (text.contains("/")) {
            try {
                String numPart = text.toLowerCase(java.util.Locale.ROOT)
                        .replace("sec", "")
                        .replace("s", "")
                        .trim();
                int slash = numPart.indexOf('/');
                if (slash > 0) {
                    double num = Double.parseDouble(numPart.substring(0, slash).trim());
                    double den = Double.parseDouble(numPart.substring(slash + 1).trim());
                    if (den != 0) {
                        double seconds = num / den;
                        if (seconds > 0 && seconds < 1) {
                            return trim("1/" + Math.round(1.0 / seconds) + "s", 20);
                        }
                        if (seconds >= 1) {
                            return trim(String.format(java.util.Locale.ROOT, "%.1fs", seconds), 20);
                        }
                    }
                }
            } catch (Exception ignored) {
                // fall through
            }
        }
        return trim(text, 20);
    }

    private static String trim(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        if (text.isEmpty()) {
            return null;
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen);
    }

    @Data
    public static class ExifInfo {
        private Date shootTime;
        private BigDecimal latitude;
        private BigDecimal longitude;
        private String cameraModel;
        private String lensInfo;
        private String aperture;
        private String shutterSpeed;
        private Integer iso;
        private String focalLength;
    }
}
