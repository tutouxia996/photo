/*
 Navicat Premium Data Transfer

 Source Server         : 10.11.20.34
 Source Server Type    : PostgreSQL
 Source Server Version : 90204
 Source Host           : 10.11.20.34:5432
 Source Catalog        : sqfast
 Source Schema         : public

 Target Server Type    : PostgreSQL
 Target Server Version : 90204
 File Encoding         : 65001

 Date: 06/03/2025 09:27:04
*/


-- ----------------------------
-- Table structure for gen_table
-- ----------------------------
DROP TABLE IF EXISTS "public"."gen_table";
CREATE TABLE "public"."gen_table" (
  "table_id" int8 NOT NULL,
  "table_name" varchar(200) COLLATE "pg_catalog"."default",
  "table_comment" varchar(500) COLLATE "pg_catalog"."default",
  "sub_table_name" varchar(64) COLLATE "pg_catalog"."default",
  "sub_table_fk_name" varchar(64) COLLATE "pg_catalog"."default",
  "class_name" varchar(100) COLLATE "pg_catalog"."default",
  "tpl_category" varchar(200) COLLATE "pg_catalog"."default",
  "tpl_web_type" varchar(30) COLLATE "pg_catalog"."default",
  "package_name" varchar(100) COLLATE "pg_catalog"."default",
  "module_name" varchar(30) COLLATE "pg_catalog"."default",
  "business_name" varchar(30) COLLATE "pg_catalog"."default",
  "function_name" varchar(50) COLLATE "pg_catalog"."default",
  "function_author" varchar(50) COLLATE "pg_catalog"."default",
  "gen_type" char(1) COLLATE "pg_catalog"."default",
  "gen_path" varchar(200) COLLATE "pg_catalog"."default",
  "options" varchar(1000) COLLATE "pg_catalog"."default",
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6),
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6),
  "remark" varchar(500) COLLATE "pg_catalog"."default",
  "controller_package_name" varchar(255) COLLATE "pg_catalog"."default",
  "controller_module_name" varchar(255) COLLATE "pg_catalog"."default",
  "edit_type" char(1) COLLATE "pg_catalog"."default"
)
;
COMMENT ON COLUMN "public"."gen_table"."table_id" IS '编号';
COMMENT ON COLUMN "public"."gen_table"."table_name" IS '表名称';
COMMENT ON COLUMN "public"."gen_table"."table_comment" IS '表描述';
COMMENT ON COLUMN "public"."gen_table"."sub_table_name" IS '关联子表的表名';
COMMENT ON COLUMN "public"."gen_table"."sub_table_fk_name" IS '子表关联的外键名';
COMMENT ON COLUMN "public"."gen_table"."class_name" IS '实体类名称';
COMMENT ON COLUMN "public"."gen_table"."tpl_category" IS '使用的模板（crud单表操作 tree树表操作）';
COMMENT ON COLUMN "public"."gen_table"."tpl_web_type" IS '前端模板类型（element-ui模版 element-plus模版）';
COMMENT ON COLUMN "public"."gen_table"."package_name" IS '生成包路径';
COMMENT ON COLUMN "public"."gen_table"."module_name" IS '生成模块名';
COMMENT ON COLUMN "public"."gen_table"."business_name" IS '生成业务名';
COMMENT ON COLUMN "public"."gen_table"."function_name" IS '生成功能名';
COMMENT ON COLUMN "public"."gen_table"."function_author" IS '生成功能作者';
COMMENT ON COLUMN "public"."gen_table"."gen_type" IS '生成代码方式（0zip压缩包 1自定义路径）';
COMMENT ON COLUMN "public"."gen_table"."gen_path" IS '生成路径（不填默认项目路径）';
COMMENT ON COLUMN "public"."gen_table"."options" IS '其它生成选项';
COMMENT ON COLUMN "public"."gen_table"."create_by" IS '创建者';
COMMENT ON COLUMN "public"."gen_table"."create_time" IS '创建时间';
COMMENT ON COLUMN "public"."gen_table"."update_by" IS '更新者';
COMMENT ON COLUMN "public"."gen_table"."update_time" IS '更新时间';
COMMENT ON COLUMN "public"."gen_table"."remark" IS '备注';
COMMENT ON COLUMN "public"."gen_table"."controller_package_name" IS 'controller生成包路径';
COMMENT ON COLUMN "public"."gen_table"."controller_module_name" IS 'controller生成模块名';
COMMENT ON COLUMN "public"."gen_table"."edit_type" IS '编辑页类型（0抽屉 1弹框）';
COMMENT ON TABLE "public"."gen_table" IS '代码生成业务表';

-- ----------------------------
-- Records of gen_table
-- ----------------------------

-- ----------------------------
-- Table structure for gen_table_column
-- ----------------------------
DROP TABLE IF EXISTS "public"."gen_table_column";
CREATE TABLE "public"."gen_table_column" (
  "column_id" int8 NOT NULL,
  "table_id" int8,
  "column_name" varchar(200) COLLATE "pg_catalog"."default",
  "column_comment" varchar(500) COLLATE "pg_catalog"."default",
  "column_type" varchar(100) COLLATE "pg_catalog"."default",
  "java_type" varchar(500) COLLATE "pg_catalog"."default",
  "java_field" varchar(200) COLLATE "pg_catalog"."default",
  "is_pk" char(1) COLLATE "pg_catalog"."default",
  "is_increment" char(1) COLLATE "pg_catalog"."default",
  "is_required" char(1) COLLATE "pg_catalog"."default",
  "is_insert" char(1) COLLATE "pg_catalog"."default",
  "is_edit" char(1) COLLATE "pg_catalog"."default",
  "is_list" char(1) COLLATE "pg_catalog"."default",
  "is_query" char(1) COLLATE "pg_catalog"."default",
  "query_type" varchar(200) COLLATE "pg_catalog"."default",
  "html_type" varchar(200) COLLATE "pg_catalog"."default",
  "dict_type" varchar(200) COLLATE "pg_catalog"."default",
  "sort" int4,
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6),
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6),
  "max_length" int4,
  "min_length" int4
)
;
COMMENT ON COLUMN "public"."gen_table_column"."column_id" IS '编号';
COMMENT ON COLUMN "public"."gen_table_column"."table_id" IS '归属表编号';
COMMENT ON COLUMN "public"."gen_table_column"."column_name" IS '列名称';
COMMENT ON COLUMN "public"."gen_table_column"."column_comment" IS '列描述';
COMMENT ON COLUMN "public"."gen_table_column"."column_type" IS '列类型';
COMMENT ON COLUMN "public"."gen_table_column"."java_type" IS 'JAVA类型';
COMMENT ON COLUMN "public"."gen_table_column"."java_field" IS 'JAVA字段名';
COMMENT ON COLUMN "public"."gen_table_column"."is_pk" IS '是否主键（1是）';
COMMENT ON COLUMN "public"."gen_table_column"."is_increment" IS '是否自增（1是）';
COMMENT ON COLUMN "public"."gen_table_column"."is_required" IS '是否必填（1是）';
COMMENT ON COLUMN "public"."gen_table_column"."is_insert" IS '是否为插入字段（1是）';
COMMENT ON COLUMN "public"."gen_table_column"."is_edit" IS '是否编辑字段（1是）';
COMMENT ON COLUMN "public"."gen_table_column"."is_list" IS '是否列表字段（1是）';
COMMENT ON COLUMN "public"."gen_table_column"."is_query" IS '是否查询字段（1是）';
COMMENT ON COLUMN "public"."gen_table_column"."query_type" IS '查询方式（等于、不等于、大于、小于、范围）';
COMMENT ON COLUMN "public"."gen_table_column"."html_type" IS '显示类型（文本框、文本域、下拉框、复选框、单选框、日期控件）';
COMMENT ON COLUMN "public"."gen_table_column"."dict_type" IS '字典类型';
COMMENT ON COLUMN "public"."gen_table_column"."sort" IS '排序';
COMMENT ON COLUMN "public"."gen_table_column"."create_by" IS '创建者';
COMMENT ON COLUMN "public"."gen_table_column"."create_time" IS '创建时间';
COMMENT ON COLUMN "public"."gen_table_column"."update_by" IS '更新者';
COMMENT ON COLUMN "public"."gen_table_column"."update_time" IS '更新时间';
COMMENT ON COLUMN "public"."gen_table_column"."max_length" IS '最大长度';
COMMENT ON COLUMN "public"."gen_table_column"."min_length" IS '最小长度';
COMMENT ON TABLE "public"."gen_table_column" IS '代码生成业务表字段';

-- ----------------------------
-- Records of gen_table_column
-- ----------------------------

-- ----------------------------
-- Table structure for qrtz_blob_triggers
-- ----------------------------
DROP TABLE IF EXISTS "public"."qrtz_blob_triggers";
CREATE TABLE "public"."qrtz_blob_triggers" (
  "sched_name" varchar(120) COLLATE "pg_catalog"."default" NOT NULL,
  "trigger_name" varchar(200) COLLATE "pg_catalog"."default" NOT NULL,
  "trigger_group" varchar(200) COLLATE "pg_catalog"."default" NOT NULL,
  "blob_data" bytea
)
;
COMMENT ON COLUMN "public"."qrtz_blob_triggers"."sched_name" IS '调度名称';
COMMENT ON COLUMN "public"."qrtz_blob_triggers"."trigger_name" IS 'qrtz_triggers表trigger_name的外键';
COMMENT ON COLUMN "public"."qrtz_blob_triggers"."trigger_group" IS 'qrtz_triggers表trigger_group的外键';
COMMENT ON COLUMN "public"."qrtz_blob_triggers"."blob_data" IS '存放持久化Trigger对象';
COMMENT ON TABLE "public"."qrtz_blob_triggers" IS 'Blob类型的触发器表';

-- ----------------------------
-- Records of qrtz_blob_triggers
-- ----------------------------

-- ----------------------------
-- Table structure for qrtz_calendars
-- ----------------------------
DROP TABLE IF EXISTS "public"."qrtz_calendars";
CREATE TABLE "public"."qrtz_calendars" (
  "sched_name" varchar(120) COLLATE "pg_catalog"."default" NOT NULL,
  "calendar_name" varchar(200) COLLATE "pg_catalog"."default" NOT NULL,
  "calendar" bytea NOT NULL
)
;
COMMENT ON COLUMN "public"."qrtz_calendars"."sched_name" IS '调度名称';
COMMENT ON COLUMN "public"."qrtz_calendars"."calendar_name" IS '日历名称';
COMMENT ON COLUMN "public"."qrtz_calendars"."calendar" IS '存放持久化calendar对象';
COMMENT ON TABLE "public"."qrtz_calendars" IS '日历信息表';

-- ----------------------------
-- Records of qrtz_calendars
-- ----------------------------

-- ----------------------------
-- Table structure for qrtz_cron_triggers
-- ----------------------------
DROP TABLE IF EXISTS "public"."qrtz_cron_triggers";
CREATE TABLE "public"."qrtz_cron_triggers" (
  "sched_name" varchar(120) COLLATE "pg_catalog"."default" NOT NULL,
  "trigger_name" varchar(200) COLLATE "pg_catalog"."default" NOT NULL,
  "trigger_group" varchar(200) COLLATE "pg_catalog"."default" NOT NULL,
  "cron_expression" varchar(200) COLLATE "pg_catalog"."default" NOT NULL,
  "time_zone_id" varchar(80) COLLATE "pg_catalog"."default"
)
;
COMMENT ON COLUMN "public"."qrtz_cron_triggers"."sched_name" IS '调度名称';
COMMENT ON COLUMN "public"."qrtz_cron_triggers"."trigger_name" IS 'qrtz_triggers表trigger_name的外键';
COMMENT ON COLUMN "public"."qrtz_cron_triggers"."trigger_group" IS 'qrtz_triggers表trigger_group的外键';
COMMENT ON COLUMN "public"."qrtz_cron_triggers"."cron_expression" IS 'cron表达式';
COMMENT ON COLUMN "public"."qrtz_cron_triggers"."time_zone_id" IS '时区';
COMMENT ON TABLE "public"."qrtz_cron_triggers" IS 'Cron类型的触发器表';

-- ----------------------------
-- Records of qrtz_cron_triggers
-- ----------------------------

-- ----------------------------
-- Table structure for qrtz_fired_triggers
-- ----------------------------
DROP TABLE IF EXISTS "public"."qrtz_fired_triggers";
CREATE TABLE "public"."qrtz_fired_triggers" (
  "sched_name" varchar(120) COLLATE "pg_catalog"."default" NOT NULL,
  "entry_id" varchar(95) COLLATE "pg_catalog"."default" NOT NULL,
  "trigger_name" varchar(200) COLLATE "pg_catalog"."default" NOT NULL,
  "trigger_group" varchar(200) COLLATE "pg_catalog"."default" NOT NULL,
  "instance_name" varchar(200) COLLATE "pg_catalog"."default" NOT NULL,
  "fired_time" int8 NOT NULL,
  "sched_time" int8 NOT NULL,
  "priority" int4 NOT NULL,
  "state" varchar(16) COLLATE "pg_catalog"."default" NOT NULL,
  "job_name" varchar(200) COLLATE "pg_catalog"."default",
  "job_group" varchar(200) COLLATE "pg_catalog"."default",
  "is_nonconcurrent" varchar(1) COLLATE "pg_catalog"."default",
  "requests_recovery" varchar(1) COLLATE "pg_catalog"."default"
)
;
COMMENT ON COLUMN "public"."qrtz_fired_triggers"."sched_name" IS '调度名称';
COMMENT ON COLUMN "public"."qrtz_fired_triggers"."entry_id" IS '调度器实例id';
COMMENT ON COLUMN "public"."qrtz_fired_triggers"."trigger_name" IS 'qrtz_triggers表trigger_name的外键';
COMMENT ON COLUMN "public"."qrtz_fired_triggers"."trigger_group" IS 'qrtz_triggers表trigger_group的外键';
COMMENT ON COLUMN "public"."qrtz_fired_triggers"."instance_name" IS '调度器实例名';
COMMENT ON COLUMN "public"."qrtz_fired_triggers"."fired_time" IS '触发的时间';
COMMENT ON COLUMN "public"."qrtz_fired_triggers"."sched_time" IS '定时器制定的时间';
COMMENT ON COLUMN "public"."qrtz_fired_triggers"."priority" IS '优先级';
COMMENT ON COLUMN "public"."qrtz_fired_triggers"."state" IS '状态';
COMMENT ON COLUMN "public"."qrtz_fired_triggers"."job_name" IS '任务名称';
COMMENT ON COLUMN "public"."qrtz_fired_triggers"."job_group" IS '任务组名';
COMMENT ON COLUMN "public"."qrtz_fired_triggers"."is_nonconcurrent" IS '是否并发';
COMMENT ON COLUMN "public"."qrtz_fired_triggers"."requests_recovery" IS '是否接受恢复执行';
COMMENT ON TABLE "public"."qrtz_fired_triggers" IS '已触发的触发器表';

-- ----------------------------
-- Records of qrtz_fired_triggers
-- ----------------------------

-- ----------------------------
-- Table structure for qrtz_job_details
-- ----------------------------
DROP TABLE IF EXISTS "public"."qrtz_job_details";
CREATE TABLE "public"."qrtz_job_details" (
  "sched_name" varchar(120) COLLATE "pg_catalog"."default" NOT NULL,
  "job_name" varchar(200) COLLATE "pg_catalog"."default" NOT NULL,
  "job_group" varchar(200) COLLATE "pg_catalog"."default" NOT NULL,
  "description" varchar(250) COLLATE "pg_catalog"."default",
  "job_class_name" varchar(250) COLLATE "pg_catalog"."default" NOT NULL,
  "is_durable" varchar(1) COLLATE "pg_catalog"."default" NOT NULL,
  "is_nonconcurrent" varchar(1) COLLATE "pg_catalog"."default" NOT NULL,
  "is_update_data" varchar(1) COLLATE "pg_catalog"."default" NOT NULL,
  "requests_recovery" varchar(1) COLLATE "pg_catalog"."default" NOT NULL,
  "job_data" bytea
)
;
COMMENT ON COLUMN "public"."qrtz_job_details"."sched_name" IS '调度名称';
COMMENT ON COLUMN "public"."qrtz_job_details"."job_name" IS '任务名称';
COMMENT ON COLUMN "public"."qrtz_job_details"."job_group" IS '任务组名';
COMMENT ON COLUMN "public"."qrtz_job_details"."description" IS '相关介绍';
COMMENT ON COLUMN "public"."qrtz_job_details"."job_class_name" IS '执行任务类名称';
COMMENT ON COLUMN "public"."qrtz_job_details"."is_durable" IS '是否持久化';
COMMENT ON COLUMN "public"."qrtz_job_details"."is_nonconcurrent" IS '是否并发';
COMMENT ON COLUMN "public"."qrtz_job_details"."is_update_data" IS '是否更新数据';
COMMENT ON COLUMN "public"."qrtz_job_details"."requests_recovery" IS '是否接受恢复执行';
COMMENT ON COLUMN "public"."qrtz_job_details"."job_data" IS '存放持久化job对象';
COMMENT ON TABLE "public"."qrtz_job_details" IS '任务详细信息表';

-- ----------------------------
-- Records of qrtz_job_details
-- ----------------------------

-- ----------------------------
-- Table structure for qrtz_locks
-- ----------------------------
DROP TABLE IF EXISTS "public"."qrtz_locks";
CREATE TABLE "public"."qrtz_locks" (
  "sched_name" varchar(120) COLLATE "pg_catalog"."default" NOT NULL,
  "lock_name" varchar(40) COLLATE "pg_catalog"."default" NOT NULL
)
;
COMMENT ON COLUMN "public"."qrtz_locks"."sched_name" IS '调度名称';
COMMENT ON COLUMN "public"."qrtz_locks"."lock_name" IS '悲观锁名称';
COMMENT ON TABLE "public"."qrtz_locks" IS '存储的悲观锁信息表';

-- ----------------------------
-- Records of qrtz_locks
-- ----------------------------

-- ----------------------------
-- Table structure for qrtz_paused_trigger_grps
-- ----------------------------
DROP TABLE IF EXISTS "public"."qrtz_paused_trigger_grps";
CREATE TABLE "public"."qrtz_paused_trigger_grps" (
  "sched_name" varchar(120) COLLATE "pg_catalog"."default" NOT NULL,
  "trigger_group" varchar(200) COLLATE "pg_catalog"."default" NOT NULL
)
;
COMMENT ON COLUMN "public"."qrtz_paused_trigger_grps"."sched_name" IS '调度名称';
COMMENT ON COLUMN "public"."qrtz_paused_trigger_grps"."trigger_group" IS 'qrtz_triggers表trigger_group的外键';
COMMENT ON TABLE "public"."qrtz_paused_trigger_grps" IS '暂停的触发器表';

