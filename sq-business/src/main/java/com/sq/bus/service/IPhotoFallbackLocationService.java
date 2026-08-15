package com.sq.bus.service;

import com.sq.bus.domain.AiLandmarkRequest;
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
     * 不覆盖 EXIF/视频/手工等权威来源；默认可覆盖已有 region_center / ai_landmark / time_interp。
     *
     * @return 结果：updated / centerLat / centerLng / address / photoCount
     */
    java.util.Map<String, Object> fillMissingByRegionCenter(Long albumId, RegionLocateRequest request);

    /**
     * AI 识别用户点选的照片地标，地理编码后写入 ai_landmark（可覆盖 region_center）。
     * 可多次调用；每次仅识别请求中的 photoIds。
     *
     * @return 结果：updated / sampled / recognized / landmarks / interpUpdated
     */
    java.util.Map<String, Object> fillMissingByAiLandmark(Long albumId, AiLandmarkRequest request);

    /**
     * 将相册内尚未确认的估计点（区/AI/估，置信度&lt;1）全部确认上主轨迹。
     * 保留来源标记，置信度置 1。
     *
     * @return confirmed 数量等
     */
    java.util.Map<String, Object> confirmAllPendingEstimated(Long albumId);
}
