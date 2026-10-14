-- 相册分享：访问次数
-- 已有 photo 库增量执行

USE `photo`;

ALTER TABLE `biz_album_share`
  ADD COLUMN `view_count` bigint(20) NOT NULL DEFAULT 0
  COMMENT '访问次数（访客打开分享页累计）'
  AFTER `status`;
