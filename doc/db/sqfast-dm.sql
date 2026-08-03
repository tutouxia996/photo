

-- ----------------------------
-- Records of sys_config
-- ----------------------------
INSERT INTO "HGCAR"."SYS_CONFIG" VALUES (1, '主框架页-默认皮肤样式名称', 'sys.index.skinName', 'skin-red', 'Y', 'admin', '2024-06-21 10:57:33', 'admin', '2026-06-16 16:23:24', '蓝色 skin-blue、绿色 skin-green、紫色 skin-purple、红色 skin-red、黄色 skin-yellow', '0');
INSERT INTO "HGCAR"."SYS_CONFIG" VALUES (2, '用户管理-账号初始密码', 'sys.user.initPassword', '123456', 'Y', 'admin', '2024-06-21 10:57:33', '', NULL, '初始化密码 123456', '0');
INSERT INTO "HGCAR"."SYS_CONFIG" VALUES (3, '主框架页-侧边栏主题', 'sys.index.sideTheme', 'theme-dark', 'Y', 'admin', '2024-06-21 10:57:33', '', NULL, '深色主题theme-dark，浅色主题theme-light', '0');
INSERT INTO "HGCAR"."SYS_CONFIG" VALUES (4, '账号自助-验证码开关', 'sys.account.captchaEnabled', 'true', 'Y', 'admin', '2024-06-21 10:57:33', 'admin', '2026-06-15 17:10:16', '是否开启验证码功能（true开启，false关闭）', '0');
INSERT INTO "HGCAR"."SYS_CONFIG" VALUES (5, '账号自助-是否开启用户注册功能', 'sys.account.registerUser', 'false', 'Y', 'admin', '2024-06-21 10:57:33', '', NULL, '是否开启注册用户功能（true开启，false关闭）', '0');
INSERT INTO "HGCAR"."SYS_CONFIG" VALUES (6, '用户登录-黑名单列表', 'sys.login.blackIPList', '', 'Y', 'admin', '2024-06-21 10:57:33', '', NULL, '设置登录IP黑名单限制，多个匹配项以;分隔，支持匹配（*通配、网段）', '0');
INSERT INTO "HGCAR"."SYS_CONFIG" VALUES (7, '密码策略-密码过期天数', 'sys.password.expireDays', '90', 'Y', 'admin', '2026-06-17 19:12:03', '', NULL, '密码过期天数，超过需强制修改，0或空表示不限制', '0');


-- ----------------------------
-- Records of sys_dept
-- ----------------------------
INSERT INTO "HGCAR"."SYS_DEPT" VALUES (100, 0, '0', '根机构', 0, NULL, '15888888888', NULL, '0', '0', 'admin', '2024-06-21 10:57:31', 'admin', '2026-06-17 20:21:46');
INSERT INTO "HGCAR"."SYS_DEPT" VALUES (2067138531893932033, 100, '0,100', '111', 0, NULL, NULL, NULL, '0', '1', 'admin', '2026-06-17 14:53:26', 'admin', '2026-06-17 14:53:45');
INSERT INTO "HGCAR"."SYS_DEPT" VALUES (2067177650527305729, 100, '0,100', 'qqq', 0, NULL, NULL, NULL, '0', '1', 'admin', '2026-06-17 17:28:53', 'admin', '2026-06-17 17:28:56');


