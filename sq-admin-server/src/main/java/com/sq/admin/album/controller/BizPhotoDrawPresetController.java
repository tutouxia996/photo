package com.sq.admin.album.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sq.bus.domain.BizPhotoDrawPreset;
import com.sq.bus.service.IBizPhotoDrawPresetService;
import com.sq.common.annotation.Log;
import com.sq.common.core.controller.BaseController;
import com.sq.common.core.domain.AjaxResult;
import com.sq.common.core.page.TableDataInfo;
import com.sq.common.enums.BusinessType;
import com.sq.common.utils.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * AI 出图预设风格管理
 */
@RestController
@RequestMapping("/album/drawPreset")
public class BizPhotoDrawPresetController extends BaseController {

    @Autowired
    private IBizPhotoDrawPresetService presetService;

    @PreAuthorize("@ss.hasPermi('album:drawPreset:list')")
    @GetMapping("/list")
    public TableDataInfo list(BizPhotoDrawPreset query) {
        startPage();
        LambdaQueryWrapper<BizPhotoDrawPreset> wrapper = new LambdaQueryWrapper<BizPhotoDrawPreset>()
                .like(StringUtils.isNotEmpty(query.getLabel()), BizPhotoDrawPreset::getLabel, query.getLabel())
                .like(StringUtils.isNotEmpty(query.getPresetKey()), BizPhotoDrawPreset::getPresetKey, query.getPresetKey())
                .eq(query.getEnabled() != null, BizPhotoDrawPreset::getEnabled, query.getEnabled())
                .eq(StringUtils.isNotEmpty(query.getSource()), BizPhotoDrawPreset::getSource, query.getSource())
                .orderByAsc(BizPhotoDrawPreset::getSortOrder)
                .orderByAsc(BizPhotoDrawPreset::getPresetId);
        List<BizPhotoDrawPreset> list = presetService.list(wrapper);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('album:drawPreset:query')")
    @GetMapping("/{presetId}")
    public AjaxResult getInfo(@PathVariable Long presetId) {
        return success(presetService.getById(presetId));
    }

    @PreAuthorize("@ss.hasPermi('album:drawPreset:add')")
    @Log(title = "AI预设风格", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody BizPhotoDrawPreset preset) {
        presetService.prepareCreate(preset);
        preset.setCreateBy(getUsername());
        preset.setCreateTime(new Date());
        presetService.save(preset);
        return success(preset);
    }

    @PreAuthorize("@ss.hasPermi('album:drawPreset:edit')")
    @Log(title = "AI预设风格", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody BizPhotoDrawPreset preset) {
        presetService.prepareUpdate(preset);
        preset.setUpdateBy(getUsername());
        preset.setUpdateTime(new Date());
        return toAjax(presetService.updateById(preset));
    }

    @PreAuthorize("@ss.hasPermi('album:drawPreset:remove')")
    @Log(title = "AI预设风格", businessType = BusinessType.DELETE)
    @DeleteMapping("/{presetIds}")
    public AjaxResult remove(@PathVariable Long[] presetIds) {
        return toAjax(presetService.removePresets(Arrays.asList(presetIds)));
    }

    /**
     * 解析 skill：支持上传 .md/.txt，或 body 中 text 字段粘贴。
     */
    @PreAuthorize("@ss.hasPermi('album:drawPreset:add')")
    @PostMapping("/parseSkill")
    public AjaxResult parseSkill(@RequestParam(value = "file", required = false) MultipartFile file,
                                 @RequestParam(value = "text", required = false) String text) throws Exception {
        String raw;
        String fileName = null;
        if (file != null && !file.isEmpty()) {
            fileName = file.getOriginalFilename();
            raw = new String(file.getBytes(), StandardCharsets.UTF_8);
        } else if (StringUtils.isNotEmpty(text)) {
            raw = text;
        } else {
            return error("请上传 skill 文件或粘贴文本");
        }
        Map<String, Object> parsed = presetService.parseSkill(raw, fileName);
        return success(parsed);
    }
}
