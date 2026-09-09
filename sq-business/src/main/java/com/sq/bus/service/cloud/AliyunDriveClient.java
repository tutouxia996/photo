package com.sq.bus.service.cloud;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.sq.bus.config.AlbumProperties;
import com.sq.common.exception.ServiceException;
import com.sq.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 阿里云盘网页接口（refresh_token）：鉴权、个人相册列举、下载。
 */
public class AliyunDriveClient {

    private static final Logger log = LoggerFactory.getLogger(AliyunDriveClient.class);

    public interface ProgressCallback {
        void onBytes(long written, long expected);
    }

    private static final String[] AUTH_HOSTS = {
            "https://auth.alipan.com",
            "https://auth.aliyundrive.com"
    };
    private static final String[] API_HOSTS = {
            "https://api.alipan.com",
            "https://api.aliyundrive.com"
    };

    private final AlbumProperties.AliyunDriveConfig cfg;
    private String accessToken;
    private String refreshToken;
    private String defaultDriveId;
    private String albumDriveId;
    private String backupDriveId;
    private String resourceDriveId;
    private String apiHost = API_HOSTS[0];
    /** 记住可用的下载 Referer，避免每个文件都试错 */
    private volatile String stickyDownloadReferer;

    public AliyunDriveClient(AlbumProperties.AliyunDriveConfig cfg, String refreshToken) {
        this.cfg = cfg;
        this.refreshToken = refreshToken;
        this.stickyDownloadReferer = StringUtils.isNotEmpty(cfg.getDownloadReferer())
                ? cfg.getDownloadReferer() : "https://www.aliyundrive.com/";
    }

    public synchronized String getRefreshToken() {
        return refreshToken;
    }

    public void ensureLogin() {
        if (StringUtils.isEmpty(refreshToken)) {
            throw new ServiceException("未配置阿里云盘 refreshToken");
        }
        refreshAccessToken();
        loadAlbumDriveId();
    }

    public String resolveAlbumId(String albumId, String albumName) {
        if (StringUtils.isNotEmpty(albumId)) {
            return albumId.trim();
        }
        if (StringUtils.isEmpty(albumName)) {
            throw new ServiceException("请配置 album.aliyunDrive.remoteAlbumId 或 remoteAlbumName");
        }
        String want = albumName.trim();
        for (JSONObject item : listAlbums()) {
            String name = item.getString("name");
            if (want.equals(name)) {
                String id = item.getString("album_id");
                if (StringUtils.isEmpty(id)) {
                    id = item.getString("albumId");
                }
                if (StringUtils.isNotEmpty(id)) {
                    return id;
                }
            }
        }
        throw new ServiceException("未找到名为「" + want + "」的个人相册");
    }

    public List<JSONObject> listAlbums() {
        ensureDriveIds();
        List<JSONObject> all = new ArrayList<JSONObject>();
        String marker = null;
        do {
            JSONObject body = new JSONObject();
            body.put("album_drive_id", albumDriveId);
            body.put("drive_id", defaultDriveId);
            body.put("limit", 100);
            if (StringUtils.isNotEmpty(marker)) {
                body.put("marker", marker);
            }
            JSONObject resp = postApi("/adrive/v1/album/list", body);
            JSONArray items = resp.getJSONArray("items");
            if (items != null) {
                for (int i = 0; i < items.size(); i++) {
                    all.add(items.getJSONObject(i));
                }
            }
            marker = firstNonEmpty(resp.getString("next_marker"), resp.getString("nextMarker"));
        } while (StringUtils.isNotEmpty(marker));
        return all;
    }