-- ----------------------------
-- Records of sys_dict_data
-- ----------------------------
INSERT INTO "HGCAR"."SYS_DICT_DATA" VALUES (4, 1, '显示', '0', 'sys_show_hide', '', 'primary', 'Y', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '显示菜单', '0');
INSERT INTO "HGCAR"."SYS_DICT_DATA" VALUES (5, 2, '隐藏', '1', 'sys_show_hide', '', 'danger', 'N', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '隐藏菜单', '0');
INSERT INTO "HGCAR"."SYS_DICT_DATA" VALUES (6, 1, '正常', '0', 'sys_normal_disable', '', 'primary', 'Y', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '正常状态', '0');
INSERT INTO "HGCAR"."SYS_DICT_DATA" VALUES (7, 2, '停用', '1', 'sys_normal_disable', '', 'danger', 'N', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '停用状态', '0');
INSERT INTO "HGCAR"."SYS_DICT_DATA" VALUES (8, 1, '正常', '0', 'sys_job_status', '', 'primary', 'Y', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '正常状态', '0');
INSERT INTO "HGCAR"."SYS_DICT_DATA" VALUES (9, 2, '暂停', '1', 'sys_job_status', '', 'danger', 'N', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '停用状态', '0');
INSERT INTO "HGCAR"."SYS_DICT_DATA" VALUES (10, 1, '默认', 'DEFAULT', 'sys_job_group', '', '', 'Y', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '默认分组', '0');
INSERT INTO "HGCAR"."SYS_DICT_DATA" VALUES (11, 2, '系统', 'SYSTEM', 'sys_job_group', '', '', 'N', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '系统分组', '0');
INSERT INTO "HGCAR"."SYS_DICT_DATA" VALUES (12, 1, '是', 'Y', 'sys_yes_no', '', 'primary', 'Y', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '系统默认是', '0');
INSERT INTO "HGCAR"."SYS_DICT_DATA" VALUES (13, 2, '否', 'N', 'sys_yes_no', '', 'danger', 'N', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '系统默认否', '0');
INSERT INTO "HGCAR"."SYS_DICT_DATA" VALUES (14, 1, '通知', '1', 'sys_notice_type', '', 'warning', 'Y', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '通知', '0');
INSERT INTO "HGCAR"."SYS_DICT_DATA" VALUES (15, 2, '公告', '2', 'sys_notice_type', '', 'success', 'N', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '公告', '0');
INSERT INTO "HGCAR"."SYS_DICT_DATA" VALUES (16, 1, '正常', '0', 'sys_notice_status', '', 'primary', 'Y', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '正常状态', '0');
INSERT INTO "HGCAR"."SYS_DICT_DATA" VALUES (17, 2, '关闭', '1', 'sys_notice_status', '', 'danger', 'N', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '关闭状态', '0');
INSERT INTO "HGCAR"."SYS_DICT_DATA" VALUES (18, 99, '其他', '0', 'sys_oper_type', '', 'info', 'N', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '其他操作', '0');
INSERT INTO "HGCAR"."SYS_DICT_DATA" VALUES (19, 1, '新增', '1', 'sys_oper_type', '', 'info', 'N', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '新增操作', '0');
INSERT INTO "HGCAR"."SYS_DICT_DATA" VALUES (20, 2, '修改', '2', 'sys_oper_type', '', 'info', 'N', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '修改操作', '0');
INSERT INTO "HGCAR"."SYS_DICT_DATA" VALUES (21, 3, '删除', '3', 'sys_oper_type', '', 'danger', 'N', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '删除操作', '0');
INSERT INTO "HGCAR"."SYS_DICT_DATA" VALUES (22, 4, '授权', '4', 'sys_oper_type', '', 'primary', 'N', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '授权操作', '0');
INSERT INTO "HGCAR"."SYS_DICT_DATA" VALUES (23, 5, '导出', '5', 'sys_oper_type', '', 'warning', 'N', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '导出操作', '0');
INSERT INTO "HGCAR"."SYS_DICT_DATA" VALUES (24, 6, '导入', '6', 'sys_oper_type', '', 'warning', 'N', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '导入操作', '0');
INSERT INTO "HGCAR"."SYS_DICT_DATA" VALUES (25, 7, '强退', '7', 'sys_oper_type', '', 'danger', 'N', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '强退操作', '0');
INSERT INTO "HGCAR"."SYS_DICT_DATA" VALUES (26, 8, '生成代码', '8', 'sys_oper_type', '', 'warning', 'N', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '生成操作', '0');
INSERT INTO "HGCAR"."SYS_DICT_DATA" VALUES (27, 9, '清空数据', '9', 'sys_oper_type', '', 'danger', 'N', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '清空操作', '0');
INSERT INTO "HGCAR"."SYS_DICT_DATA" VALUES (28, 1, '成功', '0', 'sys_common_status', '', 'primary', 'N', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '正常状态', '0');
INSERT INTO "HGCAR"."SYS_DICT_DATA" VALUES (29, 2, '失败', '1', 'sys_common_status', '', 'danger', 'N', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '停用状态', '0');
INSERT INTO "HGCAR"."SYS_DICT_DATA" VALUES (9002001, 1, '企业', '1', 'demo_declare_enterprise_type', '', 'primary', 'Y', '0', 'admin', '2026-06-18 15:39:11', '', NULL, '企业', '0');
INSERT INTO "HGCAR"."SYS_DICT_DATA" VALUES (9002002, 2, '代理公司', '2', 'demo_declare_enterprise_type', '', 'primary', 'N', '0', 'admin', '2026-06-18 15:39:11', '', NULL, '代理公司', '0');
INSERT INTO "HGCAR"."SYS_DICT_DATA" VALUES (9002003, 3, '报关行', '3', 'demo_declare_enterprise_type', '', 'primary', 'N', '0', 'admin', '2026-06-18 15:39:11', '', NULL, '报关行', '0');
INSERT INTO "HGCAR"."SYS_DICT_DATA" VALUES (9002004, 1, '无需担保', '0', 'demo_risk_guarantee_flag', '', 'primary', 'Y', '0', 'admin', '2026-06-18 15:39:11', '', NULL, '无需担保', '0');
INSERT INTO "HGCAR"."SYS_DICT_DATA" VALUES (9002005, 2, '未担保', '1', 'demo_risk_guarantee_flag', '', 'warning', 'N', '0', 'admin', '2026-06-18 15:39:11', '', NULL, '需要担保但尚未缴纳', '0');
INSERT INTO "HGCAR"."SYS_DICT_DATA" VALUES (9002006, 3, '已担保', '2', 'demo_risk_guarantee_flag', '', 'success', 'N', '0', 'admin', '2026-06-18 15:39:11', '', NULL, '已担保', '0');
INSERT INTO "HGCAR"."SYS_DICT_DATA" VALUES (9002007, 1, '电子口岸申报', '1', 'demo_declare_source_flag', '', 'primary', 'Y', '0', 'admin', '2026-06-18 15:39:11', '', NULL, '电子口岸申报', '0');
INSERT INTO "HGCAR"."SYS_DICT_DATA" VALUES (9002008, 2, '地方平台申报', '2', 'demo_declare_source_flag', '', 'primary', 'N', '0', 'admin', '2026-06-18 15:39:11', '', NULL, '地方平台申报', '0');
INSERT INTO "HGCAR"."SYS_DICT_DATA" VALUES (9002009, 3, '其它申报', '3', 'demo_declare_source_flag', '', 'info', 'N', '0', 'admin', '2026-06-18 15:39:11', '', NULL, '其它申报', '0');
INSERT INTO "HGCAR"."SYS_DICT_DATA" VALUES (9002010, 1, '备案申请', '1', 'demo_declare_type', '', 'primary', 'Y', '0', 'admin', '2026-06-18 15:39:11', '', NULL, '备案申请', '0');
INSERT INTO "HGCAR"."SYS_DICT_DATA" VALUES (9002011, 2, '变更申请', '2', 'demo_declare_type', '', 'warning', 'N', '0', 'admin', '2026-06-18 15:39:11', '', NULL, '变更申请', '0');
INSERT INTO "HGCAR"."SYS_DICT_DATA" VALUES (9002012, 1, '暂存', '0', 'demo_approval_status', '', 'info', 'Y', '0', 'admin', '2026-06-18 15:39:11', '', NULL, '暂存', '0');
INSERT INTO "HGCAR"."SYS_DICT_DATA" VALUES (9002013, 2, '通过', '1', 'demo_approval_status', '', 'success', 'N', '0', 'admin', '2026-06-18 15:39:11', '', NULL, '通过', '0');
INSERT INTO "HGCAR"."SYS_DICT_DATA" VALUES (9002014, 3, '退单', '2', 'demo_approval_status', '', 'danger', 'N', '0', 'admin', '2026-06-18 15:39:11', '', NULL, '退单', '0');
INSERT INTO "HGCAR"."SYS_DICT_DATA" VALUES (9002015, 4, '待审核', '3', 'demo_approval_status', '', 'warning', 'N', '0', 'admin', '2026-06-18 15:39:11', '', NULL, '待审核', '0');
INSERT INTO "HGCAR"."SYS_DICT_DATA" VALUES (9002016, 1, '在执行', '1', 'demo_execute_flag', '', 'primary', 'Y', '0', 'admin', '2026-06-18 15:39:11', '', NULL, '在执行', '0');
INSERT INTO "HGCAR"."SYS_DICT_DATA" VALUES (9002017, 2, '已注销', '2', 'demo_execute_flag', '', 'danger', 'N', '0', 'admin', '2026-06-18 15:39:11', '', NULL, '已注销', '0');
INSERT INTO "HGCAR"."SYS_DICT_DATA" VALUES (9002018, 1, '加工账册', '1', 'demo_qualification_type', '', 'primary', 'Y', '0', 'admin', '2026-06-18 15:39:11', '', NULL, '加工账册', '0');
INSERT INTO "HGCAR"."SYS_DICT_DATA" VALUES (9002019, 2, '以企业为单元', '2', 'demo_qualification_type', '', 'success', 'N', '0', 'admin', '2026-06-18 15:39:11', '', NULL, '以企业为单元', '0');
INSERT INTO "HGCAR"."SYS_DICT_DATA" VALUES (9002020, 3, '工单核销', '3', 'demo_qualification_type', '', 'warning', 'N', '0', 'admin', '2026-06-18 15:39:11', '', NULL, '工单核销', '0');
INSERT INTO "HGCAR"."SYS_DICT_DATA" VALUES (9002021, 1, '未修改', '0', 'demo_modify_flag', '', 'info', 'Y', '0', 'admin', '2026-06-18 15:39:11', '', NULL, '未修改', '0');
INSERT INTO "HGCAR"."SYS_DICT_DATA" VALUES (9002022, 2, '修改', '1', 'demo_modify_flag', '', 'warning', 'N', '0', 'admin', '2026-06-18 15:39:11', '', NULL, '修改', '0');
INSERT INTO "HGCAR"."SYS_DICT_DATA" VALUES (9002023, 3, '删除', '2', 'demo_modify_flag', '', 'danger', 'N', '0', 'admin', '2026-06-18 15:39:11', '', NULL, '删除', '0');
INSERT INTO "HGCAR"."SYS_DICT_DATA" VALUES (9002024, 4, '增加', '3', 'demo_modify_flag', '', 'success', 'N', '0', 'admin', '2026-06-18 15:39:11', '', NULL, '增加', '0');


