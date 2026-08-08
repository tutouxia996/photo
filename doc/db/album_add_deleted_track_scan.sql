-- =====================================================================
-- 轨迹 / 扫描目录增加二态 deleted 字段（已有库增量脚本）
-- 0未删除 1已删除
-- =====================================================================

USE `photo`;

ALTER TABLE `biz_track`
  ADD COLUMN `deleted` tinyint(4) NOT NULL DEFAULT 0 COMMENT '0未删除 1已删除' AFTER `remark`,
  ADD KEY `idx_track_deleted` (`deleted`);

ALTER TABLE `biz_scan_path`
  ADD COLUMN `deleted` tinyint(4) NOT NULL DEFAULT 0 COMMENT '0未删除 1已删除' AFTER `remark`,
  ADD KEY `idx_scan_path_deleted` (`deleted`);
