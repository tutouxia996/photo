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
}
