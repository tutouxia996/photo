package com.sq.admin.album.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.sq.bus.domain.BizPhoto;
import com.sq.bus.domain.BizTrack;
import com.sq.bus.domain.BizTrackPoint;
import com.sq.bus.service.IBizPhotoService;
import com.sq.bus.service.IBizTrackGpxFileService;
import com.sq.bus.service.IBizTrackPointService;
import com.sq.bus.service.IBizTrackService;
import com.sq.bus.service.route.AmapDirectionService;
import com.sq.bus.service.route.AmapPlaceSearchService;
import com.sq.bus.service.route.TrackCustomSegmentService;
import com.sq.bus.service.route.TrackRoutePlanResult;
import com.sq.bus.service.route.TrackRouteResolveService;
import com.sq.bus.service.route.TrainRoutePlanService;
import com.sq.bus.service.route.TrainSegmentService;
import com.sq.bus.service.route.TrainStop;
import com.sq.bus.utils.GeoDistanceUtils;
import com.sq.bus.utils.TravelModeInferUtils;
import com.sq.common.annotation.Log;
import com.sq.common.core.controller.BaseController;
import com.sq.common.core.domain.AjaxResult;
import com.sq.common.core.page.TableDataInfo;
import com.sq.common.enums.BusinessType;
import com.sq.common.utils.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 轨迹管理
 */
@RestController
@RequestMapping("/album/track")
public class BizTrackController extends BaseController {

    @Autowired
    private IBizTrackService trackService;

    @Autowired
    private IBizTrackPointService trackPointService;

    @Autowired
    private IBizPhotoService photoService;

    @Autowired
    private AmapDirectionService amapDirectionService;

    @Autowired
    private TrackRouteResolveService trackRouteResolveService;

    @Autowired
    private AmapPlaceSearchService amapPlaceSearchService;

    @Autowired
    private TrackCustomSegmentService trackCustomSegmentService;

    @Autowired
    private TrainRoutePlanService trainRoutePlanService;

    @Autowired
    private TrainSegmentService trainSegmentService;

    @Autowired
    private IBizTrackGpxFileService trackGpxFileService;

    @PreAuthorize("@ss.hasPermi('album:track:list')")
    @GetMapping("/list")
    public TableDataInfo list(BizTrack query) {
        startPage();
        LambdaQueryWrapper<BizTrack> wrapper = new LambdaQueryWrapper<BizTrack>()
                .eq(query.getAlbumId() != null, BizTrack::getAlbumId, query.getAlbumId())
                .like(StringUtils.isNotEmpty(query.getTrackName()), BizTrack::getTrackName, query.getTrackName())
                .eq(query.getIsPublic() != null, BizTrack::getIsPublic, query.getIsPublic())
                .eq(BizTrack::getDeleted, 0)
                .orderByDesc(BizTrack::getTrackId);
        List<BizTrack> list = trackService.list(wrapper);
        trackGpxFileService.enrichTracks(list);
        return getDataTable(list);
    }

    /**
     * 高德地名/POI 搜索（用于自定义增补路段选点）
     */
    @PreAuthorize("@ss.hasPermi('album:track:edit')")
    @GetMapping("/place/search")
    public AjaxResult searchPlace(@RequestParam String keywords,
                                  @RequestParam(required = false) String city,
                                  @RequestParam(required = false) Integer offset) {
        return success(amapPlaceSearchService.search(keywords, city, offset));
    }

    /**
     * 自定义增补路段：搜索起终点 + 出行方式真实折线，插入两个无照片途经点
     */
    @PreAuthorize("@ss.hasPermi('album:track:edit')")
    @Log(title = "轨迹增补路段", businessType = BusinessType.INSERT)
    @PostMapping("/{trackId}/custom-segment")
    public AjaxResult addCustomSegment(@PathVariable Long trackId, @RequestBody Map<String, Object> body) {
        return success(trackCustomSegmentService.addCustomSegment(trackId, body));
    }

    /** 火车时刻表 API 是否可用（无 Key 仍可手工经停） */
    @PreAuthorize("@ss.hasPermi('album:track:edit')")
    @GetMapping("/train/status")
    public AjaxResult trainStatus() {
        return success(trainRoutePlanService.apiStatus());
    }

