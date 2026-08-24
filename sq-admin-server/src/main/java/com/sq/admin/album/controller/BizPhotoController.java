package com.sq.admin.album.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sq.bus.config.AlbumProperties;
import com.sq.bus.constants.AlbumDeleted;
import com.sq.bus.constants.PhotoLocationSource;
import com.sq.bus.domain.BizAlbum;
import com.sq.bus.domain.BizPhoto;
import com.sq.bus.domain.RegionLocateRequest;
import com.sq.bus.domain.vo.PhotoDrawBatchRequest;
import com.sq.bus.domain.vo.PhotoDrawRequest;
import com.sq.bus.domain.vo.PhotoScoreRequest;
import com.sq.bus.service.IBizAlbumService;
import com.sq.bus.service.IBizPhotoService;
import com.sq.bus.service.IBizTrackService;
import com.sq.bus.service.IPhotoFallbackLocationService;
import com.sq.bus.service.IPhotoDrawService;
import com.sq.bus.service.IPhotoQualityService;
import com.sq.bus.service.route.AmapPlaceSearchService;
import com.sq.bus.utils.ExifParseUtils;
import com.sq.bus.utils.PhotoFieldUtils;
import com.sq.bus.utils.ThumbUtils;
import com.sq.bus.utils.VideoMetaUtils;
import com.sq.common.annotation.Log;
import com.sq.common.core.controller.BaseController;
import com.sq.common.core.domain.AjaxResult;
import com.sq.common.core.page.TableDataInfo;
import com.sq.common.enums.BusinessType;
import com.sq.common.utils.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.URLConnection;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 图片管理
 */
@RestController
@RequestMapping("/album/photo")
public class BizPhotoController extends BaseController {

    private static final Set<String> VIDEO_EXT = new HashSet<String>(Arrays.asList(
            "mp4", "mov", "avi", "mkv", "wmv"));

    @Autowired
    private IBizPhotoService photoService;

    @Autowired
    private IBizAlbumService albumService;

    @Autowired
    private IBizTrackService trackService;

    @Autowired
    private IPhotoFallbackLocationService fallbackLocationService;

    @Autowired
    private IPhotoQualityService photoQualityService;

    @Autowired
    private IPhotoDrawService photoDrawService;

    @Autowired
    private AmapPlaceSearchService amapPlaceSearchService;

    @Autowired
    private com.sq.bus.service.VisitedRegionGeoService visitedRegionGeoService;

    @Autowired
    private AlbumProperties albumProperties;

    @Autowired
    private com.sq.bus.service.IVideoProxyService videoProxyService;

    @Autowired
    private com.sq.bus.service.IBizScanPathService scanPathService;

