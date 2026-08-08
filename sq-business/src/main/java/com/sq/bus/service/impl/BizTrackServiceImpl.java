package com.sq.bus.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sq.bus.domain.BizPhoto;
import com.sq.bus.domain.BizTrack;
import com.sq.bus.domain.BizTrackPoint;
import com.sq.bus.mapper.BizTrackMapper;
import com.sq.bus.service.IBizPhotoService;
import com.sq.bus.service.IBizTrackPointService;
import com.sq.bus.service.IBizTrackService;
import com.sq.bus.utils.GeoDistanceUtils;
import com.sq.common.exception.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class BizTrackServiceImpl extends ServiceImpl<BizTrackMapper, BizTrack> implements IBizTrackService {

    /** 相邻点超过该距离（公里）视为异常跳变，分段重新起算 */
    private static final double JUMP_THRESHOLD_KM = 50.0;

    @Autowired
    private IBizPhotoService photoService;

    @Autowired
    private IBizTrackPointService trackPointService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BizTrack generateTrack(Long albumId, String trackName, Date startTime, Date endTime) {
        if (albumId == null) {
            throw new ServiceException("相册ID不能为空");
        }
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
            throw new ServiceException("所选范围内没有带地理位置的图片");
        }

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

        BizTrack track = new BizTrack();
        track.setAlbumId(albumId);
        track.setTrackName(trackName == null || trackName.isEmpty() ? "相册轨迹-" + albumId : trackName);
        track.setStartTime(first.getShootTime());
        track.setEndTime(last.getShootTime());
        track.setTotalDistance(BigDecimal.valueOf(totalDistance).setScale(2, BigDecimal.ROUND_HALF_UP));
        track.setTotalDuration(durationSec);
        track.setPointCount(points.size());
        track.setTrackColor("#3B82F6");
        track.setIsPublic(1);
        track.setCreateTime(new Date());
        save(track);

        for (BizTrackPoint point : points) {
            point.setTrackId(track.getTrackId());
        }
        trackPointService.saveBatch(points);
        return track;
    }
}