    /** 站到站查询班次列表（需配置 album.map.train.apiKey） */
    @PreAuthorize("@ss.hasPermi('album:track:edit')")
    @GetMapping("/train/query")
    public AjaxResult queryTrains(@RequestParam String fromStation,
                                  @RequestParam String toStation,
                                  @RequestParam String date,
                                  @RequestParam(required = false) String filter) {
        return success(trainRoutePlanService.queryTrains(fromStation, toStation, date, filter));
    }

    /** 按车次拉取经停站 */
    @PreAuthorize("@ss.hasPermi('album:track:edit')")
    @GetMapping("/train/stops")
    public AjaxResult trainStops(@RequestParam String trainNo,
                                 @RequestParam(required = false) String fromStation,
                                 @RequestParam(required = false) String toStation) {
        List<TrainStop> stops = trainRoutePlanService.lookupStops(trainNo, fromStation, toStation);
        return success(stops);
    }

    /** 车次/手工经停 → 贴轨预览（不落库） */
    @PreAuthorize("@ss.hasPermi('album:track:edit')")
    @PostMapping("/train/plan")
    public AjaxResult planTrainRoute(@RequestBody Map<String, Object> body) {
        return success(trainRoutePlanService.plan(body));
    }

    /** 按经停站插入多站途经点并贴轨 */
    @PreAuthorize("@ss.hasPermi('album:track:edit')")
    @Log(title = "轨迹车次路段", businessType = BusinessType.INSERT)
    @PostMapping("/{trackId}/train-segment")
    public AjaxResult addTrainSegment(@PathVariable Long trackId, @RequestBody Map<String, Object> body) {
        return success(trainSegmentService.addTrainSegment(trackId, body));
    }

    @PreAuthorize("@ss.hasPermi('album:track:query')")
    @GetMapping("/{trackId}")
    public AjaxResult getInfo(@PathVariable Long trackId) {
        BizTrack track = trackService.getById(trackId);
        if (track == null || track.getDeleted() != null && track.getDeleted() == 1) {
            return error("轨迹不存在或已删除");
        }
        List<BizTrackPoint> points = trackPointService.list(new LambdaQueryWrapper<BizTrackPoint>()
                .eq(BizTrackPoint::getTrackId, trackId)
                .orderByAsc(BizTrackPoint::getSequence));
        fillPointMedia(points);
        trackGpxFileService.enrichTracks(java.util.Collections.singletonList(track));
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("track", track);
        data.put("points", points);
        data.put("gpxOverlays", trackGpxFileService.listOverlaysForTrack(track));
        return success(data);
    }

