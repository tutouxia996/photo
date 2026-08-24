-- =====================================================================
-- 相册网站数据库导出（仅若依 sys 配置数据，不含业务数据）
-- 库名: photo
-- 导出时间: 2026-08-25
-- 说明:
--   - 含全部表结构（biz_* 为空表）
--   - 含 sys_* 配置数据（用户/角色/菜单/字典等）
--   - 不含 sys_oper_log / sys_logininfor / sys_job_log 日志
--   - 不含 biz_* 业务数据
-- 变更（2026-08-25）:
--   - biz_aliyun_drive_setting.remark 改为 TEXT（多选云盘相册 JSON）
--   - 修正 biz_photo / biz_photo_draw / biz_photo_draw_preset 乱码注释
--   - biz 表 AUTO_INCREMENT 重置为 1（新库初始化）
-- 已有库升级多相册: 执行 doc/db/album_aliyun_remark_text.sql
-- 用法: mysql -u用户 -p < photo_sys_only_export.sql
-- =====================================================================

CREATE DATABASE IF NOT EXISTS `photo` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `photo`;
-- MySQL dump 10.13  Distrib 5.7.29, for Win64 (x86_64)
--
-- Host: localhost    Database: photo
-- ------------------------------------------------------
-- Server version	5.7.29-log

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `biz_album`
--

