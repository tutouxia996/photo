package com.sq.bus.domain;

import lombok.Data;

import java.util.List;

/**
 * AI 地标识别请求：由用户在照片地图上点选要送检的媒体。
 */
@Data
public class AiLandmarkRequest {

    /** 要送视觉模型识别的照片 ID 列表（必填） */
    private List<Long> photoIds;

    /**
     * 识别成功后是否用 AI 点对剩余区域粗定位做时间推算。
     * 默认 true。
     */
    private Boolean interpolateOthers;
}
