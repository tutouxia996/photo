package com.sq.admin.journal.support;

import com.sq.bus.domain.BizJournalBook;
import com.sq.common.core.domain.AjaxResult;
import com.sq.common.core.domain.entity.SysRole;
import com.sq.common.core.domain.model.LoginUser;
import com.sq.common.utils.SecurityUtils;
import com.sq.common.utils.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

/**
 * 日记本资源级权限：本人可读写；超管可查全部。
 */
@Component
public class JournalAccessHelper {

    private static final String ALL_PERMISSION = "*:*:*";
    private static final String SUPER_ADMIN_ROLE = "admin";

    public boolean isSuperAdmin() {
        LoginUser loginUser = (LoginUser) SecurityUtils.getLoginUser();
        if (loginUser == null || loginUser.getUser() == null) {
            return false;
        }
        if (loginUser.getUser().isAdmin()) {
            return true;
        }
        if (!CollectionUtils.isEmpty(loginUser.getPermissions())
                && loginUser.getPermissions().contains(ALL_PERMISSION)) {
            return true;
        }
        if (!CollectionUtils.isEmpty(loginUser.getUser().getRoles())) {
            for (SysRole role : loginUser.getUser().getRoles()) {
                if (role != null && SUPER_ADMIN_ROLE.equals(role.getRoleKey())) {
                    return true;
                }
            }
        }
        return false;
    }

    public String currentUsername() {
        try {
            return SecurityUtils.getUsername();
        } catch (Exception e) {
            return null;
        }
    }

    public boolean canEdit(BizJournalBook book) {
        if (book == null) {
            return false;
        }
        if (isSuperAdmin()) {
            return true;
        }
        String username = currentUsername();
        return StringUtils.isNotEmpty(username) && username.equals(book.getCreateBy());
    }

    public AjaxResult denyIfCannotEdit(BizJournalBook book) {
        if (book == null) {
            return AjaxResult.error("日记本不存在");
        }
        if (!canEdit(book)) {
            return AjaxResult.error("无权操作该日记本");
        }
        return null;
    }
}
