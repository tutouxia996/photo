-- 相册分享：已有 photo 库增量执行
-- 全新安装请同步更新 doc/db/photo_sys_only_export.sql

USE `photo`;

CREATE TABLE IF NOT EXISTS `biz_album_share` (
  `share_id`      bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '分享ID',
  `album_id`      bigint(20)   NOT NULL COMMENT '主相册ID（列表归属/兼容单相册）',
  `album_ids`     varchar(1000) DEFAULT NULL COMMENT '分享包含的全部相册ID JSON数组；空表示仅 album_id',
  `share_token`   varchar(64)  NOT NULL COMMENT '分享令牌（URL用，不可猜测）',
  `share_track`   tinyint(4)   NOT NULL DEFAULT 0 COMMENT '是否分享轨迹：0否 1是',
  `media_scope`   varchar(16)  NOT NULL DEFAULT 'all' COMMENT '媒体范围：all全部 image仅图片 video仅视频',
  `select_mode`   varchar(16)  NOT NULL DEFAULT 'all' COMMENT '选择模式：all全部 include仅所选 exclude排除所选',
  `photo_ids`     mediumtext   DEFAULT NULL COMMENT '所选/排除的照片ID JSON数组',
  `status`        tinyint(4)   NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
  `view_count`    bigint(20)   NOT NULL DEFAULT 0 COMMENT '访问次数（访客打开分享页累计）',
  `expire_time`   datetime     DEFAULT NULL COMMENT '过期时间，空表示不过期',
  `create_by`     varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time`   datetime     DEFAULT NULL COMMENT '创建时间',
  `update_by`     varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time`   datetime     DEFAULT NULL COMMENT '更新时间',
  `remark`        varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`share_id`),
  UNIQUE KEY `uk_share_token` (`share_token`),
  KEY `idx_album_id` (`album_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='相册分享链接';
