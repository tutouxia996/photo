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
            shareService.fillAlbumIdList(s);
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
        shareService.fillAlbumIdList(share);
        if (album != null) {
            share.setAlbumName(album.getAlbumName());
        }
        return success(share);
    }

    @PreAuthorize("@ss.hasAnyPermi('album:album:edit,album:photo:list')")
    @Log(title = "相册分享", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody BizAlbumShare body) {
        if (body == null) {
            return error("参数不能为空");
        }
        // 主相册 albumId 在前，再合并 albumIdList（去重保序）
        java.util.LinkedHashSet<Long> idSet = new java.util.LinkedHashSet<Long>();
        if (body.getAlbumId() != null) {
            idSet.add(body.getAlbumId());
        }
        if (body.getAlbumIdList() != null) {
            for (Long id : body.getAlbumIdList()) {
                if (id != null) {
                    idSet.add(id);
                }
            }
        }
        if (idSet.isEmpty()) {
            return error("相册ID不能为空");
        }
        List<Long> albumIds = new java.util.ArrayList<Long>(idSet);
        List<BizAlbum> albums = albumService.listByIds(albumIds);
        if (albums == null || albums.isEmpty()) {
            return error("相册不存在");
        }
        Map<Long, BizAlbum> albumMap = new HashMap<Long, BizAlbum>();
        for (BizAlbum a : albums) {
            if (a != null && a.getAlbumId() != null) {
                albumMap.put(a.getAlbumId(), a);
            }
        }
        List<BizAlbum> ordered = new java.util.ArrayList<BizAlbum>();
        for (Long id : albumIds) {
            BizAlbum a = albumMap.get(id);
            if (a == null || a.getDeleted() == null || a.getDeleted() != AlbumDeleted.NORMAL) {
                return error("相册不存在或已删除：" + id);
            }
            ordered.add(a);
        }
        AjaxResult deny = albumAccessHelper.denyIfCannotEditAny(ordered);
        if (deny != null) {
            return deny;
        }

        BizAlbumShare share = new BizAlbumShare();
        share.setAlbumIdList(albumIds);
        shareService.writeAlbumIds(share);
        share.setShareToken(shareService.generateToken());
        share.setShareTrack(body.getShareTrack() != null && body.getShareTrack() == 1 ? 1 : 0);
        share.setMediaScope(normalizeScope(body.getMediaScope()));
        // 多相册暂不支持按文件勾选，强制全部
        if (albumIds.size() > 1) {
            share.setSelectMode("all");
            share.setPhotoIdList(java.util.Collections.<Long>emptyList());
        } else {
            share.setSelectMode(normalizeSelectMode(body.getSelectMode()));
            share.setPhotoIdList(body.getPhotoIdList());
        }
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
        shareService.fillAlbumIdList(share);
        share.setAlbumName(buildAlbumNames(ordered));

        Map<String, Object> data = new HashMap<String, Object>();
        data.put("share", share);
        data.put("sharePath", "/share/" + share.getShareToken());
        return success(data);
    }

    private static String buildAlbumNames(List<BizAlbum> albums) {
        if (albums == null || albums.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(albums.size(), 3);
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                sb.append("、");
            }
            String name = albums.get(i).getAlbumName();
            sb.append(name != null ? name : "未命名");
        }
        if (albums.size() > 3) {
            sb.append(" 等").append(albums.size()).append("个相册");
        }
        return sb.toString();
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
