package com.sq.bus.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sq.bus.domain.BizAlbumShare;
import com.sq.bus.domain.BizPhoto;

import java.util.List;

public interface IBizAlbumShareService extends IService<BizAlbumShare> {

    /**
     * 按令牌查有效分享（启用且未过期）
     */
    BizAlbumShare getValidByToken(String token);

    /**
     * 解析 photoIds JSON 到 photoIdList
     */
    void fillPhotoIdList(BizAlbumShare share);

    /**
     * 序列化 photoIdList 到 photoIds
     */
    void writePhotoIds(BizAlbumShare share);

    /**
     * 解析 albumIds JSON + albumId → albumIdList（去重保序）
     */
    void fillAlbumIdList(BizAlbumShare share);

    /**
     * 序列化 albumIdList 到 albumIds；并同步主 albumId 为列表首项
     */
    void writeAlbumIds(BizAlbumShare share);

    /**
     * 本次分享覆盖的全部相册 ID（至少含 albumId）
     */
    List<Long> resolveAlbumIds(BizAlbumShare share);

    /**
     * 生成不可猜测的分享令牌
     */
    String generateToken();

    /**
     * 按分享规则构造照片查询条件（已含 albumId(s) / deleted）
     */
    LambdaQueryWrapper<BizPhoto> buildPhotoQuery(BizAlbumShare share);

    /**
     * 判断照片是否在本次分享范围内
     */
    boolean isPhotoAllowed(BizAlbumShare share, BizPhoto photo);

    /**
     * 访客可见字段裁剪
     */
    List<BizPhoto> sanitizePhotos(List<BizPhoto> photos);
}