    /**
     * 缩略图：优先静态文件；视频无封面时按需 ffmpeg 截帧并缓存。
     */
    @GetMapping("/thumb/{photoId}")
    public void thumb(@PathVariable Long photoId, HttpServletResponse response) throws Exception {
        BizPhoto photo = photoService.getById(photoId);
        if (photo == null || photo.getDeleted() != null && photo.getDeleted() == AlbumDeleted.PURGED) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        File file = resolveMediaFile(photo, false);
        if (file == null || !file.exists() || !file.isFile()) {
            scanPathService.ensurePhotoThumb(photoId);
            photo = photoService.getById(photoId);
            file = resolveMediaFile(photo, false);
        }
        if (file == null || !file.exists() || !file.isFile()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        response.setHeader("Cache-Control", "public, max-age=86400");
        response.setContentType(MediaType.IMAGE_JPEG_VALUE);
        Files.copy(file.toPath(), response.getOutputStream());
    }

    /**
     * 足迹图：已访问省/市/区县的行政区边界（GeoJSON，GCJ-02）
     */
    @PreAuthorize("@ss.hasPermi('album:photo:list')")
    @GetMapping("/visitedRegionGeo")
    public AjaxResult visitedRegionGeo() {
        try {
            return success(visitedRegionGeoService.buildVisitedRegionGeo());
        } catch (Exception e) {
            // 高亮失败不阻断首页点位展示
            java.util.Map<String, Object> empty = new java.util.LinkedHashMap<String, Object>();
            java.util.Map<String, Object> geo = new java.util.LinkedHashMap<String, Object>();
            geo.put("type", "FeatureCollection");
            geo.put("features", new java.util.ArrayList<Object>());
            empty.put("geojson", geo);
            empty.put("provinces", new java.util.ArrayList<String>());
            empty.put("cities", new java.util.ArrayList<String>());
            empty.put("districts", new java.util.ArrayList<String>());
            empty.put("featureCount", 0);
            return success(empty);
        }
    }

    /**
     * 媒体访问：默认缩略图；original=true 返回原文件；
     * 视频浏览档 quality=720p|1080p + fps=30|60（文件在 cache/proxy，不入库）。
     */
    @GetMapping("/media/{photoId}")
    public void media(@PathVariable Long photoId,
                      @RequestParam(value = "original", defaultValue = "false") boolean original,
                      @RequestParam(value = "quality", required = false) String quality,
                      @RequestParam(value = "fps", required = false) Integer fps,
                      HttpServletRequest request,
                      HttpServletResponse response) throws Exception {
        BizPhoto photo = photoService.getById(photoId);
        if (photo == null || photo.getDeleted() != null && photo.getDeleted() == AlbumDeleted.PURGED) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        File file;
        if (!original && photo.getFileType() != null && photo.getFileType() == 2
                && StringUtils.isNotEmpty(quality)
                && !"original".equalsIgnoreCase(quality)) {
            file = videoProxyService.resolveReadyFile(photoId, quality, fps);
            if (file == null) {
                response.setStatus(HttpServletResponse.SC_CONFLICT);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write("{\"msg\":\"浏览档未就绪，请先调用 videoProxy/ensure\"}");
                return;
            }
        } else {
            file = resolveMediaFile(photo, original);
        }
        if (file == null || !file.exists() || !file.isFile()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String contentType = URLConnection.guessContentTypeFromName(file.getName());
        if (contentType == null) {
            contentType = Files.probeContentType(file.toPath());
        }
        if (contentType == null) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        response.setHeader("Accept-Ranges", "bytes");
        response.setHeader("Cache-Control", "public, max-age=86400");
        writeFileWithRange(file, contentType, request.getHeader("Range"), response);
    }

    /** 查询视频浏览档状态（不触发转码） */
    @GetMapping("/videoProxy/{photoId}")
    public AjaxResult videoProxyStatus(@PathVariable Long photoId,
                                       @RequestParam(value = "quality", defaultValue = "1080p") String quality,
                                       @RequestParam(value = "fps", defaultValue = "30") Integer fps) {
        return success(videoProxyService.status(photoId, quality, fps));
    }

    /** 确保浏览档存在：缺失则异步 ffmpeg 转码（原片不改、不入库） */
    @PostMapping("/videoProxy/{photoId}/ensure")
    public AjaxResult ensureVideoProxy(@PathVariable Long photoId,
                                       @RequestParam(value = "quality", defaultValue = "1080p") String quality,
                                       @RequestParam(value = "fps", defaultValue = "30") Integer fps) {
        return success(videoProxyService.ensure(photoId, quality, fps));
    }

    /** 视频浏览档后台转码全局进度（右下角浮层） */
    @PreAuthorize("@ss.hasPermi('album:photo:list') or @ss.hasPermi('album:scan:list')")
    @GetMapping("/videoProxy/progress")
    public AjaxResult videoProxyProgress() {
        return success(videoProxyService.getProgress());
    }

    @PreAuthorize("@ss.hasPermi('album:photo:list')")
    @GetMapping("/list")
    public TableDataInfo list(BizPhoto query,
                              @RequestParam(value = "shootTimeOrder", defaultValue = "desc") String shootTimeOrder,
                              @RequestParam(value = "scoreFilter", required = false) String scoreFilter,
                              @RequestParam(value = "originFilter", required = false) String originFilter) {
        startPage();
        int deleted = query.getDeleted() == null ? AlbumDeleted.NORMAL : query.getDeleted();
        LambdaQueryWrapper<BizPhoto> wrapper = new LambdaQueryWrapper<BizPhoto>()
                .eq(query.getAlbumId() != null, BizPhoto::getAlbumId, query.getAlbumId())
                .eq(query.getFileType() != null, BizPhoto::getFileType, query.getFileType())
                .like(StringUtils.isNotEmpty(query.getFileName()), BizPhoto::getFileName, query.getFileName())
                .eq(BizPhoto::getDeleted, deleted);
        applyScoreFilter(wrapper, scoreFilter);
        applyOriginFilter(wrapper, originFilter);
        boolean asc = "asc".equalsIgnoreCase(shootTimeOrder);
        if (asc) {
            wrapper.orderByAsc(BizPhoto::getShootTime).orderByAsc(BizPhoto::getPhotoId);
        } else {
            wrapper.orderByDesc(BizPhoto::getShootTime).orderByDesc(BizPhoto::getPhotoId);
        }
        return getDataTable(photoService.list(wrapper));
    }

    /**
     * 地图点位（默认仅权威 GPS，不含时间插值等兜底；includeEstimated=true 可包含）
     */
    @PreAuthorize("@ss.hasPermi('album:photo:list')")
    @GetMapping("/mapPoints")
    public AjaxResult mapPoints(@RequestParam(required = false) Long albumId,
                                @RequestParam(value = "includeEstimated", required = false) Boolean includeEstimated) {
        if (albumId != null) {
            BizAlbum album = albumService.getById(albumId);
            if (album == null || album.getDeleted() == null || album.getDeleted() != AlbumDeleted.NORMAL) {
                return error("相册不存在");
            }
        }
        LambdaQueryWrapper<BizPhoto> wrapper = new LambdaQueryWrapper<BizPhoto>()
                .eq(BizPhoto::getDeleted, AlbumDeleted.NORMAL)
                .isNotNull(BizPhoto::getLatitude)
                .isNotNull(BizPhoto::getLongitude)
                .eq(albumId != null, BizPhoto::getAlbumId, albumId)
                .orderByAsc(BizPhoto::getShootTime)
                .orderByAsc(BizPhoto::getPhotoId);
        List<BizPhoto> list = photoService.list(wrapper);
        if (list == null) {
            list = new ArrayList<BizPhoto>();
        }
        boolean include = includeEstimated != null
                ? includeEstimated
                : (albumProperties.getFallbackLocation() != null
                && albumProperties.getFallbackLocation().isIncludeInMap());
        if (!include) {
            List<BizPhoto> authoritative = new ArrayList<BizPhoto>();
            for (BizPhoto photo : list) {
                if (PhotoLocationSource.isAuthoritativeGps(photo)) {
                    authoritative.add(photo);
                }
            }
            list = authoritative;
        }
        return success(list);
    }

    /**
     * 按同相册权威 GPS 对无坐标媒体做时间插值兜底（不影响主轨迹）
     */
    @PreAuthorize("@ss.hasPermi('album:photo:edit')")
    @Log(title = "照片坐标兜底", businessType = BusinessType.UPDATE)
    @PostMapping("/fallbackLocate/{albumId}")
    public AjaxResult fallbackLocate(@PathVariable Long albumId) {
        BizAlbum album = albumService.getById(albumId);
        if (album == null || album.getDeleted() == null || album.getDeleted() != AlbumDeleted.NORMAL) {
            return error("相册不存在");
        }
        int updated = fallbackLocationService.fillMissingByTimeInterp(albumId);
        return success(updated);
    }

    /**
     * 按国家/省/市/区为无 GPS 媒体写入区域中心粗定位（region_center，可拖动确认）
     */
    @PreAuthorize("@ss.hasPermi('album:photo:edit')")
    @Log(title = "相册区域粗定位", businessType = BusinessType.UPDATE)
    @PostMapping("/regionLocate/{albumId}")
    public AjaxResult regionLocate(@PathVariable Long albumId, @RequestBody RegionLocateRequest body) {
        BizAlbum album = albumService.getById(albumId);
        if (album == null || album.getDeleted() == null || album.getDeleted() != AlbumDeleted.NORMAL) {
            return error("相册不存在");
        }
        java.util.Map<String, Object> result = fallbackLocationService.fillMissingByRegionCenter(albumId, body);
        try {
            com.sq.bus.domain.BizTrack draft = trackService.ensureDraftTrackFromEstimated(albumId);
            if (draft != null) {
                result.put("trackId", draft.getTrackId());
            }
        } catch (Exception e) {
            // 草稿写入失败不影响区域坐标已落库
        }
        return success(result);
    }

    /**
     * AI 识别用户点选的照片地标，写入更细的 ai_landmark 估计坐标（可覆盖 region_center；可多次）
     */
    @PreAuthorize("@ss.hasPermi('album:photo:edit')")
    @Log(title = "AI地标识别", businessType = BusinessType.UPDATE)
    @PostMapping("/aiLandmark/{albumId}")
    public AjaxResult aiLandmark(@PathVariable Long albumId,
                                 @RequestBody(required = false) com.sq.bus.domain.AiLandmarkRequest body) {
        BizAlbum album = albumService.getById(albumId);
        if (album == null || album.getDeleted() == null || album.getDeleted() != AlbumDeleted.NORMAL) {
            return error("相册不存在");
        }
        java.util.Map<String, Object> result = fallbackLocationService.fillMissingByAiLandmark(albumId, body);
        try {
            com.sq.bus.domain.BizTrack draft = trackService.ensureDraftTrackFromEstimated(albumId);
            if (draft != null) {
                result.put("trackId", draft.getTrackId());
            }
        } catch (Exception e) {
            // 草稿刷新失败不影响 AI 坐标已落库
        }
        return success(result);
    }

    /**
     * 行政区/地址地理编码（区域粗定位预览）
     */
    @PreAuthorize("@ss.hasAnyPermi('album:photo:edit,album:track:edit')")
    @GetMapping("/geocode")
    public AjaxResult geocode(@RequestParam String address) {
        return success(amapPlaceSearchService.geocode(address));
    }

    /**
     * 纠正媒体定位（设备 GPS 漂移等到岸边）：写入手工坐标并同步照片轨。
     */
    @PreAuthorize("@ss.hasPermi('album:photo:edit')")
    @Log(title = "纠正媒体定位", businessType = BusinessType.UPDATE)
    @PutMapping("/correctPosition")
    public AjaxResult correctPosition(@RequestBody BizPhoto body) {
        if (body == null || body.getPhotoId() == null) {
            return error("图片ID不能为空");
        }
        if (body.getLatitude() == null || body.getLongitude() == null) {
            return error("经纬度不能为空");
        }
        BizPhoto existing = photoService.getById(body.getPhotoId());
        if (existing == null || (existing.getDeleted() != null && existing.getDeleted() == AlbumDeleted.PURGED)) {
            return error("图片不存在或已删除");
        }
        existing.setLatitude(body.getLatitude());
        existing.setLongitude(body.getLongitude());
        existing.setLocationSource(PhotoLocationSource.MANUAL);
        existing.setLocationConfidence(java.math.BigDecimal.ONE);
        if (body.getAddress() != null) {
            existing.setAddress(body.getAddress());
        }
        if (body.getProvince() != null) {
            existing.setProvince(body.getProvince());
        }
        if (body.getCity() != null) {
            existing.setCity(body.getCity());
        }
        if (body.getDistrict() != null) {
            existing.setDistrict(body.getDistrict());
        }
        existing.setUpdateBy(getUsername());
        existing.setUpdateTime(new Date());
        PhotoFieldUtils.clamp(existing);
        boolean ok = photoService.updateById(existing);
        if (ok && existing.getAlbumId() != null) {
            try {
                fallbackLocationService.fillMissingByTimeInterp(existing.getAlbumId());
                trackService.autoSyncAlbumTrack(existing.getAlbumId());
            } catch (Exception ignored) {
            }
            albumService.refreshAlbumStats(existing.getAlbumId());
        }
        return ok ? success(existing) : error("保存失败");
    }

    /**
     * 微调估计坐标（仍为兜底来源，不上主轨迹）
     */
    @PreAuthorize("@ss.hasPermi('album:photo:edit')")
    @Log(title = "微调估计坐标", businessType = BusinessType.UPDATE)
    @PutMapping("/estimatedPosition")
    public AjaxResult updateEstimatedPosition(@RequestBody BizPhoto body) {
        if (body == null || body.getPhotoId() == null) {
            return error("图片ID不能为空");
        }
        if (body.getLatitude() == null || body.getLongitude() == null) {
            return error("经纬度不能为空");
        }
        BizPhoto existing = photoService.getById(body.getPhotoId());
        if (existing == null || (existing.getDeleted() != null && existing.getDeleted() == AlbumDeleted.PURGED)) {
            return error("图片不存在或已删除");
        }
        if (!PhotoLocationSource.isFallback(existing.getLocationSource())) {
            return error("仅允许调整估计坐标的照片/视频");
        }
        existing.setLatitude(body.getLatitude());
        existing.setLongitude(body.getLongitude());
        if (body.getAddress() != null) {
            existing.setAddress(body.getAddress());
        }
        if (body.getProvince() != null) {
            existing.setProvince(body.getProvince());
        }
        if (body.getCity() != null) {
            existing.setCity(body.getCity());
        }
        if (body.getDistrict() != null) {
            existing.setDistrict(body.getDistrict());
        }
        // 保留原兜底来源（region_center / ai_landmark / time_interp），不强制改成时间插值
        if (existing.getLocationConfidence() == null) {
            existing.setLocationConfidence(java.math.BigDecimal.valueOf(0.6).setScale(3, java.math.BigDecimal.ROUND_HALF_UP));
        }
        existing.setUpdateBy(getUsername());
        existing.setUpdateTime(new Date());
        PhotoFieldUtils.clamp(existing);
        return toAjax(photoService.updateById(existing));
    }

    /**
     * 确认估计坐标：保留 AI/估/区 来源标记，置信度置 1 后进入主轨迹
     */
    @PreAuthorize("@ss.hasPermi('album:photo:edit')")
    @Log(title = "确认估计坐标上主轨迹", businessType = BusinessType.UPDATE)
    @PostMapping("/confirmEstimated")
    public AjaxResult confirmEstimated(@RequestBody BizPhoto body) {
        if (body == null || body.getPhotoId() == null) {
            return error("图片ID不能为空");
        }
        BizPhoto existing = photoService.getById(body.getPhotoId());
        if (existing == null || (existing.getDeleted() != null && existing.getDeleted() == AlbumDeleted.PURGED)) {
            return error("图片不存在或已删除");
        }
        if (!PhotoLocationSource.isFallback(existing.getLocationSource())
                && !PhotoLocationSource.MANUAL.equals(existing.getLocationSource())) {
            // 已是 EXIF/视频权威点无需再确认
            if (PhotoLocationSource.isAuthoritativeGps(existing)) {
                return success(existing);
            }
            return error("当前媒体不是估计坐标，无法确认");
        }
        if (body.getLatitude() != null && body.getLongitude() != null) {
            existing.setLatitude(body.getLatitude());
            existing.setLongitude(body.getLongitude());
        }
        if (existing.getLatitude() == null || existing.getLongitude() == null) {
            return error("经纬度不能为空");
        }
        if (body.getAddress() != null) {
            existing.setAddress(body.getAddress());
        }
        if (body.getProvince() != null) {
            existing.setProvince(body.getProvince());
        }
        if (body.getCity() != null) {
            existing.setCity(body.getCity());
        }
        if (body.getDistrict() != null) {
            existing.setDistrict(body.getDistrict());
        }
        // 保留原估计来源（ai_landmark / time_interp / region_center），地图继续显示 AI/估/区
        if (!PhotoLocationSource.isFallback(existing.getLocationSource())) {
            existing.setLocationSource(PhotoLocationSource.MANUAL);
        }
        existing.setLocationConfidence(java.math.BigDecimal.ONE);
        existing.setUpdateBy(getUsername());
        existing.setUpdateTime(new Date());
        PhotoFieldUtils.clamp(existing);
        boolean ok = photoService.updateById(existing);
        if (ok && existing.getAlbumId() != null) {
            try {
                fallbackLocationService.fillMissingByTimeInterp(existing.getAlbumId());
                // 含清除「区域粗定位草稿」并启用轨迹（勿只 autoSync，否则草稿 remark 清不掉）
                trackService.promoteAfterEstimatedConfirmed(existing.getAlbumId());
            } catch (Exception ignored) {
            }
            albumService.refreshAlbumStats(existing.getAlbumId());
        }
        return ok ? success(existing) : error("保存失败");
    }

    /**
     * 相册内全部待确认估计点一键上主轨迹（保留 AI/估/区 标记）
     */
    @PreAuthorize("@ss.hasPermi('album:photo:edit')")
    @Log(title = "全部估计点确认上主轨迹", businessType = BusinessType.UPDATE)
    @PostMapping("/confirmEstimatedBatch/{albumId}")
    public AjaxResult confirmEstimatedBatch(@PathVariable Long albumId) {
        BizAlbum album = albumService.getById(albumId);
        if (album == null || album.getDeleted() == null || album.getDeleted() != AlbumDeleted.NORMAL) {
            return error("相册不存在");
        }
        java.util.Map<String, Object> result = fallbackLocationService.confirmAllPendingEstimated(albumId);
        try {
            com.sq.bus.domain.BizTrack track = trackService.promoteAfterEstimatedConfirmed(albumId);
            if (track != null) {
                result.put("trackId", track.getTrackId());
                result.put("trackEnabled", track.getEnabled());
                result.put("promoted", track.getRemark() == null
                        || !String.valueOf(track.getRemark()).contains("区域粗定位草稿"));
            }
        } catch (Exception e) {
            // 坐标已确认；轨迹转正失败不回滚确认结果
            result.put("promoteError", e.getMessage());
        }
        return success(result);
    }

    /**
     * 启动相册照片后台打分（不阻塞；进度见 /score/progress）。
     */
    @PreAuthorize("@ss.hasPermi('album:photo:edit')")
    @Log(title = "照片质量打分", businessType = BusinessType.UPDATE)
    @PostMapping("/score/{albumId}")
    public AjaxResult scoreAlbum(@PathVariable Long albumId,
                                 @RequestBody(required = false) PhotoScoreRequest request) {
        return success(photoQualityService.startScoreAlbum(albumId, request));
    }

    /**
     * 照片质量打分进度。
     */
    @PreAuthorize("@ss.hasPermi('album:photo:list')")
    @GetMapping("/score/progress")
    public AjaxResult scoreProgress() {
        return success(photoQualityService.getScoreProgress());
    }

    /**
     * AI 出图可用预设。
     */
    @PreAuthorize("@ss.hasPermi('album:photo:list')")
    @GetMapping("/draw/presets")
    public AjaxResult drawPresets() {
        return success(photoDrawService.listPresets());
    }

    /**
     * 对单张照片执行万相图生图 + 程序拼版（须已通过质量打分）。
     */
    @PreAuthorize("@ss.hasPermi('album:photo:edit')")
    @Log(title = "AI出图", businessType = BusinessType.INSERT)
    @PostMapping("/draw/{photoId}")
    public AjaxResult drawPhoto(@PathVariable Long photoId,
                                @RequestBody(required = false) PhotoDrawRequest request) {
        return success(photoDrawService.drawPhoto(photoId, request, getUsername()));
    }

    /**
     * 批量 AI 出图（多选合格原片）。
     */
    @PreAuthorize("@ss.hasPermi('album:photo:edit')")
    @Log(title = "AI出图批量", businessType = BusinessType.INSERT)
    @PostMapping("/draw/batch/{albumId}")
    public AjaxResult drawPhotoBatch(@PathVariable Long albumId,
                                     @RequestBody(required = false) PhotoDrawBatchRequest request) {
        return success(photoDrawService.drawPhotoBatch(albumId, request, getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('album:photo:query')")
    @GetMapping("/{photoId}")
    public AjaxResult getInfo(@PathVariable Long photoId) {
        BizPhoto photo = photoService.getById(photoId);
        if (photo == null || photo.getDeleted() != null && photo.getDeleted() == AlbumDeleted.PURGED) {
            return error("图片不存在或已删除");
        }
        return success(photo);
    }

    @PreAuthorize("@ss.hasPermi('album:photo:upload')")
    @Log(title = "图片上传", businessType = BusinessType.INSERT)
    @PostMapping("/upload")
    public AjaxResult upload(@RequestParam("file") MultipartFile file,
                             @RequestParam("albumId") Long albumId) throws Exception {
        if (file == null || file.isEmpty()) {
            return error("上传文件不能为空");
        }
        BizAlbum album = albumService.getById(albumId);
        if (album == null || album.getDeleted() == null || album.getDeleted() != AlbumDeleted.NORMAL) {
            return error("相册不存在");
        }
        String original = PhotoFieldUtils.safeFileName(file.getOriginalFilename(), 160);
        String datePath = new SimpleDateFormat("yyyy/MM/dd").format(new Date());
        File dir = new File(albumProperties.getUploadPath(), datePath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String savedName = System.currentTimeMillis() + "_" + original;
        File dest = new File(dir, savedName);
        file.transferTo(dest);

        String md5 = md5Of(dest);
        BizPhoto exists = photoService.findByMd5(md5);
        if (exists != null) {
            dest.delete();
            return error("文件已存在，跳过重复上传");
        }
        int fileType = isVideoFileName(original) ? 2 : 1;
        BizPhoto reusable = photoService.findReusableByMd5(md5);
        if (reusable != null) {
            String fileUrl = "/album/files/upload/" + datePath + "/" + savedName;
            String thumbUrl = createUploadThumb(dest, datePath, savedName, fileType);
            fillUploadMeta(reusable, dest, fileType);
            Long oldAlbumId = reusable.getAlbumId();
            reusable.setAlbumId(albumId);
            reusable.setFileName(original);
            reusable.setFilePath(dest.getAbsolutePath());
            reusable.setFileUrl(fileUrl);
            reusable.setThumbUrl(thumbUrl);
            reusable.setFileSize(dest.length());
            reusable.setFileType(fileType);
            if (fileType == 2) {
                reusable.setDuration(ThumbUtils.getVideoDurationSeconds(dest));
            }
            reusable.setMd5(md5);
            reusable.setDeleted(AlbumDeleted.NORMAL);
            reusable.setUpdateBy(getUsername());
            reusable.setUpdateTime(new Date());
            PhotoFieldUtils.clamp(reusable);
            photoService.updateById(reusable);
            if (oldAlbumId != null && !oldAlbumId.equals(albumId)) {
                albumService.refreshAlbumStats(oldAlbumId);
            }
            albumService.refreshAlbumStats(albumId);
            afterUploadLocation(albumId, reusable);
            return success(reusable);
        }

        String fileUrl = "/album/files/upload/" + datePath + "/" + savedName;
        String thumbUrl = createUploadThumb(dest, datePath, savedName, fileType);

        BizPhoto photo = new BizPhoto();
        fillUploadMeta(photo, dest, fileType);
        photo.setAlbumId(albumId);
        photo.setFileName(original);
        photo.setFilePath(dest.getAbsolutePath());
        photo.setFileUrl(fileUrl);
        photo.setThumbUrl(thumbUrl);
        photo.setFileSize(dest.length());
        photo.setFileType(fileType);
        if (fileType == 2) {
            photo.setDuration(ThumbUtils.getVideoDurationSeconds(dest));
        }
        photo.setMd5(md5);
        photo.setSortOrder(0);
        photo.setDeleted(AlbumDeleted.NORMAL);
        photo.setCreateBy(getUsername());
        photo.setCreateTime(new Date());
        PhotoFieldUtils.clamp(photo);
        photoService.save(photo);
        albumService.refreshAlbumStats(albumId);
        afterUploadLocation(albumId, photo);
        return success(photo);
    }

    private static boolean isVideoFileName(String fileName) {
        if (fileName == null) {
            return false;
        }
        int idx = fileName.lastIndexOf('.');
        if (idx < 0 || idx == fileName.length() - 1) {
            return false;
        }
        return VIDEO_EXT.contains(fileName.substring(idx + 1).toLowerCase(Locale.ROOT));
    }

    private String createUploadThumb(File dest, String datePath, String savedName, int fileType) {
        try {
            File thumbDir = new File(albumProperties.getThumbPath(), datePath);
            if (fileType == 2) {
                String thumbName = "s_" + savedName.replaceAll("\\.[^.]+$", "") + ".jpg";
                File thumbFile = new File(thumbDir, thumbName);
                if (ThumbUtils.createVideoThumbnail(dest, thumbFile, albumProperties.getThumb().getSmallWidth())) {
                    return "/album/files/thumb/" + datePath + "/" + thumbName;
                }
                return null;
            }
            File thumbFile = new File(thumbDir, "s_" + savedName);
            ThumbUtils.createThumbnail(dest, thumbFile, albumProperties.getThumb().getSmallWidth());
            return "/album/files/thumb/" + datePath + "/s_" + savedName;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void fillUploadMeta(BizPhoto photo, File dest, int fileType) {
        if (fileType == 2) {
            VideoMetaUtils.MetaInfo meta = VideoMetaUtils.parse(dest);
            photo.setShootTime(meta.getShootTime() != null ? meta.getShootTime() : new Date());
            PhotoLocationSource.applyDeviceGps(photo, meta.getLatitude(), meta.getLongitude(), fileType);
            photo.setCameraModel(null);
            photo.setLensInfo(null);
            photo.setAperture(null);
            photo.setShutterSpeed(null);
            photo.setIso(null);
            photo.setFocalLength(null);
            return;
        }
        ExifParseUtils.ExifInfo exif = ExifParseUtils.parse(dest);
        photo.setShootTime(exif.getShootTime() != null ? exif.getShootTime() : new Date());
        PhotoLocationSource.applyDeviceGps(photo, exif.getLatitude(), exif.getLongitude(), fileType);
        photo.setCameraModel(exif.getCameraModel());
        photo.setLensInfo(exif.getLensInfo());
        photo.setAperture(exif.getAperture());
        photo.setShutterSpeed(exif.getShutterSpeed());
        photo.setIso(exif.getIso());
        photo.setFocalLength(exif.getFocalLength());
    }

    /** 上传后：先尝试时间插值兜底，再对权威 GPS 同步主轨迹 */
    private void afterUploadLocation(Long albumId, BizPhoto photo) {
        try {
            fallbackLocationService.fillMissingByTimeInterp(albumId);
        } catch (Exception ignored) {
        }
        if (photo != null && photo.getPhotoId() != null) {
            BizPhoto latest = photoService.getById(photo.getPhotoId());
            if (latest != null) {
                photo.setLatitude(latest.getLatitude());
                photo.setLongitude(latest.getLongitude());
                photo.setLocationSource(latest.getLocationSource());
                photo.setLocationConfidence(latest.getLocationConfidence());
            }
        }
        if (PhotoLocationSource.isAuthoritativeGps(photo)) {
            try {
                trackService.autoSyncAlbumTrack(albumId);
            } catch (Exception ignored) {
            }
        }
    }

    @PreAuthorize("@ss.hasPermi('album:photo:edit')")
    @Log(title = "图片管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody BizPhoto photo) {
        if (photo.getPhotoId() == null) {
            return error("图片ID不能为空");
        }
        BizPhoto existing = photoService.getById(photo.getPhotoId());
        if (existing == null || (existing.getDeleted() != null && existing.getDeleted() == AlbumDeleted.PURGED)) {
            return error("图片不存在或已删除");
        }
        boolean coordsChanged = !sameCoord(existing.getLatitude(), photo.getLatitude())
                || !sameCoord(existing.getLongitude(), photo.getLongitude());
        if (coordsChanged && photo.getLatitude() != null && photo.getLongitude() != null) {
            photo.setLocationSource(PhotoLocationSource.MANUAL);
            photo.setLocationConfidence(java.math.BigDecimal.ONE);
        } else if (photo.getLocationSource() == null) {
            photo.setLocationSource(existing.getLocationSource());
            photo.setLocationConfidence(existing.getLocationConfidence());
        }
        PhotoFieldUtils.clamp(photo);
        photo.setUpdateBy(getUsername());
        photo.setUpdateTime(new Date());
        boolean ok = photoService.updateById(photo);
        Long albumId = photo.getAlbumId() != null ? photo.getAlbumId() : existing.getAlbumId();
        if (ok && albumId != null) {
            albumService.refreshAlbumStats(albumId);
            if (PhotoLocationSource.isAuthoritativeGps(photo)) {
                try {
                    fallbackLocationService.fillMissingByTimeInterp(albumId);
                    trackService.autoSyncAlbumTrack(albumId);
                } catch (Exception ignored) {
                }
            }
        }
        return toAjax(ok);
    }

    private static boolean sameCoord(java.math.BigDecimal a, java.math.BigDecimal b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.compareTo(b) == 0;
    }

    @PreAuthorize("@ss.hasPermi('album:photo:remove')")
    @Log(title = "图片回收站", businessType = BusinessType.DELETE)
    @DeleteMapping("/{photoIds}")
    public AjaxResult remove(@PathVariable Long[] photoIds) {
        return toAjax(photoService.trashPhotos(Arrays.asList(photoIds)));
    }

    @PreAuthorize("@ss.hasPermi('album:photo:edit')")
    @Log(title = "图片恢复", businessType = BusinessType.UPDATE)
    @PutMapping("/restore/{photoIds}")
    public AjaxResult restore(@PathVariable Long[] photoIds) {
        return toAjax(photoService.restorePhotos(Arrays.asList(photoIds)));
    }

    @PreAuthorize("@ss.hasPermi('album:photo:remove')")
    @Log(title = "图片彻底删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/purge/{photoIds}")
    public AjaxResult purge(@PathVariable Long[] photoIds) {
        return toAjax(photoService.purgePhotos(Arrays.asList(photoIds)));
    }

    private void applyScoreFilter(LambdaQueryWrapper<BizPhoto> wrapper, String scoreFilter) {
        if (StringUtils.isEmpty(scoreFilter) || "all".equalsIgnoreCase(scoreFilter)) {
            return;
        }
        if ("pass".equalsIgnoreCase(scoreFilter)) {
            wrapper.eq(BizPhoto::getScorePass, 1);
        } else if ("fail".equalsIgnoreCase(scoreFilter)) {
            wrapper.eq(BizPhoto::getScorePass, 0);
        } else if ("unscored".equalsIgnoreCase(scoreFilter)) {
            wrapper.isNull(BizPhoto::getScorePass);
        }
    }

    private void applyOriginFilter(LambdaQueryWrapper<BizPhoto> wrapper, String originFilter) {
        if (StringUtils.isEmpty(originFilter) || "all".equalsIgnoreCase(originFilter)) {
            return;
        }
        if ("aiDraw".equalsIgnoreCase(originFilter)) {
            wrapper.eq(BizPhoto::getOriginType, "ai_draw");
        } else if ("original".equalsIgnoreCase(originFilter)) {
            wrapper.and(w -> w.isNull(BizPhoto::getOriginType)
                    .or().eq(BizPhoto::getOriginType, "")
                    .or().eq(BizPhoto::getOriginType, "original"));
        }
    }

    private File resolveMediaFile(BizPhoto photo, boolean original) {
        if (!original) {
            if (StringUtils.isNotEmpty(photo.getThumbUrl())
                    && photo.getThumbUrl().startsWith("/album/files/thumb/")) {
                String rel = photo.getThumbUrl().substring("/album/files/thumb/".length());
                File thumb = new File(albumProperties.getThumbPath(), rel);
                if (thumb.exists() && thumb.isFile()) {
                    return thumb;
                }
            }
            // 缩略图缺失时不回退原图/原视频，避免相册网格下大文件卡顿
            return null;
        }
        if (StringUtils.isNotEmpty(photo.getFilePath())) {
            File origin = new File(photo.getFilePath());
            if (origin.exists() && origin.isFile()) {
                return origin;
            }
        }
        return null;
    }

    private void writeFileWithRange(File file, String contentType, String rangeHeader,
                                    HttpServletResponse response) throws Exception {
        long fileLength = file.length();
        long start = 0;
        long end = fileLength - 1;
        boolean isPartial = false;
        if (StringUtils.isNotEmpty(rangeHeader) && rangeHeader.startsWith("bytes=")) {
            String[] parts = rangeHeader.substring(6).split("-");
            try {
                if (parts.length > 0 && StringUtils.isNotEmpty(parts[0])) {
                    start = Long.parseLong(parts[0]);
                }
                if (parts.length > 1 && StringUtils.isNotEmpty(parts[1])) {
                    end = Long.parseLong(parts[1]);
                }
                if (end >= fileLength) {
                    end = fileLength - 1;
                }
                if (start > end || start < 0) {
                    response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                    response.setHeader("Content-Range", "bytes */" + fileLength);
                    return;
                }
                isPartial = true;
            } catch (NumberFormatException ignored) {
                start = 0;
                end = fileLength - 1;
                isPartial = false;
            }
        }
        long contentLength = end - start + 1;
        response.setContentType(contentType);
        response.setHeader("Content-Length", String.valueOf(contentLength));
        if (isPartial) {
            response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
            response.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + fileLength);
        } else {
            response.setStatus(HttpServletResponse.SC_OK);
        }
        try (RandomAccessFile raf = new RandomAccessFile(file, "r");
             OutputStream out = response.getOutputStream()) {
            raf.seek(start);
            byte[] buffer = new byte[8192];
            long remain = contentLength;
            while (remain > 0) {
                int read = raf.read(buffer, 0, (int) Math.min(buffer.length, remain));
                if (read < 0) {
                    break;
                }
                out.write(buffer, 0, read);
                remain -= read;
            }
            out.flush();
        } catch (IOException e) {
            // 客户端中断（切页、拖进度、关闭标签等）属正常现象，勿上抛以免全局异常处理再写 JSON
            if (!isClientAbort(e)) {
                throw e;
            }
        }
    }

    private static boolean isClientAbort(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String name = t.getClass().getName();
            if (name.endsWith("ClientAbortException") || name.endsWith("EofException")) {
                return true;
            }
            String msg = t.getMessage();
            if (msg != null) {
                String lower = msg.toLowerCase();
                if (lower.contains("broken pipe")
                        || lower.contains("connection reset")
                        || msg.contains("远程主机强迫关闭")
                        || msg.contains("你的主机中的软件中止了一个已建立的连接")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String md5Of(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] data = java.nio.file.Files.readAllBytes(file.toPath());
        byte[] digest = md.digest(data);
        StringBuilder sb = new StringBuilder(32);
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
