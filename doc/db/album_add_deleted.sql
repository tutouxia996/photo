-- =====================================================================
-- 相册/照片增加三态 deleted 字段（已有库增量脚本）
-- 0未删除 1已删除 2回收站
-- =====================================================================

USE `photo`;

ALTER TABLE `biz_album`
  ADD COLUMN `deleted` tinyint(4) NOT NULL DEFAULT 0 COMMENT '0未删除 1已删除 2回收站' AFTER `remark`,
  ADD KEY `idx_deleted` (`deleted`);

ALTER TABLE `biz_photo`
  ADD COLUMN `deleted` tinyint(4) NOT NULL DEFAULT 0 COMMENT '0未删除 1已删除 2回收站' AFTER `remark`,
  ADD KEY `idx_deleted` (`deleted`);
