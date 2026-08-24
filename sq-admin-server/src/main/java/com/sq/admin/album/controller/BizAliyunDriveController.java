package com.sq.admin.album.controller;

import com.sq.bus.domain.vo.AliyunDriveSettingVo;
import com.sq.bus.service.IAliyunDriveSettingService;
import com.sq.bus.service.cloud.AliyunDriveSyncService;
import com.sq.common.annotation.Log;
import com.sq.common.core.controller.BaseController;
import com.sq.common.core.domain.AjaxResult;
import com.sq.common.enums.BusinessType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 阿里云盘相册同步配置（页面保存，不再依赖 yml 密钥）。
 */
@RestController
@RequestMapping("/album/aliyun")
public class BizAliyunDriveController extends BaseController {

    @Autowired
    private IAliyunDriveSettingService settingService;

    @Autowired
    private AliyunDriveSyncService syncService;

    @PreAuthorize("@ss.hasPermi('album:aliyun:query')")
    @GetMapping
    public AjaxResult get() {
        AliyunDriveSettingVo vo = settingService.getForPage();
        vo.setRunning(syncService.isRunning());
        return success(vo);
    }

    @PreAuthorize("@ss.hasPermi('album:aliyun:edit')")
    @Log(title = "云盘同步配置", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult save(@RequestBody AliyunDriveSettingVo vo) {
        settingService.saveFromPage(vo, getUsername());
        return success();
    }

    @PreAuthorize("@ss.hasPermi('album:aliyun:run')")
    @Log(title = "云盘立即同步", businessType = BusinessType.OTHER)
    @PostMapping("/run")
    public AjaxResult run() {
        return success(syncService.startSyncAsync());
    }

    @PreAuthorize("@ss.hasPermi('album:aliyun:run')")
    @Log(title = "云盘暂停下载", businessType = BusinessType.OTHER)
    @PostMapping("/pause")
    public AjaxResult pause() {
        return success(syncService.pauseSync());
    }

    @PreAuthorize("@ss.hasPermi('album:aliyun:run')")
    @Log(title = "云盘继续下载", businessType = BusinessType.OTHER)
    @PostMapping("/resume")
    public AjaxResult resume() {
        return success(syncService.resumeSync());
    }

    @PreAuthorize("@ss.hasPermi('album:aliyun:query') or @ss.hasPermi('album:aliyun:run')")
    @GetMapping("/progress")
    public AjaxResult progress() {
        return success(syncService.getProgress());
    }

    @PreAuthorize("@ss.hasPermi('album:aliyun:query')")
    @GetMapping("/albums")
    public AjaxResult albums(@RequestParam(required = false) String refreshToken) {
        return success(settingService.listRemoteAlbums(refreshToken));
    }
}
