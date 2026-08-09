-- 轨迹路段出行方式：表示「从该点到下一点」的交通方式（高铁/公交/步行等）
-- 已有库执行本脚本；新建库见 album_biz_init.sql

ALTER TABLE `biz_track_point`
  ADD COLUMN `travel_mode` varchar(32) DEFAULT NULL COMMENT '到下一站的出行方式：hsr/train/bus/metro/walk/drive/bike/flight/other' AFTER `description`;
