package com.sq.bus.service;

import com.sq.bus.domain.vo.VideoProxyStatus;

import java.io.File;

/**
 * 视频浏览代理片：原片不改动，派生文件落在独立 cache/proxy 目录，不入库。
 */
public interface IVideoProxyService {

    /** 查询某档状态；不触发转码 */
    VideoProxyStatus status(Long photoId, String quality, Integer fps);

    /** 若未就绪则异步开始转码；已就绪直接返回 */
    VideoProxyStatus ensure(Long photoId, String quality, Integer fps);

    /**
     * 同 {@link #ensure(Long, String, Integer)}；force=true 时删除已有浏览档再转。
     */
    VideoProxyStatus ensure(Long photoId, String quality, Integer fps, boolean force);

    /** 仅当该档已就绪时返回文件，否则 null */
    File resolveReadyFile(Long photoId, String quality, Integer fps);

    /** 彻底删除某照片的全部代理片 */
    void deleteProxies(Long photoId);

    /** 路径是否落在代理缓存根下（扫描时应跳过） */
    boolean isUnderProxyRoot(File file);

    /** 是否满足转码条件（1080p+、≥30fps，见 album.videoProxy） */
    boolean needsVideoProxy(Long photoId);

    /** 全局转码进度（右下角浮层） */
    com.sq.bus.domain.vo.VideoProxyProgress getProgress();

    /** 批量排队前重置计数 */
    void beginBatch();

    /** 批量排队结束（不再新增任务） */
    void endBatch();

    /** 本批跳过一视频（不符合条件等） */
    void onBatchVideoSkipped();
}
