package com.sq.bus.constants;

/**
 * 相册/照片删除状态：0正常 1历史彻底删除标记（已弃用，现改为物理删除） 2回收站
 */
public final class AlbumDeleted {

    public static final int NORMAL = 0;
    public static final int PURGED = 1;
    public static final int TRASH = 2;

    private AlbumDeleted() {
    }
}
