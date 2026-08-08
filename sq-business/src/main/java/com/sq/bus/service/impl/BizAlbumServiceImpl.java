package com.sq.bus.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sq.bus.constants.AlbumDeleted;
import com.sq.bus.domain.BizAlbum;
import com.sq.bus.domain.BizPhoto;
import com.sq.bus.mapper.BizAlbumMapper;
import com.sq.bus.mapper.BizPhotoMapper;
import com.sq.bus.service.IBizAlbumService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BizAlbumServiceImpl extends ServiceImpl<BizAlbumMapper, BizAlbum> implements IBizAlbumService {

    @Autowired
    private BizPhotoMapper photoMapper;

    @Override
    public void refreshAlbumStats(Long albumId) {
        if (albumId == null) {
            return;
        }
        List<BizPhoto> photos = photoMapper.selectList(new LambdaQueryWrapper<BizPhoto>()
                .eq(BizPhoto::getAlbumId, albumId)
                .eq(BizPhoto::getDeleted, AlbumDeleted.NORMAL)
                .orderByAsc(BizPhoto::getShootTime));
        BizAlbum album = getById(albumId);
        if (album == null) {
            return;
        }
        album.setPhotoCount(photos.size());
        Date start = null;
        Date end = null;
        Long coverPhotoId = null;
        Set<String> locations = new LinkedHashSet<String>();
        for (BizPhoto photo : photos) {
            if (photo.getShootTime() != null) {
                if (start == null || photo.getShootTime().before(start)) {
                    start = photo.getShootTime();
                }
                if (end == null || photo.getShootTime().after(end)) {
                    end = photo.getShootTime();
                }
            }
            if (photo.getCity() != null && photo.getCity().length() > 0) {
                locations.add(photo.getCity());
            } else if (photo.getProvince() != null && photo.getProvince().length() > 0) {
                locations.add(photo.getProvince());
            }
            // 优先用首张图片作为封面（视频不作为封面）
            if (coverPhotoId == null && photo.getFileType() != null && photo.getFileType() == 1) {
                coverPhotoId = photo.getPhotoId();
            }
        }
        album.setStartTime(start);
        album.setEndTime(end);
        if (!locations.isEmpty()) {
            album.setLocationSummary(locations.stream().limit(5).collect(Collectors.joining("、")));
        }
        if (coverPhotoId != null) {
            album.setCoverUrl("/album/photo/media/" + coverPhotoId);
        } else if (photos.isEmpty()) {
            album.setCoverUrl(null);
        }
        album.setUpdateTime(new Date());
        updateById(album);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean trashAlbums(Collection<Long> albumIds) {
        if (albumIds == null || albumIds.isEmpty()) {
            return false;
        }
        Date now = new Date();
        boolean ok = update(new LambdaUpdateWrapper<BizAlbum>()
                .in(BizAlbum::getAlbumId, albumIds)
                .eq(BizAlbum::getDeleted, AlbumDeleted.NORMAL)
                .set(BizAlbum::getDeleted, AlbumDeleted.TRASH)
                .set(BizAlbum::getUpdateTime, now));
        photoMapper.update(null, new LambdaUpdateWrapper<BizPhoto>()
                .in(BizPhoto::getAlbumId, albumIds)
                .eq(BizPhoto::getDeleted, AlbumDeleted.NORMAL)
                .set(BizPhoto::getDeleted, AlbumDeleted.TRASH)
                .set(BizPhoto::getUpdateTime, now));
        return ok;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean restoreAlbums(Collection<Long> albumIds) {
        if (albumIds == null || albumIds.isEmpty()) {
            return false;
        }
        Date now = new Date();
        boolean ok = update(new LambdaUpdateWrapper<BizAlbum>()
                .in(BizAlbum::getAlbumId, albumIds)
                .eq(BizAlbum::getDeleted, AlbumDeleted.TRASH)
                .set(BizAlbum::getDeleted, AlbumDeleted.NORMAL)
                .set(BizAlbum::getUpdateTime, now));
        photoMapper.update(null, new LambdaUpdateWrapper<BizPhoto>()
                .in(BizPhoto::getAlbumId, albumIds)
                .eq(BizPhoto::getDeleted, AlbumDeleted.TRASH)
                .set(BizPhoto::getDeleted, AlbumDeleted.NORMAL)
                .set(BizPhoto::getUpdateTime, now));
        if (ok) {
            for (Long albumId : albumIds) {
                refreshAlbumStats(albumId);
            }
        }
        return ok;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean purgeAlbums(Collection<Long> albumIds) {
        if (albumIds == null || albumIds.isEmpty()) {
            return false;
        }
        Date now = new Date();
        boolean ok = update(new LambdaUpdateWrapper<BizAlbum>()
                .in(BizAlbum::getAlbumId, albumIds)
                .in(BizAlbum::getDeleted, AlbumDeleted.TRASH, AlbumDeleted.NORMAL)
                .set(BizAlbum::getDeleted, AlbumDeleted.PURGED)
                .set(BizAlbum::getUpdateTime, now));
        photoMapper.update(null, new LambdaUpdateWrapper<BizPhoto>()
                .in(BizPhoto::getAlbumId, albumIds)
                .ne(BizPhoto::getDeleted, AlbumDeleted.PURGED)
                .set(BizPhoto::getDeleted, AlbumDeleted.PURGED)
                .set(BizPhoto::getUpdateTime, now));
        return ok;
    }

    @Override
    public boolean removeAlbums(Collection<Long> albumIds) {
        return trashAlbums(albumIds);
    }
}
