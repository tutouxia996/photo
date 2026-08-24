package com.sq.bus.service;

import com.sq.bus.config.AlbumProperties;
import com.sq.bus.domain.vo.AliyunDriveSettingVo;
import com.sq.bus.domain.vo.RemoteAlbumItem;

import java.util.List;

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

    /**
     * 解析配置中的云盘相册列表（多相册优先，否则回落单项）。
     */
    List<RemoteAlbumItem> listRemoteAlbumsFromConfig(AlbumProperties.AliyunDriveConfig cfg);

    /**
     * 按云盘相册名查找或创建本地相册（用于多相册各自入库）。
     */
    Long resolveOrCreateLocalAlbumId(String albumName, String updateBy);

    /**
     * 本机父目录 + 云盘相册名 → 完整下载路径
     */
    String composeAlbumLocalPath(String basePath, String albumName);

    /**
     * 列举云盘个人相册（需有效 refresh_token）。
     */
    java.util.List<java.util.Map<String, String>> listRemoteAlbums(String refreshToken);
}