-- ----------------------------
-- Records of qrtz_paused_trigger_grps
-- ----------------------------

-- ----------------------------
-- Table structure for qrtz_scheduler_state
-- ----------------------------
DROP TABLE IF EXISTS "public"."qrtz_scheduler_state";
CREATE TABLE "public"."qrtz_scheduler_state" (
  "sched_name" varchar(120) COLLATE "pg_catalog"."default" NOT NULL,
  "instance_name" varchar(200) COLLATE "pg_catalog"."default" NOT NULL,
  "last_checkin_time" int8 NOT NULL,
  "checkin_interval" int8 NOT NULL
)
;
COMMENT ON COLUMN "public"."qrtz_scheduler_state"."sched_name" IS '调度名称';
COMMENT ON COLUMN "public"."qrtz_scheduler_state"."instance_name" IS '实例名称';
COMMENT ON COLUMN "public"."qrtz_scheduler_state"."last_checkin_time" IS '上次检查时间';
COMMENT ON COLUMN "public"."qrtz_scheduler_state"."checkin_interval" IS '检查间隔时间';
COMMENT ON TABLE "public"."qrtz_scheduler_state" IS '调度器状态表';

-- ----------------------------
-- Records of qrtz_scheduler_state
-- ----------------------------

-- ----------------------------
-- Table structure for qrtz_simple_triggers
-- ----------------------------
DROP TABLE IF EXISTS "public"."qrtz_simple_triggers";
CREATE TABLE "public"."qrtz_simple_triggers" (
  "sched_name" varchar(120) COLLATE "pg_catalog"."default" NOT NULL,
  "trigger_name" varchar(200) COLLATE "pg_catalog"."default" NOT NULL,
  "trigger_group" varchar(200) COLLATE "pg_catalog"."default" NOT NULL,
  "repeat_count" int8 NOT NULL,
  "repeat_interval" int8 NOT NULL,
  "times_triggered" int8 NOT NULL
)
;
COMMENT ON COLUMN "public"."qrtz_simple_triggers"."sched_name" IS '调度名称';
COMMENT ON COLUMN "public"."qrtz_simple_triggers"."trigger_name" IS 'qrtz_triggers表trigger_name的外键';
COMMENT ON COLUMN "public"."qrtz_simple_triggers"."trigger_group" IS 'qrtz_triggers表trigger_group的外键';
COMMENT ON COLUMN "public"."qrtz_simple_triggers"."repeat_count" IS '重复的次数统计';
COMMENT ON COLUMN "public"."qrtz_simple_triggers"."repeat_interval" IS '重复的间隔时间';
COMMENT ON COLUMN "public"."qrtz_simple_triggers"."times_triggered" IS '已经触发的次数';
COMMENT ON TABLE "public"."qrtz_simple_triggers" IS '简单触发器的信息表';

-- ----------------------------
-- Records of qrtz_simple_triggers
-- ----------------------------

-- ----------------------------
-- Table structure for qrtz_simprop_triggers
-- ----------------------------
DROP TABLE IF EXISTS "public"."qrtz_simprop_triggers";
CREATE TABLE "public"."qrtz_simprop_triggers" (
  "sched_name" varchar(120) COLLATE "pg_catalog"."default" NOT NULL,
  "trigger_name" varchar(200) COLLATE "pg_catalog"."default" NOT NULL,
  "trigger_group" varchar(200) COLLATE "pg_catalog"."default" NOT NULL,
  "str_prop_1" varchar(512) COLLATE "pg_catalog"."default",
  "str_prop_2" varchar(512) COLLATE "pg_catalog"."default",
  "str_prop_3" varchar(512) COLLATE "pg_catalog"."default",
  "int_prop_1" int4,
  "int_prop_2" int4,
  "long_prop_1" int8,
  "long_prop_2" int8,
  "dec_prop_1" numeric(13,4),
  "dec_prop_2" numeric(13,4),
  "bool_prop_1" varchar(1) COLLATE "pg_catalog"."default",
  "bool_prop_2" varchar(1) COLLATE "pg_catalog"."default"
)
;
COMMENT ON COLUMN "public"."qrtz_simprop_triggers"."sched_name" IS '调度名称';
COMMENT ON COLUMN "public"."qrtz_simprop_triggers"."trigger_name" IS 'qrtz_triggers表trigger_name的外键';
COMMENT ON COLUMN "public"."qrtz_simprop_triggers"."trigger_group" IS 'qrtz_triggers表trigger_group的外键';
COMMENT ON COLUMN "public"."qrtz_simprop_triggers"."str_prop_1" IS 'String类型的trigger的第一个参数';
COMMENT ON COLUMN "public"."qrtz_simprop_triggers"."str_prop_2" IS 'String类型的trigger的第二个参数';
COMMENT ON COLUMN "public"."qrtz_simprop_triggers"."str_prop_3" IS 'String类型的trigger的第三个参数';
COMMENT ON COLUMN "public"."qrtz_simprop_triggers"."int_prop_1" IS 'int类型的trigger的第一个参数';
COMMENT ON COLUMN "public"."qrtz_simprop_triggers"."int_prop_2" IS 'int类型的trigger的第二个参数';
COMMENT ON COLUMN "public"."qrtz_simprop_triggers"."long_prop_1" IS 'long类型的trigger的第一个参数';
COMMENT ON COLUMN "public"."qrtz_simprop_triggers"."long_prop_2" IS 'long类型的trigger的第二个参数';
COMMENT ON COLUMN "public"."qrtz_simprop_triggers"."dec_prop_1" IS 'decimal类型的trigger的第一个参数';
COMMENT ON COLUMN "public"."qrtz_simprop_triggers"."dec_prop_2" IS 'decimal类型的trigger的第二个参数';
COMMENT ON COLUMN "public"."qrtz_simprop_triggers"."bool_prop_1" IS 'Boolean类型的trigger的第一个参数';
COMMENT ON COLUMN "public"."qrtz_simprop_triggers"."bool_prop_2" IS 'Boolean类型的trigger的第二个参数';
COMMENT ON TABLE "public"."qrtz_simprop_triggers" IS '同步机制的行锁表';

-- ----------------------------
-- Records of qrtz_simprop_triggers
-- ----------------------------

-- ----------------------------
-- Table structure for qrtz_triggers
-- ----------------------------
DROP TABLE IF EXISTS "public"."qrtz_triggers";
CREATE TABLE "public"."qrtz_triggers" (
  "sched_name" varchar(120) COLLATE "pg_catalog"."default" NOT NULL,
  "trigger_name" varchar(200) COLLATE "pg_catalog"."default" NOT NULL,
  "trigger_group" varchar(200) COLLATE "pg_catalog"."default" NOT NULL,
  "job_name" varchar(200) COLLATE "pg_catalog"."default" NOT NULL,
  "job_group" varchar(200) COLLATE "pg_catalog"."default" NOT NULL,
  "description" varchar(250) COLLATE "pg_catalog"."default",
  "next_fire_time" int8,
  "prev_fire_time" int8,
  "priority" int4,
  "trigger_state" varchar(16) COLLATE "pg_catalog"."default" NOT NULL,
  "trigger_type" varchar(8) COLLATE "pg_catalog"."default" NOT NULL,
  "start_time" int8 NOT NULL,
  "end_time" int8,
  "calendar_name" varchar(200) COLLATE "pg_catalog"."default",
  "misfire_instr" int2,
  "job_data" bytea
)
;
COMMENT ON COLUMN "public"."qrtz_triggers"."sched_name" IS '调度名称';
COMMENT ON COLUMN "public"."qrtz_triggers"."trigger_name" IS '触发器的名字';
COMMENT ON COLUMN "public"."qrtz_triggers"."trigger_group" IS '触发器所属组的名字';
COMMENT ON COLUMN "public"."qrtz_triggers"."job_name" IS 'qrtz_job_details表job_name的外键';
COMMENT ON COLUMN "public"."qrtz_triggers"."job_group" IS 'qrtz_job_details表job_group的外键';
COMMENT ON COLUMN "public"."qrtz_triggers"."description" IS '相关介绍';
COMMENT ON COLUMN "public"."qrtz_triggers"."next_fire_time" IS '上一次触发时间（毫秒）';
COMMENT ON COLUMN "public"."qrtz_triggers"."prev_fire_time" IS '下一次触发时间（默认为-1表示不触发）';
COMMENT ON COLUMN "public"."qrtz_triggers"."priority" IS '优先级';
COMMENT ON COLUMN "public"."qrtz_triggers"."trigger_state" IS '触发器状态';
COMMENT ON COLUMN "public"."qrtz_triggers"."trigger_type" IS '触发器的类型';
COMMENT ON COLUMN "public"."qrtz_triggers"."start_time" IS '开始时间';
COMMENT ON COLUMN "public"."qrtz_triggers"."end_time" IS '结束时间';
COMMENT ON COLUMN "public"."qrtz_triggers"."calendar_name" IS '日程表名称';
COMMENT ON COLUMN "public"."qrtz_triggers"."misfire_instr" IS '补偿执行的策略';
COMMENT ON COLUMN "public"."qrtz_triggers"."job_data" IS '存放持久化job对象';
COMMENT ON TABLE "public"."qrtz_triggers" IS '触发器详细信息表';

-- ----------------------------
-- Records of qrtz_triggers
-- ----------------------------

-- ----------------------------
-- Table structure for sys_config
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_config";
CREATE TABLE "public"."sys_config" (
  "id" int8 NOT NULL,
  "config_name" varchar(100) COLLATE "pg_catalog"."default",
  "config_key" varchar(100) COLLATE "pg_catalog"."default",
  "config_value" varchar(500) COLLATE "pg_catalog"."default",
  "config_type" char(1) COLLATE "pg_catalog"."default",
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6),
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6),
  "remark" varchar(500) COLLATE "pg_catalog"."default"
)
;
COMMENT ON COLUMN "public"."sys_config"."id" IS '参数主键';
COMMENT ON COLUMN "public"."sys_config"."config_name" IS '参数名称';
COMMENT ON COLUMN "public"."sys_config"."config_key" IS '参数键名';
COMMENT ON COLUMN "public"."sys_config"."config_value" IS '参数键值';
COMMENT ON COLUMN "public"."sys_config"."config_type" IS '系统内置（Y是 N否）';
COMMENT ON COLUMN "public"."sys_config"."create_by" IS '创建者';
COMMENT ON COLUMN "public"."sys_config"."create_time" IS '创建时间';
COMMENT ON COLUMN "public"."sys_config"."update_by" IS '更新者';
COMMENT ON COLUMN "public"."sys_config"."update_time" IS '更新时间';
COMMENT ON COLUMN "public"."sys_config"."remark" IS '备注';
COMMENT ON TABLE "public"."sys_config" IS '参数配置表';

-- ----------------------------
-- Records of sys_config
-- ----------------------------
INSERT INTO "public"."sys_config" VALUES (1, '主框架页-默认皮肤样式名称', 'sys.index.skinName', 'skin-blue', 'Y', 'admin', '2024-06-21 10:57:33', '', NULL, '蓝色 skin-blue、绿色 skin-green、紫色 skin-purple、红色 skin-red、黄色 skin-yellow');
INSERT INTO "public"."sys_config" VALUES (2, '用户管理-账号初始密码', 'sys.user.initPassword', '123456', 'Y', 'admin', '2024-06-21 10:57:33', '', NULL, '初始化密码 123456');
INSERT INTO "public"."sys_config" VALUES (3, '主框架页-侧边栏主题', 'sys.index.sideTheme', 'theme-dark', 'Y', 'admin', '2024-06-21 10:57:33', '', NULL, '深色主题theme-dark，浅色主题theme-light');
INSERT INTO "public"."sys_config" VALUES (4, '账号自助-验证码开关', 'sys.account.captchaEnabled', 'true', 'Y', 'admin', '2024-06-21 10:57:33', 'admin', '2024-07-19 10:28:25', '是否开启验证码功能（true开启，false关闭）');
INSERT INTO "public"."sys_config" VALUES (5, '账号自助-是否开启用户注册功能', 'sys.account.registerUser', 'false', 'Y', 'admin', '2024-06-21 10:57:33', '', NULL, '是否开启注册用户功能（true开启，false关闭）');
INSERT INTO "public"."sys_config" VALUES (6, '用户登录-黑名单列表', 'sys.login.blackIPList', '', 'Y', 'admin', '2024-06-21 10:57:33', '', NULL, '设置登录IP黑名单限制，多个匹配项以;分隔，支持匹配（*通配、网段）');
INSERT INTO "public"."sys_config" VALUES (7, '密码策略-密码过期天数', 'sys.password.expireDays', '90', 'Y', 'admin', '2026-06-17 18:00:00', '', NULL, '密码过期天数，超过需强制修改，0或空表示不限制');

-- ----------------------------
-- Table structure for sys_dept
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_dept";
CREATE TABLE "public"."sys_dept" (
  "id" int8 NOT NULL,
  "parent_id" int8,
  "ancestors" varchar(50) COLLATE "pg_catalog"."default",
  "eng_name" varchar(200) COLLATE "pg_catalog"."default",
  "dept_name" varchar(30) COLLATE "pg_catalog"."default",
  "order_num" int4,
  "leader" varchar(20) COLLATE "pg_catalog"."default",
  "phone" varchar(11) COLLATE "pg_catalog"."default",
  "email" varchar(50) COLLATE "pg_catalog"."default",
  "dep_type" varchar(20) COLLATE "pg_catalog"."default",
  "status" char(1) COLLATE "pg_catalog"."default",
  "del_flag" char(1) COLLATE "pg_catalog"."default",
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6),
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6)
)
;
COMMENT ON COLUMN "public"."sys_dept"."id" IS '机构id';
COMMENT ON COLUMN "public"."sys_dept"."parent_id" IS '父机构id';
COMMENT ON COLUMN "public"."sys_dept"."ancestors" IS '祖级列表';
COMMENT ON COLUMN "public"."sys_dept"."eng_name" IS '英文名称';
COMMENT ON COLUMN "public"."sys_dept"."dept_name" IS '机构名称';
COMMENT ON COLUMN "public"."sys_dept"."order_num" IS '显示顺序';
COMMENT ON COLUMN "public"."sys_dept"."leader" IS '负责人';
COMMENT ON COLUMN "public"."sys_dept"."phone" IS '联系电话';
COMMENT ON COLUMN "public"."sys_dept"."email" IS '邮箱';
COMMENT ON COLUMN "public"."sys_dept"."dep_type" IS '机构类型';
COMMENT ON COLUMN "public"."sys_dept"."status" IS '机构状态（0正常 1停用）';
COMMENT ON COLUMN "public"."sys_dept"."del_flag" IS '删除标志（0代表存在 1代表删除）';
COMMENT ON COLUMN "public"."sys_dept"."create_by" IS '创建者';
COMMENT ON COLUMN "public"."sys_dept"."create_time" IS '创建时间';
COMMENT ON COLUMN "public"."sys_dept"."update_by" IS '更新者';
COMMENT ON COLUMN "public"."sys_dept"."update_time" IS '更新时间';
COMMENT ON TABLE "public"."sys_dept" IS '机构表';

-- ----------------------------
-- Records of sys_dept
-- ----------------------------
INSERT INTO "public"."sys_dept" VALUES (203, 100, '0,100', NULL, '二连浩特口岸', 1, NULL, NULL, NULL, NULL, '0', '0', 'admin', '2025-02-17 12:06:25', '', NULL);
INSERT INTO "public"."sys_dept" VALUES (100, 0, '0', NULL, '自治区口岸办', 0, NULL, '15888888888', NULL, NULL, '0', '0', 'admin', '2024-06-21 10:57:31', 'admin', '2025-03-06 00:26:53.803369');
INSERT INTO "public"."sys_dept" VALUES (1897326789697044482, 100, '0,100', NULL, '123', 1, NULL, NULL, NULL, NULL, '0', '2', 'admin', '2025-03-06 00:42:13.250758', 'admin', '2025-03-06 00:42:19.366462');

-- ----------------------------
-- Table structure for sys_dict_data
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_dict_data";
CREATE TABLE "public"."sys_dict_data" (
  "id" int8 NOT NULL,
  "dict_sort" int4,
  "dict_label" varchar(100) COLLATE "pg_catalog"."default",
  "dict_value" varchar(100) COLLATE "pg_catalog"."default",
  "dict_type" varchar(100) COLLATE "pg_catalog"."default",
  "css_class" varchar(100) COLLATE "pg_catalog"."default",
  "list_class" varchar(100) COLLATE "pg_catalog"."default",
  "is_default" char(1) COLLATE "pg_catalog"."default",
  "status" char(1) COLLATE "pg_catalog"."default",
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6),
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6),
  "remark" varchar(500) COLLATE "pg_catalog"."default"
)
;
COMMENT ON COLUMN "public"."sys_dict_data"."id" IS '字典编码';
COMMENT ON COLUMN "public"."sys_dict_data"."dict_sort" IS '字典排序';
COMMENT ON COLUMN "public"."sys_dict_data"."dict_label" IS '字典标签';
COMMENT ON COLUMN "public"."sys_dict_data"."dict_value" IS '字典键值';
COMMENT ON COLUMN "public"."sys_dict_data"."dict_type" IS '字典类型';
COMMENT ON COLUMN "public"."sys_dict_data"."css_class" IS '样式属性（其他样式扩展）';
COMMENT ON COLUMN "public"."sys_dict_data"."list_class" IS '表格回显样式';
COMMENT ON COLUMN "public"."sys_dict_data"."is_default" IS '是否默认（Y是 N否）';
COMMENT ON COLUMN "public"."sys_dict_data"."status" IS '状态（0正常 1停用）';
COMMENT ON COLUMN "public"."sys_dict_data"."create_by" IS '创建者';
COMMENT ON COLUMN "public"."sys_dict_data"."create_time" IS '创建时间';
COMMENT ON COLUMN "public"."sys_dict_data"."update_by" IS '更新者';
COMMENT ON COLUMN "public"."sys_dict_data"."update_time" IS '更新时间';
COMMENT ON COLUMN "public"."sys_dict_data"."remark" IS '备注';
COMMENT ON TABLE "public"."sys_dict_data" IS '字典数据表';

