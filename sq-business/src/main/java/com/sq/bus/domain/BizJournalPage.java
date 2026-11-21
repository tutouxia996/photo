package com.sq.bus.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 日记页 biz_journal_page（懒创建）
 */
@Data
@TableName("biz_journal_page")
public class BizJournalPage implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "page_id", type = IdType.AUTO)
    private Long pageId;

    private Long bookId;

    /** 页码，从 1 起 */
    private Integer pageNo;

    /** 图层 JSON：stroke / textbox / image … */
    private String contentJson;

    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    private String updateBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    private String remark;
}
