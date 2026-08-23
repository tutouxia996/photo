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
     * 为已入库但缺少缩略图的视频补截帧（按扫描目录关联相册；pathId 空则全部）。
     * @param force true 时即使已有封面也强制重截（大疆 Action 等可重试）
     * @return repaired/failed/skipped/total
     */
    java.util.Map<String, Object> repairMissingVideoThumbs(Long pathId, boolean force);

    /**
     * 一键排队生成视频浏览档（720p/1080p × 30/60），写入 cache/proxy，不改原片、不入库。
     * @param pathId 扫描目录；空则全部启用目录
     * @param force true 时已有浏览档也重新转码
     * @return queued/ready/skipped/failed/total 等统计（转码在后台进行）
     */
    java.util.Map<String, Object> enqueueVideoProxies(Long pathId, boolean force);

    /**
     * 统计扫描目录下视频的转码资格（不入队、不写盘）。
     */
    java.util.Map<String, Object> videoProxyStats(Long pathId);
}
