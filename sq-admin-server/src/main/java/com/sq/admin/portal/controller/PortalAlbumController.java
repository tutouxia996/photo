package com.sq.admin.portal.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sq.bus.constants.AlbumDeleted;
import com.sq.bus.domain.BizAlbum;
import com.sq.bus.domain.BizPhoto;
import com.sq.bus.domain.BizTrack;
import com.sq.bus.domain.BizTrackPoint;
import com.sq.bus.service.IBizAlbumService;
import com.sq.bus.service.IBizPhotoService;
import com.sq.bus.service.IBizTrackPointService;
import com.sq.bus.service.IBizTrackService;
import com.sq.common.core.controller.BaseController;
import com.sq.common.core.domain.AjaxResult;
import com.sq.common.core.page.TableDataInfo;
import com.sq.common.utils.StringUtils;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 前台公开接口（无需登录）
 */
@RestController
@RequestMapping("/portal")
public class PortalAlbumController extends BaseController {

    @Autowired
    private IBizAlbumService albumService;

    @Autowired
    private IBizPhotoService photoService;

    @Autowired
    private IBizTrackService trackService;

    @Autowired
    private IBizTrackPointService trackPointService;

    /**
     * 公开相册列表
     */
    @GetMapping("/album/list")
    public TableDataInfo albumList(BizAlbum query) {
        startPage();
        LambdaQueryWrapper<BizAlbum> wrapper = new LambdaQueryWrapper<BizAlbum>()
                .eq(BizAlbum::getIsPublic, 1)
                .eq(BizAlbum::getDeleted, AlbumDeleted.NORMAL)
                .like(StringUtils.isNotEmpty(query.getAlbumName()), BizAlbum::getAlbumName, query.getAlbumName())
                .orderByAsc(BizAlbum::getSortOrder)
                .orderByDesc(BizAlbum::getAlbumId);
        return getDataTable(albumService.list(wrapper));
    }

    /**
     * 相册详情
     */
    @GetMapping("/album/{id}")
    public AjaxResult albumDetail(@PathVariable("id") Long id) {
        BizAlbum album = albumService.getById(id);
        if (album == null || album.getIsPublic() == null || album.getIsPublic() != 1
                || album.getDeleted() == null || album.getDeleted() != AlbumDeleted.NORMAL) {
            return error("相册不存在或未公开");
        }
        return success(album);
    }

    /**
     * 按日期分组统计
     */
    @GetMapping("/photo/groupByDate")
    public AjaxResult groupByDate(@RequestParam Long albumId) {
        BizAlbum album = albumService.getById(albumId);
        if (album == null || album.getIsPublic() == null || album.getIsPublic() != 1
                || album.getDeleted() == null || album.getDeleted() != AlbumDeleted.NORMAL) {
            return error("相册不存在或未公开");
        }
        return success(photoService.groupCountByDate(albumId));
    }

    /**
     * 相册图片列表（可按日期）
     */
    @GetMapping("/photo/list")
    public TableDataInfo photoList(@RequestParam Long albumId,
                                   @RequestParam(required = false) String shootDate) {
        BizAlbum album = albumService.getById(albumId);
        if (album == null || album.getIsPublic() == null || album.getIsPublic() != 1
                || album.getDeleted() == null || album.getDeleted() != AlbumDeleted.NORMAL) {
            return getDataTable(java.util.Collections.<BizPhoto>emptyList());
        }
        startPage();
        LambdaQueryWrapper<BizPhoto> wrapper = new LambdaQueryWrapper<BizPhoto>()
                .eq(BizPhoto::getAlbumId, albumId)
                .eq(BizPhoto::getDeleted, AlbumDeleted.NORMAL)
                .apply(StringUtils.isNotEmpty(shootDate), "DATE_FORMAT(shoot_time,'%Y-%m-%d') = {0}", shootDate)
                .orderByDesc(BizPhoto::getShootTime)
                .orderByDesc(BizPhoto::getPhotoId);
        return getDataTable(photoService.list(wrapper));
    }

    /**
     * 地图点位
     */
    @GetMapping("/photo/mapPoints")
    public AjaxResult mapPoints(@RequestParam(required = false) Long albumId,
                                @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date startTime,
                                @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date endTime) {
        LambdaQueryWrapper<BizPhoto> wrapper = new LambdaQueryWrapper<BizPhoto>()
                .eq(BizPhoto::getDeleted, AlbumDeleted.NORMAL)
                .isNotNull(BizPhoto::getLatitude)
                .isNotNull(BizPhoto::getLongitude)
                .eq(albumId != null, BizPhoto::getAlbumId, albumId)
                .ge(startTime != null, BizPhoto::getShootTime, startTime)
                .le(endTime != null, BizPhoto::getShootTime, endTime)
                .orderByAsc(BizPhoto::getShootTime);
        if (albumId != null) {
            BizAlbum album = albumService.getById(albumId);
            if (album == null || album.getIsPublic() == null || album.getIsPublic() != 1
                    || album.getDeleted() == null || album.getDeleted() != AlbumDeleted.NORMAL) {
                return error("相册不存在或未公开");
            }
        } else {
            // 仅公开相册的图片
            List<BizAlbum> publicAlbums = albumService.list(new LambdaQueryWrapper<BizAlbum>()
                    .eq(BizAlbum::getIsPublic, 1)
                    .eq(BizAlbum::getDeleted, AlbumDeleted.NORMAL));
            if (publicAlbums.isEmpty()) {
                return success(java.util.Collections.emptyList());
            }
            java.util.List<Long> ids = new java.util.ArrayList<Long>();
            for (BizAlbum a : publicAlbums) {
                ids.add(a.getAlbumId());
            }
            wrapper.in(BizPhoto::getAlbumId, ids);
        }
        return success(photoService.list(wrapper));
    }

    /**
     * 相册下公开轨迹
     */
    @GetMapping("/track/listByAlbum")
    public AjaxResult trackList(@RequestParam Long albumId) {
        BizAlbum album = albumService.getById(albumId);
        if (album == null || album.getIsPublic() == null || album.getIsPublic() != 1
                || album.getDeleted() == null || album.getDeleted() != AlbumDeleted.NORMAL) {
            return error("相册不存在或未公开");
        }
        List<BizTrack> list = trackService.list(new LambdaQueryWrapper<BizTrack>()
                .eq(BizTrack::getAlbumId, albumId)
                .eq(BizTrack::getIsPublic, 1)
                .orderByDesc(BizTrack::getTrackId));
        return success(list);
    }

    /**
     * 轨迹点详情
     */
    @GetMapping("/track/{id}/points")
    public AjaxResult trackPoints(@PathVariable("id") Long id) {
        BizTrack track = trackService.getById(id);
        if (track == null || track.getIsPublic() == null || track.getIsPublic() != 1) {
            return error("轨迹不存在或未公开");
        }
        List<BizTrackPoint> points = trackPointService.list(new LambdaQueryWrapper<BizTrackPoint>()
                .eq(BizTrackPoint::getTrackId, id)
                .orderByAsc(BizTrackPoint::getSequence));
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("track", track);
        data.put("points", points);
        return success(data);
    }
}
