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
 * 轨迹点 biz_track_point
 */
@Data
@TableName("biz_track_point")
public class BizTrackPoint implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "point_id", type = IdType.AUTO)
    private Long pointId;

    private Long trackId;

    private Long photoId;

    private BigDecimal latitude;

    private BigDecimal longitude;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date pointTime;

    private Integer sequence;

    private BigDecimal altitude;

    /** 点位说明（车次、路线等） */
    private String description;

    /**
     * 到下一站的出行方式：
     * hsr/train/bus/metro/walk/drive/bike/flight/other
     */
    private String travelMode;

    /**
     * 到下一站的真实路线折线（JSON:[[lat,lng],...]，GCJ-02，供高德底图直接绘制）
     */
    private String routePath;

    /** 非表字段：关联媒体展示用 */
    @TableField(exist = false)
    private Integer fileType;

    @TableField(exist = false)
    private String fileName;

    @TableField(exist = false)
    private String thumbUrl;

    @TableField(exist = false)
    private String fileUrl;

    @TableField(exist = false)
    private String address;

    @TableField(exist = false)
    private Integer duration;
}
