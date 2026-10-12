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
     * 解析本次要同步的云盘相册：实时列举全部个人相册，再去掉排除列表。
     * 已存在的本地文件仍会增量跳过；新相册与旧相册中的新文件都会被检测。
     */
    List<RemoteAlbumItem> resolveAlbumsToSync(AlbumProperties.AliyunDriveConfig cfg);

    /**
     * 兼容旧调用：仅返回配置里写死的相册列表（不含实时列举）。
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

    /**
     * 将轮换后的 refresh_token 写回数据库（阿里云盘每次刷新会使旧 token 失效）。
     */
    void persistRotatedRefreshToken(String refreshToken);
}
