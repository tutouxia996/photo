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
 * 轨迹 GPX 文件 biz_track_gpx_file
 */
@Data
@TableName("biz_track_gpx_file")
public class BizTrackGpxFile implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "gpx_id", type = IdType.AUTO)
    private Long gpxId;

    private Long albumId;

    private String fileName;

    private String storagePath;

    private Integer pointCount;

    /** GPX 里程（公里） */
    private BigDecimal distanceKm;

    /** 出行方式：hsr/train/bus/metro/walk/drive/bike/flight/other */
    private String travelMode;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date endTime;

    /** 是否参与生成：0否 1是 */
    private Integer enabled;

    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    private String remark;

    /** 0未删除 1已删除 */
    private Integer deleted;
}
