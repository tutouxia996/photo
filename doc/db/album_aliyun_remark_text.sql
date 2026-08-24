-- 云盘同步多相册：remark 存 remoteAlbums JSON，varchar(500) 不够
-- 全新安装请用 doc/db/photo_sys_only_export.sql（已含 TEXT）
-- 已有库执行本脚本后，重新保存云盘同步配置即可

USE `photo`;

ALTER TABLE `biz_aliyun_drive_setting`
  MODIFY COLUMN `remark` text COMMENT '多选云盘相册 JSON（remoteAlbums）';
