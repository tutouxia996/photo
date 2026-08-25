package com.sq.bus.utils;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 视频元数据解析（ffprobe）：定位、拍摄时间。失败返回空字段，不抛错。
 */
public final class VideoMetaUtils {

    private static final Logger log = LoggerFactory.getLogger(VideoMetaUtils.class);

    /** ISO6709：+39.9042+116.4074/ 或带海拔 */
    private static final Pattern ISO6709 = Pattern.compile(
            "([+-]\\d+(?:\\.\\d+)?)([+-]\\d+(?:\\.\\d+)?)(?:[+-]\\d+(?:\\.\\d+)?)?/?"
    );
    /** 逗号/空格分隔：39.9042,116.4074 */
    private static final Pattern LAT_LNG_COMMA = Pattern.compile(
            "(-?\\d+(?:\\.\\d+)?)\\s*[,\\s]\\s*(-?\\d+(?:\\.\\d+)?)"
    );

    private VideoMetaUtils() {
    }

    public static MetaInfo parse(File file) {
        MetaInfo info = new MetaInfo();
        if (file == null || !file.exists() || !file.isFile()) {
            return info;
        }
        try {
            String ffprobe = ExternalMediaTools.ffprobe();
            // JSON 更稳：覆盖 format/stream tags（安卓/苹果/大疆常见 location）
            ExternalMediaTools.ProcessResult jsonResult = ExternalMediaTools.run(new String[]{
                    ffprobe, "-v", "error",
                    "-print_format", "json",
                    "-show_format",
                    "-show_streams",
                    file.getAbsolutePath()
            }, 45);
            if (jsonResult.ok() && jsonResult.output != null && !jsonResult.output.trim().isEmpty()) {
                applyJson(info, jsonResult.output);
            }
            // 回退：旧式 TAG 文本（部分封装仅在此露出）
            if (info.getLatitude() == null || info.getLongitude() == null || info.getShootTime() == null) {
                ExternalMediaTools.ProcessResult tagResult = ExternalMediaTools.run(new String[]{
                        ffprobe, "-v", "error",
                        "-show_entries",
                        "format_tags:stream_tags",
                        "-of", "default",
                        file.getAbsolutePath()
                }, 45);
                if (tagResult.ok() && tagResult.output != null && !tagResult.output.isEmpty()) {
                    applyTags(info, tagResult.output);
                }
            }
        } catch (Exception e) {
            log.debug("视频元数据解析失败 file={} err={}", file.getAbsolutePath(), e.toString());
        }
        return info;
    }

    private static void applyJson(MetaInfo info, String jsonText) {
        try {
            JSONObject root = JSON.parseObject(jsonText);
            if (root == null) {
                return;
            }
            JSONObject format = root.getJSONObject("format");
            if (format != null) {
                applyTagMap(info, format.getJSONObject("tags"));
            }
            JSONArray streams = root.getJSONArray("streams");
            if (streams != null) {
                for (int i = 0; i < streams.size(); i++) {
                    JSONObject stream = streams.getJSONObject(i);
                    if (stream != null) {
                        applyTagMap(info, stream.getJSONObject("tags"));
                    }
                }
            }
        } catch (Exception e) {
            log.debug("ffprobe JSON 解析失败：{}", e.toString());
        }
    }

    private static void applyTagMap(MetaInfo info, JSONObject tags) {
        if (tags == null || tags.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : tags.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            applyOneTag(info, entry.getKey(), String.valueOf(entry.getValue()));
        }
    }

    private static void applyTags(MetaInfo info, String output) {
        for (String rawLine : output.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            String key;
            String value;
            if (line.regionMatches(true, 0, "TAG:", 0, 4)) {
                int eq = line.indexOf('=');
                if (eq <= 4) {
                    continue;
                }
                key = line.substring(4, eq).trim();
                value = line.substring(eq + 1).trim();
            } else {
                int eq = line.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                key = line.substring(0, eq).trim();
                value = line.substring(eq + 1).trim();
            }
            if (value.isEmpty()) {
                continue;
            }
            applyOneTag(info, key, value);
        }
    }

