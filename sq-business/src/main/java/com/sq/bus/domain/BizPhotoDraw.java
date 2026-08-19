package com.sq.bus.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 照片 AI 出图记录 biz_photo_draw
 */
@Data
@TableName("biz_photo_draw")
public class BizPhotoDraw implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "draw_id", type = IdType.AUTO)
    private Long drawId;

    private Long albumId;

    private Long sourcePhotoId;

    private Long resultPhotoId;

    private String preset;

    private String status;

    private String taskId;

    private String errorMsg;

    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
}
