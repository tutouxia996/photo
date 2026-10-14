-- 相册分享支持一次多选多个相册（同一令牌）
-- 已有 photo 库增量执行；全新安装请同步更新 doc/db/photo_sys_only_export.sql 与 album_add_share.sql

USE `photo`;

ALTER TABLE `biz_album_share`
  ADD COLUMN `album_ids` varchar(1000) DEFAULT NULL
  COMMENT '分享包含的全部相册ID JSON数组；空表示仅 album_id 单相册'
  AFTER `album_id`;
