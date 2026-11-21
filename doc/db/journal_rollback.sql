-- 日记本模块数据库还原：删除新增表与菜单（不改动相册原有表字段）
-- 执行：管道导入到 photo 库

USE `photo`;

-- 角色菜单关联
DELETE FROM `sys_role_menu` WHERE `menu_id` BETWEEN 3100 AND 3199;

-- 菜单 / 按钮权限
DELETE FROM `sys_menu` WHERE `id` BETWEEN 3100 AND 3199;
DELETE FROM `sys_menu` WHERE `perms` LIKE 'journal:%';

-- 业务表（先子后父）
DROP TABLE IF EXISTS `biz_journal_bookmark`;
DROP TABLE IF EXISTS `biz_journal_page`;
DROP TABLE IF EXISTS `biz_journal_book`;