DROP TABLE IF EXISTS `biz_album`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `biz_album` (
  `album_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '相册ID',
  `album_name` varchar(100) NOT NULL COMMENT '相册名称',
  `album_desc` varchar(500) DEFAULT NULL COMMENT '相册描述',
  `cover_url` varchar(255) DEFAULT NULL COMMENT '封面图片访问地址',
  `is_public` tinyint(4) NOT NULL DEFAULT '1' COMMENT '是否公开：0私有 1公开',
  `photo_count` int(11) NOT NULL DEFAULT '0' COMMENT '照片数量',
  `start_time` datetime DEFAULT NULL COMMENT '最早拍摄时间',
  `end_time` datetime DEFAULT NULL COMMENT '最晚拍摄时间',
  `location_summary` varchar(200) DEFAULT NULL COMMENT '地点概要',
  `sort_order` int(11) NOT NULL DEFAULT '0' COMMENT '排序号',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '0未删除 1已删除 2回收站',
  PRIMARY KEY (`album_id`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='相册表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_aliyun_drive_setting`
--

DROP TABLE IF EXISTS `biz_aliyun_drive_setting`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `biz_aliyun_drive_setting` (
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_photo`
--

DROP TABLE IF EXISTS `biz_photo`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `biz_photo` (
  `photo_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '图片ID',
  `album_id` bigint(20) NOT NULL COMMENT '所属相册ID',
  `file_name` varchar(200) NOT NULL COMMENT '原始文件名',
  `file_path` varchar(500) NOT NULL COMMENT '物理文件绝对路径',
  `file_url` varchar(255) NOT NULL COMMENT '访问相对URL',
  `thumb_url` varchar(255) DEFAULT NULL COMMENT '缩略图URL',
  `file_size` bigint(20) NOT NULL DEFAULT '0' COMMENT '文件大小（字节）',
  `file_type` tinyint(4) NOT NULL DEFAULT '1' COMMENT '文件类型：1图片 2视频',
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
  `sort_order` int(11) NOT NULL DEFAULT '0' COMMENT '排序号',
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
  `deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '0未删除 1已删除 2回收站',
  PRIMARY KEY (`photo_id`),
  KEY `idx_album_id` (`album_id`),
  KEY `idx_shoot_time` (`shoot_time`),
  KEY `idx_md5` (`md5`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='图片表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_photo_draw`
--

DROP TABLE IF EXISTS `biz_photo_draw`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='照片AI出图记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_photo_draw_preset`
--

DROP TABLE IF EXISTS `biz_photo_draw_preset`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `biz_photo_draw_preset` (
  `preset_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '预设ID',
  `preset_key` varchar(64) NOT NULL COMMENT '预设标识（出图请求 preset）',
  `label` varchar(100) NOT NULL COMMENT '显示名称',
  `panel_prompt` text NOT NULL COMMENT '万相图生图风格描述/prompt',
  `panel_size` varchar(32) NOT NULL DEFAULT '960*1280' COMMENT '出图尺寸',
  `layout` varchar(64) NOT NULL DEFAULT 'FULL_CANVAS' COMMENT '布局 PhotoDrawLayout',
  `grade_photo` tinyint(4) NOT NULL DEFAULT '1' COMMENT '上下双联时是否对原图轻调色：0否 1是',
  `sort_order` int(11) NOT NULL DEFAULT '0' COMMENT '排序号',
  `enabled` tinyint(4) NOT NULL DEFAULT '1' COMMENT '是否启用：0禁用 1启用',
  `source` varchar(32) NOT NULL DEFAULT 'manual' COMMENT '来源：builtin/manual/skill_import',
  `skill_raw` mediumtext COMMENT '导入的 skill 原文',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`preset_id`),
  UNIQUE KEY `uk_preset_key` (`preset_key`),
  KEY `idx_enabled_sort` (`enabled`,`sort_order`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='AI出图预设风格';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_scan_log`
--

DROP TABLE IF EXISTS `biz_scan_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `biz_scan_log` (
  `log_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `path_id` bigint(20) NOT NULL COMMENT '扫描目录ID',
  `scan_type` tinyint(4) NOT NULL DEFAULT '1' COMMENT '扫描类型：1增量 2全量',
  `total_count` int(11) NOT NULL DEFAULT '0' COMMENT '扫描文件总数',
  `new_count` int(11) NOT NULL DEFAULT '0' COMMENT '新增数量',
  `skip_count` int(11) NOT NULL DEFAULT '0' COMMENT '跳过数量',
  `fail_count` int(11) NOT NULL DEFAULT '0' COMMENT '失败数量',
  `status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '状态：0进行中 1成功 2失败',
  `message` varchar(1000) DEFAULT NULL COMMENT '结果摘要/异常信息',
  `start_time` datetime DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`log_id`),
  KEY `idx_scan_log_path_id` (`path_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='扫描记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_scan_path`
--

DROP TABLE IF EXISTS `biz_scan_path`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `biz_scan_path` (
  `path_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '配置ID',
  `path_name` varchar(100) NOT NULL COMMENT '目录名称',
  `local_path` varchar(500) NOT NULL COMMENT '本地磁盘绝对路径',
  `default_album_id` bigint(20) DEFAULT NULL COMMENT '默认绑定相册ID',
  `scan_cron` varchar(50) DEFAULT NULL COMMENT '定时扫描Cron',
  `last_scan_time` datetime DEFAULT NULL COMMENT '上次扫描时间',
  `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '状态：0禁用 1启用',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '0未删除 1已删除',
  PRIMARY KEY (`path_id`),
  KEY `idx_scan_path_deleted` (`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='扫描目录配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_track`
--

DROP TABLE IF EXISTS `biz_track`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `biz_track` (
  `track_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '轨迹ID',
  `album_id` bigint(20) NOT NULL COMMENT '所属相册ID',
  `track_name` varchar(100) NOT NULL COMMENT '轨迹名称',
  `start_time` datetime DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `total_distance` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '总距离（公里）',
  `total_duration` bigint(20) NOT NULL DEFAULT '0' COMMENT '总时长（秒）',
  `point_count` int(11) NOT NULL DEFAULT '0' COMMENT '轨迹点数量',
  `track_color` varchar(20) NOT NULL DEFAULT '#3B82F6' COMMENT '轨迹线颜色',
  `is_public` tinyint(4) NOT NULL DEFAULT '1' COMMENT '是否前台展示',
  `enabled` tinyint(4) NOT NULL DEFAULT '1' COMMENT '是否启用轨迹：0关闭（不生成/地图不显示）1开启',
  `gpx_enabled` tinyint(4) NOT NULL DEFAULT '1' COMMENT '是否在地图启用GPX线路：0否 1是',
  `source_type` varchar(16) DEFAULT 'photo' COMMENT '来源：photo/gpx/mixed',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '0未删除 1已删除',
  PRIMARY KEY (`track_id`),
  KEY `idx_track_album_id` (`album_id`),
  KEY `idx_track_deleted` (`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='轨迹表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_track_gpx_file`
--

DROP TABLE IF EXISTS `biz_track_gpx_file`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `biz_track_gpx_file` (
  `gpx_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'GPX文件ID',
  `album_id` bigint(20) NOT NULL COMMENT '所属相册ID',
  `file_name` varchar(200) NOT NULL COMMENT '原始文件名',
  `storage_path` varchar(500) NOT NULL COMMENT '服务器存储路径',
  `point_count` int(11) NOT NULL DEFAULT '0' COMMENT '轨迹点数',
  `distance_km` decimal(10,3) NOT NULL DEFAULT '0.000' COMMENT 'GPX里程（公里）',
  `travel_mode` varchar(32) DEFAULT NULL COMMENT '出行方式：hsr/train/bus/metro/walk/drive/bike/flight/other',
  `start_time` datetime DEFAULT NULL COMMENT 'GPX开始时间',
  `end_time` datetime DEFAULT NULL COMMENT 'GPX结束时间',
  `enabled` tinyint(4) NOT NULL DEFAULT '1' COMMENT '是否参与生成：0否 1是',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '0未删除 1已删除',
  PRIMARY KEY (`gpx_id`),
  KEY `idx_gpx_album_id` (`album_id`),
  KEY `idx_gpx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='轨迹GPX文件';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_track_point`
--

DROP TABLE IF EXISTS `biz_track_point`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `biz_track_point` (
  `point_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '点位ID',
  `track_id` bigint(20) NOT NULL COMMENT '所属轨迹ID',
  `photo_id` bigint(20) DEFAULT NULL COMMENT '关联图片ID',
  `latitude` decimal(10,7) NOT NULL COMMENT '纬度',
  `longitude` decimal(10,7) NOT NULL COMMENT '经度',
  `point_time` datetime DEFAULT NULL COMMENT '点位时间',
  `sequence` int(11) NOT NULL DEFAULT '0' COMMENT '序号',
  `altitude` decimal(8,2) DEFAULT NULL COMMENT '海拔（米）',
  `description` varchar(500) DEFAULT NULL COMMENT '点位说明（车次、路线等）',
  `travel_mode` varchar(32) DEFAULT NULL COMMENT '到下一站的出行方式',
  `route_path` mediumtext COMMENT '到下一站的真实路线折线 JSON',
  PRIMARY KEY (`point_id`),
  KEY `idx_point_track_id` (`track_id`),
  KEY `idx_point_photo_id` (`photo_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='轨迹点表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gen_table`
--

DROP TABLE IF EXISTS `gen_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `gen_table` (
  `table_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '编号',
  `table_name` varchar(200) DEFAULT '' COMMENT '表名称',
  `table_comment` varchar(500) DEFAULT '' COMMENT '表描述',
  `sub_table_name` varchar(64) DEFAULT NULL COMMENT '关联子表的表名',
  `sub_table_fk_name` varchar(64) DEFAULT NULL COMMENT '子表关联的外键名',
  `class_name` varchar(100) DEFAULT '' COMMENT '实体类名称',
  `tpl_category` varchar(200) DEFAULT 'crud' COMMENT '使用的模板（crud单表操作 tree树表操作）',
  `tpl_web_type` varchar(30) DEFAULT '' COMMENT '前端模板类型（element-ui模版 element-plus模版）',
  `package_name` varchar(100) DEFAULT NULL COMMENT '生成包路径',
  `module_name` varchar(30) DEFAULT NULL COMMENT '生成模块名',
  `business_name` varchar(30) DEFAULT NULL COMMENT '生成业务名',
  `function_name` varchar(50) DEFAULT NULL COMMENT '生成功能名',
  `function_author` varchar(50) DEFAULT NULL COMMENT '生成功能作者',
  `gen_type` char(1) DEFAULT '0' COMMENT '生成代码方式（0zip压缩包 1自定义路径）',
  `gen_path` varchar(200) DEFAULT '/' COMMENT '生成路径（不填默认项目路径）',
  `options` varchar(1000) DEFAULT NULL COMMENT '其它生成选项',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `controller_package_name` varchar(255) DEFAULT NULL COMMENT 'controller生成包路径',
  `controller_module_name` varchar(255) DEFAULT NULL COMMENT 'controller生成模块名',
  `edit_type` char(1) DEFAULT NULL COMMENT '编辑页类型（0抽屉 1弹框）',
  PRIMARY KEY (`table_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代码生成业务表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gen_table_column`
--

DROP TABLE IF EXISTS `gen_table_column`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `gen_table_column` (
  `column_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '编号',
  `table_id` bigint(20) DEFAULT NULL COMMENT '归属表编号',
  `column_name` varchar(200) DEFAULT NULL COMMENT '列名称',
  `column_comment` varchar(500) DEFAULT NULL COMMENT '列描述',
  `column_type` varchar(100) DEFAULT NULL COMMENT '列类型',
  `java_type` varchar(500) DEFAULT NULL COMMENT 'JAVA类型',
  `java_field` varchar(200) DEFAULT NULL COMMENT 'JAVA字段名',
  `is_pk` char(1) DEFAULT NULL COMMENT '是否主键（1是）',
  `is_increment` char(1) DEFAULT NULL COMMENT '是否自增（1是）',
  `is_required` char(1) DEFAULT NULL COMMENT '是否必填（1是）',
  `is_insert` char(1) DEFAULT NULL COMMENT '是否为插入字段（1是）',
  `is_edit` char(1) DEFAULT NULL COMMENT '是否编辑字段（1是）',
  `is_list` char(1) DEFAULT NULL COMMENT '是否列表字段（1是）',
  `is_query` char(1) DEFAULT NULL COMMENT '是否查询字段（1是）',
  `query_type` varchar(200) DEFAULT 'EQ' COMMENT '查询方式（等于、不等于、大于、小于、范围）',
  `html_type` varchar(200) DEFAULT NULL COMMENT '显示类型（文本框、文本域、下拉框、复选框、单选框、日期控件）',
  `dict_type` varchar(200) DEFAULT '' COMMENT '字典类型',
  `sort` int(11) DEFAULT NULL COMMENT '排序',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `max_length` int(11) DEFAULT NULL COMMENT '最大长度',
  `min_length` int(11) DEFAULT NULL COMMENT '最小长度',
  PRIMARY KEY (`column_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代码生成业务表字段';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `qrtz_blob_triggers`
--

DROP TABLE IF EXISTS `qrtz_blob_triggers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `qrtz_blob_triggers` (
  `sched_name` varchar(120) NOT NULL COMMENT '调度名称',
  `trigger_name` varchar(200) NOT NULL COMMENT 'qrtz_triggers表trigger_name的外键',
  `trigger_group` varchar(200) NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
  `blob_data` blob COMMENT '存放持久化Trigger对象',
  PRIMARY KEY (`sched_name`,`trigger_name`,`trigger_group`),
  CONSTRAINT `qrtz_blob_triggers_ibfk_1` FOREIGN KEY (`sched_name`, `trigger_name`, `trigger_group`) REFERENCES `qrtz_triggers` (`sched_name`, `trigger_name`, `trigger_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Blob类型的触发器表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `qrtz_calendars`
--

DROP TABLE IF EXISTS `qrtz_calendars`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `qrtz_calendars` (
  `sched_name` varchar(120) NOT NULL COMMENT '调度名称',
  `calendar_name` varchar(200) NOT NULL COMMENT '日历名称',
  `calendar` blob NOT NULL COMMENT '存放持久化calendar对象',
  PRIMARY KEY (`sched_name`,`calendar_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日历信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `qrtz_cron_triggers`
--

DROP TABLE IF EXISTS `qrtz_cron_triggers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `qrtz_cron_triggers` (
  `sched_name` varchar(120) NOT NULL COMMENT '调度名称',
  `trigger_name` varchar(200) NOT NULL COMMENT 'qrtz_triggers表trigger_name的外键',
  `trigger_group` varchar(200) NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
  `cron_expression` varchar(200) NOT NULL COMMENT 'cron表达式',
  `time_zone_id` varchar(80) DEFAULT NULL COMMENT '时区',
  PRIMARY KEY (`sched_name`,`trigger_name`,`trigger_group`),
  CONSTRAINT `qrtz_cron_triggers_ibfk_1` FOREIGN KEY (`sched_name`, `trigger_name`, `trigger_group`) REFERENCES `qrtz_triggers` (`sched_name`, `trigger_name`, `trigger_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Cron类型的触发器表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `qrtz_fired_triggers`
--

DROP TABLE IF EXISTS `qrtz_fired_triggers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `qrtz_fired_triggers` (
  `sched_name` varchar(120) NOT NULL COMMENT '调度名称',
  `entry_id` varchar(95) NOT NULL COMMENT '调度器实例id',
  `trigger_name` varchar(200) NOT NULL COMMENT 'qrtz_triggers表trigger_name的外键',
  `trigger_group` varchar(200) NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
  `instance_name` varchar(200) NOT NULL COMMENT '调度器实例名',
  `fired_time` bigint(20) NOT NULL COMMENT '触发的时间',
  `sched_time` bigint(20) NOT NULL COMMENT '定时器制定的时间',
  `priority` int(11) NOT NULL COMMENT '优先级',
  `state` varchar(16) NOT NULL COMMENT '状态',
  `job_name` varchar(200) DEFAULT NULL COMMENT '任务名称',
  `job_group` varchar(200) DEFAULT NULL COMMENT '任务组名',
  `is_nonconcurrent` varchar(1) DEFAULT NULL COMMENT '是否并发',
  `requests_recovery` varchar(1) DEFAULT NULL COMMENT '是否接受恢复执行',
  PRIMARY KEY (`sched_name`,`entry_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='已触发的触发器表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `qrtz_job_details`
--

DROP TABLE IF EXISTS `qrtz_job_details`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `qrtz_job_details` (
  `sched_name` varchar(120) NOT NULL COMMENT '调度名称',
  `job_name` varchar(200) NOT NULL COMMENT '任务名称',
  `job_group` varchar(200) NOT NULL COMMENT '任务组名',
  `description` varchar(250) DEFAULT NULL COMMENT '相关介绍',
  `job_class_name` varchar(250) NOT NULL COMMENT '执行任务类名称',
  `is_durable` varchar(1) NOT NULL COMMENT '是否持久化',
  `is_nonconcurrent` varchar(1) NOT NULL COMMENT '是否并发',
  `is_update_data` varchar(1) NOT NULL COMMENT '是否更新数据',
  `requests_recovery` varchar(1) NOT NULL COMMENT '是否接受恢复执行',
  `job_data` blob COMMENT '存放持久化job对象',
  PRIMARY KEY (`sched_name`,`job_name`,`job_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务详细信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `qrtz_locks`
--

DROP TABLE IF EXISTS `qrtz_locks`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `qrtz_locks` (
  `sched_name` varchar(120) NOT NULL COMMENT '调度名称',
  `lock_name` varchar(40) NOT NULL COMMENT '悲观锁名称',
  PRIMARY KEY (`sched_name`,`lock_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='存储的悲观锁信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `qrtz_paused_trigger_grps`
--

DROP TABLE IF EXISTS `qrtz_paused_trigger_grps`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `qrtz_paused_trigger_grps` (
  `sched_name` varchar(120) NOT NULL COMMENT '调度名称',
  `trigger_group` varchar(200) NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
  PRIMARY KEY (`sched_name`,`trigger_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='暂停的触发器表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `qrtz_scheduler_state`
--

DROP TABLE IF EXISTS `qrtz_scheduler_state`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `qrtz_scheduler_state` (
  `sched_name` varchar(120) NOT NULL COMMENT '调度名称',
  `instance_name` varchar(200) NOT NULL COMMENT '实例名称',
  `last_checkin_time` bigint(20) NOT NULL COMMENT '上次检查时间',
  `checkin_interval` bigint(20) NOT NULL COMMENT '检查间隔时间',
  PRIMARY KEY (`sched_name`,`instance_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='调度器状态表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `qrtz_simple_triggers`
--

DROP TABLE IF EXISTS `qrtz_simple_triggers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `qrtz_simple_triggers` (
  `sched_name` varchar(120) NOT NULL COMMENT '调度名称',
  `trigger_name` varchar(200) NOT NULL COMMENT 'qrtz_triggers表trigger_name的外键',
  `trigger_group` varchar(200) NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
  `repeat_count` bigint(20) NOT NULL COMMENT '重复的次数统计',
  `repeat_interval` bigint(20) NOT NULL COMMENT '重复的间隔时间',
  `times_triggered` bigint(20) NOT NULL COMMENT '已经触发的次数',
  PRIMARY KEY (`sched_name`,`trigger_name`,`trigger_group`),
  CONSTRAINT `qrtz_simple_triggers_ibfk_1` FOREIGN KEY (`sched_name`, `trigger_name`, `trigger_group`) REFERENCES `qrtz_triggers` (`sched_name`, `trigger_name`, `trigger_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='简单触发器的信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `qrtz_simprop_triggers`
--

DROP TABLE IF EXISTS `qrtz_simprop_triggers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `qrtz_simprop_triggers` (
  `sched_name` varchar(120) NOT NULL COMMENT '调度名称',
  `trigger_name` varchar(200) NOT NULL COMMENT 'qrtz_triggers表trigger_name的外键',
  `trigger_group` varchar(200) NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
  `str_prop_1` varchar(512) DEFAULT NULL COMMENT 'String类型的trigger的第一个参数',
  `str_prop_2` varchar(512) DEFAULT NULL COMMENT 'String类型的trigger的第二个参数',
  `str_prop_3` varchar(512) DEFAULT NULL COMMENT 'String类型的trigger的第三个参数',
  `int_prop_1` int(11) DEFAULT NULL COMMENT 'int类型的trigger的第一个参数',
  `int_prop_2` int(11) DEFAULT NULL COMMENT 'int类型的trigger的第二个参数',
  `long_prop_1` bigint(20) DEFAULT NULL COMMENT 'long类型的trigger的第一个参数',
  `long_prop_2` bigint(20) DEFAULT NULL COMMENT 'long类型的trigger的第二个参数',
  `dec_prop_1` decimal(13,4) DEFAULT NULL COMMENT 'decimal类型的trigger的第一个参数',
  `dec_prop_2` decimal(13,4) DEFAULT NULL COMMENT 'decimal类型的trigger的第二个参数',
  `bool_prop_1` varchar(1) DEFAULT NULL COMMENT 'Boolean类型的trigger的第一个参数',
  `bool_prop_2` varchar(1) DEFAULT NULL COMMENT 'Boolean类型的trigger的第二个参数',
  PRIMARY KEY (`sched_name`,`trigger_name`,`trigger_group`),
  CONSTRAINT `qrtz_simprop_triggers_ibfk_1` FOREIGN KEY (`sched_name`, `trigger_name`, `trigger_group`) REFERENCES `qrtz_triggers` (`sched_name`, `trigger_name`, `trigger_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='同步机制的行锁表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `qrtz_triggers`
--

DROP TABLE IF EXISTS `qrtz_triggers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `qrtz_triggers` (
  `sched_name` varchar(120) NOT NULL COMMENT '调度名称',
  `trigger_name` varchar(200) NOT NULL COMMENT '触发器的名字',
  `trigger_group` varchar(200) NOT NULL COMMENT '触发器所属组的名字',
  `job_name` varchar(200) NOT NULL COMMENT 'qrtz_job_details表job_name的外键',
  `job_group` varchar(200) NOT NULL COMMENT 'qrtz_job_details表job_group的外键',
  `description` varchar(250) DEFAULT NULL COMMENT '相关介绍',
  `next_fire_time` bigint(20) DEFAULT NULL COMMENT '上一次触发时间（毫秒）',
  `prev_fire_time` bigint(20) DEFAULT NULL COMMENT '下一次触发时间（默认为-1表示不触发）',
  `priority` int(11) DEFAULT NULL COMMENT '优先级',
  `trigger_state` varchar(16) NOT NULL COMMENT '触发器状态',
  `trigger_type` varchar(8) NOT NULL COMMENT '触发器的类型',
  `start_time` bigint(20) NOT NULL COMMENT '开始时间',
  `end_time` bigint(20) DEFAULT NULL COMMENT '结束时间',
  `calendar_name` varchar(200) DEFAULT NULL COMMENT '日程表名称',
  `misfire_instr` smallint(6) DEFAULT NULL COMMENT '补偿执行的策略',
  `job_data` blob COMMENT '存放持久化job对象',
  PRIMARY KEY (`sched_name`,`trigger_name`,`trigger_group`),
  KEY `sched_name` (`sched_name`,`job_name`,`job_group`),
  CONSTRAINT `qrtz_triggers_ibfk_1` FOREIGN KEY (`sched_name`, `job_name`, `job_group`) REFERENCES `qrtz_job_details` (`sched_name`, `job_name`, `job_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='触发器详细信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_config`
--

DROP TABLE IF EXISTS `sys_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `sys_config` (
  `id` int(11) NOT NULL AUTO_INCREMENT COMMENT '参数主键',
  `config_name` varchar(100) DEFAULT '' COMMENT '参数名称',
  `config_key` varchar(100) DEFAULT '' COMMENT '参数键名',
  `config_value` varchar(500) DEFAULT '' COMMENT '参数键值',
  `config_type` char(1) DEFAULT 'N' COMMENT '系统内置（Y是 N否）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `del_flag` char(1) DEFAULT '0' COMMENT '删除标志（0代表存在 1代表删除）',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COMMENT='参数配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_dept`
--

DROP TABLE IF EXISTS `sys_dept`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `sys_dept` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '机构id',
  `parent_id` bigint(20) DEFAULT '0' COMMENT '父机构id',
  `ancestors` varchar(50) DEFAULT '' COMMENT '祖级列表',
  `dept_name` varchar(30) DEFAULT '' COMMENT '机构名称',
  `order_num` int(11) DEFAULT '0' COMMENT '显示顺序',
  `leader` varchar(20) DEFAULT NULL COMMENT '负责人',
  `phone` varchar(11) DEFAULT NULL COMMENT '联系电话',
  `email` varchar(50) DEFAULT NULL COMMENT '邮箱',
  `status` char(1) DEFAULT '0' COMMENT '机构状态（0正常 1停用）',
  `del_flag` char(1) DEFAULT '0' COMMENT '删除标志（0代表存在 1代表删除）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=101 DEFAULT CHARSET=utf8mb4 COMMENT='机构表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_dict_data`
--

DROP TABLE IF EXISTS `sys_dict_data`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `sys_dict_data` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '字典编码',
  `dict_sort` int(11) DEFAULT '0' COMMENT '字典排序',
  `dict_label` varchar(100) DEFAULT '' COMMENT '字典标签',
  `dict_value` varchar(100) DEFAULT '' COMMENT '字典键值',
  `dict_type` varchar(100) DEFAULT '' COMMENT '字典类型',
  `css_class` varchar(100) DEFAULT NULL COMMENT '样式属性（其他样式扩展）',
  `list_class` varchar(100) DEFAULT NULL COMMENT '表格回显样式',
  `is_default` char(1) DEFAULT 'N' COMMENT '是否默认（Y是 N否）',
  `status` char(1) DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `del_flag` char(1) DEFAULT '0' COMMENT '删除标志（0代表存在 1代表删除）',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=30 DEFAULT CHARSET=utf8mb4 COMMENT='字典数据表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_dict_type`
--

DROP TABLE IF EXISTS `sys_dict_type`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `sys_dict_type` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '字典主键',
  `dict_name` varchar(100) DEFAULT '' COMMENT '字典名称',
  `dict_type` varchar(100) DEFAULT '' COMMENT '字典类型',
  `status` char(1) DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `del_flag` char(1) DEFAULT '0' COMMENT '删除标志（0代表存在 1代表删除）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `dict_type` (`dict_type`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COMMENT='字典类型表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_job`
--

DROP TABLE IF EXISTS `sys_job`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `sys_job` (
  `job_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `job_name` varchar(64) NOT NULL DEFAULT '' COMMENT '任务名称',
  `job_group` varchar(64) NOT NULL DEFAULT 'DEFAULT' COMMENT '任务组名',
  `invoke_target` varchar(500) NOT NULL COMMENT '调用目标字符串',
  `cron_expression` varchar(255) DEFAULT '' COMMENT 'cron执行表达式',
  `misfire_policy` varchar(20) DEFAULT '3' COMMENT '计划执行错误策略（1立即执行 2执行一次 3放弃执行）',
  `concurrent` char(1) DEFAULT '1' COMMENT '是否并发执行（0允许 1禁止）',
  `status` char(1) DEFAULT '0' COMMENT '状态（0正常 1暂停）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT '' COMMENT '备注信息',
  PRIMARY KEY (`job_id`,`job_name`,`job_group`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COMMENT='定时任务调度表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_job_log`
--

DROP TABLE IF EXISTS `sys_job_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `sys_job_log` (
  `job_log_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '任务日志ID',
  `job_name` varchar(64) NOT NULL COMMENT '任务名称',
  `job_group` varchar(64) NOT NULL COMMENT '任务组名',
  `invoke_target` varchar(500) NOT NULL COMMENT '调用目标字符串',
  `job_message` varchar(500) DEFAULT NULL COMMENT '日志信息',
  `status` char(1) DEFAULT '0' COMMENT '执行状态（0正常 1失败）',
  `exception_info` varchar(2000) DEFAULT '' COMMENT '异常信息',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`job_log_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定时任务调度日志表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_logininfor`
--

DROP TABLE IF EXISTS `sys_logininfor`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `sys_logininfor` (
  `info_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '访问ID',
  `user_name` varchar(50) DEFAULT '' COMMENT '用户账号',
  `ipaddr` varchar(128) DEFAULT '' COMMENT '登录IP地址',
  `login_location` varchar(255) DEFAULT '' COMMENT '登录地点',
  `browser` varchar(50) DEFAULT '' COMMENT '浏览器类型',
  `os` varchar(50) DEFAULT '' COMMENT '操作系统',
  `status` char(1) DEFAULT '0' COMMENT '登录状态（0成功 1失败）',
  `msg` varchar(255) DEFAULT '' COMMENT '提示消息',
  `login_time` datetime DEFAULT NULL COMMENT '访问时间',
  PRIMARY KEY (`info_id`),
  KEY `idx_sys_logininfor_s` (`status`),
  KEY `idx_sys_logininfor_lt` (`login_time`)
) ENGINE=InnoDB AUTO_INCREMENT=2091351132922146819 DEFAULT CHARSET=utf8mb4 COMMENT='系统访问记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_menu`
--

DROP TABLE IF EXISTS `sys_menu`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `sys_menu` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
  `menu_name` varchar(50) NOT NULL COMMENT '菜单名称',
  `parent_id` bigint(20) DEFAULT '0' COMMENT '父菜单ID',
  `order_num` int(11) DEFAULT '0' COMMENT '显示顺序',
  `path` varchar(200) DEFAULT '' COMMENT '路由地址',
  `component` varchar(255) DEFAULT NULL COMMENT '组件路径',
  `query` varchar(255) DEFAULT NULL COMMENT '路由参数',
  `is_frame` int(11) DEFAULT '1' COMMENT '是否为外链（0是 1否）',
  `is_cache` int(11) DEFAULT '0' COMMENT '是否缓存（0缓存 1不缓存）',
  `menu_type` char(1) DEFAULT '' COMMENT '菜单类型（M目录 C菜单 F按钮）',
  `visible` char(1) DEFAULT '0' COMMENT '菜单状态（0显示 1隐藏）',
  `status` char(1) DEFAULT '0' COMMENT '菜单状态（0正常 1停用）',
  `perms` varchar(100) DEFAULT NULL COMMENT '权限标识',
  `icon` varchar(100) DEFAULT '#' COMMENT '菜单图标',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT '' COMMENT '备注',
  `del_flag` char(1) DEFAULT '0' COMMENT '删除标志（0代表存在 1代表删除）',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3064 DEFAULT CHARSET=utf8mb4 COMMENT='菜单权限表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_notice`
--

DROP TABLE IF EXISTS `sys_notice`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `sys_notice` (
  `id` int(11) NOT NULL AUTO_INCREMENT COMMENT '公告ID',
  `notice_title` varchar(50) NOT NULL COMMENT '公告标题',
  `notice_type` char(1) NOT NULL COMMENT '公告类型（1通知 2公告）',
  `notice_content` longblob COMMENT '公告内容',
  `status` char(1) DEFAULT '0' COMMENT '公告状态（0正常 1关闭）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `del_flag` char(1) DEFAULT '0' COMMENT '删除标志（0代表存在 1代表删除）',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知公告表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_oper_log`
--

DROP TABLE IF EXISTS `sys_oper_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `sys_oper_log` (
  `oper_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '日志主键',
  `title` varchar(50) DEFAULT '' COMMENT '模块标题',
  `business_type` int(11) DEFAULT '0' COMMENT '业务类型（0其它 1新增 2修改 3删除）',
  `method` varchar(100) DEFAULT '' COMMENT '方法名称',
  `request_method` varchar(10) DEFAULT '' COMMENT '请求方式',
  `operator_type` int(11) DEFAULT '0' COMMENT '操作类别（0其它 1后台用户 2手机端用户）',
  `oper_name` varchar(50) DEFAULT '' COMMENT '操作人员',
  `dept_name` varchar(50) DEFAULT '' COMMENT '机构名称',
  `oper_url` varchar(255) DEFAULT '' COMMENT '请求URL',
  `oper_ip` varchar(128) DEFAULT '' COMMENT '主机地址',
  `oper_location` varchar(255) DEFAULT '' COMMENT '操作地点',
  `oper_param` varchar(2000) DEFAULT '' COMMENT '请求参数',
  `json_result` varchar(2000) DEFAULT '' COMMENT '返回参数',
  `status` int(11) DEFAULT '0' COMMENT '操作状态（0正常 1异常）',
  `error_msg` varchar(2000) DEFAULT '' COMMENT '错误消息',
  `oper_time` datetime DEFAULT NULL COMMENT '操作时间',
  `cost_time` bigint(20) DEFAULT '0' COMMENT '消耗时间',
  PRIMARY KEY (`oper_id`),
  KEY `idx_sys_oper_log_bt` (`business_type`),
  KEY `idx_sys_oper_log_s` (`status`),
  KEY `idx_sys_oper_log_ot` (`oper_time`)
) ENGINE=InnoDB AUTO_INCREMENT=2091357733645111298 DEFAULT CHARSET=utf8mb4 COMMENT='操作日志记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_post`
--

DROP TABLE IF EXISTS `sys_post`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `sys_post` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '岗位ID',
  `post_code` varchar(64) NOT NULL COMMENT '岗位编码',
  `post_name` varchar(50) NOT NULL COMMENT '岗位名称',
  `post_sort` int(11) NOT NULL COMMENT '显示顺序',
  `status` char(1) NOT NULL COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `del_flag` char(1) DEFAULT '0' COMMENT '删除标志（0代表存在 1代表删除）',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='岗位信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_role`
--

DROP TABLE IF EXISTS `sys_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `sys_role` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  `dept_id` bigint(20) DEFAULT NULL COMMENT '机构ID',
  `role_name` varchar(30) NOT NULL COMMENT '角色名称',
  `role_key` varchar(100) NOT NULL COMMENT '角色权限字符串',
  `role_sort` int(11) NOT NULL COMMENT '显示顺序',
  `data_scope` char(1) DEFAULT '1' COMMENT '数据范围（1：全部数据权限 2：自定数据权限 3：本机构数据权限 4：本机构及以下数据权限）',
  `menu_check_strictly` tinyint(1) DEFAULT '1' COMMENT '菜单树选择项是否关联显示',
  `dept_check_strictly` tinyint(1) DEFAULT '1' COMMENT '机构树选择项是否关联显示',
  `status` char(1) NOT NULL COMMENT '角色状态（0正常 1停用）',
  `del_flag` char(1) DEFAULT '0' COMMENT '删除标志（0代表存在 1代表删除）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COMMENT='角色信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_role_dept`
--

DROP TABLE IF EXISTS `sys_role_dept`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `sys_role_dept` (
  `role_id` bigint(20) NOT NULL COMMENT '角色ID',
  `dept_id` bigint(20) NOT NULL COMMENT '机构ID',
  PRIMARY KEY (`role_id`,`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色和机构关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_role_menu`
--

DROP TABLE IF EXISTS `sys_role_menu`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `sys_role_menu` (
  `role_id` bigint(20) NOT NULL COMMENT '角色ID',
  `menu_id` bigint(20) NOT NULL COMMENT '菜单ID',
  PRIMARY KEY (`role_id`,`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色和菜单关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_user`
--

DROP TABLE IF EXISTS `sys_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `sys_user` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `dept_id` bigint(20) DEFAULT NULL COMMENT '机构ID',
  `user_name` varchar(30) NOT NULL COMMENT '用户账号',
  `nick_name` varchar(30) NOT NULL COMMENT '用户昵称',
  `user_type` varchar(2) DEFAULT '00' COMMENT '用户类型（00系统用户）',
  `email` varchar(50) DEFAULT '' COMMENT '用户邮箱',
  `phonenumber` varchar(11) DEFAULT '' COMMENT '手机号码',
  `sex` char(1) DEFAULT '0' COMMENT '用户性别（0男 1女 2未知）',
  `avatar` varchar(100) DEFAULT '' COMMENT '头像地址',
  `password` varchar(100) DEFAULT '' COMMENT '密码',
  `status` char(1) DEFAULT '0' COMMENT '帐号状态（0正常 1停用）',
  `del_flag` char(1) DEFAULT '0' COMMENT '删除标志（0代表存在 1代表删除）',
  `login_ip` varchar(128) DEFAULT '' COMMENT '最后登录IP',
  `login_date` datetime DEFAULT NULL COMMENT '最后登录时间',
  `pwd_update_time` datetime DEFAULT NULL COMMENT '密码修改时间',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COMMENT='用户信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_user_post`
--

DROP TABLE IF EXISTS `sys_user_post`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `sys_user_post` (
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `post_id` bigint(20) NOT NULL COMMENT '岗位ID',
  PRIMARY KEY (`user_id`,`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户与岗位关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_user_role`
--

DROP TABLE IF EXISTS `sys_user_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `sys_user_role` (
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `role_id` bigint(20) NOT NULL COMMENT '角色ID',
  PRIMARY KEY (`user_id`,`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户和角色关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping routines for database 'photo'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-08-23 17:20:12

-- ===================== sys 配置数据 =====================
-- MySQL dump 10.13  Distrib 5.7.29, for Win64 (x86_64)
--
-- Host: localhost    Database: photo
-- ------------------------------------------------------
-- Server version	5.7.29-log

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Dumping data for table `sys_config`
--

LOCK TABLES `sys_config` WRITE;
/*!40000 ALTER TABLE `sys_config` DISABLE KEYS */;
INSERT INTO `sys_config` VALUES (1,'主框架页-默认皮肤样式名称','sys.index.skinName','skin-blue','Y','admin','2024-06-21 10:57:33','',NULL,'蓝色 skin-blue、绿色 skin-green、紫色 skin-purple、红色 skin-red、黄色 skin-yellow','0'),(2,'用户管理-账号初始密码','sys.user.initPassword','123456','Y','admin','2024-06-21 10:57:33','',NULL,'初始化密码 123456','0'),(3,'主框架页-侧边栏主题','sys.index.sideTheme','theme-dark','Y','admin','2024-06-21 10:57:33','',NULL,'深色主题theme-dark，浅色主题theme-light','0'),(4,'账号自助-验证码开关','sys.account.captchaEnabled','false','Y','admin','2024-06-21 10:57:33','admin','2024-07-19 10:28:25','是否开启验证码功能（true开启，false关闭）','0'),(5,'账号自助-是否开启用户注册功能','sys.account.registerUser','false','Y','admin','2024-06-21 10:57:33','',NULL,'是否开启注册用户功能（true开启，false关闭）','0'),(6,'用户登录-黑名单列表','sys.login.blackIPList','','Y','admin','2024-06-21 10:57:33','',NULL,'设置登录IP黑名单限制，多个匹配项以;分隔，支持匹配（*通配、网段）','0'),(7,'密码策略-密码过期天数','sys.password.expireDays','90','Y','admin','2026-06-17 18:00:00','',NULL,'密码过期天数，超过需强制修改，0或空表示不限制','0');
/*!40000 ALTER TABLE `sys_config` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `sys_dept`
--

LOCK TABLES `sys_dept` WRITE;
/*!40000 ALTER TABLE `sys_dept` DISABLE KEYS */;
INSERT INTO `sys_dept` VALUES (100,0,'0','晟谦科技',0,NULL,'15888888888',NULL,'0','0','admin','2024-06-21 10:57:31','admin','2024-06-21 14:27:16');
/*!40000 ALTER TABLE `sys_dept` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `sys_dict_data`
--

LOCK TABLES `sys_dict_data` WRITE;
/*!40000 ALTER TABLE `sys_dict_data` DISABLE KEYS */;
INSERT INTO `sys_dict_data` VALUES (4,1,'显示','0','sys_show_hide','','primary','Y','0','admin','2024-06-21 10:57:33','',NULL,'显示菜单','0'),(5,2,'隐藏','1','sys_show_hide','','danger','N','0','admin','2024-06-21 10:57:33','',NULL,'隐藏菜单','0'),(6,1,'正常','0','sys_normal_disable','','primary','Y','0','admin','2024-06-21 10:57:33','',NULL,'正常状态','0'),(7,2,'停用','1','sys_normal_disable','','danger','N','0','admin','2024-06-21 10:57:33','',NULL,'停用状态','0'),(8,1,'正常','0','sys_job_status','','primary','Y','0','admin','2024-06-21 10:57:33','',NULL,'正常状态','0'),(9,2,'暂停','1','sys_job_status','','danger','N','0','admin','2024-06-21 10:57:33','',NULL,'停用状态','0'),(10,1,'默认','DEFAULT','sys_job_group','','','Y','0','admin','2024-06-21 10:57:33','',NULL,'默认分组','0'),(11,2,'系统','SYSTEM','sys_job_group','','','N','0','admin','2024-06-21 10:57:33','',NULL,'系统分组','0'),(12,1,'是','Y','sys_yes_no','','primary','Y','0','admin','2024-06-21 10:57:33','',NULL,'系统默认是','0'),(13,2,'否','N','sys_yes_no','','danger','N','0','admin','2024-06-21 10:57:33','',NULL,'系统默认否','0'),(14,1,'通知','1','sys_notice_type','','warning','Y','0','admin','2024-06-21 10:57:33','',NULL,'通知','0'),(15,2,'公告','2','sys_notice_type','','success','N','0','admin','2024-06-21 10:57:33','',NULL,'公告','0'),(16,1,'正常','0','sys_notice_status','','primary','Y','0','admin','2024-06-21 10:57:33','',NULL,'正常状态','0'),(17,2,'关闭','1','sys_notice_status','','danger','N','0','admin','2024-06-21 10:57:33','',NULL,'关闭状态','0'),(18,99,'其他','0','sys_oper_type','','info','N','0','admin','2024-06-21 10:57:33','',NULL,'其他操作','0'),(19,1,'新增','1','sys_oper_type','','info','N','0','admin','2024-06-21 10:57:33','',NULL,'新增操作','0'),(20,2,'修改','2','sys_oper_type','','info','N','0','admin','2024-06-21 10:57:33','',NULL,'修改操作','0'),(21,3,'删除','3','sys_oper_type','','danger','N','0','admin','2024-06-21 10:57:33','',NULL,'删除操作','0'),(22,4,'授权','4','sys_oper_type','','primary','N','0','admin','2024-06-21 10:57:33','',NULL,'授权操作','0'),(23,5,'导出','5','sys_oper_type','','warning','N','0','admin','2024-06-21 10:57:33','',NULL,'导出操作','0'),(24,6,'导入','6','sys_oper_type','','warning','N','0','admin','2024-06-21 10:57:33','',NULL,'导入操作','0'),(25,7,'强退','7','sys_oper_type','','danger','N','0','admin','2024-06-21 10:57:33','',NULL,'强退操作','0'),(26,8,'生成代码','8','sys_oper_type','','warning','N','0','admin','2024-06-21 10:57:33','',NULL,'生成操作','0'),(27,9,'清空数据','9','sys_oper_type','','danger','N','0','admin','2024-06-21 10:57:33','',NULL,'清空操作','0'),(28,1,'成功','0','sys_common_status','','primary','N','0','admin','2024-06-21 10:57:33','',NULL,'正常状态','0'),(29,2,'失败','1','sys_common_status','','danger','N','0','admin','2024-06-21 10:57:33','',NULL,'停用状态','0');
/*!40000 ALTER TABLE `sys_dict_data` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `sys_dict_type`
--

LOCK TABLES `sys_dict_type` WRITE;
/*!40000 ALTER TABLE `sys_dict_type` DISABLE KEYS */;
INSERT INTO `sys_dict_type` VALUES (1,'用户性别','sys_user_sex','0','admin','2024-06-21 10:57:33','admin','2024-07-25 15:42:02','用户性别列表','0'),(2,'菜单状态','sys_show_hide','0','admin','2024-06-21 10:57:33','',NULL,'菜单状态列表','0'),(3,'系统开关','sys_normal_disable','0','admin','2024-06-21 10:57:33','',NULL,'系统开关列表','0'),(4,'任务状态','sys_job_status','0','admin','2024-06-21 10:57:33','',NULL,'任务状态列表','0'),(5,'任务分组','sys_job_group','0','admin','2024-06-21 10:57:33','',NULL,'任务分组列表','0'),(6,'系统是否','sys_yes_no','0','admin','2024-06-21 10:57:33','',NULL,'系统是否列表','0'),(7,'通知类型','sys_notice_type','0','admin','2024-06-21 10:57:33','',NULL,'通知类型列表','0'),(8,'通知状态','sys_notice_status','0','admin','2024-06-21 10:57:33','',NULL,'通知状态列表','0'),(9,'操作类型','sys_oper_type','0','admin','2024-06-21 10:57:33','',NULL,'操作类型列表','0'),(10,'系统状态','sys_common_status','0','admin','2024-06-21 10:57:33','',NULL,'登录状态列表','0');
/*!40000 ALTER TABLE `sys_dict_type` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `sys_job`
--

LOCK TABLES `sys_job` WRITE;
/*!40000 ALTER TABLE `sys_job` DISABLE KEYS */;
INSERT INTO `sys_job` VALUES (1,'系统默认（无参）','DEFAULT','ryTask.ryNoParams','0/10 * * * * ?','3','1','1','admin','2024-06-21 10:57:33','',NULL,''),(2,'系统默认（有参）','DEFAULT','ryTask.ryParams(\'ry\')','0/15 * * * * ?','3','1','1','admin','2024-06-21 10:57:33','',NULL,''),(3,'系统默认（多参）','DEFAULT','ryTask.ryMultipleParams(\'ry\', true, 2000L, 316.50D, 100)','0/20 * * * * ?','3','1','1','admin','2024-06-21 10:57:33','',NULL,''),(4,'阿里云盘相册同步','DEFAULT','aliyunAlbumSyncTask.syncAndScan()','0 0 2 * * ?','2','0','1','admin','2026-08-18 01:04:47','admin','2026-08-18 21:03:18','');
/*!40000 ALTER TABLE `sys_job` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `sys_menu`
--

LOCK TABLES `sys_menu` WRITE;
/*!40000 ALTER TABLE `sys_menu` DISABLE KEYS */;
INSERT INTO `sys_menu` VALUES (1,'系统管理',0,10,'system',NULL,'',1,0,'M','0','0','','system','admin','2024-06-21 10:57:32','admin','2024-08-09 10:28:26','系统管理目录','0'),(2,'系统监控',0,11,'monitor',NULL,'',1,0,'M','0','0','','monitor','admin','2024-06-21 10:57:32','admin','2024-08-09 10:28:33','系统监控目录','0'),(3,'系统工具',0,1,'tool',NULL,'',1,0,'M','1','0','','tool','admin','2024-06-21 10:57:32','admin','2026-08-22 00:43:39','系统工具目录','0'),(100,'用户管理',1,1,'user','system/user/index','',1,0,'C','0','0','system:user:list','user','admin','2024-06-21 10:57:32','',NULL,'用户管理菜单','0'),(101,'角色管理',1,2,'role','system/role/index','',1,0,'C','0','0','system:role:list','peoples','admin','2024-06-21 10:57:32','',NULL,'角色管理菜单','0'),(102,'菜单管理',1,3,'menu','system/menu/index','',1,0,'C','0','0','system:menu:list','tree-table','admin','2024-06-21 10:57:32','',NULL,'菜单管理菜单','0'),(103,'机构管理',1,4,'dept','system/dept/index','',1,0,'C','0','0','system:dept:list','tree','admin','2024-06-21 10:57:32','admin','2024-07-01 10:01:26','机构管理菜单','0'),(104,'岗位管理',1,5,'post','system/post/index','',1,0,'C','0','0','system:post:list','post','admin','2024-06-21 10:57:32','admin','2025-02-17 11:01:44','岗位管理菜单','0'),(105,'字典管理',1,6,'dict','system/dict/index','',1,0,'C','0','1','system:dict:list','dict','admin','2024-06-21 10:57:32','admin','2025-02-17 11:02:01','字典管理菜单','0'),(106,'参数设置',1,7,'config','system/config/index','',1,0,'C','0','0','system:config:list','edit','admin','2024-06-21 10:57:32','',NULL,'参数设置菜单','0'),(107,'通知公告',1,8,'notice','system/notice/index','',1,0,'C','1','0','system:notice:list','message','admin','2024-06-21 10:57:32','admin','2024-07-17 09:52:07','通知公告菜单','0'),(108,'日志管理',1,9,'log','','',1,0,'M','0','0','','log','admin','2024-06-21 10:57:32','',NULL,'日志管理菜单','0'),(109,'在线用户',2,1,'online','monitor/online/index','',1,0,'C','0','0','monitor:online:list','online','admin','2024-06-21 10:57:32','',NULL,'在线用户菜单','0'),(110,'定时任务',2,2,'job','monitor/job/index','',1,0,'C','0','0','monitor:job:list','job','admin','2024-06-21 10:57:32','',NULL,'定时任务菜单','0'),(111,'数据监控',2,3,'druid','monitor/druid/index','',1,0,'C','0','0','monitor:druid:list','druid','admin','2024-06-21 10:57:32','',NULL,'数据监控菜单','0'),(112,'服务监控',2,4,'server','monitor/server/index','',1,0,'C','0','0','monitor:server:list','server','admin','2024-06-21 10:57:32','',NULL,'服务监控菜单','0'),(113,'缓存监控',2,5,'cache','monitor/cache/index','',1,0,'C','0','0','monitor:cache:list','redis','admin','2024-06-21 10:57:32','',NULL,'缓存监控菜单','0'),(114,'缓存列表',2,6,'cacheList','monitor/cache/list','',1,0,'C','0','0','monitor:cache:list','redis-list','admin','2024-06-21 10:57:32','',NULL,'缓存列表菜单','0'),(115,'表单构建',3,1,'build','tool/build/index','',1,0,'C','0','0','tool:build:list','build','admin','2024-06-21 10:57:32','',NULL,'表单构建菜单','0'),(116,'代码生成',3,2,'gen','tool/gen/index','',1,0,'C','0','0','tool:gen:list','code','admin','2024-06-21 10:57:32','',NULL,'代码生成菜单','0'),(117,'系统接口',3,3,'swagger','tool/swagger/index','',1,0,'C','0','0','tool:swagger:list','swagger','admin','2024-06-21 10:57:32','',NULL,'系统接口菜单','0'),(500,'操作日志',108,1,'operlog','monitor/operlog/index','',1,0,'C','0','0','monitor:operlog:list','form','admin','2024-06-21 10:57:32','',NULL,'操作日志菜单','0'),(501,'登录日志',108,2,'logininfor','monitor/logininfor/index','',1,0,'C','0','0','monitor:logininfor:list','logininfor','admin','2024-06-21 10:57:32','',NULL,'登录日志菜单','0'),(1000,'用户查询',100,1,'','','',1,0,'F','0','0','system:user:query','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1001,'用户新增',100,2,'','','',1,0,'F','0','0','system:user:add','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1002,'用户修改',100,3,'','','',1,0,'F','0','0','system:user:edit','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1003,'用户删除',100,4,'','','',1,0,'F','0','0','system:user:remove','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1004,'用户导出',100,5,'','','',1,0,'F','0','0','system:user:export','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1005,'用户导入',100,6,'','','',1,0,'F','0','0','system:user:import','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1006,'重置密码',100,7,'','','',1,0,'F','0','0','system:user:resetPwd','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1007,'角色查询',101,1,'','','',1,0,'F','0','0','system:role:query','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1008,'角色新增',101,2,'','','',1,0,'F','0','0','system:role:add','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1009,'角色修改',101,3,'','','',1,0,'F','0','0','system:role:edit','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1010,'角色删除',101,4,'','','',1,0,'F','0','0','system:role:remove','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1011,'角色导出',101,5,'','','',1,0,'F','0','0','system:role:export','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1012,'菜单查询',102,1,'','','',1,0,'F','0','0','system:menu:query','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1013,'菜单新增',102,2,'','','',1,0,'F','0','0','system:menu:add','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1014,'菜单修改',102,3,'','','',1,0,'F','0','0','system:menu:edit','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1015,'菜单删除',102,4,'','','',1,0,'F','0','0','system:menu:remove','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1016,'机构查询',103,1,'','','',1,0,'F','0','0','system:dept:query','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1017,'机构新增',103,2,'','','',1,0,'F','0','0','system:dept:add','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1018,'机构修改',103,3,'','','',1,0,'F','0','0','system:dept:edit','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1019,'机构删除',103,4,'','','',1,0,'F','0','0','system:dept:remove','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1020,'岗位查询',104,1,'','','',1,0,'F','0','0','system:post:query','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1021,'岗位新增',104,2,'','','',1,0,'F','0','0','system:post:add','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1022,'岗位修改',104,3,'','','',1,0,'F','0','0','system:post:edit','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1023,'岗位删除',104,4,'','','',1,0,'F','0','0','system:post:remove','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1024,'岗位导出',104,5,'','','',1,0,'F','0','0','system:post:export','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1025,'字典查询',105,1,'#','','',1,0,'F','0','0','system:dict:query','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1026,'字典新增',105,2,'#','','',1,0,'F','0','0','system:dict:add','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1027,'字典修改',105,3,'#','','',1,0,'F','0','0','system:dict:edit','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1028,'字典删除',105,4,'#','','',1,0,'F','0','0','system:dict:remove','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1029,'字典导出',105,5,'#','','',1,0,'F','0','0','system:dict:export','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1030,'参数查询',106,1,'#','','',1,0,'F','0','0','system:config:query','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1031,'参数新增',106,2,'#','','',1,0,'F','0','0','system:config:add','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1032,'参数修改',106,3,'#','','',1,0,'F','0','0','system:config:edit','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1033,'参数删除',106,4,'#','','',1,0,'F','0','0','system:config:remove','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1034,'参数导出',106,5,'#','','',1,0,'F','0','0','system:config:export','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1035,'公告查询',107,1,'#','','',1,0,'F','0','0','system:notice:query','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1036,'公告新增',107,2,'#','','',1,0,'F','0','0','system:notice:add','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1037,'公告修改',107,3,'#','','',1,0,'F','0','0','system:notice:edit','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1038,'公告删除',107,4,'#','','',1,0,'F','0','0','system:notice:remove','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1039,'操作查询',500,1,'#','','',1,0,'F','0','0','monitor:operlog:query','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1040,'操作删除',500,2,'#','','',1,0,'F','0','0','monitor:operlog:remove','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1041,'日志导出',500,3,'#','','',1,0,'F','0','0','monitor:operlog:export','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1042,'登录查询',501,1,'#','','',1,0,'F','0','0','monitor:logininfor:query','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1043,'登录删除',501,2,'#','','',1,0,'F','0','0','monitor:logininfor:remove','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1044,'日志导出',501,3,'#','','',1,0,'F','0','0','monitor:logininfor:export','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1045,'账户解锁',501,4,'#','','',1,0,'F','0','0','monitor:logininfor:unlock','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1046,'在线查询',109,1,'#','','',1,0,'F','0','0','monitor:online:query','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1047,'批量强退',109,2,'#','','',1,0,'F','0','0','monitor:online:batchLogout','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1048,'单条强退',109,3,'#','','',1,0,'F','0','0','monitor:online:forceLogout','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1049,'任务查询',110,1,'#','','',1,0,'F','0','0','monitor:job:query','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1050,'任务新增',110,2,'#','','',1,0,'F','0','0','monitor:job:add','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1051,'任务修改',110,3,'#','','',1,0,'F','0','0','monitor:job:edit','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1052,'任务删除',110,4,'#','','',1,0,'F','0','0','monitor:job:remove','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1053,'状态修改',110,5,'#','','',1,0,'F','0','0','monitor:job:changeStatus','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1054,'任务导出',110,6,'#','','',1,0,'F','0','0','monitor:job:export','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1055,'生成查询',116,1,'#','','',1,0,'F','0','0','tool:gen:query','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1056,'生成修改',116,2,'#','','',1,0,'F','0','0','tool:gen:edit','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1057,'生成删除',116,3,'#','','',1,0,'F','0','0','tool:gen:remove','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1058,'导入代码',116,4,'#','','',1,0,'F','0','0','tool:gen:import','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1059,'预览代码',116,5,'#','','',1,0,'F','0','0','tool:gen:preview','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(1060,'生成代码',116,6,'#','','',1,0,'F','0','0','tool:gen:code','#','admin','2024-06-21 10:57:32','',NULL,'','0'),(2256,'字典管理（新）',1,6,'newDict','system/newDict/index',NULL,1,0,'C','0','0','system:dict:list','dict','admin','2024-07-22 11:06:53','admin','2024-07-24 11:07:11','','0'),(2279,'字典查询',2256,1,'',NULL,NULL,1,0,'F','0','0','system:dict:query','#','admin','2024-07-24 11:07:57','admin','2024-07-24 11:08:09','','0'),(2280,'字典保存',2256,4,'',NULL,NULL,1,0,'F','0','0','system:dict:addDictTypeAndData','#','admin','2024-07-24 11:08:43','ghn','2024-07-24 11:24:31','','0'),(2281,'字典导出',2256,5,'',NULL,NULL,1,0,'F','0','0','system:dict:export','#','admin','2024-07-24 11:09:05','ghn','2024-07-24 11:25:08','','0'),(2282,'字典新增',2256,2,'',NULL,NULL,1,0,'F','0','0','bus:dict:add','#','ghn','2024-07-24 11:25:00','',NULL,'','0'),(2283,'字典数据删除',2256,3,'',NULL,NULL,1,0,'F','0','0','bus:dict:remove','#','ghn','2024-07-24 11:25:35','',NULL,'','0'),(3000,'相册管理',0,2,'album',NULL,'',1,0,'M','0','0','','example','admin','2026-08-08 15:52:53','',NULL,'相册业务目录','0'),(3001,'相册列表',3000,1,'album','album/album/index','',1,0,'C','0','0','album:album:list','documentation','admin','2026-08-08 15:52:53','',NULL,'相册管理菜单','0'),(3002,'图片管理',3000,2,'photo','album/photo/index','',1,0,'C','0','0','album:photo:list','image','admin','2026-08-08 15:52:53','',NULL,'图片管理菜单','0'),(3003,'轨迹管理',3000,3,'track','album/track/index','',1,0,'C','0','0','album:track:list','guide','admin','2026-08-08 15:52:53','',NULL,'轨迹管理菜单','0'),(3004,'磁盘扫描',3000,4,'scan','album/scan/index','',1,0,'C','0','0','album:scan:list','server','admin','2026-08-08 15:52:53','',NULL,'磁盘扫描管理','0'),(3005,'云盘同步',3000,5,'aliyun','album/aliyun/index','',1,0,'C','0','0','album:aliyun:query','upload','admin','2026-08-18 21:01:12','',NULL,'阿里云盘相册同步配置','0'),(3006,'AI预设风格',3000,6,'drawPreset','album/draw-preset/index','',1,0,'C','0','0','album:drawPreset:list','star','admin','2026-08-20 22:21:38','',NULL,'AI出图预设风格管理','0'),(3010,'相册查询',3001,1,'','','',1,0,'F','0','0','album:album:query','#','admin','2026-08-08 15:52:53','',NULL,'','0'),(3011,'相册新增',3001,2,'','','',1,0,'F','0','0','album:album:add','#','admin','2026-08-08 15:52:53','',NULL,'','0'),(3012,'相册修改',3001,3,'','','',1,0,'F','0','0','album:album:edit','#','admin','2026-08-08 15:52:53','',NULL,'','0'),(3013,'相册删除',3001,4,'','','',1,0,'F','0','0','album:album:remove','#','admin','2026-08-08 15:52:53','',NULL,'','0'),(3020,'图片查询',3002,1,'','','',1,0,'F','0','0','album:photo:query','#','admin','2026-08-08 15:52:53','',NULL,'','0'),(3021,'图片新增',3002,2,'','','',1,0,'F','0','0','album:photo:add','#','admin','2026-08-08 15:52:53','',NULL,'','0'),(3022,'图片修改',3002,3,'','','',1,0,'F','0','0','album:photo:edit','#','admin','2026-08-08 15:52:53','',NULL,'','0'),(3023,'图片删除',3002,4,'','','',1,0,'F','0','0','album:photo:remove','#','admin','2026-08-08 15:52:53','',NULL,'','0'),(3024,'图片上传',3002,5,'','','',1,0,'F','0','0','album:photo:upload','#','admin','2026-08-08 15:52:53','',NULL,'','0'),(3030,'轨迹查询',3003,1,'','','',1,0,'F','0','0','album:track:query','#','admin','2026-08-08 15:52:53','',NULL,'','0'),(3031,'轨迹生成',3003,2,'','','',1,0,'F','0','0','album:track:generate','#','admin','2026-08-08 15:52:53','',NULL,'','0'),(3032,'轨迹修改',3003,3,'','','',1,0,'F','0','0','album:track:edit','#','admin','2026-08-08 15:52:53','',NULL,'','0'),(3033,'轨迹删除',3003,4,'','','',1,0,'F','0','0','album:track:remove','#','admin','2026-08-08 15:52:53','',NULL,'','0'),(3040,'扫描查询',3004,1,'','','',1,0,'F','0','0','album:scan:query','#','admin','2026-08-08 15:52:53','',NULL,'','0'),(3041,'扫描新增',3004,2,'','','',1,0,'F','0','0','album:scan:add','#','admin','2026-08-08 15:52:53','',NULL,'','0'),(3042,'扫描修改',3004,3,'','','',1,0,'F','0','0','album:scan:edit','#','admin','2026-08-08 15:52:53','',NULL,'','0'),(3043,'扫描删除',3004,4,'','','',1,0,'F','0','0','album:scan:remove','#','admin','2026-08-08 15:52:53','',NULL,'','0'),(3044,'执行扫描',3004,5,'','','',1,0,'F','0','0','album:scan:run','#','admin','2026-08-08 15:52:53','',NULL,'','0'),(3050,'云盘配置查询',3005,1,'','','',1,0,'F','0','0','album:aliyun:query','#','admin','2026-08-18 21:01:12','',NULL,'','0'),(3051,'云盘配置修改',3005,2,'','','',1,0,'F','0','0','album:aliyun:edit','#','admin','2026-08-18 21:01:12','',NULL,'','0'),(3052,'云盘立即同步',3005,3,'','','',1,0,'F','0','0','album:aliyun:run','#','admin','2026-08-18 21:01:12','',NULL,'','0'),(3060,'预设查询',3006,1,'','','',1,0,'F','0','0','album:drawPreset:query','#','admin','2026-08-20 22:21:38','',NULL,'','0'),(3061,'预设新增',3006,2,'','','',1,0,'F','0','0','album:drawPreset:add','#','admin','2026-08-20 22:21:38','',NULL,'','0'),(3062,'预设修改',3006,3,'','','',1,0,'F','0','0','album:drawPreset:edit','#','admin','2026-08-20 22:21:38','',NULL,'','0'),(3063,'预设删除',3006,4,'','','',1,0,'F','0','0','album:drawPreset:remove','#','admin','2026-08-20 22:21:38','',NULL,'','0');
/*!40000 ALTER TABLE `sys_menu` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `sys_notice`
--

LOCK TABLES `sys_notice` WRITE;
/*!40000 ALTER TABLE `sys_notice` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_notice` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `sys_post`
--

LOCK TABLES `sys_post` WRITE;
/*!40000 ALTER TABLE `sys_post` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_post` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `sys_role`
--

LOCK TABLES `sys_role` WRITE;
/*!40000 ALTER TABLE `sys_role` DISABLE KEYS */;
INSERT INTO `sys_role` VALUES (1,100,'超级管理员','admin',1,'1',1,1,'0','0','admin','2024-06-21 10:57:32','',NULL,'超级管理员');
/*!40000 ALTER TABLE `sys_role` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `sys_role_dept`
--

LOCK TABLES `sys_role_dept` WRITE;
/*!40000 ALTER TABLE `sys_role_dept` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_role_dept` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `sys_role_menu`
--

LOCK TABLES `sys_role_menu` WRITE;
/*!40000 ALTER TABLE `sys_role_menu` DISABLE KEYS */;
INSERT INTO `sys_role_menu` VALUES (1,3000),(1,3001),(1,3002),(1,3003),(1,3004),(1,3005),(1,3006),(1,3010),(1,3011),(1,3012),(1,3013),(1,3020),(1,3021),(1,3022),(1,3023),(1,3024),(1,3030),(1,3031),(1,3032),(1,3033),(1,3040),(1,3041),(1,3042),(1,3043),(1,3044),(1,3050),(1,3051),(1,3052),(1,3060),(1,3061),(1,3062),(1,3063);
/*!40000 ALTER TABLE `sys_role_menu` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `sys_user`
--

LOCK TABLES `sys_user` WRITE;
/*!40000 ALTER TABLE `sys_user` DISABLE KEYS */;
INSERT INTO `sys_user` VALUES (1,103,'admin','超级管理员','00',NULL,'15888888888','1','','{sm3}DOu27VYMzaNrcShzQgbBwg==$2e107930584528e2164da4201a55639f64bbe495b13ba99e554a96daa70488df','0','0','123.123.130.241','2026-08-23 10:25:40',NULL,'admin','2024-06-21 10:57:31','','2026-08-23 10:25:40','管理员');
/*!40000 ALTER TABLE `sys_user` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `sys_user_post`
--

LOCK TABLES `sys_user_post` WRITE;
/*!40000 ALTER TABLE `sys_user_post` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_user_post` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `sys_user_role`
--

LOCK TABLES `sys_user_role` WRITE;
/*!40000 ALTER TABLE `sys_user_role` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_user_role` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping routines for database 'photo'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-08-23 17:20:13
