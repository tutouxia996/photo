package com.sq.bus.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sq.bus.config.AlbumProperties;
import com.sq.bus.domain.BizPhoto;
import com.sq.bus.domain.BizTrack;
import com.sq.bus.domain.BizTrackGpxFile;
import com.sq.bus.domain.BizTrackPoint;
import com.sq.bus.mapper.BizTrackGpxFileMapper;
import com.sq.bus.service.IBizPhotoService;
import com.sq.bus.service.IBizTrackGpxFileService;
import com.sq.bus.service.IBizTrackPointService;
import com.sq.bus.service.IBizTrackService;
import com.sq.bus.service.gpx.GpxTrackBuildService;
import com.sq.bus.utils.CoordTransformUtils;
import com.sq.bus.utils.GeoDistanceUtils;
import com.sq.bus.utils.GpxParseUtils;
import com.sq.bus.utils.GpxParseUtils.GpxSample;
import com.sq.bus.utils.TravelModeInferUtils;
import com.sq.common.exception.ServiceException;
import com.sq.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * GPX 仅作为相册叠层数据源：不新建、不覆盖 biz_track。
 * 查看照片/自定义轨迹时，在同一地图上叠加启用中的 GPX 折线；
 * 拍摄时间与 GPX 点接近的媒体会挂到 GPX 坐标上显示。
 */
