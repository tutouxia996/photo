-- 照片坐标来源与置信度（区分 EXIF/视频 GPS 与兜底估计，避免污染主轨迹）
-- 已有库执行本脚本

ALTER TABLE `biz_photo`
  ADD COLUMN `location_source` varchar(32) DEFAULT NULL COMMENT '坐标来源：exif/video/gpx_match/manual/time_interp/ai_landmark/region_center' AFTER `longitude`,
  ADD COLUMN `location_confidence` decimal(4,3) DEFAULT NULL COMMENT '坐标置信度 0~1（兜底估计用）' AFTER `location_source`;
