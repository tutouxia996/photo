package com.sq.bus.domain.vo;

import lombok.Data;

/**
 * 单张照片 AI 出图请求。
 */
@Data
public class PhotoDrawRequest {

    /** 预设：travel-poster | photo-abstract */
    private String preset;

    /** 英文标题（可选，空则自动生成） */
    private String title;

    /** 水墨预设副标题 / 短句 */
    private String subtitle;

    /** 旅行海报三词关键词，如 word1 / word2 / word3 */
    private String keywords;
}
