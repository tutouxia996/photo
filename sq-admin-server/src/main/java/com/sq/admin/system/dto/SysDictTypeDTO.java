package com.sq.admin.system.dto;

import com.sq.common.core.domain.entity.SysDictData;
import io.swagger.annotations.ApiModel;
import lombok.Data;

import java.util.List;

/**
 * FileName: SysDictTypeDto
 * @Author: hngGeng
 * Date: 2024/7/23 11:16
 * Description:
 */
@Data
@ApiModel("字典数据DTO")
public class SysDictTypeDTO {
    private static final long serialVersionUID = 1L;

    private Long dictId;

    private String dictName;

    private String dictType;

    private String status;

    private String remark;

    private List<SysDictData> dictDataList;

}