    private static void applyOneTag(MetaInfo info, String key, String value) {
        if (key == null || value == null) {
            return;
        }
        String keyLower = key.toLowerCase(Locale.ROOT).trim();
        String text = value.trim();
        if (text.isEmpty()) {
            return;
        }
        if (info.getLatitude() == null && isLocationKey(keyLower)) {
            fillLocation(info, text);
        }
        if (info.getShootTime() == null && isTimeKey(keyLower)) {
            Date shootTime = parseCreationTime(text);
            if (shootTime != null) {
                info.setShootTime(shootTime);
            }
        }
    }

    private static boolean isLocationKey(String keyLower) {
        return "location".equals(keyLower)
                || "location-eng".equals(keyLower)
                || "com.apple.quicktime.location.iso6709".equals(keyLower)
                || keyLower.contains("location")
                || keyLower.endsWith("iso6709")
                || "©xyz".equals(keyLower)
                || "xyz".equals(keyLower);
    }

    private static boolean isTimeKey(String keyLower) {
        return "creation_time".equals(keyLower)
                || "com.apple.quicktime.creationdate".equals(keyLower)
                || "date".equals(keyLower)
                || "datetime".equals(keyLower)
                || "datetimeoriginal".equals(keyLower);
    }

    private static void fillLocation(MetaInfo info, String raw) {
        if (raw == null || raw.isEmpty() || info.getLatitude() != null) {
            return;
        }
        String text = raw.trim();
        Matcher iso = ISO6709.matcher(text);
        if (iso.find()) {
            setLatLng(info, iso.group(1), iso.group(2));
            return;
        }
        Matcher comma = LAT_LNG_COMMA.matcher(text);
        if (comma.find()) {
            setLatLng(info, comma.group(1), comma.group(2));
        }
    }

    private static void setLatLng(MetaInfo info, String latText, String lngText) {
        try {
            double lat = Double.parseDouble(latText);
            double lng = Double.parseDouble(lngText);
            if (lat == 0D && lng == 0D) {
                return;
            }
            if (lat < -90D || lat > 90D || lng < -180D || lng > 180D) {
                return;
            }
            info.setLatitude(BigDecimal.valueOf(lat).setScale(7, BigDecimal.ROUND_HALF_UP));
            info.setLongitude(BigDecimal.valueOf(lng).setScale(7, BigDecimal.ROUND_HALF_UP));
        } catch (Exception ignored) {
            // 非法坐标忽略
        }
    }

    private static Date parseCreationTime(String raw) {
        String text = raw.trim();
        if (text.isEmpty()) {
            return null;
        }
        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy:MM:dd HH:mm:ss"
        };
        for (String pattern : patterns) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.ROOT);
                if (pattern.contains("'Z'") || pattern.endsWith("XXX")) {
                    sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                }
                return sdf.parse(normalizeFractionalSeconds(text, pattern));
            } catch (ParseException ignored) {
                // try next
            }
        }
        return null;
    }

    /** 将微秒小数截到毫秒，适配 SSS 格式 */
    private static String normalizeFractionalSeconds(String text, String pattern) {
        if (!pattern.contains("SSS") || !text.contains(".")) {
            return text;
        }
        int dot = text.indexOf('.');
        int zone = text.indexOf('Z', dot);
        if (zone < 0) {
            zone = text.length();
            for (int i = dot + 1; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '+' || c == '-') {
                    zone = i;
                    break;
                }
            }
        }
        String frac = text.substring(dot + 1, zone).replaceAll("\\D", "");
        if (frac.length() <= 3) {
            return text;
        }
        return text.substring(0, dot + 1) + frac.substring(0, 3) + text.substring(zone);
    }

    @Data
    public static class MetaInfo {
        private Date shootTime;
        private BigDecimal latitude;
        private BigDecimal longitude;
    }
}
