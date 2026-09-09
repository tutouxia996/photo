package com.sq.bus.task;

import com.sq.bus.domain.vo.AliyunSyncProgress;
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
     * 若上次有剩余未下完（失败/暂停），自动续下，避免远程大视频卡在 paused 状态。
     */
    public void syncAndScan() {
        AliyunSyncProgress p = aliyunDriveSyncService.getProgress();
        if (p != null && p.isRunning() && p.getStatus() == 0 && !p.isPaused()) {
            log.info("云盘同步进行中（剩余 {}），定时任务跳过", p.getRemaining());
            return;
        }
        if (p != null && p.getRemaining() > 0) {
            log.info("云盘尚有剩余 {} 个未下载（paused={}），定时任务自动续下",
                    p.getRemaining(), p.isPaused());
        }
        String result = aliyunDriveSyncService.syncAndScan();
        log.info("aliyunAlbumSyncTask: {}", result);
    }
}
