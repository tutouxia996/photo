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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        // 有效打开分享页计一次访问（异步失败不影响展示）
        try {
            shareService.increaseViewCount(share.getShareId());
        } catch (Exception ignored) {
            /* ignore */
        }
        List<Long> albumIds = shareService.resolveAlbumIds(share);
        List<BizAlbum> albums = loadNormalAlbums(albumIds);
        if (albums.isEmpty()) {
            return error("分享的相册不存在");
        }

        long photoCount = 0L;
        List<Map<String, Object>> albumViews = new ArrayList<Map<String, Object>>();
        for (BizAlbum album : albums) {
            long cnt = photoService.count(shareService.buildPhotoQuery(share, album.getAlbumId()));
            photoCount += cnt;
            albumViews.add(toAlbumView(album, cnt));
        }
        // 主相册兼容字段：photoCount 为整次分享合计
        Map<String, Object> primary = toAlbumView(albums.get(0), photoCount);
        if (albums.size() > 1) {
            primary.put("albumName", buildMultiTitle(albums));
        }

        Map<String, Object> shareView = new HashMap<String, Object>();
        shareView.put("shareTrack", share.getShareTrack() != null && share.getShareTrack() == 1);
        shareView.put("mediaScope", share.getMediaScope());
        shareView.put("expireTime", share.getExpireTime());
        shareView.put("albumCount", albums.size());

        Map<String, Object> data = new HashMap<String, Object>();
        data.put("album", primary);
        data.put("albums", albumViews);
        data.put("share", shareView);
        return success(data);
    }

    /**
     * 分享内的媒体列表（可选 albumId 仅看某一相册）
     */
    @GetMapping("/{token}/photos")
    public TableDataInfo photos(@PathVariable("token") String token,
                                @RequestParam(value = "albumId", required = false) Long albumId) {
        BizAlbumShare share = shareService.getValidByToken(token);
        if (share == null) {
            return getDataTable(java.util.Collections.<BizPhoto>emptyList());
        }
        List<BizAlbum> albums = loadNormalAlbums(shareService.resolveAlbumIds(share));
        if (albums.isEmpty()) {
            return getDataTable(java.util.Collections.<BizPhoto>emptyList());
        }
        Long filter = resolveAlbumFilter(share, albumId);
        startPage();
        List<BizPhoto> list = photoService.list(shareService.buildPhotoQuery(share, filter));
        return getDataTable(shareService.sanitizePhotos(list));
    }

    /**
     * 分享内地图点位（仅分享范围内且有坐标的媒体；可选 albumId）
     */
    @GetMapping("/{token}/mapPoints")
    public AjaxResult mapPoints(@PathVariable("token") String token,
                                @RequestParam(value = "albumId", required = false) Long albumId) {
        BizAlbumShare share = shareService.getValidByToken(token);
        if (share == null) {
            return error("分享不存在或已失效");
        }
        Long filter = resolveAlbumFilter(share, albumId);
        LambdaQueryWrapper<BizPhoto> wrapper = shareService.buildPhotoQuery(share, filter);
        wrapper.isNotNull(BizPhoto::getLatitude).isNotNull(BizPhoto::getLongitude)
                .orderByAsc(BizPhoto::getShootTime);
        List<BizPhoto> list = photoService.list(wrapper);
        return success(shareService.sanitizePhotos(list));
    }

    /**
     * 分享轨迹列表（需开启分享轨迹；可选 albumId）
     */
    @GetMapping("/{token}/tracks")
    public AjaxResult tracks(@PathVariable("token") String token,
                             @RequestParam(value = "albumId", required = false) Long albumId) {
        BizAlbumShare share = shareService.getValidByToken(token);
        if (share == null) {
            return error("分享不存在或已失效");
        }
        if (share.getShareTrack() == null || share.getShareTrack() != 1) {
            return success(java.util.Collections.emptyList());
        }
        List<Long> albumIds = shareService.resolveAlbumIds(share);
        Long filter = resolveAlbumFilter(share, albumId);
        if (filter != null) {
            albumIds = java.util.Collections.singletonList(filter);
        }
        if (albumIds.isEmpty()) {
            return success(java.util.Collections.emptyList());
        }
        LambdaQueryWrapper<BizTrack> tw = new LambdaQueryWrapper<BizTrack>()
                .eq(BizTrack::getEnabled, 1)
                .eq(BizTrack::getDeleted, 0)
                .orderByDesc(BizTrack::getTrackId);
        if (albumIds.size() == 1) {
            tw.eq(BizTrack::getAlbumId, albumIds.get(0));
        } else {
            tw.in(BizTrack::getAlbumId, albumIds);
        }
        List<BizTrack> list = trackService.list(tw);
        trackGpxFileService.enrichTracks(list);
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        for (BizTrack t : list) {
            Map<String, Object> m = new HashMap<String, Object>();
            m.put("trackId", t.getTrackId());
            m.put("albumId", t.getAlbumId());
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
        Set<Long> albumIdSet = new HashSet<Long>(shareService.resolveAlbumIds(share));
        BizTrack track = trackService.getById(trackId);
        if (track == null || track.getAlbumId() == null
                || !albumIdSet.contains(track.getAlbumId())
                || (track.getEnabled() != null && track.getEnabled() == 0)
                || (track.getDeleted() != null && track.getDeleted() == 1)) {
            return error("轨迹不存在");
        }
        List<BizTrackPoint> points = trackPointService.list(new LambdaQueryWrapper<BizTrackPoint>()
                .eq(BizTrackPoint::getTrackId, trackId)
                .orderByAsc(BizTrackPoint::getSequence));
        trackGpxFileService.enrichTracks(java.util.Collections.singletonList(track));
        Map<String, Object> trackView = new HashMap<String, Object>();
        trackView.put("trackId", track.getTrackId());
        trackView.put("albumId", track.getAlbumId());
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

    /** 校验 albumId 属于本次分享；非法则返回 null（查全部） */
    private Long resolveAlbumFilter(BizAlbumShare share, Long albumId) {
        if (albumId == null) {
            return null;
        }
        List<Long> ids = shareService.resolveAlbumIds(share);
        return ids.contains(albumId) ? albumId : null;
    }

    private List<BizAlbum> loadNormalAlbums(List<Long> albumIds) {
        List<BizAlbum> out = new ArrayList<BizAlbum>();
        if (albumIds == null || albumIds.isEmpty()) {
            return out;
        }
        List<BizAlbum> rows = albumService.listByIds(albumIds);
        Map<Long, BizAlbum> map = new HashMap<Long, BizAlbum>();
        if (rows != null) {
            for (BizAlbum a : rows) {
                if (a != null && a.getAlbumId() != null
                        && a.getDeleted() != null && a.getDeleted() == AlbumDeleted.NORMAL) {
                    map.put(a.getAlbumId(), a);
                }
            }
        }
        for (Long id : albumIds) {
            BizAlbum a = map.get(id);
            if (a != null) {
                out.add(a);
            }
        }
        return out;
    }

    private static Map<String, Object> toAlbumView(BizAlbum album, Long photoCount) {
        Map<String, Object> albumView = new HashMap<String, Object>();
        albumView.put("albumId", album.getAlbumId());
        albumView.put("albumName", album.getAlbumName());
        albumView.put("albumDesc", album.getAlbumDesc());
        albumView.put("coverUrl", album.getCoverUrl());
        albumView.put("locationSummary", album.getLocationSummary());
        albumView.put("startTime", album.getStartTime());
        albumView.put("endTime", album.getEndTime());
        if (photoCount != null) {
            albumView.put("photoCount", photoCount);
        } else {
            albumView.put("photoCount", album.getPhotoCount());
        }
        return albumView;
    }

    private static String buildMultiTitle(List<BizAlbum> albums) {
        if (albums == null || albums.isEmpty()) {
            return "分享相册";
        }
        if (albums.size() == 1) {
            String n = albums.get(0).getAlbumName();
            return n != null ? n : "分享相册";
        }
        String first = albums.get(0).getAlbumName();
        if (first == null || first.isEmpty()) {
            first = "相册";
        }
        return first + " 等" + albums.size() + "个相册";
    }
}
