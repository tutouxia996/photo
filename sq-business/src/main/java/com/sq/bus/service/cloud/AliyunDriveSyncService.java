package com.sq.bus.service.cloud;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.sq.bus.domain.vo.RemoteAlbumItem;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sq.bus.config.AlbumProperties;
import com.sq.bus.domain.BizScanPath;
import com.sq.bus.service.IBizScanPathService;
import com.sq.bus.domain.vo.AliyunSyncProgress;
import com.sq.bus.service.IAliyunDriveSettingService;
import com.sq.bus.service.cloud.AliyunDriveClient.DriveFile;
import com.sq.common.exception.ServiceException;
import com.sq.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 阿里云盘个人相册 → 本机目录增量下载，并可选触发磁盘扫描入库。
 * <p>
 * 目标：把指定相册内全部图片/视频落到本地；已存在的重复文件直接跳过。
 * </p>
 */
@Service
public class AliyunDriveSyncService {

    private static final Logger log = LoggerFactory.getLogger(AliyunDriveSyncService.class);

    private static final Set<String> MEDIA_EXT = new HashSet<String>(Arrays.asList(
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif", "tif", "tiff", "jfif",
            "mp4", "mov", "avi", "mkv", "wmv", "m4v", "3gp", "mpeg", "mpg", "flv", "ts", "mts",
            "dng", "raw", "arw", "cr2", "cr3", "nef", "orf", "rw2"));

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicInteger progressStatus = new AtomicInteger(1);
    private final AtomicInteger progressTotal = new AtomicInteger();
    private final AtomicInteger progressNeed = new AtomicInteger();
    private final AtomicInteger progressDownloaded = new AtomicInteger();
    private final AtomicInteger progressSkipped = new AtomicInteger();
    private final AtomicInteger progressFailed = new AtomicInteger();
    /** 本轮队列里因云盘 404 已记为缺失、不再计入剩余的个数 */
    private final AtomicInteger progressGone = new AtomicInteger();
    private final AtomicInteger progressRemaining = new AtomicInteger();
    private volatile String progressPhase = "done";
    private volatile String progressMessage = "";
    private volatile String activeTokenFile = "";
    /** 多相册批次：相册总数 */
    private volatile int batchAlbumCount;
    /** 多相册批次：当前相册序号（1-based） */
    private volatile int batchAlbumIndex;
    private volatile String batchCurrentAlbumName = "";
    /** 已完成相册累计 */
    private final AtomicInteger batchCompletedNeed = new AtomicInteger();
    private final AtomicInteger batchCompletedDownloaded = new AtomicInteger();
    private final AtomicInteger batchCompletedSkipped = new AtomicInteger();
    private final AtomicInteger batchCompletedFailed = new AtomicInteger();
    private final AtomicInteger batchCompletedRemaining = new AtomicInteger();
    private final ConcurrentHashMap<String, AliyunSyncProgress.CurrentFile> currentFiles =
            new ConcurrentHashMap<String, AliyunSyncProgress.CurrentFile>();
    private final CopyOnWriteArrayList<AliyunSyncProgress.FailedFile> failedFiles =
            new CopyOnWriteArrayList<AliyunSyncProgress.FailedFile>();
    private volatile TokenStore persistStore;

    @Autowired
    private AlbumProperties albumProperties;

    @Autowired
    private IBizScanPathService scanPathService;

    @Autowired
    private IAliyunDriveSettingService aliyunDriveSettingService;

