package com.sq.bus.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sq.bus.config.AlbumProperties;
import com.sq.bus.constants.AlbumDeleted;
import com.sq.bus.domain.BizAlbum;
import com.sq.bus.domain.BizPhoto;
import com.sq.bus.domain.vo.PhotoScoreRequest;
import com.sq.bus.service.IBizAlbumService;
import com.sq.bus.service.IBizPhotoService;
import com.sq.bus.service.IPhotoQualityService;
import com.sq.bus.service.score.PhotoQualityAnalyzer;
import com.sq.bus.service.score.PhotoQualityResult;
import com.sq.common.exception.ServiceException;
import com.sq.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PhotoQualityServiceImpl implements IPhotoQualityService {

    private static final Logger log = LoggerFactory.getLogger(PhotoQualityServiceImpl.class);

    @Autowired
    private IBizPhotoService photoService;

    @Autowired
    private IBizAlbumService albumService;

    @Autowired
    private AlbumProperties albumProperties;

    @Override
    public Map<String, Object> scoreAlbum(Long albumId, PhotoScoreRequest request) {
        BizAlbum album = albumService.getById(albumId);
        if (album == null || album.getDeleted() == null || album.getDeleted() != AlbumDeleted.NORMAL) {
            throw new ServiceException("相册不存在");
        }
        AlbumProperties.PhotoScoreConfig cfg = albumProperties.getPhotoScore();
        int passScore = cfg == null ? 70 : cfg.getPassScore();
        int analyzeWidth = cfg == null ? 320 : cfg.getAnalyzeWidth();
        int minShortSide = cfg == null ? 720 : cfg.getMinShortSide();
        boolean force = request != null && Boolean.TRUE.equals(request.getForce());

        LambdaQueryWrapper<BizPhoto> wrapper = new LambdaQueryWrapper<BizPhoto>()
                .eq(BizPhoto::getAlbumId, albumId)
                .eq(BizPhoto::getDeleted, AlbumDeleted.NORMAL)
                .orderByAsc(BizPhoto::getPhotoId);
        List<BizPhoto> all = photoService.list(wrapper);
        Set<Long> idFilter = null;
        if (request != null && request.getPhotoIds() != null && !request.getPhotoIds().isEmpty()) {
            idFilter = new HashSet<Long>(request.getPhotoIds());
        }

        int scored = 0;
        int passed = 0;
        int failed = 0;
        int skipped = 0;
        Date now = new Date();
        List<BizPhoto> toUpdate = new ArrayList<BizPhoto>();

        for (BizPhoto photo : all) {
            if (idFilter != null && !idFilter.contains(photo.getPhotoId())) {
                continue;
            }
            if (!force && photo.getAestheticScore() != null) {
                skipped++;
                continue;
            }
            PhotoQualityResult result;
            if (photo.getFileType() != null && photo.getFileType() == 2) {
                result = PhotoQualityResult.fail(0, "视频不参与图生图");
            } else {
                File file = resolveFile(photo);
                result = PhotoQualityAnalyzer.analyze(file, analyzeWidth, minShortSide, passScore);
            }
            photo.setAestheticScore(result.getScore());
            photo.setScorePass(result.isPassed() ? 1 : 0);
            photo.setScoreReason(result.getReason());
            photo.setScoredAt(now);
            toUpdate.add(photo);
            scored++;
            if (result.isPassed()) {
                passed++;
            } else {
                failed++;
            }
        }

        if (!toUpdate.isEmpty()) {
            photoService.updateBatchById(toUpdate, 80);
        }

        Map<String, Object> map = new HashMap<String, Object>();
        map.put("albumId", albumId);
        map.put("passScore", passScore);
        map.put("scored", scored);
        map.put("passed", passed);
        map.put("failed", failed);
        map.put("skipped", skipped);
        log.info("photo score albumId={} scored={} passed={} failed={} skipped={}",
                albumId, scored, passed, failed, skipped);
        return map;
    }

    @Override
    public boolean isEligibleForDraw(BizPhoto photo) {
        if (photo == null) {
            return false;
        }
        if (photo.getDeleted() != null && photo.getDeleted() != AlbumDeleted.NORMAL) {
            return false;
        }
        if (photo.getFileType() != null && photo.getFileType() == 2) {
            return false;
        }
        return photo.getScorePass() != null && photo.getScorePass() == 1;
    }

    private File resolveFile(BizPhoto photo) {
        if (photo == null) {
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
}
