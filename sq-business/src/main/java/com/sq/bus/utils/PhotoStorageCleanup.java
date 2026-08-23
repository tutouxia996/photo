package com.sq.bus.utils;

import com.sq.bus.config.AlbumProperties;
import com.sq.bus.domain.BizPhoto;
import com.sq.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * 彻底删除时清理本地原图、缩略图与视频浏览代理片。
 */
public final class PhotoStorageCleanup {

    private static final Logger log = LoggerFactory.getLogger(PhotoStorageCleanup.class);

    private PhotoStorageCleanup() {
    }

    public static void deleteLocalFiles(BizPhoto photo, AlbumProperties albumProperties) {
        if (photo == null) {
            return;
        }
        deleteQuietly(resolveOriginFile(photo));
        deleteQuietly(resolveThumbFile(photo, albumProperties));
        deleteProxyDir(photo, albumProperties);
    }

    /** 删除 cache/proxy/{photoId}/ 下全部浏览档（不碰原片目录） */
    private static void deleteProxyDir(BizPhoto photo, AlbumProperties albumProperties) {
        if (photo.getPhotoId() == null || albumProperties == null
                || StringUtils.isEmpty(albumProperties.getProxyPath())) {
            return;
        }
        File dir = new File(albumProperties.getProxyPath(), String.valueOf(photo.getPhotoId()));
        if (!dir.exists()) {
            return;
        }
        File[] children = dir.listFiles();
        if (children != null) {
            for (File f : children) {
                deleteQuietly(f);
            }
        }
        try {
            if (dir.exists() && dir.isDirectory() && !dir.delete()) {
                log.warn("failed to delete proxy dir: {}", dir.getAbsolutePath());
            }
        } catch (Exception e) {
            log.warn("delete proxy dir error {}: {}", dir.getAbsolutePath(), e.getMessage());
        }
    }

    private static File resolveOriginFile(BizPhoto photo) {
        if (StringUtils.isEmpty(photo.getFilePath())) {
            return null;
        }
        return new File(photo.getFilePath());
    }

    private static File resolveThumbFile(BizPhoto photo, AlbumProperties albumProperties) {
        if (albumProperties == null || StringUtils.isEmpty(albumProperties.getThumbPath())) {
            return null;
        }
        String thumbUrl = photo.getThumbUrl();
        if (StringUtils.isEmpty(thumbUrl) || !thumbUrl.startsWith("/album/files/thumb/")) {
            return null;
        }
        String rel = thumbUrl.substring("/album/files/thumb/".length());
        if (StringUtils.isEmpty(rel) || rel.contains("..")) {
            return null;
        }
        return new File(albumProperties.getThumbPath(), rel);
    }

    private static void deleteQuietly(File file) {
        if (file == null) {
            return;
        }
        try {
            if (file.exists() && file.isFile()) {
                if (!file.delete()) {
                    log.warn("failed to delete local file: {}", file.getAbsolutePath());
                } else {
                    log.info("deleted local file: {}", file.getAbsolutePath());
                }
            }
        } catch (Exception e) {
            log.warn("delete local file error {}: {}", file.getAbsolutePath(), e.getMessage());
        }
    }
}
