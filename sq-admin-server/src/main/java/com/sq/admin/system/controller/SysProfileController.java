package com.sq.admin.system.controller;

import com.sq.common.annotation.Log;
import com.sq.common.config.ProjectConfig;
import com.sq.common.constant.UserConstants;
import com.sq.common.core.controller.BaseController;
import com.sq.common.core.domain.AjaxResult;
import com.sq.common.core.domain.entity.SysUser;
import com.sq.common.core.domain.model.LoginUser;
import com.sq.common.enums.BusinessType;
import com.sq.common.utils.PasswordValidator;
import com.sq.common.utils.SecurityUtils;
import com.sq.common.utils.StringUtils;
import com.sq.common.utils.file.FileUploadUtils;
import com.sq.common.utils.file.MimeTypeUtils;
import com.sq.framework.web.service.TokenService;
import com.sq.manager.service.Sm2PasswordService;
import com.sq.system.service.ISysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 个人信息 业务处理
 * 
 * @author tzt
 */
@RestController
@RequestMapping("/system/user/profile")
public class SysProfileController extends BaseController {
    @Autowired
    private ISysUserService userService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private Sm2PasswordService sm2PasswordService;

    /**
     * 个人信息
     */
    @GetMapping
    public AjaxResult profile() {
        LoginUser loginUser = (LoginUser) getLoginUser();
        SysUser user = loginUser.getUser();
        AjaxResult ajax = AjaxResult.success(user);
        ajax.put("roleGroup", userService.selectUserRoleGroup(loginUser.getUsername()));
        ajax.put("postGroup", userService.selectUserPostGroup(loginUser.getUsername()));
        return ajax;
    }

    /**
     * 修改用户
     */
    @Log(title = "个人信息", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult updateProfile(@RequestBody SysUser user) {
        LoginUser loginUser = (LoginUser) getLoginUser();
        SysUser sysUser = loginUser.getUser();
        user.setUserName(sysUser.getUserName());
        if (StringUtils.isNotEmpty(user.getPhonenumber())
                && UserConstants.NOT_UNIQUE.equals(userService.checkPhoneUnique(user))) {
            return error("修改用户'" + user.getUserName() + "'失败，手机号码已存在");
        }
        if (StringUtils.isNotEmpty(user.getEmail())
                && UserConstants.NOT_UNIQUE.equals(userService.checkEmailUnique(user))) {
            return error("修改用户'" + user.getUserName() + "'失败，邮箱账号已存在");
        }
        user.setId(sysUser.getId());
        user.setPassword(null);
        user.setAvatar(null);
        user.setDeptId(null);
        if (userService.updateUserProfile(user) > 0) {
            // 更新缓存用户信息
            sysUser.setNickName(user.getNickName());
            sysUser.setPhonenumber(user.getPhonenumber());
            sysUser.setEmail(user.getEmail());
            sysUser.setSex(user.getSex());
            tokenService.setLoginUser(loginUser);
            return success();
        }
        return error("修改个人信息异常，请联系管理员");
    }

    /**
     * 重置密码
     */
    @Log(title = "个人信息", businessType = BusinessType.UPDATE)
    @PutMapping("/updatePwd")
    public AjaxResult updatePwd(@RequestBody Map<String, String> data) {
        String oldPassword = data.get("oldPassword");
        String newPassword = data.get("newPassword");
        LoginUser loginUser = (LoginUser) getLoginUser();
        String userName = loginUser.getUsername();
        String password = loginUser.getPassword();
        String plainOldPassword = sm2PasswordService.decryptPassword(oldPassword);
        String plainNewPassword = sm2PasswordService.decryptPassword(newPassword);
        if (!SecurityUtils.matchesPassword(plainOldPassword, password)) {
            return error("修改密码失败，旧密码错误");
        }
        if (SecurityUtils.matchesPassword(plainNewPassword, password)) {
            return error("新密码不能与旧密码相同");
        }
        PasswordValidator.validate(plainNewPassword);
        String encodedPassword = SecurityUtils.encryptPassword(plainNewPassword);
        if (userService.resetUserPwd(userName, encodedPassword) > 0) {
            // 更新缓存用户密码
            loginUser.getUser().setPassword(encodedPassword);
            tokenService.setLoginUser(loginUser);
            return success();
        }
        return error("修改密码异常，请联系管理员");
    }

    /**
     * 头像上传
     */
    @Log(title = "用户头像", businessType = BusinessType.UPDATE)
    @PostMapping("/avatar")
    public AjaxResult avatar(@RequestParam("avatarfile") MultipartFile file) throws Exception {
        if (!file.isEmpty()) {
            LoginUser loginUser = (LoginUser) getLoginUser();
            String avatar = FileUploadUtils.upload(ProjectConfig.getAvatarPath(), file, MimeTypeUtils.IMAGE_EXTENSION);
            if (userService.updateUserAvatar(loginUser.getUsername(), avatar)) {
                AjaxResult ajax = AjaxResult.success();
                ajax.put("imgUrl", avatar);
                // 更新缓存用户头像
                loginUser.getUser().setAvatar(avatar);
                tokenService.setLoginUser(loginUser);
                return ajax;
            }
        }
        return error("上传图片异常，请联系管理员");
    }
}