    /**
     * 轨迹点关联填充媒体信息，供地图 Marker / 预览使用
     */
    private void fillPointMedia(List<BizTrackPoint> points) {
        if (points == null || points.isEmpty()) {
            return;
        }
        Set<Long> photoIds = points.stream()
                .map(BizTrackPoint::getPhotoId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (photoIds.isEmpty()) {
            return;
        }
        Map<Long, BizPhoto> photoMap = photoService.listByIds(photoIds).stream()
                .collect(Collectors.toMap(BizPhoto::getPhotoId, p -> p, (a, b) -> a));
        for (BizTrackPoint point : points) {
            BizPhoto photo = photoMap.get(point.getPhotoId());
            if (photo == null) {
                continue;
            }
            point.setFileType(photo.getFileType());
            point.setFileName(photo.getFileName());
            point.setThumbUrl(photo.getThumbUrl());
            point.setFileUrl(photo.getFileUrl());
            point.setAddress(photo.getAddress());
            point.setDuration(photo.getDuration());
        }
    }

    @PreAuthorize("@ss.hasPermi('album:track:generate')")
    @Log(title = "轨迹生成", businessType = BusinessType.INSERT)
    @PostMapping("/generate")
    public AjaxResult generate(@RequestParam Long albumId,
                               @RequestParam(required = false) String trackName,
                               @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date startTime,
                               @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date endTime) {
        BizTrack track = trackService.generateTrack(albumId, trackName, startTime, endTime);
        track.setCreateBy(getUsername());
        trackService.updateById(track);
        return success(track);
    }

    @PreAuthorize("@ss.hasPermi('album:track:edit')")
    @Log(title = "轨迹管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody BizTrack track) {
        if (track == null || track.getTrackId() == null) {
            return error("轨迹ID不能为空");
        }
        BizTrack db = trackService.getById(track.getTrackId());
        if (db == null || db.getDeleted() != null && db.getDeleted() == 1) {
            return error("轨迹不存在或已删除");
        }
        if (StringUtils.isNotEmpty(track.getTrackName())) {
            db.setTrackName(track.getTrackName().trim());
        }
        if (track.getTrackColor() != null) {
            db.setTrackColor(track.getTrackColor());
        }
        if (track.getIsPublic() != null) {
            db.setIsPublic(track.getIsPublic());
        }
        if (track.getEnabled() != null) {
            db.setEnabled(track.getEnabled() == 1 ? 1 : 0);
        }
        if (track.getGpxEnabled() != null) {
            db.setGpxEnabled(track.getGpxEnabled() == 1 ? 1 : 0);
        }
        // 允许清空行程说明
        if (track.getRemark() != null) {
            db.setRemark(track.getRemark());
        }
        db.setUpdateBy(getUsername());
        db.setUpdateTime(new Date());
        boolean ok = trackService.updateById(db);
        // 重新开启且尚无点位时，按相册 GPS 生成/同步轨迹
        if (ok && db.getEnabled() != null && db.getEnabled() == 1
                && (db.getPointCount() == null || db.getPointCount() <= 0)
                && db.getAlbumId() != null) {
            try {
                trackService.autoSyncAlbumTrack(db.getAlbumId());
            } catch (Exception ignored) {
                // 开关已保存；无 GPS 时不同步即可
            }
        }
        return toAjax(ok);
    }

    /**
     * 预览某路段按出行方式规划的真实路线（不落库）；未传方式时按坐标距离/时间推断
     */
    @PreAuthorize("@ss.hasPermi('album:track:edit')")
    @PostMapping("/route/preview")
    public AjaxResult previewRoute(@RequestBody Map<String, Object> body) {
        BigDecimal fromLat = toDecimal(body.get("fromLat"));
        BigDecimal fromLng = toDecimal(body.get("fromLng"));
        BigDecimal toLat = toDecimal(body.get("toLat"));
        BigDecimal toLng = toDecimal(body.get("toLng"));
        String travelMode = body.get("travelMode") == null ? null : String.valueOf(body.get("travelMode"));
        if (fromLat == null || fromLng == null || toLat == null || toLng == null) {
            return error("起终点坐标不能为空");
        }
        if (StringUtils.isEmpty(travelMode)) {
            double km = GeoDistanceUtils.haversineKm(
                    fromLat.doubleValue(), fromLng.doubleValue(),
                    toLat.doubleValue(), toLng.doubleValue());
            travelMode = TravelModeInferUtils.infer(km, null);
        }
        TrackRoutePlanResult planned = amapDirectionService.plan(
                fromLat.doubleValue(), fromLng.doubleValue(),
                toLat.doubleValue(), toLng.doubleValue(),
                travelMode);
        return success(amapDirectionService.toPreviewMap(planned));
    }

    /**
     * 根据照片/视频 GPS 批量贴合路网。
     * strategy=photo：短距连 GPS、中距步行、大间隔驾车，并自动修复错误的公交/高铁绕路。
     */
    @PreAuthorize("@ss.hasPermi('album:track:query')")
    @Log(title = "轨迹路网规划", businessType = BusinessType.UPDATE)
    @PostMapping("/{trackId}/resolve-routes")
    public AjaxResult resolveRoutes(@PathVariable Long trackId,
                                    @RequestParam(required = false, defaultValue = "false") boolean force,
                                    @RequestParam(required = false, defaultValue = "photo") String strategy) {
        return success(trackRouteResolveService.resolveTrackRoutes(trackId, force, strategy));
    }

    /**
     * 批量更新点位说明/出行方式；若出行方式变化则按高德规划真实路线并写入 routePath
     */
    @PreAuthorize("@ss.hasPermi('album:track:edit')")
    @Log(title = "轨迹点位", businessType = BusinessType.UPDATE)
    @PutMapping("/points")
    public AjaxResult updatePoints(@RequestBody List<BizTrackPoint> points) {
        if (points == null || points.isEmpty()) {
            return success();
        }
        Long trackId = null;
        for (BizTrackPoint item : points) {
            if (item == null || item.getPointId() == null) {
                return error("点位ID不能为空");
            }
            BizTrackPoint db = trackPointService.getById(item.getPointId());
            if (db == null) {
                return error("点位不存在：" + item.getPointId());
            }
            if (trackId == null) {
                trackId = db.getTrackId();
            } else if (!Objects.equals(trackId, db.getTrackId())) {
                return error("不能跨轨迹批量更新点位");
            }
            if (item.getDescription() != null) {
                String desc = item.getDescription().trim();
                db.setDescription(desc.isEmpty() ? null : desc);
            }
            boolean modeChanged = false;
            if (item.getTravelMode() != null) {
                String mode = item.getTravelMode().trim();
                String newMode = mode.isEmpty() ? null : mode;
                modeChanged = !Objects.equals(
                        db.getTravelMode() == null ? null : db.getTravelMode().trim().toLowerCase(),
                        newMode == null ? null : newMode.toLowerCase());
                db.setTravelMode(newMode);
            }
            if (item.getSequence() != null) {
                db.setSequence(item.getSequence());
            }
            boolean hasClientPath = item.getRoutePath() != null && !item.getRoutePath().trim().isEmpty();
            if (modeChanged && !hasClientPath) {
                // 出行方式变了且未带新折线 → 按新方式重规划
                refreshRoutePath(db);
            } else if (item.getRoutePath() != null) {
                // 前端预览/手动画线带回的折线
                String rp = item.getRoutePath().trim();
                db.setRoutePath(rp.isEmpty() ? null : rp);
            } else if (modeChanged) {
                refreshRoutePath(db);
            }
            trackPointService.updateById(db);
        }
        touchTrack(trackId);
        return success();
    }

    private void refreshRoutePath(BizTrackPoint fromPoint) {
        if (fromPoint.getTravelMode() == null || StringUtils.isEmpty(fromPoint.getTravelMode())) {
            fromPoint.setRoutePath(null);
            return;
        }
        BizTrackPoint next = findNextPoint(fromPoint);
        if (next == null || fromPoint.getLatitude() == null || fromPoint.getLongitude() == null
                || next.getLatitude() == null || next.getLongitude() == null) {
            fromPoint.setRoutePath(null);
            return;
        }
        TrackRoutePlanResult planned = amapDirectionService.plan(
                fromPoint.getLatitude().doubleValue(), fromPoint.getLongitude().doubleValue(),
                next.getLatitude().doubleValue(), next.getLongitude().doubleValue(),
                fromPoint.getTravelMode());
        fromPoint.setRoutePath(planned.hasPath() ? amapDirectionService.toRoutePathJson(planned.getPath()) : null);
    }

    private BizTrackPoint findNextPoint(BizTrackPoint fromPoint) {
        List<BizTrackPoint> list = trackPointService.list(new LambdaQueryWrapper<BizTrackPoint>()
                .eq(BizTrackPoint::getTrackId, fromPoint.getTrackId())
                .orderByAsc(BizTrackPoint::getSequence)
                .orderByAsc(BizTrackPoint::getPointId));
        for (int i = 0; i < list.size(); i++) {
            if (Objects.equals(list.get(i).getPointId(), fromPoint.getPointId()) && i + 1 < list.size()) {
                return list.get(i + 1);
            }
        }
        return null;
    }

    private BigDecimal toDecimal(Object val) {
        if (val == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(val));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从轨迹中移除点位（不删除关联照片）；删除后重连前后点折线
     */
    @PreAuthorize("@ss.hasPermi('album:track:edit')")
    @Log(title = "轨迹点位", businessType = BusinessType.DELETE)
    @DeleteMapping("/point/{pointId}")
    public AjaxResult removePoint(@PathVariable Long pointId) {
        BizTrackPoint point = trackPointService.getById(pointId);
        if (point == null) {
            return error("点位不存在");
        }
        Long trackId = point.getTrackId();
        List<BizTrackPoint> before = trackPointService.list(new LambdaQueryWrapper<BizTrackPoint>()
                .eq(BizTrackPoint::getTrackId, trackId)
                .orderByAsc(BizTrackPoint::getSequence)
                .orderByAsc(BizTrackPoint::getPointId));
        int idx = -1;
        for (int i = 0; i < before.size(); i++) {
            if (Objects.equals(before.get(i).getPointId(), pointId)) {
                idx = i;
                break;
            }
        }
        Long prevId = idx > 0 ? before.get(idx - 1).getPointId() : null;

        trackPointService.removeById(pointId);
        List<BizTrackPoint> remain = trackPointService.list(new LambdaQueryWrapper<BizTrackPoint>()
                .eq(BizTrackPoint::getTrackId, trackId)
                .orderByAsc(BizTrackPoint::getSequence)
                .orderByAsc(BizTrackPoint::getPointId));
        int seq = 1;
        for (BizTrackPoint p : remain) {
            if (!Objects.equals(p.getSequence(), seq)) {
                p.setSequence(seq);
                trackPointService.updateById(p);
            }
            seq++;
        }
        // 删除后把前一点接到新的下一点
        if (prevId != null) {
            BizTrackPoint prev = trackPointService.getById(prevId);
            if (prev != null) {
                refreshRoutePath(prev);
                trackPointService.updateById(prev);
            }
        }
        BizTrack track = trackService.getById(trackId);
        if (track != null && (track.getDeleted() == null || track.getDeleted() == 0)) {
            track.setPointCount(remain.size());
            track.setUpdateBy(getUsername());
            track.setUpdateTime(new Date());
            trackService.updateById(track);
        }
        return success();
    }

    private void touchTrack(Long trackId) {
        if (trackId == null) {
            return;
        }
        trackService.update(new LambdaUpdateWrapper<BizTrack>()
                .eq(BizTrack::getTrackId, trackId)
                .eq(BizTrack::getDeleted, 0)
                .set(BizTrack::getUpdateBy, getUsername())
                .set(BizTrack::getUpdateTime, new Date()));
    }

    @PreAuthorize("@ss.hasPermi('album:track:remove')")
    @Log(title = "轨迹管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{trackIds}")
    public AjaxResult remove(@PathVariable Long[] trackIds) {
        return toAjax(trackService.update(new LambdaUpdateWrapper<BizTrack>()
                .in(BizTrack::getTrackId, Arrays.asList(trackIds))
                .eq(BizTrack::getDeleted, 0)
                .set(BizTrack::getDeleted, 1)
                .set(BizTrack::getUpdateBy, getUsername())
                .set(BizTrack::getUpdateTime, new Date())));
    }

    /** 相册下 GPX 文件列表 */
    @PreAuthorize("@ss.hasPermi('album:track:list')")
    @GetMapping("/gpx/list")
    public AjaxResult gpxList(@RequestParam Long albumId) {
        return success(trackGpxFileService.listByAlbum(albumId));
    }

    /** 导入 GPX（可多文件），作为叠层显示在既有轨迹地图上，不新建轨迹 */
    @PreAuthorize("@ss.hasPermi('album:track:generate')")
    @Log(title = "轨迹GPX导入", businessType = BusinessType.INSERT)
    @PostMapping("/gpx/import")
    public AjaxResult importGpx(@RequestParam Long albumId,
                                @RequestParam("files") MultipartFile[] files) {
        return success(trackGpxFileService.importGpxFiles(albumId, files, getUsername()));
    }

    /** 启用/停用单个 GPX（影响地图叠层） */
    @PreAuthorize("@ss.hasPermi('album:track:edit')")
    @Log(title = "轨迹GPX启用", businessType = BusinessType.UPDATE)
    @PutMapping("/gpx/{gpxId}/enabled")
    public AjaxResult gpxEnabled(@PathVariable Long gpxId, @RequestParam boolean enabled) {
        return toAjax(trackGpxFileService.setEnabled(gpxId, enabled, getUsername()));
    }

    /** 设置 GPX 出行方式（影响线路颜色） */
    @PreAuthorize("@ss.hasPermi('album:track:edit')")
    @Log(title = "轨迹GPX出行方式", businessType = BusinessType.UPDATE)
    @PutMapping("/gpx/{gpxId}/travel-mode")
    public AjaxResult gpxTravelMode(@PathVariable Long gpxId, @RequestParam String travelMode) {
        return toAjax(trackGpxFileService.setTravelMode(gpxId, travelMode, getUsername()));
    }

    /** 删除 GPX（影响地图叠层） */
    @PreAuthorize("@ss.hasPermi('album:track:remove')")
    @Log(title = "轨迹GPX删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/gpx/{gpxId}")
    public AjaxResult removeGpx(@PathVariable Long gpxId) {
        return toAjax(trackGpxFileService.removeGpx(gpxId, getUsername()));
    }
}
