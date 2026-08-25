package com.sq.bus.service.impl;

import com.sq.bus.config.AlbumProperties;
import com.sq.bus.domain.BizPhoto;
import com.sq.bus.domain.vo.VideoProxyJobItem;
import com.sq.bus.domain.vo.VideoProxyProgress;
import com.sq.bus.domain.vo.VideoProxyStatus;
import com.sq.bus.service.IBizPhotoService;
import com.sq.bus.service.IVideoProxyService;
import com.sq.bus.utils.ExternalMediaTools;
import com.sq.bus.utils.VideoStreamProbe;
import com.sq.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 视频浏览代理：ffmpeg 转出固定档位，存放于 album.proxyPath，与原片目录隔离。
 */
@Service
public class VideoProxyServiceImpl implements IVideoProxyService {

    private static final Logger log = LoggerFactory.getLogger(VideoProxyServiceImpl.class);

    public static final String QUALITY_480 = "480p";
    public static final String QUALITY_720 = "720p";
    public static final String QUALITY_1080 = "1080p";
    public static final String QUALITY_ORIGINAL = "original";

    public static final String STATUS_MISSING = "missing";
    public static final String STATUS_GENERATING = "generating";
    public static final String STATUS_READY = "ready";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_ORIGINAL = "original";
    /** 不满足转码条件，应直接播原片 */
    public static final String STATUS_NOT_REQUIRED = "not_required";

    private final Map<String, String> runtimeStatus = new ConcurrentHashMap<String, String>();
    private final Map<String, String> runtimeMessage = new ConcurrentHashMap<String, String>();
    private final Map<String, JobMeta> jobMeta = new ConcurrentHashMap<String, JobMeta>();
    /** 转码资格缓存，减少 status 轮询重复 ffprobe */
    private final Map<Long, Boolean> eligibilityCache = new ConcurrentHashMap<Long, Boolean>();
    private final ProgressTracker progressTracker = new ProgressTracker();

    private static final class JobMeta {
        Long photoId;
        String fileName;
        String variant;
    }

    private static final class ProgressTracker {
        final AtomicInteger total = new AtomicInteger(0);
        final AtomicInteger finished = new AtomicInteger(0);
        final AtomicInteger failed = new AtomicInteger(0);
        final AtomicBoolean batchEnqueueing = new AtomicBoolean(false);
        final AtomicInteger videosSkipped = new AtomicInteger(0);
        final LinkedList<VideoProxyJobItem> recentFailed = new LinkedList<VideoProxyJobItem>();

        void reset() {
            total.set(0);
            finished.set(0);
            failed.set(0);
            videosSkipped.set(0);
            recentFailed.clear();
        }

        void onQueued() {
            total.incrementAndGet();
        }

        void onFinished(boolean success, JobMeta meta, String failMsg) {
            finished.incrementAndGet();
            if (!success) {
                failed.incrementAndGet();
                if (meta != null) {
                    VideoProxyJobItem item = new VideoProxyJobItem();
                    item.setPhotoId(meta.photoId);
                    item.setFileName(meta.fileName);
                    item.setVariant(meta.variant);
                    item.setMessage(failMsg);
                    recentFailed.addFirst(item);
                    while (recentFailed.size() > 8) {
                        recentFailed.removeLast();
                    }
                }
            }
        }
    }

