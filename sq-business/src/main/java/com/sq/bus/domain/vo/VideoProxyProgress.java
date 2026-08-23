package com.sq.bus.domain.vo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 视频浏览档后台转码全局进度（供右下角浮层轮询）。
 */
public class VideoProxyProgress implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 0 进行中，1 成功结束，2 有失败 */
    private int status = 1;
    private boolean running = false;
    private int total = 0;
    private int done = 0;
    private int failed = 0;
    private int generating = 0;
    private int remaining = 0;
    private int percent = 0;
    private String message = "";
    /** 本批排队时跳过的视频数（不符合转码条件或已就绪） */
    private int skippedVideos = 0;
    private List<VideoProxyJobItem> currentJobs = new ArrayList<VideoProxyJobItem>();
    private List<VideoProxyJobItem> failedJobs = new ArrayList<VideoProxyJobItem>();

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getDone() {
        return done;
    }

    public void setDone(int done) {
        this.done = done;
    }

    public int getFailed() {
        return failed;
    }

    public void setFailed(int failed) {
        this.failed = failed;
    }

    public int getGenerating() {
        return generating;
    }

    public void setGenerating(int generating) {
        this.generating = generating;
    }

    public int getRemaining() {
        return remaining;
    }

    public void setRemaining(int remaining) {
        this.remaining = remaining;
    }

    public int getPercent() {
        return percent;
    }

    public void setPercent(int percent) {
        this.percent = percent;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getSkippedVideos() {
        return skippedVideos;
    }

    public void setSkippedVideos(int skippedVideos) {
        this.skippedVideos = skippedVideos;
    }

    public List<VideoProxyJobItem> getCurrentJobs() {
        return currentJobs;
    }

    public void setCurrentJobs(List<VideoProxyJobItem> currentJobs) {
        this.currentJobs = currentJobs;
    }

    public List<VideoProxyJobItem> getFailedJobs() {
        return failedJobs;
    }

    public void setFailedJobs(List<VideoProxyJobItem> failedJobs) {
        this.failedJobs = failedJobs;
    }
}
