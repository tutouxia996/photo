package com.sq.bus.utils;

import net.coobird.thumbnailator.Thumbnails;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
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
            ProcessResult result = runProcess(new String[]{
                    "ffprobe", "-v", "error",
                    "-show_entries", "format=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1",
                    source.getAbsolutePath()
            }, 30);
            if (!result.finished || result.exitCode != 0) {
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
            ProcessResult result = runProcess(new String[]{
                    "ffmpeg", "-nostdin", "-y",
                    "-ss", "00:00:01",
                    "-i", source.getAbsolutePath(),
                    "-vframes", "1",
                    "-vf", "scale=" + width + ":-1",
                    target.getAbsolutePath()
            }, 45);
            return result.finished && result.exitCode == 0 && target.exists() && target.length() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 启动外部进程：合并并排空 stdout，避免管道缓冲区塞满导致永久阻塞；超时强杀。
     */
    private static ProcessResult runProcess(String[] command, long timeoutSeconds) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        ByteArrayOutputStream collected = new ByteArrayOutputStream();
        Thread drain = new Thread(() -> {
            try (InputStream in = process.getInputStream()) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) != -1) {
                    // 只保留前 8KB，足够解析 ffprobe 输出
                    if (collected.size() < 8192) {
                        collected.write(buf, 0, Math.min(n, 8192 - collected.size()));
                    }
                }
            } catch (IOException ignored) {
                // 进程被杀掉时读流失败属正常
            }
        }, "media-process-drain");
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
