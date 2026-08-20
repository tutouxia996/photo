package com.sq.bus.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * AI 出图预设风格 biz_photo_draw_preset
 */
@Data
@TableName("biz_photo_draw_preset")
public class BizPhotoDrawPreset implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "preset_id", type = IdType.AUTO)
    private Long presetId;

    /** 预设标识（出图请求中的 preset） */
    private String presetKey;

    private String label;

    /** 万相图生图风格描述 */
    private String panelPrompt;

    /** 如 960*1280 */
    private String panelSize;

    /** PhotoDrawLayout 枚举名 */
    private String layout;

    /** 上下双联时是否对原图轻调色：0否 1是 */
    private Integer gradePhoto;

    private Integer sortOrder;

    /** 0禁用 1启用 */
    private Integer enabled;

    /** builtin / manual / skill_import */
    private String source;

    /** 导入的 skill 原文 */
    private String skillRaw;

    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    private String updateBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    private String remark;
}
