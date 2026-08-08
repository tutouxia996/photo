package com.sq.admin.album.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.sq.bus.domain.BizScanLog;
import com.sq.bus.domain.BizScanPath;
import com.sq.bus.service.IBizScanLogService;
import com.sq.bus.service.IBizScanPathService;
import com.sq.common.annotation.Log;
import com.sq.common.core.controller.BaseController;
import com.sq.common.core.domain.AjaxResult;
import com.sq.common.core.page.TableDataInfo;
import com.sq.common.enums.BusinessType;
import com.sq.common.utils.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Date;

/**
 * 磁盘扫描管理
 */
@RestController
@RequestMapping("/album/scan")
public class BizScanPathController extends BaseController {

    @Autowired
    private IBizScanPathService scanPathService;

    @Autowired
    private IBizScanLogService scanLogService;

    @PreAuthorize("@ss.hasPermi('album:scan:list')")
    @GetMapping("/list")
    public TableDataInfo list(BizScanPath query) {
        startPage();
        LambdaQueryWrapper<BizScanPath> wrapper = new LambdaQueryWrapper<BizScanPath>()
                .like(StringUtils.isNotEmpty(query.getPathName()), BizScanPath::getPathName, query.getPathName())
                .eq(query.getStatus() != null, BizScanPath::getStatus, query.getStatus())
                .eq(BizScanPath::getDeleted, 0)
                .orderByDesc(BizScanPath::getPathId);
        return getDataTable(scanPathService.list(wrapper));
    }

    @PreAuthorize("@ss.hasPermi('album:scan:query')")
    @GetMapping("/{pathId}")
    public AjaxResult getInfo(@PathVariable Long pathId) {
        BizScanPath path = scanPathService.getById(pathId);
        if (path == null || path.getDeleted() != null && path.getDeleted() == 1) {
            return error("扫描目录不存在或已删除");
        }
        return success(path);
    }

    @PreAuthorize("@ss.hasPermi('album:scan:add')")
    @Log(title = "扫描目录", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody BizScanPath path) {
        path.setCreateBy(getUsername());
        path.setCreateTime(new Date());
        if (path.getStatus() == null) {
            path.setStatus(1);
        }
        path.setDeleted(0);
        return toAjax(scanPathService.save(path));
    }

    @PreAuthorize("@ss.hasPermi('album:scan:edit')")
    @Log(title = "扫描目录", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody BizScanPath path) {
        path.setUpdateBy(getUsername());
        path.setUpdateTime(new Date());
        return toAjax(scanPathService.updateById(path));
    }

    @PreAuthorize("@ss.hasPermi('album:scan:remove')")
    @Log(title = "扫描目录", businessType = BusinessType.DELETE)
    @DeleteMapping("/{pathIds}")
    public AjaxResult remove(@PathVariable Long[] pathIds) {
        return toAjax(scanPathService.update(new LambdaUpdateWrapper<BizScanPath>()
                .in(BizScanPath::getPathId, Arrays.asList(pathIds))
                .eq(BizScanPath::getDeleted, 0)
                .set(BizScanPath::getDeleted, 1)
                .set(BizScanPath::getUpdateBy, getUsername())
                .set(BizScanPath::getUpdateTime, new Date())));
    }

    @PreAuthorize("@ss.hasPermi('album:scan:run')")
    @Log(title = "执行扫描", businessType = BusinessType.OTHER)
    @PostMapping("/run/{pathId}")
    public AjaxResult run(@PathVariable Long pathId,
                          @RequestParam(defaultValue = "false") boolean fullScan) {
        BizScanPath path = scanPathService.getById(pathId);
        if (path == null || path.getDeleted() != null && path.getDeleted() == 1) {
            return error("扫描目录不存在或已删除");
        }
        // 异步执行，立即返回日志以便前端轮询进度
        Long logId = scanPathService.startScanAsync(pathId, fullScan);
        return success(scanLogService.getById(logId));
    }

    /**
     * 查询单条扫描日志（含进行中进度），供前端轮询
     */
    @PreAuthorize("@ss.hasPermi('album:scan:query') or @ss.hasPermi('album:scan:list') or @ss.hasPermi('album:album:add') or @ss.hasPermi('album:album:list')")
    @GetMapping("/progress/{logId}")
    public AjaxResult progress(@PathVariable Long logId) {
        BizScanLog scanLog = scanLogService.getById(logId);
        if (scanLog == null) {
            return error("扫描记录不存在");
        }
        return success(scanLog);
    }

    @PreAuthorize("@ss.hasPermi('album:scan:list')")
    @GetMapping("/log/list")
    public TableDataInfo logList(BizScanLog query) {
        startPage();
        LambdaQueryWrapper<BizScanLog> wrapper = new LambdaQueryWrapper<BizScanLog>()
                .eq(query.getPathId() != null, BizScanLog::getPathId, query.getPathId())
                .orderByDesc(BizScanLog::getLogId);
        return getDataTable(scanLogService.list(wrapper));
    }
}
