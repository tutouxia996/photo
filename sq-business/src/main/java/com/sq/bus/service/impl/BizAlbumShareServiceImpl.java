package com.sq.bus.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sq.bus.constants.AlbumDeleted;
import com.sq.bus.domain.BizAlbumShare;
import com.sq.bus.domain.BizPhoto;
import com.sq.bus.mapper.BizAlbumShareMapper;
import com.sq.bus.service.IBizAlbumShareService;
import com.sq.common.utils.StringUtils;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class BizAlbumShareServiceImpl extends ServiceImpl<BizAlbumShareMapper, BizAlbumShare>
        implements IBizAlbumShareService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] TOKEN_CHARS =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();

    @Override
    public BizAlbumShare getValidByToken(String token) {
        if (StringUtils.isEmpty(token)) {
            return null;
        }
        BizAlbumShare share = getOne(new LambdaQueryWrapper<BizAlbumShare>()
                .eq(BizAlbumShare::getShareToken, token.trim())
                .last("LIMIT 1"));
        if (share == null) {
            return null;
        }
        if (share.getStatus() == null || share.getStatus() != 1) {
            return null;
        }
        if (share.getExpireTime() != null && share.getExpireTime().before(new Date())) {
            return null;
        }
        fillPhotoIdList(share);
        return share;
    }

    @Override
    public void fillPhotoIdList(BizAlbumShare share) {
        if (share == null) {
            return;
        }
        if (StringUtils.isEmpty(share.getPhotoIds())) {
            share.setPhotoIdList(Collections.emptyList());
            return;
        }
        try {
            List<Long> ids = MAPPER.readValue(share.getPhotoIds(), new TypeReference<List<Long>>() {});
            share.setPhotoIdList(ids != null ? ids : Collections.<Long>emptyList());
        } catch (Exception e) {
            share.setPhotoIdList(Collections.emptyList());
        }
    }

    @Override
    public void writePhotoIds(BizAlbumShare share) {
        if (share == null) {
            return;
        }
        List<Long> ids = share.getPhotoIdList();
        if (ids == null || ids.isEmpty()) {
            share.setPhotoIds(null);
            return;
        }
        try {
            share.setPhotoIds(MAPPER.writeValueAsString(ids));
        } catch (Exception e) {
            share.setPhotoIds(null);
        }
    }

    @Override
    public String generateToken() {
        char[] buf = new char[40];
        for (int i = 0; i < buf.length; i++) {
            buf[i] = TOKEN_CHARS[RANDOM.nextInt(TOKEN_CHARS.length)];
        }
        return new String(buf);
    }

    @Override
    public LambdaQueryWrapper<BizPhoto> buildPhotoQuery(BizAlbumShare share) {
        LambdaQueryWrapper<BizPhoto> wrapper = new LambdaQueryWrapper<BizPhoto>()
                .eq(BizPhoto::getAlbumId, share.getAlbumId())
                .eq(BizPhoto::getDeleted, AlbumDeleted.NORMAL)
                .orderByDesc(BizPhoto::getShootTime)
                .orderByDesc(BizPhoto::getPhotoId);

        String scope = normalizeScope(share.getMediaScope());
        if ("image".equals(scope)) {
            wrapper.eq(BizPhoto::getFileType, 1);
        } else if ("video".equals(scope)) {
            wrapper.eq(BizPhoto::getFileType, 2);
        }

        fillPhotoIdList(share);
        String mode = normalizeSelectMode(share.getSelectMode());
        List<Long> ids = share.getPhotoIdList();
        if ("include".equals(mode)) {
            if (ids == null || ids.isEmpty()) {
                wrapper.eq(BizPhoto::getPhotoId, -1L);
            } else {
                wrapper.in(BizPhoto::getPhotoId, ids);
            }
        } else if ("exclude".equals(mode)) {
            if (ids != null && !ids.isEmpty()) {
                wrapper.notIn(BizPhoto::getPhotoId, ids);
            }
        }
        return wrapper;
    }

    @Override
    public boolean isPhotoAllowed(BizAlbumShare share, BizPhoto photo) {
        if (share == null || photo == null) {
            return false;
        }
        if (photo.getAlbumId() == null || !photo.getAlbumId().equals(share.getAlbumId())) {
            return false;
        }
        if (photo.getDeleted() != null && photo.getDeleted() != AlbumDeleted.NORMAL) {
            return false;
        }
        String scope = normalizeScope(share.getMediaScope());
        Integer ft = photo.getFileType();
        if ("image".equals(scope) && (ft == null || ft != 1)) {
            return false;
        }
        if ("video".equals(scope) && (ft == null || ft != 2)) {
            return false;
        }
        fillPhotoIdList(share);
        String mode = normalizeSelectMode(share.getSelectMode());
        List<Long> ids = share.getPhotoIdList();
        Set<Long> idSet = ids == null || ids.isEmpty()
                ? Collections.<Long>emptySet()
                : new HashSet<Long>(ids);
        if ("include".equals(mode)) {
            return photo.getPhotoId() != null && idSet.contains(photo.getPhotoId());
        }
        if ("exclude".equals(mode)) {
            return photo.getPhotoId() == null || !idSet.contains(photo.getPhotoId());
        }
        return true;
    }

    @Override
    public List<BizPhoto> sanitizePhotos(List<BizPhoto> photos) {
        if (photos == null || photos.isEmpty()) {
            return Collections.emptyList();
        }
        List<BizPhoto> out = new ArrayList<BizPhoto>(photos.size());
        for (BizPhoto p : photos) {
            if (p == null) {
                continue;
            }
            BizPhoto n = new BizPhoto();
            n.setPhotoId(p.getPhotoId());
            n.setAlbumId(p.getAlbumId());
            n.setFileName(p.getFileName());
            n.setThumbUrl(p.getThumbUrl());
            n.setFileType(p.getFileType());
            n.setIsPano(p.getIsPano());
            n.setDuration(p.getDuration());
            n.setShootTime(p.getShootTime());
            n.setLatitude(p.getLatitude());
            n.setLongitude(p.getLongitude());
            n.setAddress(p.getAddress());
            out.add(n);
        }
        return out;
    }

    private static String normalizeScope(String scope) {
        if (StringUtils.isEmpty(scope)) {
            return "all";
        }
        String s = scope.trim().toLowerCase();
        if ("image".equals(s) || "1".equals(s) || "photo".equals(s)) {
            return "image";
        }
        if ("video".equals(s) || "2".equals(s)) {
            return "video";
        }
        return "all";
    }

    private static String normalizeSelectMode(String mode) {
        if (StringUtils.isEmpty(mode)) {
            return "all";
        }
        String m = mode.trim().toLowerCase();
        if ("include".equals(m) || "exclude".equals(m)) {
            return m;
        }
        return "all";
    }
}
