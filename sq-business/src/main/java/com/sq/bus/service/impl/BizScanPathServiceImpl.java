package com.sq.bus.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sq.bus.config.AlbumProperties;
import com.sq.bus.constants.AlbumDeleted;
import com.sq.bus.domain.BizAlbum;
import com.sq.bus.domain.BizPhoto;
import com.sq.bus.domain.BizScanLog;
import com.sq.bus.domain.BizScanPath;
import com.sq.bus.mapper.BizScanPathMapper;
import com.sq.bus.service.IBizAlbumService;
import com.sq.bus.service.IBizPhotoService;
import com.sq.bus.service.IBizScanLogService;
import com.sq.bus.service.IBizScanPathService;
import com.sq.bus.utils.ExifParseUtils;
import com.sq.bus.utils.PhotoFieldUtils;
import com.sq.bus.utils.ThumbUtils;
import com.sq.common.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BizScanPathServiceImpl extends ServiceImpl<BizScanPathMapper, BizScanPath> implements IBizScanPathService {

    private static final Logger log = LoggerFactory.getLogger(BizScanPathServiceImpl.class);

    private static final Set<String> IMAGE_EXT = new HashSet<String>(Arrays.asList(
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "tif", "tiff"));
    private static final Set<String> VIDEO_EXT = new HashSet<String>(Arrays.asList(
            "mp4", "mov", "avi", "mkv", "wmv"));

    /** 与 biz_scan_log.message varchar(1000) 对齐，预留余量 */
    private static final int SCAN_MESSAGE_MAX_LEN = 900;

    /** 同一目录同时只允许一个进行中的扫描 */
    private final Set<Long> runningPathIds = ConcurrentHashMap.newKeySet();

    @Autowired
    private IBizScanLogService scanLogService;

    @Autowired
    private IBizPhotoService photoService;

    @Autowired
    private IBizAlbumService albumService;

    @Autowired
    private AlbumProperties albumProperties;

    @Autowired
    @Qualifier("threadPoolTaskExecutor")
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;

    @Override
    public Long runScan(Long pathId, boolean fullScan) {
        Long logId = prepareAndStart(pathId, fullScan, false);
        waitUntilFinished(logId);
        return logId;
    }

    @Override
    public Long startScanAsync(Long pathId, boolean fullScan) {
        return prepareAndStart(pathId, fullScan, true);
    }

    private Long prepareAndStart(Long pathId, boolean fullScan, boolean async) {
        BizScanPath scanPath = getById(pathId);
        if (scanPath == null || (scanPath.getDeleted() != null && scanPath.getDeleted() == 1)) {
            throw new ServiceException("扫描目录不存在或已删除");
        }
        if (scanPath.getStatus() != null && scanPath.getStatus() == 0) {
            throw new ServiceException("扫描目录已禁用");
        }
        if (scanPath.getDefaultAlbumId() == null) {
            throw new ServiceException("请先配置默认绑定相册");
        }
        File root = new File(scanPath.getLocalPath());
        if (!root.exists() || !root.isDirectory()) {
            throw new ServiceException("本地目录不存在或不可访问：" + scanPath.getLocalPath());
        }
        if (!runningPathIds.add(pathId)) {
            throw new ServiceException("该目录正在扫描中，请稍后再试");
        }
        // 清理异常中断遗留的“进行中”日志，避免永久卡住
        scanLogService.update(new LambdaUpdateWrapper<BizScanLog>()
                .eq(BizScanLog::getPathId, pathId)
                .eq(BizScanLog::getStatus, 0)
                .set(BizScanLog::getStatus, 2)
                .set(BizScanLog::getMessage, "扫描已中断")
                .set(BizScanLog::getEndTime, new Date()));

        BizScanLog scanLog = new BizScanLog();
        scanLog.setPathId(pathId);
        scanLog.setScanType(fullScan ? 2 : 1);
        scanLog.setStatus(0);
        scanLog.setStartTime(new Date());
        scanLog.setCreateTime(new Date());
        scanLog.setTotalCount(0);
        scanLog.setNewCount(0);
        scanLog.setSkipCount(0);
        scanLog.setFailCount(0);
        scanLog.setMessage("正在统计文件…");
        scanLogService.save(scanLog);

        final Long logId = scanLog.getLogId();
        Runnable task = () -> {
            try {
                executeScan(logId, scanPath, root, fullScan);
            } catch (Throwable t) {
                // Error/未捕获异常也要落库，避免前端永久轮询「进行中」
                log.error("扫描线程异常退出 pathId={} logId={}", pathId, logId, t);
                markScanFailed(logId, "扫描异常中断：" + t.getClass().getSimpleName()
                        + (t.getMessage() == null ? "" : (" - " + t.getMessage())));
            } finally {
                runningPathIds.remove(pathId);
            }
        };

        if (async) {
            threadPoolTaskExecutor.execute(task);
        } else {
            task.run();
        }
        return logId;
    }

    private void waitUntilFinished(Long logId) {
        while (true) {
            BizScanLog current = scanLogService.getById(logId);
            if (current == null || current.getStatus() == null || current.getStatus() != 0) {
                return;
            }
            try {
                Thread.sleep(200L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void executeScan(Long logId, BizScanPath scanPath, File root, boolean fullScan) {
        BizScanLog scanLog = scanLogService.getById(logId);
        if (scanLog == null) {
            return;
        }
        StringBuilder failMsg = new StringBuilder();
        ScanCounter counter = new ScanCounter();
        try {
            int estimated = countMediaFiles(root);
            counter.estimated = estimated;
            scanLog.setTotalCount(estimated);
            scanLog.setMessage(estimated == 0 ? "目录中未发现媒体文件" : "开始扫描…");
            scanLogService.updateById(scanLog);

            if (estimated > 0) {
                doWalk(root, scanPath, fullScan, counter, failMsg, logId);
            }
        } catch (Exception e) {
            counter.failed++;
            appendFailDetail(failMsg, "scan", e);
            log.error("扫描失败 pathId={} logId={}", scanPath.getPathId(), logId, e);
        } finally {
            try {
                albumService.refreshAlbumStats(scanPath.getDefaultAlbumId());
            } catch (Exception e) {
                log.warn("刷新相册统计失败 albumId={}", scanPath.getDefaultAlbumId(), e);
            }

            try {
                scanPath.setLastScanTime(new Date());
                scanPath.setUpdateTime(new Date());
                updateById(scanPath);
            } catch (Exception e) {
                log.warn("更新扫描目录时间失败 pathId={}", scanPath.getPathId(), e);
            }

            try {
                BizScanLog finished = scanLogService.getById(logId);
                if (finished != null && finished.getStatus() != null && finished.getStatus() == 0) {
                    finished.setTotalCount(Math.max(counter.estimated, counter.total));
                    finished.setNewCount(counter.created);
                    finished.setSkipCount(counter.skipped);
                    finished.setFailCount(counter.failed);
                    finished.setStatus(counter.failed > 0 && counter.created == 0 && counter.skipped == 0 ? 2 : 1);
                    String summary = failMsg.length() == 0
                            ? "扫描完成"
                            : ("扫描完成，部分失败：" + failMsg);
                    finished.setMessage(trimScanMessage(summary));
                    finished.setEndTime(new Date());
                    try {
                        scanLogService.updateById(finished);
                    } catch (Exception updateEx) {
                        // message 超长等导致写失败时，用短文案重试，避免永远停在「进行中」
                        log.error("写入扫描完成状态失败，尝试短消息重试 logId={}", logId, updateEx);
                        finished.setMessage(trimScanMessage(
                                "扫描完成：新增" + counter.created
                                        + " 跳过" + counter.skipped
                                        + " 失败" + counter.failed
                                        + "（详情见服务端日志）"));
                        scanLogService.updateById(finished);
                    }
                }
            } catch (Exception e) {
                log.error("写入扫描完成状态失败 logId={}", logId, e);
                markScanFailed(logId, "扫描完成但状态落库失败，请查看服务端日志");
            }
        }
    }

    private void markScanFailed(Long logId, String message) {
        try {
            BizScanLog logRow = scanLogService.getById(logId);
            if (logRow == null || (logRow.getStatus() != null && logRow.getStatus() != 0)) {
                return;
            }
            logRow.setStatus(2);
            logRow.setMessage(trimScanMessage(message));
            logRow.setEndTime(new Date());
            scanLogService.updateById(logRow);
        } catch (Exception e) {
            log.warn("标记扫描失败状态异常 logId={}", logId, e);
        }
    }

    private static String trimScanMessage(String message) {
        if (message == null) {
            return null;
        }
        if (message.length() <= SCAN_MESSAGE_MAX_LEN) {
            return message;
        }
        return message.substring(0, SCAN_MESSAGE_MAX_LEN - 1) + "…";
    }

    private int countMediaFiles(File dir) {
        File[] children = dir.listFiles();
        if (children == null) {
            return 0;
        }
        int count = 0;
        for (File file : children) {
            if (file.isDirectory()) {
                count += countMediaFiles(file);
                continue;
            }
            String ext = extension(file.getName());
            if (IMAGE_EXT.contains(ext) || VIDEO_EXT.contains(ext)) {
                count++;
            }
        }
        return count;
    }

    private void doWalk(File dir, BizScanPath scanPath, boolean fullScan, ScanCounter counter,
                        StringBuilder failMsg, Long logId) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File file : children) {
            if (file.isDirectory()) {
                doWalk(file, scanPath, fullScan, counter, failMsg, logId);
                continue;
            }
            String ext = extension(file.getName());
            if (!IMAGE_EXT.contains(ext) && !VIDEO_EXT.contains(ext)) {
                continue;
            }
            counter.total++;
            int fileType = IMAGE_EXT.contains(ext) ? 1 : 2;
            // 先写入「当前文件」，大视频 MD5/截帧前前端也能看到进度
            persistProgress(logId, counter, file.getName());
            try {
                String md5 = md5Of(file);
                BizPhoto exists = photoService.findByMd5(md5);
                if (exists != null) {
                    Long existsAlbumId = exists.getAlbumId();
                    boolean sameAlbum = existsAlbumId != null
                            && existsAlbumId.equals(scanPath.getDefaultAlbumId());
                    BizAlbum owner = existsAlbumId == null ? null : albumService.getById(existsAlbumId);
                    boolean albumAlive = owner != null
                            && (owner.getDeleted() == null || owner.getDeleted() == AlbumDeleted.NORMAL);
                    if (sameAlbum || albumAlive) {
                        // 已在当前/其他有效相册中：按内容去重跳过
                        counter.skipped++;
                        continue;
                    }
                    // 所属相册已不在正常态：回收该记录到当前相册
                    reclaimPhoto(exists, file, scanPath, md5, fileType);
                    counter.created++;
                    continue;
                }
                BizPhoto reusable = photoService.findReusableByMd5(md5);
                if (reusable != null) {
                    reclaimPhoto(reusable, file, scanPath, md5, fileType);
                    counter.created++;
                    continue;
                }
                importFile(file, scanPath, md5, fileType);
                counter.created++;
            } catch (Exception ex) {
                counter.failed++;
                appendFailDetail(failMsg, file.getName(), ex);
                log.warn("扫描单文件失败 file={} size={}", file.getAbsolutePath(), file.length(), ex);
            } finally {
                // 每个文件处理后刷新计数，供前端轮询
                persistProgress(logId, counter, file.getName());
            }
        }
    }

    private void persistProgress(Long logId, ScanCounter counter, String currentFile) {
        long now = System.currentTimeMillis();
        int processed = counter.created + counter.skipped + counter.failed;
        boolean finishedAll = counter.estimated > 0 && processed >= counter.estimated;
        // 节流写库，避免大目录每个文件都更新；收尾必写
        if (!finishedAll && now - counter.lastPersistAt < 400L) {
            return;
        }
        counter.lastPersistAt = now;
        BizScanLog progress = new BizScanLog();
        progress.setLogId(logId);
        progress.setTotalCount(Math.max(counter.estimated, counter.total));
        progress.setNewCount(counter.created);
        progress.setSkipCount(counter.skipped);
        progress.setFailCount(counter.failed);
        progress.setStatus(0);
        String shown = currentFile == null ? "" : PhotoFieldUtils.trim(currentFile, 120);
        progress.setMessage(trimScanMessage(
                "正在处理(" + processed + "/" + Math.max(counter.estimated, counter.total) + ")：" + shown));
        scanLogService.updateById(progress);
    }

    private void importFile(File file, BizScanPath scanPath, String md5, int fileType) throws Exception {
        // 视频走 metadata-extractor 可能极慢/卡住，拍摄时间改用文件时间；图片仍解析 EXIF
        ExifParseUtils.ExifInfo exif = fileType == 1 ? ExifParseUtils.parse(file) : new ExifParseUtils.ExifInfo();
        String relativeName = file.getName();
        String fileUrl = "/album/files/scan/" + scanPath.getPathId() + "/" + relativeName;

        // 缩略图（图片用 Thumbnailator；视频优先 ffmpeg 截帧）
        String thumbUrl = null;
        File thumbDir = new File(albumProperties.getThumbPath(), String.valueOf(scanPath.getPathId()));
        String thumbName = fileType == 2
                ? "s_" + relativeName.replaceAll("\\.[^.]+$", "") + ".jpg"
                : "s_" + relativeName;
        File thumbFile = new File(thumbDir, thumbName);
        try {
            if (fileType == 1) {
                ThumbUtils.createThumbnail(file, thumbFile, albumProperties.getThumb().getSmallWidth());
                if (thumbFile.exists()) {
                    thumbUrl = "/album/files/thumb/" + scanPath.getPathId() + "/" + thumbName;
                }
            } else if (fileType == 2) {
                if (ThumbUtils.createVideoThumbnail(file, thumbFile, albumProperties.getThumb().getSmallWidth())) {
                    thumbUrl = "/album/files/thumb/" + scanPath.getPathId() + "/" + thumbName;
                }
            }
        } catch (Exception ignored) {
            // 缩略图失败不阻断入库
        }

        BizPhoto photo = new BizPhoto();
        photo.setAlbumId(scanPath.getDefaultAlbumId());
        photo.setFileName(relativeName);
        photo.setFilePath(file.getAbsolutePath());
        photo.setFileUrl(fileUrl);
        photo.setThumbUrl(thumbUrl);
        photo.setFileSize(file.length());
        photo.setFileType(fileType);
        if (fileType == 2) {
            photo.setDuration(ThumbUtils.getVideoDurationSeconds(file));
        }
        photo.setShootTime(exif.getShootTime() != null ? exif.getShootTime() : new Date(file.lastModified()));
        photo.setLatitude(exif.getLatitude());
        photo.setLongitude(exif.getLongitude());
        photo.setCameraModel(exif.getCameraModel());
        photo.setLensInfo(exif.getLensInfo());
        photo.setAperture(exif.getAperture());
        photo.setShutterSpeed(exif.getShutterSpeed());
        photo.setIso(exif.getIso());
        photo.setFocalLength(exif.getFocalLength());
        photo.setMd5(md5);
        photo.setSortOrder(0);
        photo.setDeleted(AlbumDeleted.NORMAL);
        photo.setCreateTime(new Date());
        PhotoFieldUtils.clamp(photo);
        photoService.save(photo);
    }

    private void reclaimPhoto(BizPhoto photo, File file, BizScanPath scanPath, String md5, int fileType) throws Exception {
        ExifParseUtils.ExifInfo exif = fileType == 1 ? ExifParseUtils.parse(file) : new ExifParseUtils.ExifInfo();
        String relativeName = file.getName();
        String fileUrl = "/album/files/scan/" + scanPath.getPathId() + "/" + relativeName;
        String thumbUrl = photo.getThumbUrl();
        File thumbDir = new File(albumProperties.getThumbPath(), String.valueOf(scanPath.getPathId()));
        String thumbName = fileType == 2
                ? "s_" + relativeName.replaceAll("\\.[^.]+$", "") + ".jpg"
                : "s_" + relativeName;
        File thumbFile = new File(thumbDir, thumbName);
        try {
            if (fileType == 1) {
                ThumbUtils.createThumbnail(file, thumbFile, albumProperties.getThumb().getSmallWidth());
                if (thumbFile.exists()) {
                    thumbUrl = "/album/files/thumb/" + scanPath.getPathId() + "/" + thumbName;
                }
            } else if (fileType == 2) {
                if (ThumbUtils.createVideoThumbnail(file, thumbFile, albumProperties.getThumb().getSmallWidth())) {
                    thumbUrl = "/album/files/thumb/" + scanPath.getPathId() + "/" + thumbName;
                }
            }
        } catch (Exception ignored) {
        }

        Long oldAlbumId = photo.getAlbumId();
        photo.setAlbumId(scanPath.getDefaultAlbumId());
        photo.setFileName(relativeName);
        photo.setFilePath(file.getAbsolutePath());
        photo.setFileUrl(fileUrl);
        photo.setThumbUrl(thumbUrl);
        photo.setFileSize(file.length());
        photo.setFileType(fileType);
        if (fileType == 2) {
            photo.setDuration(ThumbUtils.getVideoDurationSeconds(file));
        }
        photo.setShootTime(exif.getShootTime() != null ? exif.getShootTime() : new Date(file.lastModified()));
        photo.setLatitude(exif.getLatitude());
        photo.setLongitude(exif.getLongitude());
        photo.setCameraModel(exif.getCameraModel());
        photo.setLensInfo(exif.getLensInfo());
        photo.setAperture(exif.getAperture());
        photo.setShutterSpeed(exif.getShutterSpeed());
        photo.setIso(exif.getIso());
        photo.setFocalLength(exif.getFocalLength());
        photo.setMd5(md5);
        photo.setDeleted(AlbumDeleted.NORMAL);
        photo.setUpdateTime(new Date());
        PhotoFieldUtils.clamp(photo);
        photoService.updateById(photo);
        if (oldAlbumId != null && !oldAlbumId.equals(scanPath.getDefaultAlbumId())) {
            albumService.refreshAlbumStats(oldAlbumId);
        }
    }

    private static void appendFailDetail(StringBuilder failMsg, String fileName, Exception ex) {
        if (failMsg.length() >= SCAN_MESSAGE_MAX_LEN) {
            return;
        }
        String reason = ex.getMessage();
        if (reason == null || reason.isEmpty()) {
            reason = ex.getClass().getSimpleName();
        }
        // 异常文案可能很长（含 SQL），只保留简短原因
        reason = reason.replace('\n', ' ').replace('\r', ' ');
        if (reason.length() > 80) {
            reason = reason.substring(0, 80) + "…";
        }
        String piece = fileName + ':' + reason + ';';
        if (failMsg.length() + piece.length() > SCAN_MESSAGE_MAX_LEN) {
            int remain = SCAN_MESSAGE_MAX_LEN - failMsg.length();
            if (remain > 1) {
                failMsg.append(piece, 0, remain - 1).append('…');
            }
            return;
        }
        failMsg.append(piece);
    }

    private static String extension(String name) {
        int idx = name.lastIndexOf('.');
        if (idx < 0) {
            return "";
        }
        return name.substring(idx + 1).toLowerCase(Locale.ROOT);
    }

    private static String md5Of(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) != -1) {
                md.update(buf, 0, len);
            }
        }
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder(32);
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static class ScanCounter {
        private int estimated;
        private int total;
        private int created;
        private int skipped;
        private int failed;
        private long lastPersistAt;
    }
}
