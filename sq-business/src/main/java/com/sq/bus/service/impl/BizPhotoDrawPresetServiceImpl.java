package com.sq.bus.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sq.bus.domain.BizPhotoDrawPreset;
import com.sq.bus.mapper.BizPhotoDrawPresetMapper;
import com.sq.bus.service.IBizPhotoDrawPresetService;
import com.sq.bus.service.draw.DrawSkillImportParser;
import com.sq.bus.service.draw.PhotoDrawLayout;
import com.sq.common.exception.ServiceException;
import com.sq.common.utils.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BizPhotoDrawPresetServiceImpl extends ServiceImpl<BizPhotoDrawPresetMapper, BizPhotoDrawPreset>
        implements IBizPhotoDrawPresetService {

    @Override
    public List<BizPhotoDrawPreset> listEnabled() {
        return list(new LambdaQueryWrapper<BizPhotoDrawPreset>()
                .eq(BizPhotoDrawPreset::getEnabled, 1)
                .orderByAsc(BizPhotoDrawPreset::getSortOrder)
                .orderByAsc(BizPhotoDrawPreset::getPresetId));
    }

    @Override
    public BizPhotoDrawPreset getEnabledByKey(String presetKey) {
        if (StringUtils.isEmpty(presetKey)) {
            return null;
        }
        String key = presetKey.trim().toLowerCase(Locale.ROOT);
        return getOne(new LambdaQueryWrapper<BizPhotoDrawPreset>()
                .eq(BizPhotoDrawPreset::getPresetKey, key)
                .eq(BizPhotoDrawPreset::getEnabled, 1)
                .last("LIMIT 1"), false);
    }

    @Override
    public Map<String, Object> parseSkill(String raw, String fileName) {
        try {
            Map<String, Object> parsed = DrawSkillImportParser.parse(raw, fileName);
            Object label = parsed.get("label");
            parsed.put("presetKey", DrawSkillImportParser.suggestKey(label == null ? null : String.valueOf(label)));
            parsed.put("panelSize", "960*1280");
            parsed.put("layout", PhotoDrawLayout.FULL_CANVAS.name());
            return parsed;
        } catch (IllegalArgumentException e) {
            throw new ServiceException(e.getMessage());
        }
    }

    @Override
    public void prepareCreate(BizPhotoDrawPreset preset) {
        if (preset == null) {
            throw new ServiceException("预设不能为空");
        }
        normalize(preset);
        if (StringUtils.isEmpty(preset.getLabel())) {
            throw new ServiceException("请填写风格名称");
        }
        if (StringUtils.isEmpty(preset.getPanelPrompt())) {
            throw new ServiceException("请填写风格描述");
        }
        if (StringUtils.isEmpty(preset.getPresetKey())) {
            preset.setPresetKey(DrawSkillImportParser.suggestKey(preset.getLabel()));
        }
        preset.setPresetKey(preset.getPresetKey().trim().toLowerCase(Locale.ROOT));
        validateKeyFormat(preset.getPresetKey());
        long exists = count(new LambdaQueryWrapper<BizPhotoDrawPreset>()
                .eq(BizPhotoDrawPreset::getPresetKey, preset.getPresetKey()));
        if (exists > 0) {
            throw new ServiceException("预设标识已存在：" + preset.getPresetKey());
        }
        if (preset.getEnabled() == null) {
            preset.setEnabled(1);
        }
        if (preset.getSortOrder() == null) {
            preset.setSortOrder(100);
        }
        if (StringUtils.isEmpty(preset.getSource())) {
            preset.setSource("manual");
        }
        if ("builtin".equals(preset.getSource())) {
            throw new ServiceException("不能手动创建内置预设");
        }
        if (preset.getGradePhoto() == null) {
            preset.setGradePhoto(1);
        }
    }

    @Override
    public void prepareUpdate(BizPhotoDrawPreset preset) {
        if (preset == null || preset.getPresetId() == null) {
            throw new ServiceException("预设ID不能为空");
        }
        BizPhotoDrawPreset db = getById(preset.getPresetId());
        if (db == null) {
            throw new ServiceException("预设不存在");
        }
        normalize(preset);
        // 内置标识不可改；防止前端篡改 source
        if ("builtin".equals(db.getSource())) {
            preset.setPresetKey(db.getPresetKey());
            preset.setSource("builtin");
        }
        if (StringUtils.isNotEmpty(preset.getPresetKey())) {
            preset.setPresetKey(preset.getPresetKey().trim().toLowerCase(Locale.ROOT));
            validateKeyFormat(preset.getPresetKey());
            long exists = count(new LambdaQueryWrapper<BizPhotoDrawPreset>()
                    .eq(BizPhotoDrawPreset::getPresetKey, preset.getPresetKey())
                    .ne(BizPhotoDrawPreset::getPresetId, preset.getPresetId()));
            if (exists > 0) {
                throw new ServiceException("预设标识已存在：" + preset.getPresetKey());
            }
        }
        if (StringUtils.isEmpty(preset.getLabel())) {
            throw new ServiceException("请填写风格名称");
        }
        if (StringUtils.isEmpty(preset.getPanelPrompt())) {
            throw new ServiceException("请填写风格描述");
        }
    }

    @Override
    public int removePresets(Collection<Long> presetIds) {
        if (presetIds == null || presetIds.isEmpty()) {
            return 0;
        }
        List<Long> ids = presetIds.stream().filter(id -> id != null).distinct().collect(Collectors.toList());
        if (ids.isEmpty()) {
            return 0;
        }
        List<BizPhotoDrawPreset> rows = listByIds(ids);
        List<String> builtinLabels = new ArrayList<String>();
        List<Long> removable = new ArrayList<Long>();
        for (BizPhotoDrawPreset row : rows) {
            if (row == null) {
                continue;
            }
            if ("builtin".equals(row.getSource())) {
                builtinLabels.add(row.getLabel());
            } else {
                removable.add(row.getPresetId());
            }
        }
        if (!builtinLabels.isEmpty()) {
            throw new ServiceException("内置预设不可删除：" + String.join("、", builtinLabels));
        }
        if (removable.isEmpty()) {
            return 0;
        }
        removeByIds(removable);
        return removable.size();
    }

    private void normalize(BizPhotoDrawPreset preset) {
        if (StringUtils.isEmpty(preset.getPanelSize())) {
            preset.setPanelSize("960*1280");
        }
        if (StringUtils.isEmpty(preset.getLayout())) {
            preset.setLayout(PhotoDrawLayout.FULL_CANVAS.name());
        } else {
            try {
                PhotoDrawLayout.valueOf(preset.getLayout().trim());
                preset.setLayout(preset.getLayout().trim());
            } catch (Exception e) {
                throw new ServiceException("无效拼版布局：" + preset.getLayout());
            }
        }
        if (preset.getLabel() != null) {
            preset.setLabel(preset.getLabel().trim());
        }
        if (preset.getPanelPrompt() != null) {
            preset.setPanelPrompt(preset.getPanelPrompt().trim());
        }
    }

    private void validateKeyFormat(String key) {
        if (StringUtils.isEmpty(key) || !key.matches("^[a-z0-9][a-z0-9-]{0,62}$")) {
            throw new ServiceException("预设标识仅允许小写字母、数字与连字符，且不超过 63 字符");
        }
    }
}
