-- 轨迹路段真实路径折线（高德规划结果，GCJ-02，JSON:[[lat,lng],...]）
-- 已有库执行本脚本；新建库见 album_biz_init.sql

ALTER TABLE `biz_track_point`
  ADD COLUMN `route_path` mediumtext COMMENT '到下一站的真实路线折线 JSON' AFTER `travel_mode`;
