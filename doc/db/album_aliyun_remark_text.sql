-- 云盘同步：remark 存排除相册 JSON，varchar(500) 不够
-- 新格式：{"excludedAlbums":[{"albumId":"...","name":"..."}]}
-- 旧格式纯数组白名单升级后视为无排除（同步全部）
-- 全新安装请用 doc/db/photo_sys_only_export.sql（已含 TEXT）
-- 已有库执行本脚本后，重新保存云盘同步配置即可

USE `photo`;

ALTER TABLE `biz_aliyun_drive_setting`
  MODIFY COLUMN `remark` text COMMENT '云盘同步排除相册 JSON（excludedAlbums）';
