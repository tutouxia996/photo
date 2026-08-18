package com.sq.bus.domain.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 阿里云盘同步进度。
 * status：0 下载中，1 完成，2 失败，3 已暂停。
 */
@Data
public class AliyunSyncProgress {
    private int status;
    /** listing / downloading / scanning / paused / done / failed */
    private String phase;
    private int total;
    private int needDownload;
    private int downloaded;
    private int skipped;
    private int failed;
    /** 尚未下载完的文件数（含进行中） */
    private int remaining;
    private int percent;
    private String message;
    private boolean paused;
    private boolean running;
    private boolean canPause;
    private boolean canResume;
    private List<CurrentFile> currentFiles = new ArrayList<CurrentFile>();
    /** 本轮失败的文件（继续下载时会重试） */
    private List<FailedFile> failedFiles = new ArrayList<FailedFile>();

    @Data
    public static class CurrentFile {
        private String name;
        private long written;
        private long expected;
        private int percent;
    }

    @Data
    public static class FailedFile {
        private String name;
        private String reason;
    }
}
