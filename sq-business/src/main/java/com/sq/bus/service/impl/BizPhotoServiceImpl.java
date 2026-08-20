package com.sq.bus.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sq.bus.config.AlbumProperties;
import com.sq.bus.constants.AlbumDeleted;
import com.sq.bus.domain.BizAlbum;
import com.sq.bus.domain.BizPhoto;
import com.sq.bus.domain.BizPhotoDraw;
import com.sq.bus.domain.BizTrackPoint;
import com.sq.bus.mapper.BizPhotoDrawMapper;
import com.sq.bus.mapper.BizPhotoMapper;
import com.sq.bus.service.IBizAlbumService;
import com.sq.bus.service.IBizPhotoService;
import com.sq.bus.service.IBizTrackPointService;
import com.sq.bus.utils.PhotoStorageCleanup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class BizPhotoServiceImpl extends ServiceImpl<BizPhotoMapper, BizPhoto> implements IBizPhotoService {

    @Lazy
    @Autowired
    private IBizAlbumService albumService;

    @Autowired
    private AlbumProperties albumProperties;

    @Autowired
    private BizPhotoDrawMapper photoDrawMapper;

    @Autowired(required = false)
    private IBizTrackPointService trackPointService;

    @Override
    public List<Map<String, Object>> groupCountByDate(Long albumId) {
        if (albumId == null) {
            return Collections.emptyList();
        }
        return baseMapper.groupCountByDate(albumId);
    }

    @Override
    public BizPhoto findByMd5(String md5) {
        if (md5 == null || md5.isEmpty()) {
            return null;
        }
        return getOne(new LambdaQueryWrapper<BizPhoto>()
                .eq(BizPhoto::getMd5, md5)
                .eq(BizPhoto::getDeleted, AlbumDeleted.NORMAL)
                .last("limit 1"), false);
    }

    @Override
    public BizPhoto findReusableByMd5(String md5) {
        if (md5 == null || md5.isEmpty()) {
            return null;
        }
        // 仅复用回收站中的记录（彻底删除会物理清库与文件，不可再复用）
        return getOne(new LambdaQueryWrapper<BizPhoto>()
                .eq(BizPhoto::getMd5, md5)
                .eq(BizPhoto::getDeleted, AlbumDeleted.TRASH)
                .orderByDesc(BizPhoto::getPhotoId)
                .last("limit 1"), false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean trashPhotos(Collection<Long> photoIds) {
        return markPhotos(photoIds, AlbumDeleted.TRASH, AlbumDeleted.NORMAL);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean restorePhotos(Collection<Long> photoIds) {
        boolean ok = markPhotos(photoIds, AlbumDeleted.NORMAL, AlbumDeleted.TRASH);
        if (ok) {
            // 若所属相册仍在回收站，同步恢复相册，避免照片恢复后无处可见
            List<BizPhoto> photos = listByIds(photoIds);
            Set<Long> albumIds = new HashSet<Long>();
            for (BizPhoto photo : photos) {
                if (photo.getAlbumId() != null) {
                    albumIds.add(photo.getAlbumId());
                }
            }
            for (Long albumId : albumIds) {
                BizAlbum album = albumService.getById(albumId);
                if (album != null && album.getDeleted() != null && album.getDeleted() == AlbumDeleted.TRASH) {
                    albumService.update(new LambdaUpdateWrapper<BizAlbum>()
                            .eq(BizAlbum::getAlbumId, albumId)
                            .eq(BizAlbum::getDeleted, AlbumDeleted.TRASH)
                            .set(BizAlbum::getDeleted, AlbumDeleted.NORMAL)
                            .set(BizAlbum::getUpdateTime, new Date()));
                    albumService.refreshAlbumStats(albumId);
                }
            }
        }
        return ok;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean purgePhotos(Collection<Long> photoIds) {
        if (photoIds == null || photoIds.isEmpty()) {
            return false;
        }
        List<BizPhoto> photos = list(new LambdaQueryWrapper<BizPhoto>()
                .in(BizPhoto::getPhotoId, photoIds)
                .in(BizPhoto::getDeleted, AlbumDeleted.TRASH, AlbumDeleted.NORMAL, AlbumDeleted.PURGED));
        if (photos.isEmpty()) {
            return false;
        }

        List<Long> ids = new ArrayList<Long>(photos.size());
        for (BizPhoto photo : photos) {
            ids.add(photo.getPhotoId());
            PhotoStorageCleanup.deleteLocalFiles(photo, albumProperties);
        }

        cleanupRelatedRows(ids);
        boolean ok = removeByIds(ids);
        if (ok) {
            refreshAlbums(photos);
        }
        return ok;
    }

    private void cleanupRelatedRows(List<Long> photoIds) {
        if (photoIds == null || photoIds.isEmpty()) {
            return;
        }
        photoDrawMapper.delete(new LambdaQueryWrapper<BizPhotoDraw>()
                .and(w -> w.in(BizPhotoDraw::getSourcePhotoId, photoIds)
                        .or()
                        .in(BizPhotoDraw::getResultPhotoId, photoIds)));
        if (trackPointService != null) {
            trackPointService.remove(new LambdaQueryWrapper<BizTrackPoint>()
                    .in(BizTrackPoint::getPhotoId, photoIds));
        }
    }

    private boolean markPhotos(Collection<Long> photoIds, int toStatus, int fromStatus) {
        if (photoIds == null || photoIds.isEmpty()) {
            return false;
        }
        List<BizPhoto> photos = list(new LambdaQueryWrapper<BizPhoto>()
                .in(BizPhoto::getPhotoId, photoIds)
                .eq(BizPhoto::getDeleted, fromStatus));
        if (photos.isEmpty()) {
            return false;
        }
        List<Long> ids = new ArrayList<Long>(photos.size());
        for (BizPhoto photo : photos) {
            ids.add(photo.getPhotoId());
        }
        boolean ok = update(new LambdaUpdateWrapper<BizPhoto>()
                .in(BizPhoto::getPhotoId, ids)
                .eq(BizPhoto::getDeleted, fromStatus)
                .set(BizPhoto::getDeleted, toStatus)
                .set(BizPhoto::getUpdateTime, new Date()));
        if (ok) {
            refreshAlbums(photos);
        }
        return ok;
    }

    private void refreshAlbums(List<BizPhoto> photos) {
        Set<Long> albumIds = new HashSet<Long>();
        for (BizPhoto photo : photos) {
            if (photo.getAlbumId() != null) {
                albumIds.add(photo.getAlbumId());
            }
        }
        for (Long albumId : albumIds) {
            albumService.refreshAlbumStats(albumId);
        }
    }
}
