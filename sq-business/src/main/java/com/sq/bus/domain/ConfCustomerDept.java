package com.sq.bus.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/**
 * 机构货主关联对象
 *
 * @author shengqian
 * @date 2024-07-01
 */
@Data
@TableName("conf_customer_dept")
public class ConfCustomerDept {
private static final long serialVersionUID=1L;

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 货主ID */
    private Long customerId;

    /** 机构ID */
    private Long deptId;

}
