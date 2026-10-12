package com.sq.bus.domain.vo;

import com.sq.bus.domain.vo.RemoteAlbumItem;
import lombok.Data;

import java.util.List;

/**
 * 阿里云盘同步配置（页面读写；refreshToken 仅回显脱敏值）。
 */
@Data
public class AliyunDriveSettingVo {
    private boolean enabled;
    /** 提交新 token 时填写；查询时为脱敏或空 */
    private String refreshToken;
    /** 是否已保存过 token（脱敏展示） */
    private boolean hasRefreshToken;
    private String refreshTokenMasked;
    /** 兼容旧字段（已不再作为同步目标） */
    private String remoteAlbumName;
    private String remoteAlbumId;
    /** 兼容旧版白名单；新逻辑忽略 */
    private List<RemoteAlbumItem> remoteAlbums;
    /** 排除不同步的云盘相册；空=同步全部 */
    private List<RemoteAlbumItem> excludedAlbums;
    private String localPath;
    /** 页面用：本机父目录（不含相册名子文件夹） */
    private String localBasePath;
    /** 页面用：入库绑定相册 ID（保存时自动维护 scanPathId） */
    private Long bindAlbumId;
    /** 只读展示：已关联的磁盘扫描目录名称 */
    private String linkedScanPathName;
    /** 只读展示：已关联的磁盘扫描本地路径 */
    private String linkedScanPathLocal;
    private Long scanPathId;
    private boolean triggerScan;
    private boolean fullScan;
    private String tokenFile;
    private Integer connectTimeoutMs;
    private Integer readTimeoutMs;
    private Integer downloadTimeoutMs;
    private boolean mediaOnly;
    private Integer downloadConcurrency;
    private String downloadReferer;
    private Integer chunkConcurrency;
    private Long multipartMinBytes;
    /** db=已保存到库；yml=尚未保存，当前为配置文件默认值 */
    private String source;
    private boolean running;
}