    @PostConstruct
    public void restorePersistedProgress() {
        try {
            AlbumProperties.AliyunDriveConfig cfg = resolveConfig();
            if (cfg == null || StringUtils.isEmpty(cfg.getTokenFile())) {
                return;
            }
            activeTokenFile = cfg.getTokenFile();
            TokenStore store = TokenStore.load(cfg.getTokenFile());
            persistStore = store;
            applyPersistedProgress(store.syncProgress, true);
        } catch (Exception e) {
            log.warn("恢复云盘下载进度失败：{}", e.getMessage());
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public AliyunSyncProgress getProgress() {
        AliyunSyncProgress p = snapshotProgress();
        boolean live = running.get() || p.getStatus() == 0;
        p.setPaused(paused.get() || p.getStatus() == 3);
        p.setRunning(live);
        p.setRemaining(Math.max(0, progressRemaining.get()));
        p.setCanPause(live && !paused.get() && p.getStatus() == 0);
        p.setCanResume(!live && (p.getStatus() == 3 || p.getRemaining() > 0));
        p.setPercent(calcPercent(p));
        return p;
    }

    /**
     * 后台页面立即同步 / 继续下载。
     */
    public AliyunSyncProgress startSyncAsync() {
        paused.set(false);
        if (running.get()) {
            return getProgress();
        }
        markProgress(0, "listing", "正在启动同步…");
        persistProgressQuiet();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String msg = syncAndScan();
                    log.info("阿里云盘异步同步结束：{}", msg);
                } catch (Exception e) {
                    log.warn("阿里云盘异步同步失败：{}", e.getMessage());
                    finishProgress(2, e.getMessage());
                    persistProgressQuiet();
                }
            }
        }, "aliyun-sync");
        t.setDaemon(true);
        t.start();
        return getProgress();
    }

    public AliyunSyncProgress pauseSync() {
        if (!running.get()) {
            if (progressRemaining.get() > 0) {
                paused.set(true);
                markProgress(3, "paused", "已暂停，剩余 " + progressRemaining.get() + " 个未下载");
                persistProgressQuiet();
            }
            return getProgress();
        }
        paused.set(true);
        markProgress(3, "paused", "正在暂停，当前文件完成后停止…");
        persistProgressQuiet();
        return getProgress();
    }

    public AliyunSyncProgress resumeSync() {
        paused.set(false);
        if (running.get()) {
            markProgress(0, "downloading", "继续下载，剩余 " + progressRemaining.get() + " 个");
            persistProgressQuiet();
            return getProgress();
        }
        return startSyncAsync();
    }

    /**
     * 同步下载 +（可选）触发扫描。供 Quartz 调用。
     */
    public String syncAndScan() {
        AlbumProperties.AliyunDriveConfig cfg = resolveConfig();
        if (cfg == null || !cfg.isEnabled()) {
            String msg = "阿里云盘同步未启用（请在「相册管理 → 云盘同步」打开开关）";
            finishProgress(2, msg);
            persistProgressQuiet();
            return msg;
        }
        if (!running.compareAndSet(false, true)) {
            return "上一次同步仍在进行中，已跳过";
        }
        paused.set(false);
        activeTokenFile = cfg.getTokenFile();
        try {
            markProgress(0, "listing", "正在登录并列举云盘文件…");
            persistProgressQuiet();

            List<RemoteAlbumItem> albums = aliyunDriveSettingService.listRemoteAlbumsFromConfig(cfg);
            if (albums.isEmpty()) {
                throw new ServiceException("未配置云盘相册，请先在云盘同步页选择并保存");
            }
            String basePath = cfg.getLocalPath();
            int albumCount = albums.size();
            resetBatchProgress(albumCount);
            int sumRemote = 0;
            int sumDownloaded = 0;
            int sumSkipped = 0;
            int sumFailed = 0;
            boolean anyPaused = false;
            StringBuilder scanMsg = new StringBuilder();

            for (int i = 0; i < albumCount; i++) {
                if (paused.get()) {
                    anyPaused = true;
                    break;
                }
                RemoteAlbumItem album = albums.get(i);
                batchAlbumIndex = i + 1;
                batchCurrentAlbumName = album.getName() == null ? "" : album.getName();
                markProgress(0, "listing", String.format(Locale.ROOT,
                        "正在同步相册 %d/%d：%s", batchAlbumIndex, albumCount, batchCurrentAlbumName));
                persistProgressQuiet();

                AlbumProperties.AliyunDriveConfig oneCfg = copyDriveCfg(cfg);
                oneCfg.setRemoteAlbumId(album.getAlbumId());
                oneCfg.setRemoteAlbumName(album.getName());
                oneCfg.setLocalPath(aliyunDriveSettingService.composeAlbumLocalPath(basePath, album.getName()));

                SyncResult result = doSync(oneCfg);
                sumRemote += result.remoteMedia;
                sumDownloaded += result.downloaded;
                sumSkipped += result.skipped;
                sumFailed += result.failed;

                if (!result.paused && progressRemaining.get() <= 0) {
                    accumulateCompletedAlbumProgress();
                }

                if (result.paused) {
                    anyPaused = true;
                    break;
                }

                if (cfg.isTriggerScan()) {
                    try {
                        Long bindId = aliyunDriveSettingService.resolveOrCreateLocalAlbumId(album.getName(), "system");
                        Long pathId = scanPathService.upsertScanPathForSync(
                                oneCfg.getLocalPath(), bindId, album.getName());
                        if (pathId == null) {
                            scanMsg.append("；").append(album.getName()).append("未触发扫描");
                        } else {
                            markProgress(0, "scanning",
                                    "相册「" + album.getName() + "」下载完成，正在触发磁盘扫描…");
                            Long logId = scanPathService.startScanAsync(pathId, cfg.isFullScan());
                            scanMsg.append("；").append(album.getName()).append("→扫描logId=").append(logId);
                        }
                    } catch (Exception scanEx) {
                        scanMsg.append("；").append(album.getName()).append("扫描失败:").append(scanEx.getMessage());
                        log.warn("云盘相册 {} 触发扫描失败: {}", album.getName(), scanEx.getMessage());
                    }
                }
            }

            if (anyPaused) {
                String msg = String.format(Locale.ROOT,
                        "已暂停：相册=%d，已下载=%d，跳过=%d，失败=%d，剩余=%d",
                        albumCount, sumDownloaded, sumSkipped, sumFailed, progressRemaining.get());
                msg += scanMsg.toString();
                markProgress(3, "paused", msg);
                persistProgressQuiet();
                log.info(msg);
                return msg;
            }
            String msg = String.format(Locale.ROOT,
                    "同步完成：%d 个相册，远程媒体=%d，下载=%d，跳过(已存在)=%d，失败=%d",
                    albumCount, sumRemote, sumDownloaded, sumSkipped, sumFailed);
            msg += scanMsg.toString();
            log.info(msg);
            progressRemaining.set(0);
            finishProgress(1, msg);
            persistProgressQuiet();
            return msg;
        } catch (Exception e) {
            finishProgress(2, e.getMessage());
            persistProgressQuiet();
            throw e;
        } finally {
            running.set(false);
            currentFiles.clear();
        }
    }

    private SyncResult doSync(AlbumProperties.AliyunDriveConfig cfg) {
        Path localDir = Paths.get(cfg.getLocalPath());
        try {
            Files.createDirectories(localDir);
        } catch (Exception e) {
            throw new ServiceException("无法创建本机目录：" + cfg.getLocalPath() + "，" + e.getMessage());
        }

        TokenStore store = TokenStore.load(cfg.getTokenFile());
        persistStore = store;
        String refresh = StringUtils.isNotEmpty(store.refreshToken) ? store.refreshToken : cfg.getRefreshToken();
        if (StringUtils.isEmpty(refresh)) {
            throw new ServiceException("未配置 refreshToken（请在「云盘同步」页面填写，或确认 tokenFile 可读写）");
        }

        AliyunDriveClient client = new AliyunDriveClient(cfg, refresh);
        client.ensureLogin();
        store.refreshToken = client.getRefreshToken();
        store.save(cfg.getTokenFile());

        String albumId = client.resolveAlbumId(cfg.getRemoteAlbumId(), cfg.getRemoteAlbumName());
        List<DriveFile> listed = client.listAlbumFiles(albumId);

        Map<String, DriveFile> byId = new LinkedHashMap<String, DriveFile>();
        int skippedFilter = 0;
        for (DriveFile f : listed) {
            if (f == null || StringUtils.isEmpty(f.fileId)) {
                continue;
            }
            // 默认下相册内全部原文件；仅 mediaOnly=true 时才按扩展名过滤
            if (cfg.isMediaOnly() && !isMedia(f)) {
                skippedFilter++;
                continue;
            }
            if (!byId.containsKey(f.fileId)) {
                byId.put(f.fileId, f);
            }
        }
        List<DriveFile> files = new ArrayList<DriveFile>(byId.values());
        int concurrency = Math.max(1, cfg.getDownloadConcurrency());
        log.info("阿里云盘相册 {}：接口条目={}，待下载原文件={}，过滤跳过={}（mediaOnly={}），并发={}，同步到 {}",
                albumId, listed.size(), files.size(), skippedFilter, cfg.isMediaOnly(), concurrency, localDir);

        SyncResult result = new SyncResult();
        result.albumId = albumId;

        JSONObject fileState = store.files;
        if (fileState == null) {
            fileState = new JSONObject();
            store.files = fileState;
        }
        final JSONObject fileStateFinal = fileState;
        final AliyunDriveClient clientRef = client;
        final TokenStore storeRef = store;
        final Object stateLock = new Object();
        final AtomicInteger downloaded = new AtomicInteger();
        final AtomicInteger skipped = new AtomicInteger();
        final AtomicInteger failed = new AtomicInteger();
        final int total = files.size();
        final Path localDirFinal = localDir;
        final AlbumProperties.AliyunDriveConfig cfgFinal = cfg;
        result.remoteMedia = total;

        // 先快速跳过已存在，再并行下载剩余
        List<DriveFile> needDownload = new ArrayList<DriveFile>();
        for (DriveFile file : files) {
            if (isRememberedMissing(file, fileStateFinal)) {
                skipped.incrementAndGet();
                continue;
            }
            Path existing = findAlreadyDownloaded(file, localDir, fileStateFinal);
            if (existing != null) {
                skipped.incrementAndGet();
                synchronized (stateLock) {
                    remember(fileStateFinal, file, existing.getFileName().toString());
                }
                continue;
            }
            needDownload.add(file);
        }
        log.info("已存在跳过 {}，待下载 {}，开始并行下载", skipped.get(), needDownload.size());
        progressTotal.set(total);
        progressNeed.set(needDownload.size());
        progressDownloaded.set(0);
        progressSkipped.set(skipped.get());
        progressFailed.set(0);
        progressGone.set(0);
        failedFiles.clear();
        progressRemaining.set(needDownload.size());
        persistStore = store;
        updateBatchProgressMessage();
        if (paused.get()) {
            markProgress(3, "paused", "已暂停，剩余 " + needDownload.size() + " 个未下载");
            persistProgress(store, cfg.getTokenFile());
            result.paused = true;
            result.downloaded = 0;
            result.skipped = skipped.get();
            result.failed = 0;
            return result;
        }
        if (needDownload.isEmpty()) {
            markProgress(0, "downloading", buildBatchProgressMessage("没有需要下载的新文件"));
        } else {
            markProgress(0, "downloading", buildBatchProgressMessage(
                    "开始下载 " + needDownload.size() + " 个文件"));
        }
        persistProgress(store, cfg.getTokenFile());

        if (!needDownload.isEmpty()) {
            final ConcurrentLinkedQueue<DriveFile> queue = new ConcurrentLinkedQueue<DriveFile>(needDownload);
            int workers = Math.min(concurrency, needDownload.size());
            final CountDownLatch latch = new CountDownLatch(workers);
            java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(
                    workers,
                    new java.util.concurrent.ThreadFactory() {
                        private final AtomicInteger n = new AtomicInteger();
                        @Override
                        public Thread newThread(Runnable r) {
                            Thread t = new Thread(r, "aliyun-dl-" + n.incrementAndGet());
                            t.setDaemon(true);
                            return t;
                        }
                    });
            try {
                for (int i = 0; i < workers; i++) {
                    pool.submit(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                while (!paused.get()) {
                                    DriveFile file = queue.poll();
                                    if (file == null) {
                                        break;
                                    }
                                    try {
                                        downloadOne(clientRef, storeRef, stateLock, cfgFinal, localDirFinal, fileStateFinal, file,
                                                downloaded, skipped, failed, total);
                                    } catch (Exception e) {
                                        markFileFailed(file, e, failed);
                                    }
                                    refreshRemaining();
                                    int done = progressDownloaded.get() + progressFailed.get();
                                    boolean force = paused.get() || done == 1 || done % 5 == 0;
                                    if (force) {
                                        synchronized (stateLock) {
                                            persistProgress(storeRef, cfgFinal.getTokenFile());
                                        }
                                    }
                                }
                            } finally {
                                latch.countDown();
                            }
                        }
                    });
                }
                latch.await();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                paused.set(true);
            } finally {
                pool.shutdownNow();
            }
        }

        store.refreshToken = client.getRefreshToken();
        synchronized (stateLock) {
            persistProgress(store, cfg.getTokenFile());
        }
        result.downloaded = downloaded.get();
        result.skipped = skipped.get();
        result.failed = failed.get();
        // 失败的仍算剩余（下次重试）；404 已记缺失的不再计入
        refreshRemaining();
        int rem = progressRemaining.get();
        result.paused = rem > 0;
        if (result.paused) {
            paused.set(true);
        }
        return result;
    }

    private void downloadOne(AliyunDriveClient client, TokenStore store, Object stateLock,
                             AlbumProperties.AliyunDriveConfig cfg, Path localDir, JSONObject fileState,
                             DriveFile file, AtomicInteger downloaded, AtomicInteger skipped, AtomicInteger failed, int total) {
        final String key = StringUtils.isNotEmpty(file.fileId) ? file.fileId : file.name;
        String localName;
        synchronized (stateLock) {
            localName = pickLocalName(file, fileState, localDir);
        }
        Path target = localDir.resolve(localName);
        try {
            Files.deleteIfExists(Paths.get(target.toString() + ".aliyun.part"));
        } catch (Exception ignored) {
            // ignore
        }

        Exception last = null;
        boolean ok = false;
        for (int i = 1; i <= 2; i++) {
            try {
                String url = client.getDownloadUrl(file.driveId, file.fileId);
                beginCurrentFile(key, file.name, file.size);
                client.downloadTo(url, target, file.size, new AliyunDriveClient.ProgressCallback() {
                    @Override
                    public void onBytes(long written, long expected) {
                        updateCurrentFile(key, file.name, written, expected);
                    }
                });
                ok = true;
                break;
            } catch (Exception e) {
                last = e;
                log.info("下载重试 {}/2 {} : {}", i, file.name, e.getMessage());
                try {
                    Thread.sleep(800L * i);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        if (!ok) {
            endCurrentFile(key);
            if (isRemoteGone(last)) {
                skipped.incrementAndGet();
                progressSkipped.incrementAndGet();
                progressGone.incrementAndGet();
                synchronized (stateLock) {
                    rememberMissing(fileState, file);
                    persistProgress(store, cfg.getTokenFile());
                }
                refreshRemaining();
                log.info("相册有记录但各盘均找不到文件，已跳过：{}（{}）",
                        file.name, last.getMessage());
                return;
            }
            markFileFailed(file, last, failed);
            return;
        }

        int d;
        synchronized (stateLock) {
            remember(fileState, file, localName);
            store.refreshToken = client.getRefreshToken();
            d = downloaded.incrementAndGet();
        }
        progressDownloaded.incrementAndGet();
        endCurrentFile(key);
        refreshRemaining();
        log.info("已下载 {}/{}：{} -> {}，剩余 {}", d, total, file.name, target.getFileName(), progressRemaining.get());
    }

    /**
     * 已下载判定（满足任一即可跳过）：
     * 1) 状态里有 fileId，且本地文件存在、大小一致；
     * 2) 本地已有同名且大小一致；
     * 3) 本地已有 fileId 后缀命名且大小一致。
     */
    private static Path findAlreadyDownloaded(DriveFile file, Path localDir, JSONObject fileState) {
        JSONObject prev = fileState.getJSONObject(file.fileId);
        if (prev != null) {
            String localName = prev.getString("localName");
            Long size = prev.getLong("size");
            if (StringUtils.isNotEmpty(localName)) {
                Path p = localDir.resolve(sanitizeFileName(localName));
                long expect = file.size > 0 ? file.size : (size == null ? -1 : size);
                if (isCompleteLocalFile(p, expect)) {
                    return p;
                }
            }
        }

        String base = sanitizeFileName(file.name);
        Path byName = localDir.resolve(base);
        if (isCompleteLocalFile(byName, file.size)) {
            return byName;
        }

        if (StringUtils.isNotEmpty(file.fileId)) {
            String shortId = file.fileId.length() > 8 ? file.fileId.substring(0, 8) : file.fileId;
            int dot = base.lastIndexOf('.');
            String alt = dot > 0
                    ? base.substring(0, dot) + "_" + shortId + base.substring(dot)
                    : base + "_" + shortId;
            Path altPath = localDir.resolve(alt);
            if (isCompleteLocalFile(altPath, file.size)) {
                return altPath;
            }
        }
        return null;
    }

    private static boolean isCompleteLocalFile(Path path, long expectedSize) {
        try {
            if (!Files.isRegularFile(path)) {
                return false;
            }
            long actual = Files.size(path);
            if (actual <= 0) {
                return false;
            }
            if (expectedSize <= 0) {
                return true;
            }
            return actual == expectedSize;
        } catch (Exception e) {
            return false;
        }
    }

    private AliyunSyncProgress snapshotProgress() {
        AliyunSyncProgress p = new AliyunSyncProgress();
        p.setStatus(progressStatus.get());
        p.setPhase(progressPhase);
        p.setTotal(progressTotal.get());
        p.setNeedDownload(progressNeed.get());
        p.setDownloaded(progressDownloaded.get());
        p.setSkipped(progressSkipped.get());
        p.setFailed(progressFailed.get());
        p.setRemaining(Math.max(0, progressRemaining.get()));
        p.setMessage(progressMessage == null ? "" : progressMessage);
        fillBatchFields(p);
        p.setCurrentFiles(new ArrayList<AliyunSyncProgress.CurrentFile>(currentFiles.values()));
        p.setFailedFiles(new ArrayList<AliyunSyncProgress.FailedFile>(failedFiles));
        return p;
    }

    private void markProgress(int status, String phase, String message) {
        progressStatus.set(status);
        progressPhase = phase;
        progressMessage = message == null ? "" : message;
        if (status == 0 && "listing".equals(phase)) {
            currentFiles.clear();
            failedFiles.clear();
        }
        if (status == 3) {
            paused.set(true);
        }
    }

    private void finishProgress(int status, String message) {
        progressStatus.set(status);
        progressPhase = status == 1 ? "done" : (status == 3 ? "paused" : "failed");
        progressMessage = message == null ? "" : message;
        currentFiles.clear();
        if (status == 1) {
            progressRemaining.set(0);
            paused.set(false);
        }
    }

    private void persistProgressQuiet() {
        try {
            TokenStore store = persistStore;
            String file = activeTokenFile;
            if (StringUtils.isEmpty(file)) {
                AlbumProperties.AliyunDriveConfig cfg = resolveConfig();
                if (cfg != null) {
                    file = cfg.getTokenFile();
                }
            }
            if (store == null && StringUtils.isNotEmpty(file)) {
                store = TokenStore.load(file);
                persistStore = store;
            }
            persistProgress(store, file);
        } catch (Exception e) {
            log.warn("持久化下载进度失败：{}", e.getMessage());
        }
    }

    private void persistProgress(TokenStore store, String tokenFile) {
        if (store == null || StringUtils.isEmpty(tokenFile)) {
            return;
        }
        store.syncProgress = snapshotProgressJson();
        store.save(tokenFile);
    }

    private JSONObject snapshotProgressJson() {
        JSONObject o = new JSONObject();
        o.put("status", progressStatus.get());
        o.put("phase", progressPhase);
        o.put("total", progressTotal.get());
        o.put("needDownload", progressNeed.get());
        o.put("downloaded", progressDownloaded.get());
        o.put("skipped", progressSkipped.get());
        o.put("failed", progressFailed.get());
        o.put("remaining", progressRemaining.get());
        o.put("message", progressMessage);
        o.put("paused", paused.get() || progressStatus.get() == 3);
        o.put("albumCount", batchAlbumCount);
        o.put("albumIndex", batchAlbumIndex);
        o.put("currentAlbumName", batchCurrentAlbumName);
        o.put("batchCompletedNeed", batchCompletedNeed.get());
        o.put("batchCompletedDownloaded", batchCompletedDownloaded.get());
        o.put("batchCompletedSkipped", batchCompletedSkipped.get());
        o.put("batchCompletedFailed", batchCompletedFailed.get());
        o.put("batchCompletedRemaining", batchCompletedRemaining.get());
        if (!failedFiles.isEmpty()) {
            o.put("failedFiles", JSON.parseArray(JSON.toJSONString(failedFiles)));
        }
        return o;
    }

    private void applyPersistedProgress(JSONObject o, boolean processRestarted) {
        if (o == null || o.isEmpty()) {
            return;
        }
        int status = o.getIntValue("status");
        int remaining = o.getIntValue("remaining");
        if (processRestarted && status == 0 && remaining > 0) {
            status = 3;
        }
        progressStatus.set(status);
        progressPhase = o.getString("phase");
        progressTotal.set(o.getIntValue("total"));
        progressNeed.set(o.getIntValue("needDownload"));
        progressDownloaded.set(o.getIntValue("downloaded"));
        progressSkipped.set(o.getIntValue("skipped"));
        progressFailed.set(o.getIntValue("failed"));
        progressRemaining.set(remaining);
        progressMessage = o.getString("message");
        batchAlbumCount = o.getIntValue("albumCount");
        batchAlbumIndex = o.getIntValue("albumIndex");
        batchCurrentAlbumName = nvl(o.getString("currentAlbumName"));
        batchCompletedNeed.set(o.getIntValue("batchCompletedNeed"));
        batchCompletedDownloaded.set(o.getIntValue("batchCompletedDownloaded"));
        batchCompletedSkipped.set(o.getIntValue("batchCompletedSkipped"));
        batchCompletedFailed.set(o.getIntValue("batchCompletedFailed"));
        batchCompletedRemaining.set(o.getIntValue("batchCompletedRemaining"));
        paused.set(status == 3 || Boolean.TRUE.equals(o.getBoolean("paused")));
        if (status == 3 && remaining > 0 && StringUtils.isEmpty(progressMessage)) {
            progressMessage = "已暂停，剩余 " + remaining + " 个未下载";
            progressPhase = "paused";
        }
        failedFiles.clear();
        com.alibaba.fastjson2.JSONArray arr = o.getJSONArray("failedFiles");
        if (arr != null) {
            for (int i = 0; i < arr.size(); i++) {
                JSONObject row = arr.getJSONObject(i);
                if (row == null) {
                    continue;
                }
                AliyunSyncProgress.FailedFile f = new AliyunSyncProgress.FailedFile();
                f.setName(row.getString("name"));
                f.setReason(row.getString("reason"));
                failedFiles.add(f);
            }
        }
    }

    private void refreshRemaining() {
        progressRemaining.set(Math.max(0,
                progressNeed.get() - progressDownloaded.get() - progressGone.get()));
        updateBatchProgressMessage();
    }

    private void resetBatchProgress(int albumCount) {
        batchAlbumCount = Math.max(1, albumCount);
        batchAlbumIndex = 0;
        batchCurrentAlbumName = "";
        batchCompletedNeed.set(0);
        batchCompletedDownloaded.set(0);
        batchCompletedSkipped.set(0);
        batchCompletedFailed.set(0);
        batchCompletedRemaining.set(0);
    }

    private void accumulateCompletedAlbumProgress() {
        batchCompletedNeed.addAndGet(progressNeed.get());
        batchCompletedDownloaded.addAndGet(progressDownloaded.get());
        batchCompletedSkipped.addAndGet(progressSkipped.get());
        batchCompletedFailed.addAndGet(progressFailed.get());
        batchCompletedRemaining.addAndGet(progressRemaining.get());
    }

    private void fillBatchFields(AliyunSyncProgress p) {
        int count = batchAlbumCount > 0 ? batchAlbumCount : 1;
        p.setAlbumCount(count);
        p.setAlbumIndex(batchAlbumIndex);
        p.setCurrentAlbumName(batchCurrentAlbumName);
        int currentNeed = progressNeed.get();
        int overallNeed = batchCompletedNeed.get() + currentNeed;
        int overallDownloaded = batchCompletedDownloaded.get() + progressDownloaded.get();
        int overallSkipped = batchCompletedSkipped.get() + progressSkipped.get();
        int overallFailed = batchCompletedFailed.get() + progressFailed.get();
        int overallRemaining = batchCompletedRemaining.get() + progressRemaining.get();
        p.setOverallNeed(overallNeed);
        p.setOverallDownloaded(overallDownloaded);
        p.setOverallSkipped(overallSkipped);
        p.setOverallFailed(overallFailed);
        p.setOverallRemaining(Math.max(0, overallRemaining));
        int pending = count - batchAlbumIndex;
        if (batchAlbumIndex <= 0) {
            pending = count;
        } else if (progressStatus.get() == 1) {
            pending = 0;
        } else {
            pending = Math.max(0, pending);
        }
        p.setAlbumsPending(pending);
    }

    private String buildBatchProgressMessage(String detail) {
        if (batchAlbumCount <= 1) {
            return detail == null ? "" : detail;
        }
        String albumPart = String.format(Locale.ROOT, "相册 %d/%d", batchAlbumIndex, batchAlbumCount);
        if (StringUtils.isNotEmpty(batchCurrentAlbumName)) {
            albumPart += "：" + batchCurrentAlbumName;
        }
        int overallNeed = batchCompletedNeed.get() + progressNeed.get();
        int overallRemaining = batchCompletedRemaining.get() + progressRemaining.get();
        String overallPart = String.format(Locale.ROOT,
                "累计待下 %d，剩余 %d", overallNeed, Math.max(0, overallRemaining));
        if (batchAlbumIndex < batchAlbumCount) {
            overallPart += String.format(Locale.ROOT, "（另有 %d 个相册待列举）",
                    Math.max(0, batchAlbumCount - batchAlbumIndex));
        }
        if (StringUtils.isEmpty(detail)) {
            return albumPart + " · " + overallPart;
        }
        return albumPart + " · " + detail + " · " + overallPart;
    }

    private void updateBatchProgressMessage() {
        if (batchAlbumCount <= 1 || progressStatus.get() != 0) {
            return;
        }
        if (!"downloading".equals(progressPhase) && !"listing".equals(progressPhase)) {
            return;
        }
        String detail;
        if ("listing".equals(progressPhase)) {
            detail = "正在列举文件";
        } else if (progressRemaining.get() > 0) {
            detail = String.format(Locale.ROOT, "本相册剩余 %d", progressRemaining.get());
        } else {
            detail = "本相册下载中";
        }
        progressMessage = buildBatchProgressMessage(detail);
    }

    private static String nvl(String s) {
        return s == null ? "" : s.trim();
    }

    private void markFileFailed(DriveFile file, Throwable e, AtomicInteger failed) {
        failed.incrementAndGet();
        progressFailed.incrementAndGet();
        AliyunSyncProgress.FailedFile row = new AliyunSyncProgress.FailedFile();
        row.setName(file == null || file.name == null ? "" : file.name);
        row.setReason(e == null || e.getMessage() == null ? "unknown" : e.getMessage());
        failedFiles.add(row);
        while (failedFiles.size() > 30) {
            failedFiles.remove(0);
        }
        refreshRemaining();
        log.info("下载失败 name={}：{}", row.getName(), row.getReason());
    }

    private void beginCurrentFile(String key, String name, long expected) {
        AliyunSyncProgress.CurrentFile f = new AliyunSyncProgress.CurrentFile();
        f.setName(name);
        f.setExpected(expected);
        f.setWritten(0L);
        f.setPercent(0);
        currentFiles.put(key, f);
        progressMessage = buildBatchProgressMessage("正在下载 " + (name == null ? "" : name));
    }

    private void updateCurrentFile(String key, String name, long written, long expected) {
        AliyunSyncProgress.CurrentFile f = currentFiles.get(key);
        if (f == null) {
            f = new AliyunSyncProgress.CurrentFile();
            f.setName(name);
            currentFiles.put(key, f);
        }
        f.setName(name);
        f.setWritten(written);
        f.setExpected(expected);
        if (expected > 0) {
            f.setPercent((int) Math.min(99, (written * 100) / expected));
        }
    }

    private void endCurrentFile(String key) {
        currentFiles.remove(key);
    }

    private int calcPercent(AliyunSyncProgress p) {
        if (p.getStatus() == 1) {
            return 100;
        }
        int need = p.getOverallNeed() > 0 ? p.getOverallNeed() : p.getNeedDownload();
        int downloaded = p.getOverallNeed() > 0 ? p.getOverallDownloaded() : p.getDownloaded();
        int failed = p.getOverallNeed() > 0 ? p.getOverallFailed() : p.getFailed();
        if (need <= 0) {
            return "listing".equals(p.getPhase()) ? 0 : ((p.getStatus() == 2 || p.getRemaining() <= 0) ? 100 : 0);
        }
        double done = downloaded + failed;
        for (AliyunSyncProgress.CurrentFile f : p.getCurrentFiles()) {
            if (f.getExpected() > 0) {
                done += Math.min(0.99d, (double) f.getWritten() / (double) f.getExpected());
            }
        }
        int pct = (int) Math.floor(done * 100.0d / need);
        if (p.getStatus() == 0) {
            return Math.min(99, Math.max(0, pct));
        }
        return Math.min(99, Math.max(0, pct));
    }

    private AlbumProperties.AliyunDriveConfig resolveConfig() {
        try {
            if (aliyunDriveSettingService != null) {
                return aliyunDriveSettingService.getEffectiveConfig();
            }
        } catch (Exception e) {
            log.warn("读取云盘页面配置失败，回落 yml：{}", e.getMessage());
        }
        return albumProperties.getAliyunDrive();
    }

    private static AlbumProperties.AliyunDriveConfig copyDriveCfg(AlbumProperties.AliyunDriveConfig src) {
        if (src == null) {
            return new AlbumProperties.AliyunDriveConfig();
        }
        return JSON.parseObject(JSON.toJSONString(src), AlbumProperties.AliyunDriveConfig.class);
    }

    private Long resolveScanPathId(AlbumProperties.AliyunDriveConfig cfg) {
        if (cfg.getScanPathId() != null) {
            return cfg.getScanPathId();
        }
        String want = normalizePath(cfg.getLocalPath());
        List<BizScanPath> list = scanPathService.list(new LambdaQueryWrapper<BizScanPath>()
                .eq(BizScanPath::getDeleted, 0)
                .eq(BizScanPath::getStatus, 1));
        for (BizScanPath p : list) {
            if (p.getLocalPath() != null && want.equals(normalizePath(p.getLocalPath()))) {
                return p.getPathId();
            }
        }
        return null;
    }

    private static String pickLocalName(DriveFile file, JSONObject fileState, Path localDir) {
        JSONObject prev = fileState.getJSONObject(file.fileId);
        if (prev != null && StringUtils.isNotEmpty(prev.getString("localName"))) {
            return sanitizeFileName(prev.getString("localName"));
        }
        String base = sanitizeFileName(file.name);
        Path candidate = localDir.resolve(base);
        if (!Files.exists(candidate) || isCompleteLocalFile(candidate, file.size)) {
            return base;
        }
        String shortId = file.fileId.length() > 8 ? file.fileId.substring(0, 8) : file.fileId;
        int dot = base.lastIndexOf('.');
        return dot > 0
                ? base.substring(0, dot) + "_" + shortId + base.substring(dot)
                : base + "_" + shortId;
    }

    private static boolean isRememberedMissing(DriveFile file, JSONObject fileState) {
        if (file == null || StringUtils.isEmpty(file.fileId) || fileState == null) {
            return false;
        }
        JSONObject prev = fileState.getJSONObject(file.fileId);
        return prev != null && Boolean.TRUE.equals(prev.getBoolean("missing"));
    }

    private static boolean isRemoteGone(Throwable e) {
        if (e == null || e.getMessage() == null) {
            return false;
        }
        String msg = e.getMessage().toLowerCase(Locale.ROOT);
        return msg.contains("404") || msg.contains("cannot be found") || msg.contains("notfound");
    }

    private static void rememberMissing(JSONObject fileState, DriveFile file) {
        JSONObject row = new JSONObject();
        row.put("name", file.name);
        row.put("size", file.size);
        row.put("missing", true);
        row.put("driveId", file.driveId);
        fileState.put(file.fileId, row);
    }

    private static void remember(JSONObject fileState, DriveFile file, String localName) {
        JSONObject row = new JSONObject();
        row.put("name", file.name);
        row.put("size", file.size);
        row.put("contentHash", file.contentHash);
        row.put("localName", localName);
        row.put("driveId", file.driveId);
        fileState.put(file.fileId, row);
    }

    private static boolean isMedia(DriveFile file) {
        if (file == null) {
            return false;
        }
        if (isMediaName(file.name)) {
            return true;
        }
        String cat = file.category == null ? "" : file.category.toLowerCase(Locale.ROOT);
        return "image".equals(cat) || "video".equals(cat);
    }

    private static boolean isMediaName(String name) {
        if (StringUtils.isEmpty(name)) {
            return false;
        }
        int i = name.lastIndexOf('.');
        if (i < 0 || i == name.length() - 1) {
            return false;
        }
        return MEDIA_EXT.contains(name.substring(i + 1).toLowerCase(Locale.ROOT));
    }

    private static String sanitizeFileName(String name) {
        if (name == null) {
            return "unnamed";
        }
        String s = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return s.isEmpty() ? "unnamed" : s;
    }

    private static String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        try {
            return Paths.get(path).toAbsolutePath().normalize().toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return path.replace('\\', '/').toLowerCase(Locale.ROOT);
        }
    }

    private static class SyncResult {
        String albumId;
        int remoteMedia;
        int downloaded;
        int skipped;
        int failed;
        boolean paused;
    }

    private static class TokenStore {
        String refreshToken;
        JSONObject files = new JSONObject();
        JSONObject syncProgress;

        static TokenStore load(String tokenFile) {
            TokenStore store = new TokenStore();
            if (StringUtils.isEmpty(tokenFile)) {
                return store;
            }
            Path path = Paths.get(tokenFile);
            if (!Files.isRegularFile(path)) {
                return store;
            }
            try {
                String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                JSONObject obj = JSON.parseObject(text);
                if (obj != null) {
                    store.refreshToken = obj.getString("refreshToken");
                    JSONObject files = obj.getJSONObject("files");
                    if (files != null) {
                        store.files = files;
                    }
                    store.syncProgress = obj.getJSONObject("syncProgress");
                }
            } catch (Exception e) {
                log.warn("读取 tokenFile 失败 {}: {}", tokenFile, e.getMessage());
            }
            return store;
        }

        void save(String tokenFile) {
            if (StringUtils.isEmpty(tokenFile)) {
                return;
            }
            synchronized (this) {
                try {
                    Path path = Paths.get(tokenFile);
                    Path parent = path.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    JSONObject obj = new JSONObject();
                    obj.put("refreshToken", refreshToken);
                    obj.put("files", files == null ? new JSONObject() : files);
                    if (syncProgress != null) {
                        obj.put("syncProgress", syncProgress);
                    }
                    Files.write(path, obj.toJSONString().getBytes(StandardCharsets.UTF_8));
                } catch (Exception e) {
                    log.warn("写入 tokenFile 失败 {}: {}", tokenFile, e.getMessage());
                }
            }
        }
    }
}
