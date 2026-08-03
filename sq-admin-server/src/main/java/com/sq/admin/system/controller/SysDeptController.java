package com.sq.admin.system.controller;

import com.sq.bus.service.IConfCustomerDeptService;
import com.sq.common.annotation.Log;
import com.sq.common.constant.UserConstants;
import com.sq.common.core.controller.BaseController;
import com.sq.common.core.domain.AjaxResult;
import com.sq.common.core.domain.R;
import com.sq.common.core.domain.entity.SysDept;
import com.sq.common.enums.BusinessType;
import com.sq.common.utils.StringUtils;
import com.sq.system.service.ISysDeptService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.query.Param;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 机构信息
 * 
 * @author tzt
 */
@RestController
@RequestMapping("/system/dept")
public class SysDeptController extends BaseController {
    @Autowired
    private ISysDeptService deptService;
//    @Autowired
//    private IConfCustomerDeptService confCustomerDeptService;

    /**
     * 获取机构列表
     */
    @PreAuthorize("@ss.hasPermi('system:dept:list')")
    @GetMapping("/list")
    public AjaxResult list(SysDept dept) {
        List<SysDept> depts = deptService.selectDeptList(dept);
        return success(depts);
    }

    /**
     * 查询机构列表（排除节点）
     */
    @PreAuthorize("@ss.hasPermi('system:dept:list')")
    @GetMapping("/list/exclude/{deptId}")
    public AjaxResult excludeChild(@PathVariable(value = "deptId", required = false) Long deptId) {
        List<SysDept> depts = deptService.selectDeptList(new SysDept());
        depts.removeIf(d -> d.getId().intValue() == deptId || ArrayUtils.contains(StringUtils.split(d.getAncestors(), ","), deptId + ""));
        return success(depts);
    }

    /**
     * 根据机构编号获取详细信息
     */
    @PreAuthorize("@ss.hasAnyPermi('system:dept:query,system:dept:view')")
    @GetMapping(value = "/{deptId}")
    public AjaxResult getInfo(@PathVariable Long deptId) {
        deptService.checkDeptDataScope(deptId);
        return success(deptService.selectDeptById(deptId));
    }

    /**
     * 新增机构
     */
    @PreAuthorize("@ss.hasPermi('system:dept:add')")
    @Log(title = "机构管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody SysDept dept) {
        if (UserConstants.NOT_UNIQUE.equals(deptService.checkDeptNameUnique(dept))) {
            return error("新增机构'" + dept.getDeptName() + "'失败，机构名称已存在");
        }
        dept.setCreateBy(getUsername());
        return toAjax(deptService.insertDept(dept));
    }

    /**
     * 修改机构
     */
    @PreAuthorize("@ss.hasPermi('system:dept:edit')")
    @Log(title = "机构管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody SysDept dept) {
        Long deptId = dept.getId();
        deptService.checkDeptDataScope(deptId);
        if (UserConstants.NOT_UNIQUE.equals(deptService.checkDeptNameUnique(dept))) {
            return error("修改机构'" + dept.getDeptName() + "'失败，机构名称已存在");
        }
        else if (dept.getParentId().equals(deptId)) {
            return error("修改机构'" + dept.getDeptName() + "'失败，上级机构不能是自己");
        }
        else if (StringUtils.equals(UserConstants.DEPT_DISABLE, dept.getStatus()) && deptService.selectNormalChildrenDeptById(deptId) > 0) {
            return error("该机构包含未停用的子机构！");
        }
        dept.setUpdateBy(getUsername());
        return toAjax(deptService.updateDept(dept));
    }

    /**
     * 删除机构
     */
    @PreAuthorize("@ss.hasPermi('system:dept:remove')")
    @Log(title = "机构管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{deptId}")
    public AjaxResult remove(@PathVariable Long deptId) {
        if (deptService.hasChildByDeptId(deptId)) {
            return warn("存在下级机构,不允许删除");
        }
        if (deptService.checkDeptExistUser(deptId)) {
            return warn("机构存在用户,不允许删除");
        }
        deptService.checkDeptDataScope(deptId);
        return toAjax(deptService.deleteDeptById(deptId));
    }

//    @PostMapping("/relateEnt")
//    public R relateEnt(@RequestBody DeptRelateEntVo vo) {
//        confCustomerDeptService.lambdaUpdate().eq(ConfCustomerDept::getDeptId, vo.getId()).remove();
//        List<ConfCustomerDept> list = new ArrayList<>();
//        if (CollectionUtils.isNotEmpty(vo.getClientIds())) {
//            for (Long clientId : vo.getClientIds()) {
//                ConfCustomerDept deptEnt = new ConfCustomerDept();
//                deptEnt.setDeptId(vo.getId());
//                deptEnt.setCustomerId(clientId);
//                list.add(deptEnt);
//            }
//        }
//        if (CollectionUtils.isNotEmpty(list)) {
//            confCustomerDeptService.saveBatch(list);
//        }
//        return R.ok();
//    }
//
//
//
//    @GetMapping("/deptEnt")
//    public R<DeptRelateEntVo> deptEnt(@Param("deptId") Long deptId) {
//        List<ConfCustomerDept> list = confCustomerDeptService.lambdaQuery().eq(ConfCustomerDept::getDeptId, deptId).list();
//        DeptRelateEntVo vo = new DeptRelateEntVo();
//        vo.setDeptId(deptId);
//        vo.setClientIds(list.stream().map(ConfCustomerDept::getCustomerId).collect(Collectors.toList()));
//        return R.ok(vo);
//    }
}
