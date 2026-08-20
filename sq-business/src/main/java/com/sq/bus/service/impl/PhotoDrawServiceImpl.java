package com.sq.bus.service.impl;

import com.sq.bus.config.AlbumProperties;
import com.sq.bus.constants.AlbumDeleted;
import com.sq.bus.domain.BizPhoto;
import com.sq.bus.domain.BizPhotoDraw;
import com.sq.bus.domain.BizPhotoDrawPreset;
import com.sq.bus.domain.vo.PhotoDrawBatchRequest;
import com.sq.bus.domain.vo.PhotoDrawRequest;
import com.sq.bus.mapper.BizPhotoDrawMapper;
import com.sq.bus.service.IBizAlbumService;
import com.sq.bus.service.IBizPhotoDrawPresetService;
import com.sq.bus.service.IBizPhotoService;
import com.sq.bus.service.IPhotoDrawService;
import com.sq.bus.service.IPhotoQualityService;
import com.sq.bus.service.ai.WanxiangImg2ImgClient;
import com.sq.bus.service.draw.PhotoDrawCompositor;
import com.sq.bus.service.draw.PhotoDrawLayout;
import com.sq.bus.utils.ImageEncodeUtils;
import com.sq.bus.utils.PhotoFieldUtils;
import com.sq.bus.utils.ThumbUtils;
import com.sq.common.exception.ServiceException;
import com.sq.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class PhotoDrawServiceImpl implements IPhotoDrawService {

    private static final Logger log = LoggerFactory.getLogger(PhotoDrawServiceImpl.class);

    @Autowired
    private IBizPhotoService photoService;

    @Autowired
    private IBizAlbumService albumService;

    @Autowired
    private IPhotoQualityService photoQualityService;

    @Autowired
    private WanxiangImg2ImgClient wanxiangClient;

    @Autowired
    private BizPhotoDrawMapper photoDrawMapper;

    @Autowired
    private AlbumProperties albumProperties;

    @Autowired
    private IBizPhotoDrawPresetService drawPresetService;

    @Override
    public List<Map<String, Object>> listPresets() {
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        for (BizPhotoDrawPreset p : drawPresetService.listEnabled()) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("id", p.getPresetKey());
            row.put("label", p.getLabel());
            row.put("layout", p.getLayout());
            list.add(row);
        }
        return list;
    }

    @Override
    public Map<String, Object> drawPhoto(Long photoId, PhotoDrawRequest request, String operator) {
        ensureEnabled();
        BizPhoto source = photoService.getById(photoId);
        if (source == null || source.getDeleted() == null || source.getDeleted() != AlbumDeleted.NORMAL) {
            throw new ServiceException("照片不存在");
        }
        if ("ai_draw".equals(source.getOriginType())) {
            throw new ServiceException("AI 创作图不能再次出图，请选择原片");
        }
        if (!photoQualityService.isEligibleForDraw(source)) {
            throw new ServiceException("该照片未通过出图质量打分，请先打分并确保合格（默认≥70分）");
        }
        BizPhotoDrawPreset preset = drawPresetService.getEnabledByKey(request == null ? null : request.getPreset());
        if (preset == null) {
            throw new ServiceException("无效出图预设，请从列表中选择");
        }
        File originFile = resolveFile(source);
        if (originFile == null) {
            throw new ServiceException("找不到原图文件");
        }

        Date now = new Date();
        BizPhotoDraw record = new BizPhotoDraw();
        record.setAlbumId(source.getAlbumId());
        record.setSourcePhotoId(source.getPhotoId());
        record.setPreset(preset.getPresetKey());
        record.setStatus("running");
        record.setCreateBy(operator);
        record.setCreateTime(now);
        record.setUpdateTime(now);
        photoDrawMapper.insert(record);

        try {
            AlbumProperties.PhotoDrawConfig cfg = drawCfg();
            String dataUrl = ImageEncodeUtils.toDataUrlJpeg(originFile, cfg.getMaxInputEdge());
            String panelSize = StringUtils.isEmpty(preset.getPanelSize()) ? "960*1280" : preset.getPanelSize();
            byte[] panelBytes = wanxiangClient.generatePanel(dataUrl, preset.getPanelPrompt(), panelSize);
            BufferedImage original = PhotoDrawCompositor.readImage(originFile);
            BufferedImage panel = PhotoDrawCompositor.readImage(panelBytes);

            String title = buildTitle(source, request);
            String subtitle = buildSubtitle(source, request);
            String keywords = buildKeywords(source, request);
            PhotoDrawLayout layout = resolveLayout(preset.getLayout());
            boolean gradePhoto = preset.getGradePhoto() == null || preset.getGradePhoto() != 0;
            BufferedImage poster = PhotoDrawCompositor.compose(
                    layout, gradePhoto, original, panel, title, subtitle, keywords);

            BizPhoto saved = savePoster(source, preset, poster, operator, now);
            record.setResultPhotoId(saved.getPhotoId());
            record.setStatus("success");
            record.setUpdateTime(new Date());
            photoDrawMapper.updateById(record);
            albumService.refreshAlbumStats(source.getAlbumId());

            Map<String, Object> result = new HashMap<String, Object>();
            result.put("drawId", record.getDrawId());
            result.put("sourcePhotoId", source.getPhotoId());
            result.put("preset", preset.getPresetKey());
            result.put("presetLabel", preset.getLabel());
            result.put("photo", saved);
            log.info("photo draw ok source={} result={} preset={}", source.getPhotoId(), saved.getPhotoId(), preset.getPresetKey());
            return result;
        } catch (Exception e) {
            record.setStatus("failed");
            record.setErrorMsg(truncate(e.getMessage(), 480));
            record.setUpdateTime(new Date());
            photoDrawMapper.updateById(record);
            if (e instanceof ServiceException) {
                throw (ServiceException) e;
            }
            throw new ServiceException("出图失败：" + e.getMessage());
        }
    }

    @Override
    public Map<String, Object> drawPhotoBatch(Long albumId, PhotoDrawBatchRequest request, String operator) {
        if (request == null || request.getPhotoIds() == null || request.getPhotoIds().isEmpty()) {
            throw new ServiceException("请选择要出图的照片");
        }
        int success = 0;
        int failed = 0;
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (Long photoId : request.getPhotoIds()) {
            if (photoId == null) {
                continue;
            }
            PhotoDrawRequest single = new PhotoDrawRequest();
            single.setPreset(request.getPreset());
            single.setTitle(request.getTitle());
            single.setSubtitle(request.getSubtitle());
            single.setKeywords(request.getKeywords());
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("photoId", photoId);
            try {
                Map<String, Object> one = drawPhoto(photoId, single, operator);
                row.put("ok", true);
                row.put("result", one);
                success++;
            } catch (Exception e) {
                row.put("ok", false);
                row.put("error", truncate(e.getMessage(), 200));
                failed++;
            }
            items.add(row);
        }
        Map<String, Object> summary = new HashMap<String, Object>();
        summary.put("albumId", albumId);
        summary.put("success", success);
        summary.put("failed", failed);
        summary.put("total", request.getPhotoIds().size());
        summary.put("items", items);
        return summary;
    }

    private BizPhoto savePoster(BizPhoto source, BizPhotoDrawPreset preset, BufferedImage poster,
                                  String operator, Date now) throws Exception {
        String datePath = new SimpleDateFormat("yyyy/MM/dd").format(now);
        File dir = new File(albumProperties.getUploadPath(), "ai-draw/" + datePath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String baseName = source.getPhotoId() + "_" + preset.getPresetKey() + "_" + System.currentTimeMillis() + ".jpg";
        File dest = new File(dir, baseName);
        float quality = drawCfg().getJpegQuality() > 0 ? drawCfg().getJpegQuality() : 0.95f;
        ImageEncodeUtils.writeJpeg(poster, dest, quality);
        log.info("photo draw saved {}x{} {} bytes", poster.getWidth(), poster.getHeight(), dest.length());

        String rel = "ai-draw/" + datePath + "/" + baseName;
        String fileUrl = "/album/files/upload/" + rel;
        String thumbUrl = createThumb(dest, datePath, baseName);

        BizPhoto photo = new BizPhoto();
        photo.setAlbumId(source.getAlbumId());
        photo.setFileName("AI-" + preset.getLabel() + "-" + safeStem(source.getFileName()) + ".jpg");
        photo.setFilePath(dest.getAbsolutePath());
        photo.setFileUrl(fileUrl);
        photo.setThumbUrl(thumbUrl);
        photo.setFileSize(dest.length());
        photo.setFileType(1);
        photo.setShootTime(source.getShootTime());
        photo.setLatitude(source.getLatitude());
        photo.setLongitude(source.getLongitude());
        photo.setLocationSource(source.getLocationSource());
        photo.setLocationConfidence(source.getLocationConfidence());
        photo.setAddress(source.getAddress());
        photo.setProvince(source.getProvince());
        photo.setCity(source.getCity());
        photo.setDistrict(source.getDistrict());
        photo.setSortOrder(0);
        photo.setRemark("AI出图:" + preset.getLabel() + ";源照片=" + source.getPhotoId());
        photo.setOriginType("ai_draw");
        photo.setSourcePhotoId(source.getPhotoId());
        photo.setDeleted(AlbumDeleted.NORMAL);
        photo.setCreateBy(operator);
        photo.setCreateTime(now);
        PhotoFieldUtils.clamp(photo);
        photoService.save(photo);
        return photo;
    }

    private PhotoDrawLayout resolveLayout(String layout) {
        if (StringUtils.isEmpty(layout)) {
            return PhotoDrawLayout.FULL_CANVAS;
        }
        try {
            return PhotoDrawLayout.valueOf(layout.trim());
        } catch (Exception e) {
            log.warn("unknown draw layout {}, fallback FULL_CANVAS", layout);
            return PhotoDrawLayout.FULL_CANVAS;
        }
    }

    private String createThumb(File dest, String datePath, String savedName) {
        try {
            File thumbDir = new File(albumProperties.getThumbPath(), datePath);
            String thumbName = "s_" + savedName;
            File thumbFile = new File(thumbDir, thumbName);
            ThumbUtils.createThumbnail(dest, thumbFile, albumProperties.getThumb().getSmallWidth());
            return "/album/files/thumb/" + datePath + "/" + thumbName;
        } catch (Exception e) {
            log.warn("draw thumb failed: {}", e.getMessage());
            return null;
        }
    }

    private String buildTitle(BizPhoto source, PhotoDrawRequest request) {
        if (request != null && StringUtils.isNotEmpty(request.getTitle())) {
            return request.getTitle().trim();
        }
        if (StringUtils.isNotEmpty(source.getCity())) {
            return source.getCity();
        }
        if (StringUtils.isNotEmpty(source.getDistrict())) {
            return source.getDistrict();
        }
        return safeStem(source.getFileName()).replace('_', ' ');
    }

    private String buildSubtitle(BizPhoto source, PhotoDrawRequest request) {
        if (request != null && StringUtils.isNotEmpty(request.getSubtitle())) {
            return request.getSubtitle().trim();
        }
        if (source.getShootTime() != null) {
            return new SimpleDateFormat("'a moment in' MMMM yyyy", Locale.ENGLISH).format(source.getShootTime());
        }
        return "quiet light on paper";
    }

    private String buildKeywords(BizPhoto source, PhotoDrawRequest request) {
        if (request != null && StringUtils.isNotEmpty(request.getKeywords())) {
            return request.getKeywords().trim();
        }
        List<String> parts = new ArrayList<String>();
        if (StringUtils.isNotEmpty(source.getCity())) {
            parts.add(source.getCity().toLowerCase(Locale.ROOT));
        }
        if (StringUtils.isNotEmpty(source.getDistrict())) {
            parts.add(source.getDistrict().toLowerCase(Locale.ROOT));
        }
        if (parts.isEmpty() && source.getShootTime() != null) {
            parts.add(new SimpleDateFormat("MMM yyyy", Locale.ENGLISH).format(source.getShootTime()).toLowerCase(Locale.ROOT));
        }
        while (parts.size() < 3) {
            parts.add("memory");
        }
        return parts.get(0) + " / " + parts.get(1) + " / " + parts.get(2);
    }

    private void ensureEnabled() {
        AlbumProperties.PhotoDrawConfig cfg = drawCfg();
        if (cfg != null && !cfg.isEnabled()) {
            throw new ServiceException("AI 出图未启用（album.photoDraw.enabled=false）");
        }
        if (!wanxiangClient.isConfigured()) {
            throw new ServiceException("未配置万相 API Key：请在 application-local.yml 设置 album.photoDraw.apiKey 或 AI_LANDMARK_API_KEY");
        }
    }

    private AlbumProperties.PhotoDrawConfig drawCfg() {
        return albumProperties == null ? new AlbumProperties.PhotoDrawConfig() : albumProperties.getPhotoDraw();
    }

    private File resolveFile(BizPhoto photo) {
        if (photo == null || StringUtils.isEmpty(photo.getFilePath())) {
            return null;
        }
        File f = new File(photo.getFilePath());
        return f.exists() && f.isFile() ? f : null;
    }

    private static String safeStem(String name) {
        if (name == null) {
            return "photo";
        }
        int idx = name.lastIndexOf('.');
        String stem = idx > 0 ? name.substring(0, idx) : name;
        if (stem.length() > 40) {
            stem = stem.substring(0, 40);
        }
        return stem;
    }

    private static String truncate(String msg, int max) {
        if (msg == null) {
            return null;
        }
        return msg.length() <= max ? msg : msg.substring(0, max);
    }
}
