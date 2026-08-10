-- 轨迹启用开关：关闭后不再自动生成/刷新，且前台地图不展示；数据保留不删除
-- 已有库执行本脚本；新建库见 album_biz_init.sql

ALTER TABLE `biz_track`
  ADD COLUMN `enabled` tinyint(4) NOT NULL DEFAULT 1 COMMENT '是否启用轨迹：0关闭（不生成/地图不显示）1开启' AFTER `is_public`;