    public List<DriveFile> listAlbumFiles(String albumId) {
        List<DriveFile> all = new ArrayList<DriveFile>();
        String marker = null;
        int page = 0;
        do {
            JSONObject body = new JSONObject();
            body.put("album_id", albumId);
            body.put("fields", "*");
            body.put("limit", 100);
            body.put("order_by", "joined_at");
            body.put("order_direction", "DESC");
            // 空 filter = 相册内全部文件（图片+视频）
            body.put("filter", "");
            if (StringUtils.isNotEmpty(marker)) {
                body.put("marker", marker);
            }
            JSONObject resp = postApi("/adrive/v1/album/list_files", body);
            JSONArray items = resp.getJSONArray("items");
            int pageCount = 0;
            if (items != null) {
                for (int i = 0; i < items.size(); i++) {
                    JSONObject raw = items.getJSONObject(i);
                    if (raw != null && raw.getJSONObject("file") != null) {
                        JSONObject inner = raw.getJSONObject("file");
                        JSONObject merged = new JSONObject();
                        merged.putAll(raw);
                        merged.putAll(inner);
                        raw = merged;
                    }
                    DriveFile f = DriveFile.from(raw);
                    if (f == null || StringUtils.isEmpty(f.fileId)) {
                        continue;
                    }
                    if ("folder".equalsIgnoreCase(f.type)) {
                        continue;
                    }
                    all.add(f);
                    pageCount++;
                }
            }
            page++;
            marker = firstNonEmpty(resp.getString("next_marker"), resp.getString("nextMarker"));
            log.info("阿里云盘相册列文件：第{}页 {} 条，累计 {}，还有下一页={}",
                    page, pageCount, all.size(), StringUtils.isNotEmpty(marker));
            // 防止异常死循环
            if (page > 5000) {
                log.warn("相册分页超过 5000 页，停止继续拉取");
                break;
            }
        } while (StringUtils.isNotEmpty(marker));
        return all;
    }

    public synchronized String getDownloadUrl(String driveId, String fileId) {
        ensureDriveIds();
        List<String> driveIds = new ArrayList<String>();
        if (StringUtils.isNotEmpty(driveId)) {
            driveIds.add(driveId);
        }
        if (StringUtils.isNotEmpty(defaultDriveId) && !driveIds.contains(defaultDriveId)) {
            driveIds.add(defaultDriveId);
        }
        if (StringUtils.isNotEmpty(albumDriveId) && !driveIds.contains(albumDriveId)) {
            driveIds.add(albumDriveId);
        }
        if (StringUtils.isNotEmpty(resourceDriveId) && !driveIds.contains(resourceDriveId)) {
            driveIds.add(resourceDriveId);
        }
        if (StringUtils.isNotEmpty(backupDriveId) && !driveIds.contains(backupDriveId)) {
            driveIds.add(backupDriveId);
        }
        Exception last = null;
        for (String did : driveIds) {
            for (int attempt = 1; attempt <= 3; attempt++) {
                try {
                    String url = getDownloadUrlOnce(did, fileId);
                    if (StringUtils.isNotEmpty(driveId) && !driveId.equals(did)) {
                        log.info("下载地址改用 driveId={}（原 driveId={}）fileId={}", did, driveId, fileId);
                    }
                    return url;
                } catch (Exception e) {
                    last = e;
                    String msg = e.getMessage() == null ? "" : e.getMessage();
                    boolean notFound = msg.contains("404") || msg.toLowerCase(Locale.ROOT).contains("cannot be found")
                            || msg.toLowerCase(Locale.ROOT).contains("notfound");
                    if (notFound) {
                        log.info("driveId={} 无此文件，尝试下一盘 fileId={}", did, fileId);
                        break;
                    }
                    boolean rateLimited = msg.contains("429") || msg.toLowerCase(Locale.ROOT).contains("too many")
                            || msg.toLowerCase(Locale.ROOT).contains("throttl");
                    if (rateLimited && attempt < 3) {
                        log.info("获取下载地址限流，{}ms 后重试 fileId={} driveId={}", 700 * attempt, fileId, did);
                        try {
                            Thread.sleep(700L * attempt);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new ServiceException("获取下载地址被中断");
                        }
                        continue;
                    }
                    break;
                }
            }
        }
        throw last == null
                ? new ServiceException("获取原文件下载地址失败：fileId=" + fileId)
                : (last instanceof RuntimeException ? (RuntimeException) last : new ServiceException(last.getMessage()));
    }

