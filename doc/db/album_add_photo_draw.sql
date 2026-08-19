-- AI 出图记录（万相图生图 + 程序拼版）
-- 已有库执行本脚本

CREATE TABLE IF NOT EXISTS `biz_photo_draw` (
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
