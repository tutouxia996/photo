package com.sq.bus.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 相册分享 biz_album_share
 */
@Data
@TableName("biz_album_share")
public class BizAlbumShare implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "share_id", type = IdType.AUTO)
    private Long shareId;

    private Long albumId;

    /** 分享令牌（URL 用，不可猜测） */
    private String shareToken;

    /** 是否分享轨迹：0否 1是 */
    private Integer shareTrack;

    /** 媒体范围：all / image / video */
    private String mediaScope;

    /** 选择模式：all / include / exclude */
    private String selectMode;

    /** 所选/排除的照片 ID JSON 数组 */
    private String photoIds;

    /** 状态：1启用 0停用 */
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date expireTime;

    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    private String updateBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    private String remark;

    /** 解析后的照片 ID（非库字段） */
    @TableField(exist = false)
    private List<Long> photoIdList;

    /** 相册名称（非库字段，列表展示用） */
    @TableField(exist = false)
    private String albumName;
}
