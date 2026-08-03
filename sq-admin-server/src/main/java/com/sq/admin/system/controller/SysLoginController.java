package com.sq.admin.system.controller;

import com.sq.common.constant.Constants;
import com.sq.common.core.domain.AjaxResult;
import com.sq.common.core.domain.entity.SysMenu;
import com.sq.common.core.domain.entity.SysUser;
import com.sq.common.core.domain.model.LoginBody;
import com.sq.common.core.domain.model.LoginUser;
import com.sq.common.utils.SecurityUtils;
import com.sq.common.utils.StringUtils;
import com.sq.manager.service.SysLoginService;
import com.sq.manager.service.SysPermissionService;
import com.sq.system.service.ISysConfigService;
import com.sq.system.service.ISysMenuService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * 登录验证
 *
 * @author tzt
 */
@RestController
public class SysLoginController {
    @Autowired
    private SysLoginService loginService;

    @Autowired
    private ISysMenuService menuService;

    @Autowired
    private SysPermissionService permissionService;

    @Autowired
    private ISysConfigService configService;

    /**
     * 登录方法
     *
     * @param loginBody 登录信息
     * @return 结果
     */
    @PostMapping("/login")
    public AjaxResult login(@RequestBody LoginBody loginBody) {
        AjaxResult ajax = AjaxResult.success();
        // 生成令牌
        String token = loginService.login(loginBody.getUsername(), loginBody.getPassword(), loginBody.getCode(),
                loginBody.getUuid());
        ajax.put(Constants.TOKEN, token);
        LoginUser loginUser = (LoginUser) SecurityUtils.getLoginUser();
        if (loginUser != null && !loginUser.getUser().isAdmin()
                && loginUser.getUser().getPwdUpdateTime() == null) {
            ajax.put("forceChangePwd", true);
            ajax.put("forceChangePwdReason", "first_login");
        }
        return ajax;
    }

    /**
     * 获取用户信息
     *
     * @return 用户信息
     */
    @GetMapping("getInfo")
    public AjaxResult getInfo() {
        SysUser user = ((LoginUser) SecurityUtils.getLoginUser()).getUser();
        // 角色集合
        Set<String> roles = permissionService.getRolePermission(user);
        // 权限集合
        Set<String> permissions = permissionService.getMenuPermission(user);
        AjaxResult ajax = AjaxResult.success();
        ajax.put("user", user);
        ajax.put("roles", roles);
        ajax.put("permissions", permissions);
        if (!user.isAdmin()) {
            if (user.getPwdUpdateTime() == null) {
                ajax.put("forceChangePwd", true);
                ajax.put("forceChangePwdReason", "first_login");
            } else {
                String expireDaysStr = configService.selectConfigByKey("sys.password.expireDays");
                if (StringUtils.isNotEmpty(expireDaysStr)) {
                    try {
                        int expireDays = Integer.parseInt(expireDaysStr);
                        if (expireDays > 0) {
                            long diff = System.currentTimeMillis() - user.getPwdUpdateTime().getTime();
                            long days = diff / (24L * 60 * 60 * 1000);
                            if (days >= expireDays) {
                                ajax.put("forceChangePwd", true);
                                ajax.put("forceChangePwdReason", "expired");
                            }
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return ajax;
    }

    /**
     * 获取路由信息
     *
     * @return 路由信息
     */
    @GetMapping("getRouters")
    public AjaxResult getRouters() {
        Long userId = SecurityUtils.getUserId();
        List<SysMenu> menus = menuService.selectMenuTreeByUserId(userId);
        return AjaxResult.success(menuService.buildMenus(menus));
    }
}
