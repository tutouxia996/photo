-- =====================================================================
-- 相册展示网站业务表初始化（MySQL 5.7+）
-- 库名：photo
-- =====================================================================

USE `photo`;

-- ----------------------------
-- 相册表
-- ----------------------------
DROP TABLE IF EXISTS `biz_album`;
CREATE TABLE `biz_album` (
  `album_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '相册ID',
  `album_name` varchar(100) NOT NULL COMMENT '相册名称',
  `album_desc` varchar(500) DEFAULT NULL COMMENT '相册描述',
  `cover_url` varchar(255) DEFAULT NULL COMMENT '封面图片访问地址',
  `is_public` tinyint(4) NOT NULL DEFAULT 1 COMMENT '是否公开：0私有 1公开',
  `photo_count` int(11) NOT NULL DEFAULT 0 COMMENT '照片数量',
  `start_time` datetime DEFAULT NULL COMMENT '最早拍摄时间',
  `end_time` datetime DEFAULT NULL COMMENT '最晚拍摄时间',
  `location_summary` varchar(200) DEFAULT NULL COMMENT '地点概要',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '排序号',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `deleted` tinyint(4) NOT NULL DEFAULT 0 COMMENT '0未删除 1已删除 2回收站',
  PRIMARY KEY (`album_id`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='相册表';

-- ----------------------------
-- 图片表
-- ----------------------------
DROP TABLE IF EXISTS `biz_photo`;
CREATE TABLE `biz_photo` (
  `photo_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '图片ID',
  `album_id` bigint(20) NOT NULL COMMENT '所属相册ID',
  `file_name` varchar(200) NOT NULL COMMENT '原始文件名',
  `file_path` varchar(500) NOT NULL COMMENT '物理文件绝对路径',
  `file_url` varchar(255) NOT NULL COMMENT '访问相对URL',
  `thumb_url` varchar(255) DEFAULT NULL COMMENT '缩略图URL',
  `file_size` bigint(20) NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
  `file_type` tinyint(4) NOT NULL DEFAULT 1 COMMENT '文件类型：1图片 2视频',
  `duration` int(11) DEFAULT NULL COMMENT '视频时长（秒）',
  `shoot_time` datetime DEFAULT NULL COMMENT '拍摄时间',
  `latitude` decimal(10,7) DEFAULT NULL COMMENT '纬度',
  `longitude` decimal(10,7) DEFAULT NULL COMMENT '经度',
  `location_source` varchar(32) DEFAULT NULL COMMENT '坐标来源：exif/video/gpx_match/manual/time_interp/ai_landmark/region_center',
  `location_confidence` decimal(4,3) DEFAULT NULL COMMENT '坐标置信度 0~1（兜底估计用）',
  `address` varchar(200) DEFAULT NULL COMMENT '详细地址',
  `province` varchar(50) DEFAULT NULL COMMENT '省份',
  `city` varchar(50) DEFAULT NULL COMMENT '城市',
  `district` varchar(50) DEFAULT NULL COMMENT '区县',
  `camera_model` varchar(100) DEFAULT NULL COMMENT '相机型号',
  `lens_info` varchar(100) DEFAULT NULL COMMENT '镜头信息',
  `aperture` varchar(20) DEFAULT NULL COMMENT '光圈值',
  `shutter_speed` varchar(20) DEFAULT NULL COMMENT '快门速度',
  `iso` int(11) DEFAULT NULL COMMENT 'ISO感光度',
  `focal_length` varchar(20) DEFAULT NULL COMMENT '焦距',
  `md5` varchar(32) DEFAULT NULL COMMENT '文件MD5',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '排序号',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `aesthetic_score` int(11) DEFAULT NULL COMMENT '出图质量分 0-100',
  `score_pass` tinyint(4) DEFAULT NULL COMMENT '是否达到图生图门槛：0否 1是 空未打分',
  `score_reason` varchar(200) DEFAULT NULL COMMENT '打分摘要',
  `scored_at` datetime DEFAULT NULL COMMENT '最近打分时间',
  `origin_type` varchar(20) DEFAULT 'original' COMMENT '来源：original原片 ai_draw AI出图',
  `source_photo_id` bigint(20) DEFAULT NULL COMMENT 'AI出图时的源照片ID',
  `deleted` tinyint(4) NOT NULL DEFAULT 0 COMMENT '0未删除 1已删除 2回收站',
  PRIMARY KEY (`photo_id`),
  KEY `idx_album_id` (`album_id`),
  KEY `idx_shoot_time` (`shoot_time`),
  KEY `idx_md5` (`md5`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图片表';

-- ----------------------------
-- AI 出图记录
-- ----------------------------
DROP TABLE IF EXISTS `biz_photo_draw`;
CREATE TABLE `biz_photo_draw` (
  `draw_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '出图记录ID',
  `album_id` bigint(20) NOT NULL COMMENT '相册ID',
  `source_photo_id` bigint(20) NOT NULL COMMENT '源照片ID',
  `result_photo_id` bigint(20) DEFAULT NULL COMMENT '生成后入库的照片ID',
  `preset` varchar(64) NOT NULL COMMENT '预设标识',
  `status` varchar(20) NOT NULL DEFAULT 'pending' COMMENT 'pending/running/success/failed',
  `task_id` varchar(64) DEFAULT NULL COMMENT '万相异步任务ID',
  `error_msg` varchar(500) DEFAULT NULL COMMENT '失败原因',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`draw_id`),
  KEY `idx_source_photo` (`source_photo_id`),
  KEY `idx_album` (`album_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='照片AI出图记录';

-- ----------------------------
-- 轨迹表
-- ----------------------------
DROP TABLE IF EXISTS `biz_track`;
CREATE TABLE `biz_track` (
  `track_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '轨迹ID',
  `album_id` bigint(20) NOT NULL COMMENT '所属相册ID',
  `track_name` varchar(100) NOT NULL COMMENT '轨迹名称',
  `start_time` datetime DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `total_distance` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '总距离（公里）',
  `total_duration` bigint(20) NOT NULL DEFAULT 0 COMMENT '总时长（秒）',
  `point_count` int(11) NOT NULL DEFAULT 0 COMMENT '轨迹点数量',
  `track_color` varchar(20) NOT NULL DEFAULT '#3B82F6' COMMENT '轨迹线颜色',
  `is_public` tinyint(4) NOT NULL DEFAULT 1 COMMENT '是否前台展示',
  `enabled` tinyint(4) NOT NULL DEFAULT 1 COMMENT '是否启用轨迹：0关闭（不生成/地图不显示）1开启',
  `gpx_enabled` tinyint(4) NOT NULL DEFAULT 1 COMMENT '是否在地图启用GPX线路：0否 1是',
  `source_type` varchar(16) DEFAULT 'photo' COMMENT '来源：photo/gpx/mixed',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '行程说明/备注',
  `deleted` tinyint(4) NOT NULL DEFAULT 0 COMMENT '0未删除 1已删除',
  PRIMARY KEY (`track_id`),
  KEY `idx_track_album_id` (`album_id`),
  KEY `idx_track_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='轨迹表';

-- ----------------------------
-- 轨迹点表
-- ----------------------------
DROP TABLE IF EXISTS `biz_track_point`;
CREATE TABLE `biz_track_point` (
  `point_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '点位ID',
  `track_id` bigint(20) NOT NULL COMMENT '所属轨迹ID',
  `photo_id` bigint(20) DEFAULT NULL COMMENT '关联图片ID',
  `latitude` decimal(10,7) NOT NULL COMMENT '纬度',
  `longitude` decimal(10,7) NOT NULL COMMENT '经度',
  `point_time` datetime DEFAULT NULL COMMENT '点位时间',
  `sequence` int(11) NOT NULL DEFAULT 0 COMMENT '序号',
  `altitude` decimal(8,2) DEFAULT NULL COMMENT '海拔（米）',
  `description` varchar(500) DEFAULT NULL COMMENT '点位说明（车次、路线等）',
  `travel_mode` varchar(32) DEFAULT NULL COMMENT '到下一站的出行方式：hsr/train/bus/metro/walk/drive/bike/flight/other',
  `route_path` mediumtext COMMENT '到下一站的真实路线折线 JSON([[lat,lng],...], GCJ-02)',
  PRIMARY KEY (`point_id`),
  KEY `idx_point_track_id` (`track_id`),
  KEY `idx_point_photo_id` (`photo_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='轨迹点表';

-- ----------------------------
-- 轨迹 GPX 文件表
-- ----------------------------
DROP TABLE IF EXISTS `biz_track_gpx_file`;
CREATE TABLE `biz_track_gpx_file` (
  `gpx_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'GPX文件ID',
  `album_id` bigint(20) NOT NULL COMMENT '所属相册ID',
  `file_name` varchar(200) NOT NULL COMMENT '原始文件名',
  `storage_path` varchar(500) NOT NULL COMMENT '服务器存储路径',
  `point_count` int(11) NOT NULL DEFAULT 0 COMMENT '轨迹点数',
  `distance_km` decimal(10,3) NOT NULL DEFAULT 0.000 COMMENT 'GPX里程（公里）',
  `travel_mode` varchar(32) DEFAULT NULL COMMENT '出行方式：hsr/train/bus/metro/walk/drive/bike/flight/other',
  `start_time` datetime DEFAULT NULL COMMENT 'GPX开始时间',
  `end_time` datetime DEFAULT NULL COMMENT 'GPX结束时间',
  `enabled` tinyint(4) NOT NULL DEFAULT 1 COMMENT '是否参与生成：0否 1是',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `deleted` tinyint(4) NOT NULL DEFAULT 0 COMMENT '0未删除 1已删除',
  PRIMARY KEY (`gpx_id`),
  KEY `idx_gpx_album_id` (`album_id`),
  KEY `idx_gpx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='轨迹GPX文件';

-- ----------------------------
-- 扫描目录配置表
-- ----------------------------
DROP TABLE IF EXISTS `biz_scan_path`;
CREATE TABLE `biz_scan_path` (
  `path_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '配置ID',
  `path_name` varchar(100) NOT NULL COMMENT '目录名称',
  `local_path` varchar(500) NOT NULL COMMENT '本地磁盘绝对路径',
  `default_album_id` bigint(20) DEFAULT NULL COMMENT '默认绑定相册ID',
  `scan_cron` varchar(50) DEFAULT NULL COMMENT '定时扫描Cron',
  `last_scan_time` datetime DEFAULT NULL COMMENT '上次扫描时间',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '状态：0禁用 1启用',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `deleted` tinyint(4) NOT NULL DEFAULT 0 COMMENT '0未删除 1已删除',
  PRIMARY KEY (`path_id`),
  KEY `idx_scan_path_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='扫描目录配置表';

-- ----------------------------
-- 扫描记录表
-- ----------------------------
DROP TABLE IF EXISTS `biz_scan_log`;
CREATE TABLE `biz_scan_log` (
  `log_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `path_id` bigint(20) NOT NULL COMMENT '扫描目录ID',
  `scan_type` tinyint(4) NOT NULL DEFAULT 1 COMMENT '扫描类型：1增量 2全量',
  `total_count` int(11) NOT NULL DEFAULT 0 COMMENT '扫描文件总数',
  `new_count` int(11) NOT NULL DEFAULT 0 COMMENT '新增数量',
  `skip_count` int(11) NOT NULL DEFAULT 0 COMMENT '跳过数量',
  `fail_count` int(11) NOT NULL DEFAULT 0 COMMENT '失败数量',
  `status` tinyint(4) NOT NULL DEFAULT 0 COMMENT '状态：0进行中 1成功 2失败',
  `message` varchar(1000) DEFAULT NULL COMMENT '结果摘要/异常信息',
  `start_time` datetime DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`log_id`),
  KEY `idx_scan_log_path_id` (`path_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='扫描记录表';

-- ----------------------------
-- 阿里云盘相册同步配置（页面保存，单行 id=1）
-- ----------------------------
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

-- ----------------------------
-- 菜单与权限（相册管理）
-- ----------------------------
DELETE FROM sys_role_menu WHERE menu_id BETWEEN 3000 AND 3099;
DELETE FROM sys_menu WHERE id BETWEEN 3000 AND 3099;

INSERT INTO sys_menu(id, menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) VALUES
(3000,'相册管理',0,2,'album',NULL,'',1,0,'M','0','0','','example','admin',NOW(),'相册业务目录'),
(3001,'相册列表',3000,1,'album','album/album/index','',1,0,'C','0','0','album:album:list','documentation','admin',NOW(),'相册管理菜单'),
(3002,'图片管理',3000,2,'photo','album/photo/index','',1,0,'C','0','0','album:photo:list','image','admin',NOW(),'图片管理菜单'),
(3003,'轨迹管理',3000,3,'track','album/track/index','',1,0,'C','0','0','album:track:list','guide','admin',NOW(),'轨迹管理菜单'),
(3004,'磁盘扫描',3000,4,'scan','album/scan/index','',1,0,'C','0','0','album:scan:list','server','admin',NOW(),'磁盘扫描管理'),
(3005,'云盘同步',3000,5,'aliyun','album/aliyun/index','',1,0,'C','0','0','album:aliyun:query','upload','admin',NOW(),'阿里云盘相册同步配置'),
(3010,'相册查询',3001,1,'','','',1,0,'F','0','0','album:album:query','#','admin',NOW(),''),
(3011,'相册新增',3001,2,'','','',1,0,'F','0','0','album:album:add','#','admin',NOW(),''),
(3012,'相册修改',3001,3,'','','',1,0,'F','0','0','album:album:edit','#','admin',NOW(),''),
(3013,'相册删除',3001,4,'','','',1,0,'F','0','0','album:album:remove','#','admin',NOW(),''),
(3020,'图片查询',3002,1,'','','',1,0,'F','0','0','album:photo:query','#','admin',NOW(),''),
(3021,'图片新增',3002,2,'','','',1,0,'F','0','0','album:photo:add','#','admin',NOW(),''),
(3022,'图片修改',3002,3,'','','',1,0,'F','0','0','album:photo:edit','#','admin',NOW(),''),
(3023,'图片删除',3002,4,'','','',1,0,'F','0','0','album:photo:remove','#','admin',NOW(),''),
(3024,'图片上传',3002,5,'','','',1,0,'F','0','0','album:photo:upload','#','admin',NOW(),''),
(3030,'轨迹查询',3003,1,'','','',1,0,'F','0','0','album:track:query','#','admin',NOW(),''),
(3031,'轨迹生成',3003,2,'','','',1,0,'F','0','0','album:track:generate','#','admin',NOW(),''),
(3032,'轨迹修改',3003,3,'','','',1,0,'F','0','0','album:track:edit','#','admin',NOW(),''),
(3033,'轨迹删除',3003,4,'','','',1,0,'F','0','0','album:track:remove','#','admin',NOW(),''),
(3040,'扫描查询',3004,1,'','','',1,0,'F','0','0','album:scan:query','#','admin',NOW(),''),
(3041,'扫描新增',3004,2,'','','',1,0,'F','0','0','album:scan:add','#','admin',NOW(),''),
(3042,'扫描修改',3004,3,'','','',1,0,'F','0','0','album:scan:edit','#','admin',NOW(),''),
(3043,'扫描删除',3004,4,'','','',1,0,'F','0','0','album:scan:remove','#','admin',NOW(),''),
(3044,'执行扫描',3004,5,'','','',1,0,'F','0','0','album:scan:run','#','admin',NOW(),''),
(3050,'云盘配置查询',3005,1,'','','',1,0,'F','0','0','album:aliyun:query','#','admin',NOW(),''),
(3051,'云盘配置修改',3005,2,'','','',1,0,'F','0','0','album:aliyun:edit','#','admin',NOW(),''),
(3052,'云盘立即同步',3005,3,'','','',1,0,'F','0','0','album:aliyun:run','#','admin',NOW(),'');

INSERT INTO sys_role_menu(role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id BETWEEN 3000 AND 3099;

-- 示例相册
INSERT INTO biz_album(album_name, album_desc, is_public, photo_count, sort_order, create_by, create_time, remark)
VALUES ('默认相册', '系统初始化示例相册，可在后台修改', 1, 0, 0, 'admin', NOW(), 'init');
