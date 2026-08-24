-- 阿里云盘同步：建表 + 菜单 + 定时任务（已有 photo 库增量执行）
-- 全新安装请直接用 doc/db/photo_sys_only_export.sql
-- 多相册 remark 字段过短请再执行 doc/db/album_aliyun_remark_text.sql

USE `photo`;

CREATE TABLE IF NOT EXISTS `biz_aliyun_drive_setting` (
  `id` bigint(20) NOT NULL COMMENT '固定为 1',
  `enabled` tinyint(4) NOT NULL DEFAULT '0' COMMENT '是否启用',
  `refresh_token` varchar(1024) DEFAULT '' COMMENT '网页版 refresh_token',
  `remote_album_name` varchar(200) DEFAULT '' COMMENT '云盘相册名称（兼容：首个）',
  `remote_album_id` varchar(100) DEFAULT '' COMMENT '云盘相册ID（兼容：首个）',
  `local_path` varchar(500) DEFAULT '' COMMENT '本机父目录（多相册时各下载到 父目录/相册名）',
  `scan_path_id` bigint(20) DEFAULT NULL COMMENT '磁盘扫描目录 path_id（多相册时由程序按相册维护）',
  `trigger_scan` tinyint(4) NOT NULL DEFAULT '1' COMMENT '下载后是否触发扫描',
  `full_scan` tinyint(4) NOT NULL DEFAULT '0' COMMENT '1全量 0增量',
  `token_file` varchar(500) DEFAULT '' COMMENT 'token 与已下文件状态文件',
  `connect_timeout_ms` int(11) DEFAULT '15000',
  `read_timeout_ms` int(11) DEFAULT '120000',
  `download_timeout_ms` int(11) DEFAULT '600000',
  `media_only` tinyint(4) NOT NULL DEFAULT '0' COMMENT '1仅图片视频扩展名',
  `download_concurrency` int(11) DEFAULT '2',
  `download_referer` varchar(200) DEFAULT '',
  `chunk_concurrency` int(11) DEFAULT '8',
  `multipart_min_bytes` bigint(20) DEFAULT '2097152',
  `update_by` varchar(64) DEFAULT '',
  `update_time` datetime DEFAULT NULL,
  `remark` text COMMENT '多选云盘相册 JSON（remoteAlbums）',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='阿里云盘相册同步配置';

-- 菜单：相册管理 → 云盘同步
INSERT IGNORE INTO `sys_menu` VALUES
(3005,'云盘同步',3000,5,'aliyun','album/aliyun/index','',1,0,'C','0','0','album:aliyun:query','upload','admin','2026-08-18 21:01:12','',NULL,'阿里云盘相册同步配置','0'),
(3050,'云盘配置查询',3005,1,'','','',1,0,'F','0','0','album:aliyun:query','#','admin','2026-08-18 21:01:12','',NULL,'','0'),
(3051,'云盘配置修改',3005,2,'','','',1,0,'F','0','0','album:aliyun:edit','#','admin','2026-08-18 21:01:12','',NULL,'','0'),
(3052,'云盘立即同步',3005,3,'','','',1,0,'F','0','0','album:aliyun:run','#','admin','2026-08-18 21:01:12','',NULL,'','0');

INSERT IGNORE INTO `sys_role_menu` VALUES
(1,3005),(1,3050),(1,3051),(1,3052);

INSERT IGNORE INTO `sys_job` VALUES
(4,'阿里云盘相册同步','DEFAULT','aliyunAlbumSyncTask.syncAndScan()','0 0 2 * * ?','2','0','1','admin','2026-08-18 01:04:47','admin','2026-08-18 21:03:18','');
