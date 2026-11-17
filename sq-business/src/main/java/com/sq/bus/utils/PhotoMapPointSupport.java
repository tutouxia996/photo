package com.sq.bus.utils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sq.bus.constants.PhotoLocationSource;
import com.sq.bus.domain.BizPhoto;
import com.sq.bus.domain.vo.PhotoMapPointVo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 地图点位查询公共逻辑：列裁剪、bbox、硬上限、权威过滤、瘦 VO。
 */
public final class PhotoMapPointSupport {

    /** 未传 limit 时的默认上限（全库首页防爆） */
    public static final int DEFAULT_LIMIT = 5000;
    /** 允许的最大 limit */
    public static final int MAX_LIMIT = 10000;

    private PhotoMapPointSupport() {
    }

    public static void selectMapColumns(LambdaQueryWrapper<BizPhoto> wrapper) {
        if (wrapper == null) {
            return;
        }
        wrapper.select(
                BizPhoto::getPhotoId,
                BizPhoto::getAlbumId,
                BizPhoto::getFileName,
                BizPhoto::getThumbUrl,
                BizPhoto::getFileType,
                BizPhoto::getIsPano,
                BizPhoto::getDuration,
                BizPhoto::getShootTime,
                BizPhoto::getLatitude,
                BizPhoto::getLongitude,
                BizPhoto::getLocationSource,
                BizPhoto::getLocationConfidence,
                BizPhoto::getAddress
        );
    }

    public static void applyBbox(LambdaQueryWrapper<BizPhoto> wrapper,
                                 BigDecimal minLat, BigDecimal maxLat,
                                 BigDecimal minLng, BigDecimal maxLng) {
        if (wrapper == null) {
            return;
        }
        if (minLat != null) {
            wrapper.ge(BizPhoto::getLatitude, minLat);
        }
        if (maxLat != null) {
            wrapper.le(BizPhoto::getLatitude, maxLat);
        }
        if (minLng != null) {
            wrapper.ge(BizPhoto::getLongitude, minLng);
        }
        if (maxLng != null) {
            wrapper.le(BizPhoto::getLongitude, maxLng);
        }
    }

    public static int resolveLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    public static void applyLimit(LambdaQueryWrapper<BizPhoto> wrapper, Integer limit) {
        if (wrapper == null) {
            return;
        }
        wrapper.last("LIMIT " + resolveLimit(limit));
    }

    public static List<BizPhoto> keepAuthoritative(List<BizPhoto> list) {
        if (list == null || list.isEmpty()) {
            return new ArrayList<BizPhoto>();
        }
        List<BizPhoto> out = new ArrayList<BizPhoto>();
        for (BizPhoto photo : list) {
            if (PhotoLocationSource.isAuthoritativeGps(photo)) {
                out.add(photo);
            }
        }
        return out;
    }

    public static List<PhotoMapPointVo> toVos(List<BizPhoto> list) {
        return PhotoMapPointVo.fromList(list);
    }
}
