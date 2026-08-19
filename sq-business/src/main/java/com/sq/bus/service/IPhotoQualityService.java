package com.sq.bus.service;

import com.sq.bus.domain.BizPhoto;
import com.sq.bus.domain.vo.PhotoScoreProgress;
import com.sq.bus.domain.vo.PhotoScoreRequest;

import java.util.Map;

public interface IPhotoQualityService {

    /**
     * 启动相册后台打分（立即返回进度快照，不阻塞）。
     */
    Map<String, Object> startScoreAlbum(Long albumId, PhotoScoreRequest request);

    /**
     * 当前打分进度。
     */
    PhotoScoreProgress getScoreProgress();

    /**
     * 是否允许拿去图生图：必须是图片且分数达标。
     */
    boolean isEligibleForDraw(BizPhoto photo);
}
