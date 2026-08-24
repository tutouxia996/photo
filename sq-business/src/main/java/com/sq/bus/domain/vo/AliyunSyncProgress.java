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
    /** 多相册：总数（1 表示单相册） */
    private int albumCount;
    /** 多相册：当前正在处理的相册序号，从 1 开始 */
    private int albumIndex;
    /** 多相册：当前相册名称 */
    private String currentAlbumName;
    /** 多相册累计：已列举出的待下载总数（后续相册会继续增加） */
    private int overallNeed;
    /** 多相册累计：尚未完成下载的文件数（含当前相册队列） */
    private int overallRemaining;
    private int overallDownloaded;
    private int overallSkipped;
    private int overallFailed;
    /** 多相册：尚未开始列举的相册数量 */
    private int albumsPending;
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
