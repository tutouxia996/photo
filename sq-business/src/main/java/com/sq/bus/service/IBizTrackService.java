package com.sq.bus.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sq.bus.domain.BizTrack;

import java.util.Date;

public interface IBizTrackService extends IService<BizTrack> {

    /**
     * 根据相册与时间范围生成轨迹
     */
    BizTrack generateTrack(Long albumId, String trackName, Date startTime, Date endTime);
}
