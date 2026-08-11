-- GPX 文件出行方式（可自动推断、可手动修改）
-- 已有库执行本脚本

ALTER TABLE `biz_track_gpx_file`
  ADD COLUMN `travel_mode` varchar(32) DEFAULT NULL COMMENT '出行方式：hsr/train/bus/metro/walk/drive/bike/flight/other' AFTER `distance_km`;