-- ----------------------------
-- Records of sys_dict_data
-- ----------------------------
INSERT INTO "public"."sys_dict_data" VALUES (4, 1, '显示', '0', 'sys_show_hide', '', 'primary', 'Y', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '显示菜单');
INSERT INTO "public"."sys_dict_data" VALUES (5, 2, '隐藏', '1', 'sys_show_hide', '', 'danger', 'N', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '隐藏菜单');
INSERT INTO "public"."sys_dict_data" VALUES (6, 1, '正常', '0', 'sys_normal_disable', '', 'primary', 'Y', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '正常状态');
INSERT INTO "public"."sys_dict_data" VALUES (7, 2, '停用', '1', 'sys_normal_disable', '', 'danger', 'N', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '停用状态');
INSERT INTO "public"."sys_dict_data" VALUES (8, 1, '正常', '0', 'sys_job_status', '', 'primary', 'Y', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '正常状态');
INSERT INTO "public"."sys_dict_data" VALUES (9, 2, '暂停', '1', 'sys_job_status', '', 'danger', 'N', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '停用状态');
INSERT INTO "public"."sys_dict_data" VALUES (10, 1, '默认', 'DEFAULT', 'sys_job_group', '', '', 'Y', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '默认分组');
INSERT INTO "public"."sys_dict_data" VALUES (11, 2, '系统', 'SYSTEM', 'sys_job_group', '', '', 'N', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '系统分组');
INSERT INTO "public"."sys_dict_data" VALUES (12, 1, '是', 'Y', 'sys_yes_no', '', 'primary', 'Y', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '系统默认是');
INSERT INTO "public"."sys_dict_data" VALUES (13, 2, '否', 'N', 'sys_yes_no', '', 'danger', 'N', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '系统默认否');
INSERT INTO "public"."sys_dict_data" VALUES (14, 1, '通知', '1', 'sys_notice_type', '', 'warning', 'Y', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '通知');
INSERT INTO "public"."sys_dict_data" VALUES (15, 2, '公告', '2', 'sys_notice_type', '', 'success', 'N', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '公告');
INSERT INTO "public"."sys_dict_data" VALUES (16, 1, '正常', '0', 'sys_notice_status', '', 'primary', 'Y', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '正常状态');
INSERT INTO "public"."sys_dict_data" VALUES (17, 2, '关闭', '1', 'sys_notice_status', '', 'danger', 'N', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '关闭状态');
INSERT INTO "public"."sys_dict_data" VALUES (18, 99, '其他', '0', 'sys_oper_type', '', 'info', 'N', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '其他操作');
INSERT INTO "public"."sys_dict_data" VALUES (19, 1, '新增', '1', 'sys_oper_type', '', 'info', 'N', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '新增操作');
INSERT INTO "public"."sys_dict_data" VALUES (20, 2, '修改', '2', 'sys_oper_type', '', 'info', 'N', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '修改操作');
INSERT INTO "public"."sys_dict_data" VALUES (21, 3, '删除', '3', 'sys_oper_type', '', 'danger', 'N', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '删除操作');
INSERT INTO "public"."sys_dict_data" VALUES (22, 4, '授权', '4', 'sys_oper_type', '', 'primary', 'N', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '授权操作');
INSERT INTO "public"."sys_dict_data" VALUES (23, 5, '导出', '5', 'sys_oper_type', '', 'warning', 'N', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '导出操作');
INSERT INTO "public"."sys_dict_data" VALUES (24, 6, '导入', '6', 'sys_oper_type', '', 'warning', 'N', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '导入操作');
INSERT INTO "public"."sys_dict_data" VALUES (25, 7, '强退', '7', 'sys_oper_type', '', 'danger', 'N', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '强退操作');
INSERT INTO "public"."sys_dict_data" VALUES (26, 8, '生成代码', '8', 'sys_oper_type', '', 'warning', 'N', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '生成操作');
INSERT INTO "public"."sys_dict_data" VALUES (27, 9, '清空数据', '9', 'sys_oper_type', '', 'danger', 'N', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '清空操作');
INSERT INTO "public"."sys_dict_data" VALUES (28, 1, '成功', '0', 'sys_common_status', '', 'primary', 'N', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '正常状态');
INSERT INTO "public"."sys_dict_data" VALUES (29, 2, '失败', '1', 'sys_common_status', '', 'danger', 'N', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '停用状态');

-- ----------------------------
-- Table structure for sys_dict_type
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_dict_type";
CREATE TABLE "public"."sys_dict_type" (
  "id" int8 NOT NULL,
  "dict_name" varchar(100) COLLATE "pg_catalog"."default",
  "dict_type" varchar(100) COLLATE "pg_catalog"."default",
  "status" char(1) COLLATE "pg_catalog"."default",
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6),
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6),
  "remark" varchar(500) COLLATE "pg_catalog"."default"
)
;
COMMENT ON COLUMN "public"."sys_dict_type"."id" IS '字典主键';
COMMENT ON COLUMN "public"."sys_dict_type"."dict_name" IS '字典名称';
COMMENT ON COLUMN "public"."sys_dict_type"."dict_type" IS '字典类型';
COMMENT ON COLUMN "public"."sys_dict_type"."status" IS '状态（0正常 1停用）';
COMMENT ON COLUMN "public"."sys_dict_type"."create_by" IS '创建者';
COMMENT ON COLUMN "public"."sys_dict_type"."create_time" IS '创建时间';
COMMENT ON COLUMN "public"."sys_dict_type"."update_by" IS '更新者';
COMMENT ON COLUMN "public"."sys_dict_type"."update_time" IS '更新时间';
COMMENT ON COLUMN "public"."sys_dict_type"."remark" IS '备注';
COMMENT ON TABLE "public"."sys_dict_type" IS '字典类型表';

-- ----------------------------
-- Records of sys_dict_type
-- ----------------------------
INSERT INTO "public"."sys_dict_type" VALUES (1, '用户性别', 'sys_user_sex', '0', 'admin', '2024-06-21 10:57:33', 'admin', '2024-07-25 15:42:02', '用户性别列表');
INSERT INTO "public"."sys_dict_type" VALUES (2, '菜单状态', 'sys_show_hide', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '菜单状态列表');
INSERT INTO "public"."sys_dict_type" VALUES (3, '系统开关', 'sys_normal_disable', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '系统开关列表');
INSERT INTO "public"."sys_dict_type" VALUES (4, '任务状态', 'sys_job_status', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '任务状态列表');
INSERT INTO "public"."sys_dict_type" VALUES (5, '任务分组', 'sys_job_group', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '任务分组列表');
INSERT INTO "public"."sys_dict_type" VALUES (6, '系统是否', 'sys_yes_no', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '系统是否列表');
INSERT INTO "public"."sys_dict_type" VALUES (7, '通知类型', 'sys_notice_type', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '通知类型列表');
INSERT INTO "public"."sys_dict_type" VALUES (8, '通知状态', 'sys_notice_status', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '通知状态列表');
INSERT INTO "public"."sys_dict_type" VALUES (9, '操作类型', 'sys_oper_type', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '操作类型列表');
INSERT INTO "public"."sys_dict_type" VALUES (10, '系统状态', 'sys_common_status', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '登录状态列表');

-- ----------------------------
-- Table structure for sys_job
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_job";
CREATE TABLE "public"."sys_job" (
  "job_id" int8 NOT NULL,
  "job_name" varchar(64) COLLATE "pg_catalog"."default" NOT NULL,
  "job_group" varchar(64) COLLATE "pg_catalog"."default" NOT NULL,
  "invoke_target" varchar(500) COLLATE "pg_catalog"."default" NOT NULL,
  "cron_expression" varchar(255) COLLATE "pg_catalog"."default",
  "misfire_policy" varchar(20) COLLATE "pg_catalog"."default",
  "concurrent" char(1) COLLATE "pg_catalog"."default",
  "status" char(1) COLLATE "pg_catalog"."default",
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6),
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6),
  "remark" varchar(500) COLLATE "pg_catalog"."default"
)
;
COMMENT ON COLUMN "public"."sys_job"."job_id" IS '任务ID';
COMMENT ON COLUMN "public"."sys_job"."job_name" IS '任务名称';
COMMENT ON COLUMN "public"."sys_job"."job_group" IS '任务组名';
COMMENT ON COLUMN "public"."sys_job"."invoke_target" IS '调用目标字符串';
COMMENT ON COLUMN "public"."sys_job"."cron_expression" IS 'cron执行表达式';
COMMENT ON COLUMN "public"."sys_job"."misfire_policy" IS '计划执行错误策略（1立即执行 2执行一次 3放弃执行）';
COMMENT ON COLUMN "public"."sys_job"."concurrent" IS '是否并发执行（0允许 1禁止）';
COMMENT ON COLUMN "public"."sys_job"."status" IS '状态（0正常 1暂停）';
COMMENT ON COLUMN "public"."sys_job"."create_by" IS '创建者';
COMMENT ON COLUMN "public"."sys_job"."create_time" IS '创建时间';
COMMENT ON COLUMN "public"."sys_job"."update_by" IS '更新者';
COMMENT ON COLUMN "public"."sys_job"."update_time" IS '更新时间';
COMMENT ON COLUMN "public"."sys_job"."remark" IS '备注信息';
COMMENT ON TABLE "public"."sys_job" IS '定时任务调度表';

-- ----------------------------
-- Records of sys_job
-- ----------------------------
INSERT INTO "public"."sys_job" VALUES (1, '系统默认（无参）', 'DEFAULT', 'ryTask.ryNoParams', '0/10 * * * * ?', '3', '1', '1', 'admin', '2024-06-21 10:57:33', '', NULL, '');
INSERT INTO "public"."sys_job" VALUES (2, '系统默认（有参）', 'DEFAULT', 'ryTask.ryParams(''ry'')', '0/15 * * * * ?', '3', '1', '1', 'admin', '2024-06-21 10:57:33', '', NULL, '');
INSERT INTO "public"."sys_job" VALUES (3, '系统默认（多参）', 'DEFAULT', 'ryTask.ryMultipleParams(''ry'', true, 2000L, 316.50D, 100)', '0/20 * * * * ?', '3', '1', '1', 'admin', '2024-06-21 10:57:33', '', NULL, '');

-- ----------------------------
-- Table structure for sys_job_log
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_job_log";
CREATE TABLE "public"."sys_job_log" (
  "job_log_id" int8 NOT NULL,
  "job_name" varchar(64) COLLATE "pg_catalog"."default" NOT NULL,
  "job_group" varchar(64) COLLATE "pg_catalog"."default" NOT NULL,
  "invoke_target" varchar(500) COLLATE "pg_catalog"."default" NOT NULL,
  "job_message" varchar(500) COLLATE "pg_catalog"."default",
  "status" char(1) COLLATE "pg_catalog"."default",
  "exception_info" varchar(2000) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6)
)
;
COMMENT ON COLUMN "public"."sys_job_log"."job_log_id" IS '任务日志ID';
COMMENT ON COLUMN "public"."sys_job_log"."job_name" IS '任务名称';
COMMENT ON COLUMN "public"."sys_job_log"."job_group" IS '任务组名';
COMMENT ON COLUMN "public"."sys_job_log"."invoke_target" IS '调用目标字符串';
COMMENT ON COLUMN "public"."sys_job_log"."job_message" IS '日志信息';
COMMENT ON COLUMN "public"."sys_job_log"."status" IS '执行状态（0正常 1失败）';
COMMENT ON COLUMN "public"."sys_job_log"."exception_info" IS '异常信息';
COMMENT ON COLUMN "public"."sys_job_log"."create_time" IS '创建时间';
COMMENT ON TABLE "public"."sys_job_log" IS '定时任务调度日志表';

-- ----------------------------
-- Records of sys_job_log
-- ----------------------------

-- ----------------------------
-- Table structure for sys_logininfor
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_logininfor";
CREATE TABLE "public"."sys_logininfor" (
  "info_id" int8 NOT NULL,
  "user_name" varchar(50) COLLATE "pg_catalog"."default",
  "ipaddr" varchar(128) COLLATE "pg_catalog"."default",
  "login_location" varchar(255) COLLATE "pg_catalog"."default",
  "browser" varchar(50) COLLATE "pg_catalog"."default",
  "os" varchar(50) COLLATE "pg_catalog"."default",
  "status" char(1) COLLATE "pg_catalog"."default",
  "msg" varchar(255) COLLATE "pg_catalog"."default",
  "login_time" timestamp(6)
)
;
COMMENT ON COLUMN "public"."sys_logininfor"."info_id" IS '访问ID';
COMMENT ON COLUMN "public"."sys_logininfor"."user_name" IS '用户账号';
COMMENT ON COLUMN "public"."sys_logininfor"."ipaddr" IS '登录IP地址';
COMMENT ON COLUMN "public"."sys_logininfor"."login_location" IS '登录地点';
COMMENT ON COLUMN "public"."sys_logininfor"."browser" IS '浏览器类型';
COMMENT ON COLUMN "public"."sys_logininfor"."os" IS '操作系统';
COMMENT ON COLUMN "public"."sys_logininfor"."status" IS '登录状态（0成功 1失败）';
COMMENT ON COLUMN "public"."sys_logininfor"."msg" IS '提示消息';
COMMENT ON COLUMN "public"."sys_logininfor"."login_time" IS '访问时间';
COMMENT ON TABLE "public"."sys_logininfor" IS '系统访问记录';

