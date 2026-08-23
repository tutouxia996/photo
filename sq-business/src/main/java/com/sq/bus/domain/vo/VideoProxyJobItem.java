package com.sq.bus.domain.vo;

import java.io.Serializable;

public class VideoProxyJobItem implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long photoId;
    private String fileName;
    private String variant;
    private String message;

    public Long getPhotoId() {
        return photoId;
    }

    public void setPhotoId(Long photoId) {
        this.photoId = photoId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getVariant() {
        return variant;
    }

    public void setVariant(String variant) {
        this.variant = variant;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
