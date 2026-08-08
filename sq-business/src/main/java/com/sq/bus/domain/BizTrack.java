package com.sq.bus.domain;

import com.baomidou.mybatisplus.annotation.IdType;
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

    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    private String updateBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    private String remark;
}
