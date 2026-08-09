package com.sq.bus.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * ffmpeg / ffprobe 可执行文件定位与进程执行。
 */
public final class ExternalMediaTools {

    private static final Logger log = LoggerFactory.getLogger(ExternalMediaTools.class);

    private static volatile String ffmpegCmd = "ffmpeg";
    private static volatile String ffprobeCmd = "ffprobe";
    private static volatile boolean resolved = false;

    private ExternalMediaTools() {
    }

    /** 可由配置覆盖；传空则忽略 */
    public static synchronized void configure(String ffmpegPath, String ffprobePath) {
        if (ffmpegPath != null && !ffmpegPath.trim().isEmpty()) {
            ffmpegCmd = ffmpegPath.trim();
        }
        if (ffprobePath != null && !ffprobePath.trim().isEmpty()) {
            ffprobeCmd = ffprobePath.trim();
        }
        resolved = false;
        ensureResolved();
    }

    public static String ffmpeg() {
        ensureResolved();
        return ffmpegCmd;
    }

    public static String ffprobe() {
        ensureResolved();
        return ffprobeCmd;
    }

    private static synchronized void ensureResolved() {
        if (resolved) {
            return;
        }
        String ff = firstWorking(buildCandidates(ffmpegCmd, "ffmpeg", "ffmpeg.exe"));
        String fp = firstWorking(buildCandidates(ffprobeCmd, "ffprobe", "ffprobe.exe"));
        if (ff != null) {
            ffmpegCmd = ff;
        }
        if (fp != null) {
            ffprobeCmd = fp;
        }
        resolved = true;
        log.info("媒体工具定位：ffmpeg={} ffprobe={}", ffmpegCmd, ffprobeCmd);
        if (ff == null) {
            log.warn("未找到可用的 ffmpeg，视频截帧将失败。可配置 album.ffmpegPath 或将 ffmpeg 加入 PATH");
        }
        if (fp == null) {
            log.warn("未找到可用的 ffprobe，视频时长/定位解析可能失败。可配置 album.ffprobePath");
        }
    }

    private static List<String> buildCandidates(String preferred, String bare, String bareExe) {
        List<String> list = new ArrayList<String>();
        if (preferred != null && !preferred.trim().isEmpty()) {
            list.add(preferred.trim());
        }
        String envFfmpeg = System.getenv("ALBUM_FFMPEG");
        String envFfprobe = System.getenv("ALBUM_FFPROBE");
        if ("ffmpeg".equals(bare) && envFfmpeg != null && !envFfmpeg.trim().isEmpty()) {
            list.add(envFfmpeg.trim());
        }
        if ("ffprobe".equals(bare) && envFfprobe != null && !envFfprobe.trim().isEmpty()) {
            list.add(envFfprobe.trim());
        }
        list.add(bare);
        list.add(bareExe);
        String home = System.getProperty("user.home", "");
        if (!home.isEmpty()) {
            list.add(home + "\\.lmstudio\\bin\\" + bareExe);
            list.add(home + "/.lmstudio/bin/" + bare);
            list.add(home + "\\scoop\\shims\\" + bareExe);
            list.add(home + "\\AppData\\Local\\Microsoft\\WinGet\\Links\\" + bareExe);
        }
        list.add("C:\\ffmpeg\\bin\\" + bareExe);
        list.add("C:\\ProgramData\\chocolatey\\bin\\" + bareExe);
        return list;
    }

    private static String firstWorking(List<String> candidates) {
        for (String cmd : candidates) {
            if (cmd == null || cmd.isEmpty()) {
                continue;
            }
            // 绝对路径先检查文件存在
            File asFile = new File(cmd);
            if ((cmd.contains("/") || cmd.contains("\\")) && (!asFile.exists() || !asFile.isFile())) {
                continue;
            }
            try {
                ProcessResult result = run(new String[]{cmd, "-version"}, 8);
                if (result.finished && result.exitCode == 0) {
                    return cmd;
                }
            } catch (Exception ignored) {
                // try next
            }
        }
        return null;
    }

    public static ProcessResult run(String[] command, long timeoutSeconds)
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
        }, "ext-media-drain");
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

    public static final class ProcessResult {
        public final boolean finished;
        public final int exitCode;
        public final String output;

        public ProcessResult(boolean finished, int exitCode, String output) {
            this.finished = finished;
            this.exitCode = exitCode;
            this.output = output == null ? "" : output;
        }

        public boolean ok() {
            return finished && exitCode == 0;
        }

        public String shortOutput() {
            String text = output.replace('\r', ' ').replace('\n', ' ').trim();
            if (text.length() <= 240) {
                return text;
            }
            return text.substring(0, 240) + "…";
        }
    }
}
