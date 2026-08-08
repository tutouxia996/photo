package com.sq.admin.album.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sq.bus.config.AlbumProperties;
import com.sq.bus.constants.AlbumDeleted;
import com.sq.bus.domain.BizAlbum;
import com.sq.bus.domain.BizPhoto;
import com.sq.bus.service.IBizAlbumService;
import com.sq.bus.service.IBizPhotoService;
import com.sq.bus.utils.ExifParseUtils;
import com.sq.bus.utils.PhotoFieldUtils;
import com.sq.bus.utils.ThumbUtils;
import com.sq.common.annotation.Log;
import com.sq.common.core.controller.BaseController;
import com.sq.common.core.domain.AjaxResult;
import com.sq.common.core.page.TableDataInfo;
import com.sq.common.enums.BusinessType;
import com.sq.common.utils.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.URLConnection;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * 图片管理
 */
@RestController
@RequestMapping("/album/photo")
public class BizPhotoController extends BaseController {

    @Autowired
    private IBizPhotoService photoService;

    @Autowired
    private IBizAlbumService albumService;

    @Autowired
    private AlbumProperties albumProperties;

    /**
     * 媒体访问：默认缩略图；original=true 返回原文件（支持 Range，便于视频播放）
     */
    @GetMapping("/media/{photoId}")
    public void media(@PathVariable Long photoId,
                      @RequestParam(value = "original", defaultValue = "false") boolean original,
                      HttpServletRequest request,
                      HttpServletResponse response) throws Exception {
        BizPhoto photo = photoService.getById(photoId);
        if (photo == null || photo.getDeleted() != null && photo.getDeleted() == AlbumDeleted.PURGED) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        File file = resolveMediaFile(photo, original);
        if (file == null || !file.exists() || !file.isFile()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String contentType = URLConnection.guessContentTypeFromName(file.getName());
        if (contentType == null) {
            contentType = Files.probeContentType(file.toPath());
        }
        if (contentType == null) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        response.setHeader("Accept-Ranges", "bytes");
        response.setHeader("Cache-Control", "public, max-age=86400");
        writeFileWithRange(file, contentType, request.getHeader("Range"), response);
    }

    @PreAuthorize("@ss.hasPermi('album:photo:list')")
    @GetMapping("/list")
    public TableDataInfo list(BizPhoto query) {
        startPage();
        int deleted = query.getDeleted() == null ? AlbumDeleted.NORMAL : query.getDeleted();
        LambdaQueryWrapper<BizPhoto> wrapper = new LambdaQueryWrapper<BizPhoto>()
                .eq(query.getAlbumId() != null, BizPhoto::getAlbumId, query.getAlbumId())
                .eq(query.getFileType() != null, BizPhoto::getFileType, query.getFileType())
                .like(StringUtils.isNotEmpty(query.getFileName()), BizPhoto::getFileName, query.getFileName())
                .eq(BizPhoto::getDeleted, deleted)
                .orderByDesc(BizPhoto::getShootTime)
                .orderByDesc(BizPhoto::getPhotoId);
        return getDataTable(photoService.list(wrapper));
    }

    /**
     * 地图点位（含 GPS 的正常照片，供前端缩放距离聚合）
     */
    @PreAuthorize("@ss.hasPermi('album:photo:list')")
    @GetMapping("/mapPoints")
    public AjaxResult mapPoints(@RequestParam(required = false) Long albumId) {
        if (albumId != null) {
            BizAlbum album = albumService.getById(albumId);
            if (album == null || album.getDeleted() == null || album.getDeleted() != AlbumDeleted.NORMAL) {
                return error("相册不存在");
            }
        }
        LambdaQueryWrapper<BizPhoto> wrapper = new LambdaQueryWrapper<BizPhoto>()
                .eq(BizPhoto::getDeleted, AlbumDeleted.NORMAL)
                .isNotNull(BizPhoto::getLatitude)
                .isNotNull(BizPhoto::getLongitude)
                .eq(albumId != null, BizPhoto::getAlbumId, albumId)
                .orderByAsc(BizPhoto::getShootTime)
                .orderByAsc(BizPhoto::getPhotoId);
        return success(photoService.list(wrapper));
    }

    @PreAuthorize("@ss.hasPermi('album:photo:query')")
    @GetMapping("/{photoId}")
    public AjaxResult getInfo(@PathVariable Long photoId) {
        BizPhoto photo = photoService.getById(photoId);
        if (photo == null || photo.getDeleted() != null && photo.getDeleted() == AlbumDeleted.PURGED) {
            return error("图片不存在或已删除");
        }
        return success(photo);
    }

    @PreAuthorize("@ss.hasPermi('album:photo:upload')")
    @Log(title = "图片上传", businessType = BusinessType.INSERT)
    @PostMapping("/upload")
    public AjaxResult upload(@RequestParam("file") MultipartFile file,
                             @RequestParam("albumId") Long albumId) throws Exception {
        if (file == null || file.isEmpty()) {
            return error("上传文件不能为空");
        }
        BizAlbum album = albumService.getById(albumId);
        if (album == null || album.getDeleted() == null || album.getDeleted() != AlbumDeleted.NORMAL) {
            return error("相册不存在");
        }
        String original = PhotoFieldUtils.safeFileName(file.getOriginalFilename(), 160);
        String datePath = new SimpleDateFormat("yyyy/MM/dd").format(new Date());
        File dir = new File(albumProperties.getUploadPath(), datePath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String savedName = System.currentTimeMillis() + "_" + original;
        File dest = new File(dir, savedName);
        file.transferTo(dest);

        String md5 = md5Of(dest);
        BizPhoto exists = photoService.findByMd5(md5);
        if (exists != null) {
            dest.delete();
            return error("文件已存在，跳过重复上传");
        }
        BizPhoto reusable = photoService.findReusableByMd5(md5);
        if (reusable != null) {
            ExifParseUtils.ExifInfo exif = ExifParseUtils.parse(dest);
            String fileUrl = "/album/files/upload/" + datePath + "/" + savedName;
            String thumbUrl = null;
            try {
                File thumbDir = new File(albumProperties.getThumbPath(), datePath);
                File thumbFile = new File(thumbDir, "s_" + savedName);
                ThumbUtils.createThumbnail(dest, thumbFile, albumProperties.getThumb().getSmallWidth());
                thumbUrl = "/album/files/thumb/" + datePath + "/s_" + savedName;
            } catch (Exception ignored) {
            }
            Long oldAlbumId = reusable.getAlbumId();
            reusable.setAlbumId(albumId);
            reusable.setFileName(original);
            reusable.setFilePath(dest.getAbsolutePath());
            reusable.setFileUrl(fileUrl);
            reusable.setThumbUrl(thumbUrl);
            reusable.setFileSize(dest.length());
            reusable.setFileType(1);
            reusable.setShootTime(exif.getShootTime() != null ? exif.getShootTime() : new Date());
            reusable.setLatitude(exif.getLatitude());
            reusable.setLongitude(exif.getLongitude());
            reusable.setCameraModel(exif.getCameraModel());
            reusable.setLensInfo(exif.getLensInfo());
            reusable.setAperture(exif.getAperture());
            reusable.setShutterSpeed(exif.getShutterSpeed());
            reusable.setIso(exif.getIso());
            reusable.setFocalLength(exif.getFocalLength());
            reusable.setMd5(md5);
            reusable.setDeleted(AlbumDeleted.NORMAL);
            reusable.setUpdateBy(getUsername());
            reusable.setUpdateTime(new Date());
            PhotoFieldUtils.clamp(reusable);
            photoService.updateById(reusable);
            if (oldAlbumId != null && !oldAlbumId.equals(albumId)) {
                albumService.refreshAlbumStats(oldAlbumId);
            }
            albumService.refreshAlbumStats(albumId);
            return success(reusable);
        }

        ExifParseUtils.ExifInfo exif = ExifParseUtils.parse(dest);
        String fileUrl = "/album/files/upload/" + datePath + "/" + savedName;
        String thumbUrl = null;
        try {
            File thumbDir = new File(albumProperties.getThumbPath(), datePath);
            File thumbFile = new File(thumbDir, "s_" + savedName);
            ThumbUtils.createThumbnail(dest, thumbFile, albumProperties.getThumb().getSmallWidth());
            thumbUrl = "/album/files/thumb/" + datePath + "/s_" + savedName;
        } catch (Exception ignored) {
        }

        BizPhoto photo = new BizPhoto();
        photo.setAlbumId(albumId);
        photo.setFileName(original);
        photo.setFilePath(dest.getAbsolutePath());
        photo.setFileUrl(fileUrl);
        photo.setThumbUrl(thumbUrl);
        photo.setFileSize(dest.length());
        photo.setFileType(1);
        photo.setShootTime(exif.getShootTime() != null ? exif.getShootTime() : new Date());
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
        photo.setCreateBy(getUsername());
        photo.setCreateTime(new Date());
        PhotoFieldUtils.clamp(photo);
        photoService.save(photo);
        albumService.refreshAlbumStats(albumId);
        return success(photo);
    }

    @PreAuthorize("@ss.hasPermi('album:photo:edit')")
    @Log(title = "图片管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody BizPhoto photo) {
        PhotoFieldUtils.clamp(photo);
        photo.setUpdateBy(getUsername());
        photo.setUpdateTime(new Date());
        boolean ok = photoService.updateById(photo);
        if (ok && photo.getAlbumId() != null) {
            albumService.refreshAlbumStats(photo.getAlbumId());
        }
        return toAjax(ok);
    }

    @PreAuthorize("@ss.hasPermi('album:photo:remove')")
    @Log(title = "图片回收站", businessType = BusinessType.DELETE)
    @DeleteMapping("/{photoIds}")
    public AjaxResult remove(@PathVariable Long[] photoIds) {
        return toAjax(photoService.trashPhotos(Arrays.asList(photoIds)));
    }

    @PreAuthorize("@ss.hasPermi('album:photo:edit')")
    @Log(title = "图片恢复", businessType = BusinessType.UPDATE)
    @PutMapping("/restore/{photoIds}")
    public AjaxResult restore(@PathVariable Long[] photoIds) {
        return toAjax(photoService.restorePhotos(Arrays.asList(photoIds)));
    }

    @PreAuthorize("@ss.hasPermi('album:photo:remove')")
    @Log(title = "图片彻底删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/purge/{photoIds}")
    public AjaxResult purge(@PathVariable Long[] photoIds) {
        return toAjax(photoService.purgePhotos(Arrays.asList(photoIds)));
    }

    private File resolveMediaFile(BizPhoto photo, boolean original) {
        if (!original
                && StringUtils.isNotEmpty(photo.getThumbUrl())
                && photo.getThumbUrl().startsWith("/album/files/thumb/")) {
            String rel = photo.getThumbUrl().substring("/album/files/thumb/".length());
            File thumb = new File(albumProperties.getThumbPath(), rel);
            if (thumb.exists() && thumb.isFile()) {
                return thumb;
            }
        }
        // 视频无缩略图时：非原图请求也不回退到视频本体，避免 <img> 无法渲染
        if (!original && photo.getFileType() != null && photo.getFileType() == 2) {
            return null;
        }
        if (StringUtils.isNotEmpty(photo.getFilePath())) {
            File origin = new File(photo.getFilePath());
            if (origin.exists() && origin.isFile()) {
                return origin;
            }
        }
        return null;
    }

    private void writeFileWithRange(File file, String contentType, String rangeHeader,
                                    HttpServletResponse response) throws Exception {
        long fileLength = file.length();
        long start = 0;
        long end = fileLength - 1;
        boolean isPartial = false;
        if (StringUtils.isNotEmpty(rangeHeader) && rangeHeader.startsWith("bytes=")) {
            String[] parts = rangeHeader.substring(6).split("-");
            try {
                if (parts.length > 0 && StringUtils.isNotEmpty(parts[0])) {
                    start = Long.parseLong(parts[0]);
                }
                if (parts.length > 1 && StringUtils.isNotEmpty(parts[1])) {
                    end = Long.parseLong(parts[1]);
                }
                if (end >= fileLength) {
                    end = fileLength - 1;
                }
                if (start > end || start < 0) {
                    response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                    response.setHeader("Content-Range", "bytes */" + fileLength);
                    return;
                }
                isPartial = true;
            } catch (NumberFormatException ignored) {
                start = 0;
                end = fileLength - 1;
                isPartial = false;
            }
        }
        long contentLength = end - start + 1;
        response.setContentType(contentType);
        response.setHeader("Content-Length", String.valueOf(contentLength));
        if (isPartial) {
            response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
            response.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + fileLength);
        } else {
            response.setStatus(HttpServletResponse.SC_OK);
        }
        try (RandomAccessFile raf = new RandomAccessFile(file, "r");
             OutputStream out = response.getOutputStream()) {
            raf.seek(start);
            byte[] buffer = new byte[8192];
            long remain = contentLength;
            while (remain > 0) {
                int read = raf.read(buffer, 0, (int) Math.min(buffer.length, remain));
                if (read < 0) {
                    break;
                }
                out.write(buffer, 0, read);
                remain -= read;
            }
            out.flush();
        } catch (IOException e) {
            // 客户端中断（切页、拖进度、关闭标签等）属正常现象，勿上抛以免全局异常处理再写 JSON
            if (!isClientAbort(e)) {
                throw e;
            }
        }
    }

    private static boolean isClientAbort(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String name = t.getClass().getName();
            if (name.endsWith("ClientAbortException") || name.endsWith("EofException")) {
                return true;
            }
            String msg = t.getMessage();
            if (msg != null) {
                String lower = msg.toLowerCase();
                if (lower.contains("broken pipe")
                        || lower.contains("connection reset")
                        || msg.contains("远程主机强迫关闭")
                        || msg.contains("你的主机中的软件中止了一个已建立的连接")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String md5Of(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] data = java.nio.file.Files.readAllBytes(file.toPath());
        byte[] digest = md.digest(data);
        StringBuilder sb = new StringBuilder(32);
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
