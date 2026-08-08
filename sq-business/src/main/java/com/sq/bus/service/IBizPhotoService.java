package com.sq.bus.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sq.bus.domain.BizPhoto;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface IBizPhotoService extends IService<BizPhoto> {

    List<Map<String, Object>> groupCountByDate(Long albumId);

    /**
     * 仅匹配未删除（deleted=0）的照片
     */
    BizPhoto findByMd5(String md5);

    /**
     * 匹配任意状态的同 MD5 记录（优先回收站，其次已删除）
     */
    BizPhoto findReusableByMd5(String md5);

    boolean trashPhotos(Collection<Long> photoIds);

    boolean restorePhotos(Collection<Long> photoIds);

    boolean purgePhotos(Collection<Long> photoIds);
}
