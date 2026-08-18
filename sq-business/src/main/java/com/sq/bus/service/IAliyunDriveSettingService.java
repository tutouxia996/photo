package com.sq.bus.service;

import com.sq.bus.config.AlbumProperties;
import com.sq.bus.domain.vo.AliyunDriveSettingVo;

public interface IAliyunDriveSettingService {

    /**
     * 页面展示用配置：库优先，未保存过则回落 yml；token 脱敏。
     */
    AliyunDriveSettingVo getForPage();

    /**
     * 保存页面配置。refreshToken 为空或脱敏占位则保留原值。
     */
    void saveFromPage(AliyunDriveSettingVo vo, String updateBy);

    /**
     * 同步任务实际使用的配置（库覆盖 yml）。
     */
    AlbumProperties.AliyunDriveConfig getEffectiveConfig();
}