-- ----------------------------
-- Records of sys_dict_type
-- ----------------------------
INSERT INTO "HGCAR"."SYS_DICT_TYPE" VALUES (1, '用户性别', 'sys_user_sex', '0', 'admin', '2024-06-21 10:57:33', 'admin', '2024-07-25 15:42:02', '用户性别列表', '0');
INSERT INTO "HGCAR"."SYS_DICT_TYPE" VALUES (2, '菜单状态', 'sys_show_hide', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '菜单状态列表', '0');
INSERT INTO "HGCAR"."SYS_DICT_TYPE" VALUES (3, '系统开关', 'sys_normal_disable', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '系统开关列表', '0');
INSERT INTO "HGCAR"."SYS_DICT_TYPE" VALUES (4, '任务状态', 'sys_job_status', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '任务状态列表', '0');
INSERT INTO "HGCAR"."SYS_DICT_TYPE" VALUES (5, '任务分组', 'sys_job_group', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '任务分组列表', '0');
INSERT INTO "HGCAR"."SYS_DICT_TYPE" VALUES (6, '系统是否', 'sys_yes_no', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '系统是否列表', '0');
INSERT INTO "HGCAR"."SYS_DICT_TYPE" VALUES (7, '通知类型', 'sys_notice_type', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '通知类型列表', '0');
INSERT INTO "HGCAR"."SYS_DICT_TYPE" VALUES (8, '通知状态', 'sys_notice_status', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '通知状态列表', '0');
INSERT INTO "HGCAR"."SYS_DICT_TYPE" VALUES (9, '操作类型', 'sys_oper_type', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '操作类型列表', '0');
INSERT INTO "HGCAR"."SYS_DICT_TYPE" VALUES (10, '系统状态', 'sys_common_status', '0', 'admin', '2024-06-21 10:57:33', '', NULL, '登录状态列表', '0');
INSERT INTO "HGCAR"."SYS_DICT_TYPE" VALUES (9001001, '申报企业类型代码', 'demo_declare_enterprise_type', '0', 'admin', '2026-06-18 15:38:53', '', NULL, '申报企业类型代码', '0');
INSERT INTO "HGCAR"."SYS_DICT_TYPE" VALUES (9001002, '风险担保标记代码', 'demo_risk_guarantee_flag', '0', 'admin', '2026-06-18 15:38:53', '', NULL, '风险担保标记代码', '0');
INSERT INTO "HGCAR"."SYS_DICT_TYPE" VALUES (9001003, '申报来源标记代码', 'demo_declare_source_flag', '0', 'admin', '2026-06-18 15:38:53', '', NULL, '申报来源标记代码', '0');
INSERT INTO "HGCAR"."SYS_DICT_TYPE" VALUES (9001004, '申报类型代码', 'demo_declare_type', '0', 'admin', '2026-06-18 15:38:53', '', NULL, '申报类型代码', '0');
INSERT INTO "HGCAR"."SYS_DICT_TYPE" VALUES (9001005, '审批状态代码', 'demo_approval_status', '0', 'admin', '2026-06-18 15:38:53', '', NULL, '审批状态代码', '0');
INSERT INTO "HGCAR"."SYS_DICT_TYPE" VALUES (9001006, '执行标记代码', 'demo_execute_flag', '0', 'admin', '2026-06-18 15:38:53', '', NULL, '执行标记代码', '0');
INSERT INTO "HGCAR"."SYS_DICT_TYPE" VALUES (9001007, '资质类型', 'demo_qualification_type', '0', 'admin', '2026-06-18 15:38:53', '', NULL, '资质类型', '0');
INSERT INTO "HGCAR"."SYS_DICT_TYPE" VALUES (9001008, '修改标记代码', 'demo_modify_flag', '0', 'admin', '2026-06-18 15:38:53', '', NULL, '修改标记代码', '0');
INSERT INTO "HGCAR"."SYS_DICT_TYPE" VALUES (2066703866905223169, 'a', 'a', '0', 'admin', '2026-06-16 10:06:14', 'admin', '2026-06-17 17:27:37', NULL, '1');

