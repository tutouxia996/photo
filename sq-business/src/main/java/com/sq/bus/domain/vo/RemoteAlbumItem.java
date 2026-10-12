package com.sq.bus.domain.vo;

import lombok.Data;

/**
 * 云盘个人相册（同步目标或排除项）
 */
@Data
public class RemoteAlbumItem {
    private String albumId;
    private String name;
}
