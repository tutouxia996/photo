-- 360 全景标识：已有 photo 库增量执行
-- 全新安装请同步更新 doc/db/photo_sys_only_export.sql 中 biz_photo 表结构

USE `photo`;

ALTER TABLE `biz_photo`
  ADD COLUMN `is_pano` tinyint(4) DEFAULT NULL COMMENT '是否360全景：1是 0否 空未检测'
  AFTER `file_type`;