-- ----------------------------
-- Records of sys_job
-- ----------------------------
INSERT INTO "HGCAR"."SYS_JOB" VALUES (1, '系统默认（无参）', 'DEFAULT', 'ryTask.ryNoParams', '0/10 * * * * ?', '3', '1', '1', 'admin', '2024-06-21 10:57:33', '', NULL, '');
INSERT INTO "HGCAR"."SYS_JOB" VALUES (2, '系统默认（有参）', 'DEFAULT', 'ryTask.ryParams(\'ry\')', '0/15 * * * * ?', '3', '1', '1', 'admin', '2024-06-21 10:57:33', '', NULL, '');
INSERT INTO "HGCAR"."SYS_JOB" VALUES (3, '系统默认（多参）', 'DEFAULT', 'ryTask.ryMultipleParams(\'ry\', true, 2000L, 316.50D, 100)', '0/20 * * * * ?', '3', '1', '1', 'admin', '2024-06-21 10:57:33', '', NULL, '');

-- ----------------------------
-- Records of sys_menu
-- ----------------------------
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1, '系统管理', 0, 10, 'system', NULL, '', 1, 0, 'M', '0', '0', '', 'system', 'admin', '2024-06-21 10:57:32', 'admin', '2024-08-09 10:28:26', '系统管理目录', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (2, '系统监控', 0, 11, 'monitor', NULL, '', 1, 0, 'M', '0', '0', '', 'monitor', 'admin', '2024-06-21 10:57:32', 'admin', '2024-08-09 10:28:33', '系统监控目录', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (3, '系统工具', 0, 12, 'tool', NULL, '', 1, 0, 'M', '0', '0', '', 'tool', 'admin', '2024-06-21 10:57:32', 'admin', '2026-06-16 10:01:58', '系统工具目录', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (100, '用户管理', 1, 1, 'user', 'system/user/index', '', 1, 0, 'C', '0', '0', 'system:user:list', 'user', 'admin', '2024-06-21 10:57:32', '', NULL, '用户管理菜单', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (101, '角色管理', 1, 2, 'role', 'system/role/index', '', 1, 0, 'C', '0', '0', 'system:role:list', 'peoples', 'admin', '2024-06-21 10:57:32', '', NULL, '角色管理菜单', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (102, '菜单管理', 1, 3, 'menu', 'system/menu/index', '', 1, 0, 'C', '0', '0', 'system:menu:list', 'tree-table', 'admin', '2024-06-21 10:57:32', '', NULL, '菜单管理菜单', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (103, '机构管理', 1, 4, 'dept', 'system/dept/index', '', 1, 0, 'C', '0', '0', 'system:dept:list', 'tree', 'admin', '2024-06-21 10:57:32', 'admin', '2024-07-01 10:01:26', '部门管理菜单', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (104, '岗位管理', 1, 5, 'post', 'system/post/index', '', 1, 0, 'C', '0', '0', 'system:post:list', 'post', 'admin', '2024-06-21 10:57:32', 'admin', '2025-02-17 11:01:44', '岗位管理菜单', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (105, '字典管理', 1, 6, 'dict', 'system/dict/index', '', 1, 0, 'C', '0', '1', 'system:dict:list', 'dict', 'admin', '2024-06-21 10:57:32', 'admin', '2025-02-17 11:02:01', '字典管理菜单', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (106, '参数设置', 1, 7, 'config', 'system/config/index', '', 1, 0, 'C', '0', '0', 'system:config:list', 'edit', 'admin', '2024-06-21 10:57:32', '', NULL, '参数设置菜单', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (107, '通知公告', 1, 8, 'notice', 'system/notice/index', '', 1, 0, 'C', '1', '0', 'system:notice:list', 'message', 'admin', '2024-06-21 10:57:32', 'admin', '2024-07-17 09:52:07', '通知公告菜单', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (108, '日志管理', 1, 9, 'log', '', '', 1, 0, 'M', '0', '0', '', 'log', 'admin', '2024-06-21 10:57:32', '', NULL, '日志管理菜单', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (109, '在线用户', 2, 1, 'online', 'monitor/online/index', '', 1, 0, 'C', '0', '0', 'monitor:online:list', 'online', 'admin', '2024-06-21 10:57:32', '', NULL, '在线用户菜单', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (110, '定时任务', 2, 2, 'job', 'monitor/job/index', '', 1, 0, 'C', '0', '0', 'monitor:job:list', 'job', 'admin', '2024-06-21 10:57:32', '', NULL, '定时任务菜单', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (111, '数据监控', 2, 3, 'druid', 'monitor/druid/index', '', 1, 0, 'C', '0', '0', 'monitor:druid:list', 'druid', 'admin', '2024-06-21 10:57:32', '', NULL, '数据监控菜单', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (112, '服务监控', 2, 4, 'server', 'monitor/server/index', '', 1, 0, 'C', '0', '0', 'monitor:server:list', 'server', 'admin', '2024-06-21 10:57:32', '', NULL, '服务监控菜单', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (113, '缓存监控', 2, 5, 'cache', 'monitor/cache/index', '', 1, 0, 'C', '0', '0', 'monitor:cache:list', 'redis', 'admin', '2024-06-21 10:57:32', '', NULL, '缓存监控菜单', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (114, '缓存列表', 2, 6, 'cacheList', 'monitor/cache/list', '', 1, 0, 'C', '0', '0', 'monitor:cache:list', 'redis-list', 'admin', '2024-06-21 10:57:32', '', NULL, '缓存列表菜单', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (115, '表单构建', 3, 1, 'build', 'tool/build/index', '', 1, 0, 'C', '0', '0', 'tool:build:list', 'build', 'admin', '2024-06-21 10:57:32', '', NULL, '表单构建菜单', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (116, '代码生成', 3, 2, 'gen', 'tool/gen/index', '', 1, 0, 'C', '0', '0', 'tool:gen:list', 'code', 'admin', '2024-06-21 10:57:32', '', NULL, '代码生成菜单', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (117, '系统接口', 3, 3, 'swagger', 'tool/swagger/index', '', 1, 0, 'C', '0', '0', 'tool:swagger:list', 'swagger', 'admin', '2024-06-21 10:57:32', '', NULL, '系统接口菜单', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (500, '操作日志', 108, 1, 'operlog', 'monitor/operlog/index', '', 1, 0, 'C', '0', '0', 'monitor:operlog:list', 'form', 'admin', '2024-06-21 10:57:32', '', NULL, '操作日志菜单', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (501, '登录日志', 108, 2, 'logininfor', 'monitor/logininfor/index', '', 1, 0, 'C', '0', '0', 'monitor:logininfor:list', 'logininfor', 'admin', '2024-06-21 10:57:32', '', NULL, '登录日志菜单', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1000, '用户查询', 100, 1, '', '', '', 1, 0, 'F', '0', '0', 'system:user:query', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1001, '用户新增', 100, 2, '', '', '', 1, 0, 'F', '0', '0', 'system:user:add', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1002, '用户修改', 100, 3, '', '', '', 1, 0, 'F', '0', '0', 'system:user:edit', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1003, '用户删除', 100, 4, '', '', '', 1, 0, 'F', '0', '0', 'system:user:remove', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1004, '用户导出', 100, 5, '', '', '', 1, 0, 'F', '0', '0', 'system:user:export', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1005, '用户导入', 100, 6, '', '', '', 1, 0, 'F', '0', '0', 'system:user:import', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1006, '重置密码', 100, 7, '', '', '', 1, 0, 'F', '0', '0', 'system:user:resetPwd', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1007, '角色查询', 101, 1, '', '', '', 1, 0, 'F', '0', '0', 'system:role:query', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1008, '角色新增', 101, 2, '', '', '', 1, 0, 'F', '0', '0', 'system:role:add', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1009, '角色修改', 101, 3, '', '', '', 1, 0, 'F', '0', '0', 'system:role:edit', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1010, '角色删除', 101, 4, '', '', '', 1, 0, 'F', '0', '0', 'system:role:remove', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1011, '角色导出', 101, 5, '', '', '', 1, 0, 'F', '0', '0', 'system:role:export', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1012, '菜单查询', 102, 1, '', '', '', 1, 0, 'F', '0', '0', 'system:menu:query', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1013, '菜单新增', 102, 2, '', '', '', 1, 0, 'F', '0', '0', 'system:menu:add', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1014, '菜单修改', 102, 3, '', '', '', 1, 0, 'F', '0', '0', 'system:menu:edit', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1015, '菜单删除', 102, 4, '', '', '', 1, 0, 'F', '0', '0', 'system:menu:remove', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1016, '部门查询', 103, 1, '', '', '', 1, 0, 'F', '0', '0', 'system:dept:query', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1017, '部门新增', 103, 2, '', '', '', 1, 0, 'F', '0', '0', 'system:dept:add', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1018, '部门修改', 103, 3, '', '', '', 1, 0, 'F', '0', '0', 'system:dept:edit', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1019, '部门删除', 103, 4, '', '', '', 1, 0, 'F', '0', '0', 'system:dept:remove', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1020, '岗位查询', 104, 1, '', '', '', 1, 0, 'F', '0', '0', 'system:post:query', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1021, '岗位新增', 104, 2, '', '', '', 1, 0, 'F', '0', '0', 'system:post:add', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1022, '岗位修改', 104, 3, '', '', '', 1, 0, 'F', '0', '0', 'system:post:edit', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1023, '岗位删除', 104, 4, '', '', '', 1, 0, 'F', '0', '0', 'system:post:remove', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1024, '岗位导出', 104, 5, '', '', '', 1, 0, 'F', '0', '0', 'system:post:export', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1025, '字典查询', 105, 1, '#', '', '', 1, 0, 'F', '0', '0', 'system:dict:query', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1026, '字典新增', 105, 2, '#', '', '', 1, 0, 'F', '0', '0', 'system:dict:add', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1027, '字典修改', 105, 3, '#', '', '', 1, 0, 'F', '0', '0', 'system:dict:edit', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1028, '字典删除', 105, 4, '#', '', '', 1, 0, 'F', '0', '0', 'system:dict:remove', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1029, '字典导出', 105, 5, '#', '', '', 1, 0, 'F', '0', '0', 'system:dict:export', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1030, '参数查询', 106, 1, '#', '', '', 1, 0, 'F', '0', '0', 'system:config:query', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1031, '参数新增', 106, 2, '#', '', '', 1, 0, 'F', '0', '0', 'system:config:add', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1032, '参数修改', 106, 3, '#', '', '', 1, 0, 'F', '0', '0', 'system:config:edit', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1033, '参数删除', 106, 4, '#', '', '', 1, 0, 'F', '0', '0', 'system:config:remove', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1034, '参数导出', 106, 5, '#', '', '', 1, 0, 'F', '0', '0', 'system:config:export', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1035, '公告查询', 107, 1, '#', '', '', 1, 0, 'F', '0', '0', 'system:notice:query', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1036, '公告新增', 107, 2, '#', '', '', 1, 0, 'F', '0', '0', 'system:notice:add', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1037, '公告修改', 107, 3, '#', '', '', 1, 0, 'F', '0', '0', 'system:notice:edit', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1038, '公告删除', 107, 4, '#', '', '', 1, 0, 'F', '0', '0', 'system:notice:remove', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1039, '操作查询', 500, 1, '#', '', '', 1, 0, 'F', '0', '0', 'monitor:operlog:query', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1040, '操作删除', 500, 2, '#', '', '', 1, 0, 'F', '0', '0', 'monitor:operlog:remove', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1041, '日志导出', 500, 3, '#', '', '', 1, 0, 'F', '0', '0', 'monitor:operlog:export', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1042, '登录查询', 501, 1, '#', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:query', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1043, '登录删除', 501, 2, '#', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:remove', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1044, '日志导出', 501, 3, '#', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:export', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1045, '账户解锁', 501, 4, '#', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:unlock', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1046, '在线查询', 109, 1, '#', '', '', 1, 0, 'F', '0', '0', 'monitor:online:query', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1047, '批量强退', 109, 2, '#', '', '', 1, 0, 'F', '0', '0', 'monitor:online:batchLogout', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1048, '单条强退', 109, 3, '#', '', '', 1, 0, 'F', '0', '0', 'monitor:online:forceLogout', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1049, '任务查询', 110, 1, '#', '', '', 1, 0, 'F', '0', '0', 'monitor:job:query', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1050, '任务新增', 110, 2, '#', '', '', 1, 0, 'F', '0', '0', 'monitor:job:add', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1051, '任务修改', 110, 3, '#', '', '', 1, 0, 'F', '0', '0', 'monitor:job:edit', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1052, '任务删除', 110, 4, '#', '', '', 1, 0, 'F', '0', '0', 'monitor:job:remove', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1053, '状态修改', 110, 5, '#', '', '', 1, 0, 'F', '0', '0', 'monitor:job:changeStatus', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1054, '任务导出', 110, 6, '#', '', '', 1, 0, 'F', '0', '0', 'monitor:job:export', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1055, '生成查询', 116, 1, '#', '', '', 1, 0, 'F', '0', '0', 'tool:gen:query', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1056, '生成修改', 116, 2, '#', '', '', 1, 0, 'F', '0', '0', 'tool:gen:edit', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1057, '生成删除', 116, 3, '#', '', '', 1, 0, 'F', '0', '0', 'tool:gen:remove', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1058, '导入代码', 116, 4, '#', '', '', 1, 0, 'F', '0', '0', 'tool:gen:import', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1059, '预览代码', 116, 5, '#', '', '', 1, 0, 'F', '0', '0', 'tool:gen:preview', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (1060, '生成代码', 116, 6, '#', '', '', 1, 0, 'F', '0', '0', 'tool:gen:code', '#', 'admin', '2024-06-21 10:57:32', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (2256, '数据字典', 1, 6, 'newDict', 'system/newDict/index', NULL, 1, 0, 'C', '0', '0', 'system:dict:list', 'dict', 'admin', '2024-07-22 11:06:53', 'admin', '2026-05-28 15:57:01', '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (2279, '字典查询', 2256, 1, '', NULL, NULL, 1, 0, 'F', '0', '0', 'system:dict:query', '#', 'admin', '2024-07-24 11:07:57', 'admin', '2024-07-24 11:08:09', '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (2280, '字典保存', 2256, 4, '', NULL, NULL, 1, 0, 'F', '0', '0', 'system:dict:addDictTypeAndData', '#', 'admin', '2024-07-24 11:08:43', 'ghn', '2024-07-24 11:24:31', '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (2281, '字典导出', 2256, 5, '', NULL, NULL, 1, 0, 'F', '0', '0', 'system:dict:export', '#', 'admin', '2024-07-24 11:09:05', 'ghn', '2024-07-24 11:25:08', '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (2282, '字典新增', 2256, 2, '', NULL, NULL, 1, 0, 'F', '0', '0', 'bus:dict:add', '#', 'ghn', '2024-07-24 11:25:00', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (2283, '字典数据删除', 2256, 3, '', NULL, NULL, 1, 0, 'F', '0', '0', 'bus:dict:remove', '#', 'ghn', '2024-07-24 11:25:35', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (9003001, '示例功能', 0, 20, 'demo', NULL, '', 1, 0, 'M', '0', '0', '', 'example', 'admin', '2026-06-18 15:39:51', '', NULL, '示例功能目录', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (9003002, '企业资质申请', 9003001, 1, 'qualification', 'demo/qualification/index', '', 1, 0, 'C', '0', '0', 'demo:qualification:list', 'form', 'admin', '2026-06-18 15:39:59', '', NULL, '企业资质申请菜单', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (9003003, '查询', 9003002, 1, '#', '', '', 1, 0, 'F', '0', '0', 'demo:qualification:query', '#', 'admin', '2026-06-18 15:40:41', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (9003004, '新增', 9003002, 2, '#', '', '', 1, 0, 'F', '0', '0', 'demo:qualification:add', '#', 'admin', '2026-06-18 15:40:41', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (9003005, '修改', 9003002, 3, '#', '', '', 1, 0, 'F', '0', '0', 'demo:qualification:edit', '#', 'admin', '2026-06-18 15:40:41', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (9003006, '删除', 9003002, 4, '#', '', '', 1, 0, 'F', '0', '0', 'demo:qualification:remove', '#', 'admin', '2026-06-18 15:40:41', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (9003007, '申报', 9003002, 5, '#', '', '', 1, 0, 'F', '0', '0', 'demo:qualification:declare', '#', 'admin', '2026-06-18 15:40:41', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (9003008, '查看', 9003002, 6, '#', '', '', 1, 0, 'F', '0', '0', 'demo:qualification:view', '#', 'admin', '2026-06-18 15:40:41', '', NULL, '', '0');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (2067170435405938690, 'aa', 0, 1, 'aaa', NULL, NULL, 1, 0, 'M', '0', '0', NULL, '#', 'admin', '2026-06-17 17:00:13', 'admin', '2026-06-17 17:01:54', '', '1');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (2067176784088956929, 'aaa', 0, 1, 'aaaa', NULL, NULL, 1, 0, 'M', '0', '0', NULL, '#', 'admin', '2026-06-17 17:25:27', 'admin', '2026-06-17 17:25:31', '', '1');
INSERT INTO "HGCAR"."SYS_MENU" VALUES (2069255079626518529, '企业资质申请2', 9003001, 1, 'qualification2', 'demo/qualification/layout-demo', NULL, 1, 0, 'C', '0', '0', 'demo:qualification:list', 'excel', 'admin', '2026-06-23 11:03:48', 'admin', '2026-06-23 11:05:20', '', '0');

-- ----------------------------
-- Records of sys_post
-- ----------------------------
INSERT INTO "HGCAR"."SYS_POST" VALUES (2066870442681831425, '12', '12', 1, '0', 'admin', '2026-06-16 21:08:09', 'admin', '2026-06-17 17:25:47', NULL, '1');

-- ----------------------------
-- Records of sys_role
-- ----------------------------
INSERT INTO "HGCAR"."SYS_ROLE" VALUES (1, 100, '超级管理员', 'admin', 1, '1', 1, 1, '0', '0', 'admin', '2024-06-21 10:57:32', '', NULL, '超级管理员');
INSERT INTO "HGCAR"."SYS_ROLE" VALUES (2067082263217004545, NULL, '1', '1', 0, '1', 1, 1, '0', '1', 'admin', '2026-06-17 11:09:51', 'admin', '2026-06-17 14:52:33', NULL);
INSERT INTO "HGCAR"."SYS_ROLE" VALUES (2067162679579975681, NULL, 'aaaaaaa', 'aaaaaaaaaa', 0, '1', 1, 1, '0', '1', 'admin', '2026-06-17 16:29:24', 'admin', '2026-06-17 16:29:28', NULL);
INSERT INTO "HGCAR"."SYS_ROLE" VALUES (2067177568423804929, NULL, 'qqq', 'qqqq', 0, '1', 1, 1, '0', '1', 'admin', '2026-06-17 17:28:33', 'admin', '2026-06-17 17:28:38', NULL);
INSERT INTO "HGCAR"."SYS_ROLE" VALUES (2067208508927791105, NULL, 'a', 'a', 0, '1', 1, 1, '0', '1', 'admin', '2026-06-17 19:31:30', 'admin', '2026-06-17 20:25:57', NULL);

-- ----------------------------
-- Records of sys_user
-- ----------------------------
INSERT INTO "HGCAR"."SYS_USER" VALUES (1, 103, 'admin', '超级管理员', '00', NULL, '15888888888', '1', '', '{sm3}DOu27VYMzaNrcShzQgbBwg==$2e107930584528e2164da4201a55639f64bbe495b13ba99e554a96daa70488df', '0', '0', '127.0.0.1', '2026-06-23 23:23:21', 'admin', '2024-06-21 10:57:31', '', '2026-06-23 23:23:19', '管理员', '2026-06-17 19:28:31');
INSERT INTO "HGCAR"."SYS_USER" VALUES (2067082219654963202, NULL, '12', '12', '00', '', '', '0', '', '{sm3}gg9SR8H/QRd9qGO7h8rqpQ==$e588859c007297bb99e8fd742a6e6c6a92e4c65920e55f3093d55a81b711647b', '0', '2', '', NULL, 'admin', '2026-06-17 11:09:40', '', NULL, NULL, '2026-06-17 11:09:40');
INSERT INTO "HGCAR"."SYS_USER" VALUES (2067163189846417409, NULL, '啊啊啊啊啊啊', 'aa', '00', '', '', '0', '', '{sm3}l6/37nT4EcfV/Wd9fHWcVA==$ed003f8e285f99d437c0760cf4d9fd29870df745be753e9e88f5d4af077f4833', '0', '1', '', NULL, 'admin', '2026-06-17 16:31:25', 'admin', '2026-06-17 16:31:38', NULL, '2026-06-17 16:31:25');
INSERT INTO "HGCAR"."SYS_USER" VALUES (2067177512153022465, NULL, 'qqq', 'qqq', '00', '', '', '0', '', '{sm3}nn90IRP5y+7jayxr1MHnwg==$5be90bece6ee9b2b30bc5ab61da3c818ba35cf702caadd562f2621a9a2b899aa', '0', '1', '', NULL, 'admin', '2026-06-17 17:28:20', 'admin', '2026-06-17 17:28:23', NULL, '2026-06-17 17:28:20');
INSERT INTO "HGCAR"."SYS_USER" VALUES (2067182934180122626, 100, 'aaaa', 'aaa', '00', '12@qq.com', '13812341234', '0', '', '{sm3}4lXxitbc0gEmoLBdz/+cQw==$6a4b19967b33015f46458a513fc19da9aca778576e800fd6a1e964b61a8b74f2', '0', '0', '127.0.0.1', '2026-06-17 20:13:39', 'admin', '2026-06-17 17:49:53', 'admin', '2026-06-17 20:26:22', NULL, '2026-06-17 20:14:10');
INSERT INTO "HGCAR"."SYS_USER" VALUES (2067187277528813569, NULL, 'aaaaa', 'aaaa', '00', '', '', '0', '', '{sm3}bibNaA182k7CADwm3kN3JQ==$a4a7bc5fd99a064cdcf522dfc87c260799aeed7376f8b6e79c7a6d69047a4dca', '1', '1', '', NULL, 'admin', '2026-06-17 18:07:08', 'admin', '2026-06-17 20:38:34', NULL, NULL);