    private String getDownloadUrlOnce(String driveId, String fileId) {
        JSONObject body = new JSONObject();
        body.put("drive_id", driveId);
        body.put("file_id", fileId);
        // 原文件下载（不要带 image_thumbnail_process / video_thumbnail_process）
        body.put("expire_sec", 14400);
        JSONObject resp = postApi("/v2/file/get_download_url", body);
        String url = firstNonEmpty(
                resp.getString("url"),
                resp.getString("download_url"),
                resp.getString("cdn_url"));
        if (StringUtils.isEmpty(url)) {
            throw new ServiceException("获取原文件下载地址失败：fileId=" + fileId + " driveId=" + driveId);
        }
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.contains("image/resize") || lower.contains("video/snapshot") || lower.contains("thumbnail")) {
            throw new ServiceException("接口返回了缩略图地址而非原文件：fileId=" + fileId);
        }
        return url;
    }

    /**
     * 下载到目标文件；expectedSize>0 时校验落盘大小。
     * <p>
     * 大文件使用 HTTP Range 多分片并行（接近官方客户端多连接加速）；
     * CDN 不支持 Range / 签名不匹配时自动回退单连接。
     * </p>
     */
    public void downloadTo(String downloadUrl, Path target, long expectedSize) throws Exception {
        downloadTo(downloadUrl, target, expectedSize, null);
    }

    public void downloadTo(String downloadUrl, Path target, long expectedSize,
                           ProgressCallback progress) throws Exception {
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path part = target.resolveSibling(target.getFileName().toString() + ".aliyun.part");
        Files.deleteIfExists(part);

        int chunks = cfg.getChunkConcurrency();
        long minSize = cfg.getMultipartMinBytes() > 0 ? cfg.getMultipartMinBytes() : (2L * 1024 * 1024);
        if (expectedSize >= minSize && chunks > 1) {
            Exception lastMp = null;
            for (String referer : refererCandidates(preferredReferer())) {
                try {
                    downloadMultipart(downloadUrl, target, part, expectedSize, referer, chunks, progress);
                    stickyDownloadReferer = referer;
                    return;
                } catch (Exception e) {
                    lastMp = e;
                    Files.deleteIfExists(part);
                    String msg = e.getMessage() == null ? "" : e.getMessage();
                    if (msg.contains("403") || msg.contains("SignatureDoesNotMatch")) {
                        continue;
                    }
                    break;
                }
            }
            log.warn("分片下载失败，回退单连接 {}：{}", target.getFileName(),
                    lastMp == null ? "unknown" : lastMp.getMessage());
            Files.deleteIfExists(part);
        }
        downloadSingleWithRefererFallback(downloadUrl, target, part, expectedSize, progress);
    }

    private String preferredReferer() {
        if (StringUtils.isNotEmpty(stickyDownloadReferer)) {
            return stickyDownloadReferer;
        }
        return StringUtils.isNotEmpty(cfg.getDownloadReferer())
                ? cfg.getDownloadReferer() : "https://www.aliyundrive.com/";
    }

    private String[] refererCandidates(String primary) {
        List<String> list = new ArrayList<String>();
        if (StringUtils.isNotEmpty(primary)) {
            list.add(primary);
        }
        // 当前账号签名多为 aliyundrive.com；alipan 放后减少首包 403
        for (String r : new String[]{
                "https://www.aliyundrive.com/",
                "https://www.alipan.com/",
                "https://www.aliyundrive.com",
                "https://www.alipan.com"
        }) {
            if (!list.contains(r)) {
                list.add(r);
            }
        }
        return list.toArray(new String[0]);
    }

    private void downloadSingleWithRefererFallback(String downloadUrl, Path target, Path part,
                                                   long expectedSize, ProgressCallback progress) throws Exception {
        Exception last = null;
        for (String referer : refererCandidates(preferredReferer())) {
            try {
                downloadSingle(downloadUrl, target, part, expectedSize, referer, progress);
                stickyDownloadReferer = referer;
                return;
            } catch (Exception e) {
                last = e;
                String msg = e.getMessage() == null ? "" : e.getMessage();
                Files.deleteIfExists(part);
                if (msg.contains("403") || msg.contains("SignatureDoesNotMatch")) {
                    log.debug("单连接 Referer={} 失败，尝试下一个：{}", referer, msg);
                    continue;
                }
                throw e;
            }
        }
        throw last == null ? new ServiceException("下载失败") : last;
    }

    private void downloadMultipart(String downloadUrl, Path target, Path part,
                                   long expectedSize, String referer, int chunkCount,
                                   ProgressCallback progress) throws Exception {
        String finalUrl = looksLikeCdnUrl(downloadUrl)
                ? downloadUrl
                : resolveFinalDownloadUrl(downloadUrl, referer);
        int n = Math.max(2, chunkCount);
        while (n > 2 && expectedSize / n < 512L * 1024) {
            n--;
        }
        long chunkSize = (expectedSize + n - 1) / n;
        log.info("分片下载 {}：size={}，分片={}，每片约 {} KB，Referer={}",
                target.getFileName(), expectedSize, n, chunkSize / 1024, referer);

        Files.deleteIfExists(part);
        try (java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(
                part,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.WRITE,
                java.nio.file.StandardOpenOption.READ,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {
            channel.truncate(expectedSize);
        }

        final AtomicLong writtenTotal = new AtomicLong(0);
        final AtomicLong lastLogAt = new AtomicLong(System.currentTimeMillis());
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(n);
        List<java.util.concurrent.Future<?>> futures = new ArrayList<java.util.concurrent.Future<?>>();
        final String urlFinal = finalUrl;
        final String refererFinal = referer;
        final Path partFinal = part;
        final long expectedFinal = expectedSize;
        try {
            for (int i = 0; i < n; i++) {
                final long start = i * chunkSize;
                if (start >= expectedSize) {
                    break;
                }
                final long end = Math.min(expectedSize - 1, start + chunkSize - 1);
                final int idx = i;
                futures.add(pool.submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            downloadRangeToFile(urlFinal, refererFinal, partFinal, start, end,
                                    writtenTotal, lastLogAt, expectedFinal, target.getFileName().toString(),
                                    progress);
                        } catch (Exception e) {
                            throw new RuntimeException("分片" + idx + "失败 [" + start + "-" + end + "]：" + e.getMessage(), e);
                        }
                    }
                }));
            }
            for (java.util.concurrent.Future<?> f : futures) {
                f.get();
            }
        } catch (java.util.concurrent.ExecutionException ee) {
            Throwable c = ee.getCause() != null ? ee.getCause() : ee;
            throw new ServiceException(c.getMessage() == null ? "分片下载失败" : c.getMessage());
        } finally {
            pool.shutdownNow();
        }

        long actual = Files.size(part);
        if (actual != expectedSize) {
            throw new ServiceException("分片合并后大小不一致：期望 " + expectedSize + "，实际 " + actual);
        }
        Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
        stickyDownloadReferer = referer;
        log.info("分片下载完成 {}：{} 字节", target.getFileName(), actual);
    }

    private void downloadRangeToFile(String url, String referer, Path part,
                                     long start, long end,
                                     AtomicLong writtenTotal, AtomicLong lastLogAt,
                                     long expectedSize, String displayName,
                                     ProgressCallback progress) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = openDownload(url, referer, start, end);
            int code = conn.getResponseCode();
            if (isRedirect(code)) {
                String loc = conn.getHeaderField("Location");
                conn.disconnect();
                conn = null;
                if (StringUtils.isEmpty(loc)) {
                    throw new ServiceException("分片重定向无 Location");
                }
                // Location 必须按原样拼到绝对 URL；不要二次编码
                String next = resolveRedirectLocation(url, loc);
                conn = openDownload(next, referer, start, end);
                code = conn.getResponseCode();
            }
            // 分片必须 206；若返回 200 说明 CDN 忽略了 Range，需回退单连接
            if (code != 206) {
                String err = code >= 400
                        ? readStreamQuiet(conn.getErrorStream())
                        : ("HTTP " + code + "（不支持 Range）");
                throw new ServiceException("HTTP " + code + "：" + abbreviateOssError(err));
            }
            long expectLen = end - start + 1;
            long got = 0L;
            try (InputStream in = new BufferedInputStream(conn.getInputStream());
                 java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(
                         part, java.nio.file.StandardOpenOption.WRITE)) {
                byte[] buf = new byte[256 * 1024];
                long pos = start;
                int n;
                while (got < expectLen && (n = in.read(buf, 0, (int) Math.min(buf.length, expectLen - got))) >= 0) {
                    java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(buf, 0, n);
                    while (bb.hasRemaining()) {
                        int w = channel.write(bb, pos);
                        pos += w;
                    }
                    got += n;
                    long total = writtenTotal.addAndGet(n);
                    notifyProgress(progress, total, expectedSize);
                    long now = System.currentTimeMillis();
                    long prev = lastLogAt.get();
                    if (now - prev >= 10000L && lastLogAt.compareAndSet(prev, now)) {
                        int pct = expectedSize > 0 ? (int) Math.min(99, (total * 100) / expectedSize) : 0;
                        log.info("下载中 {}：{} / {}（{}%），分片并行中", displayName, total, expectedSize, pct);
                    }
                }
            }
            if (got != expectLen) {
                throw new ServiceException("分片字节不足：期望 " + expectLen + "，实际 " + got);
            }
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private void downloadSingle(String downloadUrl, Path target, Path part,
                                long expectedSize, String referer, ProgressCallback progress) throws Exception {
        HttpURLConnection conn = null;
        try {
            Files.deleteIfExists(part);
            conn = openDownload(downloadUrl, referer, -1L, -1L);
            int code = conn.getResponseCode();
            if (isRedirect(code)) {
                String loc = conn.getHeaderField("Location");
                conn.disconnect();
                conn = null;
                if (StringUtils.isEmpty(loc)) {
                    throw new ServiceException("下载重定向无 Location，HTTP " + code);
                }
                conn = openDownload(resolveRedirectLocation(downloadUrl, loc), referer, -1L, -1L);
                code = conn.getResponseCode();
            }
            if (code >= 400) {
                String err = readStreamQuiet(conn.getErrorStream());
                throw new ServiceException("下载失败 HTTP " + code + "：" + abbreviateOssError(err));
            }
            log.info("单连接下载 {}（期望 {} 字节）", target.getFileName(), expectedSize);
            long written = 0L;
            long lastLogAt = System.currentTimeMillis();
            try (InputStream in = new BufferedInputStream(conn.getInputStream());
                 OutputStream out = new BufferedOutputStream(Files.newOutputStream(part))) {
                byte[] buf = new byte[256 * 1024];
                int n;
                while ((n = in.read(buf)) >= 0) {
                    out.write(buf, 0, n);
                    written += n;
                    notifyProgress(progress, written, expectedSize);
                    long now = System.currentTimeMillis();
                    if (now - lastLogAt >= 10000L) {
                        if (expectedSize > 0) {
                            int pct = (int) Math.min(99, (written * 100) / expectedSize);
                            log.info("下载中 {}：{} / {}（{}%）", target.getFileName(), written, expectedSize, pct);
                        } else {
                            log.info("下载中 {}：已写入 {} 字节", target.getFileName(), written);
                        }
                        lastLogAt = now;
                    }
                }
                out.flush();
            }
            if (written <= 0) {
                throw new ServiceException("下载结果为空（0 字节）");
            }
            if (expectedSize > 0 && written != expectedSize) {
                throw new ServiceException("下载大小不一致：期望 " + expectedSize + "，实际 " + written);
            }
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
            stickyDownloadReferer = referer;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static void notifyProgress(ProgressCallback progress, long written, long expected) {
        if (progress != null) {
            try {
                progress.onBytes(written, expected);
            } catch (Exception ignored) {
                // ignore
            }
        }
    }

    /**
     * 无 Range 跟随重定向，得到最终 CDN URL。200 时立即断开，不读 body。
     */
    private static boolean looksLikeCdnUrl(String url) {
        if (StringUtils.isEmpty(url)) {
            return false;
        }
        String u = url.toLowerCase(Locale.ROOT);
        return u.contains("aliyundrive.net")
                || u.contains("alicloudccp.com")
                || u.contains("aliyuncs.com")
                || u.contains("alicdn.com");
    }

    private String resolveFinalDownloadUrl(String downloadUrl, String referer) throws Exception {
        String current = downloadUrl;
        for (int i = 0; i < 5; i++) {
            HttpURLConnection conn = openDownload(current, referer, -1L, -1L);
            try {
                int code = conn.getResponseCode();
                if (isRedirect(code)) {
                    String loc = conn.getHeaderField("Location");
                    if (StringUtils.isEmpty(loc)) {
                        return current;
                    }
                    current = resolveRedirectLocation(current, loc);
                    continue;
                }
                if (code >= 400) {
                    String err = readStreamQuiet(conn.getErrorStream());
                    throw new ServiceException("解析下载地址失败 HTTP " + code + "：" + abbreviateOssError(err));
                }
                // 已是最终地址；断开以免把整文件读进来
                return current;
            } finally {
                conn.disconnect();
            }
        }
        return current;
    }

    private static String resolveRedirectLocation(String currentUrl, String location) {
        if (location == null) {
            return currentUrl;
        }
        String loc = location.trim();
        // 绝对地址必须原样使用，勿 toExternalForm 重编码（会破坏 OSS Signature）
        if (loc.startsWith("http://") || loc.startsWith("https://")) {
            return loc;
        }
        try {
            return new URL(new URL(currentUrl), loc).toExternalForm();
        } catch (Exception e) {
            return loc;
        }
    }

    private static boolean isRedirect(int code) {
        return code == HttpURLConnection.HTTP_MOVED_TEMP || code == HttpURLConnection.HTTP_MOVED_PERM
                || code == HttpURLConnection.HTTP_SEE_OTHER || code == 307 || code == 308;
    }

    /**
     * CDN 下载：禁止 Authorization；不要改写预签名 URL；覆盖 Java 默认 User-Agent。
     * rangeStart&lt;0 表示整文件。
     */
    private HttpURLConnection openDownload(String downloadUrl, String referer,
                                           long rangeStart, long rangeEnd) throws Exception {
        // 切勿 URI.create：会重编码 query（Signature 里的 +/= 等），直接 SignatureDoesNotMatch
        URL url = new URL(downloadUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(false);
        conn.setConnectTimeout(Math.max(3000, cfg.getConnectTimeoutMs()));
        // 大视频空闲读超时：尊重 downloadTimeoutMs（默认 10 分钟），不再硬压到 180s
        int readTimeout = cfg.getDownloadTimeoutMs() > 0 ? cfg.getDownloadTimeoutMs() : 600000;
        conn.setReadTimeout(Math.max(60000, readTimeout));
        conn.setRequestMethod("GET");
        // 覆盖 JDK 默认 User-Agent: Java/x.x（部分 CDN 把 UA 算进 additional-headers）
        conn.setRequestProperty("User-Agent", "");
        if (StringUtils.isNotEmpty(referer)) {
            conn.setRequestProperty("Referer", referer);
        }
        if (rangeStart >= 0 && rangeEnd >= rangeStart) {
            conn.setRequestProperty("Range", "bytes=" + rangeStart + "-" + rangeEnd);
        }
        return conn;
    }

    private static String abbreviateOssError(String err) {
        if (err == null) {
            return "";
        }
        String code = null;
        int c0 = err.indexOf("<Code>");
        int c1 = err.indexOf("</Code>");
        if (c0 >= 0 && c1 > c0) {
            code = err.substring(c0 + 6, c1).trim();
        }
        String msg = null;
        int m0 = err.indexOf("<Message>");
        int m1 = err.indexOf("</Message>");
        if (m0 >= 0 && m1 > m0) {
            msg = err.substring(m0 + 9, m1).trim();
        }
        if (code != null || msg != null) {
            return (code == null ? "" : code) + (msg == null ? "" : (" " + msg));
        }
        return err.length() > 200 ? err.substring(0, 200) + "..." : err;
    }

    private void refreshAccessToken() {
        JSONObject body = new JSONObject();
        body.put("grant_type", "refresh_token");
        body.put("refresh_token", refreshToken);
        Exception last = null;
        for (String host : AUTH_HOSTS) {
            try {
                JSONObject resp = postJson(host + "/v2/account/token", body.toJSONString(), null);
                String access = resp.getString("access_token");
                String refresh = resp.getString("refresh_token");
                if (StringUtils.isEmpty(access) || StringUtils.isEmpty(refresh)) {
                    String code = resp.getString("code");
                    String msg = resp.getString("message");
                    throw new ServiceException("刷新 token 失败：" + (code == null ? resp.toJSONString() : code + " " + msg));
                }
                this.accessToken = access;
                this.refreshToken = refresh;
                this.defaultDriveId = firstNonEmpty(resp.getString("default_drive_id"), resp.getString("defaultDriveId"));
                this.resourceDriveId = firstNonEmpty(resp.getString("resource_drive_id"), resp.getString("resourceDriveId"));
                this.backupDriveId = firstNonEmpty(resp.getString("backup_drive_id"), resp.getString("backupDriveId"));
                log.info("阿里云盘 token 刷新成功 defaultDrive={} backupDrive={} resourceDrive={}",
                        defaultDriveId, backupDriveId, resourceDriveId);
                return;
            } catch (Exception e) {
                last = e;
                log.warn("auth host {} 失败: {}", host, e.getMessage());
            }
        }
        throw new ServiceException("刷新阿里云盘 token 失败：" + (last == null ? "unknown" : last.getMessage()));
    }

    private void loadAlbumDriveId() {
        JSONObject resp = postApi("/adrive/v1/user/albums_info", new JSONObject());
        JSONObject data = resp.getJSONObject("data");
        if (data == null) {
            data = resp;
        }
        albumDriveId = firstNonEmpty(data.getString("driveId"), data.getString("drive_id"));
        if (StringUtils.isEmpty(albumDriveId)) {
            throw new ServiceException("无法获取相册空间 driveId");
        }
        if (StringUtils.isEmpty(defaultDriveId)) {
            defaultDriveId = albumDriveId;
        }
    }

    private void ensureDriveIds() {
        if (StringUtils.isEmpty(accessToken)) {
            ensureLogin();
        }
        if (StringUtils.isEmpty(albumDriveId) || StringUtils.isEmpty(defaultDriveId)) {
            loadAlbumDriveId();
        }
    }

    private JSONObject postApi(String path, JSONObject body) {
        Exception last = null;
        List<String> hosts = new ArrayList<String>();
        hosts.add(apiHost);
        for (String h : API_HOSTS) {
            if (!hosts.contains(h)) {
                hosts.add(h);
            }
        }
        for (String host : hosts) {
            try {
                JSONObject resp = postJson(host + path, body == null ? "{}" : body.toJSONString(), accessToken);
                String code = resp.getString("code");
                if (StringUtils.isNotEmpty(code) && !"OK".equalsIgnoreCase(code) && !"Success".equalsIgnoreCase(code)) {
                    // AccessTokenExpired / TokenExpired → 刷新一次再试
                    if (code.toLowerCase().contains("token") || code.contains("AccessToken")) {
                        refreshAccessToken();
                        resp = postJson(host + path, body == null ? "{}" : body.toJSONString(), accessToken);
                        code = resp.getString("code");
                    }
                    if (StringUtils.isNotEmpty(code) && !"OK".equalsIgnoreCase(code) && !"Success".equalsIgnoreCase(code)
                            && resp.get("items") == null && resp.get("data") == null && resp.get("url") == null) {
                        throw new ServiceException(code + " " + resp.getString("message"));
                    }
                }
                this.apiHost = host;
                return resp;
            } catch (Exception e) {
                last = e;
                log.warn("api host {}{} 失败: {}", host, path, e.getMessage());
            }
        }
        throw new ServiceException("调用阿里云盘接口失败 " + path + "：" + (last == null ? "unknown" : last.getMessage()));
    }

    private JSONObject postJson(String url, String jsonBody, String bearer) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(Math.max(3000, cfg.getConnectTimeoutMs()));
        conn.setReadTimeout(Math.max(10000, cfg.getReadTimeoutMs()));
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Referer", "https://www.alipan.com/");
        conn.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        conn.setRequestProperty("x-canary", "client=web,app=adrive,version=v5");
        if (StringUtils.isNotEmpty(bearer)) {
            conn.setRequestProperty("Authorization", "Bearer " + bearer);
        }
        byte[] bytes = jsonBody.getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(bytes.length);
        OutputStream os = conn.getOutputStream();
        os.write(bytes);
        os.flush();
        os.close();

        int code = conn.getResponseCode();
        String text = readStreamQuiet(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
        conn.disconnect();
        JSONObject resp = StringUtils.isEmpty(text) ? new JSONObject() : JSON.parseObject(text);
        if (resp == null) {
            resp = new JSONObject();
        }
        if (code >= 400) {
            String msg = firstNonEmpty(resp.getString("message"), resp.getString("code"), text);
            throw new ServiceException("HTTP " + code + "：" + msg);
        }
        return resp;
    }

    private static String readStreamQuiet(InputStream in) {
        if (in == null) {
            return "";
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String firstNonEmpty(String... vals) {
        if (vals == null) {
            return null;
        }
        for (String v : vals) {
            if (StringUtils.isNotEmpty(v)) {
                return v;
            }
        }
        return null;
    }

    public static class DriveFile {
        public String driveId;
        public String fileId;
        public String name;
        public String type;
        public long size;
        public String contentHash;
        public String category;

        static DriveFile from(JSONObject o) {
            if (o == null) {
                return null;
            }
            DriveFile f = new DriveFile();
            f.driveId = firstNonEmpty(o.getString("drive_id"), o.getString("driveId"));
            f.fileId = firstNonEmpty(o.getString("file_id"), o.getString("fileId"));
            f.name = o.getString("name");
            f.type = o.getString("type");
            Long size = o.getLong("size");
            f.size = size == null ? 0L : size;
            f.contentHash = firstNonEmpty(o.getString("content_hash"), o.getString("contentHash"));
            f.category = o.getString("category");
            return f;
        }
    }
}
