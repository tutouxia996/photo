package com.sq.bus.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 阿里云盘相册同步配置（单行，id 固定为 1）
 */
@Data
@TableName("biz_aliyun_drive_setting")
public class BizAliyunDriveSetting implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final long SINGLE_ID = 1L;

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    private Integer enabled;
    private String refreshToken;
    private String remoteAlbumName;
    private String remoteAlbumId;
    private String localPath;
    private Long scanPathId;
    private Integer triggerScan;
    private Integer fullScan;
    private String tokenFile;
    private Integer connectTimeoutMs;
    private Integer readTimeoutMs;
    private Integer downloadTimeoutMs;
    private Integer mediaOnly;
    private Integer downloadConcurrency;
    private String downloadReferer;
    private Integer chunkConcurrency;
    private Long multipartMinBytes;
    private String updateBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    private String remark;
}
