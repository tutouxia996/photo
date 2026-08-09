-- 轨迹点位说明：用于记录车次、步行路线等文字描述
-- 已有库执行本脚本；新建库见 album_biz_init.sql

ALTER TABLE `biz_track_point`
  ADD COLUMN `description` varchar(500) DEFAULT NULL COMMENT '点位说明（车次、路线等）' AFTER `altitude`;
