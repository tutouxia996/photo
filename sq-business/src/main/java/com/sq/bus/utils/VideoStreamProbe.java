package com.sq.bus.utils;

import com.sq.bus.config.AlbumProperties;

import java.io.File;
import java.util.Locale;

/**
 * ffprobe 读取视频流宽高与帧率，用于判断是否需要生成浏览档。
 */
public final class VideoStreamProbe {

    private VideoStreamProbe() {
    }

    public static StreamInfo probe(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return null;
        }
        try {
            ExternalMediaTools.ProcessResult result = ExternalMediaTools.run(new String[]{
                    ExternalMediaTools.ffprobe(), "-v", "error",
                    "-select_streams", "v:0",
                    "-show_entries", "stream=width,height,r_frame_rate,avg_frame_rate",
                    "-of", "csv=p=0:s=x",
                    file.getAbsolutePath()
            }, 60);
            if (!result.ok() || result.output == null || result.output.trim().isEmpty()) {
                return null;
            }
            String line = result.output.trim().split("\\R")[0].trim();
            String[] parts = line.split("x");
            if (parts.length < 3) {
                return null;
            }
            int width = Integer.parseInt(parts[0].trim());
            int height = Integer.parseInt(parts[1].trim());
            double fps = parseFps(parts[2].trim());
            if (parts.length >= 4 && fps <= 0) {
                fps = parseFps(parts[3].trim());
            }
            if (width <= 0 || height <= 0) {
                return null;
            }
            StreamInfo info = new StreamInfo();
            info.width = width;
            info.height = height;
            info.fps = fps;
            return info;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 是否需要转浏览档：1080p 及以上 且 ≥30fps（可配置 album.videoProxy）。
     */
    public static boolean needsVideoProxy(File file, long fileSizeBytes, AlbumProperties.VideoProxy cfg) {
        return checkEligibility(file, fileSizeBytes, cfg).eligible;
    }

    /**
     * 不满足时返回可读原因（size / resolution / fps / probe_failed）。
     */
    public static Eligibility checkEligibility(File file, long fileSizeBytes, AlbumProperties.VideoProxy cfg) {
        if (file == null || !file.exists()) {
            return Eligibility.no("file_missing");
        }
        AlbumProperties.VideoProxy c = cfg != null ? cfg : new AlbumProperties.VideoProxy();
        long bytes = fileSizeBytes > 0 ? fileSizeBytes : file.length();
        long minBytes = c.getMinBytes();
        if (minBytes > 0 && bytes <= minBytes) {
            return Eligibility.no("size", formatGb(bytes) + " <= " + formatGb(minBytes));
        }
        StreamInfo info = probe(file);
        if (info == null) {
            return Eligibility.no("probe_failed", "ffprobe 无法读取视频流");
        }
        int minW = c.getMinWidth() > 0 ? c.getMinWidth() : 1920;
        int minH = c.getMinHeight() > 0 ? c.getMinHeight() : 1080;
        if (!isResolutionOrAbove(info.width, info.height, minW, minH)) {
            return Eligibility.no("resolution", info.summary() + " < 1080p");
        }
        int minFps = c.getMinFps() > 0 ? c.getMinFps() : 30;
        if (Math.round(info.fps) < minFps) {
            return Eligibility.no("fps", info.summary() + " < " + minFps + "fps");
        }
        return Eligibility.yes(info);
    }

    private static String formatGb(long bytes) {
        return String.format(Locale.ROOT, "%.2fGB", bytes / (1024d * 1024d * 1024d));
    }

    public static final class Eligibility {
        public final boolean eligible;
        public final String code;
        public final String detail;
        public final StreamInfo stream;

        private Eligibility(boolean eligible, String code, String detail, StreamInfo stream) {
            this.eligible = eligible;
            this.code = code;
            this.detail = detail;
            this.stream = stream;
        }

        static Eligibility yes(StreamInfo info) {
            return new Eligibility(true, "ok", info.summary(), info);
        }

        static Eligibility no(String code) {
            return no(code, code);
        }

        static Eligibility no(String code, String detail) {
            return new Eligibility(false, code, detail, null);
        }
    }

    /** 宽≥minW 或 高≥minH 视为达到目标分辨率（含竖屏 1080p、4K 等）。 */
    public static boolean isResolutionOrAbove(int width, int height, int minW, int minH) {
        return width >= minW || height >= minH;
    }

    /** @deprecated 使用 {@link #isResolutionOrAbove} */
    @Deprecated
    public static boolean is2KOrAbove(int width, int height, int minW, int minH) {
        return isResolutionOrAbove(width, height, minW, minH);
    }

    private static double parseFps(String raw) {
        if (raw == null || raw.isEmpty() || "0/0".equals(raw) || "N/A".equalsIgnoreCase(raw)) {
            return 0;
        }
        try {
            if (raw.contains("/")) {
                String[] p = raw.split("/");
                double num = Double.parseDouble(p[0].trim());
                double den = Double.parseDouble(p[1].trim());
                if (den <= 0) {
                    return 0;
                }
                return num / den;
            }
            return Double.parseDouble(raw.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    public static final class StreamInfo {
        public int width;
        public int height;
        public double fps;

        public String summary() {
            return width + "x" + height + "@" + String.format(Locale.ROOT, "%.0f", fps) + "fps";
        }
    }
}
