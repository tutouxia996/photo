package com.sq.bus.service;

import com.sq.bus.domain.BizPhoto;
import com.sq.bus.domain.vo.PhotoScoreRequest;

import java.util.Map;

public interface IPhotoQualityService {

    /**
     * 为相册（或指定照片）打出图质量分。
     */
    Map<String, Object> scoreAlbum(Long albumId, PhotoScoreRequest request);

    /**
     * 是否允许拿去图生图：必须是图片且分数达标。
     */
    boolean isEligibleForDraw(BizPhoto photo);
}
