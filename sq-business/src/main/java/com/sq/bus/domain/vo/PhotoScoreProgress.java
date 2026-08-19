package com.sq.bus.domain.vo;

import lombok.Data;

/**
 * 相册照片后台打分进度。
 */
@Data
public class PhotoScoreProgress {

    /** 0进行中 1完成 2失败 */
    private int status = 1;

    private Long albumId;

    private String albumName;

    /** 本次要打分的张数（不含已跳过） */
    private int total;

    private int done;

    private int passed;

    private int failed;

    private int skipped;

    private int remaining;

    private int percent;

    private boolean running;

    private String currentFile;

    private String message;
}
