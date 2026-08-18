-- 阿里云盘相册同步：页面可改配置（单行表 id=1）
-- 执行后：后台「相册管理 → 云盘同步」可保存；未保存前仍可读 yml 默认值。

CREATE TABLE IF NOT EXISTS `biz_aliyun_drive_setting` (
  `id` bigint NOT NULL COMMENT '固定为 1',
  `enabled` tinyint NOT NULL DEFAULT 0 COMMENT '是否启用',
  `refresh_token` varchar(1024) DEFAULT '' COMMENT '网页版 refresh_token',
  `remote_album_name` varchar(200) DEFAULT '' COMMENT '云盘相册名称',
  `remote_album_id` varchar(100) DEFAULT '' COMMENT '云盘相册ID（优先）',
  `local_path` varchar(500) DEFAULT '' COMMENT '本机下载目录',
  `scan_path_id` bigint DEFAULT NULL COMMENT '磁盘扫描目录 path_id',
  `trigger_scan` tinyint NOT NULL DEFAULT 1 COMMENT '下载后是否触发扫描',
  `full_scan` tinyint NOT NULL DEFAULT 0 COMMENT '1全量 0增量',
  `token_file` varchar(500) DEFAULT '' COMMENT 'token 与已下文件状态文件',
  `connect_timeout_ms` int DEFAULT 15000,
  `read_timeout_ms` int DEFAULT 120000,
  `download_timeout_ms` int DEFAULT 600000,
  `media_only` tinyint NOT NULL DEFAULT 0 COMMENT '1仅图片视频扩展名',
  `download_concurrency` int DEFAULT 2,
  `download_referer` varchar(200) DEFAULT '',
  `chunk_concurrency` int DEFAULT 8,
  `multipart_min_bytes` bigint DEFAULT 2097152,
  `update_by` varchar(64) DEFAULT '',
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='阿里云盘相册同步配置';

-- 菜单：相册管理 → 云盘同步
DELETE FROM sys_role_menu WHERE menu_id BETWEEN 3005 AND 3005 OR menu_id BETWEEN 3050 AND 3052;
DELETE FROM sys_menu WHERE id IN (3005, 3050, 3051, 3052);

INSERT INTO sys_menu(id, menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) VALUES
(3005,'云盘同步',3000,5,'aliyun','album/aliyun/index','',1,0,'C','0','0','album:aliyun:query','upload','admin',NOW(),'阿里云盘相册同步配置'),
(3050,'云盘配置查询',3005,1,'','','',1,0,'F','0','0','album:aliyun:query','#','admin',NOW(),''),
(3051,'云盘配置修改',3005,2,'','','',1,0,'F','0','0','album:aliyun:edit','#','admin',NOW(),''),
(3052,'云盘立即同步',3005,3,'','','',1,0,'F','0','0','album:aliyun:run','#','admin',NOW(),'');

INSERT INTO sys_role_menu(role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id IN (3005, 3050, 3051, 3052);
