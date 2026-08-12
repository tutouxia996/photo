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
 * 图片 biz_photo
 */
@Data
@TableName("biz_photo")
public class BizPhoto implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "photo_id", type = IdType.AUTO)
    private Long photoId;

    private Long albumId;

    private String fileName;

    private String filePath;

    private String fileUrl;

    private String thumbUrl;

    private Long fileSize;

    /** 文件类型：1图片 2视频 */
    private Integer fileType;

    /** 视频时长（秒） */
    private Integer duration;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date shootTime;

    private BigDecimal latitude;

    private BigDecimal longitude;

    /**
     * 坐标来源：exif / video / gpx_match / manual / time_interp / ai_landmark / region_center
     */
    private String locationSource;

    /** 坐标置信度 0~1（兜底估计用） */
    private BigDecimal locationConfidence;

    private String address;

    private String province;

    private String city;

    private String district;

    private String cameraModel;

    private String lensInfo;

    private String aperture;

    private String shutterSpeed;

    private Integer iso;

    private String focalLength;

    private String md5;

    private Integer sortOrder;

    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    private String updateBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    private String remark;

    /** 0未删除 1已删除 2回收站 */
    private Integer deleted;
}
