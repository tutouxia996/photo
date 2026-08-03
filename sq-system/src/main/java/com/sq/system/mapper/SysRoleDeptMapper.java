package com.sq.system.mapper;

import com.sq.system.domain.SysRoleDept;

import java.util.List;

/**
 * 角色与机构关联表 数据层
 * 
 * @author tzt
 */
public interface SysRoleDeptMapper {
    /**
     * 通过角色ID删除角色和机构关联
     * 
     * @param roleId 角色ID
     * @return 结果
     */
    public int deleteRoleDeptByRoleId(Long roleId);

    /**
     * 批量删除角色机构关联信息
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    public int deleteRoleDept(Long[] ids);

    /**
     * 查询机构使用数量
     * 
     * @param deptId 机构ID
     * @return 结果
     */
    public int selectCountRoleDeptByDeptId(Long deptId);

    /**
     * 批量新增角色机构信息
     * 
     * @param roleDeptList 角色机构列表
     * @return 结果
     */
    public int batchRoleDept(List<SysRoleDept> roleDeptList);
}
