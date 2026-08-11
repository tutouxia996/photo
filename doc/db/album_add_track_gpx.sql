-- GPX 文件源：与照片 GPS 互补生成轨迹
-- 已有库执行本脚本；新建库见 album_biz_init.sql

CREATE TABLE IF NOT EXISTS `biz_track_gpx_file` (
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

ALTER TABLE `biz_track`
  ADD COLUMN `source_type` varchar(16) DEFAULT 'photo' COMMENT '来源：photo/gpx/mixed' AFTER `enabled`;
