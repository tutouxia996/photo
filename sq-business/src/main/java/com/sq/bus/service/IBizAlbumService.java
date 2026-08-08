package com.sq.bus.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sq.bus.domain.BizAlbum;

public interface IBizAlbumService extends IService<BizAlbum> {

    /**
     * 刷新相册统计（照片数、时间跨度、地点概要）
     */
    void refreshAlbumStats(Long albumId);
}
