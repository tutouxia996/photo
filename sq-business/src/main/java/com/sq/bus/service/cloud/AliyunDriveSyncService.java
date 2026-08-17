package com.sq.bus.service.cloud;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sq.bus.config.AlbumProperties;
import com.sq.bus.domain.BizScanPath;
import com.sq.bus.service.IBizScanPathService;
import com.sq.bus.service.cloud.AliyunDriveClient.DriveFile;
import com.sq.common.exception.ServiceException;
import com.sq.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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

    @Autowired
    private AlbumProperties albumProperties;

    @Autowired
    private IBizScanPathService scanPathService;

    /**
     * 同步下载 +（可选）触发扫描。供 Quartz 调用。
     */
    public String syncAndScan() {
        AlbumProperties.AliyunDriveConfig cfg = albumProperties.getAliyunDrive();
        if (cfg == null || !cfg.isEnabled()) {
            return "阿里云盘同步未启用（album.aliyunDrive.enabled=false）";
        }
        if (!running.compareAndSet(false, true)) {
            return "上一次同步仍在进行中，已跳过";
        }
        try {
            SyncResult result = doSync(cfg);
            String msg = String.format(Locale.ROOT,
                    "同步完成：相册=%s，远程媒体=%d，下载=%d，跳过(已存在)=%d，失败=%d",
                    result.albumId, result.remoteMedia, result.downloaded, result.skipped, result.failed);
            if (cfg.isTriggerScan()) {
                Long pathId = resolveScanPathId(cfg);
                if (pathId == null) {
                    msg += "；未触发扫描：请配置 scanPathId，或新增 localPath 对应的磁盘扫描目录并绑定相册";
                } else {
                    Long logId = scanPathService.startScanAsync(pathId, cfg.isFullScan());
                    msg += "；已触发扫描 pathId=" + pathId + " logId=" + logId
                            + (cfg.isFullScan() ? "（全量）" : "（增量）");
                }
            }
            log.info(msg);
            return msg;
        } finally {
            running.set(false);
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
        String refresh = StringUtils.isNotEmpty(store.refreshToken) ? store.refreshToken : cfg.getRefreshToken();
        if (StringUtils.isEmpty(refresh)) {
            throw new ServiceException("未配置 refreshToken（application-local.yml 或 tokenFile）");
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

        if (!needDownload.isEmpty()) {
            java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(
                    Math.min(concurrency, needDownload.size()),
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
                List<java.util.concurrent.Future<?>> futures = new ArrayList<java.util.concurrent.Future<?>>();
                for (final DriveFile file : needDownload) {
                    futures.add(pool.submit(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                downloadOne(clientRef, storeRef, stateLock, cfgFinal, localDirFinal, fileStateFinal, file,
                                        downloaded, failed, total);
                            } catch (Exception e) {
                                failed.incrementAndGet();
                                log.warn("下载失败 name={}: {}", file.name, e.getMessage());
                            }
                        }
                    }));
                }
                for (java.util.concurrent.Future<?> f : futures) {
                    try {
                        f.get();
                    } catch (Exception e) {
                        log.warn("下载任务异常: {}", e.getMessage());
                    }
                }
            } finally {
                pool.shutdownNow();
            }
        }

        store.refreshToken = client.getRefreshToken();
        synchronized (stateLock) {
            store.save(cfg.getTokenFile());
        }
        result.downloaded = downloaded.get();
        result.skipped = skipped.get();
        result.failed = failed.get();
        return result;
    }

    private void downloadOne(AliyunDriveClient client, TokenStore store, Object stateLock,
                             AlbumProperties.AliyunDriveConfig cfg, Path localDir, JSONObject fileState,
                             DriveFile file, AtomicInteger downloaded, AtomicInteger failed, int total) {
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
                client.downloadTo(url, target, file.size);
                ok = true;
                break;
            } catch (Exception e) {
                last = e;
                log.warn("下载重试 {}/2 {} : {}", i, file.name, e.getMessage());
                try {
                    Thread.sleep(800L * i);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        if (!ok) {
            failed.incrementAndGet();
            log.warn("下载失败 name={}: {}", file.name, last == null ? "unknown" : last.getMessage());
            return;
        }

        int d;
        synchronized (stateLock) {
            remember(fileState, file, localName);
            store.refreshToken = client.getRefreshToken();
            d = downloaded.incrementAndGet();
            if (d == 1 || d % 5 == 0) {
                store.save(cfg.getTokenFile());
            }
        }
        log.info("已下载 {}/{}：{} -> {}", d, total, file.name, target.getFileName());
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
    }

    private static class TokenStore {
        String refreshToken;
        JSONObject files = new JSONObject();

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
                    Files.write(path, obj.toJSONString().getBytes(StandardCharsets.UTF_8));
                } catch (Exception e) {
                    log.warn("写入 tokenFile 失败 {}: {}", tokenFile, e.getMessage());
                }
            }
        }
    }
}
