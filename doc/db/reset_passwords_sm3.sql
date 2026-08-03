-- =====================================================================
-- 生产批量重置 SM3 密码脚本
-- =====================================================================
-- 用途：v1.1.0 国密升级后，将所有现存用户的 BCrypt 密码批量重置为统一
--       SM3 临时密码，并备份原 BCrypt 字段到 backup_pwd_<timestamp> 表，
--       便于回滚校验。所有用户首次登录后必须修改密码。
--
-- 临时密码（明文）：Sq@reset2025
-- SM3 编码值：     {sm3}NfQwQwyrccOl6TgjK6Cfwg==$3cd1a3367d6f4c674b1fbe33d0c40c7e09c49885c6efef939286601587d1163a
--
-- 执行步骤：
--   1. 停服 / 切到维护页
--   2. 选择对应数据库章节（MySQL / GaussDB / DM）
--   3. 替换 ${TS} 为执行时时间戳（如 20260615103000）
--   4. 执行 BACKUP 段确认备份表生成且行数与 sys_user 一致
--   5. 执行 RESET 段
--   6. 全量比对：备份表行数 == sys_user 行数 == UPDATE 影响行数
--   7. 启服并通知所有用户使用 Sq@reset2025 登录后立即改密
--
-- 回滚（紧急）：UPDATE sys_user SET password = b.password
--              FROM backup_pwd_${TS} b WHERE sys_user.user_id = b.user_id;
-- =====================================================================


-- =====================================================================
-- MySQL 8
-- =====================================================================
-- BACKUP
CREATE TABLE `backup_pwd_${TS}` AS
  SELECT user_id, user_name, password, NOW() AS backup_time
  FROM sys_user;

-- 校验：以下两个查询行数必须相等
-- SELECT COUNT(*) FROM `backup_pwd_${TS}`;
-- SELECT COUNT(*) FROM sys_user;

-- RESET
UPDATE sys_user
SET password = '{sm3}NfQwQwyrccOl6TgjK6Cfwg==$3cd1a3367d6f4c674b1fbe33d0c40c7e09c49885c6efef939286601587d1163a',
    update_time = NOW(),
    update_by = 'gm-upgrade'
WHERE password LIKE '$2a$%' OR password LIKE '$2b$%' OR password NOT LIKE '{sm3}%';


-- =====================================================================
-- GaussDB / openGauss / PostgreSQL 兼容
-- =====================================================================
-- BACKUP
CREATE TABLE "public"."backup_pwd_${TS}" AS
  SELECT user_id, user_name, password, NOW() AS backup_time
  FROM "public"."sys_user";

-- 校验：
-- SELECT COUNT(*) FROM "public"."backup_pwd_${TS}";
-- SELECT COUNT(*) FROM "public"."sys_user";

-- RESET
UPDATE "public"."sys_user"
SET password = '{sm3}NfQwQwyrccOl6TgjK6Cfwg==$3cd1a3367d6f4c674b1fbe33d0c40c7e09c49885c6efef939286601587d1163a',
    update_time = NOW(),
    update_by = 'gm-upgrade'
WHERE password LIKE '$2a$%' OR password LIKE '$2b$%' OR password NOT LIKE '{sm3}%';


-- =====================================================================
-- 达梦 DM
-- =====================================================================
-- BACKUP
CREATE TABLE "sqfast"."BACKUP_PWD_${TS}" AS
  SELECT "USER_ID", "USER_NAME", "PASSWORD", SYSDATE AS BACKUP_TIME
  FROM "sqfast"."SYS_USER";

-- 校验：
-- SELECT COUNT(*) FROM "sqfast"."BACKUP_PWD_${TS}";
-- SELECT COUNT(*) FROM "sqfast"."SYS_USER";

-- RESET
UPDATE "sqfast"."SYS_USER"
SET "PASSWORD" = '{sm3}NfQwQwyrccOl6TgjK6Cfwg==$3cd1a3367d6f4c674b1fbe33d0c40c7e09c49885c6efef939286601587d1163a',
    "UPDATE_TIME" = SYSDATE,
    "UPDATE_BY" = 'gm-upgrade'
WHERE "PASSWORD" LIKE '$2a$%' OR "PASSWORD" LIKE '$2b$%' OR "PASSWORD" NOT LIKE '{sm3}%';
