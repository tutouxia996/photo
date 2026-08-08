package com.sq.bus.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sq.bus.config.AlbumProperties;
import com.sq.bus.domain.BizPhoto;
import com.sq.bus.domain.BizScanLog;
import com.sq.bus.domain.BizScanPath;
import com.sq.bus.mapper.BizScanPathMapper;
import com.sq.bus.service.IBizAlbumService;
import com.sq.bus.service.IBizPhotoService;
import com.sq.bus.service.IBizScanLogService;
import com.sq.bus.service.IBizScanPathService;
import com.sq.bus.utils.ExifParseUtils;
import com.sq.bus.utils.ThumbUtils;
import com.sq.common.exception.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Service
public class BizScanPathServiceImpl extends ServiceImpl<BizScanPathMapper, BizScanPath> implements IBizScanPathService {

    private static final Set<String> IMAGE_EXT = new HashSet<String>(Arrays.asList(
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "tif", "tiff"));
    private static final Set<String> VIDEO_EXT = new HashSet<String>(Arrays.asList(
            "mp4", "mov", "avi", "mkv", "wmv"));

    @Autowired
    private IBizScanLogService scanLogService;

    @Autowired
    private IBizPhotoService photoService;

    @Autowired
    private IBizAlbumService albumService;

    @Autowired
    private AlbumProperties albumProperties;

    @Override
    public Long runScan(Long pathId, boolean fullScan) {
        BizScanPath scanPath = getById(pathId);
        if (scanPath == null) {
            throw new ServiceException("扫描目录不存在");
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

        BizScanLog log = new BizScanLog();
        log.setPathId(pathId);
        log.setScanType(fullScan ? 2 : 1);
        log.setStatus(0);
        log.setStartTime(new Date());
        log.setCreateTime(new Date());
        log.setTotalCount(0);
        log.setNewCount(0);
        log.setSkipCount(0);
        log.setFailCount(0);
        scanLogService.save(log);

        StringBuilder failMsg = new StringBuilder();
        ScanCounter counter = new ScanCounter();
        try {
            doWalk(root, scanPath, fullScan, counter, failMsg);
        } catch (Exception e) {
            counter.failed++;
            failMsg.append(e.getMessage());
        }

        albumService.refreshAlbumStats(scanPath.getDefaultAlbumId());

        scanPath.setLastScanTime(new Date());
        scanPath.setUpdateTime(new Date());
        updateById(scanPath);

        log.setTotalCount(counter.total);
        log.setNewCount(counter.created);
        log.setSkipCount(counter.skipped);
        log.setFailCount(counter.failed);
        log.setStatus(counter.failed > 0 && counter.created == 0 ? 2 : 1);
        log.setMessage(failMsg.length() == 0 ? "扫描完成" : failMsg.toString());
        log.setEndTime(new Date());
        scanLogService.updateById(log);
        return log.getLogId();
    }

    private void doWalk(File dir, BizScanPath scanPath, boolean fullScan, ScanCounter counter, StringBuilder failMsg) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File file : children) {
            if (file.isDirectory()) {
                doWalk(file, scanPath, fullScan, counter, failMsg);
                continue;
            }
            String ext = extension(file.getName());
            if (!IMAGE_EXT.contains(ext) && !VIDEO_EXT.contains(ext)) {
                continue;
            }
            counter.total++;
            try {
                String md5 = md5Of(file);
                BizPhoto exists = photoService.findByMd5(md5);
                if (exists != null) {
                    Long existsAlbumId = exists.getAlbumId();
                    boolean sameAlbum = existsAlbumId != null
                            && existsAlbumId.equals(scanPath.getDefaultAlbumId());
                    boolean albumAlive = existsAlbumId != null
                            && albumService.getById(existsAlbumId) != null;
                    if (sameAlbum || albumAlive) {
                        // 已在当前/其他有效相册中：按内容去重跳过
                        counter.skipped++;
                        continue;
                    }
                    // 相册已删留下的孤儿记录，清理后允许重新导入
                    photoService.removeById(exists.getPhotoId());
                }
                importFile(file, scanPath, md5, IMAGE_EXT.contains(ext) ? 1 : 2);
                counter.created++;
            } catch (Exception ex) {
                counter.failed++;
                if (failMsg.length() < 800) {
                    failMsg.append(file.getName()).append(':').append(ex.getMessage()).append(';');
                }
            }
        }
    }

    private void importFile(File file, BizScanPath scanPath, String md5, int fileType) throws Exception {
        ExifParseUtils.ExifInfo exif = ExifParseUtils.parse(file);
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
        photo.setCreateTime(new Date());
        photoService.save(photo);
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
        private int total;
        private int created;
        private int skipped;
        private int failed;
    }
}
