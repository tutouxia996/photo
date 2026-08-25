package com.sq.bus.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sq.bus.domain.BizScanPath;

public interface IBizScanPathService extends IService<BizScanPath> {

    /**
     * 同步执行目录扫描（阻塞直到结束），返回扫描日志ID
     * @param pathId 目录配置ID
     * @param fullScan true=全量
     */
    Long runScan(Long pathId, boolean fullScan);

    /**
     * 异步启动目录扫描，立即返回扫描日志ID，可通过日志接口轮询进度
     * @param pathId 目录配置ID
     * @param fullScan true=全量
     */
    Long startScanAsync(Long pathId, boolean fullScan);

    /**
     * 为已入库视频补封面，并补齐缺失的 GPS；补到 GPS 后会自动刷新相册轨迹。
     * @param force true 时即使已有封面也强制重截（大疆 Action 等可重试）
     * @return repaired/gpsFilled/tracksSynced/failed/skipped/total
     */
    java.util.Map<String, Object> repairMissingVideoThumbs(Long pathId, boolean force);

    /**
     * 一键排队生成视频浏览档（480p/720p/1080p × 30），写入 cache/proxy，不改原片、不入库。
     * @param pathId 扫描目录；空则全部启用目录
     * @param force true 时已有浏览档也重新转码（覆盖）；false 时已就绪的档位跳过
     * @return queued/ready/skipped/failed/total 等统计（转码在后台进行）
     */
    java.util.Map<String, Object> enqueueVideoProxies(Long pathId, boolean force);

    /**
     * 统计扫描目录下视频的转码资格（不入队、不写盘）。
     */
    java.util.Map<String, Object> videoProxyStats(Long pathId);

    /**
     * 确保照片/视频有可用的缩略图；视频无封面时尝试 ffmpeg 截帧并写库。
     *
     * @return thumbUrl，失败返回 null
     */
    String ensurePhotoThumb(Long photoId);

    /**
     * 当前进行中的扫描日志（无则 null）。
     */
    com.sq.bus.domain.BizScanLog getActiveScanLog();

    /**
     * 按本机路径与绑定相册自动创建或更新扫描目录，返回 pathId。
     */
    Long upsertScanPathForSync(String localPath, Long albumId, String pathName);
}
