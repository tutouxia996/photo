package com.sq.bus.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sq.bus.domain.BizAlbum;

import java.util.Collection;

public interface IBizAlbumService extends IService<BizAlbum> {

    /**
     * 刷新相册统计（照片数、时间跨度、地点概要）
     */
    void refreshAlbumStats(Long albumId);

    /**
     * 放入回收站（deleted=2），其下正常照片一并入回收站
     */
    boolean trashAlbums(Collection<Long> albumIds);

    /**
     * 从回收站恢复（deleted=0）
     */
    boolean restoreAlbums(Collection<Long> albumIds);

    /**
     * 彻底删除标记（deleted=1）
     */
    boolean purgeAlbums(Collection<Long> albumIds);

    /**
     * 兼容旧接口：等同于 trashAlbums
     */
    boolean removeAlbums(Collection<Long> albumIds);
}
