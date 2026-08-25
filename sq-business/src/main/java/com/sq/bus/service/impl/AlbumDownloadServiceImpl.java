package com.sq.bus.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sq.bus.config.AlbumProperties;
import com.sq.bus.constants.AlbumDeleted;
import com.sq.bus.domain.BizAlbum;
import com.sq.bus.domain.BizPhoto;
import com.sq.bus.domain.vo.AlbumDownloadProgress;
import com.sq.bus.service.IAlbumDownloadService;
import com.sq.bus.service.IBizAlbumService;
import com.sq.bus.service.IBizPhotoService;
import com.sq.bus.utils.PhotoFieldUtils;
import com.sq.common.exception.ServiceException;
import com.sq.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 相册 zip 后台打包：不阻塞页面请求，进度供右下角浮层轮询。
 */
@Service
public class AlbumDownloadServiceImpl implements IAlbumDownloadService {

    private static final Logger log = LoggerFactory.getLogger(AlbumDownloadServiceImpl.class);
    private static final long READY_TTL_MS = 60L * 60L * 1000L;

    @Autowired
    private IBizAlbumService albumService;

    @Autowired
    private IBizPhotoService photoService;

    @Autowired
    private AlbumProperties albumProperties;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "album-download-zip");
        t.setDaemon(true);
        return t;
    });

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<AlbumDownloadProgress> progress =
            new AtomicReference<AlbumDownloadProgress>(idle());
    private volatile File readyFile;
    private volatile long readyAt;
    private volatile String readyTaskId;

    @Override
    public AlbumDownloadProgress start(Long albumId) {
        if (albumId == null) {
            throw new ServiceException("相册 ID 无效");
        }
        if (running.get()) {
            AlbumDownloadProgress cur = snapshot();
            if (cur.isRunning()) {
                return cur;
            }
        }
        BizAlbum album = albumService.getById(albumId);
        if (album == null || album.getDeleted() == null || album.getDeleted() != AlbumDeleted.NORMAL) {
            throw new ServiceException("相册不存在或已删除");
        }
        List<BizPhoto> photos = photoService.list(new LambdaQueryWrapper<BizPhoto>()
                .eq(BizPhoto::getAlbumId, albumId)
                .eq(BizPhoto::getDeleted, AlbumDeleted.NORMAL)
                .orderByDesc(BizPhoto::getShootTime)
                .orderByDesc(BizPhoto::getPhotoId));
        if (photos == null || photos.isEmpty()) {
            throw new ServiceException("相册内没有可下载的文件");
        }

        cleanupReadyFile();
        final String taskId = UUID.randomUUID().toString().replace("-", "");
        final String zipName = buildAlbumZipFileName(album.getAlbumName(), albumId);
        AlbumDownloadProgress p = new AlbumDownloadProgress();
        p.setStatus(0);
        p.setRunning(true);
        p.setPhase("packing");
        p.setTaskId(taskId);
        p.setAlbumId(albumId);
        p.setAlbumName(album.getAlbumName());
        p.setFileName(zipName);
        p.setTotal(photos.size());
        p.setPacked(0);
        p.setSkipped(0);
        p.setPercent(0);
        p.setMessage("正在准备打包…");
        progress.set(p);
        readyTaskId = taskId;
        readyFile = null;
        readyAt = 0;

        if (!running.compareAndSet(false, true)) {
            return snapshot();
        }
        final List<BizPhoto> jobPhotos = photos;
        final Long jobAlbumId = albumId;
        final String jobAlbumName = album.getAlbumName();
        executor.execute(() -> packAsync(taskId, jobAlbumId, jobAlbumName, zipName, jobPhotos));
        return snapshot();
    }

    @Override
    public AlbumDownloadProgress getProgress() {
        expireReadyIfNeeded();
        return snapshot();
    }

    @Override
    public File resolveReadyFile(String taskId) {
        expireReadyIfNeeded();
        if (StringUtils.isEmpty(taskId) || readyFile == null || !taskId.equals(readyTaskId)) {
            return null;
        }
        if (!readyFile.exists() || !readyFile.isFile()) {
            return null;
        }
        AlbumDownloadProgress p = progress.get();
        if (p == null || !"ready".equals(p.getPhase())) {
            return null;
        }
        return readyFile;
    }

    @Override
    public void markConsumed(String taskId) {
        if (StringUtils.isEmpty(taskId) || !taskId.equals(readyTaskId)) {
            return;
        }
        AlbumDownloadProgress p = snapshot();
        p.setPhase("done");
        p.setRunning(false);
        p.setStatus(1);
        p.setPercent(100);
        p.setMessage("下载完成");
        progress.set(p);
        cleanupReadyFile();
    }

    private void packAsync(String taskId, Long albumId, String albumName, String zipName, List<BizPhoto> photos) {
        File zipFile = null;
        try {
            File dir = exportDir();
            if (!dir.exists() && !dir.mkdirs()) {
                throw new ServiceException("无法创建导出目录：" + dir.getAbsolutePath());
            }
            zipFile = new File(dir, taskId + ".zip");
            Set<String> usedNames = new HashSet<String>();
            byte[] buffer = new byte[1024 * 64];
            int packed = 0;
            int skipped = 0;
            long bytesTotal = 0;
            for (BizPhoto photo : photos) {
                if (photo != null && StringUtils.isNotEmpty(photo.getFilePath())) {
                    File origin = new File(photo.getFilePath());
                    if (origin.exists() && origin.isFile()) {
                        bytesTotal += origin.length();
                    }
                }
            }
            updateProgress(taskId, albumId, albumName, zipName, photos.size(), 0, 0, bytesTotal, 0,
                    "packing", "正在打包原图/原视频…", null);

            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
                zos.setLevel(Deflater.NO_COMPRESSION);
                long written = 0;
                for (BizPhoto photo : photos) {
                    if (photo == null || StringUtils.isEmpty(photo.getFilePath())) {
                        skipped++;
                        continue;
                    }
                    File origin = new File(photo.getFilePath());
                    if (!origin.exists() || !origin.isFile()) {
                        skipped++;
                        continue;
                    }
                    String entryName = uniqueZipEntryName(photo, origin, usedNames);
                    updateProgress(taskId, albumId, albumName, zipName, photos.size(), packed, skipped,
                            bytesTotal, written, "packing", "正在打包：" + entryName, entryName);
                    ZipEntry entry = new ZipEntry(entryName);
                    entry.setTime(origin.lastModified());
                    zos.putNextEntry(entry);
                    try (FileInputStream in = new FileInputStream(origin)) {
                        int n;
                        while ((n = in.read(buffer)) > 0) {
                            zos.write(buffer, 0, n);
                            written += n;
                        }
                    }
                    zos.closeEntry();
                    packed++;
                    int pct = photos.isEmpty() ? 100
                            : Math.min(99, (int) Math.round((packed * 100.0) / photos.size()));
                    updateProgress(taskId, albumId, albumName, zipName, photos.size(), packed, skipped,
                            bytesTotal, written, "packing", "已打包 " + packed + "/" + photos.size(), entryName);
                    AlbumDownloadProgress check = progress.get();
                    if (check != null) {
                        check.setPercent(pct);
                    }
                }
                if (packed == 0) {
                    throw new ServiceException("相册内原文件均不可访问，未打包任何媒体");
                }
                zos.finish();
            }

            readyFile = zipFile;
            readyAt = System.currentTimeMillis();
            readyTaskId = taskId;
            AlbumDownloadProgress done = new AlbumDownloadProgress();
            done.setStatus(1);
            done.setRunning(false);
            done.setPhase("ready");
            done.setTaskId(taskId);
            done.setAlbumId(albumId);
            done.setAlbumName(albumName);
            done.setFileName(zipName);
            done.setTotal(photos.size());
            done.setPacked(packed);
            done.setSkipped(skipped);
            done.setPercent(100);
            done.setBytesTotal(bytesTotal);
            done.setBytesWritten(zipFile.length());
            done.setCurrentFile("");
            done.setMessage("打包完成，准备下载…");
            progress.set(done);
        } catch (Exception e) {
            log.warn("相册打包失败 albumId={} taskId={}: {}", albumId, taskId, e.getMessage());
            if (zipFile != null && zipFile.exists()) {
                try {
                    Files.deleteIfExists(zipFile.toPath());
                } catch (Exception ignored) {
                    // ignore
                }
            }
            AlbumDownloadProgress fail = new AlbumDownloadProgress();
            fail.setStatus(2);
            fail.setRunning(false);
            fail.setPhase("failed");
            fail.setTaskId(taskId);
            fail.setAlbumId(albumId);
            fail.setAlbumName(albumName);
            fail.setFileName(zipName);
            fail.setTotal(photos == null ? 0 : photos.size());
            fail.setPercent(0);
            fail.setMessage(e.getMessage() == null ? "打包失败" : e.getMessage());
            progress.set(fail);
            readyFile = null;
            readyTaskId = null;
        } finally {
            running.set(false);
        }
    }

    private void updateProgress(String taskId, Long albumId, String albumName, String zipName,
                                int total, int packed, int skipped, long bytesTotal, long written,
                                String phase, String message, String currentFile) {
        AlbumDownloadProgress p = new AlbumDownloadProgress();
        p.setStatus(0);
        p.setRunning(true);
        p.setPhase(phase);
        p.setTaskId(taskId);
        p.setAlbumId(albumId);
        p.setAlbumName(albumName);
        p.setFileName(zipName);
        p.setTotal(total);
        p.setPacked(packed);
        p.setSkipped(skipped);
        p.setBytesTotal(bytesTotal);
        p.setBytesWritten(written);
        p.setCurrentFile(currentFile == null ? "" : currentFile);
        p.setMessage(message == null ? "" : message);
        int pct = total <= 0 ? 0 : Math.min(99, (int) Math.round((packed * 100.0) / total));
        p.setPercent(pct);
        progress.set(p);
    }

    private AlbumDownloadProgress snapshot() {
        AlbumDownloadProgress cur = progress.get();
        if (cur == null) {
            return idle();
        }
        AlbumDownloadProgress copy = new AlbumDownloadProgress();
        copy.setStatus(cur.getStatus());
        copy.setRunning(cur.isRunning());
        copy.setPhase(cur.getPhase());
        copy.setTaskId(cur.getTaskId());
        copy.setAlbumId(cur.getAlbumId());
        copy.setAlbumName(cur.getAlbumName());
        copy.setFileName(cur.getFileName());
        copy.setTotal(cur.getTotal());
        copy.setPacked(cur.getPacked());
        copy.setSkipped(cur.getSkipped());
        copy.setPercent(cur.getPercent());
        copy.setBytesTotal(cur.getBytesTotal());
        copy.setBytesWritten(cur.getBytesWritten());
        copy.setCurrentFile(cur.getCurrentFile());
        copy.setMessage(cur.getMessage());
        return copy;
    }

    private static AlbumDownloadProgress idle() {
        AlbumDownloadProgress p = new AlbumDownloadProgress();
        p.setStatus(1);
        p.setRunning(false);
        p.setPhase("done");
        p.setMessage("");
        return p;
    }

    private File exportDir() {
        String root = albumProperties.getLocalRoot();
        if (StringUtils.isEmpty(root)) {
            root = System.getProperty("java.io.tmpdir");
        }
        return new File(root, "cache/export");
    }

    private void expireReadyIfNeeded() {
        if (readyFile == null || readyAt <= 0) {
            return;
        }
        if (System.currentTimeMillis() - readyAt > READY_TTL_MS) {
            AlbumDownloadProgress p = progress.get();
            if (p != null && "ready".equals(p.getPhase())) {
                p.setPhase("done");
                p.setMessage("打包文件已过期，请重新下载");
                progress.set(p);
            }
            cleanupReadyFile();
        }
    }

    private void cleanupReadyFile() {
        File f = readyFile;
        readyFile = null;
        readyAt = 0;
        readyTaskId = null;
        if (f != null && f.exists()) {
            try {
                Files.deleteIfExists(f.toPath());
            } catch (Exception e) {
                log.debug("清理相册导出文件失败: {}", e.getMessage());
            }
        }
    }

    private static String buildAlbumZipFileName(String albumName, Long albumId) {
        String base = StringUtils.isNotEmpty(albumName) ? albumName.trim() : ("album_" + albumId);
        base = PhotoFieldUtils.safeFileName(base, 100);
        if (base.toLowerCase().endsWith(".zip")) {
            base = base.substring(0, base.length() - 4);
        }
        if (StringUtils.isEmpty(base)) {
            base = "album_" + albumId;
        }
        return base + ".zip";
    }

    private static String uniqueZipEntryName(BizPhoto photo, File origin, Set<String> usedNames) {
        String raw = StringUtils.isNotEmpty(photo.getFileName()) ? photo.getFileName() : origin.getName();
        String name = PhotoFieldUtils.safeFileName(raw, 180);
        if (StringUtils.isEmpty(name)) {
            name = "photo_" + photo.getPhotoId();
        }
        if (!usedNames.contains(name)) {
            usedNames.add(name);
            return name;
        }
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        int i = 1;
        String candidate;
        do {
            candidate = stem + "(" + i + ")" + ext;
            i++;
        } while (usedNames.contains(candidate));
        usedNames.add(candidate);
        return candidate;
    }

    @PreDestroy
    public void destroy() {
        executor.shutdownNow();
        cleanupReadyFile();
    }
}
