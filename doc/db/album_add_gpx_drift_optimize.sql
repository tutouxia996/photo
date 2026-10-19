-- 轨迹：GPX 原地漂移优化开关（默认开启=地图隐藏漂移段）
-- 已有 photo 库增量执行；全新安装请同步更新 photo_sys_only_export.sql

USE `photo`;

ALTER TABLE `biz_track`
  ADD COLUMN `gpx_drift_optimize` tinyint(4) NOT NULL DEFAULT 1
    COMMENT '优化原地漂移显示：1隐藏漂移段 0以漂移色显示'
    AFTER `gpx_enabled`;
