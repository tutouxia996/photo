-- AI 出图预设风格管理
-- 已有库执行本脚本

CREATE TABLE IF NOT EXISTS `biz_photo_draw_preset` (
  `preset_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '预设ID',
  `preset_key` varchar(64) NOT NULL COMMENT '预设标识（出图时使用）',
  `label` varchar(100) NOT NULL COMMENT '显示名称',
  `panel_prompt` text NOT NULL COMMENT '万相图生图风格描述/prompt',
  `panel_size` varchar(32) NOT NULL DEFAULT '960*1280' COMMENT '万相输出尺寸',
  `layout` varchar(64) NOT NULL DEFAULT 'FULL_CANVAS' COMMENT '拼版布局 PhotoDrawLayout',
  `grade_photo` tinyint(4) NOT NULL DEFAULT 1 COMMENT '上下双联时是否对原图轻调色：0否 1是',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '排序（升序）',
  `enabled` tinyint(4) NOT NULL DEFAULT 1 COMMENT '是否启用：0禁用 1启用',
  `source` varchar(32) NOT NULL DEFAULT 'manual' COMMENT '来源：builtin/manual/skill_import',
  `skill_raw` mediumtext COMMENT '导入的 skill 原文（可选）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`preset_id`),
  UNIQUE KEY `uk_preset_key` (`preset_key`),
  KEY `idx_enabled_sort` (`enabled`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI出图预设风格';

-- 种子：与原 PhotoDrawPreset 枚举一致（已存在则跳过）
INSERT IGNORE INTO `biz_photo_draw_preset`
(`preset_key`,`label`,`panel_prompt`,`panel_size`,`layout`,`grade_photo`,`sort_order`,`enabled`,`source`,`create_by`,`create_time`)
VALUES
('ink-wash-flat','水墨扁平重构',
 'Transform the reference photo into a matte off-white rice paper ink-wash flat illustration. Preserve overall color tone of the scene. Simplify forms into soft relaxed color blocks with light ink wash edges, no hard outlines, remove fine texture clutter and heavy shadows, keep structural essence. Large negative space, main subject centered upper area on cream paper. No text, no border, no decoration.',
 '960*1280','FULL_WITH_TITLES',1,10,1,'builtin','admin',NOW()),
('travel-poster','旅行摄影海报 3:4',
 'Based on the reference photo scene, create a small risograph-style travel illustration on warm ivory cream paper. Keep the main subject silhouette recognizable, simplify details, use 2-4 muted desaturated colors from the photo, subtle print noise and low-fi risograph texture. Small centered illustration with large empty cream margins. No text, no border, no photorealism.',
 '960*640','TOP_PANEL_BOTTOM_PHOTO',1,20,1,'builtin','admin',NOW()),
('minimal-zine','极简 Zine 海报',
 'Create a sparse vertical minimal zine poster on warm paper texture with large negative space. One small abstract editorial focal element inspired by the reference photo mood and colors, one clear color accent, experimental but restrained typography area left empty. Print/scan reproduction feel, not a commercial ad. No photo collage, no watermark, no long text.',
 '960*1280','MINIMAL_ZINE',1,30,1,'builtin','admin',NOW()),
('photo-diptych','摄影+抽象双联（上下）',
 'Create an abstract geometric flat design panel inspired by the mood, light and colors of the reference photo. Minimal memory-like shapes, soft muted palette, clean editorial magazine layout for the lower half of a poster. No photo realism, no faces, no text, no decorative clutter.',
 '960*640','TOP_PHOTO_BOTTOM_PANEL',1,40,1,'builtin','admin',NOW()),
('photo-diptych-column','摄影+抽象双列',
 'Create a vertical abstract geometric flat design panel inspired by the reference photo mood and palette. Minimal editorial shapes, soft muted colors, modern design magazine aesthetic. No photorealism, no faces, no text.',
 '960*1280','LEFT_PHOTO_RIGHT_PANEL',1,50,1,'builtin','admin',NOW()),
('photo-abstract','摄影+抽象编辑',
 'Create an abstract geometric flat design panel inspired by the mood, light and colors of the reference photo. Minimal memory-like shapes, soft muted palette, clean editorial magazine layout. No photo realism, no faces, no text, no decorative clutter.',
 '960*640','TOP_PHOTO_BOTTOM_PANEL',0,60,1,'builtin','admin',NOW()),
('scene-to-art','场景蒸馏艺术',
 'Distill the reference scene into a standalone fine art piece on warm paper. Independent ink-wash or flat illustration with structural essence of the scene, soft blocks and quiet ink atmosphere, academic minimal composition. The original photograph should not appear; only artistic reinterpretation. No text.',
 '960*1280','FULL_CANVAS',1,70,1,'builtin','admin',NOW()),
('rdr2-journal','荒野大镖客2 · 日记炭笔',
 'Redraw the reference photo as a full-page charcoal pencil sketch from Arthur Morgan''s journal in Red Dead Redemption 2. Aged cream journal paper with faint stains, soft fiber texture, and mild yellowing. Loose but skilled observational charcoal and graphite drawing: visible strokes, cross-hatching, smudged shading, soft edges, uneven pressure, hand-drawn imperfections. Monochrome graphite and charcoal only, slight sepia cast allowed. Keep the scene composition and subject recognizable; simplify fine textures into sketch marks. Western frontier field-journal aesthetic, intimate diary illustration, not a clean digital line art. No photorealism, no color painting, no modern UI, no watermark. Absolutely no handwritten text, signatures, dates, or captions.',
 '960*1280','FULL_CANVAS',1,80,1,'builtin','admin',NOW()),
('photo-relic','Photo Relic 编辑',
 'Create only the lower memory-print panel for a photo-relic editorial artwork. Warm ivory paper with a recognizable primary ink relic shape derived from the reference scene: flat modern printmaking blocks, deep blue and ink black, soft edges, negative-space cuts, one small warm accent from source light. Few deliberate marks, generous blank space. No text, no photo realism.',
 '960*640','TOP_PHOTO_BOTTOM_PANEL',0,90,1,'builtin','admin',NOW());

-- 菜单：AI预设风格（相册管理下；本库 sys_menu 主键列为 id）
INSERT INTO sys_menu(id, menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark, del_flag)
SELECT 3006,'AI预设风格',3000,6,'drawPreset','album/draw-preset/index','',1,0,'C','0','0','album:drawPreset:list','star','admin',NOW(),'AI出图预设风格管理','0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 3006);

INSERT INTO sys_menu(id, menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark, del_flag)
SELECT 3060,'预设查询',3006,1,'','','',1,0,'F','0','0','album:drawPreset:query','#','admin',NOW(),'','0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 3060);

INSERT INTO sys_menu(id, menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark, del_flag)
SELECT 3061,'预设新增',3006,2,'','','',1,0,'F','0','0','album:drawPreset:add','#','admin',NOW(),'','0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 3061);

INSERT INTO sys_menu(id, menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark, del_flag)
SELECT 3062,'预设修改',3006,3,'','','',1,0,'F','0','0','album:drawPreset:edit','#','admin',NOW(),'','0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 3062);

INSERT INTO sys_menu(id, menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark, del_flag)
SELECT 3063,'预设删除',3006,4,'','','',1,0,'F','0','0','album:drawPreset:remove','#','admin',NOW(),'','0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 3063);

-- 超级管理员默认授权（role_id=1）
INSERT IGNORE INTO sys_role_menu(role_id, menu_id) VALUES
(1, 3006),(1, 3060),(1, 3061),(1, 3062),(1, 3063);
