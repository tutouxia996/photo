package com.sq.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sq.common.core.domain.entity.SysDept;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 机构管理 数据层
 * 
 * @author tzt
 */
public interface SysDeptMapper extends BaseMapper<SysDept> {
    /**
     * 查询机构管理数据
     * 
     * @param dept 机构信息
     * @return 机构信息集合
     */
    public List<SysDept> selectDeptList(SysDept dept);

    /**
     * 根据角色ID查询机构树信息
     * 
     * @param roleId 角色ID
     * @param deptCheckStrictly 机构树选择项是否关联显示
     * @return 选中机构列表
     */
    public List<Long> selectDeptListByRoleId(@Param("roleId") Long roleId, @Param("deptCheckStrictly") boolean deptCheckStrictly);

    /**
     * 根据机构ID查询信息
     * 
     * @param deptId 机构ID
     * @return 机构信息
     */
    public SysDept selectDeptById(Long deptId);

    /**
     * 根据ID查询所有子机构
     * 
     * @param deptId 机构ID
     * @return 机构列表
     */
    public List<SysDept> selectChildrenDeptById(Long deptId);

    /**
     * 根据ID查询所有子机构（正常状态）
     * 
     * @param deptId 机构ID
     * @return 子机构数
     */
    public int selectNormalChildrenDeptById(Long deptId);

    /**
     * 是否存在子节点
     * 
     * @param deptId 机构ID
     * @return 结果
     */
    public int hasChildByDeptId(Long deptId);

    /**
     * 查询机构是否存在用户
     * 
     * @param deptId 机构ID
     * @return 结果
     */
    public int checkDeptExistUser(Long deptId);

    /**
     * 校验机构名称是否唯一
     * 
     * @param deptName 机构名称
     * @param parentId 父机构ID
     * @return 结果
     */
    public SysDept checkDeptNameUnique(@Param("deptName") String deptName, @Param("parentId") Long parentId);

    /**
     * 新增机构信息
     * 
     * @param dept 机构信息
     * @return 结果
     */
    public int insertDept(SysDept dept);

    /**
     * 修改机构信息
     * 
     * @param dept 机构信息
     * @return 结果
     */
    public int updateDept(SysDept dept);

    /**
     * 修改所在机构正常状态
     * 
     * @param deptIds 机构ID组
     */
    public void updateDeptStatusNormal(Long[] deptIds);

    /**
     * 修改子元素关系
     * 
     * @param depts 子元素
     * @return 结果
     */
    public int updateDeptChildren(@Param("depts") List<SysDept> depts);
}
