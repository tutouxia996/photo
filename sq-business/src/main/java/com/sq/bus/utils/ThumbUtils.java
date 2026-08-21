package com.sq.bus.utils;

import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
     * 使用本机 ffmpeg 截取视频帧作为缩略图。
     * <p>
     * 针对大疆等 HEVC/H.265（含 Main10）做了多策略回退：
     * 强制转 8bit yuv420p（JPEG 必需）、跳过片头黑帧、仅取关键帧加速解码等。
     */
    public static boolean createVideoThumbnail(File source, File target, int width) {
        if (source == null || !source.exists() || target == null || width <= 0) {
            return false;
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        if (target.exists() && !target.delete()) {
            log.warn("无法删除旧缩略图: {}", target.getAbsolutePath());
        }

        String ffmpeg = ExternalMediaTools.ffmpeg();
        String src = source.getAbsolutePath();
        String dst = target.getAbsolutePath();
        // JPEG 只能 8bit：HEVC Main10 必须 format=yuv420p，否则常见截帧失败
        String scaleYuv = "scale=" + width + ":-2:force_original_aspect_ratio=decrease,format=yuv420p";
        String scaleOnly = "scale=" + width + ":-2:force_original_aspect_ratio=decrease";
        boolean djiLike = isDjiLikeName(source.getName());
        long timeoutSec = timeoutForVideo(source.length());

        List<String[]> attempts = new ArrayList<String[]>();

        // 大疆片头常黑/绿屏：优先从 1s、2s、0.5s 取帧
        String[] seekPoints = djiLike
                ? new String[]{"1", "2", "0.5", "3", "0"}
                : new String[]{"0", "1", "2", "0.5"};

        for (String ss : seekPoints) {
            // 快进到关键帧再解码（HEVC 更快），再转 8bit
            attempts.add(ffmpegCmd(ffmpeg,
                    "-ss", ss, "-skip_frame", "nokey",
                    "-i", src,
                    "-map", "0:v:0", "-an", "-sn",
                    "-frames:v", "1", "-q:v", "3",
                    "-vf", scaleYuv,
                    "-f", "image2", dst));
            // 精确解码（不跳非关键帧）
            attempts.add(ffmpegCmd(ffmpeg,
                    "-ss", ss,
                    "-i", src,
                    "-map", "0:v:0", "-an", "-sn",
                    "-frames:v", "1", "-q:v", "3",
                    "-vf", scaleYuv,
                    "-f", "image2", dst));
        }

        // 不 seek：从文件头解（短视频 / 某些封装）
        attempts.add(ffmpegCmd(ffmpeg,
                "-i", src,
                "-map", "0:v:0", "-an", "-sn",
                "-frames:v", "1", "-q:v", "3",
                "-vf", scaleYuv,
                "-f", "image2", dst));

        // 硬件加速尝试（部分机器 HEVC 软解很慢/失败）
        attempts.add(ffmpegCmd(ffmpeg,
                "-hwaccel", "auto",
                "-ss", "1",
                "-i", src,
                "-map", "0:v:0", "-an",
                "-frames:v", "1", "-q:v", "3",
                "-vf", scaleYuv,
                "-f", "image2", dst));

        // 不做 map，兼容奇怪封装
        attempts.add(ffmpegCmd(ffmpeg,
                "-ss", "1",
                "-i", src,
                "-frames:v", "1", "-q:v", "3",
                "-vf", scaleOnly + ",format=yuv420p",
                dst));

        // 最后兜底：不缩放
        attempts.add(ffmpegCmd(ffmpeg,
                "-ss", "1",
                "-i", src,
                "-map", "0:v:0", "-an",
                "-frames:v", "1", "-q:v", "3",
                "-pix_fmt", "yuv420p",
                dst));

        ExternalMediaTools.ProcessResult last = null;
        for (String[] c : attempts) {
            try {
                last = ExternalMediaTools.run(c, timeoutSec);
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
            log.warn("视频截帧失败 file={} djiLike={} exit={} out={}",
                    src, djiLike, last.exitCode, last.shortOutput());
        } else {
            log.warn("视频截帧失败 file={}（无法启动 ffmpeg={}）", src, ffmpeg);
        }
        return false;
    }

    private static boolean isDjiLikeName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        String n = name.toUpperCase(Locale.ROOT);
        return n.startsWith("DJI_") || n.contains("_DJI") || n.contains("DJI-");
    }

    /** 大文件 / 4K HEVC 截帧更慢 */
    private static long timeoutForVideo(long bytes) {
        if (bytes >= 2L * 1024 * 1024 * 1024) {
            return 180;
        }
        if (bytes >= 512L * 1024 * 1024) {
            return 120;
        }
        return 90;
    }

    private static String[] ffmpegCmd(String ffmpeg, String... args) {
        List<String> list = new ArrayList<String>(8 + args.length);
        list.add(ffmpeg);
        list.add("-nostdin");
        list.add("-hide_banner");
        list.add("-loglevel");
        list.add("error");
        list.add("-y");
        for (String a : args) {
            list.add(a);
        }
        return list.toArray(new String[0]);
    }
}
