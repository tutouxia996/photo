package com.sq.bus.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sq.bus.domain.BizAlbum;
import com.sq.bus.domain.BizPhoto;
import com.sq.bus.domain.BizTrack;
import com.sq.bus.domain.BizTrackPoint;
import com.sq.bus.mapper.BizTrackMapper;
import com.sq.bus.service.IBizAlbumService;
import com.sq.bus.service.IBizPhotoService;
import com.sq.bus.service.IBizTrackPointService;
import com.sq.bus.service.IBizTrackService;
import com.sq.bus.utils.GeoDistanceUtils;
import com.sq.common.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

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

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BizTrack generateTrack(Long albumId, String trackName, Date startTime, Date endTime) {
        if (albumId == null) {
            throw new ServiceException("相册ID不能为空");
        }
        List<BizPhoto> photos = listGpsPhotos(albumId, startTime, endTime);
        if (photos.isEmpty()) {
            throw new ServiceException("所选范围内没有带地理位置的图片");
        }
        TrackBuildResult built = buildTrackPoints(photos);
        return createTrack(albumId, trackName, built);
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

    private BizTrack doAutoSyncAlbumTrack(Long albumId) {
        List<BizPhoto> photos = listGpsPhotos(albumId, null, null);
        if (photos.isEmpty()) {
            return null;
        }
        TrackBuildResult built = buildTrackPoints(photos);

        BizTrack existing = getOne(new LambdaQueryWrapper<BizTrack>()
                .eq(BizTrack::getAlbumId, albumId)
                .eq(BizTrack::getDeleted, 0)
                .orderByAsc(BizTrack::getTrackId)
                .last("LIMIT 1"), false);
        if (existing == null) {
            BizTrack track = createTrack(albumId, null, built);
            log.info("自动生成相册轨迹 albumId={} trackId={} points={}", albumId, track.getTrackId(), built.points.size());
            return track;
        }
        // 关闭启用：保留已有点位，不再自动刷新生成
        if (existing.getEnabled() != null && existing.getEnabled() == 0) {
            log.debug("相册轨迹已关闭启用，跳过自动同步 albumId={} trackId={}", albumId, existing.getTrackId());
            return existing;
        }

        // 保留无照片人工途经点，按原顺序锚到前一个照片点之后
        List<BizTrackPoint> oldPoints = trackPointService.list(new LambdaQueryWrapper<BizTrackPoint>()
                .eq(BizTrackPoint::getTrackId, existing.getTrackId())
                .orderByAsc(BizTrackPoint::getSequence)
                .orderByAsc(BizTrackPoint::getPointId));
        List<KeptWaypoint> keptWaypoints = captureWaypoints(oldPoints);

        trackPointService.remove(new LambdaQueryWrapper<BizTrackPoint>()
                .eq(BizTrackPoint::getTrackId, existing.getTrackId()));

        List<BizTrackPoint> merged = mergePhotoAndWaypoints(built.points, keptWaypoints);
        int seq = 1;
        for (BizTrackPoint point : merged) {
            point.setTrackId(existing.getTrackId());
            point.setPointId(null);
            point.setSequence(seq++);
        }

        TrackBuildResult mergedBuilt = new TrackBuildResult(
                merged,
                built.totalDistance,
                built.durationSec,
                built.startTime,
                built.endTime);
        applyBuilt(existing, mergedBuilt);
        existing.setUpdateTime(new Date());
        updateById(existing);
        if (!merged.isEmpty()) {
            trackPointService.saveBatch(merged);
        }
        log.info("自动刷新相册轨迹 albumId={} trackId={} photoPoints={} waypoints={}",
                albumId, existing.getTrackId(), built.points.size(), keptWaypoints.size());
        return existing;
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

    private List<BizPhoto> listGpsPhotos(Long albumId, Date startTime, Date endTime) {
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
        return photos == null ? new ArrayList<BizPhoto>() : photos;
    }

    private BizTrack createTrack(Long albumId, String trackName, TrackBuildResult built) {
        BizTrack track = new BizTrack();
        track.setAlbumId(albumId);
        track.setTrackName(resolveTrackName(albumId, trackName));
        applyBuilt(track, built);
        track.setTrackColor("#3B82F6");
        track.setIsPublic(1);
        track.setEnabled(1);
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
