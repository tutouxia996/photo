package com.sq.bus.utils;

import lombok.Data;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 视频元数据解析（ffprobe）：定位、拍摄时间。失败返回空字段，不抛错。
 */
public final class VideoMetaUtils {

    private static final Pattern ISO6709 = Pattern.compile(
            "([+-]\\d+(?:\\.\\d+)?)([+-]\\d+(?:\\.\\d+)?)(?:[+-]\\d+(?:\\.\\d+)?)?/?"
    );

    private VideoMetaUtils() {
    }

    public static MetaInfo parse(File file) {
        MetaInfo info = new MetaInfo();
        if (file == null || !file.exists() || !file.isFile()) {
            return info;
        }
        try {
            ProcessResult result = runProcess(new String[]{
                    "ffprobe", "-v", "error",
                    "-show_entries",
                    "format_tags=location,location-eng,com.apple.quicktime.location.ISO6709,creation_time:"
                            + "stream_tags=location,location-eng,com.apple.quicktime.location.ISO6709,creation_time",
                    "-of", "default",
                    file.getAbsolutePath()
            }, 30);
            if (!result.finished || result.exitCode != 0 || result.output.isEmpty()) {
                return info;
            }
            applyTags(info, result.output);
        } catch (Exception ignored) {
            // 未安装 ffprobe 或解析失败不影响主流程
        }
        return info;
    }

    private static void applyTags(MetaInfo info, String output) {
        String location = null;
        String creationTime = null;
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
            String keyLower = key.toLowerCase(Locale.ROOT);
            if (location == null && (
                    "location".equals(keyLower)
                            || "location-eng".equals(keyLower)
                            || "com.apple.quicktime.location.iso6709".equals(keyLower))) {
                location = value;
            } else if (creationTime == null && "creation_time".equals(keyLower)) {
                creationTime = value;
            }
        }
        fillLocation(info, location);
        if (creationTime != null) {
            Date shootTime = parseCreationTime(creationTime);
            if (shootTime != null) {
                info.setShootTime(shootTime);
            }
        }
    }

    private static void fillLocation(MetaInfo info, String raw) {
        if (raw == null || raw.isEmpty()) {
            return;
        }
        Matcher matcher = ISO6709.matcher(raw.trim());
        if (!matcher.find()) {
            return;
        }
        try {
            double lat = Double.parseDouble(matcher.group(1));
            double lng = Double.parseDouble(matcher.group(2));
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
        // 常见：2024-01-01T12:00:00.000000Z / 2024-01-01 12:00:00
        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm:ss"
        };
        for (String pattern : patterns) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.ROOT);
                if (pattern.endsWith("'Z'")) {
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
        }
        String frac = text.substring(dot + 1, zone).replaceAll("\\D", "");
        if (frac.length() <= 3) {
            return text;
        }
        return text.substring(0, dot + 1) + frac.substring(0, 3) + text.substring(zone);
    }

    private static ProcessResult runProcess(String[] command, long timeoutSeconds)
            throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        ByteArrayOutputStream collected = new ByteArrayOutputStream();
        Thread drain = new Thread(() -> {
            try (InputStream in = process.getInputStream()) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) != -1) {
                    if (collected.size() < 32768) {
                        collected.write(buf, 0, Math.min(n, 32768 - collected.size()));
                    }
                }
            } catch (IOException ignored) {
                // 进程被杀掉时读流失败属正常
            }
        }, "video-meta-drain");
        drain.setDaemon(true);
        drain.start();

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
        }
        try {
            drain.join(2000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        int exitCode = finished ? process.exitValue() : -1;
        String output = new String(collected.toByteArray(), Charset.defaultCharset());
        return new ProcessResult(finished, exitCode, output);
    }

    @Data
    public static class MetaInfo {
        private Date shootTime;
        private BigDecimal latitude;
        private BigDecimal longitude;
    }

    private static final class ProcessResult {
        private final boolean finished;
        private final int exitCode;
        private final String output;

        private ProcessResult(boolean finished, int exitCode, String output) {
            this.finished = finished;
            this.exitCode = exitCode;
            this.output = output == null ? "" : output;
        }
    }
}
