-- 日记本模块初始化：表 + 按钮权限（不改动相册原有表字段）
-- 执行：管道导入到 photo 库
-- 还原：doc/db/journal_rollback.sql

USE `photo`;

CREATE TABLE IF NOT EXISTS `biz_journal_book` (
  `book_id`     bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '日记本ID',
  `title`       varchar(128) NOT NULL DEFAULT '相册日记' COMMENT '标题',
  `cover_sub`   varchar(128) DEFAULT 'HOBONICHI STYLE' COMMENT '封面副标题',
  `year`        int(11)      DEFAULT NULL COMMENT '年份',
  `page_count`  int(11)      NOT NULL DEFAULT 1000 COMMENT '总页数',
  `create_by`   varchar(64)  DEFAULT '' COMMENT '创建者（所有者）',
  `create_time` datetime     DEFAULT NULL COMMENT '创建时间',
  `update_by`   varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time` datetime     DEFAULT NULL COMMENT '更新时间',
  `remark`      varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`book_id`),
  KEY `idx_create_by` (`create_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日记本';

CREATE TABLE IF NOT EXISTS `biz_journal_page` (
  `page_id`       bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '页ID',
  `book_id`       bigint(20)   NOT NULL COMMENT '日记本ID',
  `page_no`       int(11)      NOT NULL COMMENT '页码，从1起',
  `content_json`  mediumtext   DEFAULT NULL COMMENT '图层JSON：stroke/textbox/image…',
  `create_by`     varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time`   datetime     DEFAULT NULL COMMENT '创建时间',
  `update_by`     varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time`   datetime     DEFAULT NULL COMMENT '更新时间',
  `remark`        varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`page_id`),
  UNIQUE KEY `uk_book_page` (`book_id`, `page_no`),
  KEY `idx_book_id` (`book_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日记页（懒创建）';

CREATE TABLE IF NOT EXISTS `biz_journal_bookmark` (
  `bookmark_id` bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '书签ID',
  `book_id`     bigint(20)   NOT NULL COMMENT '日记本ID',
  `page_no`     int(11)      NOT NULL COMMENT '页码',
  `title`       varchar(128) DEFAULT NULL COMMENT '书签标题',
  `create_by`   varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time` datetime     DEFAULT NULL COMMENT '创建时间',
  `update_by`   varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time` datetime     DEFAULT NULL COMMENT '更新时间',
  `remark`      varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`bookmark_id`),
  KEY `idx_book_id` (`book_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日记书签（占位）';

-- 权限按钮（日记页在前端 constantRoutes，此处仅挂 API 权限）
INSERT IGNORE INTO `sys_menu` VALUES
(3100,'日记本权限',0,99,'journal',NULL,'',1,0,'M','1','0','','documentation','admin',NOW(),'',NULL,'日记本 API 权限目录（隐藏）','0'),
(3101,'日记本查询',3100,1,'','','',1,0,'F','0','0','journal:book:query','#','admin',NOW(),'',NULL,'','0'),
(3102,'日记本编辑',3100,2,'','','',1,0,'F','0','0','journal:book:edit','#','admin',NOW(),'',NULL,'','0'),
(3110,'日记页查询',3100,3,'','','',1,0,'F','0','0','journal:page:query','#','admin',NOW(),'',NULL,'','0'),
(3111,'日记页编辑',3100,4,'','','',1,0,'F','0','0','journal:page:edit','#','admin',NOW(),'',NULL,'','0');

INSERT IGNORE INTO `sys_role_menu` VALUES
(1,3100),(1,3101),(1,3102),(1,3110),(1,3111);
