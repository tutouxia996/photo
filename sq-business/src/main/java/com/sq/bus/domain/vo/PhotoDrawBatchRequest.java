package com.sq.bus.domain.vo;

import lombok.Data;

import java.util.List;

/**
 * 相册批量 AI 出图请求。
 */
@Data
public class PhotoDrawBatchRequest {

    /** 源照片 ID 列表 */
    private List<Long> photoIds;

    private String preset;

    private String title;

    private String subtitle;

    private String keywords;
}
