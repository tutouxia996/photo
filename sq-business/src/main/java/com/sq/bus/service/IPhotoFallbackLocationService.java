package com.sq.bus.service;

import com.sq.bus.domain.RegionLocateRequest;

/**
 * 无设备 GPS / GPX 时的坐标兜底（仅填空，不覆盖权威坐标）。
 */
public interface IPhotoFallbackLocationService {

    /**
     * 按同相册权威 GPS 点与拍摄时间，为缺失坐标的媒体做时间插值/有限外推。
     *
     * @return 本轮写入或更新的照片数量
     */
    int fillMissingByTimeInterp(Long albumId);

    /**
     * 按国家/省/市/区（或给定坐标）为无 GPS 媒体写入区域中心粗定位（region_center）。
     * 不覆盖 EXIF/视频/手工/时间插值/AI 等来源；默认可覆盖已有 region_center。
     *
     * @return 结果：updated / centerLat / centerLng / address / photoCount
     */
    java.util.Map<String, Object> fillMissingByRegionCenter(Long albumId, RegionLocateRequest request);
}