-- ----------------------------
-- Records of sys_logininfor
-- ----------------------------
INSERT INTO "public"."sys_logininfor" VALUES (1897232473972592642, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '1', '验证码已失效', '2025-03-05 18:27:25.539045');
INSERT INTO "public"."sys_logininfor" VALUES (1897232509636759554, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-03-05 18:27:34.034705');
INSERT INTO "public"."sys_logininfor" VALUES (1897232540649443330, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '退出成功', '2025-03-05 18:27:41.427142');
INSERT INTO "public"."sys_logininfor" VALUES (1897233281862651906, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '1', '验证码已失效', '2025-03-05 18:30:38.152096');
INSERT INTO "public"."sys_logininfor" VALUES (1897233306076368898, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '1', '验证码错误', '2025-03-05 18:30:43.921215');
INSERT INTO "public"."sys_logininfor" VALUES (1897233322023112706, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-03-05 18:30:47.722086');
INSERT INTO "public"."sys_logininfor" VALUES (1897317929372360706, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-03-06 00:07:00.791999');
INSERT INTO "public"."sys_logininfor" VALUES (1897317973945229313, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '退出成功', '2025-03-06 00:07:11.40108');
INSERT INTO "public"."sys_logininfor" VALUES (1897318021277949953, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-03-06 00:07:22.686032');

-- ----------------------------
-- Table structure for sys_menu
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_menu";
CREATE TABLE "public"."sys_menu" (
  "id" int8 NOT NULL,
  "menu_name" varchar(50) COLLATE "pg_catalog"."default" NOT NULL,
  "parent_id" int8,
  "order_num" int4,
  "path" varchar(200) COLLATE "pg_catalog"."default",
  "component" varchar(255) COLLATE "pg_catalog"."default",
  "query" varchar(255) COLLATE "pg_catalog"."default",
  "is_frame" int4,
  "is_cache" int4,
  "menu_type" char(1) COLLATE "pg_catalog"."default",
  "visible" char(1) COLLATE "pg_catalog"."default",
  "status" char(1) COLLATE "pg_catalog"."default",
  "perms" varchar(100) COLLATE "pg_catalog"."default",
  "icon" varchar(100) COLLATE "pg_catalog"."default",
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6),
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6),
  "remark" varchar(500) COLLATE "pg_catalog"."default"
)
;
COMMENT ON COLUMN "public"."sys_menu"."id" IS '菜单ID';
COMMENT ON COLUMN "public"."sys_menu"."menu_name" IS '菜单名称';
COMMENT ON COLUMN "public"."sys_menu"."parent_id" IS '父菜单ID';
COMMENT ON COLUMN "public"."sys_menu"."order_num" IS '显示顺序';
COMMENT ON COLUMN "public"."sys_menu"."path" IS '路由地址';
COMMENT ON COLUMN "public"."sys_menu"."component" IS '组件路径';
COMMENT ON COLUMN "public"."sys_menu"."query" IS '路由参数';
COMMENT ON COLUMN "public"."sys_menu"."is_frame" IS '是否为外链（0是 1否）';
COMMENT ON COLUMN "public"."sys_menu"."is_cache" IS '是否缓存（0缓存 1不缓存）';
COMMENT ON COLUMN "public"."sys_menu"."menu_type" IS '菜单类型（M目录 C菜单 F按钮）';
COMMENT ON COLUMN "public"."sys_menu"."visible" IS '菜单状态（0显示 1隐藏）';
COMMENT ON COLUMN "public"."sys_menu"."status" IS '菜单状态（0正常 1停用）';
COMMENT ON COLUMN "public"."sys_menu"."perms" IS '权限标识';
COMMENT ON COLUMN "public"."sys_menu"."icon" IS '菜单图标';
COMMENT ON COLUMN "public"."sys_menu"."create_by" IS '创建者';
COMMENT ON COLUMN "public"."sys_menu"."create_time" IS '创建时间';
COMMENT ON COLUMN "public"."sys_menu"."update_by" IS '更新者';
COMMENT ON COLUMN "public"."sys_menu"."update_time" IS '更新时间';
COMMENT ON COLUMN "public"."sys_menu"."remark" IS '备注';
COMMENT ON TABLE "public"."sys_menu" IS '菜单权限表';

-- ----------------------------
-- Records of sys_menu
-- ----------------------------
INSERT INTO "public"."sys_menu" VALUES (1, '系统管理', 0, 10, 'system', NULL, '', 1, 0, 'M', '0', '0', '', 'system', 'admin', '2024-06-21 10:57:32', 'admin', '2024-08-09 10:28:26', '系统管理目录');
INSERT INTO "public"."sys_menu" VALUES (2, '系统监控', 0, 11, 'monitor', NULL, '', 1, 0, 'M', '0', '0', '', 'monitor', 'admin', '2024-06-21 10:57:32', 'admin', '2024-08-09 10:28:33', '系统监控目录');
INSERT INTO "public"."sys_menu" VALUES (3, '系统工具', 0, 1, 'tool', NULL, '', 1, 0, 'M', '0', '0', '', 'tool', 'admin', '2024-06-21 10:57:32', 'admin', '2024-07-16 16:58:52', '系统工具目录');
INSERT INTO "public"."sys_menu" VALUES (100, '用户管理', 1, 1, 'user', 'system/user/index', '', 1, 0, 'C', '0', '0', 'system:user:list', 'user', 'admin', '2024-06-21 10:57:32', '', NULL, '用户管理菜单');
INSERT INTO "public"."sys_menu" VALUES (101, '角色管理', 1, 2, 'role', 'system/role/index', '', 1, 0, 'C', '0', '0', 'system:role:list', 'peoples', 'admin', '2024-06-21 10:57:32', '', NULL, '角色管理菜单');
INSERT INTO "public"."sys_menu" VALUES (102, '菜单管理', 1, 3, 'menu', 'system/menu/index', '', 1, 0, 'C', '0', '0', 'system:menu:list', 'tree-table', 'admin', '2024-06-21 10:57:32', '', NULL, '菜单管理菜单');
INSERT INTO "public"."sys_menu" VALUES (103, '机构管理', 1, 4, 'dept', 'system/dept/index', '', 1, 0, 'C', '0', '0', 'system:dept:list', 'tree', 'admin', '2024-06-21 10:57:32', 'admin', '2024-07-01 10:01:26', '机构管理菜单');
INSERT INTO "public"."sys_menu" VALUES (104, '岗位管理', 1, 5, 'post', 'system/post/index', '', 1, 0, 'C', '0', '0', 'system:post:list', 'post', 'admin', '2024-06-21 10:57:32', 'admin', '2025-02-17 11:01:44', '岗位管理菜单');
INSERT INTO "public"."sys_menu" VALUES (106, '参数设置', 1, 7, 'config', 'system/config/index', '', 1, 0, 'C', '0', '0', 'system:config:list', 'edit', 'admin', '2024-06-21 10:57:32', '', NULL, '参数设置菜单');
INSERT INTO "public"."sys_menu" VALUES (107, '通知公告', 1, 8, 'notice', 'system/notice/index', '', 1, 0, 'C', '1', '0', 'system:notice:list', 'message', 'admin', '2024-06-21 10:57:32', 'admin', '2024-07-17 09:52:07', '通知公告菜单');
INSERT INTO "public"."sys_menu" VALUES (108, '日志管理', 1, 9, 'log', '', '', 1, 0, 'M', '0', '0', '', 'log', 'admin', '2024-06-21 10:57:32', '', NULL, '日志管理菜单');
INSERT INTO "public"."sys_menu" VALUES (109, '在线用户', 2, 1, 'online', 'monitor/online/index', '', 1, 0, 'C', '0', '0', 'monitor:online:list', 'online', 'admin', '2024-06-21 10:57:32', '', NULL, '在线用户菜单');
INSERT INTO "public"."sys_menu" VALUES (110, '定时任务', 2, 2, 'job', 'monitor/job/index', '', 1, 0, 'C', '0', '0', 'monitor:job:list', 'job', 'admin', '2024-06-21 10:57:32', '', NULL, '定时任务菜单');
INSERT INTO "public"."sys_menu" VALUES (111, '数据监控', 2, 3, 'druid', 'monitor/druid/index', '', 1, 0, 'C', '0', '0', 'monitor:druid:list', 'druid', 'admin', '2024-06-21 10:57:32', '', NULL, '数据监控菜单');
INSERT INTO "public"."sys_menu" VALUES (112, '服务监控', 2, 4, 'server', 'monitor/server/index', '', 1, 0, 'C', '0', '0', 'monitor:server:list', 'server', 'admin', '2024-06-21 10:57:32', '', NULL, '服务监控菜单');
INSERT INTO "public"."sys_menu" VALUES (113, '缓存监控', 2, 5, 'cache', 'monitor/cache/index', '', 1, 0, 'C', '0', '0', 'monitor:cache:list', 'redis', 'admin', '2024-06-21 10:57:32', '', NULL, '缓存监控菜单');
INSERT INTO "public"."sys_menu" VALUES (114, '缓存列表', 2, 6, 'cacheList', 'monitor/cache/list', '', 1, 0, 'C', '0', '0', 'monitor:cache:list', 'redis-list', 'admin', '2024-06-21 10:57:32', '', NULL, '缓存列表菜单');
INSERT INTO "public"."sys_menu" VALUES (115, '表单构建', 3, 1, 'build', 'tool/build/index', '', 1, 0, 'C', '0', '0', 'tool:build:list', 'build', 'admin', '2024-06-21 10:57:32', '', NULL, '表单构建菜单');
INSERT INTO "public"."sys_menu" VALUES (116, '代码生成', 3, 2, 'gen', 'tool/gen/index', '', 1, 0, 'C', '0', '0', 'tool:gen:list', 'code', 'admin', '2024-06-21 10:57:32', '', NULL, '代码生成菜单');
INSERT INTO "public"."sys_menu" VALUES (117, '系统接口', 3, 3, 'swagger', 'tool/swagger/index', '', 1, 0, 'C', '0', '0', 'tool:swagger:list', 'swagger', 'admin', '2024-06-21 10:57:32', '', NULL, '系统接口菜单');
INSERT INTO "public"."sys_menu" VALUES (500, '操作日志', 108, 1, 'operlog', 'monitor/operlog/index', '', 1, 0, 'C', '0', '0', 'monitor:operlog:list', 'form', 'admin', '2024-06-21 10:57:32', '', NULL, '操作日志菜单');
INSERT INTO "public"."sys_menu" VALUES (501, '登录日志', 108, 2, 'logininfor', 'monitor/logininfor/index', '', 1, 0, 'C', '0', '0', 'monitor:logininfor:list', 'logininfor', 'admin', '2024-06-21 10:57:32', '', NULL, '登录日志菜单');
INSERT INTO "public"."sys_menu" VALUES (1000, '用户查询', 100, 1, '', '', '', 1, 0, 'F', '0', '0', 'system:user:query', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (1001, '用户新增', 100, 2, '', '', '', 1, 0, 'F', '0', '0', 'system:user:add', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (1002, '用户修改', 100, 3, '', '', '', 1, 0, 'F', '0', '0', 'system:user:edit', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (1003, '用户删除', 100, 4, '', '', '', 1, 0, 'F', '0', '0', 'system:user:remove', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (1004, '用户导出', 100, 5, '', '', '', 1, 0, 'F', '0', '0', 'system:user:export', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (1005, '用户导入', 100, 6, '', '', '', 1, 0, 'F', '0', '0', 'system:user:import', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (1006, '重置密码', 100, 7, '', '', '', 1, 0, 'F', '0', '0', 'system:user:resetPwd', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (1007, '角色查询', 101, 1, '', '', '', 1, 0, 'F', '0', '0', 'system:role:query', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (1008, '角色新增', 101, 2, '', '', '', 1, 0, 'F', '0', '0', 'system:role:add', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (1009, '角色修改', 101, 3, '', '', '', 1, 0, 'F', '0', '0', 'system:role:edit', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (1010, '角色删除', 101, 4, '', '', '', 1, 0, 'F', '0', '0', 'system:role:remove', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (1011, '角色导出', 101, 5, '', '', '', 1, 0, 'F', '0', '0', 'system:role:export', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (1012, '菜单查询', 102, 1, '', '', '', 1, 0, 'F', '0', '0', 'system:menu:query', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (1013, '菜单新增', 102, 2, '', '', '', 1, 0, 'F', '0', '0', 'system:menu:add', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (1014, '菜单修改', 102, 3, '', '', '', 1, 0, 'F', '0', '0', 'system:menu:edit', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (1015, '菜单删除', 102, 4, '', '', '', 1, 0, 'F', '0', '0', 'system:menu:remove', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (1016, '机构查询', 103, 1, '', '', '', 1, 0, 'F', '0', '0', 'system:dept:query', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (1017, '机构新增', 103, 2, '', '', '', 1, 0, 'F', '0', '0', 'system:dept:add', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (1018, '机构修改', 103, 3, '', '', '', 1, 0, 'F', '0', '0', 'system:dept:edit', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (1019, '机构删除', 103, 4, '', '', '', 1, 0, 'F', '0', '0', 'system:dept:remove', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (1020, '岗位查询', 104, 1, '', '', '', 1, 0, 'F', '0', '0', 'system:post:query', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (1021, '岗位新增', 104, 2, '', '', '', 1, 0, 'F', '0', '0', 'system:post:add', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (1022, '岗位修改', 104, 3, '', '', '', 1, 0, 'F', '0', '0', 'system:post:edit', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (1023, '岗位删除', 104, 4, '', '', '', 1, 0, 'F', '0', '0', 'system:post:remove', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (1024, '岗位导出', 104, 5, '', '', '', 1, 0, 'F', '0', '0', 'system:post:export', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (1030, '参数查询', 106, 1, '#', '', '', 1, 0, 'F', '0', '0', 'system:config:query', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (1031, '参数新增', 106, 2, '#', '', '', 1, 0, 'F', '0', '0', 'system:config:add', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (1032, '参数修改', 106, 3, '#', '', '', 1, 0, 'F', '0', '0', 'system:config:edit', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (1033, '参数删除', 106, 4, '#', '', '', 1, 0, 'F', '0', '0', 'system:config:remove', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (1034, '参数导出', 106, 5, '#', '', '', 1, 0, 'F', '0', '0', 'system:config:export', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (1035, '公告查询', 107, 1, '#', '', '', 1, 0, 'F', '0', '0', 'system:notice:query', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (1036, '公告新增', 107, 2, '#', '', '', 1, 0, 'F', '0', '0', 'system:notice:add', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (1037, '公告修改', 107, 3, '#', '', '', 1, 0, 'F', '0', '0', 'system:notice:edit', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (1038, '公告删除', 107, 4, '#', '', '', 1, 0, 'F', '0', '0', 'system:notice:remove', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (1039, '操作查询', 500, 1, '#', '', '', 1, 0, 'F', '0', '0', 'monitor:operlog:query', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (1040, '操作删除', 500, 2, '#', '', '', 1, 0, 'F', '0', '0', 'monitor:operlog:remove', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (1041, '日志导出', 500, 3, '#', '', '', 1, 0, 'F', '0', '0', 'monitor:operlog:export', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (1042, '登录查询', 501, 1, '#', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:query', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (1043, '登录删除', 501, 2, '#', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:remove', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (1044, '日志导出', 501, 3, '#', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:export', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (1045, '账户解锁', 501, 4, '#', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:unlock', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (1046, '在线查询', 109, 1, '#', '', '', 1, 0, 'F', '0', '0', 'monitor:online:query', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (1047, '批量强退', 109, 2, '#', '', '', 1, 0, 'F', '0', '0', 'monitor:online:batchLogout', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (1048, '单条强退', 109, 3, '#', '', '', 1, 0, 'F', '0', '0', 'monitor:online:forceLogout', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (1049, '任务查询', 110, 1, '#', '', '', 1, 0, 'F', '0', '0', 'monitor:job:query', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (1050, '任务新增', 110, 2, '#', '', '', 1, 0, 'F', '0', '0', 'monitor:job:add', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (1051, '任务修改', 110, 3, '#', '', '', 1, 0, 'F', '0', '0', 'monitor:job:edit', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (1052, '任务删除', 110, 4, '#', '', '', 1, 0, 'F', '0', '0', 'monitor:job:remove', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (1053, '状态修改', 110, 5, '#', '', '', 1, 0, 'F', '0', '0', 'monitor:job:changeStatus', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (1054, '任务导出', 110, 6, '#', '', '', 1, 0, 'F', '0', '0', 'monitor:job:export', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (1055, '生成查询', 116, 1, '#', '', '', 1, 0, 'F', '0', '0', 'tool:gen:query', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (1056, '生成修改', 116, 2, '#', '', '', 1, 0, 'F', '0', '0', 'tool:gen:edit', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (1057, '生成删除', 116, 3, '#', '', '', 1, 0, 'F', '0', '0', 'tool:gen:remove', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (1058, '导入代码', 116, 4, '#', '', '', 1, 0, 'F', '0', '0', 'tool:gen:import', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (1059, '预览代码', 116, 5, '#', '', '', 1, 0, 'F', '0', '0', 'tool:gen:preview', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (1060, '生成代码', 116, 6, '#', '', '', 1, 0, 'F', '0', '0', 'tool:gen:code', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (2256, '字典管理', 1, 6, 'newDict', 'system/newDict/index', NULL, 1, 0, 'C', '0', '0', 'system:dict:list', 'dict', 'admin', '2024-07-22 11:06:53', 'admin', '2024-07-24 11:07:11', '');
INSERT INTO "public"."sys_menu" VALUES (2279, '字典查询', 2256, 1, '', NULL, NULL, 1, 0, 'F', '0', '0', 'system:dict:query', '#', 'admin', '2024-07-24 11:07:57', 'admin', '2024-07-24 11:08:09', '');
INSERT INTO "public"."sys_menu" VALUES (2280, '字典保存', 2256, 4, '', NULL, NULL, 1, 0, 'F', '0', '0', 'system:dict:addDictTypeAndData', '#', 'admin', '2024-07-24 11:08:43', 'ghn', '2024-07-24 11:24:31', '');
INSERT INTO "public"."sys_menu" VALUES (2281, '字典导出', 2256, 5, '', NULL, NULL, 1, 0, 'F', '0', '0', 'system:dict:export', '#', 'admin', '2024-07-24 11:09:05', 'ghn', '2024-07-24 11:25:08', '');
INSERT INTO "public"."sys_menu" VALUES (2282, '字典新增', 2256, 2, '', NULL, NULL, 1, 0, 'F', '0', '0', 'bus:dict:add', '#', 'ghn', '2024-07-24 11:25:00', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (2283, '字典数据删除', 2256, 3, '', NULL, NULL, 1, 0, 'F', '0', '0', 'bus:dict:remove', '#', 'ghn', '2024-07-24 11:25:35', '', NULL, '');
INSERT INTO "public"."sys_menu" VALUES (1897326646402842625, '123', 0, 7, '123', NULL, NULL, 1, 0, 'M', '0', '0', NULL, 'button', 'admin', '2025-03-06 00:41:39.085166', 'admin', '2025-03-06 00:41:51.17553', NULL);

-- ----------------------------
-- Table structure for sys_notice
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_notice";
CREATE TABLE "public"."sys_notice" (
  "id" int4 NOT NULL,
  "notice_title" varchar(50) COLLATE "pg_catalog"."default" NOT NULL,
  "notice_type" char(1) COLLATE "pg_catalog"."default" NOT NULL,
  "notice_content" bytea,
  "status" char(1) COLLATE "pg_catalog"."default",
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6),
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6),
  "remark" varchar(255) COLLATE "pg_catalog"."default"
)
;
COMMENT ON COLUMN "public"."sys_notice"."id" IS '公告ID';
COMMENT ON COLUMN "public"."sys_notice"."notice_title" IS '公告标题';
COMMENT ON COLUMN "public"."sys_notice"."notice_type" IS '公告类型（1通知 2公告）';
COMMENT ON COLUMN "public"."sys_notice"."notice_content" IS '公告内容';
COMMENT ON COLUMN "public"."sys_notice"."status" IS '公告状态（0正常 1关闭）';
COMMENT ON COLUMN "public"."sys_notice"."create_by" IS '创建者';
COMMENT ON COLUMN "public"."sys_notice"."create_time" IS '创建时间';
COMMENT ON COLUMN "public"."sys_notice"."update_by" IS '更新者';
COMMENT ON COLUMN "public"."sys_notice"."update_time" IS '更新时间';
COMMENT ON COLUMN "public"."sys_notice"."remark" IS '备注';
COMMENT ON TABLE "public"."sys_notice" IS '通知公告表';

-- ----------------------------
-- Records of sys_notice
-- ----------------------------

-- ----------------------------
-- Table structure for sys_oper_log
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_oper_log";
CREATE TABLE "public"."sys_oper_log" (
  "oper_id" int8 NOT NULL,
  "title" varchar(50) COLLATE "pg_catalog"."default",
  "business_type" int4,
  "method" varchar(100) COLLATE "pg_catalog"."default",
  "request_method" varchar(10) COLLATE "pg_catalog"."default",
  "operator_type" int4,
  "oper_name" varchar(50) COLLATE "pg_catalog"."default",
  "dept_name" varchar(50) COLLATE "pg_catalog"."default",
  "oper_url" varchar(255) COLLATE "pg_catalog"."default",
  "oper_ip" varchar(128) COLLATE "pg_catalog"."default",
  "oper_location" varchar(255) COLLATE "pg_catalog"."default",
  "oper_param" varchar(2000) COLLATE "pg_catalog"."default",
  "json_result" varchar(2000) COLLATE "pg_catalog"."default",
  "status" int4,
  "error_msg" varchar(2000) COLLATE "pg_catalog"."default",
  "oper_time" timestamp(6),
  "cost_time" int8
)
;
COMMENT ON COLUMN "public"."sys_oper_log"."oper_id" IS '日志主键';
COMMENT ON COLUMN "public"."sys_oper_log"."title" IS '模块标题';
COMMENT ON COLUMN "public"."sys_oper_log"."business_type" IS '业务类型（0其它 1新增 2修改 3删除）';
COMMENT ON COLUMN "public"."sys_oper_log"."method" IS '方法名称';
COMMENT ON COLUMN "public"."sys_oper_log"."request_method" IS '请求方式';
COMMENT ON COLUMN "public"."sys_oper_log"."operator_type" IS '操作类别（0其它 1后台用户 2手机端用户）';
COMMENT ON COLUMN "public"."sys_oper_log"."oper_name" IS '操作人员';
COMMENT ON COLUMN "public"."sys_oper_log"."dept_name" IS '机构名称';
COMMENT ON COLUMN "public"."sys_oper_log"."oper_url" IS '请求URL';
COMMENT ON COLUMN "public"."sys_oper_log"."oper_ip" IS '主机地址';
COMMENT ON COLUMN "public"."sys_oper_log"."oper_location" IS '操作地点';
COMMENT ON COLUMN "public"."sys_oper_log"."oper_param" IS '请求参数';
COMMENT ON COLUMN "public"."sys_oper_log"."json_result" IS '返回参数';
COMMENT ON COLUMN "public"."sys_oper_log"."status" IS '操作状态（0正常 1异常）';
COMMENT ON COLUMN "public"."sys_oper_log"."error_msg" IS '错误消息';
COMMENT ON COLUMN "public"."sys_oper_log"."oper_time" IS '操作时间';
COMMENT ON COLUMN "public"."sys_oper_log"."cost_time" IS '消耗时间';
COMMENT ON TABLE "public"."sys_oper_log" IS '操作日志记录';

-- ----------------------------
-- Records of sys_oper_log
-- ----------------------------
INSERT INTO "public"."sys_oper_log" VALUES (5239, '菜单管理', 2, 'com.sq.admin.system.controller.SysMenuController.edit()', 'PUT', 1, 'admin', NULL, '/system/menu', '127.0.0.1', '内网IP', '{"children":[],"component":"system/newDict/index","createTime":"2024-07-22 11:06:53","icon":"dict","isCache":"0","isFrame":"1","menuId":2256,"menuName":"字典管理","menuType":"C","orderNum":6,"params":{},"parentId":1,"path":"newDict","perms":"system:dict:list","status":"0","visible":"0"}', '{"msg":"修改菜单''字典管理''失败，菜单名称已存在","code":500}', 0, NULL, '2025-02-17 12:03:00', 9);
INSERT INTO "public"."sys_oper_log" VALUES (5240, '机构管理', 2, 'com.sq.admin.system.controller.SysDeptController.edit()', 'PUT', 1, 'admin', NULL, '/system/dept', '127.0.0.1', '内网IP', '{"ancestors":"0","children":[],"deptId":100,"deptName":"呼和浩特海关","orderNum":0,"params":{},"phone":"15888888888","status":"0"}', NULL, 1, 'Cannot invoke "java.lang.Long.equals(Object)" because the return value of "com.sq.common.core.domain.entity.SysDept.getParentId()" is null', '2025-02-17 12:05:30', 8);
INSERT INTO "public"."sys_oper_log" VALUES (5241, '机构管理', 2, 'com.sq.admin.system.controller.SysDeptController.edit()', 'PUT', 1, 'admin', NULL, '/system/dept', '127.0.0.1', '内网IP', '{"ancestors":"0","children":[],"deptId":100,"deptName":"呼和浩特海关","orderNum":0,"params":{},"phone":"15888888888","status":"0"}', NULL, 1, 'Cannot invoke "java.lang.Long.equals(Object)" because the return value of "com.sq.common.core.domain.entity.SysDept.getParentId()" is null', '2025-02-17 12:05:32', 10);
INSERT INTO "public"."sys_oper_log" VALUES (5242, '机构管理', 1, 'com.sq.admin.system.controller.SysDeptController.add()', 'POST', 1, 'admin', NULL, '/system/dept', '127.0.0.1', '内网IP', '{"ancestors":"0,100","children":[],"createBy":"admin","deptName":"二连浩特口岸","orderNum":1,"params":{},"parentId":100,"status":"0"}', '{"msg":"操作成功","code":200}', 0, NULL, '2025-02-17 12:06:25', 41);
INSERT INTO "public"."sys_oper_log" VALUES (1897234200784281601, '用户管理', 1, 'com.sq.admin.system.controller.SysUserController.add()', 'POST', 1, 'admin', NULL, '/system/user', '127.0.0.1', '内网IP', '{"admin":false,"createBy":"admin","nickName":"123","params":{},"postIds":[],"roleIds":[],"status":"0","userName":"123"}', NULL, 1, '
### Error updating database.  Cause: org.postgresql.util.PSQLException: ERROR: null value in column "user_id" violates not-null constraint
  Detail: Failing row contains (null, null, 123, 123, null, null, null, null, null, $2a$10$5/hgxXBBSMJqoQEmhU72iOLxNW5VCUUGKUs9VXcmrK0kwXjNvV2zS, 0, null, null, null, admin, 2025-03-05 18:34:17.090381, null, null, null).
### The error may exist in file [D:\workspace\open-plat\sq-fast\sq-system\target\classes\com\sq\system\mapper\xml\SysUserMapper.xml]
### The error may involve com.sq.system.mapper.SysUserMapper.insertUser-Inline
### The error occurred while setting parameters
### SQL: insert into sys_user(                  user_name,       nick_name,                               password,       status,       create_by,            create_time    )values(                  ?,       ?,                               ?,       ?,       ?,            now()    )
### Cause: org.postgresql.util.PSQLException: ERROR: null value in column "user_id" violates not-null constraint
  Detail: Failing row contains (null, null, 123, 123, null, null, null, null, null, $2a$10$5/hgxXBBSMJqoQEmhU72iOLxNW5VCUUGKUs9VXcmrK0kwXjNvV2zS, 0, null, null, null, admin, 2025-03-05 18:34:17.090381, null, null, null).
; ERROR: null value in column "user_id" violates not-null constraint
  Detail: Failing row contains (null, null, 123, 123, null, null, null, null, null, $2a$10$5/hgxXBBSMJqoQEmhU72iOLxNW5VCUUGKUs9VXcmrK0kwXjNvV2zS, 0, null, null, null, admin, 2025-03-05 18:34:17.090381, null, null, null).; nested exception is org.postgresql.util.PSQLException: ERROR: null value in column "user_id" violates not-null constraint
  Detail: Failing row contains (null, null, 123, 123, null, null, null, null, null, $2a$10$5/hgxXBBSMJqoQEmhU72iOLxNW5VCUUGKUs9VXcmrK0kwXjNvV2zS, 0, null, null, null, admin, 2025-03-05 18:34:17.090381, null, null, null).', '2025-03-05 18:34:17.23961', 217);
INSERT INTO "public"."sys_oper_log" VALUES (1897234904789843970, '用户管理', 1, 'com.sq.admin.system.controller.SysUserController.add()', 'POST', 1, 'admin', NULL, '/system/user', '127.0.0.1', '内网IP', '{"admin":false,"createBy":"admin","nickName":"123","params":{},"postIds":[],"roleIds":[],"status":"0","userId":1897234904370413569,"userName":"123"}', '{"msg":"操作成功","code":200}', 0, NULL, '2025-03-05 18:37:05.080032', 284);
INSERT INTO "public"."sys_oper_log" VALUES (1897318080786735106, '用户管理', 1, 'com.sq.admin.system.controller.SysUserController.add()', 'POST', 1, 'admin', NULL, '/system/user', '127.0.0.1', '内网IP', '{"admin":false,"createBy":"admin","deptId":100,"nickName":"123","params":{},"postIds":[],"roleIds":[],"status":"0","userId":1897318079738159105,"userName":"123"}', '{"msg":"操作成功","code":200}', 0, NULL, '2025-03-06 00:07:36.882614', 553);
INSERT INTO "public"."sys_oper_log" VALUES (1897319007342043137, '用户管理', 3, 'com.sq.admin.system.controller.SysUserController.remove()', 'DELETE', 1, 'admin', NULL, '/system/user/1897234904370413569', '127.0.0.1', '内网IP', '{}', '{"msg":"操作成功","code":200}', 0, NULL, '2025-03-06 00:11:17.785025', 471);
INSERT INTO "public"."sys_oper_log" VALUES (1897321941794676738, '用户管理', 1, 'com.sq.admin.system.controller.SysUserController.add()', 'POST', 1, 'admin', NULL, '/system/user', '127.0.0.1', '内网IP', '{"admin":false,"createBy":"admin","deptId":203,"nickName":"哈哈哈","params":{},"postIds":[],"roleIds":[],"status":"0","userId":1897321940574134273,"userName":"haha"}', '{"msg":"操作成功","code":200}', 0, NULL, '2025-03-06 00:22:57.418928', 551);
INSERT INTO "public"."sys_oper_log" VALUES (1897322071390281729, '角色管理', 1, 'com.sq.admin.system.controller.SysRoleController.add()', 'POST', 1, 'admin', NULL, '/system/role', '127.0.0.1', '内网IP', '{"admin":false,"createBy":"admin","deptCheckStrictly":true,"deptIds":[],"flag":false,"menuCheckStrictly":true,"menuIds":[],"params":{},"roleKey":"123","roleName":"123","roleSort":0,"status":"0"}', NULL, 1, '
### Error updating database.  Cause: org.postgresql.util.PSQLException: ERROR: null value in column "role_id" violates not-null constraint
  Detail: Failing row contains (null, null, 123, 123, 0, null, 1, 1, 0, null, admin, 2025-03-06 00:23:28.00332, null, null, null).
### The error may exist in file [D:\workspace\open-plat\sq-fast\sq-system\target\classes\com\sq\system\mapper\xml\SysRoleMapper.xml]
### The error may involve com.sq.system.mapper.SysRoleMapper.insertRole-Inline
### The error occurred while setting parameters
### SQL: insert into sys_role(                                           role_name,                                 role_key,                                 role_sort,                                           menu_check_strictly,                                 dept_check_strictly,                                 status,                                           create_by,                   create_time)values(                                           ?,                                 ?,                                 ?,                                           ?,                                 ?,                                 ?,                                           ?,                   now())
### Cause: org.postgresql.util.PSQLException: ERROR: null value in column "role_id" violates not-null constraint
  Detail: Failing row contains (null, null, 123, 123, 0, null, 1, 1, 0, null, admin, 2025-03-06 00:23:28.00332, null, null, null).
; ERROR: null value in column "role_id" violates not-null constraint
  Detail: Failing row contains (null, null, 123, 123, 0, null, 1, 1, 0, null, admin, 2025-03-06 00:23:28.00332, null, null, null).; nested exception is org.postgresql.util.PSQLException: ERROR: null value in column "role_id" violates not-null constraint
  Detail: Failing row contains (null, null, 123, 123, 0, null, 1, 1, 0, null, admin, 2025-03-06 00:23:28.00332, null, null, null).', '2025-03-06 00:23:28.308351', 396);
INSERT INTO "public"."sys_oper_log" VALUES (1897322669267345410, '菜单管理', 1, 'com.sq.admin.system.controller.SysMenuController.add()', 'POST', 1, 'admin', NULL, '/system/menu', '127.0.0.1', '内网IP', '{"children":[],"createBy":"admin","icon":"button","isCache":"0","isFrame":"1","menuName":"123","menuType":"M","orderNum":1,"params":{},"parentId":0,"path":"123","status":"0","visible":"0"}', NULL, 1, '
### Error updating database.  Cause: org.postgresql.util.PSQLException: ERROR: null value in column "menu_id" violates not-null constraint
  Detail: Failing row contains (null, 123, null, 1, 123, null, null, 1, 0, M, 0, 0, null, button, admin, 2025-03-06 00:25:50.71887, null, null, null).
### The error may exist in file [D:\workspace\open-plat\sq-fast\sq-system\target\classes\com\sq\system\mapper\xml\SysMenuMapper.xml]
### The error may involve com.sq.system.mapper.SysMenuMapper.insertMenu-Inline
### The error occurred while setting parameters
### SQL: insert into sys_menu(            menu_name,     order_num,     path,             is_frame,     is_cache,     menu_type,     visible,     status,         icon,         create_by,    create_time   )values(            ?,     ?,     ?,             ?,     ?,     ?,     ?,     ?,         ?,         ?,    now()   )
### Cause: org.postgresql.util.PSQLException: ERROR: null value in column "menu_id" violates not-null constraint
  Detail: Failing row contains (null, 123, null, 1, 123, null, null, 1, 0, M, 0, 0, null, button, admin, 2025-03-06 00:25:50.71887, null, null, null).
; ERROR: null value in column "menu_id" violates not-null constraint
  Detail: Failing row contains (null, 123, null, 1, 123, null, null, 1, 0, M, 0, 0, null, button, admin, 2025-03-06 00:25:50.71887, null, null, null).; nested exception is org.postgresql.util.PSQLException: ERROR: null value in column "menu_id" violates not-null constraint
  Detail: Failing row contains (null, 123, null, 1, 123, null, null, 1, 0, M, 0, 0, null, button, admin, 2025-03-06 00:25:50.71887, null, null, null).', '2025-03-06 00:25:50.848019', 167);
INSERT INTO "public"."sys_oper_log" VALUES (1897322933906956290, '机构管理', 2, 'com.sq.admin.system.controller.SysDeptController.edit()', 'PUT', 1, 'admin', NULL, '/system/dept', '127.0.0.1', '内网IP', '{"ancestors":"0","children":[],"deptId":100,"deptName":"自治区口岸办","orderNum":0,"params":{},"parentId":0,"phone":"15888888888","status":"0","updateBy":"admin"}', '{"msg":"操作成功","code":200}', 0, NULL, '2025-03-06 00:26:53.954787', 358);
INSERT INTO "public"."sys_oper_log" VALUES (1897326855174324226, '岗位管理', 1, 'com.sq.admin.system.controller.SysPostController.add()', 'POST', 1, 'admin', NULL, '/system/post', '127.0.0.1', '内网IP', '{"createBy":"admin","flag":false,"params":{},"postCode":"123","postId":1897326854599704577,"postName":"123","postSort":1,"status":"0"}', '{"msg":"操作成功","code":200}', 0, NULL, '2025-03-06 00:42:28.853252', 254);
INSERT INTO "public"."sys_oper_log" VALUES (1897322712422539265, '菜单管理', 1, 'com.sq.admin.system.controller.SysMenuController.add()', 'POST', 1, 'admin', NULL, '/system/menu', '127.0.0.1', '内网IP', '{"children":[],"createBy":"admin","icon":"button","isCache":"0","isFrame":"1","menuName":"123","menuType":"M","orderNum":1,"params":{},"parentId":0,"path":"123","status":"0","visible":"0"}', NULL, 1, '
### Error updating database.  Cause: org.postgresql.util.PSQLException: ERROR: null value in column "menu_id" violates not-null constraint
  Detail: Failing row contains (null, 123, null, 1, 123, null, null, 1, 0, M, 0, 0, null, button, admin, 2025-03-06 00:26:00.983108, null, null, null).
### The error may exist in file [D:\workspace\open-plat\sq-fast\sq-system\target\classes\com\sq\system\mapper\xml\SysMenuMapper.xml]
### The error may involve com.sq.system.mapper.SysMenuMapper.insertMenu-Inline
### The error occurred while setting parameters
### SQL: insert into sys_menu(            menu_name,     order_num,     path,             is_frame,     is_cache,     menu_type,     visible,     status,         icon,         create_by,    create_time   )values(            ?,     ?,     ?,             ?,     ?,     ?,     ?,     ?,         ?,         ?,    now()   )
### Cause: org.postgresql.util.PSQLException: ERROR: null value in column "menu_id" violates not-null constraint
  Detail: Failing row contains (null, 123, null, 1, 123, null, null, 1, 0, M, 0, 0, null, button, admin, 2025-03-06 00:26:00.983108, null, null, null).
; ERROR: null value in column "menu_id" violates not-null constraint
  Detail: Failing row contains (null, 123, null, 1, 123, null, null, 1, 0, M, 0, 0, null, button, admin, 2025-03-06 00:26:00.983108, null, null, null).; nested exception is org.postgresql.util.PSQLException: ERROR: null value in column "menu_id" violates not-null constraint
  Detail: Failing row contains (null, 123, null, 1, 123, null, null, 1, 0, M, 0, 0, null, button, admin, 2025-03-06 00:26:00.983108, null, null, null).', '2025-03-06 00:26:01.148152', 185);
INSERT INTO "public"."sys_oper_log" VALUES (1897322974642036738, '机构管理', 1, 'com.sq.admin.system.controller.SysDeptController.add()', 'POST', 1, 'admin', NULL, '/system/dept', '127.0.0.1', '内网IP', '{"ancestors":"0,100","children":[],"createBy":"admin","deptName":"123","orderNum":2,"params":{},"parentId":100,"status":"0"}', NULL, 1, '
### Error updating database.  Cause: org.postgresql.util.PSQLException: ERROR: null value in column "dept_id" violates not-null constraint
  Detail: Failing row contains (null, 100, 0,100, null, 123, 2, null, null, null, null, 0, null, admin, 2025-03-06 00:27:03.528146, null, null).
### The error may exist in file [D:\workspace\open-plat\sq-fast\sq-system\target\classes\com\sq\system\mapper\xml\SysDeptMapper.xml]
### The error may involve com.sq.system.mapper.SysDeptMapper.insertDept-Inline
### The error occurred while setting parameters
### SQL: insert into sys_dept(            parent_id,       dept_name,       ancestors,       order_num,                         status,       create_by,      create_time    )values(            ?,       ?,       ?,       ?,                         ?,       ?,      now()    )
### Cause: org.postgresql.util.PSQLException: ERROR: null value in column "dept_id" violates not-null constraint
  Detail: Failing row contains (null, 100, 0,100, null, 123, 2, null, null, null, null, 0, null, admin, 2025-03-06 00:27:03.528146, null, null).
; ERROR: null value in column "dept_id" violates not-null constraint
  Detail: Failing row contains (null, 100, 0,100, null, 123, 2, null, null, null, null, 0, null, admin, 2025-03-06 00:27:03.528146, null, null).; nested exception is org.postgresql.util.PSQLException: ERROR: null value in column "dept_id" violates not-null constraint
  Detail: Failing row contains (null, 100, 0,100, null, 123, 2, null, null, null, null, 0, null, admin, 2025-03-06 00:27:03.528146, null, null).', '2025-03-06 00:27:03.656353', 216);
INSERT INTO "public"."sys_oper_log" VALUES (1897322994002944002, '机构管理', 1, 'com.sq.admin.system.controller.SysDeptController.add()', 'POST', 1, 'admin', NULL, '/system/dept', '127.0.0.1', '内网IP', '{"ancestors":"0,100","children":[],"createBy":"admin","deptName":"123","orderNum":2,"params":{},"parentId":100,"status":"0"}', NULL, 1, '
### Error updating database.  Cause: org.postgresql.util.PSQLException: ERROR: null value in column "dept_id" violates not-null constraint
  Detail: Failing row contains (null, 100, 0,100, null, 123, 2, null, null, null, null, 0, null, admin, 2025-03-06 00:27:08.158362, null, null).
### The error may exist in file [D:\workspace\open-plat\sq-fast\sq-system\target\classes\com\sq\system\mapper\xml\SysDeptMapper.xml]
### The error may involve com.sq.system.mapper.SysDeptMapper.insertDept-Inline
### The error occurred while setting parameters
### SQL: insert into sys_dept(            parent_id,       dept_name,       ancestors,       order_num,                         status,       create_by,      create_time    )values(            ?,       ?,       ?,       ?,                         ?,       ?,      now()    )
### Cause: org.postgresql.util.PSQLException: ERROR: null value in column "dept_id" violates not-null constraint
  Detail: Failing row contains (null, 100, 0,100, null, 123, 2, null, null, null, null, 0, null, admin, 2025-03-06 00:27:08.158362, null, null).
; ERROR: null value in column "dept_id" violates not-null constraint
  Detail: Failing row contains (null, 100, 0,100, null, 123, 2, null, null, null, null, 0, null, admin, 2025-03-06 00:27:08.158362, null, null).; nested exception is org.postgresql.util.PSQLException: ERROR: null value in column "dept_id" violates not-null constraint
  Detail: Failing row contains (null, 100, 0,100, null, 123, 2, null, null, null, null, 0, null, admin, 2025-03-06 00:27:08.158362, null, null).', '2025-03-06 00:27:08.271351', 208);
INSERT INTO "public"."sys_oper_log" VALUES (1897323316440064001, '岗位管理', 1, 'com.sq.admin.system.controller.SysPostController.add()', 'POST', 1, 'admin', NULL, '/system/post', '127.0.0.1', '内网IP', '{"createBy":"admin","flag":false,"params":{},"postCode":"123","postName":"1231","postSort":0,"status":"0"}', NULL, 1, '
### Error updating database.  Cause: org.postgresql.util.PSQLException: ERROR: null value in column "post_id" violates not-null constraint
  Detail: Failing row contains (null, 123, 1231, 0, 0, admin, 2025-03-06 00:28:25.030723, null, null, null).
### The error may exist in file [D:\workspace\open-plat\sq-fast\sq-system\target\classes\com\sq\system\mapper\xml\SysPostMapper.xml]
### The error may involve com.sq.system.mapper.SysPostMapper.insertPost-Inline
### The error occurred while setting parameters
### SQL: insert into sys_post(            post_code,       post_name,       post_sort,       status,             create_by,      create_time    )values(            ?,       ?,       ?,       ?,             ?,      now()    )
### Cause: org.postgresql.util.PSQLException: ERROR: null value in column "post_id" violates not-null constraint
  Detail: Failing row contains (null, 123, 1231, 0, 0, admin, 2025-03-06 00:28:25.030723, null, null, null).
; ERROR: null value in column "post_id" violates not-null constraint
  Detail: Failing row contains (null, 123, 1231, 0, 0, admin, 2025-03-06 00:28:25.030723, null, null, null).; nested exception is org.postgresql.util.PSQLException: ERROR: null value in column "post_id" violates not-null constraint
  Detail: Failing row contains (null, 123, 1231, 0, 0, admin, 2025-03-06 00:28:25.030723, null, null, null).', '2025-03-06 00:28:25.218712', 240);
INSERT INTO "public"."sys_oper_log" VALUES (1897326647002628097, '菜单管理', 1, 'com.sq.admin.system.controller.SysMenuController.add()', 'POST', 1, 'admin', NULL, '/system/menu', '127.0.0.1', '内网IP', '{"children":[],"createBy":"admin","icon":"button","isCache":"0","isFrame":"1","menuId":1897326646402842625,"menuName":"123","menuType":"M","orderNum":7,"params":{},"parentId":0,"path":"123","status":"0","visible":"0"}', '{"msg":"操作成功","code":200}', 0, NULL, '2025-03-06 00:41:39.226132', 204);
INSERT INTO "public"."sys_oper_log" VALUES (1897323530089521153, '字典类型', 1, 'com.sq.admin.system.controller.SysDictTypeController.add()', 'POST', 1, 'admin', NULL, '/system/dict/type', '127.0.0.1', '内网IP', '{"createBy":"admin","dictName":"ad","dictType":"asd","params":{},"status":"0"}', NULL, 1, '
### Error updating database.  Cause: org.postgresql.util.PSQLException: ERROR: null value in column "dict_id" violates not-null constraint
  Detail: Failing row contains (null, ad, asd, 0, admin, 2025-03-06 00:29:15.934568, null, null, null).
### The error may exist in file [D:\workspace\open-plat\sq-fast\sq-system\target\classes\com\sq\system\mapper\xml\SysDictTypeMapper.xml]
### The error may involve com.sq.system.mapper.SysDictTypeMapper.insertDictType-Inline
### The error occurred while setting parameters
### SQL: insert into sys_dict_type(      dict_name,       dict_type,       status,             create_by,      create_time    )values(      ?,       ?,       ?,             ?,      now()    )
### Cause: org.postgresql.util.PSQLException: ERROR: null value in column "dict_id" violates not-null constraint
  Detail: Failing row contains (null, ad, asd, 0, admin, 2025-03-06 00:29:15.934568, null, null, null).
; ERROR: null value in column "dict_id" violates not-null constraint
  Detail: Failing row contains (null, ad, asd, 0, admin, 2025-03-06 00:29:15.934568, null, null, null).; nested exception is org.postgresql.util.PSQLException: ERROR: null value in column "dict_id" violates not-null constraint
  Detail: Failing row contains (null, ad, asd, 0, admin, 2025-03-06 00:29:15.934568, null, null, null).', '2025-03-06 00:29:16.186793', 190);
INSERT INTO "public"."sys_oper_log" VALUES (1897323552440967170, '字典类型', 1, 'com.sq.admin.system.controller.SysDictTypeController.add()', 'POST', 1, 'admin', NULL, '/system/dict/type', '127.0.0.1', '内网IP', '{"createBy":"admin","dictName":"ad","dictType":"asd","params":{},"status":"0"}', NULL, 1, '
### Error updating database.  Cause: org.postgresql.util.PSQLException: ERROR: null value in column "dict_id" violates not-null constraint
  Detail: Failing row contains (null, ad, asd, 0, admin, 2025-03-06 00:29:21.281753, null, null, null).
### The error may exist in file [D:\workspace\open-plat\sq-fast\sq-system\target\classes\com\sq\system\mapper\xml\SysDictTypeMapper.xml]
### The error may involve com.sq.system.mapper.SysDictTypeMapper.insertDictType-Inline
### The error occurred while setting parameters
### SQL: insert into sys_dict_type(      dict_name,       dict_type,       status,             create_by,      create_time    )values(      ?,       ?,       ?,             ?,      now()    )
### Cause: org.postgresql.util.PSQLException: ERROR: null value in column "dict_id" violates not-null constraint
  Detail: Failing row contains (null, ad, asd, 0, admin, 2025-03-06 00:29:21.281753, null, null, null).
; ERROR: null value in column "dict_id" violates not-null constraint
  Detail: Failing row contains (null, ad, asd, 0, admin, 2025-03-06 00:29:21.281753, null, null, null).; nested exception is org.postgresql.util.PSQLException: ERROR: null value in column "dict_id" violates not-null constraint
  Detail: Failing row contains (null, ad, asd, 0, admin, 2025-03-06 00:29:21.281753, null, null, null).', '2025-03-06 00:29:21.506841', 193);
INSERT INTO "public"."sys_oper_log" VALUES (1897324291078873089, '参数管理', 1, 'com.sq.admin.system.controller.SysConfigController.add()', 'POST', 1, 'admin', NULL, '/system/config', '127.0.0.1', '内网IP', '{"configKey":"123","configName":"123","configType":"Y","configValue":"123","createBy":"admin","params":{}}', NULL, 1, '
### Error updating database.  Cause: org.postgresql.util.PSQLException: ERROR: null value in column "config_id" violates not-null constraint
  Detail: Failing row contains (null, 123, 123, 123, Y, admin, 2025-03-06 00:32:17.380954, null, null, null).
### The error may exist in file [D:\workspace\open-plat\sq-fast\sq-system\target\classes\com\sq\system\mapper\xml\SysConfigMapper.xml]
### The error may involve com.sq.system.mapper.SysConfigMapper.insertConfig-Inline
### The error occurred while setting parameters
### SQL: insert into sys_config (     config_name,      config_key,      config_value,      config_type,      create_by,           create_time         )values(     ?,      ?,      ?,      ?,      ?,           now()   )
### Cause: org.postgresql.util.PSQLException: ERROR: null value in column "config_id" violates not-null constraint
  Detail: Failing row contains (null, 123, 123, 123, Y, admin, 2025-03-06 00:32:17.380954, null, null, null).
; ERROR: null value in column "config_id" violates not-null constraint
  Detail: Failing row contains (null, 123, 123, 123, Y, admin, 2025-03-06 00:32:17.380954, null, null, null).; nested exception is org.postgresql.util.PSQLException: ERROR: null value in column "config_id" violates not-null constraint
  Detail: Failing row contains (null, 123, 123, 123, Y, admin, 2025-03-06 00:32:17.380954, null, null, null).', '2025-03-06 00:32:17.593966', 153);
INSERT INTO "public"."sys_oper_log" VALUES (1897324322229968898, '参数管理', 1, 'com.sq.admin.system.controller.SysConfigController.add()', 'POST', 1, 'admin', NULL, '/system/config', '127.0.0.1', '内网IP', '{"configKey":"123","configName":"123","configType":"Y","configValue":"123","createBy":"admin","params":{}}', NULL, 1, '
### Error updating database.  Cause: org.postgresql.util.PSQLException: ERROR: null value in column "config_id" violates not-null constraint
  Detail: Failing row contains (null, 123, 123, 123, Y, admin, 2025-03-06 00:32:24.81811, null, null, null).
### The error may exist in file [D:\workspace\open-plat\sq-fast\sq-system\target\classes\com\sq\system\mapper\xml\SysConfigMapper.xml]
### The error may involve com.sq.system.mapper.SysConfigMapper.insertConfig-Inline
### The error occurred while setting parameters
### SQL: insert into sys_config (     config_name,      config_key,      config_value,      config_type,      create_by,           create_time         )values(     ?,      ?,      ?,      ?,      ?,           now()   )
### Cause: org.postgresql.util.PSQLException: ERROR: null value in column "config_id" violates not-null constraint
  Detail: Failing row contains (null, 123, 123, 123, Y, admin, 2025-03-06 00:32:24.81811, null, null, null).
; ERROR: null value in column "config_id" violates not-null constraint
  Detail: Failing row contains (null, 123, 123, 123, Y, admin, 2025-03-06 00:32:24.81811, null, null, null).; nested exception is org.postgresql.util.PSQLException: ERROR: null value in column "config_id" violates not-null constraint
  Detail: Failing row contains (null, 123, 123, 123, Y, admin, 2025-03-06 00:32:24.81811, null, null, null).', '2025-03-06 00:32:25.037206', 176);
INSERT INTO "public"."sys_oper_log" VALUES (1897324917431042049, '角色管理', 1, 'com.sq.admin.system.controller.SysRoleController.add()', 'POST', 1, 'admin', NULL, '/system/role', '127.0.0.1', '内网IP', '{"admin":false,"createBy":"admin","deptCheckStrictly":true,"deptIds":[],"flag":false,"menuCheckStrictly":true,"menuIds":[3,115,116,1055,1056,1057,1058,1059,1060,117],"params":{},"roleId":1897324915958841346,"roleKey":"123","roleName":"123","roleSort":0,"status":"0"}', '{"msg":"操作成功","code":200}', 0, NULL, '2025-03-06 00:34:46.869453', 530);
INSERT INTO "public"."sys_oper_log" VALUES (1897326528006029314, '角色管理', 1, 'com.sq.admin.system.controller.SysRoleController.add()', 'POST', 1, 'admin', NULL, '/system/role', '127.0.0.1', '内网IP', '{"admin":false,"createBy":"admin","deptCheckStrictly":true,"deptIds":[],"flag":false,"menuCheckStrictly":true,"menuIds":[3,115,116,1055,1056,1057,1058,1059,1060,117],"params":{},"roleId":1897326526538022914,"roleKey":"123","roleName":"123","roleSort":0,"status":"0"}', '{"msg":"操作成功","code":200}', 0, NULL, '2025-03-06 00:41:10.85768', 514);
INSERT INTO "public"."sys_oper_log" VALUES (1897326569399615490, '角色管理', 2, 'com.sq.admin.system.controller.SysRoleController.edit()', 'PUT', 1, 'admin', NULL, '/system/role', '127.0.0.1', '内网IP', '{"admin":false,"createBy":"admin","createTime":"2025-03-06 00:41:10","delFlag":"0","deptCheckStrictly":true,"flag":false,"menuCheckStrictly":true,"menuIds":[3,115,116,1055,1056,1057,1058,1059,1060,117],"params":{},"roleId":1897326526538022914,"roleKey":"123","roleName":"123","roleSort":0,"status":"0","updateBy":"admin"}', '{"msg":"操作成功","code":200}', 0, NULL, '2025-03-06 00:41:20.735831', 493);
INSERT INTO "public"."sys_oper_log" VALUES (1897326697732734978, '菜单管理', 2, 'com.sq.admin.system.controller.SysMenuController.edit()', 'PUT', 1, 'admin', NULL, '/system/menu', '127.0.0.1', '内网IP', '{"children":[],"createTime":"2025-03-06 00:41:39","icon":"button","isCache":"0","isFrame":"1","menuId":1897326646402842625,"menuName":"123","menuType":"M","orderNum":7,"params":{},"parentId":0,"path":"123","status":"0","updateBy":"admin","visible":"0"}', '{"msg":"操作成功","code":200}', 0, NULL, '2025-03-06 00:41:51.321016', 151);
INSERT INTO "public"."sys_oper_log" VALUES (1897326790334578689, '机构管理', 1, 'com.sq.admin.system.controller.SysDeptController.add()', 'POST', 1, 'admin', NULL, '/system/dept', '127.0.0.1', '内网IP', '{"ancestors":"0,100","children":[],"createBy":"admin","deptId":1897326789697044482,"deptName":"123","orderNum":0,"params":{},"parentId":100,"status":"0"}', '{"msg":"操作成功","code":200}', 0, NULL, '2025-03-06 00:42:13.401372', 319);
INSERT INTO "public"."sys_oper_log" VALUES (1897326816364429313, '机构管理', 2, 'com.sq.admin.system.controller.SysDeptController.edit()', 'PUT', 1, 'admin', NULL, '/system/dept', '127.0.0.1', '内网IP', '{"ancestors":"0,100","children":[],"deptId":1897326789697044482,"deptName":"123","orderNum":1,"params":{},"parentId":100,"parentName":"自治区口岸办","status":"0","updateBy":"admin"}', '{"msg":"操作成功","code":200}', 0, NULL, '2025-03-06 00:42:19.594523', 546);
INSERT INTO "public"."sys_oper_log" VALUES (1897326880566640641, '岗位管理', 3, 'com.sq.admin.system.controller.SysPostController.remove()', 'DELETE', 1, 'admin', NULL, '/system/post/1897326854599704577', '127.0.0.1', '内网IP', '{}', '{"msg":"操作成功","code":200}', 0, NULL, '2025-03-06 00:42:34.908242', 245);
INSERT INTO "public"."sys_oper_log" VALUES (1897326934966763522, '字典类型', 1, 'com.sq.admin.system.controller.SysDictTypeController.add()', 'POST', 1, 'admin', NULL, '/system/dict/type', '127.0.0.1', '内网IP', '{"createBy":"admin","dictName":"aaa","dictType":"aaa","params":{},"status":"0"}', NULL, 1, '
### Error updating database.  Cause: org.postgresql.util.PSQLException: ERROR: column "del_flag" of relation "sys_dict_type" does not exist
  Position: 107
### The error may exist in file [D:\workspace\open-plat\sq-fast\sq-system\target\classes\com\sq\system\mapper\xml\SysDictTypeMapper.xml]
### The error may involve com.sq.system.mapper.SysDictTypeMapper.insertDictType-Inline
### The error occurred while setting parameters
### SQL: insert into sys_dict_type(           dict_name,       dict_type,       status,             create_by,     del_flag,create_time    )values(           ?,       ?,       ?,             ?,      0,now()    )
### Cause: org.postgresql.util.PSQLException: ERROR: column "del_flag" of relation "sys_dict_type" does not exist
  Position: 107
; bad SQL grammar []; nested exception is org.postgresql.util.PSQLException: ERROR: column "del_flag" of relation "sys_dict_type" does not exist
  Position: 107', '2025-03-06 00:42:47.880517', 285);
INSERT INTO "public"."sys_oper_log" VALUES (1897327204224303105, '参数管理', 1, 'com.sq.admin.system.controller.SysConfigController.add()', 'POST', 1, 'admin', NULL, '/system/config', '127.0.0.1', '内网IP', '{"configKey":"123","configName":"123","configType":"Y","configValue":"123","createBy":"admin","params":{}}', NULL, 1, '
### Error updating database.  Cause: org.postgresql.util.PSQLException: ERROR: null value in column "config_id" violates not-null constraint
  Detail: Failing row contains (null, 123, 123, 123, Y, admin, 2025-03-06 00:43:51.923883, null, null, null).
### The error may exist in file [D:\workspace\open-plat\sq-fast\sq-system\target\classes\com\sq\system\mapper\xml\SysConfigMapper.xml]
### The error may involve com.sq.system.mapper.SysConfigMapper.insertConfig-Inline
### The error occurred while setting parameters
### SQL: insert into sys_config (                   config_name,      config_key,      config_value,      config_type,      create_by,           create_time         )values(                   ?,      ?,      ?,      ?,      ?,           now()   )
### Cause: org.postgresql.util.PSQLException: ERROR: null value in column "config_id" violates not-null constraint
  Detail: Failing row contains (null, 123, 123, 123, Y, admin, 2025-03-06 00:43:51.923883, null, null, null).
; ERROR: null value in column "config_id" violates not-null constraint
  Detail: Failing row contains (null, 123, 123, 123, Y, admin, 2025-03-06 00:43:51.923883, null, null, null).; nested exception is org.postgresql.util.PSQLException: ERROR: null value in column "config_id" violates not-null constraint
  Detail: Failing row contains (null, 123, 123, 123, Y, admin, 2025-03-06 00:43:51.923883, null, null, null).', '2025-03-06 00:43:52.079216', 175);
INSERT INTO "public"."sys_oper_log" VALUES (1897327594726604801, '字典类型', 1, 'com.sq.admin.system.controller.SysDictTypeController.add()', 'POST', 1, 'admin', NULL, '/system/dict/type', '127.0.0.1', '内网IP', '{"createBy":"admin","dictName":"aaa","dictType":"aaa","params":{},"status":"0"}', NULL, 1, '
### Error updating database.  Cause: org.postgresql.util.PSQLException: ERROR: null value in column "dict_id" violates not-null constraint
  Detail: Failing row contains (null, aaa, aaa, 0, admin, 2025-03-06 00:45:24.921056, null, null, null).
### The error may exist in file [D:\workspace\open-plat\sq-fast\sq-system\target\classes\com\sq\system\mapper\xml\SysDictTypeMapper.xml]
### The error may involve com.sq.system.mapper.SysDictTypeMapper.insertDictType-Inline
### The error occurred while setting parameters
### SQL: insert into sys_dict_type(           dict_name,       dict_type,       status,             create_by,     create_time    )values(           ?,       ?,       ?,             ?,      now()    )
### Cause: org.postgresql.util.PSQLException: ERROR: null value in column "dict_id" violates not-null constraint
  Detail: Failing row contains (null, aaa, aaa, 0, admin, 2025-03-06 00:45:24.921056, null, null, null).
; ERROR: null value in column "dict_id" violates not-null constraint
  Detail: Failing row contains (null, aaa, aaa, 0, admin, 2025-03-06 00:45:24.921056, null, null, null).; nested exception is org.postgresql.util.PSQLException: ERROR: null value in column "dict_id" violates not-null constraint
  Detail: Failing row contains (null, aaa, aaa, 0, admin, 2025-03-06 00:45:24.921056, null, null, null).', '2025-03-06 00:45:25.17857', 253);
INSERT INTO "public"."sys_oper_log" VALUES (1897327795063341057, '参数管理', 1, 'com.sq.admin.system.controller.SysConfigController.add()', 'POST', 1, 'admin', NULL, '/system/config', '127.0.0.1', '内网IP', '{"configId":1897327794476138497,"configKey":"123","configName":"123","configType":"Y","configValue":"123","createBy":"admin","params":{}}', NULL, 1, '
### Error updating database.  Cause: org.postgresql.util.PSQLException: ERROR: integer out of range
  Where: referenced column: config_id
### The error may exist in file [D:\workspace\open-plat\sq-fast\sq-system\target\classes\com\sq\system\mapper\xml\SysConfigMapper.xml]
### The error may involve com.sq.system.mapper.SysConfigMapper.insertConfig-Inline
### The error occurred while setting parameters
### SQL: insert into sys_config (              config_id,      config_name,      config_key,      config_value,      config_type,      create_by,           create_time         )values(              ?,      ?,      ?,      ?,      ?,      ?,           now()   )
### Cause: org.postgresql.util.PSQLException: ERROR: integer out of range
  Where: referenced column: config_id
; ERROR: integer out of range
  Where: referenced column: config_id; nested exception is org.postgresql.util.PSQLException: ERROR: integer out of range
  Where: referenced column: config_id', '2025-03-06 00:46:12.946128', 211);
INSERT INTO "public"."sys_oper_log" VALUES (1897327946188263425, '参数管理', 1, 'com.sq.admin.system.controller.SysConfigController.add()', 'POST', 1, 'admin', NULL, '/system/config', '127.0.0.1', '内网IP', '{"configId":1897327945131298817,"configKey":"123","configName":"123","configType":"Y","configValue":"123","createBy":"admin","params":{}}', NULL, 1, '
### Error updating database.  Cause: org.postgresql.util.PSQLException: ERROR: integer out of range
  Where: referenced column: config_id
### The error may exist in file [D:\workspace\open-plat\sq-fast\sq-system\target\classes\com\sq\system\mapper\xml\SysConfigMapper.xml]
### The error may involve com.sq.system.mapper.SysConfigMapper.insertConfig-Inline
### The error occurred while setting parameters
### SQL: insert into sys_config (              config_id,      config_name,      config_key,      config_value,      config_type,      create_by,           create_time         )values(              ?,      ?,      ?,      ?,      ?,      ?,           now()   )
### Cause: org.postgresql.util.PSQLException: ERROR: integer out of range
  Where: referenced column: config_id
; ERROR: integer out of range
  Where: referenced column: config_id; nested exception is org.postgresql.util.PSQLException: ERROR: integer out of range
  Where: referenced column: config_id', '2025-03-06 00:46:48.973775', 313);
INSERT INTO "public"."sys_oper_log" VALUES (1897328249386110977, '参数管理', 1, 'com.sq.admin.system.controller.SysConfigController.add()', 'POST', 1, 'admin', NULL, '/system/config', '127.0.0.1', '内网IP', '{"configId":1897328248664690689,"configKey":"123","configName":"123","configType":"Y","configValue":"123","createBy":"admin","params":{}}', '{"msg":"操作成功","code":200}', 0, NULL, '2025-03-06 00:48:01.258032', 317);
INSERT INTO "public"."sys_oper_log" VALUES (1897328308479660033, '字典类型', 1, 'com.sq.admin.system.controller.SysDictTypeController.add()', 'POST', 1, 'admin', NULL, '/system/dict/type', '127.0.0.1', '内网IP', '{"createBy":"admin","dictId":1897328307506581506,"dictName":"aaa","dictType":"aaa","params":{},"status":"0"}', '{"msg":"操作成功","code":200}', 0, NULL, '2025-03-06 00:48:15.362308', 301);
INSERT INTO "public"."sys_oper_log" VALUES (1897328349122465793, '字典数据', 1, 'com.sq.admin.system.controller.SysDictDataController.add()', 'POST', 1, 'admin', NULL, '/system/dict/data', '127.0.0.1', '内网IP', '{"createBy":"admin","default":false,"dictLabel":"11","dictSort":0,"dictType":"aaa","dictValue":"11","listClass":"default","params":{},"status":"0"}', NULL, 1, '
### Error updating database.  Cause: org.postgresql.util.PSQLException: ERROR: null value in column "dict_code" violates not-null constraint
  Detail: Failing row contains (null, 0, 11, 11, aaa, null, default, null, 0, admin, 2025-03-06 00:48:24.899504, null, null, null).
### The error may exist in file [D:\workspace\open-plat\sq-fast\sq-system\target\classes\com\sq\system\mapper\xml\SysDictDataMapper.xml]
### The error may involve com.sq.system.mapper.SysDictDataMapper.insertDictData-Inline
### The error occurred while setting parameters
### SQL: insert into sys_dict_data(                       dict_sort,                                 dict_label,                                 dict_value,                                 dict_type,                                           list_class,                                           status,                                           create_by,                   create_time)values(                       ?,                                 ?,                                 ?,                                 ?,                                           ?,                                           ?,                                           ?,                   now())
### Cause: org.postgresql.util.PSQLException: ERROR: null value in column "dict_code" violates not-null constraint
  Detail: Failing row contains (null, 0, 11, 11, aaa, null, default, null, 0, admin, 2025-03-06 00:48:24.899504, null, null, null).
; ERROR: null value in column "dict_code" violates not-null constraint
  Detail: Failing row contains (null, 0, 11, 11, aaa, null, default, null, 0, admin, 2025-03-06 00:48:24.899504, null, null, null).; nested exception is org.postgresql.util.PSQLException: ERROR: null value in column "dict_code" violates not-null constraint
  Detail: Failing row contains (null, 0, 11, 11, aaa, null, default, null, 0, admin, 2025-03-06 00:48:24.899504, null, null, null).', '2025-03-06 00:48:25.036907', 161);
INSERT INTO "public"."sys_oper_log" VALUES (1897329017505710081, '字典数据', 1, 'com.sq.admin.system.controller.SysDictDataController.add()', 'POST', 1, 'admin', NULL, '/system/dict/data', '127.0.0.1', '内网IP', '{"createBy":"admin","default":false,"dictLabel":"11","dictSort":0,"dictType":"aaa","dictValue":"11","listClass":"default","params":{},"status":"0"}', NULL, 1, '
### Error updating database.  Cause: org.postgresql.util.PSQLException: ERROR: null value in column "dict_code" violates not-null constraint
  Detail: Failing row contains (null, 0, 11, 11, aaa, null, default, null, 0, admin, 2025-03-06 00:51:04.153026, null, null, null).
### The error may exist in file [D:\workspace\open-plat\sq-fast\sq-system\target\classes\com\sq\system\mapper\xml\SysDictDataMapper.xml]
### The error may involve com.sq.system.mapper.SysDictDataMapper.insertDictData-Inline
### The error occurred while setting parameters
### SQL: insert into sys_dict_data(                                 dict_sort,                                 dict_label,                                 dict_value,                                 dict_type,                                           list_class,                                           status,                                           create_by,                   create_time)values(                                 ?,                                 ?,                                 ?,                                 ?,                                           ?,                                           ?,                                           ?,                   now())
### Cause: org.postgresql.util.PSQLException: ERROR: null value in column "dict_code" violates not-null constraint
  Detail: Failing row contains (null, 0, 11, 11, aaa, null, default, null, 0, admin, 2025-03-06 00:51:04.153026, null, null, null).
; ERROR: null value in column "dict_code" violates not-null constraint
  Detail: Failing row contains (null, 0, 11, 11, aaa, null, default, null, 0, admin, 2025-03-06 00:51:04.153026, null, null, null).; nested exception is org.postgresql.util.PSQLException: ERROR: null value in column "dict_code" violates not-null constraint
  Detail: Failing row contains (null, 0, 11, 11, aaa, null, default, null, 0, admin, 2025-03-06 00:51:04.153026, null, null, null).', '2025-03-06 00:51:04.399022', 273);
INSERT INTO "public"."sys_oper_log" VALUES (1897329258397204482, '字典数据', 1, 'com.sq.admin.system.controller.SysDictDataController.add()', 'POST', 1, 'admin', NULL, '/system/dict/data', '127.0.0.1', '内网IP', '{"createBy":"admin","default":false,"dictCode":1897329257025667074,"dictLabel":"11","dictSort":0,"dictType":"aaa","dictValue":"11","listClass":"default","params":{},"status":"0"}', '{"msg":"操作成功","code":200}', 0, NULL, '2025-03-06 00:52:01.852272', 402);
INSERT INTO "public"."sys_oper_log" VALUES (1897329314512797698, '字典类型', 3, 'com.sq.admin.system.controller.SysDictTypeController.remove()', 'DELETE', 1, 'admin', NULL, '/system/dict/type/1897328307506581506', '127.0.0.1', '内网IP', '{}', NULL, 1, 'aaa已分配,不能删除', '2025-03-06 00:52:15.228582', 168);
INSERT INTO "public"."sys_oper_log" VALUES (1897329341691887617, '字典类型', 3, 'com.sq.admin.system.controller.SysDictDataController.remove()', 'DELETE', 1, 'admin', NULL, '/system/dict/data/1897329257025667074', '127.0.0.1', '内网IP', '{}', '{"msg":"操作成功","code":200}', 0, NULL, '2025-03-06 00:52:21.703172', 334);
INSERT INTO "public"."sys_oper_log" VALUES (1897329772602097665, '字典类型', 3, 'com.sq.admin.system.controller.SysDictTypeController.remove()', 'DELETE', 1, 'admin', NULL, '/system/dict/type/1897328307506581506', '127.0.0.1', '内网IP', '{}', '{"msg":"操作成功","code":200}', 0, NULL, '2025-03-06 00:54:04.429028', 331);
INSERT INTO "public"."sys_oper_log" VALUES (1897329811529433090, '参数管理', 3, 'com.sq.admin.system.controller.SysConfigController.remove()', 'DELETE', 1, 'admin', NULL, '/system/config/1897328248664690689', '127.0.0.1', '内网IP', '{}', NULL, 1, '内置参数【123】不能删除 ', '2025-03-06 00:54:13.712968', 75);
INSERT INTO "public"."sys_oper_log" VALUES (1897329843162873857, '参数管理', 3, 'com.sq.admin.system.controller.SysConfigController.remove()', 'DELETE', 1, 'admin', NULL, '/system/config/1897328248664690689', '127.0.0.1', '内网IP', '{}', NULL, 1, '内置参数【123】不能删除 ', '2025-03-06 00:54:21.252274', 75);
INSERT INTO "public"."sys_oper_log" VALUES (1897329873932288002, '参数管理', 2, 'com.sq.admin.system.controller.SysConfigController.edit()', 'PUT', 1, 'admin', NULL, '/system/config', '127.0.0.1', '内网IP', '{"configId":1897328248664690689,"configKey":"123","configName":"123","configType":"N","configValue":"123","createBy":"admin","createTime":"2025-03-06 00:48:01","params":{},"updateBy":"admin"}', '{"msg":"操作成功","code":200}', 0, NULL, '2025-03-06 00:54:28.590506', 298);
INSERT INTO "public"."sys_oper_log" VALUES (1897329886225793026, '参数管理', 3, 'com.sq.admin.system.controller.SysConfigController.remove()', 'DELETE', 1, 'admin', NULL, '/system/config/1897328248664690689', '127.0.0.1', '内网IP', '{}', '{"msg":"操作成功","code":200}', 0, NULL, '2025-03-06 00:54:31.520797', 206);
INSERT INTO "public"."sys_oper_log" VALUES (1897329929368403970, '角色管理', 3, 'com.sq.admin.system.controller.SysRoleController.remove()', 'DELETE', 1, 'admin', NULL, '/system/role/1897326526538022914', '127.0.0.1', '内网IP', '{}', '{"msg":"操作成功","code":200}', 0, NULL, '2025-03-06 00:54:41.808896', 552);
INSERT INTO "public"."sys_oper_log" VALUES (1897329991557349377, '机构管理', 3, 'com.sq.admin.system.controller.SysDeptController.remove()', 'DELETE', 1, 'admin', NULL, '/system/dept/1897326789697044482', '127.0.0.1', '内网IP', '{}', '{"msg":"操作成功","code":200}', 0, NULL, '2025-03-06 00:54:56.637808', 266);

-- ----------------------------
-- Table structure for sys_post
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_post";
CREATE TABLE "public"."sys_post" (
  "id" int8 NOT NULL,
  "post_code" varchar(64) COLLATE "pg_catalog"."default" NOT NULL,
  "post_name" varchar(50) COLLATE "pg_catalog"."default" NOT NULL,
  "post_sort" int4 NOT NULL,
  "status" char(1) COLLATE "pg_catalog"."default" NOT NULL,
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6),
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6),
  "remark" varchar(500) COLLATE "pg_catalog"."default"
)
;
COMMENT ON COLUMN "public"."sys_post"."id" IS '岗位ID';
COMMENT ON COLUMN "public"."sys_post"."post_code" IS '岗位编码';
COMMENT ON COLUMN "public"."sys_post"."post_name" IS '岗位名称';
COMMENT ON COLUMN "public"."sys_post"."post_sort" IS '显示顺序';
COMMENT ON COLUMN "public"."sys_post"."status" IS '状态（0正常 1停用）';
COMMENT ON COLUMN "public"."sys_post"."create_by" IS '创建者';
COMMENT ON COLUMN "public"."sys_post"."create_time" IS '创建时间';
COMMENT ON COLUMN "public"."sys_post"."update_by" IS '更新者';
COMMENT ON COLUMN "public"."sys_post"."update_time" IS '更新时间';
COMMENT ON COLUMN "public"."sys_post"."remark" IS '备注';
COMMENT ON TABLE "public"."sys_post" IS '岗位信息表';

-- ----------------------------
-- Records of sys_post
-- ----------------------------

-- ----------------------------
-- Table structure for sys_role
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_role";
CREATE TABLE "public"."sys_role" (
  "id" int8 NOT NULL,
  "dept_id" int8,
  "role_name" varchar(30) COLLATE "pg_catalog"."default" NOT NULL,
  "role_key" varchar(100) COLLATE "pg_catalog"."default" NOT NULL,
  "role_sort" int4 NOT NULL,
  "data_scope" char(1) COLLATE "pg_catalog"."default",
  "menu_check_strictly" int2,
  "dept_check_strictly" int2,
  "status" char(1) COLLATE "pg_catalog"."default" NOT NULL,
  "del_flag" char(1) COLLATE "pg_catalog"."default",
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6),
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6),
  "remark" varchar(500) COLLATE "pg_catalog"."default"
)
;
COMMENT ON COLUMN "public"."sys_role"."id" IS '角色ID';
COMMENT ON COLUMN "public"."sys_role"."dept_id" IS '机构ID';
COMMENT ON COLUMN "public"."sys_role"."role_name" IS '角色名称';
COMMENT ON COLUMN "public"."sys_role"."role_key" IS '角色权限字符串';
COMMENT ON COLUMN "public"."sys_role"."role_sort" IS '显示顺序';
COMMENT ON COLUMN "public"."sys_role"."data_scope" IS '数据范围（1：全部数据权限 2：自定数据权限 3：本机构数据权限 4：本机构及以下数据权限）';
COMMENT ON COLUMN "public"."sys_role"."menu_check_strictly" IS '菜单树选择项是否关联显示';
COMMENT ON COLUMN "public"."sys_role"."dept_check_strictly" IS '机构树选择项是否关联显示';
COMMENT ON COLUMN "public"."sys_role"."status" IS '角色状态（0正常 1停用）';
COMMENT ON COLUMN "public"."sys_role"."del_flag" IS '删除标志（0代表存在 1代表删除）';
COMMENT ON COLUMN "public"."sys_role"."create_by" IS '创建者';
COMMENT ON COLUMN "public"."sys_role"."create_time" IS '创建时间';
COMMENT ON COLUMN "public"."sys_role"."update_by" IS '更新者';
COMMENT ON COLUMN "public"."sys_role"."update_time" IS '更新时间';
COMMENT ON COLUMN "public"."sys_role"."remark" IS '备注';
COMMENT ON TABLE "public"."sys_role" IS '角色信息表';

-- ----------------------------
-- Records of sys_role
-- ----------------------------
INSERT INTO "public"."sys_role" VALUES (1, 100, '超级管理员', 'admin', 1, '1', 1, 1, '0', '0', 'admin', '2024-06-21 10:57:32', '', NULL, '超级管理员');
INSERT INTO "public"."sys_role" VALUES (1897324915958841346, NULL, '123', '123', 0, NULL, 1, 1, '0', NULL, 'admin', '2025-03-06 00:34:46.534664', NULL, NULL, NULL);
INSERT INTO "public"."sys_role" VALUES (1897326526538022914, NULL, '123', '123', 0, NULL, 1, 1, '0', '2', 'admin', '2025-03-06 00:41:10.523187', 'admin', '2025-03-06 00:41:20.360457', NULL);

-- ----------------------------
-- Table structure for sys_role_dept
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_role_dept";
CREATE TABLE "public"."sys_role_dept" (
  "role_id" int8 NOT NULL,
  "dept_id" int8 NOT NULL
)
;
COMMENT ON COLUMN "public"."sys_role_dept"."role_id" IS '角色ID';
COMMENT ON COLUMN "public"."sys_role_dept"."dept_id" IS '机构ID';
COMMENT ON TABLE "public"."sys_role_dept" IS '角色和机构关联表';

-- ----------------------------
-- Records of sys_role_dept
-- ----------------------------

-- ----------------------------
-- Table structure for sys_role_menu
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_role_menu";
CREATE TABLE "public"."sys_role_menu" (
  "role_id" int8 NOT NULL,
  "menu_id" int8 NOT NULL
)
;
COMMENT ON COLUMN "public"."sys_role_menu"."role_id" IS '角色ID';
COMMENT ON COLUMN "public"."sys_role_menu"."menu_id" IS '菜单ID';
COMMENT ON TABLE "public"."sys_role_menu" IS '角色和菜单关联表';

-- ----------------------------
-- Records of sys_role_menu
-- ----------------------------
INSERT INTO "public"."sys_role_menu" VALUES (1897324915958841346, 3);
INSERT INTO "public"."sys_role_menu" VALUES (1897324915958841346, 115);
INSERT INTO "public"."sys_role_menu" VALUES (1897324915958841346, 116);
INSERT INTO "public"."sys_role_menu" VALUES (1897324915958841346, 1055);
INSERT INTO "public"."sys_role_menu" VALUES (1897324915958841346, 1056);
INSERT INTO "public"."sys_role_menu" VALUES (1897324915958841346, 1057);
INSERT INTO "public"."sys_role_menu" VALUES (1897324915958841346, 1058);
INSERT INTO "public"."sys_role_menu" VALUES (1897324915958841346, 1059);
INSERT INTO "public"."sys_role_menu" VALUES (1897324915958841346, 1060);
INSERT INTO "public"."sys_role_menu" VALUES (1897324915958841346, 117);

-- ----------------------------
-- Table structure for sys_user
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_user";
CREATE TABLE "public"."sys_user" (
  "id" int8 NOT NULL,
  "dept_id" int8,
  "user_name" varchar(30) COLLATE "pg_catalog"."default" NOT NULL,
  "nick_name" varchar(30) COLLATE "pg_catalog"."default" NOT NULL,
  "user_type" varchar(2) COLLATE "pg_catalog"."default",
  "email" varchar(50) COLLATE "pg_catalog"."default",
  "phonenumber" varchar(11) COLLATE "pg_catalog"."default",
  "sex" char(1) COLLATE "pg_catalog"."default",
  "avatar" varchar(100) COLLATE "pg_catalog"."default",
  "password" varchar(100) COLLATE "pg_catalog"."default",
  "status" char(1) COLLATE "pg_catalog"."default",
  "del_flag" char(1) COLLATE "pg_catalog"."default",
  "login_ip" varchar(128) COLLATE "pg_catalog"."default",
  "login_date" timestamp(6),
  "pwd_update_time" timestamp(6),
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6),
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6),
  "remark" varchar(500) COLLATE "pg_catalog"."default"
)
;
COMMENT ON COLUMN "public"."sys_user"."id" IS '用户ID';
COMMENT ON COLUMN "public"."sys_user"."dept_id" IS '机构ID';
COMMENT ON COLUMN "public"."sys_user"."user_name" IS '用户账号';
COMMENT ON COLUMN "public"."sys_user"."nick_name" IS '用户昵称';
COMMENT ON COLUMN "public"."sys_user"."user_type" IS '用户类型（00系统用户）';
COMMENT ON COLUMN "public"."sys_user"."email" IS '用户邮箱';
COMMENT ON COLUMN "public"."sys_user"."phonenumber" IS '手机号码';
COMMENT ON COLUMN "public"."sys_user"."sex" IS '用户性别（0男 1女 2未知）';
COMMENT ON COLUMN "public"."sys_user"."avatar" IS '头像地址';
COMMENT ON COLUMN "public"."sys_user"."password" IS '密码';
COMMENT ON COLUMN "public"."sys_user"."status" IS '帐号状态（0正常 1停用）';
COMMENT ON COLUMN "public"."sys_user"."del_flag" IS '删除标志（0代表存在 1代表删除）';
COMMENT ON COLUMN "public"."sys_user"."login_ip" IS '最后登录IP';
COMMENT ON COLUMN "public"."sys_user"."login_date" IS '最后登录时间';
COMMENT ON COLUMN "public"."sys_user"."pwd_update_time" IS '密码修改时间';
COMMENT ON COLUMN "public"."sys_user"."create_by" IS '创建者';
COMMENT ON COLUMN "public"."sys_user"."create_time" IS '创建时间';
COMMENT ON COLUMN "public"."sys_user"."update_by" IS '更新者';
COMMENT ON COLUMN "public"."sys_user"."update_time" IS '更新时间';
COMMENT ON COLUMN "public"."sys_user"."remark" IS '备注';
COMMENT ON TABLE "public"."sys_user" IS '用户信息表';

-- ----------------------------
-- Records of sys_user
-- ----------------------------
INSERT INTO "public"."sys_user" VALUES (1897318079738159105, 100, '123', '123', NULL, NULL, NULL, NULL, NULL, '$2a$10$3ofmyHXOGMAG/Qx/DZy0i.3rct0/hxY.99WvsbkTgSyySBZqGJhG2', '0', NULL, NULL, NULL, 'admin', '2025-03-06 00:07:36.637296', NULL, NULL, NULL);
INSERT INTO "public"."sys_user" VALUES (1897234904370413569, NULL, '123', '123', NULL, NULL, NULL, NULL, NULL, '$2a$10$v6XVrwvkNHTYEJwzIT5Ms.7S6ZqJbJYiwhIXGTA4Nj27T8MbXQ/sC', '0', '2', NULL, NULL, 'admin', '2025-03-05 18:37:04.993038', NULL, NULL, NULL);
INSERT INTO "public"."sys_user" VALUES (1, 100, 'admin', '超级管理员', '00', NULL, '15888888888', '1', '', '{sm3}DOu27VYMzaNrcShzQgbBwg==$2e107930584528e2164da4201a55639f64bbe495b13ba99e554a96daa70488df', '0', '0', '127.0.0.1', '2025-03-06 00:07:21.338', 'admin', '2024-06-21 10:57:31', '', '2025-03-06 00:07:22.634099', '管理员');
INSERT INTO "public"."sys_user" VALUES (1897321940574134273, 203, 'haha', '哈哈哈', NULL, NULL, NULL, NULL, NULL, '$2a$10$ObOafMHby9AMYwi5dEmUqeYnJxQ9DHp86LnQXvX.jEuucGoUSvk0i', '0', '0', NULL, NULL, 'admin', '2025-03-06 00:22:57.137412', NULL, NULL, NULL);

-- ----------------------------
-- Table structure for sys_user_post
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_user_post";
CREATE TABLE "public"."sys_user_post" (
  "user_id" int8 NOT NULL,
  "post_id" int8 NOT NULL
)
;
COMMENT ON COLUMN "public"."sys_user_post"."user_id" IS '用户ID';
COMMENT ON COLUMN "public"."sys_user_post"."post_id" IS '岗位ID';
COMMENT ON TABLE "public"."sys_user_post" IS '用户与岗位关联表';

-- ----------------------------
-- Records of sys_user_post
-- ----------------------------

-- ----------------------------
-- Table structure for sys_user_role
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_user_role";
CREATE TABLE "public"."sys_user_role" (
  "user_id" int8 NOT NULL,
  "role_id" int8 NOT NULL
)
;
COMMENT ON COLUMN "public"."sys_user_role"."user_id" IS '用户ID';
COMMENT ON COLUMN "public"."sys_user_role"."role_id" IS '角色ID';
COMMENT ON TABLE "public"."sys_user_role" IS '用户和角色关联表';

-- ----------------------------
-- Records of sys_user_role
-- ----------------------------

-- ----------------------------
-- Primary Key structure for table gen_table
-- ----------------------------
ALTER TABLE "public"."gen_table" ADD CONSTRAINT "gen_table_pkey" PRIMARY KEY ("table_id");

-- ----------------------------
-- Primary Key structure for table gen_table_column
-- ----------------------------
ALTER TABLE "public"."gen_table_column" ADD CONSTRAINT "gen_table_column_pkey" PRIMARY KEY ("column_id");

-- ----------------------------
-- Primary Key structure for table qrtz_blob_triggers
-- ----------------------------
ALTER TABLE "public"."qrtz_blob_triggers" ADD CONSTRAINT "qrtz_blob_triggers_pkey" PRIMARY KEY ("sched_name", "trigger_name", "trigger_group");

-- ----------------------------
-- Primary Key structure for table qrtz_calendars
-- ----------------------------
ALTER TABLE "public"."qrtz_calendars" ADD CONSTRAINT "qrtz_calendars_pkey" PRIMARY KEY ("sched_name", "calendar_name");

-- ----------------------------
-- Primary Key structure for table qrtz_cron_triggers
-- ----------------------------
ALTER TABLE "public"."qrtz_cron_triggers" ADD CONSTRAINT "qrtz_cron_triggers_pkey" PRIMARY KEY ("sched_name", "trigger_name", "trigger_group");

-- ----------------------------
-- Primary Key structure for table qrtz_fired_triggers
-- ----------------------------
ALTER TABLE "public"."qrtz_fired_triggers" ADD CONSTRAINT "qrtz_fired_triggers_pkey" PRIMARY KEY ("sched_name", "entry_id");

-- ----------------------------
-- Primary Key structure for table qrtz_job_details
-- ----------------------------
ALTER TABLE "public"."qrtz_job_details" ADD CONSTRAINT "qrtz_job_details_pkey" PRIMARY KEY ("sched_name", "job_name", "job_group");

-- ----------------------------
-- Primary Key structure for table qrtz_locks
-- ----------------------------
ALTER TABLE "public"."qrtz_locks" ADD CONSTRAINT "qrtz_locks_pkey" PRIMARY KEY ("sched_name", "lock_name");

-- ----------------------------
-- Primary Key structure for table qrtz_paused_trigger_grps
-- ----------------------------
ALTER TABLE "public"."qrtz_paused_trigger_grps" ADD CONSTRAINT "qrtz_paused_trigger_grps_pkey" PRIMARY KEY ("sched_name", "trigger_group");

-- ----------------------------
-- Primary Key structure for table qrtz_scheduler_state
-- ----------------------------
ALTER TABLE "public"."qrtz_scheduler_state" ADD CONSTRAINT "qrtz_scheduler_state_pkey" PRIMARY KEY ("sched_name", "instance_name");

-- ----------------------------
-- Primary Key structure for table qrtz_simple_triggers
-- ----------------------------
ALTER TABLE "public"."qrtz_simple_triggers" ADD CONSTRAINT "qrtz_simple_triggers_pkey" PRIMARY KEY ("sched_name", "trigger_name", "trigger_group");

-- ----------------------------
-- Primary Key structure for table qrtz_simprop_triggers
-- ----------------------------
ALTER TABLE "public"."qrtz_simprop_triggers" ADD CONSTRAINT "qrtz_simprop_triggers_pkey" PRIMARY KEY ("sched_name", "trigger_name", "trigger_group");

-- ----------------------------
-- Indexes structure for table qrtz_triggers
-- ----------------------------
CREATE INDEX "sched_name" ON "public"."qrtz_triggers" USING btree (
  "sched_name" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "job_name" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "job_group" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);

-- ----------------------------
-- Primary Key structure for table qrtz_triggers
-- ----------------------------
ALTER TABLE "public"."qrtz_triggers" ADD CONSTRAINT "qrtz_triggers_pkey" PRIMARY KEY ("sched_name", "trigger_name", "trigger_group");

-- ----------------------------
-- Primary Key structure for table sys_config
-- ----------------------------
ALTER TABLE "public"."sys_config" ADD CONSTRAINT "sys_config_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Primary Key structure for table sys_dept
-- ----------------------------
ALTER TABLE "public"."sys_dept" ADD CONSTRAINT "sys_dept_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Primary Key structure for table sys_dict_data
-- ----------------------------
ALTER TABLE "public"."sys_dict_data" ADD CONSTRAINT "sys_dict_data_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table sys_dict_type
-- ----------------------------
CREATE INDEX "dict_type" ON "public"."sys_dict_type" USING btree (
  "dict_type" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);

-- ----------------------------
-- Primary Key structure for table sys_dict_type
-- ----------------------------
ALTER TABLE "public"."sys_dict_type" ADD CONSTRAINT "sys_dict_type_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Primary Key structure for table sys_job
-- ----------------------------
ALTER TABLE "public"."sys_job" ADD CONSTRAINT "sys_job_pkey" PRIMARY KEY ("job_id", "job_name", "job_group");

-- ----------------------------
-- Primary Key structure for table sys_job_log
-- ----------------------------
ALTER TABLE "public"."sys_job_log" ADD CONSTRAINT "sys_job_log_pkey" PRIMARY KEY ("job_log_id");

-- ----------------------------
-- Indexes structure for table sys_logininfor
-- ----------------------------
CREATE INDEX "idx_sys_logininfor_lt" ON "public"."sys_logininfor" USING btree (
  "login_time" "pg_catalog"."timestamp_ops" ASC NULLS LAST
);
CREATE INDEX "idx_sys_logininfor_s" ON "public"."sys_logininfor" USING btree (
  "status" COLLATE "pg_catalog"."default" "pg_catalog"."bpchar_ops" ASC NULLS LAST
);

-- ----------------------------
-- Primary Key structure for table sys_logininfor
-- ----------------------------
ALTER TABLE "public"."sys_logininfor" ADD CONSTRAINT "sys_logininfor_pkey" PRIMARY KEY ("info_id");

-- ----------------------------
-- Primary Key structure for table sys_menu
-- ----------------------------
ALTER TABLE "public"."sys_menu" ADD CONSTRAINT "sys_menu_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Primary Key structure for table sys_notice
-- ----------------------------
ALTER TABLE "public"."sys_notice" ADD CONSTRAINT "sys_notice_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table sys_oper_log
-- ----------------------------
CREATE INDEX "idx_sys_oper_log_bt" ON "public"."sys_oper_log" USING btree (
  "business_type" "pg_catalog"."int4_ops" ASC NULLS LAST
);
CREATE INDEX "idx_sys_oper_log_ot" ON "public"."sys_oper_log" USING btree (
  "oper_time" "pg_catalog"."timestamp_ops" ASC NULLS LAST
);
CREATE INDEX "idx_sys_oper_log_s" ON "public"."sys_oper_log" USING btree (
  "status" "pg_catalog"."int4_ops" ASC NULLS LAST
);

-- ----------------------------
-- Primary Key structure for table sys_oper_log
-- ----------------------------
ALTER TABLE "public"."sys_oper_log" ADD CONSTRAINT "sys_oper_log_pkey" PRIMARY KEY ("oper_id");

-- ----------------------------
-- Primary Key structure for table sys_post
-- ----------------------------
ALTER TABLE "public"."sys_post" ADD CONSTRAINT "sys_post_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Primary Key structure for table sys_role
-- ----------------------------
ALTER TABLE "public"."sys_role" ADD CONSTRAINT "sys_role_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Primary Key structure for table sys_role_dept
-- ----------------------------
ALTER TABLE "public"."sys_role_dept" ADD CONSTRAINT "sys_role_dept_pkey" PRIMARY KEY ("role_id", "dept_id");

-- ----------------------------
-- Primary Key structure for table sys_role_menu
-- ----------------------------
ALTER TABLE "public"."sys_role_menu" ADD CONSTRAINT "sys_role_menu_pkey" PRIMARY KEY ("role_id", "menu_id");

-- ----------------------------
-- Primary Key structure for table sys_user
-- ----------------------------
ALTER TABLE "public"."sys_user" ADD CONSTRAINT "sys_user_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Primary Key structure for table sys_user_post
-- ----------------------------
ALTER TABLE "public"."sys_user_post" ADD CONSTRAINT "sys_user_post_pkey" PRIMARY KEY ("user_id", "post_id");

-- ----------------------------
-- Primary Key structure for table sys_user_role
-- ----------------------------
ALTER TABLE "public"."sys_user_role" ADD CONSTRAINT "sys_user_role_pkey" PRIMARY KEY ("user_id", "role_id");

-- ----------------------------
-- Foreign Keys structure for table qrtz_blob_triggers
-- ----------------------------
ALTER TABLE "public"."qrtz_blob_triggers" ADD CONSTRAINT "qrtz_blob_triggers_ibfk_1" FOREIGN KEY ("sched_name", "trigger_name", "trigger_group") REFERENCES "public"."qrtz_triggers" ("sched_name", "trigger_name", "trigger_group") ON DELETE NO ACTION ON UPDATE NO ACTION;

-- ----------------------------
-- Foreign Keys structure for table qrtz_cron_triggers
-- ----------------------------
ALTER TABLE "public"."qrtz_cron_triggers" ADD CONSTRAINT "qrtz_cron_triggers_ibfk_1" FOREIGN KEY ("sched_name", "trigger_name", "trigger_group") REFERENCES "public"."qrtz_triggers" ("sched_name", "trigger_name", "trigger_group") ON DELETE NO ACTION ON UPDATE NO ACTION;

-- ----------------------------
-- Foreign Keys structure for table qrtz_simple_triggers
-- ----------------------------
ALTER TABLE "public"."qrtz_simple_triggers" ADD CONSTRAINT "qrtz_simple_triggers_ibfk_1" FOREIGN KEY ("sched_name", "trigger_name", "trigger_group") REFERENCES "public"."qrtz_triggers" ("sched_name", "trigger_name", "trigger_group") ON DELETE NO ACTION ON UPDATE NO ACTION;

-- ----------------------------
-- Foreign Keys structure for table qrtz_simprop_triggers
-- ----------------------------
ALTER TABLE "public"."qrtz_simprop_triggers" ADD CONSTRAINT "qrtz_simprop_triggers_ibfk_1" FOREIGN KEY ("sched_name", "trigger_name", "trigger_group") REFERENCES "public"."qrtz_triggers" ("sched_name", "trigger_name", "trigger_group") ON DELETE NO ACTION ON UPDATE NO ACTION;

-- ----------------------------
-- Foreign Keys structure for table qrtz_triggers
-- ----------------------------
ALTER TABLE "public"."qrtz_triggers" ADD CONSTRAINT "qrtz_triggers_ibfk_1" FOREIGN KEY ("sched_name", "job_name", "job_group") REFERENCES "public"."qrtz_job_details" ("sched_name", "job_name", "job_group") ON DELETE NO ACTION ON UPDATE NO ACTION;

-- ----------------------------
-- 逻辑删除标志列：6 张主表追加 del_flag（与 sys_user/sys_role/sys_dept 保持一致）
-- ----------------------------
ALTER TABLE "public"."sys_config"    ADD COLUMN "del_flag" char(1) COLLATE "pg_catalog"."default" DEFAULT '0';
ALTER TABLE "public"."sys_dict_data" ADD COLUMN "del_flag" char(1) COLLATE "pg_catalog"."default" DEFAULT '0';
ALTER TABLE "public"."sys_dict_type" ADD COLUMN "del_flag" char(1) COLLATE "pg_catalog"."default" DEFAULT '0';
ALTER TABLE "public"."sys_menu"      ADD COLUMN "del_flag" char(1) COLLATE "pg_catalog"."default" DEFAULT '0';
ALTER TABLE "public"."sys_notice"    ADD COLUMN "del_flag" char(1) COLLATE "pg_catalog"."default" DEFAULT '0';
ALTER TABLE "public"."sys_post"      ADD COLUMN "del_flag" char(1) COLLATE "pg_catalog"."default" DEFAULT '0';
COMMENT ON COLUMN "public"."sys_config"."del_flag"    IS '删除标志（0代表存在 1代表删除）';
COMMENT ON COLUMN "public"."sys_dict_data"."del_flag" IS '删除标志（0代表存在 1代表删除）';
COMMENT ON COLUMN "public"."sys_dict_type"."del_flag" IS '删除标志（0代表存在 1代表删除）';
COMMENT ON COLUMN "public"."sys_menu"."del_flag"      IS '删除标志（0代表存在 1代表删除）';
COMMENT ON COLUMN "public"."sys_notice"."del_flag"    IS '删除标志（0代表存在 1代表删除）';
COMMENT ON COLUMN "public"."sys_post"."del_flag"      IS '删除标志（0代表存在 1代表删除）';