@Service
public class BizTrackGpxFileServiceImpl extends ServiceImpl<BizTrackGpxFileMapper, BizTrackGpxFile>
        implements IBizTrackGpxFileService {

    private static final Logger log = LoggerFactory.getLogger(BizTrackGpxFileServiceImpl.class);

    private static final String GPX_OVERLAY_COLOR = "#10B981";

    private static final Set<String> VALID_TRAVEL_MODES = new HashSet<String>(Arrays.asList(
            "hsr", "train", "bus", "metro", "walk", "drive", "bike", "flight", "other"
    ));

    @Autowired
    private AlbumProperties albumProperties;

    @Lazy
    @Autowired
    private IBizTrackService trackService;

    @Autowired
    private IBizTrackPointService trackPointService;

    @Autowired
    private IBizPhotoService photoService;

    @Override
    public List<BizTrackGpxFile> listByAlbum(Long albumId) {
        if (albumId == null) {
            return new ArrayList<BizTrackGpxFile>();
        }
        return list(new LambdaQueryWrapper<BizTrackGpxFile>()
                .eq(BizTrackGpxFile::getAlbumId, albumId)
                .eq(BizTrackGpxFile::getDeleted, 0)
                .orderByDesc(BizTrackGpxFile::getGpxId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importGpxFiles(Long albumId, MultipartFile[] files, String username) {
        if (albumId == null) {
            throw new ServiceException("相册ID不能为空");
        }
        if (files == null || files.length == 0) {
            throw new ServiceException("请选择 GPX 文件");
        }
        int offsetHours = albumProperties.getGpx() == null ? 8 : albumProperties.getGpx().getTimeOffsetHours();
        List<BizTrackGpxFile> imported = new ArrayList<BizTrackGpxFile>();
        // 同一次上传内同名只保留最后一次，避免重复累加
        Set<String> seenInBatch = new HashSet<String>();
        List<MultipartFile> ordered = new ArrayList<MultipartFile>();
        for (int i = files.length - 1; i >= 0; i--) {
            MultipartFile file = files[i];
            if (file == null || file.isEmpty()) {
                continue;
            }
            String original = file.getOriginalFilename();
            if (original == null || !original.toLowerCase(Locale.ROOT).endsWith(".gpx")) {
                throw new ServiceException("仅支持 .gpx 文件：" + original);
            }
            String key = normalizeFileName(original);
            if (seenInBatch.contains(key)) {
                continue;
            }
            seenInBatch.add(key);
            ordered.add(0, file);
        }
        int replaced = 0;
        for (MultipartFile file : ordered) {
            String original = file.getOriginalFilename();
            File dest = saveUpload(albumId, file, original);
            List<GpxSample> samples;
            try (InputStream in = new FileInputStream(dest)) {
                samples = GpxParseUtils.parse(in, offsetHours);
            } catch (ServiceException e) {
                safeDelete(dest);
                throw e;
            } catch (Exception e) {
                safeDelete(dest);
                throw new ServiceException("读取 GPX 失败：" + e.getMessage());
            }
            if (samples.size() < 2) {
                safeDelete(dest);
                throw new ServiceException("GPX 有效点不足（需至少 2 个带时间点）：" + original);
            }
            // 同相册同名文件：覆盖旧记录，不新增累加
            replaced += retireSameNameFiles(albumId, original);

            double distKm = pathDistanceKm(samples);
            long durationSec = Math.max(0L,
                    (samples.get(samples.size() - 1).timeMs - samples.get(0).timeMs) / 1000L);
            String travelMode = TravelModeInferUtils.inferFromGpx(
                    durationSec > 0 ? distKm / (durationSec / 3600.0) : 0D,
                    distKm,
                    durationSec);

            BizTrackGpxFile row = new BizTrackGpxFile();
            row.setAlbumId(albumId);
            row.setFileName(original);
            row.setStoragePath(dest.getAbsolutePath());
            row.setPointCount(samples.size());
            row.setDistanceKm(BigDecimal.valueOf(distKm).setScale(3, RoundingMode.HALF_UP));
            row.setTravelMode(travelMode);
            row.setStartTime(GpxParseUtils.toDate(samples.get(0).timeMs));
            row.setEndTime(GpxParseUtils.toDate(samples.get(samples.size() - 1).timeMs));
            row.setEnabled(1);
            row.setCreateBy(username == null ? "" : username);
            row.setCreateTime(new Date());
            row.setDeleted(0);
            save(row);
            imported.add(row);
        }
        if (imported.isEmpty()) {
            throw new ServiceException("没有成功导入的 GPX 文件");
        }

        // 清理历史误建的 GPX 专用轨迹，并尽量保证照片轨存在；导入后默认开启 GPX 线路
        BizTrack photoTrack = syncAlbumAfterGpxChange(albumId);
        enableGpxForAlbumTracks(albumId, username);
        if (photoTrack != null) {
            photoTrack.setGpxEnabled(1);
        }

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("imported", imported.size());
        result.put("replaced", replaced);
        result.put("files", imported);
        if (photoTrack != null) {
            enrichTracks(java.util.Collections.singletonList(photoTrack));
        }
        result.put("track", photoTrack);
        result.put("gpxOverlays", listOverlaysForTrack(photoTrack != null ? photoTrack : findAnyAlbumTrack(albumId)));
        return result;
    }

    /** 同相册下同名（忽略大小写）GPX 软删除并清理磁盘文件，返回覆盖条数 */
    private int retireSameNameFiles(Long albumId, String fileName) {
        if (albumId == null || StringUtils.isEmpty(fileName)) {
            return 0;
        }
        String key = normalizeFileName(fileName);
        List<BizTrackGpxFile> oldList = list(new LambdaQueryWrapper<BizTrackGpxFile>()
                .eq(BizTrackGpxFile::getAlbumId, albumId)
                .eq(BizTrackGpxFile::getDeleted, 0));
        if (oldList == null || oldList.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (BizTrackGpxFile old : oldList) {
            if (old == null || !key.equals(normalizeFileName(old.getFileName()))) {
                continue;
            }
            old.setDeleted(1);
            updateById(old);
            if (StringUtils.isNotEmpty(old.getStoragePath())) {
                safeDelete(new File(old.getStoragePath()));
            }
            count++;
        }
        return count;
    }

    private static String normalizeFileName(String name) {
        if (name == null) {
            return "";
        }
        String n = name.trim();
        // 兼容浏览器带路径的文件名
        int slash = Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\'));
        if (slash >= 0 && slash + 1 < n.length()) {
            n = n.substring(slash + 1);
        }
        return n.toLowerCase(Locale.ROOT);
    }

    private void enableGpxForAlbumTracks(Long albumId, String username) {
        if (albumId == null) {
            return;
        }
        List<BizTrack> tracks = trackService.list(new LambdaQueryWrapper<BizTrack>()
                .eq(BizTrack::getAlbumId, albumId)
                .eq(BizTrack::getDeleted, 0));
        if (tracks == null || tracks.isEmpty()) {
            return;
        }
        Date now = new Date();
        for (BizTrack t : tracks) {
            t.setGpxEnabled(1);
            t.setUpdateBy(username == null ? "" : username);
            t.setUpdateTime(now);
            trackService.updateById(t);
        }
    }

    private BizTrack findAnyAlbumTrack(Long albumId) {
        if (albumId == null) {
            return null;
        }
        BizTrack track = findPhotoTrack(albumId);
        if (track != null) {
            return track;
        }
        return trackService.getOne(new LambdaQueryWrapper<BizTrack>()
                .eq(BizTrack::getAlbumId, albumId)
                .eq(BizTrack::getDeleted, 0)
                .orderByAsc(BizTrack::getTrackId)
                .last("LIMIT 1"), false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean setEnabled(Long gpxId, boolean enabled, String username) {
        BizTrackGpxFile row = requireGpx(gpxId);
        row.setEnabled(enabled ? 1 : 0);
        updateById(row);
        syncAlbumAfterGpxChange(row.getAlbumId());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean setTravelMode(Long gpxId, String travelMode, String username) {
        BizTrackGpxFile row = requireGpx(gpxId);
        String mode = normalizeTravelMode(travelMode);
        if (mode == null) {
            throw new ServiceException("不支持的出行方式：" + travelMode);
        }
        row.setTravelMode(mode);
        updateById(row);
        return true;
    }

    private static String normalizeTravelMode(String travelMode) {
        if (StringUtils.isEmpty(travelMode)) {
            return null;
        }
        String mode = travelMode.trim().toLowerCase(Locale.ROOT);
        return VALID_TRAVEL_MODES.contains(mode) ? mode : null;
    }

    /**
     * 已有 travelMode 直接用；否则按速度推断并回写。
     */
    private String resolveTravelMode(BizTrackGpxFile f, List<GpxSample> samples) {
        String existing = normalizeTravelMode(f.getTravelMode());
        if (existing != null) {
            return existing;
        }
        double distKm = f.getDistanceKm() != null && f.getDistanceKm().doubleValue() > 0
                ? f.getDistanceKm().doubleValue()
                : pathDistanceKm(samples);
        long durationSec = 0L;
        if (samples != null && samples.size() >= 2) {
            durationSec = Math.max(0L,
                    (samples.get(samples.size() - 1).timeMs - samples.get(0).timeMs) / 1000L);
        } else if (f.getStartTime() != null && f.getEndTime() != null) {
            durationSec = Math.max(0L, (f.getEndTime().getTime() - f.getStartTime().getTime()) / 1000L);
        }
        String inferred = TravelModeInferUtils.inferFromGpx(
                durationSec > 0 ? distKm / (durationSec / 3600.0) : 0D,
                distKm,
                durationSec);
        f.setTravelMode(inferred);
        updateById(f);
        return inferred;
    }

    /** 与前端 TRAVEL_MODES 颜色保持一致 */
    private static String colorForTravelMode(String mode) {
        if (mode == null) {
            return GPX_OVERLAY_COLOR;
        }
        switch (mode) {
            case "hsr":
                return "#E11D48";
            case "train":
                return "#C2410C";
            case "bus":
                return "#CA8A04";
            case "metro":
                return "#2563EB";
            case "walk":
                return "#16A34A";
            case "drive":
                return "#7C3AED";
            case "bike":
                return "#0D9488";
            case "flight":
                return "#0891B2";
            case "other":
                return "#64748B";
            default:
                return GPX_OVERLAY_COLOR;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeGpx(Long gpxId, String username) {
        BizTrackGpxFile row = requireGpx(gpxId);
        row.setDeleted(1);
        updateById(row);
        safeDelete(new File(row.getStoragePath()));
        syncAlbumAfterGpxChange(row.getAlbumId());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeByAlbum(Long albumId, String username) {
        if (albumId == null) {
            return 0;
        }
        List<BizTrackGpxFile> rows = list(new LambdaQueryWrapper<BizTrackGpxFile>()
                .eq(BizTrackGpxFile::getAlbumId, albumId)
                .eq(BizTrackGpxFile::getDeleted, 0));
        if (rows.isEmpty()) {
            return 0;
        }
        // 仅软删库记录，保留磁盘文件便于排查/恢复
        for (BizTrackGpxFile row : rows) {
            row.setDeleted(1);
            updateById(row);
        }
        log.info("已软删相册 GPX 库记录 albumId={} count={} by={}", albumId, rows.size(), username);
        return rows.size();
    }

    /**
     * 兼容旧调用名：不再重建/新建轨迹，只做清理与照片轨补回。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public BizTrack rebuildAlbumTrack(Long albumId) {
        return syncAlbumAfterGpxChange(albumId);
    }

    @Override
    public List<Map<String, Object>> listOverlaysForTrack(BizTrack track) {
        if (track == null || track.getAlbumId() == null) {
            return new ArrayList<Map<String, Object>>();
        }
        List<Map<String, Object>> overlays = listOverlays(track.getAlbumId());
        boolean showPath = track.getGpxEnabled() == null || track.getGpxEnabled() == 1;
        if (showPath) {
            return overlays;
        }
        // 关闭 GPX 线路：去掉折线，仍返回时间匹配到的照片，便于地图继续打点
        List<Map<String, Object>> photoOnly = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> item : overlays) {
            if (item == null) {
                continue;
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> matched = (List<Map<String, Object>>) item.get("matchedPhotos");
            if (matched == null || matched.isEmpty()) {
                continue;
            }
            Map<String, Object> row = new HashMap<String, Object>();
            row.put("gpxId", item.get("gpxId"));
            row.put("fileName", item.get("fileName"));
            row.put("travelMode", item.get("travelMode"));
            row.put("color", item.get("color"));
            row.put("path", new ArrayList<Object>());
            row.put("pathPointCount", 0);
            row.put("pointCount", item.get("pointCount"));
            row.put("showPath", Boolean.FALSE);
            row.put("readOnly", Boolean.TRUE);
            row.put("matchedPhotos", matched);
            photoOnly.add(row);
        }
        return photoOnly;
    }

    @Override
    public void enrichTracks(List<BizTrack> tracks) {
        if (tracks == null || tracks.isEmpty()) {
            return;
        }
        Set<Long> albumIds = tracks.stream()
                .map(BizTrack::getAlbumId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (albumIds.isEmpty()) {
            for (BizTrack t : tracks) {
                t.setHasGpx(0);
                applyDisplayStats(t, null);
            }
            return;
        }
        List<BizTrackGpxFile> allFiles = list(new LambdaQueryWrapper<BizTrackGpxFile>()
                .in(BizTrackGpxFile::getAlbumId, albumIds)
                .eq(BizTrackGpxFile::getDeleted, 0));
        Map<Long, List<BizTrackGpxFile>> byAlbum = new HashMap<Long, List<BizTrackGpxFile>>();
        if (allFiles != null) {
            for (BizTrackGpxFile f : allFiles) {
                if (f.getAlbumId() == null) {
                    continue;
                }
                List<BizTrackGpxFile> bucket = byAlbum.get(f.getAlbumId());
                if (bucket == null) {
                    bucket = new ArrayList<BizTrackGpxFile>();
                    byAlbum.put(f.getAlbumId(), bucket);
                }
                bucket.add(f);
            }
        }
        for (BizTrack track : tracks) {
            List<BizTrackGpxFile> files = byAlbum.get(track.getAlbumId());
            boolean hasGpx = files != null && !files.isEmpty();
            track.setHasGpx(hasGpx ? 1 : 0);
            applyDisplayStats(track, hasGpx ? files : null);
        }
    }

    /**
     * 列表/详情展示用统计（不落库）：
     * - 关闭「启用轨迹」时不计照片轨点位/里程/时长
     * - 开启「启用GPX」时再叠加 GPX 数据
     */
    private void applyDisplayStats(BizTrack track, List<BizTrackGpxFile> files) {
        boolean trackOn = track.getEnabled() == null || track.getEnabled() == 1;
        boolean gpxOn = files != null && !files.isEmpty()
                && (track.getGpxEnabled() == null || track.getGpxEnabled() == 1);

        int photoPoints = 0;
        double photoDist = 0D;
        long photoDuration = 0L;
        if (trackOn) {
            photoPoints = track.getPointCount() == null ? 0 : track.getPointCount();
            photoDist = track.getTotalDistance() == null ? 0D : track.getTotalDistance().doubleValue();
            photoDuration = track.getTotalDuration() == null ? 0L : track.getTotalDuration();
        }

        int gpxPoints = 0;
        double gpxDist = 0D;
        long gpxDuration = 0L;
        if (gpxOn) {
            List<BizTrackGpxFile> unique = dedupeByFileName(files);
            for (BizTrackGpxFile f : unique) {
                if (f.getEnabled() != null && f.getEnabled() == 0) {
                    continue;
                }
                gpxPoints += f.getPointCount() == null ? 0 : f.getPointCount();
                if (f.getDistanceKm() != null && f.getDistanceKm().compareTo(BigDecimal.ZERO) > 0) {
                    gpxDist += f.getDistanceKm().doubleValue();
                } else {
                    gpxDist += computeStoredDistance(f);
                }
                if (f.getStartTime() != null && f.getEndTime() != null) {
                    gpxDuration += Math.max(0L, (f.getEndTime().getTime() - f.getStartTime().getTime()) / 1000L);
                }
            }
        }

        track.setPointCount(photoPoints + gpxPoints);
        track.setTotalDistance(BigDecimal.valueOf(photoDist + gpxDist).setScale(2, RoundingMode.HALF_UP));
        track.setTotalDuration(photoDuration + gpxDuration);
        if (photoPoints > 0 && gpxPoints > 0) {
            track.setSourceType("mixed");
        } else if (gpxPoints > 0) {
            track.setSourceType("gpx");
        } else if (photoPoints > 0) {
            track.setSourceType("photo");
        } else if (!trackOn && files != null && !files.isEmpty()) {
            // 轨迹关、GPX 有文件但未启用：仍标照片来源占位，数值为 0
            track.setSourceType("photo");
        } else {
            track.setSourceType(track.getSourceType() == null ? "photo" : track.getSourceType());
        }
    }

    /**
     * 同名 GPX 只保留一条（gpxId 最大），并软删除其余重复项。
     */
    private List<BizTrackGpxFile> dedupeByFileName(List<BizTrackGpxFile> files) {
        if (files == null || files.isEmpty()) {
            return new ArrayList<BizTrackGpxFile>();
        }
        Map<String, BizTrackGpxFile> best = new HashMap<String, BizTrackGpxFile>();
        List<BizTrackGpxFile> duplicates = new ArrayList<BizTrackGpxFile>();
        for (BizTrackGpxFile f : files) {
            if (f == null) {
                continue;
            }
            String key = normalizeFileName(f.getFileName());
            if (key.isEmpty()) {
                key = "id:" + f.getGpxId();
            }
            BizTrackGpxFile prev = best.get(key);
            if (prev == null) {
                best.put(key, f);
                continue;
            }
            long prevId = prev.getGpxId() == null ? 0L : prev.getGpxId();
            long curId = f.getGpxId() == null ? 0L : f.getGpxId();
            if (curId >= prevId) {
                duplicates.add(prev);
                best.put(key, f);
            } else {
                duplicates.add(f);
            }
        }
        for (BizTrackGpxFile dup : duplicates) {
            if (dup.getDeleted() != null && dup.getDeleted() == 1) {
                continue;
            }
            dup.setDeleted(1);
            updateById(dup);
            if (StringUtils.isNotEmpty(dup.getStoragePath())) {
                safeDelete(new File(dup.getStoragePath()));
            }
            log.info("已清理重复 GPX albumId={} gpxId={} file={}",
                    dup.getAlbumId(), dup.getGpxId(), dup.getFileName());
        }
        return new ArrayList<BizTrackGpxFile>(best.values());
    }

    private double computeStoredDistance(BizTrackGpxFile f) {
        int offsetHours = albumProperties.getGpx() == null ? 8 : albumProperties.getGpx().getTimeOffsetHours();
        List<GpxSample> samples = loadSamples(f, offsetHours);
        if (samples.size() < 2) {
            return 0D;
        }
        double km = pathDistanceKm(samples);
        f.setDistanceKm(BigDecimal.valueOf(km).setScale(3, RoundingMode.HALF_UP));
        updateById(f);
        return km;
    }

    private static double pathDistanceKm(List<GpxSample> samples) {
        double dist = 0D;
        for (int i = 1; i < samples.size(); i++) {
            GpxSample a = samples.get(i - 1);
            GpxSample b = samples.get(i);
            dist += GeoDistanceUtils.haversineKm(a.latWgs, a.lngWgs, b.latWgs, b.lngWgs);
        }
        return dist;
    }

    @Override
    public List<Map<String, Object>> listOverlays(Long albumId) {
        List<Map<String, Object>> overlays = new ArrayList<Map<String, Object>>();
        if (albumId == null) {
            return overlays;
        }
        AlbumProperties.GpxConfig cfg = albumProperties.getGpx() == null
                ? new AlbumProperties.GpxConfig() : albumProperties.getGpx();
        int offsetHours = cfg.getTimeOffsetHours();
        long windowMs = Math.max(1, cfg.getMatchWindowSeconds()) * 1000L;

        List<BizTrackGpxFile> files = list(new LambdaQueryWrapper<BizTrackGpxFile>()
                .eq(BizTrackGpxFile::getAlbumId, albumId)
                .eq(BizTrackGpxFile::getDeleted, 0)
                .eq(BizTrackGpxFile::getEnabled, 1)
                .orderByAsc(BizTrackGpxFile::getStartTime)
                .orderByAsc(BizTrackGpxFile::getGpxId));
        files = dedupeByFileName(files);
        files.sort(Comparator
                .comparing(BizTrackGpxFile::getStartTime, Comparator.nullsLast(Date::compareTo))
                .thenComparing(BizTrackGpxFile::getGpxId, Comparator.nullsLast(Long::compareTo)));

        // 合并全部启用 GPX 采样，供时间匹配；同时按文件建叠层折线
        List<TimedSample> merged = new ArrayList<TimedSample>();
        Map<Long, Map<String, Object>> overlayByGpxId = new HashMap<Long, Map<String, Object>>();
        for (BizTrackGpxFile f : files) {
            List<GpxSample> samples = loadSamples(f, offsetHours);
            if (samples.size() < 2) {
                continue;
            }
            // 叠层展示：强制保留全部采样点，附带时间/海拔等，供点击查看（只读，不可编辑）
            List<Map<String, Object>> path = toGcjPathPoints(samples);
            if (path.size() < 2) {
                continue;
            }
            String travelMode = resolveTravelMode(f, samples);
            Map<String, Object> item = new HashMap<String, Object>();
            item.put("gpxId", f.getGpxId());
            item.put("fileName", f.getFileName());
            item.put("travelMode", travelMode);
            item.put("color", colorForTravelMode(travelMode));
            item.put("path", path);
            item.put("pointCount", samples.size());
            item.put("pathPointCount", path.size());
            item.put("stats", buildGpxStats(f, samples));
            item.put("readOnly", true);
            item.put("matchedPhotos", new ArrayList<Map<String, Object>>());
            log.info("GPX叠层 albumId={} gpxId={} file={} mode={} rawPoints={} pathPoints={}",
                    albumId, f.getGpxId(), f.getFileName(), travelMode, samples.size(), path.size());
            overlays.add(item);
            overlayByGpxId.put(f.getGpxId(), item);
            for (GpxSample s : samples) {
                merged.add(new TimedSample(f.getGpxId(), s));
            }
        }
        if (overlays.isEmpty()) {
            return overlays;
        }
        merged.sort(Comparator.comparingLong(t -> t.sample.timeMs));

        List<BizPhoto> media = listAlbumMedia(albumId);
        Set<Long> usedPhotos = new HashSet<Long>();
        int matched = 0;
        for (BizPhoto photo : media) {
            if (photo == null || photo.getPhotoId() == null || photo.getShootTime() == null) {
                continue;
            }
            if (usedPhotos.contains(photo.getPhotoId())) {
                continue;
            }
            TimedSample hit = nearest(merged, photo.getShootTime().getTime(), windowMs);
            if (hit == null) {
                continue;
            }
            Map<String, Object> overlay = overlayByGpxId.get(hit.gpxId);
            if (overlay == null) {
                continue;
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> matchedPhotos = (List<Map<String, Object>>) overlay.get("matchedPhotos");
            matchedPhotos.add(toMatchedPhoto(photo, hit.sample));
            usedPhotos.add(photo.getPhotoId());
            matched++;
        }
        log.debug("GPX 叠层匹配相册媒体 albumId={} overlays={} matched={}", albumId, overlays.size(), matched);
        return overlays;
    }

    private List<BizPhoto> listAlbumMedia(Long albumId) {
        List<BizPhoto> list = photoService.list(new LambdaQueryWrapper<BizPhoto>()
                .eq(BizPhoto::getAlbumId, albumId)
                .eq(BizPhoto::getDeleted, com.sq.bus.constants.AlbumDeleted.NORMAL)
                .isNotNull(BizPhoto::getShootTime)
                .orderByAsc(BizPhoto::getShootTime)
                .orderByAsc(BizPhoto::getPhotoId));
        return list == null ? new ArrayList<BizPhoto>() : list;
    }

    private Map<String, Object> toMatchedPhoto(BizPhoto photo, GpxSample sample) {
        Map<String, Object> m = new HashMap<String, Object>();
        m.put("photoId", photo.getPhotoId());
        // 使用 GPX 坐标（WGS84），前端 toMapLatLng 再转 GCJ
        m.put("latitude", sample.latWgs);
        m.put("longitude", sample.lngWgs);
        m.put("pointTime", photo.getShootTime());
        m.put("shootTime", photo.getShootTime());
        m.put("gpxTime", GpxParseUtils.toDate(sample.timeMs));
        m.put("matchDeltaSec", Math.abs(photo.getShootTime().getTime() - sample.timeMs) / 1000L);
        if (sample.ele != null) {
            m.put("altitude", sample.ele);
        }
        m.put("fileType", photo.getFileType());
        m.put("fileName", photo.getFileName());
        m.put("thumbUrl", photo.getThumbUrl());
        m.put("fileUrl", photo.getFileUrl());
        m.put("address", photo.getAddress());
        m.put("duration", photo.getDuration());
        m.put("onGpx", true);
        return m;
    }

    private static TimedSample nearest(List<TimedSample> samples, long timeMs, long windowMs) {
        if (samples == null || samples.isEmpty()) {
            return null;
        }
        int lo = 0;
        int hi = samples.size() - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (samples.get(mid).sample.timeMs < timeMs) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        TimedSample best = samples.get(lo);
        long bestDiff = Math.abs(best.sample.timeMs - timeMs);
        if (lo > 0) {
            TimedSample prev = samples.get(lo - 1);
            long d = Math.abs(prev.sample.timeMs - timeMs);
            if (d < bestDiff) {
                best = prev;
                bestDiff = d;
            }
        }
        if (lo + 1 < samples.size()) {
            TimedSample next = samples.get(lo + 1);
            long d = Math.abs(next.sample.timeMs - timeMs);
            if (d < bestDiff) {
                best = next;
                bestDiff = d;
            }
        }
        return bestDiff <= windowMs ? best : null;
    }

    private static final class TimedSample {
        private final Long gpxId;
        private final GpxSample sample;

        private TimedSample(Long gpxId, GpxSample sample) {
            this.gpxId = gpxId;
            this.sample = sample;
        }
    }

    private BizTrack syncAlbumAfterGpxChange(Long albumId) {
        if (albumId == null) {
            return null;
        }
        reclaimMislabeledTracks(albumId);
        cleanupDedicatedGpxTracks(albumId);
        ensurePhotoTrack(albumId);
        return findPhotoTrack(albumId);
    }

    private void reclaimMislabeledTracks(Long albumId) {
        List<BizTrack> candidates = trackService.list(new LambdaQueryWrapper<BizTrack>()
                .eq(BizTrack::getAlbumId, albumId)
                .eq(BizTrack::getDeleted, 0)
                .in(BizTrack::getSourceType, "gpx", "mixed")
                .orderByAsc(BizTrack::getTrackId));
        if (candidates == null || candidates.isEmpty()) {
            return;
        }
        for (BizTrack track : candidates) {
            if (!isDedicatedGpxTrack(track)) {
                track.setSourceType("photo");
                track.setUpdateTime(new Date());
                trackService.updateById(track);
                log.info("已将误标轨迹恢复为 photo trackId={} name={}", track.getTrackId(), track.getTrackName());
            }
        }
    }

    /** 软删除历史「独立 GPX 轨迹」记录，避免列表多出一条 */
    private void cleanupDedicatedGpxTracks(Long albumId) {
        List<BizTrack> candidates = trackService.list(new LambdaQueryWrapper<BizTrack>()
                .eq(BizTrack::getAlbumId, albumId)
                .eq(BizTrack::getDeleted, 0)
                .and(w -> w.eq(BizTrack::getSourceType, "gpx")
                        .or().like(BizTrack::getTrackName, "GPX"))
                .orderByAsc(BizTrack::getTrackId));
        if (candidates == null) {
            return;
        }
        for (BizTrack track : candidates) {
            if (isDedicatedGpxTrack(track)) {
                softDeleteGpxTrack(track);
                log.info("已清理独立 GPX 轨迹 albumId={} trackId={}", albumId, track.getTrackId());
            }
        }
    }

    private boolean isDedicatedGpxTrack(BizTrack track) {
        if (track == null || track.getTrackId() == null) {
            return false;
        }
        List<BizTrackPoint> points = trackPointService.list(new LambdaQueryWrapper<BizTrackPoint>()
                .eq(BizTrackPoint::getTrackId, track.getTrackId())
                .orderByAsc(BizTrackPoint::getSequence)
                .orderByAsc(BizTrackPoint::getPointId));
        if (points == null || points.isEmpty()) {
            String name = track.getTrackName();
            return "gpx".equals(track.getSourceType())
                    || (name != null && name.contains("GPX"));
        }
        for (BizTrackPoint p : points) {
            if (p.getPhotoId() != null) {
                return false;
            }
            if (!GpxTrackBuildService.isGpxSynthetic(p)) {
                return false;
            }
        }
        return true;
    }

    private void softDeleteGpxTrack(BizTrack track) {
        trackPointService.remove(new LambdaQueryWrapper<BizTrackPoint>()
                .eq(BizTrackPoint::getTrackId, track.getTrackId()));
        track.setDeleted(1);
        track.setUpdateTime(new Date());
        trackService.updateById(track);
    }

    private void ensurePhotoTrack(Long albumId) {
        if (findPhotoTrack(albumId) != null) {
            return;
        }
        try {
            BizTrack restored = trackService.autoSyncAlbumTrack(albumId);
            if (restored != null) {
                log.info("已自动补回照片轨迹 albumId={} trackId={}", albumId, restored.getTrackId());
            }
        } catch (Exception e) {
            log.warn("自动补回照片轨迹失败 albumId={}", albumId, e);
        }
    }

    private BizTrack findPhotoTrack(Long albumId) {
        return trackService.getOne(new LambdaQueryWrapper<BizTrack>()
                .eq(BizTrack::getAlbumId, albumId)
                .eq(BizTrack::getDeleted, 0)
                .and(w -> w.isNull(BizTrack::getSourceType)
                        .or().eq(BizTrack::getSourceType, "")
                        .or().eq(BizTrack::getSourceType, "photo"))
                .orderByAsc(BizTrack::getTrackId)
                .last("LIMIT 1"), false);
    }

    private List<GpxSample> loadSamples(BizTrackGpxFile f, int offsetHours) {
        if (f == null || StringUtils.isEmpty(f.getStoragePath())) {
            return new ArrayList<GpxSample>();
        }
        File file = new File(f.getStoragePath());
        if (!file.isFile()) {
            log.warn("GPX 文件不存在 gpxId={} path={}", f.getGpxId(), f.getStoragePath());
            return new ArrayList<GpxSample>();
        }
        try (InputStream in = new FileInputStream(file)) {
            List<GpxSample> samples = GpxParseUtils.parse(in, offsetHours);
            samples.sort(Comparator.comparingLong(s -> s.timeMs));
            // 仅去掉连续完全重合点，不做间距抽稀，保证轨迹点一一对应
            return dedupeExact(samples);
        } catch (Exception e) {
            log.warn("解析 GPX 失败 gpxId={}", f.getGpxId(), e);
            return new ArrayList<GpxSample>();
        }
    }

    /**
     * 将全部 GPX 点转为 GCJ-02 折线点（不抽稀），并附带时间/海拔/卫星/瞬时速度。
     */
    private List<Map<String, Object>> toGcjPathPoints(List<GpxSample> samples) {
        List<Map<String, Object>> path = new ArrayList<Map<String, Object>>(samples.size());
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        GpxSample prev = null;
        for (int i = 0; i < samples.size(); i++) {
            GpxSample s = samples.get(i);
            double[] gcj = CoordTransformUtils.wgs84ToGcj02(s.lngWgs, s.latWgs);
            Map<String, Object> row = new HashMap<String, Object>();
            row.put("seq", i + 1);
            row.put("lat", gcj[1]);
            row.put("lng", gcj[0]);
            row.put("latWgs", s.latWgs);
            row.put("lngWgs", s.lngWgs);
            row.put("timeMs", s.timeMs);
            row.put("time", fmt.format(new Date(s.timeMs)));
            if (s.rawTime != null) {
                row.put("rawTime", s.rawTime);
            }
            if (s.ele != null) {
                row.put("ele", Math.round(s.ele * 10.0) / 10.0);
            }
            if (s.sat != null) {
                row.put("sat", s.sat);
            }
            if (prev != null) {
                double distKm = GeoDistanceUtils.haversineKm(prev.latWgs, prev.lngWgs, s.latWgs, s.lngWgs);
                long dtMs = Math.max(1L, s.timeMs - prev.timeMs);
                double speedKmh = distKm / (dtMs / 3600000.0);
                if (!Double.isNaN(speedKmh) && !Double.isInfinite(speedKmh) && speedKmh < 500) {
                    row.put("speedKmh", Math.round(speedKmh * 10.0) / 10.0);
                }
                row.put("segDistM", Math.round(distKm * 10000.0) / 10.0);
            }
            path.add(row);
            prev = s;
        }
        return path;
    }

    private Map<String, Object> buildGpxStats(BizTrackGpxFile file, List<GpxSample> samples) {
        Map<String, Object> stats = new HashMap<String, Object>();
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        GpxSample first = samples.get(0);
        GpxSample last = samples.get(samples.size() - 1);
        stats.put("fileName", file.getFileName());
        stats.put("pointCount", samples.size());
        stats.put("startTime", fmt.format(new Date(first.timeMs)));
        stats.put("endTime", fmt.format(new Date(last.timeMs)));
        long durationSec = Math.max(0L, (last.timeMs - first.timeMs) / 1000L);
        stats.put("durationSec", durationSec);
        stats.put("durationText", formatDuration(durationSec));

        double distKm = 0D;
        double ascent = 0D;
        double descent = 0D;
        Double minEle = null;
        Double maxEle = null;
        GpxSample prev = null;
        for (GpxSample s : samples) {
            if (prev != null) {
                distKm += GeoDistanceUtils.haversineKm(prev.latWgs, prev.lngWgs, s.latWgs, s.lngWgs);
                if (prev.ele != null && s.ele != null) {
                    double d = s.ele - prev.ele;
                    if (d > 0) {
                        ascent += d;
                    } else {
                        descent += -d;
                    }
                }
            }
            if (s.ele != null) {
                if (minEle == null || s.ele < minEle) {
                    minEle = s.ele;
                }
                if (maxEle == null || s.ele > maxEle) {
                    maxEle = s.ele;
                }
            }
            prev = s;
        }
        stats.put("distanceKm", Math.round(distKm * 1000.0) / 1000.0);
        stats.put("distanceText", distKm < 1
                ? (Math.round(distKm * 1000) + " m")
                : (Math.round(distKm * 100.0) / 100.0 + " km"));
        if (minEle != null) {
            stats.put("minEle", Math.round(minEle * 10.0) / 10.0);
            stats.put("maxEle", Math.round(maxEle * 10.0) / 10.0);
            stats.put("ascent", Math.round(ascent));
            stats.put("descent", Math.round(descent));
        }
        stats.put("startLat", first.latWgs);
        stats.put("startLng", first.lngWgs);
        stats.put("endLat", last.latWgs);
        stats.put("endLng", last.lngWgs);
        return stats;
    }

    private static String formatDuration(long sec) {
        long h = sec / 3600;
        long m = (sec % 3600) / 60;
        long s = sec % 60;
        if (h > 0) {
            return String.format("%d:%02d:%02d", h, m, s);
        }
        return String.format("%02d:%02d", m, s);
    }

    private List<List<Double>> toGcjPath(List<GpxSample> samples, AlbumProperties.GpxConfig cfg) {
        List<GpxSample> keep = simplify(samples, cfg.getSimplifyMinMeters(), cfg.getMaxPathPoints());
        List<List<Double>> path = new ArrayList<List<Double>>(keep.size());
        for (GpxSample s : keep) {
            double[] gcj = CoordTransformUtils.wgs84ToGcj02(s.lngWgs, s.latWgs);
            List<Double> row = new ArrayList<Double>(2);
            row.add(gcj[1]);
            row.add(gcj[0]);
            path.add(row);
        }
        return path;
    }

    /**
     * 可选抽稀。minMeters&lt;=0 且 maxPoints&lt;=0 时原样返回全部点。
     */
    private static List<GpxSample> simplify(List<GpxSample> samples, double minMeters, int maxPoints) {
        if (samples == null || samples.isEmpty()) {
            return new ArrayList<GpxSample>();
        }
        if (samples.size() <= 2) {
            return samples;
        }
        List<GpxSample> out = samples;
        if (minMeters > 0) {
            double minKm = minMeters / 1000.0;
            out = new ArrayList<GpxSample>();
            out.add(samples.get(0));
            GpxSample prev = samples.get(0);
            for (int i = 1; i < samples.size() - 1; i++) {
                GpxSample cur = samples.get(i);
                double d = GeoDistanceUtils.haversineKm(prev.latWgs, prev.lngWgs, cur.latWgs, cur.lngWgs);
                if (d >= minKm) {
                    out.add(cur);
                    prev = cur;
                }
            }
            out.add(samples.get(samples.size() - 1));
        }
        if (maxPoints <= 0 || out.size() <= maxPoints) {
            return out;
        }
        // 仅在显式配置上限时均匀抽稀
        List<GpxSample> reduced = new ArrayList<GpxSample>();
        reduced.add(out.get(0));
        double step = (out.size() - 1) * 1.0 / (maxPoints - 1);
        for (int i = 1; i < maxPoints - 1; i++) {
            int idx = (int) Math.round(i * step);
            if (idx <= 0) {
                idx = 1;
            }
            if (idx >= out.size() - 1) {
                idx = out.size() - 2;
            }
            reduced.add(out.get(idx));
        }
        reduced.add(out.get(out.size() - 1));
        return reduced;
    }

    /** 仅去除连续完全重合（同时间且同坐标）的点 */
    private List<GpxSample> dedupeExact(List<GpxSample> samples) {
        if (samples == null || samples.size() < 2) {
            return samples == null ? new ArrayList<GpxSample>() : samples;
        }
        List<GpxSample> out = new ArrayList<GpxSample>(samples.size());
        GpxSample prev = null;
        for (GpxSample s : samples) {
            if (prev != null
                    && prev.timeMs == s.timeMs
                    && Double.compare(prev.latWgs, s.latWgs) == 0
                    && Double.compare(prev.lngWgs, s.lngWgs) == 0) {
                continue;
            }
            out.add(s);
            prev = s;
        }
        return out;
    }

    private BizTrackGpxFile requireGpx(Long gpxId) {
        BizTrackGpxFile row = getById(gpxId);
        if (row == null || row.getDeleted() != null && row.getDeleted() == 1) {
            throw new ServiceException("GPX 文件不存在");
        }
        return row;
    }

    private File saveUpload(Long albumId, MultipartFile file, String original) {
        try {
            String root = albumProperties.getUploadPath();
            if (StringUtils.isEmpty(root)) {
                root = System.getProperty("java.io.tmpdir") + "/album-upload";
            }
            File dir = new File(root, "gpx/" + albumId);
            if (!dir.exists() && !dir.mkdirs()) {
                throw new ServiceException("无法创建 GPX 存储目录");
            }
            String safe = UUID.randomUUID().toString().replace("-", "") + ".gpx";
            File dest = new File(dir, safe);
            file.transferTo(dest);
            return dest;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("保存 GPX 失败：" + e.getMessage());
        }
    }

    private void safeDelete(File file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file.toPath());
        } catch (Exception ignored) {
            // ignore
        }
    }
}
