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
     * 删除相册并级联清理照片、扫描目录、轨迹等关联数据
     */
    boolean removeAlbums(Collection<Long> albumIds);
}
