-- 照片出图质量分：合格才允许图生图
-- 已有库执行本脚本

ALTER TABLE `biz_photo`
  ADD COLUMN `aesthetic_score` int(11) DEFAULT NULL COMMENT '出图质量分 0-100' AFTER `remark`,
  ADD COLUMN `score_pass` tinyint(4) DEFAULT NULL COMMENT '是否达到图生图门槛：0否 1是 空未打分' AFTER `aesthetic_score`,
  ADD COLUMN `score_reason` varchar(200) DEFAULT NULL COMMENT '打分摘要' AFTER `score_pass`,
  ADD COLUMN `scored_at` datetime DEFAULT NULL COMMENT '最近打分时间' AFTER `score_reason`;
