package com.sq.bus.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 扫描记录 biz_scan_log
 */
@Data
@TableName("biz_scan_log")
public class BizScanLog implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "log_id", type = IdType.AUTO)
    private Long logId;

    private Long pathId;

    /** 扫描类型：1增量 2全量 */
    private Integer scanType;

    private Integer totalCount;

    private Integer newCount;

    private Integer skipCount;

    private Integer failCount;

    /** 状态：0进行中 1成功 2失败 */
    private Integer status;

    private String message;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date endTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
}
