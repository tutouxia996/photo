package com.sq.admin.album.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sq.bus.constants.AlbumDeleted;
import com.sq.bus.domain.BizAlbum;
import com.sq.bus.service.IBizAlbumService;
import com.sq.common.core.domain.AjaxResult;
import com.sq.common.core.domain.entity.SysRole;
import com.sq.common.core.domain.model.LoginUser;
import com.sq.common.utils.SecurityUtils;
import com.sq.common.utils.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 相册资源级权限：公开相册仅可查看；改删仅所有者或超级管理员。
 */
@Component
public class AlbumAccessHelper {

    private static final String ALL_PERMISSION = "*:*:*";
    private static final String SUPER_ADMIN_ROLE = "admin";

    @Autowired
    private IBizAlbumService albumService;

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

    /** 可管理（编辑/删除/上传）：所有者或超管 */
    public boolean canEdit(BizAlbum album) {
        if (album == null) {
            return false;
        }
        if (isSuperAdmin()) {
            return true;
        }
        String username = currentUsername();
        return StringUtils.isNotEmpty(username) && username.equals(album.getCreateBy());
    }

    /**
     * 可查看：自己的相册，或他人公开相册（共享=仅查看），或超管。
     */
    public boolean canView(BizAlbum album) {
        if (album == null) {
            return false;
        }
        if (canEdit(album)) {
            return true;
        }
        return album.getIsPublic() != null && album.getIsPublic() == 1;
    }

    public void fillCanEdit(Collection<BizAlbum> albums) {
        if (albums == null) {
            return;
        }
        for (BizAlbum album : albums) {
            if (album != null) {
                album.setCanEdit(canEdit(album));
            }
        }
    }

    public AjaxResult denyIfCannotView(BizAlbum album) {
        if (album == null) {
            return AjaxResult.error("相册不存在或已删除");
        }
        if (!canView(album)) {
            return AjaxResult.error("无权查看该相册");
        }
        return null;
    }

    public AjaxResult denyIfCannotEdit(BizAlbum album) {
        if (album == null) {
            return AjaxResult.error("相册不存在或已删除");
        }
        if (!canEdit(album)) {
            return AjaxResult.error("无权修改该相册，共享相册仅可查看");
        }
        return null;
    }

    public AjaxResult denyIfCannotEditAny(List<BizAlbum> albums) {
        if (albums == null || albums.isEmpty()) {
            return AjaxResult.error("相册不存在或已删除");
        }
        for (BizAlbum album : albums) {
            AjaxResult deny = denyIfCannotEdit(album);
            if (deny != null) {
                return deny;
            }
        }
        return null;
    }

    /**
     * 当前用户可查看的相册 ID（自己的 + 公开的；超管返回 null 表示不限制）
     */
    public List<Long> listViewableAlbumIds() {
        if (isSuperAdmin()) {
            return null;
        }
        String username = currentUsername();
        LambdaQueryWrapper<BizAlbum> wrapper = new LambdaQueryWrapper<BizAlbum>()
                .eq(BizAlbum::getDeleted, AlbumDeleted.NORMAL)
                .and(w -> w.eq(BizAlbum::getCreateBy, username).or().eq(BizAlbum::getIsPublic, 1))
                .select(BizAlbum::getAlbumId);
        List<BizAlbum> albums = albumService.list(wrapper);
        if (albums == null || albums.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> ids = new ArrayList<Long>();
        for (BizAlbum album : albums) {
            if (album != null && album.getAlbumId() != null) {
                ids.add(album.getAlbumId());
            }
        }
        return ids;
    }
}
