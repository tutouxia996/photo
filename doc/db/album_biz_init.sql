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
  `deleted` tinyint(4) NOT NULL DEFAULT 0 COMMENT '0未删除 1已删除 2回收站',
  PRIMARY KEY (`photo_id`),
  KEY `idx_album_id` (`album_id`),
  KEY `idx_shoot_time` (`shoot_time`),
  KEY `idx_md5` (`md5`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图片表';

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
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
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
  PRIMARY KEY (`point_id`),
  KEY `idx_point_track_id` (`track_id`),
  KEY `idx_point_photo_id` (`photo_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='轨迹点表';

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
(3044,'执行扫描',3004,5,'','','',1,0,'F','0','0','album:scan:run','#','admin',NOW(),'');

INSERT INTO sys_role_menu(role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id BETWEEN 3000 AND 3099;

-- 示例相册
INSERT INTO biz_album(album_name, album_desc, is_public, photo_count, sort_order, create_by, create_time, remark)
VALUES ('默认相册', '系统初始化示例相册，可在后台修改', 1, 0, 0, 'admin', NOW(), 'init');
