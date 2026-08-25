package com.sq.bus.domain.vo;

import java.io.Serializable;

/**
 * 视频浏览代理片状态（原片之外的可播档位）。
 */
public class VideoProxyStatus implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long photoId;
    private String quality;
    private Integer fps;
    private String variant;
    /** missing | generating | ready | failed | original */
    private String status;
    private String message;
    private Long fileSize;
    private String playUrl;

    /** 原片分辨率宽（ffprobe） */
    private Integer sourceWidth;
    /** 原片分辨率高 */
    private Integer sourceHeight;
    /** 原片帧率（四舍五入） */
    private Integer sourceFps;
    /** 如 1920x1080@30fps，供前端「原片」选项展示 */
    private String sourceLabel;

    public Long getPhotoId() {
        return photoId;
    }

    public void setPhotoId(Long photoId) {
        this.photoId = photoId;
    }

    public String getQuality() {
        return quality;
    }

    public void setQuality(String quality) {
        this.quality = quality;
    }

    public Integer getFps() {
        return fps;
    }

    public void setFps(Integer fps) {
        this.fps = fps;
    }

    public String getVariant() {
        return variant;
    }

    public void setVariant(String variant) {
        this.variant = variant;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getPlayUrl() {
        return playUrl;
    }

    public void setPlayUrl(String playUrl) {
        this.playUrl = playUrl;
    }

    public Integer getSourceWidth() {
        return sourceWidth;
    }

    public void setSourceWidth(Integer sourceWidth) {
        this.sourceWidth = sourceWidth;
    }

    public Integer getSourceHeight() {
        return sourceHeight;
    }

    public void setSourceHeight(Integer sourceHeight) {
        this.sourceHeight = sourceHeight;
    }

    public Integer getSourceFps() {
        return sourceFps;
    }

    public void setSourceFps(Integer sourceFps) {
        this.sourceFps = sourceFps;
    }

    public String getSourceLabel() {
        return sourceLabel;
    }

    public void setSourceLabel(String sourceLabel) {
        this.sourceLabel = sourceLabel;
    }
}
