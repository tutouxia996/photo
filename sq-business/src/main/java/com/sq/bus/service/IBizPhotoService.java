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
     * 匹配回收站中的同 MD5 记录（便于重新上传时复用）
     */
    BizPhoto findReusableByMd5(String md5);

    boolean trashPhotos(Collection<Long> photoIds);

    boolean restorePhotos(Collection<Long> photoIds);

    /**
     * 彻底删除：物理删除数据库记录，并删除本地原图/缩略图
     */
    boolean purgePhotos(Collection<Long> photoIds);
}
