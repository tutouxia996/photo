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
}
