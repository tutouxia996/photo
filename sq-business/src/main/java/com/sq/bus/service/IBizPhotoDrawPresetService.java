package com.sq.bus.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sq.bus.domain.BizPhotoDrawPreset;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface IBizPhotoDrawPresetService extends IService<BizPhotoDrawPreset> {

    /**
     * 启用中的预设（出图下拉用）。
     */
    List<BizPhotoDrawPreset> listEnabled();

    /**
     * 按 preset_key 查找启用中的预设。
     */
    BizPhotoDrawPreset getEnabledByKey(String presetKey);

    /**
     * 解析 skill 文本/文件内容，不落库。
     */
    Map<String, Object> parseSkill(String raw, String fileName);

    /**
     * 新增前规范化默认值并校验 key 唯一。
     */
    void prepareCreate(BizPhotoDrawPreset preset);

    /**
     * 更新前校验。
     */
    void prepareUpdate(BizPhotoDrawPreset preset);

    /**
     * 删除预设；内置（source=builtin）不可删。
     * @return 实际删除条数
     */
    int removePresets(Collection<Long> presetIds);
}
