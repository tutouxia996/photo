package com.sq.bus.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sq.bus.domain.BizScanPath;

public interface IBizScanPathService extends IService<BizScanPath> {

    /**
     * 执行目录扫描（增量/全量）
     * @param pathId 目录配置ID
     * @param fullScan true=全量
     */
    Long runScan(Long pathId, boolean fullScan);
}
