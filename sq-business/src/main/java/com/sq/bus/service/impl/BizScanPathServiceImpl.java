package com.sq.bus.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sq.bus.config.AlbumProperties;
import com.sq.bus.constants.AlbumDeleted;
import com.sq.bus.constants.PhotoLocationSource;
import com.sq.bus.domain.BizAlbum;
import com.sq.bus.domain.BizPhoto;
import com.sq.bus.domain.BizScanLog;
import com.sq.bus.domain.BizScanPath;
import com.sq.bus.mapper.BizScanPathMapper;
import com.sq.bus.service.IBizAlbumService;
import com.sq.bus.service.IBizPhotoService;
import com.sq.bus.service.IBizScanLogService;
import com.sq.bus.service.IBizScanPathService;
import com.sq.bus.service.IBizTrackService;
import com.sq.bus.service.IPhotoFallbackLocationService;
import com.sq.bus.service.IVideoProxyService;
import com.sq.bus.domain.vo.VideoProxyStatus;
import com.sq.bus.utils.ExifParseUtils;
import com.sq.bus.utils.PhotoFieldUtils;
import com.sq.bus.utils.ThumbUtils;
import com.sq.bus.utils.VideoMetaUtils;
import com.sq.bus.utils.VideoStreamProbe;
import com.sq.common.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

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
    private IBizTrackService trackService;

    @Autowired
    private IPhotoFallbackLocationService fallbackLocationService;

    @Autowired
    private AlbumProperties albumProperties;

    @Autowired
    private IVideoProxyService videoProxyService;

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

            // 扫描后：先时间插值兜底（不进主轨迹），再按权威 GPS 同步照片轨
            try {
                if (scanPath.getDefaultAlbumId() != null
                        && (counter.created > 0 || counter.gpsUpdated > 0)) {
                    fallbackLocationService.fillMissingByTimeInterp(scanPath.getDefaultAlbumId());
                    trackService.autoSyncAlbumTrack(scanPath.getDefaultAlbumId());
                }
            } catch (Exception e) {
                log.warn("自动同步相册轨迹失败 albumId={}", scanPath.getDefaultAlbumId(), e);
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
        if (shouldSkipScanDir(dir)) {
            return 0;
        }
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
            if (shouldSkipScanFile(file)) {
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
        if (shouldSkipScanDir(dir)) {
            return;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File file : children) {
            if (file.isDirectory()) {
                doWalk(file, scanPath, fullScan, counter, failMsg, logId);
                continue;
            }
            if (shouldSkipScanFile(file)) {
                continue;
            }
            String ext = extension(file.getName());
            if (!IMAGE_EXT.contains(ext) && !VIDEO_EXT.contains(ext)) {
                continue;
            }
            counter.total++;
            int fileType = IMAGE_EXT.contains(ext) ? 1 : 2;
            // 先写入「当前文件」，大视频哈希/截帧前前端也能看到进度
            persistProgress(logId, counter, file.getName());
            try {
                File mediaFile = file;
                String indexedPath = indexedFilePath(mediaFile);
                // 同路径已入库：只更新/跳过，绝不因哈希算法变更再插一条
                BizPhoto byPath = findPhotoByIndexedPath(indexedPath, mediaFile);
                if (byPath != null) {
                    handleExistingByPath(byPath, mediaFile, indexedPath, scanPath, fullScan, fileType, counter, logId);
                    continue;
                }
                persistProgress(logId, counter, "哈希 " + mediaFile.getName());
                String md5 = contentHash(mediaFile);
                BizPhoto exists = photoService.findByMd5(md5);
                if (exists != null) {
                    Long existsAlbumId = exists.getAlbumId();
                    boolean sameAlbum = existsAlbumId != null
                            && existsAlbumId.equals(scanPath.getDefaultAlbumId());
                    BizAlbum owner = existsAlbumId == null ? null : albumService.getById(existsAlbumId);
                    boolean albumAlive = owner != null
                            && (owner.getDeleted() == null || owner.getDeleted() == AlbumDeleted.NORMAL);
                    if (sameAlbum || albumAlive) {
                        // 已入库媒体：缺缩略图时补齐（增量也补；全量时视频再补 GPS/时长）
                        if (sameAlbum) {
                            try {
                                if (fileType == 2) {
                                    if (fullScan) {
                                        if (enrichExistingVideo(exists, mediaFile, scanPath)) {
                                            counter.gpsUpdated++;
                                        }
                                    } else {
                                        repairOneVideoThumb(exists, mediaFile, scanPath);
                                    }
                                } else if (fileType == 1) {
                                    enrichExistingImageThumb(exists, mediaFile, scanPath);
                                }
                            } catch (Exception enrichEx) {
                                log.warn("补齐媒体元数据/缩略图失败 file={}", mediaFile.getAbsolutePath(), enrichEx);
                            }
                        }
                        // 路径可能变更：回写当前路径/大小/哈希，便于下次路径跳过
                        touchIndexedPath(exists, mediaFile, indexedPath, md5);
                        // 已在当前/其他有效相册中：按内容去重跳过
                        counter.skipped++;
                        continue;
                    }
                    // 所属相册已不在正常态：回收该记录到当前相册
                    reclaimPhoto(exists, mediaFile, scanPath, md5, fileType);
                    counter.created++;
                    continue;
                }
                BizPhoto reusable = photoService.findReusableByMd5(md5);
                if (reusable != null) {
                    reclaimPhoto(reusable, mediaFile, scanPath, md5, fileType);
                    counter.created++;
                    continue;
                }
                // 哈希未命中后再查一次路径，防止并发/路径写法差异漏网
                byPath = findPhotoByIndexedPath(indexedPath, mediaFile);
                if (byPath != null) {
                    handleExistingByPath(byPath, mediaFile, indexedPath, scanPath, fullScan, fileType, counter, logId);
                    // 顺带把旧整文件 MD5 升级为当前哈希，避免以后再误判
                    if (md5 != null && !md5.equals(byPath.getMd5())) {
                        byPath.setMd5(md5);
                        byPath.setUpdateTime(new Date());
                        photoService.updateById(byPath);
                    }
                    continue;
                }
                persistProgress(logId, counter, "入库 " + mediaFile.getName());
                importFile(mediaFile, scanPath, md5, fileType);
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
        MediaMeta meta = parseMediaMeta(file, fileType);
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
                if (shouldCreateVideoThumb(file)
                        && ThumbUtils.createVideoThumbnail(file, thumbFile, albumProperties.getThumb().getSmallWidth())) {
                    thumbUrl = "/album/files/thumb/" + scanPath.getPathId() + "/" + thumbName;
                }
            }
        } catch (Exception ex) {
            // 缩略图失败不阻断入库，但必须打日志便于排查灰块
            log.warn("生成缩略图失败 file={} err={}", file.getAbsolutePath(), ex.toString());
        }

        BizPhoto photo = new BizPhoto();
        photo.setAlbumId(scanPath.getDefaultAlbumId());
        photo.setFileName(relativeName);
        photo.setFilePath(indexedFilePath(file));
        photo.setFileUrl(fileUrl);
        photo.setThumbUrl(thumbUrl);
        photo.setFileSize(file.length());
        photo.setFileType(fileType);
        if (fileType == 2) {
            photo.setDuration(ThumbUtils.getVideoDurationSeconds(file));
        }
        applyMediaMeta(photo, meta, new Date(file.lastModified()));
        photo.setMd5(md5);
        photo.setSortOrder(0);
        photo.setDeleted(AlbumDeleted.NORMAL);
        photo.setCreateTime(new Date());
        PhotoFieldUtils.clamp(photo);
        photoService.save(photo);
    }

    private void reclaimPhoto(BizPhoto photo, File file, BizScanPath scanPath, String md5, int fileType) throws Exception {
        MediaMeta meta = parseMediaMeta(file, fileType);
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
                if (shouldCreateVideoThumb(file)
                        && ThumbUtils.createVideoThumbnail(file, thumbFile, albumProperties.getThumb().getSmallWidth())) {
                    thumbUrl = "/album/files/thumb/" + scanPath.getPathId() + "/" + thumbName;
                }
            }
        } catch (Exception ex) {
            log.warn("回收时生成缩略图失败 file={} err={}", file.getAbsolutePath(), ex.toString());
        }

        Long oldAlbumId = photo.getAlbumId();
        photo.setAlbumId(scanPath.getDefaultAlbumId());
        photo.setFileName(relativeName);
        photo.setFilePath(indexedFilePath(file));
        photo.setFileUrl(fileUrl);
        photo.setThumbUrl(thumbUrl);
        photo.setFileSize(file.length());
        photo.setFileType(fileType);
        if (fileType == 2) {
            photo.setDuration(ThumbUtils.getVideoDurationSeconds(file));
        }
        applyMediaMeta(photo, meta, new Date(file.lastModified()));
        photo.setMd5(md5);
        photo.setDeleted(AlbumDeleted.NORMAL);
        photo.setUpdateTime(new Date());
        PhotoFieldUtils.clamp(photo);
        photoService.updateById(photo);
        if (oldAlbumId != null && !oldAlbumId.equals(scanPath.getDefaultAlbumId())) {
            albumService.refreshAlbumStats(oldAlbumId);
        }
    }

    /** 图片用 EXIF；视频用 ffprobe（避免 metadata-extractor 卡死） */
    private static MediaMeta parseMediaMeta(File file, int fileType) {
        MediaMeta meta = new MediaMeta();
        if (fileType == 1) {
            ExifParseUtils.ExifInfo exif = ExifParseUtils.parse(file);
            meta.shootTime = exif.getShootTime();
            meta.latitude = exif.getLatitude();
            meta.longitude = exif.getLongitude();
            meta.cameraModel = exif.getCameraModel();
            meta.lensInfo = exif.getLensInfo();
            meta.aperture = exif.getAperture();
            meta.shutterSpeed = exif.getShutterSpeed();
            meta.iso = exif.getIso();
            meta.focalLength = exif.getFocalLength();
            return meta;
        }
        VideoMetaUtils.MetaInfo video = VideoMetaUtils.parse(file);
        meta.shootTime = video.getShootTime();
        meta.latitude = video.getLatitude();
        meta.longitude = video.getLongitude();
        return meta;
    }

    private static void applyMediaMeta(BizPhoto photo, MediaMeta meta, Date fallbackShootTime) {
        photo.setShootTime(meta.shootTime != null ? meta.shootTime : fallbackShootTime);
        int fileType = photo.getFileType() != null ? photo.getFileType() : 1;
        PhotoLocationSource.applyDeviceGps(photo, meta.latitude, meta.longitude, fileType);
        photo.setCameraModel(meta.cameraModel);
        photo.setLensInfo(meta.lensInfo);
        photo.setAperture(meta.aperture);
        photo.setShutterSpeed(meta.shutterSpeed);
        photo.setIso(meta.iso);
        photo.setFocalLength(meta.focalLength);
    }

    /**
     * 全量扫描时为已入库图片补齐缺失/失效的缩略图。
     */
    private void enrichExistingImageThumb(BizPhoto photo, File file, BizScanPath scanPath) {
        if (hasUsableThumb(photo)) {
            return;
        }
        String relativeName = file.getName();
        File thumbDir = new File(albumProperties.getThumbPath(), String.valueOf(scanPath.getPathId()));
        String thumbName = "s_" + relativeName;
        File thumbFile = new File(thumbDir, thumbName);
        try {
            ThumbUtils.createThumbnail(file, thumbFile, albumProperties.getThumb().getSmallWidth());
            if (thumbFile.exists() && thumbFile.isFile() && thumbFile.length() > 0) {
                photo.setThumbUrl("/album/files/thumb/" + scanPath.getPathId() + "/" + thumbName);
                photo.setUpdateTime(new Date());
                PhotoFieldUtils.clamp(photo);
                photoService.updateById(photo);
            } else {
                log.warn("图片缩略图未生成 file={}", file.getAbsolutePath());
            }
        } catch (Exception ex) {
            log.warn("补齐图片缩略图失败 file={} err={}", file.getAbsolutePath(), ex.toString());
        }
    }

    /**
     * 全量扫描时为已入库视频补齐 GPS、缩略图、时长。
     * @return 是否写入了 GPS（用于触发轨迹同步）
     */
    private boolean enrichExistingVideo(BizPhoto photo, File file, BizScanPath scanPath) {
        boolean gpsFilled = false;
        boolean dirty = false;
        if (photo.getLatitude() == null || photo.getLongitude() == null
                || PhotoLocationSource.isFallback(photo.getLocationSource())) {
            VideoMetaUtils.MetaInfo meta = VideoMetaUtils.parse(file);
            if (meta.getLatitude() != null && meta.getLongitude() != null) {
                PhotoLocationSource.applyDeviceGps(photo, meta.getLatitude(), meta.getLongitude(), 2);
                if (meta.getShootTime() != null) {
                    photo.setShootTime(meta.getShootTime());
                }
                gpsFilled = true;
                dirty = true;
            }
        }
        if (photo.getDuration() == null) {
            Integer duration = ThumbUtils.getVideoDurationSeconds(file);
            if (duration != null) {
                photo.setDuration(duration);
                dirty = true;
            }
        }
        if (!hasUsableThumb(photo)) {
            if (repairOneVideoThumb(photo, file, scanPath)) {
                dirty = true;
            }
        }
        if (dirty) {
            photo.setUpdateTime(new Date());
            PhotoFieldUtils.clamp(photo);
            photoService.updateById(photo);
        }
        return gpsFilled;
    }

    /**
     * 单条视频补缩略图（忽略「超大跳过」上限，大疆 4K 也尽量截一帧）。
     * @return 是否写入了可用 thumbUrl
     */
    private boolean repairOneVideoThumb(BizPhoto photo, File file, BizScanPath scanPath) {
        if (photo == null || file == null || !file.isFile() || scanPath == null || scanPath.getPathId() == null) {
            return false;
        }
        if (hasUsableThumb(photo)) {
            return false;
        }
        String relativeName = file.getName();
        File thumbDir = new File(albumProperties.getThumbPath(), String.valueOf(scanPath.getPathId()));
        String thumbName = "s_" + relativeName.replaceAll("\\.[^.]+$", "") + ".jpg";
        File thumbFile = new File(thumbDir, thumbName);
        // 磁盘上已有可用文件：只回写 URL
        if (thumbFile.exists() && thumbFile.isFile() && thumbFile.length() > 0) {
            photo.setThumbUrl("/album/files/thumb/" + scanPath.getPathId() + "/" + thumbName);
            photo.setUpdateTime(new Date());
            PhotoFieldUtils.clamp(photo);
            photoService.updateById(photo);
            return true;
        }
        if (!ThumbUtils.createVideoThumbnail(file, thumbFile, albumProperties.getThumb().getSmallWidth())) {
            return false;
        }
        photo.setThumbUrl("/album/files/thumb/" + scanPath.getPathId() + "/" + thumbName);
        photo.setUpdateTime(new Date());
        PhotoFieldUtils.clamp(photo);
        photoService.updateById(photo);
        return true;
    }

    @Override
    public Map<String, Object> repairMissingVideoThumbs(Long pathId, boolean force) {
        List<BizScanPath> paths;
        if (pathId != null) {
            BizScanPath one = getById(pathId);
            if (one == null || (one.getDeleted() != null && one.getDeleted() == 1)) {
                throw new ServiceException("扫描目录不存在或已删除");
            }
            paths = new ArrayList<BizScanPath>();
            paths.add(one);
        } else {
            paths = list(new LambdaQueryWrapper<BizScanPath>()
                    .eq(BizScanPath::getDeleted, 0)
                    .eq(BizScanPath::getStatus, 1));
            if (paths == null) {
                paths = new ArrayList<BizScanPath>();
            }
        }

        int total = 0;
        int repaired = 0;
        int failed = 0;
        int skipped = 0;
        List<String> samples = new ArrayList<String>();

        for (BizScanPath scanPath : paths) {
            if (scanPath.getDefaultAlbumId() == null) {
                continue;
            }
            List<BizPhoto> videos = photoService.list(new LambdaQueryWrapper<BizPhoto>()
                    .eq(BizPhoto::getAlbumId, scanPath.getDefaultAlbumId())
                    .eq(BizPhoto::getDeleted, AlbumDeleted.NORMAL)
                    .eq(BizPhoto::getFileType, 2));
            if (videos == null) {
                continue;
            }
            for (BizPhoto photo : videos) {
                total++;
                if (!force && hasUsableThumb(photo)) {
                    skipped++;
                    continue;
                }
                String fp = photo.getFilePath();
                if (fp == null || fp.isEmpty()) {
                    failed++;
                    continue;
                }
                File file = new File(fp);
                if (!file.isFile()) {
                    failed++;
                    if (samples.size() < 8) {
                        samples.add(photo.getFileName() + "（源文件不存在）");
                    }
                    continue;
                }
                try {
                    if (repairOneVideoThumb(photo, file, scanPath)) {
                        repaired++;
                    } else {
                        failed++;
                        if (samples.size() < 8) {
                            samples.add(photo.getFileName() + "（截帧失败）");
                        }
                    }
                } catch (Exception e) {
                    failed++;
                    log.warn("补视频缩略图异常 photoId={} err={}", photo.getPhotoId(), e.toString());
                    if (samples.size() < 8) {
                        samples.add(photo.getFileName() + "（" + e.getMessage() + "）");
                    }
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("total", total);
        result.put("repaired", repaired);
        result.put("failed", failed);
        result.put("skipped", skipped);
        result.put("force", force);
        result.put("samples", samples);
        if (!force && repaired == 0 && failed == 0 && skipped > 0) {
            result.put("hint", "库里已有可用封面文件，故全部跳过；请强制刷新首页/地图。若仍显示「视频」占位，可勾选强制重截后再试。");
        }
        return result;
    }

    @Override
    public Map<String, Object> videoProxyStats(Long pathId) {
        List<BizScanPath> paths = resolveScanPaths(pathId);
        AlbumProperties.VideoProxy cfg = albumProperties.getVideoProxy();
        if (cfg == null) {
            cfg = new AlbumProperties.VideoProxy();
        }
        int totalVideos = 0;
        int eligible = 0;
        Map<String, Integer> skipReasons = new LinkedHashMap<String, Integer>();
        List<Map<String, Object>> eligibleSamples = new ArrayList<Map<String, Object>>();
        List<Map<String, Object>> ineligibleSamples = new ArrayList<Map<String, Object>>();

        for (BizScanPath scanPath : paths) {
            if (scanPath.getDefaultAlbumId() == null) {
                continue;
            }
            List<BizPhoto> videos = photoService.list(new LambdaQueryWrapper<BizPhoto>()
                    .eq(BizPhoto::getAlbumId, scanPath.getDefaultAlbumId())
                    .eq(BizPhoto::getDeleted, AlbumDeleted.NORMAL)
                    .eq(BizPhoto::getFileType, 2));
            if (videos == null || videos.isEmpty()) {
                continue;
            }
            for (BizPhoto photo : videos) {
                totalVideos++;
                File file = new File(photo.getFilePath() == null ? "" : photo.getFilePath());
                long size = photo.getFileSize() != null ? photo.getFileSize() : (file.exists() ? file.length() : 0L);
                VideoStreamProbe.Eligibility el = VideoStreamProbe.checkEligibility(file, size, cfg);
                if (el.eligible) {
                    eligible++;
                    if (eligibleSamples.size() < 8) {
                        Map<String, Object> sample = new LinkedHashMap<String, Object>();
                        sample.put("photoId", photo.getPhotoId());
                        sample.put("fileName", photo.getFileName());
                        sample.put("sizeGb", String.format(Locale.ROOT, "%.2f", size / (1024d * 1024d * 1024d)));
                        sample.put("stream", el.detail);
                        eligibleSamples.add(sample);
                    }
                } else {
                    String code = el.code == null ? "unknown" : el.code;
                    skipReasons.put(code, skipReasons.containsKey(code) ? skipReasons.get(code) + 1 : 1);
                    if (ineligibleSamples.size() < 5) {
                        Map<String, Object> sample = new LinkedHashMap<String, Object>();
                        sample.put("photoId", photo.getPhotoId());
                        sample.put("fileName", photo.getFileName());
                        sample.put("reason", el.detail);
                        ineligibleSamples.add(sample);
                    }
                }
            }
        }

        Map<String, Object> rules = new LinkedHashMap<String, Object>();
        rules.put("minBytes", cfg.getMinBytes());
        rules.put("minWidth", cfg.getMinWidth());
        rules.put("minHeight", cfg.getMinHeight());
        rules.put("minFps", cfg.getMinFps());
        rules.put("proxyPath", albumProperties.getProxyPath());
        rules.put("hint", "须同时满足：1080p+、≥30fps");

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("pathCount", paths.size());
        result.put("totalVideos", totalVideos);
        result.put("eligible", eligible);
        result.put("skipped", totalVideos - eligible);
        result.put("skipReasons", skipReasons);
        result.put("eligibleSamples", eligibleSamples);
        result.put("ineligibleSamples", ineligibleSamples);
        result.put("rules", rules);
        if (eligible == 0 && totalVideos > 0) {
            result.put("message", "当前没有符合转码条件的视频，proxy 目录会保持为空");
        } else if (eligible > 0) {
            result.put("message", "共 " + eligible + " 个视频可转码，请在扫描页点「一键转码」");
        } else {
            result.put("message", "该目录下没有已入库视频");
        }
        return result;
    }

    @Override
    public Map<String, Object> enqueueVideoProxies(Long pathId, boolean force) {
        List<BizScanPath> paths = resolveScanPaths(pathId);
        videoProxyService.beginBatch();
        threadPoolTaskExecutor.execute(() -> {
            try {
                runEnqueueVideoProxies(paths, force);
            } finally {
                videoProxyService.endBatch();
            }
        });
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("async", true);
        result.put("force", force);
        result.put("pathCount", paths.size());
        result.put("hint", "已开始后台排队转码，可在右下角查看进度；仅处理 1080p+、≥30fps 的视频。");
        result.put("message", "已开始后台转码");
        return result;
    }

    private List<BizScanPath> resolveScanPaths(Long pathId) {
        if (pathId != null) {
            BizScanPath one = getById(pathId);
            if (one == null || (one.getDeleted() != null && one.getDeleted() == 1)) {
                throw new ServiceException("扫描目录不存在或已删除");
            }
            List<BizScanPath> paths = new ArrayList<BizScanPath>();
            paths.add(one);
            return paths;
        }
        List<BizScanPath> paths = list(new LambdaQueryWrapper<BizScanPath>()
                .eq(BizScanPath::getDeleted, 0)
                .eq(BizScanPath::getStatus, 1));
        if (paths == null) {
            return new ArrayList<BizScanPath>();
        }
        return paths;
    }

    private void runEnqueueVideoProxies(List<BizScanPath> paths, boolean force) {
        String[] qualities = new String[]{"720p", "1080p"};
        int fps30 = 30;
        for (BizScanPath scanPath : paths) {
            if (scanPath.getDefaultAlbumId() == null) {
                continue;
            }
            List<BizPhoto> videos = photoService.list(new LambdaQueryWrapper<BizPhoto>()
                    .eq(BizPhoto::getAlbumId, scanPath.getDefaultAlbumId())
                    .eq(BizPhoto::getDeleted, AlbumDeleted.NORMAL)
                    .eq(BizPhoto::getFileType, 2));
            if (videos == null || videos.isEmpty()) {
                continue;
            }
            for (BizPhoto photo : videos) {
                if (!force && !videoProxyService.needsVideoProxy(photo.getPhotoId())) {
                    videoProxyService.onBatchVideoSkipped();
                    continue;
                }
                for (String quality : qualities) {
                    try {
                        if (!force) {
                            VideoProxyStatus before = videoProxyService.status(photo.getPhotoId(), quality, fps30);
                            if (before != null && "ready".equals(before.getStatus())) {
                                continue;
                            }
                        }
                        videoProxyService.ensure(photo.getPhotoId(), quality, fps30, force);
                    } catch (Exception e) {
                        log.warn("排队视频浏览档失败 photoId={} {}30 err={}",
                                photo.getPhotoId(), quality, e.toString());
                    }
                }
            }
        }
    }

    private boolean hasUsableThumb(BizPhoto photo) {
        if (photo.getThumbUrl() == null || photo.getThumbUrl().isEmpty()) {
            return false;
        }
        if (!photo.getThumbUrl().startsWith("/album/files/thumb/")) {
            return false;
        }
        String rel = photo.getThumbUrl().substring("/album/files/thumb/".length());
        File thumb = new File(albumProperties.getThumbPath(), rel);
        // 过小文件多半是截帧失败残留，视为不可用
        return thumb.exists() && thumb.isFile() && thumb.length() >= 1024L;
    }

    private static final class MediaMeta {
        private Date shootTime;
        private BigDecimal latitude;
        private BigDecimal longitude;
        private String cameraModel;
        private String lensInfo;
        private String aperture;
        private String shutterSpeed;
        private Integer iso;
        private String focalLength;
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

    /** 跳过缩略图/代理缓存目录，避免浏览档被再次扫进相册列表 */
    private boolean shouldSkipScanDir(File dir) {
        return isUnderConfiguredRoot(dir, albumProperties.getProxyPath())
                || isUnderConfiguredRoot(dir, albumProperties.getThumbPath());
    }

    private boolean shouldSkipScanFile(File file) {
        return isUnderConfiguredRoot(file, albumProperties.getProxyPath())
                || isUnderConfiguredRoot(file, albumProperties.getThumbPath());
    }

    private static boolean isUnderConfiguredRoot(File file, String root) {
        if (file == null || root == null || root.trim().isEmpty()) {
            return false;
        }
        try {
            File rootFile = new File(root).getCanonicalFile();
            File cur = file.getCanonicalFile();
            String rootPath = rootFile.getAbsolutePath();
            String curPath = cur.getAbsolutePath();
            if (curPath.equals(rootPath)) {
                return true;
            }
            if (!rootPath.endsWith(File.separator)) {
                rootPath = rootPath + File.separator;
            }
            return curPath.startsWith(rootPath);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 同路径已入库：路径+大小未变则跳过读盘；大小变了则就地更新，绝不新建重复行。
     */
    private void handleExistingByPath(BizPhoto exists, File file, String indexedPath,
                                      BizScanPath scanPath, boolean fullScan, int fileType,
                                      ScanCounter counter, Long logId) throws Exception {
        Long existsAlbumId = exists.getAlbumId();
        boolean sameAlbum = existsAlbumId != null
                && existsAlbumId.equals(scanPath.getDefaultAlbumId());
        BizAlbum owner = existsAlbumId == null ? null : albumService.getById(existsAlbumId);
        boolean albumAlive = owner != null
                && (owner.getDeleted() == null || owner.getDeleted() == AlbumDeleted.NORMAL);

        // 相册已删：回收到当前相册（仍是同一条记录）
        if (!(sameAlbum || albumAlive)) {
            String md5 = contentHash(file);
            reclaimPhoto(exists, file, scanPath, md5, fileType);
            counter.created++;
            return;
        }

        Long storedSize = exists.getFileSize();
        boolean sizeSame = storedSize != null && storedSize.longValue() == file.length();
        if (!sizeSame) {
            // 同路径文件被替换：刷新元数据与哈希，不新增
            persistProgress(logId, counter, "更新 " + file.getName());
            String md5 = contentHash(file);
            reclaimPhoto(exists, file, scanPath, md5, fileType);
            counter.created++;
            return;
        }

        // 统一路径写法，便于下次精确命中
        if (indexedPath != null && !indexedPath.equals(exists.getFilePath())) {
            exists.setFilePath(indexedPath);
            exists.setUpdateTime(new Date());
            photoService.updateById(exists);
        }

        if (sameAlbum) {
            try {
                if (fileType == 2) {
                    if (fullScan) {
                        if (enrichExistingVideo(exists, file, scanPath)) {
                            counter.gpsUpdated++;
                        }
                    } else {
                        repairOneVideoThumb(exists, file, scanPath);
                    }
                } else if (fileType == 1) {
                    enrichExistingImageThumb(exists, file, scanPath);
                }
            } catch (Exception enrichEx) {
                log.warn("路径命中后补齐元数据失败 file={}", file.getAbsolutePath(), enrichEx);
            }
        }
        counter.skipped++;
    }

    private BizPhoto findPhotoByIndexedPath(String indexedPath, File file) {
        if (indexedPath != null && !indexedPath.isEmpty()) {
            BizPhoto hit = photoService.findByFilePath(indexedPath);
            if (hit != null) {
                return hit;
            }
        }
        String abs = file.getAbsolutePath();
        if (abs != null && !abs.equals(indexedPath)) {
            return photoService.findByFilePath(abs);
        }
        return null;
    }

    /** 入库统一用规范路径，减少 Windows 路径写法不一致导致漏匹配 */
    private static String indexedFilePath(File file) {
        if (file == null) {
            return null;
        }
        try {
            return file.getCanonicalPath();
        } catch (Exception e) {
            return file.getAbsolutePath();
        }
    }

    /** 内容去重命中后回写路径/大小/哈希，便于下次路径跳过 */
    private void touchIndexedPath(BizPhoto photo, File file, String indexedPath, String md5) {
        if (photo == null || file == null || photo.getPhotoId() == null) {
            return;
        }
        String abs = indexedPath != null ? indexedPath : indexedFilePath(file);
        Long size = file.length();
        boolean pathChanged = photo.getFilePath() == null || !photo.getFilePath().equals(abs);
        boolean sizeChanged = photo.getFileSize() == null || photo.getFileSize().longValue() != size;
        boolean md5Changed = md5 != null && !md5.equals(photo.getMd5());
        if (!pathChanged && !sizeChanged && !md5Changed) {
            return;
        }
        photo.setFilePath(abs);
        photo.setFileSize(size);
        if (md5Changed) {
            photo.setMd5(md5);
        }
        photo.setUpdateTime(new Date());
        photoService.updateById(photo);
    }

    private boolean shouldCreateVideoThumb(File file) {
        AlbumProperties.ScanConfig cfg = albumProperties.getScan();
        if (cfg == null) {
            return true;
        }
        long limit = cfg.getSkipVideoThumbAboveBytes();
        if (limit <= 0) {
            return true;
        }
        return file.length() <= limit;
    }

    /**
     * 小文件整文件 MD5；大文件采样哈希（前缀 smd5:），避免 几十 GB 视频整盘读取。
     */
    private String contentHash(File file) throws Exception {
        AlbumProperties.ScanConfig cfg = albumProperties.getScan();
        long threshold = cfg == null ? 0L : cfg.getSampleHashThresholdBytes();
        long size = file.length();
        if (threshold <= 0 || size <= threshold) {
            return md5Of(file);
        }
        long chunk = cfg.getSampleHashChunkBytes();
        if (chunk <= 0) {
            chunk = 4L * 1024 * 1024;
        }
        return sampleMd5(file, size, chunk);
    }

    private static String sampleMd5(File file, long size, long chunk) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        // 纳入体积，降低「同头尾不同中间」碰撞
        md.update(Long.toString(size).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "r")) {
            updateSample(md, raf, 0L, chunk, size);
            if (size > chunk * 2) {
                long mid = Math.max(0L, (size - chunk) / 2);
                updateSample(md, raf, mid, chunk, size);
            }
            if (size > chunk) {
                updateSample(md, raf, Math.max(0L, size - chunk), chunk, size);
            }
        }
        return "smd5:" + toHex(md.digest());
    }

    private static void updateSample(MessageDigest md, java.io.RandomAccessFile raf,
                                     long offset, long chunk, long size) throws Exception {
        long len = Math.min(chunk, size - offset);
        if (len <= 0) {
            return;
        }
        raf.seek(offset);
        byte[] buf = new byte[(int) Math.min(1024 * 1024, len)];
        long remaining = len;
        while (remaining > 0) {
            int n = raf.read(buf, 0, (int) Math.min(buf.length, remaining));
            if (n < 0) {
                break;
            }
            md.update(buf, 0, n);
            remaining -= n;
        }
    }

    private static String md5Of(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        try (FileInputStream in = new FileInputStream(file)) {
            // 1MB 缓冲，大图/中等视频整文件哈希更快
            byte[] buf = new byte[1024 * 1024];
            int len;
            while ((len = in.read(buf)) != -1) {
                md.update(buf, 0, len);
            }
        }
        return toHex(md.digest());
    }

    private static String toHex(byte[] digest) {
        StringBuilder sb = new StringBuilder(digest.length * 2);
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
        /** 全量扫描补齐视频 GPS 的数量 */
        private int gpsUpdated;
        private long lastPersistAt;
    }
}
