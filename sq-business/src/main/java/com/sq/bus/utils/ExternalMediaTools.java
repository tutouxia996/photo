package com.sq.bus.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * ffmpeg / ffprobe 可执行文件定位与进程执行。
 */
public final class ExternalMediaTools {

    private static final Logger log = LoggerFactory.getLogger(ExternalMediaTools.class);
    private static final int MAX_OUTPUT_BYTES = 256 * 1024;

    private static volatile String ffmpegCmd = "ffmpeg";
    private static volatile String ffprobeCmd = "ffprobe";
    private static volatile boolean resolved = false;
    private static volatile boolean ffmpegOk = false;
    private static volatile boolean ffprobeOk = false;

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

    public static boolean isFfmpegAvailable() {
        ensureResolved();
        return ffmpegOk;
    }

    public static boolean isFfprobeAvailable() {
        ensureResolved();
        return ffprobeOk;
    }

    /** 供修复接口回显，便于排查 PATH/编码问题 */
    public static Map<String, Object> diagnose() {
        ensureResolved();
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("ffmpeg", ffmpegCmd);
        m.put("ffprobe", ffprobeCmd);
        m.put("ffmpegOk", ffmpegOk);
        m.put("ffprobeOk", ffprobeOk);
        m.put("userHome", System.getProperty("user.home", ""));
        return m;
    }

    private static synchronized void ensureResolved() {
        if (resolved) {
            return;
        }
        String ff = firstWorking(buildCandidates(ffmpegCmd, "ffmpeg", "ffmpeg.exe"));
        String fp = firstWorking(buildCandidates(ffprobeCmd, "ffprobe", "ffprobe.exe"));
        if (ff != null) {
            ffmpegCmd = ff;
            ffmpegOk = true;
        }
        if (fp != null) {
            ffprobeCmd = fp;
            ffprobeOk = true;
        }
        resolved = true;
        log.info("媒体工具定位：ffmpeg={} (ok={}) ffprobe={} (ok={})",
                ffmpegCmd, ffmpegOk, ffprobeCmd, ffprobeOk);
        if (!ffmpegOk) {
            log.warn("未找到可用的 ffmpeg，视频截帧将失败。可配置 album.ffmpegPath 或将 ffmpeg 加入 PATH");
        }
        if (!ffprobeOk) {
            log.warn("未找到可用的 ffprobe，视频时长/定位解析可能失败。可配置 album.ffprobePath");
        }
    }

    private static List<String> buildCandidates(String preferred, String bare, String bareExe) {
        List<String> list = new ArrayList<String>();
        if (preferred != null && !preferred.trim().isEmpty()
                && !"ffmpeg".equals(preferred.trim()) && !"ffprobe".equals(preferred.trim())
                && !"ffmpeg.exe".equalsIgnoreCase(preferred.trim())
                && !"ffprobe.exe".equalsIgnoreCase(preferred.trim())) {
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
        // 绝对路径优先于 PATH 裸命令，避免启动脚本 PATH 残缺/指到残缺构建
        String home = System.getProperty("user.home", "");
        if (!home.isEmpty()) {
            list.add(home + "\\.lmstudio\\bin\\" + bareExe);
            list.add(home + "/.lmstudio/bin/" + bare);
            list.add(home + "\\scoop\\apps\\ffmpeg\\current\\bin\\" + bareExe);
            list.add(home + "\\scoop\\shims\\" + bareExe);
            list.add(home + "\\AppData\\Local\\Microsoft\\WinGet\\Links\\" + bareExe);
        }
        list.add("C:\\ffmpeg\\bin\\" + bareExe);
        list.add("D:\\ffmpeg\\bin\\" + bareExe);
        list.add("C:\\ProgramData\\chocolatey\\bin\\" + bareExe);
        list.add(bareExe);
        list.add(bare);
        return list;
    }

    private static String firstWorking(List<String> candidates) {
        for (String cmd : candidates) {
            if (cmd == null || cmd.isEmpty()) {
                continue;
            }
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
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    int remain = MAX_OUTPUT_BYTES - collected.size();
                    if (remain <= 0) {
                        continue;
                    }
                    collected.write(buf, 0, Math.min(n, remain));
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
        // ffprobe/ffmpeg 输出为 UTF-8；中文 Windows 默认 GBK 会把小米等标签字节错解，导致 JSON/截帧诊断失败
        String output = decodeUtf8Prefer(collected.toByteArray());
        return new ProcessResult(finished, exitCode, output);
    }

    private static String decodeUtf8Prefer(byte[] raw) {
        if (raw == null || raw.length == 0) {
            return "";
        }
        String utf8 = new String(raw, StandardCharsets.UTF_8);
        // 若几乎全是替换符，再回退系统编码（极老本地化工具）
        int bad = 0;
        for (int i = 0; i < utf8.length(); i++) {
            if (utf8.charAt(i) == '\uFFFD') {
                bad++;
            }
        }
        if (bad > 0 && bad * 10 > utf8.length()) {
            return new String(raw, Charset.defaultCharset());
        }
        return utf8;
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
