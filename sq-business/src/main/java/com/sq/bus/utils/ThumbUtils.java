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

    public static boolean createVideoThumbnail(File source, File target, int width) {
        return createVideoThumbnailDetailed(source, target, width).ok;
    }

    /**
     * 使用本机 ffmpeg 截取视频帧作为缩略图。
     * 先用最简命令（兼容手机 H.264），再回退大疆 HEVC 多策略。
     */
    public static ThumbResult createVideoThumbnailDetailed(File source, File target, int width) {
        if (source == null || !source.exists() || target == null || width <= 0) {
            return ThumbResult.fail("源文件不存在或参数无效");
        }
        if (!ExternalMediaTools.isFfmpegAvailable()) {
            return ThumbResult.fail("未找到可用 ffmpeg（当前=" + ExternalMediaTools.ffmpeg() + "）");
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return ThumbResult.fail("无法创建缩略图目录：" + parent.getAbsolutePath());
        }
        if (target.exists() && !target.delete()) {
            log.warn("无法删除旧缩略图: {}", target.getAbsolutePath());
        }

        String ffmpeg = ExternalMediaTools.ffmpeg();
        String src = source.getAbsolutePath();
        String dst = target.getAbsolutePath();
        String scaleYuv = "scale=" + width + ":-2,format=yuv420p";
        boolean djiLike = isDjiLikeName(source.getName());
        long timeoutSec = timeoutForVideo(source.length());

        List<String[]> attempts = new ArrayList<String[]>();

        // 1) 最简：手机 VID_*.mp4（H.264）优先，避免 skip_frame/map 兼容问题
        for (String ss : new String[]{"1", "0", "2", "0.5"}) {
            attempts.add(ffmpegCmd(ffmpeg,
                    "-ss", ss, "-i", src,
                    "-frames:v", "1", "-q:v", "3",
                    "-vf", scaleYuv,
                    dst));
        }

        // 2) 显式选视频流
        attempts.add(ffmpegCmd(ffmpeg,
                "-ss", "1", "-i", src,
                "-map", "0:v:0", "-an", "-sn",
                "-frames:v", "1", "-q:v", "3",
                "-vf", scaleYuv,
                "-f", "image2", dst));

        // 3) 大疆/HEVC：关键帧 + yuv420p
        String[] seekPoints = djiLike
                ? new String[]{"1", "2", "0.5", "3", "0"}
                : new String[]{"0", "1"};
        for (String ss : seekPoints) {
            attempts.add(ffmpegCmd(ffmpeg,
                    "-ss", ss, "-skip_frame", "nokey",
                    "-i", src,
                    "-map", "0:v:0", "-an", "-sn",
                    "-frames:v", "1", "-q:v", "3",
                    "-vf", scaleYuv,
                    "-f", "image2", dst));
        }

        // 4) 硬件加速兜底
        attempts.add(ffmpegCmd(ffmpeg,
                "-hwaccel", "auto",
                "-ss", "1",
                "-i", src,
                "-frames:v", "1", "-q:v", "3",
                "-vf", scaleYuv,
                dst));

        // 5) 不缩放
        attempts.add(ffmpegCmd(ffmpeg,
                "-ss", "1", "-i", src,
                "-frames:v", "1", "-q:v", "3",
                "-pix_fmt", "yuv420p",
                dst));

        ExternalMediaTools.ProcessResult last = null;
        Exception lastEx = null;
        for (String[] c : attempts) {
            try {
                last = ExternalMediaTools.run(c, timeoutSec);
                if (last.ok() && target.exists() && target.length() >= 512L) {
                    return ThumbResult.ok();
                }
                if (target.exists() && target.length() < 512L) {
                    //noinspection ResultOfMethodCallIgnored
                    target.delete();
                }
            } catch (Exception e) {
                lastEx = e;
                log.warn("视频截帧异常 file={} err={}", src, e.toString());
            }
        }
        String detail;
        if (lastEx != null) {
            detail = "无法启动 ffmpeg=" + ffmpeg + "：" + lastEx.getMessage();
        } else if (last != null) {
            detail = "ffmpeg exit=" + last.exitCode + " " + last.shortOutput();
        } else {
            detail = "无法启动 ffmpeg=" + ffmpeg;
        }
        log.warn("视频截帧失败 file={} djiLike={} {}", src, djiLike, detail);
        return ThumbResult.fail(detail);
    }

    private static boolean isDjiLikeName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        String n = name.toUpperCase(Locale.ROOT);
        return n.startsWith("DJI_") || n.contains("_DJI") || n.contains("DJI-");
    }

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

    public static final class ThumbResult {
        public final boolean ok;
        public final String error;

        private ThumbResult(boolean ok, String error) {
            this.ok = ok;
            this.error = error;
        }

        public static ThumbResult ok() {
            return new ThumbResult(true, null);
        }

        public static ThumbResult fail(String error) {
            return new ThumbResult(false, error == null ? "unknown" : error);
        }
    }
}
