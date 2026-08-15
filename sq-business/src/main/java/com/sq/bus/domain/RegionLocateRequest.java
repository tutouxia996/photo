package com.sq.bus.domain;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 相册区域粗定位：为国家/省/市/区（或直接给坐标）无 GPS 媒体写入 region_center。
 */
@Data
public class RegionLocateRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 国家（可选，参与地理编码关键词） */
    private String country;

    private String province;

    private String city;

    private String district;

    /** 位置名称/详细地址（可选；不填则由行政区拼接） */
    private String address;

    /** 已解析的 WGS84 坐标（可选；不填则按行政区地理编码） */
    private BigDecimal latitude;

    private BigDecimal longitude;

    /**
     * 是否覆盖已有 region_center / ai_landmark / time_interp 等兜底坐标（默认 true）。
     * 不会覆盖 EXIF/视频/手工/GPX 等权威来源。
     */
    private Boolean overwriteRegionCenter;
}
