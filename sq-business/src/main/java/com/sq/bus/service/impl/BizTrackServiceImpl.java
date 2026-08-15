package com.sq.bus.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sq.bus.config.AlbumProperties;
import com.sq.bus.constants.PhotoLocationSource;
import com.sq.bus.domain.BizAlbum;
import com.sq.bus.domain.BizPhoto;
import com.sq.bus.domain.BizTrack;
import com.sq.bus.domain.BizTrackPoint;
import com.sq.bus.mapper.BizTrackMapper;
import com.sq.bus.service.IBizAlbumService;
import com.sq.bus.service.IBizPhotoService;
import com.sq.bus.service.IBizTrackGpxFileService;
import com.sq.bus.service.IBizTrackPointService;
import com.sq.bus.service.IBizTrackService;
import com.sq.bus.utils.GeoDistanceUtils;
import com.sq.common.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class BizTrackServiceImpl extends ServiceImpl<BizTrackMapper, BizTrack> implements IBizTrackService {

    private static final Logger log = LoggerFactory.getLogger(BizTrackServiceImpl.class);

    /** 相邻点超过该距离（公里）视为异常跳变，分段重新起算 */
    private static final double JUMP_THRESHOLD_KM = 50.0;

    /** 并发上传时按相册串行同步轨迹，避免重复新建 */
    private final ConcurrentHashMap<Long, Object> albumSyncLocks = new ConcurrentHashMap<Long, Object>();

    @Autowired
    private IBizPhotoService photoService;

    @Autowired
    private IBizTrackPointService trackPointService;

    @Autowired
    private IBizAlbumService albumService;

    @Lazy
    @Autowired
    private IBizTrackGpxFileService trackGpxFileService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private AlbumProperties albumProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BizTrack generateTrack(Long albumId, String trackName, Date startTime, Date endTime, boolean includeEstimated) {
        if (albumId == null) {
            throw new ServiceException("相册ID不能为空");
        }
        // 正式生成只认权威坐标；未在照片地图确认前，禁止用区域估计冒充正式轨迹
        List<BizPhoto> photos = listGpsPhotos(albumId, startTime, endTime, false);
        photos = excludeGpxMatchedPhotos(albumId, photos);
        if (photos.isEmpty()) {
            long fallback = countFallbackGpsPhotos(albumId);
            if (fallback > 0 || includeEstimated) {
                throw new ServiceException("请先在照片地图将估计点「确认上主轨迹」后再生成正式轨迹（仅区域粗定位不能生成）");
            }
            throw new ServiceException("所选范围内没有可生成照片轨的地理位置媒体（已匹配 GPX 的已排除）。无 GPS 相册请先「区域定位」并在照片地图确认定位");
        }
        // 已有照片轨则刷新，避免同相册重复多条
        BizTrack existing = findAlbumPhotoTrack(albumId);
        if (existing != null) {
            BizTrack rebuilt = rebuildExistingTrack(existing, photos);
            clearRegionDraftRemark(rebuilt);
            return rebuilt;
        }
        TrackBuildResult built = buildTrackPoints(photos);
        BizTrack track = createTrack(albumId, trackName, built);
        clearRegionDraftRemark(track);
        return track;
    }

    private void clearRegionDraftRemark(BizTrack track) {
        if (track == null) {
            return;
        }
        String remark = track.getRemark();
        if (remark != null && remark.contains("区域粗定位草稿")) {
            track.setRemark(null);
            // 正式生成后默认启用，可在列表再手动关闭
            track.setEnabled(1);
            track.setUpdateTime(new Date());
            updateById(track);
        }
    }

    private void markAsRegionDraft(BizTrack track) {
        if (track == null) {
            return;
        }
        track.setRemark("区域粗定位草稿：请在照片地图确认定位后再生成正式轨迹");
        track.setEnabled(0);
        track.setUpdateTime(new Date());
        updateById(track);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BizTrack ensureDraftTrackFromEstimated(Long albumId) {
        if (albumId == null) {
            return null;
        }
        List<BizPhoto> photos = listGpsPhotos(albumId, null, null, true);
        photos = excludeGpxMatchedPhotos(albumId, photos);
        if (photos.isEmpty()) {
            return null;
        }
        BizTrack existing = findAlbumPhotoTrack(albumId);
        if (existing != null) {
            // 关闭启用仍更新点位，方便下次打开照片地图/列表一致
            BizTrack rebuilt = rebuildExistingTrack(existing, photos);
            markAsRegionDraft(rebuilt);
            return rebuilt;
        }
        TrackBuildResult built = buildTrackPoints(photos);
        BizTrack track = createTrack(albumId, null, built);
        markAsRegionDraft(track);
        log.info("区域定位后写入轨迹草稿 albumId={} trackId={} points={}", albumId, track.getTrackId(), built.points.size());
        return track;
    }

    private BizTrack findAlbumPhotoTrack(Long albumId) {
        return getOne(new LambdaQueryWrapper<BizTrack>()
                .eq(BizTrack::getAlbumId, albumId)
                .eq(BizTrack::getDeleted, 0)
                .and(w -> w.isNull(BizTrack::getSourceType)
                        .or().eq(BizTrack::getSourceType, "")
                        .or().eq(BizTrack::getSourceType, "photo"))
                .orderByAsc(BizTrack::getTrackId)
                .last("LIMIT 1"), false);
    }

    @Override
    public BizTrack autoSyncAlbumTrack(Long albumId) {
        if (albumId == null) {
            return null;
        }
        Object lock = albumSyncLocks.computeIfAbsent(albumId, id -> new Object());
        synchronized (lock) {
            TransactionTemplate template = new TransactionTemplate(transactionManager);
            return template.execute(status -> doAutoSyncAlbumTrack(albumId));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeTracks(Long[] trackIds, String username) {
        if (trackIds == null || trackIds.length == 0) {
            return 0;
        }
        List<Long> ids = Arrays.stream(trackIds).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (ids.isEmpty()) {
            return 0;
        }
        List<BizTrack> tracks = list(new LambdaQueryWrapper<BizTrack>()
                .in(BizTrack::getTrackId, ids)
                .eq(BizTrack::getDeleted, 0));
        if (tracks.isEmpty()) {
            return 0;
        }
        Set<Long> albumIds = tracks.stream()
                .map(BizTrack::getAlbumId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        Date now = new Date();
        boolean updated = update(new LambdaUpdateWrapper<BizTrack>()
                .in(BizTrack::getTrackId, tracks.stream().map(BizTrack::getTrackId).collect(Collectors.toList()))
                .eq(BizTrack::getDeleted, 0)
                .set(BizTrack::getDeleted, 1)
                .set(BizTrack::getUpdateBy, username)
                .set(BizTrack::getUpdateTime, now));
        if (!updated) {
            return 0;
        }
        // GPX 挂在相册上，与 track_id 无关；相册已无轨迹时必须清掉，否则重新生成仍会叠历史 GPX
        for (Long albumId : albumIds) {
            long remaining = count(new LambdaQueryWrapper<BizTrack>()
                    .eq(BizTrack::getAlbumId, albumId)
                    .eq(BizTrack::getDeleted, 0));
            if (remaining == 0) {
                trackGpxFileService.removeByAlbum(albumId, username);
            }
        }
        return tracks.size();
    }

    /**
     * 仅维护照片/自定义主轨迹（sourceType=photo），不触碰 GPX 轨迹。
     * 已与启用 GPX 时间匹配的媒体不写入照片轨，避免贴合路网重复画线。
     */
    private BizTrack doAutoSyncAlbumTrack(Long albumId) {
        List<BizPhoto> photos = listGpsPhotos(albumId, null, null, false);
        photos = excludeGpxMatchedPhotos(albumId, photos);

        BizTrack existing = findAlbumPhotoTrack(albumId);

        if (photos.isEmpty()) {
            if (existing == null) {
                return null;
            }
            // 关闭启用：保留已有点位，不再自动刷新
            if (existing.getEnabled() != null && existing.getEnabled() == 0) {
                return existing;
            }
            // 相册仅有区域/时间等估计坐标（无权威 GPS）时：保留用户用「包含估计坐标」生成的轨迹，切勿清空
            if (countFallbackGpsPhotos(albumId) > 0) {
                log.debug("相册仅有估计坐标，跳过自动同步清空 albumId={} trackId={}",
                        albumId, existing.getTrackId());
                return existing;
            }
            // 全部权威 GPS 媒体都已挂在 GPX 上：清空照片点，保留手工途经点
            return rebuildExistingTrack(existing, new ArrayList<BizPhoto>());
        }

        if (existing == null) {
            TrackBuildResult built = buildTrackPoints(photos);
            BizTrack track = createTrack(albumId, null, built);
            log.info("自动生成相册轨迹 albumId={} trackId={} points={}", albumId, track.getTrackId(), built.points.size());
            return track;
        }
        // 关闭启用：保留已有点位，不再自动刷新生成
        if (existing.getEnabled() != null && existing.getEnabled() == 0) {
            log.debug("相册轨迹已关闭启用，跳过自动同步 albumId={} trackId={}", albumId, existing.getTrackId());
            return existing;
        }

        return rebuildExistingTrack(existing, photos);
    }

    /** 统计相册内带坐标且为兜底来源的媒体数（region_center / time_interp / ai_landmark） */
    private long countFallbackGpsPhotos(Long albumId) {
        List<BizPhoto> list = photoService.list(new LambdaQueryWrapper<BizPhoto>()
                .eq(BizPhoto::getAlbumId, albumId)
                .eq(BizPhoto::getDeleted, com.sq.bus.constants.AlbumDeleted.NORMAL)
                .isNotNull(BizPhoto::getLatitude)
                .isNotNull(BizPhoto::getLongitude));
        if (list == null || list.isEmpty()) {
            return 0L;
        }
        long n = 0L;
        for (BizPhoto photo : list) {
            if (PhotoLocationSource.isFallback(photo.getLocationSource())) {
                n++;
            }
        }
        return n;
    }

    private BizTrack rebuildExistingTrack(BizTrack existing, List<BizPhoto> photos) {
        List<BizTrackPoint> oldPoints = trackPointService.list(new LambdaQueryWrapper<BizTrackPoint>()
                .eq(BizTrackPoint::getTrackId, existing.getTrackId())
                .orderByAsc(BizTrackPoint::getSequence)
                .orderByAsc(BizTrackPoint::getPointId));

        // 照片点序未变时绝不能重建：否则会清空用户刚保存的 travelMode/routePath
        if (samePhotoSequence(oldPoints, photos)) {
            log.debug("照片轨点序未变，跳过重建 albumId={} trackId={} points={}",
                    existing.getAlbumId(), existing.getTrackId(), oldPoints.size());
            return existing;
        }

        TrackBuildResult built = photos.isEmpty()
                ? new TrackBuildResult(new ArrayList<BizTrackPoint>(), 0D, 0L, null, null)
                : buildTrackPoints(photos);

        // 保留无照片人工途经点，按原顺序锚到前一个照片点之后
        List<KeptWaypoint> keptWaypoints = captureWaypoints(oldPoints);
        Map<Long, KeptEdge> keptEdges = capturePhotoEdges(oldPoints);

        trackPointService.remove(new LambdaQueryWrapper<BizTrackPoint>()
                .eq(BizTrackPoint::getTrackId, existing.getTrackId()));

        List<BizTrackPoint> merged = mergePhotoAndWaypoints(built.points, keptWaypoints);
        restorePhotoEdges(merged, keptEdges);
        int seq = 1;
        for (BizTrackPoint point : merged) {
            point.setTrackId(existing.getTrackId());
            point.setPointId(null);
            point.setSequence(seq++);
        }

        // 有途经点时按合并结果重算起止；里程仍以照片点为主（途经点不改总里程算法）
        Date startTime = built.startTime;
        Date endTime = built.endTime;
        long durationSec = built.durationSec;
        if (!merged.isEmpty()) {
            BizTrackPoint first = merged.get(0);
            BizTrackPoint last = merged.get(merged.size() - 1);
            if (first.getPointTime() != null) {
                startTime = first.getPointTime();
            }
            if (last.getPointTime() != null) {
                endTime = last.getPointTime();
            }
            if (startTime != null && endTime != null) {
                durationSec = Math.max(0L, (endTime.getTime() - startTime.getTime()) / 1000L);
            }
        }

        TrackBuildResult mergedBuilt = new TrackBuildResult(
                merged,
                built.totalDistance,
                durationSec,
                startTime,
                endTime);
        applyBuilt(existing, mergedBuilt);
        existing.setSourceType("photo");
        existing.setUpdateTime(new Date());
        updateById(existing);
        if (!merged.isEmpty()) {
            trackPointService.saveBatch(merged);
        }
        log.info("自动刷新相册轨迹 albumId={} trackId={} photoPoints={} waypoints={}",
                existing.getAlbumId(), existing.getTrackId(), built.points.size(), keptWaypoints.size());
        return existing;
    }

    /** 照片点 photoId 顺序是否与当前 GPS 媒体列表一致（忽略途经点） */
    private boolean samePhotoSequence(List<BizTrackPoint> oldPoints, List<BizPhoto> photos) {
        List<Long> oldIds = new ArrayList<Long>();
        if (oldPoints != null) {
            for (BizTrackPoint p : oldPoints) {
                if (p != null && p.getPhotoId() != null) {
                    oldIds.add(p.getPhotoId());
                }
            }
        }
        List<Long> newIds = new ArrayList<Long>();
        if (photos != null) {
            for (BizPhoto p : photos) {
                if (p != null && p.getPhotoId() != null) {
                    newIds.add(p.getPhotoId());
                }
            }
        }
        return oldIds.equals(newIds);
    }

    /**
     * 记录照片点到下一点的用户编辑（出行方式/折线/说明），重建后按「同 photoId + 同下一站」恢复。
     */
    private Map<Long, KeptEdge> capturePhotoEdges(List<BizTrackPoint> oldPoints) {
        Map<Long, KeptEdge> map = new HashMap<Long, KeptEdge>();
        if (oldPoints == null || oldPoints.isEmpty()) {
            return map;
        }
        for (int i = 0; i < oldPoints.size(); i++) {
            BizTrackPoint from = oldPoints.get(i);
            if (from == null || from.getPhotoId() == null) {
                continue;
            }
            Long nextPhotoId = null;
            boolean hasNext = i + 1 < oldPoints.size();
            if (hasNext) {
                BizTrackPoint to = oldPoints.get(i + 1);
                nextPhotoId = to == null ? null : to.getPhotoId();
            }
            map.put(from.getPhotoId(), new KeptEdge(
                    hasNext,
                    nextPhotoId,
                    from.getTravelMode(),
                    from.getRoutePath(),
                    from.getDescription()));
        }
        return map;
    }

    private void restorePhotoEdges(List<BizTrackPoint> merged, Map<Long, KeptEdge> edges) {
        if (merged == null || merged.isEmpty() || edges == null || edges.isEmpty()) {
            return;
        }
        for (int i = 0; i < merged.size(); i++) {
            BizTrackPoint from = merged.get(i);
            if (from == null || from.getPhotoId() == null) {
                continue;
            }
            KeptEdge edge = edges.get(from.getPhotoId());
            if (edge == null) {
                continue;
            }
            if (edge.description != null && !edge.description.isEmpty()) {
                from.setDescription(edge.description);
            }
            boolean hasNext = i + 1 < merged.size();
            if (!hasNext || !edge.hasNext) {
                continue;
            }
            Long nextPhotoId = merged.get(i + 1).getPhotoId();
            // 下一站仍是同一照片（或仍是途经点）时，折线才有效
            if (Objects.equals(edge.nextPhotoId, nextPhotoId)) {
                from.setTravelMode(edge.travelMode);
                from.setRoutePath(edge.routePath);
            } else if (edge.travelMode != null && !edge.travelMode.isEmpty()) {
                // 邻居变了：保留出行方式偏好，清空旧折线以免画到错误终点
                from.setTravelMode(edge.travelMode);
                from.setRoutePath(null);
            }
        }
    }

    private List<BizPhoto> excludeGpxMatchedPhotos(Long albumId, List<BizPhoto> photos) {
        if (photos == null || photos.isEmpty() || albumId == null) {
            return photos == null ? new ArrayList<BizPhoto>() : photos;
        }
        AlbumProperties.GpxConfig cfg = albumProperties.getGpx();
        if (cfg != null && !cfg.isExcludeMatchedFromPhotoTrack()) {
            return photos;
        }
        Set<Long> matchedIds;
        try {
            matchedIds = trackGpxFileService.listMatchedPhotoIds(albumId);
        } catch (Exception e) {
            log.warn("查询 GPX 匹配媒体失败，照片轨暂不去重 albumId={}", albumId, e);
            return photos;
        }
        if (matchedIds == null || matchedIds.isEmpty()) {
            return photos;
        }
        List<BizPhoto> kept = new ArrayList<BizPhoto>();
        for (BizPhoto photo : photos) {
            if (photo == null || photo.getPhotoId() == null) {
                continue;
            }
            if (matchedIds.contains(photo.getPhotoId())) {
                continue;
            }
            kept.add(photo);
        }
        if (kept.size() != photos.size()) {
            log.info("照片轨排除已匹配 GPX 的媒体 albumId={} before={} after={}",
                    albumId, photos.size(), kept.size());
        }
        return kept;
    }

    /**
     * 记录人工点及其锚点：插在 afterPhotoId 对应照片之后；afterPhotoId=null 表示轨迹最前。
     */
    private List<KeptWaypoint> captureWaypoints(List<BizTrackPoint> oldPoints) {
        List<KeptWaypoint> kept = new ArrayList<KeptWaypoint>();
        if (oldPoints == null || oldPoints.isEmpty()) {
            return kept;
        }
        Long lastPhotoId = null;
        for (BizTrackPoint p : oldPoints) {
            if (p.getPhotoId() != null) {
                lastPhotoId = p.getPhotoId();
                continue;
            }
            BizTrackPoint copy = new BizTrackPoint();
            copy.setLatitude(p.getLatitude());
            copy.setLongitude(p.getLongitude());
            copy.setPointTime(p.getPointTime());
            copy.setAltitude(p.getAltitude());
            copy.setDescription(p.getDescription());
            copy.setTravelMode(p.getTravelMode());
            copy.setRoutePath(p.getRoutePath());
            kept.add(new KeptWaypoint(copy, lastPhotoId));
        }
        return kept;
    }

    private List<BizTrackPoint> mergePhotoAndWaypoints(List<BizTrackPoint> photoPoints, List<KeptWaypoint> waypoints) {
        List<BizTrackPoint> result = new ArrayList<BizTrackPoint>();
        if (waypoints == null) {
            waypoints = new ArrayList<KeptWaypoint>();
        }
        for (KeptWaypoint wp : waypoints) {
            if (wp.afterPhotoId == null) {
                result.add(wp.point);
            }
        }
        for (BizTrackPoint photoPoint : photoPoints) {
            result.add(photoPoint);
            Long pid = photoPoint.getPhotoId();
            for (KeptWaypoint wp : waypoints) {
                if (wp.afterPhotoId != null && wp.afterPhotoId.equals(pid)) {
                    result.add(wp.point);
                }
            }
        }
        // 锚点照片已删除的人工点：追加到末尾，避免丢失
        for (KeptWaypoint wp : waypoints) {
            if (wp.afterPhotoId == null) {
                continue;
            }
            boolean anchored = false;
            for (BizTrackPoint photoPoint : photoPoints) {
                if (wp.afterPhotoId.equals(photoPoint.getPhotoId())) {
                    anchored = true;
                    break;
                }
            }
            if (!anchored) {
                result.add(wp.point);
            }
        }
        return result;
    }

    private static final class KeptWaypoint {
        private final BizTrackPoint point;
        private final Long afterPhotoId;

        private KeptWaypoint(BizTrackPoint point, Long afterPhotoId) {
            this.point = point;
            this.afterPhotoId = afterPhotoId;
        }
    }

    /** 照片点上的用户路段编辑，按 photoId 在重建后恢复 */
    private static final class KeptEdge {
        private final boolean hasNext;
        private final Long nextPhotoId;
        private final String travelMode;
        private final String routePath;
        private final String description;

        private KeptEdge(boolean hasNext, Long nextPhotoId, String travelMode, String routePath, String description) {
            this.hasNext = hasNext;
            this.nextPhotoId = nextPhotoId;
            this.travelMode = travelMode;
            this.routePath = routePath;
            this.description = description;
        }
    }

    private List<BizPhoto> listGpsPhotos(Long albumId, Date startTime, Date endTime, boolean includeEstimated) {
        LambdaQueryWrapper<BizPhoto> query = new LambdaQueryWrapper<BizPhoto>()
                .eq(BizPhoto::getAlbumId, albumId)
                .eq(BizPhoto::getDeleted, com.sq.bus.constants.AlbumDeleted.NORMAL)
                .isNotNull(BizPhoto::getLatitude)
                .isNotNull(BizPhoto::getLongitude)
                .orderByAsc(BizPhoto::getShootTime)
                .orderByAsc(BizPhoto::getPhotoId);
        if (startTime != null) {
            query.ge(BizPhoto::getShootTime, startTime);
        }
        if (endTime != null) {
            query.le(BizPhoto::getShootTime, endTime);
        }
        List<BizPhoto> photos = photoService.list(query);
        if (photos == null || photos.isEmpty()) {
            return new ArrayList<BizPhoto>();
        }
        if (includeEstimated) {
            return photos;
        }
        AlbumProperties.FallbackLocationConfig cfg = albumProperties.getFallbackLocation();
        if (cfg != null && cfg.isIncludeInTrack()) {
            return photos;
        }
        List<BizPhoto> authoritative = new ArrayList<BizPhoto>();
        for (BizPhoto photo : photos) {
            if (PhotoLocationSource.isAuthoritativeGps(photo)) {
                authoritative.add(photo);
            }
        }
        return authoritative;
    }

    private BizTrack createTrack(Long albumId, String trackName, TrackBuildResult built) {
        BizTrack track = new BizTrack();
        track.setAlbumId(albumId);
        track.setTrackName(resolveTrackName(albumId, trackName));
        applyBuilt(track, built);
        track.setTrackColor("#3B82F6");
        track.setIsPublic(1);
        track.setEnabled(1);
        track.setSourceType("photo");
        track.setDeleted(0);
        track.setCreateTime(new Date());
        save(track);

        for (BizTrackPoint point : built.points) {
            point.setTrackId(track.getTrackId());
        }
        trackPointService.saveBatch(built.points);
        return track;
    }

    private void applyBuilt(BizTrack track, TrackBuildResult built) {
        track.setStartTime(built.startTime);
        track.setEndTime(built.endTime);
        track.setTotalDistance(BigDecimal.valueOf(built.totalDistance).setScale(2, BigDecimal.ROUND_HALF_UP));
        track.setTotalDuration(built.durationSec);
        track.setPointCount(built.points.size());
    }

    private TrackBuildResult buildTrackPoints(List<BizPhoto> photos) {
        List<BizTrackPoint> points = new ArrayList<BizTrackPoint>();
        double totalDistance = 0D;
        BizPhoto prev = null;
        int sequence = 1;
        for (BizPhoto photo : photos) {
            if (prev != null) {
                double distance = GeoDistanceUtils.haversineKm(
                        prev.getLatitude().doubleValue(), prev.getLongitude().doubleValue(),
                        photo.getLatitude().doubleValue(), photo.getLongitude().doubleValue());
                if (distance > JUMP_THRESHOLD_KM) {
                    // 异常跳变：不累计距离，仅作为新点继续
                    prev = photo;
                } else {
                    totalDistance += distance;
                }
            }
            BizTrackPoint point = new BizTrackPoint();
            point.setPhotoId(photo.getPhotoId());
            point.setLatitude(photo.getLatitude());
            point.setLongitude(photo.getLongitude());
            point.setPointTime(photo.getShootTime());
            point.setSequence(sequence++);
            points.add(point);
            prev = photo;
        }

        BizPhoto first = photos.get(0);
        BizPhoto last = photos.get(photos.size() - 1);
        long durationSec = 0L;
        if (first.getShootTime() != null && last.getShootTime() != null) {
            durationSec = Math.max(0L, (last.getShootTime().getTime() - first.getShootTime().getTime()) / 1000L);
        }
        return new TrackBuildResult(points, totalDistance, durationSec, first.getShootTime(), last.getShootTime());
    }

    /**
     * 默认名称：相册名称 + 相册轨迹 + 序号（如「天坛公园相册轨迹1」）
     */
    private String resolveTrackName(Long albumId, String trackName) {
        if (trackName != null && !trackName.trim().isEmpty()) {
            return trackName.trim();
        }
        BizAlbum album = albumService.getById(albumId);
        String albumName = album != null && album.getAlbumName() != null && !album.getAlbumName().isEmpty()
                ? album.getAlbumName()
                : "相册" + albumId;
        long count = count(new LambdaQueryWrapper<BizTrack>()
                .eq(BizTrack::getAlbumId, albumId)
                .eq(BizTrack::getDeleted, 0));
        return albumName + "相册轨迹" + (count + 1);
    }

    private static final class TrackBuildResult {
        private final List<BizTrackPoint> points;
        private final double totalDistance;
        private final long durationSec;
        private final Date startTime;
        private final Date endTime;

        private TrackBuildResult(List<BizTrackPoint> points, double totalDistance, long durationSec,
                                 Date startTime, Date endTime) {
            this.points = points;
            this.totalDistance = totalDistance;
            this.durationSec = durationSec;
            this.startTime = startTime;
            this.endTime = endTime;
        }
    }
}
