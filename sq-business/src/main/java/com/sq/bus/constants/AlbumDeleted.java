package com.sq.bus.constants;

/**
 * 相册/照片删除状态：0未删除 1已删除 2回收站
 */
public final class AlbumDeleted {

    public static final int NORMAL = 0;
    public static final int PURGED = 1;
    public static final int TRASH = 2;

    private AlbumDeleted() {
    }
}
