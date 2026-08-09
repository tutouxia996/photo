package com.sq.bus.utils;

import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * 缩略图工具
 */
public final class ThumbUtils {

    private static final Logger log = LoggerFactory.getLogger(ThumbUtils.class);

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
            ExternalMediaTools.ProcessResult result = ExternalMediaTools.run(new String[]{
                    ExternalMediaTools.ffprobe(), "-v", "error",
                    "-show_entries", "format=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1",
                    source.getAbsolutePath()
            }, 30);
            if (!result.ok()) {
                return null;
            }
            String text = result.output.trim();
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
     * 使用本机 ffmpeg 截取视频帧作为缩略图（未安装 ffmpeg 时返回 false）
     */
    public static boolean createVideoThumbnail(File source, File target, int width) {
        if (source == null || !source.exists() || target == null || width <= 0) {
            return false;
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        // 先清掉旧的失败残留
        if (target.exists() && !target.delete()) {
            log.warn("无法删除旧缩略图: {}", target.getAbsolutePath());
        }

        String ffmpeg = ExternalMediaTools.ffmpeg();
        String src = source.getAbsolutePath();
        String dst = target.getAbsolutePath();
        String scale = "scale='min(" + width + ",iw)':-2";

        // 多策略：短视频/编码差异导致单次命令失败时回退
        String[][] attempts = new String[][]{
                // 从开头截 1 帧（短视频最稳）
                {ffmpeg, "-nostdin", "-y", "-i", src, "-frames:v", "1", "-q:v", "3", "-vf", scale, dst},
                // 尝试第 1 秒（较长视频画面更有代表性）
                {ffmpeg, "-nostdin", "-y", "-ss", "1", "-i", src, "-frames:v", "1", "-q:v", "3", "-vf", scale, dst},
                // 不做缩放，最后兜底
                {ffmpeg, "-nostdin", "-y", "-i", src, "-frames:v", "1", "-q:v", "3", dst}
        };

        ExternalMediaTools.ProcessResult last = null;
        for (String[] cmd : attempts) {
            try {
                last = ExternalMediaTools.run(cmd, 60);
                if (last.ok() && target.exists() && target.length() > 0) {
                    return true;
                }
                if (target.exists() && target.length() == 0) {
                    //noinspection ResultOfMethodCallIgnored
                    target.delete();
                }
            } catch (Exception e) {
                log.warn("视频截帧异常 file={} err={}", src, e.toString());
            }
        }
        if (last != null) {
            log.warn("视频截帧失败 file={} exit={} out={}", src, last.exitCode, last.shortOutput());
        } else {
            log.warn("视频截帧失败 file={}（无法启动 ffmpeg={}）", src, ffmpeg);
        }
        return false;
    }
}
