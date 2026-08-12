package com.sq.bus.service;

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
}
