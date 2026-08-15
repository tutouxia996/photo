package com.sq.bus.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 轨迹 biz_track
 */
@Data
@TableName("biz_track")
public class BizTrack implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "track_id", type = IdType.AUTO)
    private Long trackId;

    private Long albumId;

    private String trackName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date endTime;

    private BigDecimal totalDistance;

    private Long totalDuration;

    private Integer pointCount;

    private String trackColor;

    /** 是否前台展示：0否 1是 */
    private Integer isPublic;

    /** 是否启用轨迹：0关闭（不自动同步、前台地图不画照片轨折线）1开启；不影响照片点与后台入口 */
    private Integer enabled;

    /** 是否在地图启用 GPX 线路：0否 1是 */
    private Integer gpxEnabled;

    /** 来源：photo / gpx / mixed */
    private String sourceType;

    /**
     * 库内真实照片轨点位数（非表字段）。列表 pointCount 可能因关闭「启用轨迹」而展示为 0，
     * 入口显隐请用本字段，避免「照片地图/轨迹」被误藏。
     */
    @TableField(exist = false)
    private Integer photoPointCount;

    /** 相册是否已导入 GPX（非表字段，接口回填） */
    @TableField(exist = false)
    private Integer hasGpx;

    /**
     * 相册是否已有权威 GPS 照片/视频（EXIF/视频/手工确认，非表字段）
     */
    @TableField(exist = false)
    private Integer hasAuthoritativeGps;

    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    private String updateBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    /** 行程说明/备注 */
    private String remark;

    /** 0未删除 1已删除 */
    private Integer deleted;
}
