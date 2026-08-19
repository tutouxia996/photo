-- 区分原片与 AI 出图；AI 图记录源照片 ID
-- 已有库执行本脚本

ALTER TABLE `biz_photo`
  ADD COLUMN `origin_type` varchar(20) DEFAULT 'original' COMMENT '来源：original原片 ai_draw AI出图' AFTER `scored_at`,
  ADD COLUMN `source_photo_id` bigint(20) DEFAULT NULL COMMENT 'AI出图时的源照片ID' AFTER `origin_type`;

UPDATE `biz_photo` SET `origin_type` = 'ai_draw' WHERE `remark` LIKE 'AI出图:%';
