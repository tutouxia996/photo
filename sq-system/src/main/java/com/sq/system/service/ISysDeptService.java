package com.sq.system.service;

import com.sq.common.core.domain.TreeSelect;
import com.sq.common.core.domain.entity.SysDept;

import java.util.List;

/**
 * 机构管理 服务层
 * 
 * @author tzt
 */
public interface ISysDeptService {
    /**
     * 查询机构管理数据
     * 
     * @param dept 机构信息
     * @return 机构信息集合
     */
    public List<SysDept> selectDeptList(SysDept dept);

    /**
     * 查询机构树结构信息
     * 
     * @param dept 机构信息
     * @return 机构树信息集合
     */
    public List<TreeSelect> selectDeptTreeList(SysDept dept);

    /**
     * 构建前端所需要树结构
     * 
     * @param depts 机构列表
     * @return 树结构列表
     */
    public List<SysDept> buildDeptTree(List<SysDept> depts);

    /**
     * 构建前端所需要下拉树结构
     * 
     * @param depts 机构列表
     * @return 下拉树结构列表
     */
    public List<TreeSelect> buildDeptTreeSelect(List<SysDept> depts);

    /**
     * 根据角色ID查询机构树信息
     * 
     * @param roleId 角色ID
     * @return 选中机构列表
     */
    public List<Long> selectDeptListByRoleId(Long roleId);

    /**
     * 根据机构ID查询信息
     * 
     * @param deptId 机构ID
     * @return 机构信息
     */
    public SysDept selectDeptById(Long deptId);

    /**
     * 根据ID查询所有子机构（正常状态）
     * 
     * @param deptId 机构ID
     * @return 子机构数
     */
    public int selectNormalChildrenDeptById(Long deptId);

    /**
     * 是否存在机构子节点
     * 
     * @param deptId 机构ID
     * @return 结果
     */
    public boolean hasChildByDeptId(Long deptId);

    /**
     * 查询机构是否存在用户
     * 
     * @param deptId 机构ID
     * @return 结果 true 存在 false 不存在
     */
    public boolean checkDeptExistUser(Long deptId);

    /**
     * 校验机构名称是否唯一
     * 
     * @param dept 机构信息
     * @return 结果
     */
    public String checkDeptNameUnique(SysDept dept);

    /**
     * 校验机构是否有数据权限
     * 
     * @param deptId 机构id
     */
    public void checkDeptDataScope(Long deptId);

    /**
     * 新增保存机构信息
     * 
     * @param dept 机构信息
     * @return 结果
     */
    public int insertDept(SysDept dept);

    /**
     * 修改保存机构信息
     * 
     * @param dept 机构信息
     * @return 结果
     */
    public int updateDept(SysDept dept);

    /**
     * 删除机构管理信息
     * 
     * @param deptId 机构ID
     * @return 结果
     */
    public int deleteDeptById(Long deptId);
}
