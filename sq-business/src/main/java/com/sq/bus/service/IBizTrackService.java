package com.sq.bus.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sq.bus.domain.BizTrack;

import java.util.Date;

public interface IBizTrackService extends IService<BizTrack> {

    /**
     * 根据相册与时间范围生成正式轨迹（仅权威 GPS：EXIF/视频/手工确认）。
     */
    BizTrack generateTrack(Long albumId, String trackName, Date startTime, Date endTime, boolean includeEstimated);

    /**
     * 区域定位后：用估计坐标确保相册下有一条可再次打开的照片轨草稿（写入列表）。
     * 不是正式轨迹生成；用户确认定位前不应依赖此轨作为最终行程。
     */
    BizTrack ensureDraftTrackFromEstimated(Long albumId);

    /**
     * 导入媒体后自动同步相册轨迹：无 GPS 点则跳过；已有轨迹则刷新最早一条，避免重复新建。
     *
     * @return 新建或刷新后的轨迹；无可用 GPS 时返回 null
     */
    BizTrack autoSyncAlbumTrack(Long albumId);

    /**
     * 估计点批量确认上主轨迹后：同步照片轨并清除「区域粗定位草稿」状态（可启用）。
     */
    BizTrack promoteAfterEstimatedConfirmed(Long albumId);

    /**
     * 软删轨迹；若某相册下已无未删轨迹，则级联清理该相册 GPX 叠层。
     *
     * @return 实际软删的轨迹条数
     */
    int removeTracks(Long[] trackIds, String username);
}
