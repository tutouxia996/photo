package com.sq.bus.service;

import com.sq.bus.domain.vo.AlbumDownloadProgress;

import java.io.File;

/**
 * 相册异步打包下载（原图/原视频 → zip）。
 */
public interface IAlbumDownloadService {

    /** 启动后台打包；若已有任务在跑则返回当前进度 */
    AlbumDownloadProgress start(Long albumId);

    AlbumDownloadProgress getProgress();

    /** 取已就绪的 zip；未就绪返回 null */
    File resolveReadyFile(String taskId);

    /** 客户端取走文件后标记完成并清理 */
    void markConsumed(String taskId);
}
