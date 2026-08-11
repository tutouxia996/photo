-- 轨迹级 GPX 地图开关 + GPX 文件里程缓存
-- 已有库执行本脚本；新建库见 album_biz_init.sql

ALTER TABLE `biz_track`
  ADD COLUMN `gpx_enabled` tinyint(4) NOT NULL DEFAULT 1 COMMENT '是否在地图启用GPX线路：0否 1是' AFTER `enabled`;

ALTER TABLE `biz_track_gpx_file`
  ADD COLUMN `distance_km` decimal(10,3) NOT NULL DEFAULT 0.000 COMMENT 'GPX里程（公里）' AFTER `point_count`;
