package com.sq.bus.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.sq.bus.domain.BizPhoto;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 地图点位瘦响应：只带渲染所需字段，避免整表 EXIF 打满 JSON。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PhotoMapPointVo implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long photoId;
    private Long albumId;
    private String fileName;
    private String thumbUrl;
    private Integer fileType;
    private Integer isPano;
    private Integer duration;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date shootTime;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String locationSource;
    private BigDecimal locationConfidence;
    private String address;

    public static PhotoMapPointVo from(BizPhoto photo) {
        if (photo == null) {
            return null;
        }
        PhotoMapPointVo vo = new PhotoMapPointVo();
        vo.setPhotoId(photo.getPhotoId());
        vo.setAlbumId(photo.getAlbumId());
        vo.setFileName(photo.getFileName());
        vo.setThumbUrl(photo.getThumbUrl());
        vo.setFileType(photo.getFileType());
        vo.setIsPano(photo.getIsPano());
        vo.setDuration(photo.getDuration());
        vo.setShootTime(photo.getShootTime());
        vo.setLatitude(photo.getLatitude());
        vo.setLongitude(photo.getLongitude());
        vo.setLocationSource(photo.getLocationSource());
        vo.setLocationConfidence(photo.getLocationConfidence());
        vo.setAddress(photo.getAddress());
        return vo;
    }

    public static List<PhotoMapPointVo> fromList(List<BizPhoto> photos) {
        if (photos == null || photos.isEmpty()) {
            return new ArrayList<PhotoMapPointVo>();
        }
        List<PhotoMapPointVo> out = new ArrayList<PhotoMapPointVo>(photos.size());
        for (BizPhoto photo : photos) {
            PhotoMapPointVo vo = from(photo);
            if (vo != null) {
                out.add(vo);
            }
        }
        return out;
    }
}
