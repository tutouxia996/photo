package com.sq.admin.share.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sq.bus.constants.AlbumDeleted;
import com.sq.bus.domain.BizAlbum;
import com.sq.bus.domain.BizAlbumShare;
import com.sq.bus.domain.BizPhoto;
import com.sq.bus.domain.BizTrack;
import com.sq.bus.domain.BizTrackPoint;
import com.sq.bus.service.IBizAlbumService;
import com.sq.bus.service.IBizAlbumShareService;
import com.sq.bus.service.IBizPhotoService;
import com.sq.bus.service.IBizTrackGpxFileService;
import com.sq.bus.service.IBizTrackPointService;
import com.sq.bus.service.IBizTrackService;
import com.sq.common.core.controller.BaseController;
import com.sq.common.core.domain.AjaxResult;
import com.sq.common.core.page.TableDataInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 公开分享访问（无需登录，仅凭分享令牌）
 */
@RestController
@RequestMapping("/share")
public class PublicShareController extends BaseController {

    @Autowired
    private IBizAlbumShareService shareService;

    @Autowired
    private IBizAlbumService albumService;

    @Autowired
    private IBizPhotoService photoService;

    @Autowired
    private IBizTrackService trackService;

    @Autowired
    private IBizTrackPointService trackPointService;

    @Autowired
    private IBizTrackGpxFileService trackGpxFileService;

    /**
     * 解析分享：仅返回本次分享可见信息
     */
    @GetMapping("/{token}")
    public AjaxResult resolve(@PathVariable("token") String token) {
        BizAlbumShare share = shareService.getValidByToken(token);
        if (share == null) {
            return error("分享不存在或已失效");
        }
        BizAlbum album = albumService.getById(share.getAlbumId());
        if (album == null || album.getDeleted() == null || album.getDeleted() != AlbumDeleted.NORMAL) {
            return error("分享的相册不存在");
        }

        Map<String, Object> albumView = new HashMap<String, Object>();
        albumView.put("albumId", album.getAlbumId());
        albumView.put("albumName", album.getAlbumName());
        albumView.put("albumDesc", album.getAlbumDesc());
        albumView.put("coverUrl", album.getCoverUrl());
        albumView.put("locationSummary", album.getLocationSummary());
        albumView.put("startTime", album.getStartTime());
        albumView.put("endTime", album.getEndTime());

        long photoCount = photoService.count(shareService.buildPhotoQuery(share));
        albumView.put("photoCount", photoCount);

        Map<String, Object> shareView = new HashMap<String, Object>();
        shareView.put("shareTrack", share.getShareTrack() != null && share.getShareTrack() == 1);
        shareView.put("mediaScope", share.getMediaScope());
        shareView.put("expireTime", share.getExpireTime());

        Map<String, Object> data = new HashMap<String, Object>();
        data.put("album", albumView);
        data.put("share", shareView);
        return success(data);
    }

    /**
     * 分享内的媒体列表
     */
    @GetMapping("/{token}/photos")
    public TableDataInfo photos(@PathVariable("token") String token) {
        BizAlbumShare share = shareService.getValidByToken(token);
        if (share == null) {
            return getDataTable(java.util.Collections.<BizPhoto>emptyList());
        }
        BizAlbum album = albumService.getById(share.getAlbumId());
        if (album == null || album.getDeleted() == null || album.getDeleted() != AlbumDeleted.NORMAL) {
            return getDataTable(java.util.Collections.<BizPhoto>emptyList());
        }
        startPage();
        List<BizPhoto> list = photoService.list(shareService.buildPhotoQuery(share));
        return getDataTable(shareService.sanitizePhotos(list));
    }

    /**
     * 分享内地图点位（仅分享范围内且有坐标的媒体）
     */
    @GetMapping("/{token}/mapPoints")
    public AjaxResult mapPoints(@PathVariable("token") String token) {
        BizAlbumShare share = shareService.getValidByToken(token);
        if (share == null) {
            return error("分享不存在或已失效");
        }
        LambdaQueryWrapper<BizPhoto> wrapper = shareService.buildPhotoQuery(share);
        wrapper.isNotNull(BizPhoto::getLatitude).isNotNull(BizPhoto::getLongitude)
                .orderByAsc(BizPhoto::getShootTime);
        List<BizPhoto> list = photoService.list(wrapper);
        return success(shareService.sanitizePhotos(list));
    }

    /**
     * 分享轨迹列表（需开启分享轨迹）
     */
    @GetMapping("/{token}/tracks")
    public AjaxResult tracks(@PathVariable("token") String token) {
        BizAlbumShare share = shareService.getValidByToken(token);
        if (share == null) {
            return error("分享不存在或已失效");
        }
        if (share.getShareTrack() == null || share.getShareTrack() != 1) {
            return success(java.util.Collections.emptyList());
        }
        List<BizTrack> list = trackService.list(new LambdaQueryWrapper<BizTrack>()
                .eq(BizTrack::getAlbumId, share.getAlbumId())
                .eq(BizTrack::getEnabled, 1)
                .eq(BizTrack::getDeleted, 0)
                .orderByDesc(BizTrack::getTrackId));
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        for (BizTrack t : list) {
            Map<String, Object> m = new HashMap<String, Object>();
            m.put("trackId", t.getTrackId());
            m.put("trackName", t.getTrackName());
            m.put("trackColor", t.getTrackColor());
            m.put("totalDistance", t.getTotalDistance());
            m.put("totalDuration", t.getTotalDuration());
            m.put("pointCount", t.getPointCount());
            out.add(m);
        }
        return success(out);
    }

    /**
     * 分享轨迹点详情
     */
    @GetMapping("/{token}/tracks/{trackId}/points")
    public AjaxResult trackPoints(@PathVariable("token") String token,
                                  @PathVariable("trackId") Long trackId) {
        BizAlbumShare share = shareService.getValidByToken(token);
        if (share == null) {
            return error("分享不存在或已失效");
        }
        if (share.getShareTrack() == null || share.getShareTrack() != 1) {
            return error("此分享未开放轨迹");
        }
        BizTrack track = trackService.getById(trackId);
        if (track == null || track.getAlbumId() == null
                || !track.getAlbumId().equals(share.getAlbumId())
                || (track.getEnabled() != null && track.getEnabled() == 0)
                || (track.getDeleted() != null && track.getDeleted() == 1)) {
            return error("轨迹不存在");
        }
        List<BizTrackPoint> points = trackPointService.list(new LambdaQueryWrapper<BizTrackPoint>()
                .eq(BizTrackPoint::getTrackId, trackId)
                .orderByAsc(BizTrackPoint::getSequence));
        Map<String, Object> trackView = new HashMap<String, Object>();
        trackView.put("trackId", track.getTrackId());
        trackView.put("trackName", track.getTrackName());
        trackView.put("trackColor", track.getTrackColor());
        trackView.put("totalDistance", track.getTotalDistance());
        trackView.put("totalDuration", track.getTotalDuration());
        trackView.put("pointCount", track.getPointCount());
        trackView.put("enabled", track.getEnabled());

        Map<String, Object> data = new HashMap<String, Object>();
        data.put("track", trackView);
        data.put("points", points);
        data.put("gpxOverlays", trackGpxFileService.listOverlaysForTrack(track));
        return success(data);
    }
}
