package com.sq.bus.service;

import com.sq.bus.domain.vo.PhotoDrawBatchRequest;
import com.sq.bus.domain.vo.PhotoDrawRequest;

import java.util.List;
import java.util.Map;

public interface IPhotoDrawService {

    /**
     * 可用出图预设列表。
     */
    List<Map<String, Object>> listPresets();

    /**
     * 对单张照片执行 AI 出图，成功后将新图入库并返回。
     */
    Map<String, Object> drawPhoto(Long photoId, PhotoDrawRequest request, String operator);

    /**
     * 批量 AI 出图（逐张调用，部分失败不影响其余）。
     */
    Map<String, Object> drawPhotoBatch(Long albumId, PhotoDrawBatchRequest request, String operator);
}
