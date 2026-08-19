package com.sq.bus.service.draw;

/**
 * 出图拼版方式。
 */
public enum PhotoDrawLayout {

    /** 整张万相输出，程序不再贴原图 */
    FULL_CANVAS,

    /** 整张万相输出 + 程序叠加英文标题 */
    FULL_WITH_TITLES,

    /** 上真照片（可轻调色），下 AI 面板 */
    TOP_PHOTO_BOTTOM_PANEL,

    /** 左真照片，右 AI 面板（双列） */
    LEFT_PHOTO_RIGHT_PANEL,

    /** 上 AI 插画区 + 文字，下原图像素级保留 */
    TOP_PANEL_BOTTOM_PHOTO,

    /** 极简 Zine：大留白 + 照片碎片 + AI 视觉焦点 */
    MINIMAL_ZINE
}
