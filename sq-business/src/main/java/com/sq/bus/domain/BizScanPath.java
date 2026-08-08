package com.sq.bus.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 扫描目录配置 biz_scan_path
 */
@Data
@TableName("biz_scan_path")
public class BizScanPath implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "path_id", type = IdType.AUTO)
    private Long pathId;

    private String pathName;

    private String localPath;

    private Long defaultAlbumId;

    private String scanCron;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastScanTime;

    /** 状态：0禁用 1启用 */
    private Integer status;

    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    private String updateBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    private String remark;

    /** 0未删除 1已删除 */
    private Integer deleted;
}
