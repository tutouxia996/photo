package com.sq.bus.domain.vo;

import java.io.Serializable;

/**
 * 相册打包下载进度（右下角浮层轮询）。
 * status: 0 进行中，1 成功（可取文件或已完成），2 失败
 * phase: packing | ready | transferring | done | failed
 */
public class AlbumDownloadProgress implements Serializable {

    private static final long serialVersionUID = 1L;

    private int status = 1;
    private boolean running = false;
    private String phase = "done";
    private String taskId;
    private Long albumId;
    private String albumName;
    private String fileName;
    private int total = 0;
    private int packed = 0;
    private int skipped = 0;
    private int percent = 0;
    private long bytesTotal = 0;
    private long bytesWritten = 0;
    private String currentFile = "";
    private String message = "";

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

    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public Long getAlbumId() {
        return albumId;
    }

    public void setAlbumId(Long albumId) {
        this.albumId = albumId;
    }

    public String getAlbumName() {
        return albumName;
    }

    public void setAlbumName(String albumName) {
        this.albumName = albumName;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getPacked() {
        return packed;
    }

    public void setPacked(int packed) {
        this.packed = packed;
    }

    public int getSkipped() {
        return skipped;
    }

    public void setSkipped(int skipped) {
        this.skipped = skipped;
    }

    public int getPercent() {
        return percent;
    }

    public void setPercent(int percent) {
        this.percent = percent;
    }

    public long getBytesTotal() {
        return bytesTotal;
    }

    public void setBytesTotal(long bytesTotal) {
        this.bytesTotal = bytesTotal;
    }

    public long getBytesWritten() {
        return bytesWritten;
    }

    public void setBytesWritten(long bytesWritten) {
        this.bytesWritten = bytesWritten;
    }

    public String getCurrentFile() {
        return currentFile;
    }

    public void setCurrentFile(String currentFile) {
        this.currentFile = currentFile;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
