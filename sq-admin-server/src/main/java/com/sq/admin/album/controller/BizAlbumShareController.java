package com.sq.admin.album.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sq.admin.album.support.AlbumAccessHelper;
import com.sq.bus.constants.AlbumDeleted;
import com.sq.bus.domain.BizAlbum;
import com.sq.bus.domain.BizAlbumShare;
import com.sq.bus.service.IBizAlbumService;
import com.sq.bus.service.IBizAlbumShareService;
import com.sq.common.annotation.Log;
import com.sq.common.core.controller.BaseController;
import com.sq.common.core.domain.AjaxResult;
import com.sq.common.core.page.TableDataInfo;
import com.sq.common.enums.BusinessType;
import com.sq.common.utils.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 相册分享管理（需登录）
 */
@RestController
@RequestMapping("/album/share")
public class BizAlbumShareController extends BaseController {

    @Autowired
    private IBizAlbumShareService shareService;

    @Autowired
    private IBizAlbumService albumService;

    @Autowired
    private AlbumAccessHelper albumAccessHelper;

    @PreAuthorize("@ss.hasAnyPermi('album:album:edit,album:album:query,album:photo:list')")
    @GetMapping("/list")
    public TableDataInfo list(@RequestParam Long albumId) {
        BizAlbum album = albumService.getById(albumId);
        AjaxResult deny = albumAccessHelper.denyIfCannotEdit(album);
        if (deny != null) {
            return getDataTable(java.util.Collections.<BizAlbumShare>emptyList());
        }
        startPage();
        List<BizAlbumShare> list = shareService.list(new LambdaQueryWrapper<BizAlbumShare>()
                .eq(BizAlbumShare::getAlbumId, albumId)
                .orderByDesc(BizAlbumShare::getShareId));
        for (BizAlbumShare s : list) {
            shareService.fillPhotoIdList(s);
            if (album != null) {
                s.setAlbumName(album.getAlbumName());
            }
        }
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasAnyPermi('album:album:edit,album:album:query,album:photo:list')")
    @GetMapping("/{shareId}")
    public AjaxResult getInfo(@PathVariable Long shareId) {
        BizAlbumShare share = shareService.getById(shareId);
        if (share == null) {
            return error("分享不存在");
        }
        BizAlbum album = albumService.getById(share.getAlbumId());
        AjaxResult deny = albumAccessHelper.denyIfCannotEdit(album);
        if (deny != null) {
            return deny;
        }
        shareService.fillPhotoIdList(share);
        if (album != null) {
            share.setAlbumName(album.getAlbumName());
        }
        return success(share);
    }

    @PreAuthorize("@ss.hasAnyPermi('album:album:edit,album:photo:list')")
    @Log(title = "相册分享", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody BizAlbumShare body) {
        if (body == null || body.getAlbumId() == null) {
            return error("相册ID不能为空");
        }
        BizAlbum album = albumService.getById(body.getAlbumId());
        if (album == null || album.getDeleted() == null || album.getDeleted() != AlbumDeleted.NORMAL) {
            return error("相册不存在");
        }
        AjaxResult deny = albumAccessHelper.denyIfCannotEdit(album);
        if (deny != null) {
            return deny;
        }

        BizAlbumShare share = new BizAlbumShare();
        share.setAlbumId(body.getAlbumId());
        share.setShareToken(shareService.generateToken());
        share.setShareTrack(body.getShareTrack() != null && body.getShareTrack() == 1 ? 1 : 0);
        share.setMediaScope(normalizeScope(body.getMediaScope()));
        share.setSelectMode(normalizeSelectMode(body.getSelectMode()));
        share.setPhotoIdList(body.getPhotoIdList());
        shareService.writePhotoIds(share);
        // include 且未选任何文件时无意义
        if ("include".equals(share.getSelectMode())
                && (share.getPhotoIdList() == null || share.getPhotoIdList().isEmpty())) {
            return error("请至少选择一张要分享的图片或视频");
        }
        share.setStatus(body.getStatus() == null ? 1 : body.getStatus());
        share.setExpireTime(body.getExpireTime());
        share.setRemark(body.getRemark());
        share.setCreateBy(albumAccessHelper.currentUsername());
        share.setCreateTime(new Date());
        share.setUpdateBy(share.getCreateBy());
        share.setUpdateTime(share.getCreateTime());
        shareService.save(share);
        shareService.fillPhotoIdList(share);
        share.setAlbumName(album.getAlbumName());

        Map<String, Object> data = new HashMap<String, Object>();
        data.put("share", share);
        data.put("sharePath", "/share/" + share.getShareToken());
        return success(data);
    }

    @PreAuthorize("@ss.hasAnyPermi('album:album:edit,album:photo:list')")
    @Log(title = "相册分享", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody BizAlbumShare body) {
        if (body == null || body.getShareId() == null) {
            return error("分享ID不能为空");
        }
        BizAlbumShare existing = shareService.getById(body.getShareId());
        if (existing == null) {
            return error("分享不存在");
        }
        BizAlbum album = albumService.getById(existing.getAlbumId());
        AjaxResult deny = albumAccessHelper.denyIfCannotEdit(album);
        if (deny != null) {
            return deny;
        }

        existing.setShareTrack(body.getShareTrack() != null && body.getShareTrack() == 1 ? 1 : 0);
        if (StringUtils.isNotEmpty(body.getMediaScope())) {
            existing.setMediaScope(normalizeScope(body.getMediaScope()));
        }
        if (StringUtils.isNotEmpty(body.getSelectMode())) {
            existing.setSelectMode(normalizeSelectMode(body.getSelectMode()));
        }
        if (body.getPhotoIdList() != null) {
            existing.setPhotoIdList(body.getPhotoIdList());
            shareService.writePhotoIds(existing);
        }
        if ("include".equals(normalizeSelectMode(existing.getSelectMode()))) {
            shareService.fillPhotoIdList(existing);
            if (existing.getPhotoIdList() == null || existing.getPhotoIdList().isEmpty()) {
                return error("请至少选择一张要分享的图片或视频");
            }
        }
        if (body.getStatus() != null) {
            existing.setStatus(body.getStatus());
        }
        existing.setExpireTime(body.getExpireTime());
        if (body.getRemark() != null) {
            existing.setRemark(body.getRemark());
        }
        existing.setUpdateBy(albumAccessHelper.currentUsername());
        existing.setUpdateTime(new Date());
        shareService.updateById(existing);
        shareService.fillPhotoIdList(existing);
        if (album != null) {
            existing.setAlbumName(album.getAlbumName());
        }
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("share", existing);
        data.put("sharePath", "/share/" + existing.getShareToken());
        return success(data);
    }

    @PreAuthorize("@ss.hasAnyPermi('album:album:edit,album:photo:list')")
    @Log(title = "相册分享", businessType = BusinessType.DELETE)
    @DeleteMapping("/{shareIds}")
    public AjaxResult remove(@PathVariable Long[] shareIds) {
        if (shareIds == null || shareIds.length == 0) {
            return error("参数错误");
        }
        for (Long id : shareIds) {
            BizAlbumShare share = shareService.getById(id);
            if (share == null) {
                continue;
            }
            BizAlbum album = albumService.getById(share.getAlbumId());
            AjaxResult deny = albumAccessHelper.denyIfCannotEdit(album);
            if (deny != null) {
                return deny;
            }
            shareService.removeById(id);
        }
        return success();
    }

    private static String normalizeScope(String scope) {
        if (StringUtils.isEmpty(scope)) {
            return "all";
        }
        String s = scope.trim().toLowerCase();
        if ("image".equals(s) || "1".equals(s) || "photo".equals(s)) {
            return "image";
        }
        if ("video".equals(s) || "2".equals(s)) {
            return "video";
        }
        return "all";
    }

    private static String normalizeSelectMode(String mode) {
        if (StringUtils.isEmpty(mode)) {
            return "all";
        }
        String m = mode.trim().toLowerCase();
        if ("include".equals(m) || "exclude".equals(m)) {
            return m;
        }
        return "all";
    }
}