    private final ExecutorService executor = Executors.newCachedThreadPool(new ThreadFactory() {
        private final AtomicInteger seq = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "video-proxy-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    });

    private volatile Semaphore codecSlots;

    @Autowired
    private AlbumProperties albumProperties;

    @Autowired
    private IBizPhotoService photoService;

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    @Override
    public VideoProxyStatus status(Long photoId, String quality, Integer fps) {
        Normalized n = normalize(quality, fps);
        VideoProxyStatus st = baseStatus(photoId, n);
        fillSourceMeta(st, photoId);
        if (n.original) {
            st.setStatus(STATUS_ORIGINAL);
            st.setPlayUrl("/album/photo/media/" + photoId + "?original=true");
            st.setMessage("原片");
            File origin = originFile(photoId);
            if (origin != null) {
                st.setFileSize(origin.length());
            }
            return st;
        }
        String key = jobKey(photoId, n.variant);
        File ready = proxyFile(photoId, n.variant);
        if (ready.exists() && ready.isFile() && ready.length() > 1024L) {
            st.setStatus(STATUS_READY);
            st.setFileSize(ready.length());
            st.setPlayUrl(playUrl(photoId, n));
            st.setMessage("已就绪");
            runtimeStatus.remove(key);
            runtimeMessage.remove(key);
            return st;
        }
        String rt = runtimeStatus.get(key);
        if (STATUS_GENERATING.equals(rt)) {
            st.setStatus(STATUS_GENERATING);
            st.setMessage(StringUtils.defaultIfEmpty(runtimeMessage.get(key), "正在生成浏览档…"));
            return st;
        }
        if (STATUS_FAILED.equals(rt)) {
            st.setStatus(STATUS_FAILED);
            st.setMessage(StringUtils.defaultIfEmpty(runtimeMessage.get(key), "生成失败"));
            return st;
        }
        if (!needsVideoProxy(photoId)) {
            st.setStatus(STATUS_NOT_REQUIRED);
            st.setMessage("无需浏览档（直接播原片）");
            return st;
        }
        st.setStatus(STATUS_MISSING);
        st.setMessage("尚未生成该浏览档");
        return st;
    }

    @Override
    public boolean needsVideoProxy(Long photoId) {
        if (photoId == null) {
            return false;
        }
        Boolean cached = eligibilityCache.get(photoId);
        if (cached != null) {
            return cached;
        }
        BizPhoto photo = photoService.getById(photoId);
        boolean need = needsVideoProxy(photo);
        eligibilityCache.put(photoId, need);
        return need;
    }

    private boolean needsVideoProxy(BizPhoto photo) {
        if (photo == null || photo.getFileType() == null || photo.getFileType() != 2) {
            return false;
        }
        File origin = resolveOrigin(photo);
        if (origin == null) {
            return false;
        }
        long size = photo.getFileSize() != null ? photo.getFileSize() : origin.length();
        AlbumProperties.VideoProxy cfg = albumProperties.getVideoProxy();
        return VideoStreamProbe.needsVideoProxy(origin, size, cfg);
    }

    @Override
    public VideoProxyProgress getProgress() {
        VideoProxyProgress p = new VideoProxyProgress();
        int generating = 0;
        List<VideoProxyJobItem> current = new ArrayList<VideoProxyJobItem>();
        for (Map.Entry<String, String> e : runtimeStatus.entrySet()) {
            if (!STATUS_GENERATING.equals(e.getValue())) {
                continue;
            }
            generating++;
            JobMeta meta = jobMeta.get(e.getKey());
            if (meta == null) {
                continue;
            }
            VideoProxyJobItem item = new VideoProxyJobItem();
            item.setPhotoId(meta.photoId);
            item.setFileName(meta.fileName);
            item.setVariant(meta.variant);
            item.setMessage(StringUtils.defaultIfEmpty(runtimeMessage.get(e.getKey()), "转码中"));
            current.add(item);
        }
        int total = progressTracker.total.get();
        int done = progressTracker.finished.get();
        int failed = progressTracker.failed.get();
        int skippedVideos = progressTracker.videosSkipped.get();
        boolean enqueueing = progressTracker.batchEnqueueing.get();
        boolean running = enqueueing || generating > 0 || (total > 0 && done < total);
        int remaining = total > 0 ? Math.max(0, total - done) : 0;
        int percent = 0;
        if (total > 0) {
            percent = Math.min(100, (int) Math.round(done * 100.0 / total));
        }
        p.setRunning(running);
        p.setTotal(total);
        p.setDone(done);
        p.setFailed(failed);
        p.setGenerating(generating);
        p.setRemaining(remaining);
        p.setPercent(percent);
        p.setSkippedVideos(skippedVideos);
        p.setCurrentJobs(current);
        p.setFailedJobs(new ArrayList<VideoProxyJobItem>(progressTracker.recentFailed));
        if (enqueueing) {
            p.setMessage("正在排队转码任务…");
        } else if (generating > 0) {
            VideoProxyJobItem first = current.isEmpty() ? null : current.get(0);
            if (first != null && StringUtils.isNotEmpty(first.getFileName())) {
                p.setMessage("正在转码 " + first.getFileName() + " · " + first.getVariant());
            } else {
                p.setMessage("后台转码进行中…");
            }
        } else if (total == 0) {
            if (skippedVideos > 0) {
                p.setMessage("无待转码任务：共跳过 " + skippedVideos + " 个视频（需1080p+、≥30fps，或浏览档已存在）");
            } else {
                p.setMessage("无待转码任务");
            }
        } else if (failed > 0 && done >= total) {
            p.setMessage("转码完成，部分失败 " + failed + " 个");
        } else if (total > 0 && done >= total) {
            p.setMessage("转码已全部完成");
        } else {
            p.setMessage("");
        }
        if (running) {
            p.setStatus(0);
        } else if (failed > 0 && total > 0) {
            p.setStatus(2);
        } else {
            p.setStatus(1);
        }
        return p;
    }

    @Override
    public void beginBatch() {
        progressTracker.reset();
        eligibilityCache.clear();
        progressTracker.batchEnqueueing.set(true);
    }

    @Override
    public void endBatch() {
        progressTracker.batchEnqueueing.set(false);
    }

    @Override
    public void onBatchVideoSkipped() {
        progressTracker.videosSkipped.incrementAndGet();
    }

    @Override
    public VideoProxyStatus ensure(Long photoId, String quality, Integer fps) {
        return ensure(photoId, quality, fps, false);
    }

    @Override
    public VideoProxyStatus ensure(Long photoId, String quality, Integer fps, boolean force) {
        Normalized n = normalize(quality, fps);
        if (n.original) {
            return status(photoId, quality, fps);
        }
        if (force) {
            deleteVariantFile(photoId, n.variant);
            String key = jobKey(photoId, n.variant);
            runtimeStatus.remove(key);
            runtimeMessage.remove(key);
            eligibilityCache.remove(photoId);
        }
        VideoProxyStatus current = status(photoId, n.quality, n.fps);
        if (STATUS_READY.equals(current.getStatus()) || STATUS_GENERATING.equals(current.getStatus())) {
            return current;
        }
        BizPhoto photo = photoService.getById(photoId);
        if (photo == null || photo.getFileType() == null || photo.getFileType() != 2) {
            current.setStatus(STATUS_FAILED);
            current.setMessage("不是视频或不存在");
            return current;
        }
        File origin = resolveOrigin(photo);
        if (origin == null) {
            current.setStatus(STATUS_FAILED);
            current.setMessage("原片文件不存在");
            return current;
        }
        if (!needsVideoProxy(photo)) {
            current.setStatus(STATUS_NOT_REQUIRED);
            current.setMessage("无需浏览档（直接播原片）");
            return current;
        }
        String key = jobKey(photoId, n.variant);
        runtimeStatus.put(key, STATUS_GENERATING);
        runtimeMessage.put(key, "排队转码…");
        JobMeta meta = new JobMeta();
        meta.photoId = photoId;
        meta.fileName = photo.getFileName();
        meta.variant = n.variant;
        jobMeta.put(key, meta);
        progressTracker.onQueued();
        current.setStatus(STATUS_GENERATING);
        current.setMessage("已加入后台转码队列");
        executor.execute(() -> runTranscode(photoId, origin, n, key));
        return current;
    }

    private void deleteVariantFile(Long photoId, String variant) {
        File ready = proxyFile(photoId, variant);
        if (ready.exists()) {
            //noinspection ResultOfMethodCallIgnored
            ready.delete();
        }
        File part = new File(ready.getParentFile(), variant + ".part.mp4");
        if (part.exists()) {
            //noinspection ResultOfMethodCallIgnored
            part.delete();
        }
    }

    @Override
    public File resolveReadyFile(Long photoId, String quality, Integer fps) {
        Normalized n = normalize(quality, fps);
        if (n.original) {
            return null;
        }
        File ready = proxyFile(photoId, n.variant);
        if (ready.exists() && ready.isFile() && ready.length() > 1024L) {
            return ready;
        }
        return null;
    }

    @Override
    public void deleteProxies(Long photoId) {
        if (photoId == null) {
            return;
        }
        File dir = proxyDir(photoId);
        if (dir == null || !dir.exists()) {
            return;
        }
        File[] children = dir.listFiles();
        if (children != null) {
            for (File f : children) {
                try {
                    Files.deleteIfExists(f.toPath());
                } catch (Exception e) {
                    log.warn("删除代理片失败: {}", f.getAbsolutePath(), e);
                }
            }
        }
        try {
            Files.deleteIfExists(dir.toPath());
        } catch (Exception ignored) {
            // 目录非空等
        }
        String prefix = photoId + ":";
        runtimeStatus.keySet().removeIf(k -> k.startsWith(prefix));
        runtimeMessage.keySet().removeIf(k -> k.startsWith(prefix));
        jobMeta.keySet().removeIf(k -> k.startsWith(prefix));
    }

    @Override
    public boolean isUnderProxyRoot(File file) {
        if (file == null) {
            return false;
        }
        String root = albumProperties.getProxyPath();
        if (StringUtils.isEmpty(root)) {
            return false;
        }
        try {
            File rootFile = new File(root).getCanonicalFile();
            File cur = file.getCanonicalFile();
            String rootPath = rootFile.getAbsolutePath();
            String curPath = cur.getAbsolutePath();
            if (!rootPath.endsWith(File.separator)) {
                rootPath = rootPath + File.separator;
            }
            return curPath.equals(rootFile.getAbsolutePath()) || curPath.startsWith(rootPath);
        } catch (Exception e) {
            return false;
        }
    }

    private void runTranscode(Long photoId, File origin, Normalized n, String key) {
        Semaphore slots = codecSemaphore();
        boolean acquired = false;
        File part = null;
        File target = proxyFile(photoId, n.variant);
        JobMeta meta = jobMeta.get(key);
        boolean success = false;
        String failMsg = null;
        try {
            runtimeMessage.put(key, "等待转码槽位…");
            slots.acquire();
            acquired = true;
            runtimeMessage.put(key, "正在转码 " + n.variant + "…");

            File parent = target.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IllegalStateException("无法创建代理目录: " + parent.getAbsolutePath());
            }
            // 清理残留（扩展名须以 .mp4 结尾，否则 ffmpeg 无法推断封装）
            part = new File(parent, n.variant + ".part.mp4");
            File staleDotPart = new File(target.getAbsolutePath() + ".part");
            if (staleDotPart.exists() && !staleDotPart.delete()) {
                log.warn("无法删除残留 part: {}", staleDotPart.getAbsolutePath());
            }
            if (part.exists() && !part.delete()) {
                log.warn("无法删除残留 part: {}", part.getAbsolutePath());
            }
            if (target.exists() && !target.delete()) {
                log.warn("无法删除旧代理片: {}", target.getAbsolutePath());
            }

            AlbumProperties.VideoProxy cfg = albumProperties.getVideoProxy();
            if (cfg == null) {
                cfg = new AlbumProperties.VideoProxy();
            }
            int height = qualityHeight(n.quality);
            ExternalMediaTools.ProcessResult result = transcodeWithFallback(origin, part, height, n.fps, cfg);
            if (!result.ok() || !part.exists() || part.length() < 1024L) {
                String msg = "转码失败 exit=" + result.exitCode + " " + result.shortOutput();
                log.warn("视频代理转码失败 photoId={} variant={} file={} {}", photoId, n.variant,
                        origin.getAbsolutePath(), msg);
                runtimeStatus.put(key, STATUS_FAILED);
                runtimeMessage.put(key, msg);
                failMsg = msg;
                if (part.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    part.delete();
                }
                return;
            }
            if (!part.renameTo(target)) {
                Files.move(part.toPath(), target.toPath());
            }
            runtimeStatus.remove(key);
            runtimeMessage.remove(key);
            success = true;
            log.info("视频代理已生成 photoId={} variant={} size={}", photoId, n.variant, target.length());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            runtimeStatus.put(key, STATUS_FAILED);
            runtimeMessage.put(key, "转码被中断");
            failMsg = "转码被中断";
            cleanupQuietly(part);
        } catch (Exception e) {
            log.warn("视频代理转码异常 photoId={} variant={}", photoId, n.variant, e);
            runtimeStatus.put(key, STATUS_FAILED);
            failMsg = e.getMessage() == null ? "转码异常" : e.getMessage();
            runtimeMessage.put(key, failMsg);
            cleanupQuietly(part);
        } finally {
            if (acquired) {
                slots.release();
            }
            progressTracker.onFinished(success, meta, failMsg);
            jobMeta.remove(key);
        }
    }

    private Semaphore codecSemaphore() {
        Semaphore s = codecSlots;
        if (s != null) {
            return s;
        }
        synchronized (this) {
            if (codecSlots == null) {
                int n = 1;
                if (albumProperties.getVideoProxy() != null && albumProperties.getVideoProxy().getConcurrency() > 0) {
                    n = albumProperties.getVideoProxy().getConcurrency();
                }
                codecSlots = new Semaphore(n);
            }
            return codecSlots;
        }
    }

    /**
     * 多策略转码：HEVC/10bit、异常音轨、硬件解码等场景下回退（与 ThumbUtils 思路一致）。
     */
    private ExternalMediaTools.ProcessResult transcodeWithFallback(File origin, File part, int height, int fps,
                                                                  AlbumProperties.VideoProxy cfg) throws Exception {
        String ffmpeg = ExternalMediaTools.ffmpeg();
        String src = origin.getAbsolutePath();
        String out = part.getAbsolutePath();
        int crf480 = cfg.getCrf480() > 0 ? cfg.getCrf480() : 28;
        int crf720 = cfg.getCrf720() > 0 ? cfg.getCrf720() : 26;
        int crf1080 = cfg.getCrf1080() > 0 ? cfg.getCrf1080() : 23;
        int crf = height <= 480 ? crf480 : (height <= 720 ? crf720 : crf1080);
        String preset = StringUtils.isEmpty(cfg.getPreset()) ? "veryfast" : cfg.getPreset();
        String audioBr;
        String maxrate = null;
        if (height <= 480) {
            audioBr = StringUtils.isEmpty(cfg.getAudioBitrate480()) ? "96k" : cfg.getAudioBitrate480();
            maxrate = StringUtils.isEmpty(cfg.getMaxrate480()) ? "1500k" : cfg.getMaxrate480();
        } else {
            audioBr = StringUtils.isEmpty(cfg.getAudioBitrate()) ? "128k" : cfg.getAudioBitrate();
        }
        String vf = buildVideoFilter(height, fps, true);
        String vfNoFps = buildVideoFilter(height, fps, false);

        List<String[]> attempts = new ArrayList<String[]>();
        attempts.add(buildTranscodeCmd(ffmpeg, src, out, vf, preset, crf, audioBr, maxrate, true, false));
        attempts.add(buildTranscodeCmd(ffmpeg, src, out, vf, preset, crf, audioBr, maxrate, false, false));
        attempts.add(buildTranscodeCmd(ffmpeg, src, out, vfNoFps, preset, crf, audioBr, maxrate, false, false));
        attempts.add(buildTranscodeCmd(ffmpeg, src, out, vf, preset, crf, audioBr, maxrate, true, true));
        attempts.add(buildTranscodeCmd(ffmpeg, src, out, vf, preset, crf, audioBr, maxrate, false, true));

        long timeout = timeoutSeconds(origin.length(), cfg);
        ExternalMediaTools.ProcessResult last = new ExternalMediaTools.ProcessResult(false, -1, "no attempt");
        for (int i = 0; i < attempts.size(); i++) {
            if (part.exists() && !part.delete()) {
                log.warn("无法删除残留 part: {}", part.getAbsolutePath());
            }
            last = ExternalMediaTools.run(attempts.get(i), timeout);
            if (last.ok() && part.exists() && part.length() >= 1024L) {
                if (i > 0) {
                    log.info("视频代理转码回退成功 file={} attempt={}", src, i + 1);
                }
                return last;
            }
        }
        return last;
    }

    /**
     * 缩放到目标高度内且不放大；宽高强制为偶数（竖屏 1080x1920→720 时避免 405x720 导致 x264 失败）。
     */
    private static String buildVideoFilter(int maxHeight, int fps, boolean includeFps) {
        String h = String.valueOf(maxHeight);
        StringBuilder sb = new StringBuilder();
        sb.append("scale=w='trunc(iw*min(").append(h).append("/ih\\,1)/2)*2'");
        sb.append(":h='trunc(ih*min(").append(h).append("/ih\\,1)/2)*2'");
        sb.append(",setsar=1");
        if (includeFps) {
            sb.append(",fps=").append(fps);
        }
        sb.append(",format=yuv420p");
        return sb.toString();
    }

    private static String[] buildTranscodeCmd(String ffmpeg, String src, String out, String vf, String preset,
                                              int crf, String audioBr, String maxrate, boolean withAudio, boolean hwaccel) {
        List<String> cmd = new ArrayList<String>();
        cmd.add(ffmpeg);
        cmd.add("-nostdin");
        cmd.add("-hide_banner");
        cmd.add("-loglevel");
        cmd.add("error");
        cmd.add("-y");
        if (hwaccel) {
            cmd.add("-hwaccel");
            cmd.add("auto");
        }
        cmd.add("-i");
        cmd.add(src);
        cmd.add("-map");
        cmd.add("0:v:0");
        if (withAudio) {
            cmd.add("-map");
            cmd.add("0:a:0?");
        }
        cmd.add("-sn");
        cmd.add("-dn");
        cmd.add("-vf");
        cmd.add(vf);
        cmd.add("-c:v");
        cmd.add("libx264");
        cmd.add("-preset");
        cmd.add(preset);
        cmd.add("-crf");
        cmd.add(String.valueOf(crf));
        if (StringUtils.isNotEmpty(maxrate)) {
            cmd.add("-maxrate");
            cmd.add(maxrate.trim());
            cmd.add("-bufsize");
            // 缓冲约为峰值码率 2 倍，利于 VBV 控流
            cmd.add(doubleBitrateLabel(maxrate.trim()));
        }
        cmd.add("-pix_fmt");
        cmd.add("yuv420p");
        cmd.add("-tag:v");
        cmd.add("avc1");
        if (withAudio) {
            cmd.add("-c:a");
            cmd.add("aac");
            cmd.add("-b:a");
            cmd.add(audioBr);
            cmd.add("-ac");
            cmd.add("2");
        } else {
            cmd.add("-an");
        }
        cmd.add("-max_muxing_queue_size");
        cmd.add("1024");
        cmd.add("-movflags");
        cmd.add("+faststart");
        cmd.add("-f");
        cmd.add("mp4");
        cmd.add(out);
        return cmd.toArray(new String[0]);
    }

    /** 1500k → 3000k；解析失败则原样返回。 */
    private static String doubleBitrateLabel(String rate) {
        if (rate == null || rate.isEmpty()) {
            return "3000k";
        }
        String s = rate.trim().toLowerCase(Locale.ROOT);
        try {
            if (s.endsWith("k")) {
                double v = Double.parseDouble(s.substring(0, s.length() - 1));
                return String.valueOf(Math.max(1, Math.round(v * 2))) + "k";
            }
            if (s.endsWith("m")) {
                double v = Double.parseDouble(s.substring(0, s.length() - 1));
                return String.valueOf(Math.max(1, Math.round(v * 2))) + "m";
            }
            long v = Long.parseLong(s);
            return String.valueOf(Math.max(1L, v * 2));
        } catch (NumberFormatException e) {
            return rate;
        }
    }

    private static int qualityHeight(String quality) {
        if (QUALITY_480.equals(quality)) {
            return 480;
        }
        if (QUALITY_720.equals(quality)) {
            return 720;
        }
        return 1080;
    }

    private static long timeoutSeconds(long bytes, AlbumProperties.VideoProxy cfg) {
        double gb = Math.max(0.1d, bytes / (1024d * 1024d * 1024d));
        long perGb = cfg.getTimeoutSecondsPerGb() > 0 ? cfg.getTimeoutSecondsPerGb() : 2400L;
        long min = cfg.getTimeoutMinSeconds() > 0 ? cfg.getTimeoutMinSeconds() : 1800L;
        long max = cfg.getTimeoutMaxSeconds() > 0 ? cfg.getTimeoutMaxSeconds() : 43200L;
        long t = (long) Math.ceil(gb * perGb);
        if (t < min) {
            t = min;
        }
        if (t > max) {
            t = max;
        }
        return t;
    }

    private VideoProxyStatus baseStatus(Long photoId, Normalized n) {
        VideoProxyStatus st = new VideoProxyStatus();
        st.setPhotoId(photoId);
        st.setQuality(n.quality);
        st.setFps(n.original ? null : n.fps);
        st.setVariant(n.variant);
        return st;
    }

    /** 附带原片分辨率/帧率，供前端「原片」选项展示 */
    private void fillSourceMeta(VideoProxyStatus st, Long photoId) {
        if (st == null || photoId == null) {
            return;
        }
        File origin = originFile(photoId);
        if (origin == null) {
            return;
        }
        VideoStreamProbe.StreamInfo info = VideoStreamProbe.probe(origin);
        if (info == null || info.width <= 0 || info.height <= 0) {
            return;
        }
        st.setSourceWidth(info.width);
        st.setSourceHeight(info.height);
        int fpsRound = (int) Math.round(info.fps);
        if (fpsRound > 0) {
            st.setSourceFps(fpsRound);
        }
        st.setSourceLabel(info.summary());
    }

    private static String playUrl(Long photoId, Normalized n) {
        return "/album/photo/media/" + photoId + "?quality=" + n.quality + "&fps=" + n.fps;
    }

    private File originFile(Long photoId) {
        BizPhoto photo = photoService.getById(photoId);
        return resolveOrigin(photo);
    }

    private static File resolveOrigin(BizPhoto photo) {
        if (photo == null || StringUtils.isEmpty(photo.getFilePath())) {
            return null;
        }
        File f = new File(photo.getFilePath());
        if (f.exists() && f.isFile()) {
            return f;
        }
        return null;
    }

    private File proxyDir(Long photoId) {
        String root = albumProperties.getProxyPath();
        if (StringUtils.isEmpty(root) || photoId == null) {
            return null;
        }
        return new File(root, String.valueOf(photoId));
    }

    private File proxyFile(Long photoId, String variant) {
        File dir = proxyDir(photoId);
        if (dir == null) {
            return new File(".", variant + ".mp4");
        }
        return new File(dir, variant + ".mp4");
    }

    private static String jobKey(Long photoId, String variant) {
        return photoId + ":" + variant;
    }

    private static void cleanupQuietly(File part) {
        if (part != null && part.exists()) {
            //noinspection ResultOfMethodCallIgnored
            part.delete();
        }
    }

    static Normalized normalize(String quality, Integer fps) {
        String q = quality == null ? QUALITY_480 : quality.trim().toLowerCase(Locale.ROOT);
        if ("原片".equals(quality) || "origin".equals(q) || "source".equals(q)) {
            q = QUALITY_ORIGINAL;
        }
        if (!QUALITY_480.equals(q) && !QUALITY_720.equals(q) && !QUALITY_1080.equals(q) && !QUALITY_ORIGINAL.equals(q)) {
            q = QUALITY_480;
        }
        int f = fps == null ? 30 : fps;
        if (f != 30) {
            f = 30;
        }
        Normalized n = new Normalized();
        n.quality = q;
        n.fps = f;
        n.original = QUALITY_ORIGINAL.equals(q);
        n.variant = n.original ? QUALITY_ORIGINAL : (q + f);
        return n;
    }

    static final class Normalized {
        String quality;
        int fps;
        boolean original;
        String variant;
    }
}
