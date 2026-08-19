package com.sq.bus.domain.vo;

import lombok.Data;

import java.util.List;

/**
 * 相册照片质量打分请求。
 */
@Data
public class PhotoScoreRequest {

    /** 只打这些照片；空则打整个相册未打分（或 force 时全部） */
    private List<Long> photoIds;

    /** true=已打过分的也重算 */
    private Boolean force;
}
