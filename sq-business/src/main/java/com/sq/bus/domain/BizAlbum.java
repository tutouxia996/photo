package com.sq.bus.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 相册 biz_album
 */
@Data
@TableName("biz_album")
public class BizAlbum implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "album_id", type = IdType.AUTO)
    private Long albumId;

    private String albumName;

    private String albumDesc;

    private String coverUrl;

    /** 是否公开：0私有 1公开 */
    private Integer isPublic;

    private Integer photoCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date endTime;

    private String locationSummary;

    private Integer sortOrder;

    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    private String updateBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    private String remark;
}
