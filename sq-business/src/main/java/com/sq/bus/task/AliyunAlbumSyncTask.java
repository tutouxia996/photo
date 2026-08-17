package com.sq.bus.task;

import com.sq.bus.service.cloud.AliyunDriveSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 若依 Quartz 可调用任务：阿里云盘个人相册同步 + 磁盘扫描入库。
 * <p>
 * 调用目标：{@code aliyunAlbumSyncTask.syncAndScan()}
 * </p>
 */
@Component("aliyunAlbumSyncTask")
public class AliyunAlbumSyncTask {

    private static final Logger log = LoggerFactory.getLogger(AliyunAlbumSyncTask.class);

    @Autowired
    private AliyunDriveSyncService aliyunDriveSyncService;

    /**
     * 无参方法，供 sys_job.invoke_target 调用。
     */
    public void syncAndScan() {
        String result = aliyunDriveSyncService.syncAndScan();
        log.info("aliyunAlbumSyncTask: {}", result);
    }
}
