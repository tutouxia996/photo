package com.sq.bus.utils;

import net.coobird.thumbnailator.Thumbnails;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 缩略图工具
 */
public final class ThumbUtils {

    private ThumbUtils() {
    }

    public static void createThumbnail(File source, File target, int width) throws IOException {
        if (source == null || !source.exists() || target == null || width <= 0) {
            return;
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        Thumbnails.of(source)
                .width(width)
                .keepAspectRatio(true)
                .toFile(target);
    }

    /**
     * 读取视频时长（秒），依赖本机 ffprobe；失败返回 null
     */
    public static Integer getVideoDurationSeconds(File source) {
        if (source == null || !source.exists() || !source.isFile()) {
            return null;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffprobe", "-v", "error",
                    "-show_entries", "format=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1",
                    source.getAbsolutePath()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            StringBuilder out = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    out.append(line.trim());
                }
            }
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return null;
            }
            if (process.exitValue() != 0) {
                return null;
            }
            String text = out.toString().trim();
            if (text.isEmpty()) {
                return null;
            }
            double seconds = Double.parseDouble(text);
            if (seconds < 0) {
                return null;
            }
            return (int) Math.round(seconds);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 使用本机 ffmpeg 截取视频首帧作为缩略图（未安装 ffmpeg 时返回 false）
     */
    public static boolean createVideoThumbnail(File source, File target, int width) {
        if (source == null || !source.exists() || target == null || width <= 0) {
            return false;
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg", "-y",
                    "-ss", "00:00:01",
                    "-i", source.getAbsolutePath(),
                    "-vframes", "1",
                    "-vf", "scale=" + width + ":-1",
                    target.getAbsolutePath()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(45, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0 && target.exists() && target.length() > 0;
        } catch (Exception e) {
            return false;
        }
    }
}
