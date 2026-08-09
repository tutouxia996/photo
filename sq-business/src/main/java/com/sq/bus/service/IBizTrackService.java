package com.sq.bus.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sq.bus.domain.BizTrack;

import java.util.Date;

public interface IBizTrackService extends IService<BizTrack> {

    /**
     * 根据相册与时间范围生成轨迹
     */
    BizTrack generateTrack(Long albumId, String trackName, Date startTime, Date endTime);

    /**
     * 导入媒体后自动同步相册轨迹：无 GPS 点则跳过；已有轨迹则刷新最早一条，避免重复新建。
     *
     * @return 新建或刷新后的轨迹；无可用 GPS 时返回 null
     */
    BizTrack autoSyncAlbumTrack(Long albumId);
}
