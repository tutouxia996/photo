package com.sq.bus.domain.vo;

import lombok.Data;

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
    private String remoteAlbumName;
    private String remoteAlbumId;
    private String localPath;
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
