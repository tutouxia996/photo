package com.sq.bus.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sq.bus.config.AlbumProperties;
import com.sq.bus.constants.AlbumDeleted;
import com.sq.bus.domain.BizAlbum;
import com.sq.bus.domain.BizAliyunDriveSetting;
import com.sq.bus.domain.BizScanPath;
import com.sq.bus.domain.vo.RemoteAlbumItem;
import com.sq.bus.domain.vo.AliyunDriveSettingVo;
import com.sq.bus.mapper.BizAliyunDriveSettingMapper;
import com.sq.bus.service.IAliyunDriveSettingService;
import com.sq.bus.service.IBizAlbumService;
import com.sq.bus.service.IBizScanPathService;
import com.sq.bus.service.cloud.AliyunDriveClient;
import com.sq.common.exception.ServiceException;
import com.sq.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AliyunDriveSettingServiceImpl
        extends ServiceImpl<BizAliyunDriveSettingMapper, BizAliyunDriveSetting>
        implements IAliyunDriveSettingService {

    private static final Logger log = LoggerFactory.getLogger(AliyunDriveSettingServiceImpl.class);

    @Autowired
    private AlbumProperties albumProperties;

    @Autowired
    private IBizScanPathService scanPathService;

    @Autowired
    private IBizAlbumService albumService;

    @Override
    public AliyunDriveSettingVo getForPage() {
        AlbumProperties.AliyunDriveConfig cfg = getEffectiveConfig();
        BizAliyunDriveSetting row = getByIdQuiet();
        AliyunDriveSettingVo vo = toVo(cfg);
        vo.setSource(row == null ? "yml" : "db");
        String token = cfg.getRefreshToken();
        boolean has = StringUtils.isNotEmpty(token);
        vo.setHasRefreshToken(has);
        vo.setRefreshTokenMasked(maskToken(token));
        vo.setRefreshToken("");
        fillPathFields(vo);
        return vo;
    }

    @Override
    public void saveFromPage(AliyunDriveSettingVo vo, String updateBy) {
        if (vo == null) {
            throw new ServiceException("配置不能为空");
        }
        List<RemoteAlbumItem> albums = normalizeRemoteAlbums(vo);
        if (albums.isEmpty()) {
            throw new ServiceException("请至少选择一个云盘相册");
        }
        vo.setRemoteAlbums(albums);
        RemoteAlbumItem first = albums.get(0);
        vo.setRemoteAlbumId(first.getAlbumId());
        vo.setRemoteAlbumName(first.getName());

        String basePath = normalizePath(nvl(vo.getLocalBasePath()));
        if (StringUtils.isEmpty(basePath)) {
            basePath = inferBasePathFromLegacy(nvl(vo.getLocalPath()), albums);
        }
        if (StringUtils.isEmpty(basePath)) {
            throw new ServiceException("本机父目录不能为空");
        }
        vo.setLocalPath(basePath);

        BizAliyunDriveSetting row = getById(BizAliyunDriveSetting.SINGLE_ID);
        if (row == null) {
            row = fromYml(albumProperties.getAliyunDrive());
            row.setId(BizAliyunDriveSetting.SINGLE_ID);
        }
        applyVo(row, vo);
        row.setRemark(JSON.toJSONString(albums));
        String incoming = vo.getRefreshToken() == null ? "" : vo.getRefreshToken().trim();
        if (StringUtils.isEmpty(incoming) || looksMasked(incoming)) {
            // 保留库中 / yml 原 token
        } else {
            row.setRefreshToken(incoming);
        }
        if (StringUtils.isEmpty(row.getTokenFile())) {
            AlbumProperties.AliyunDriveConfig yml = albumProperties.getAliyunDrive();
            row.setTokenFile(yml == null ? "D:/uploadPath/album/aliyun-drive-token.json" : yml.getTokenFile());
        }
        row.setUpdateBy(updateBy);
        row.setUpdateTime(new Date());

        if (vo.isTriggerScan()) {
            row.setScanPathId(null);
            for (RemoteAlbumItem album : albums) {
                Long bindAlbumId = resolveOrCreateLocalAlbumId(album.getName(), updateBy);
                String albumLocalPath = composeAlbumLocalPath(basePath, album.getName());
                scanPathService.upsertScanPathForSync(albumLocalPath, bindAlbumId, album.getName());
            }
        } else {
            row.setScanPathId(null);
        }

        saveOrUpdate(row);

        if (StringUtils.isNotEmpty(row.getRefreshToken())) {
            writeTokenFile(row.getTokenFile(), row.getRefreshToken(), albums);
        } else {
            writeSyncAlbumsToTokenFile(row.getTokenFile(), albums);
        }
    }

    @Override
    public List<RemoteAlbumItem> listRemoteAlbumsFromConfig(AlbumProperties.AliyunDriveConfig cfg) {
        if (cfg == null) {
            return Collections.emptyList();
        }
        if (cfg.getRemoteAlbums() != null && !cfg.getRemoteAlbums().isEmpty()) {
            return cfg.getRemoteAlbums();
        }
        if (StringUtils.isNotEmpty(cfg.getRemoteAlbumId()) || StringUtils.isNotEmpty(cfg.getRemoteAlbumName())) {
            RemoteAlbumItem one = new RemoteAlbumItem();
            one.setAlbumId(nvl(cfg.getRemoteAlbumId()));
            one.setName(nvl(cfg.getRemoteAlbumName()));
            if (StringUtils.isEmpty(one.getName())) {
                one.setName(one.getAlbumId());
            }
            return Collections.singletonList(one);
        }
        return Collections.emptyList();
    }

    @Override
    public Long resolveOrCreateLocalAlbumId(String albumName, String updateBy) {
        String name = nvl(albumName);
        if (StringUtils.isEmpty(name)) {
            throw new ServiceException("云盘相册名称为空，无法创建本地相册");
        }
        BizAlbum existing = findAlbumByName(name);
        if (existing != null) {
            return existing.getAlbumId();
        }
        BizAlbum created = new BizAlbum();
        created.setAlbumName(name);
        created.setIsPublic(1);
        created.setPhotoCount(0);
        created.setSortOrder(0);
        created.setDeleted(AlbumDeleted.NORMAL);
        created.setCreateBy(updateBy);
        created.setCreateTime(new Date());
        albumService.save(created);
        log.info("云盘同步自动创建本地相册 albumId={} name={}", created.getAlbumId(), name);
        return created.getAlbumId();
    }

    @Override
    public String composeAlbumLocalPath(String basePath, String albumName) {
        return composeAlbumLocalPathStatic(basePath, albumName);
    }

    @Override
    public List<Map<String, String>> listRemoteAlbums(String refreshToken) {
        AlbumProperties.AliyunDriveConfig cfg = getEffectiveConfig();
        String token = StringUtils.isNotEmpty(refreshToken) ? refreshToken.trim() : cfg.getRefreshToken();
        if (StringUtils.isEmpty(token) || looksMasked(token)) {
            throw new ServiceException("请先填写并保存 refresh_token，或在刷新相册列表时传入新 token");
        }
        AliyunDriveClient client = new AliyunDriveClient(cfg, token);
        client.ensureLogin();
        List<Map<String, String>> out = new ArrayList<Map<String, String>>();
        for (JSONObject item : client.listAlbums()) {
            if (item == null) {
                continue;
            }
            String name = item.getString("name");
            String id = item.getString("album_id");
            if (StringUtils.isEmpty(id)) {
                id = item.getString("albumId");
            }
            if (StringUtils.isEmpty(name) || StringUtils.isEmpty(id)) {
                continue;
            }
            Map<String, String> row = new LinkedHashMap<String, String>();
            row.put("albumId", id);
            row.put("name", name);
            out.add(row);
        }
        return out;
    }

    @Override
    public AlbumProperties.AliyunDriveConfig getEffectiveConfig() {
        AlbumProperties.AliyunDriveConfig yml = albumProperties.getAliyunDrive();
        AlbumProperties.AliyunDriveConfig cfg = copyCfg(yml);
        BizAliyunDriveSetting row = getByIdQuiet();
        if (row != null) {
            overlay(cfg, row);
        }
        return cfg;
    }

    private BizAliyunDriveSetting getByIdQuiet() {
        try {
            return getById(BizAliyunDriveSetting.SINGLE_ID);
        } catch (Exception e) {
            log.warn("读取 biz_aliyun_drive_setting 失败（请先执行 doc/db/album_add_aliyun_drive_setting.sql）：{}", e.getMessage());
            return null;
        }
    }

    private static AlbumProperties.AliyunDriveConfig copyCfg(AlbumProperties.AliyunDriveConfig src) {
        if (src == null) {
            return new AlbumProperties.AliyunDriveConfig();
        }
        return JSON.parseObject(JSON.toJSONString(src), AlbumProperties.AliyunDriveConfig.class);
    }

    private static void overlay(AlbumProperties.AliyunDriveConfig cfg, BizAliyunDriveSetting row) {
        if (row.getEnabled() != null) {
            cfg.setEnabled(row.getEnabled() == 1);
        }
        if (row.getRefreshToken() != null) {
            cfg.setRefreshToken(row.getRefreshToken());
        }
        if (row.getRemoteAlbumName() != null) {
            cfg.setRemoteAlbumName(row.getRemoteAlbumName());
        }
        if (row.getRemoteAlbumId() != null) {
            cfg.setRemoteAlbumId(row.getRemoteAlbumId());
        }
        if (row.getLocalPath() != null) {
            cfg.setLocalPath(row.getLocalPath());
        }
        cfg.setScanPathId(row.getScanPathId());
        if (row.getTriggerScan() != null) {
            cfg.setTriggerScan(row.getTriggerScan() == 1);
        }
        if (row.getFullScan() != null) {
            cfg.setFullScan(row.getFullScan() == 1);
        }
        if (StringUtils.isNotEmpty(row.getTokenFile())) {
            cfg.setTokenFile(row.getTokenFile());
        }
        if (row.getConnectTimeoutMs() != null) {
            cfg.setConnectTimeoutMs(row.getConnectTimeoutMs());
        }
        if (row.getReadTimeoutMs() != null) {
            cfg.setReadTimeoutMs(row.getReadTimeoutMs());
        }
        if (row.getDownloadTimeoutMs() != null) {
            cfg.setDownloadTimeoutMs(row.getDownloadTimeoutMs());
        }
        if (row.getMediaOnly() != null) {
            cfg.setMediaOnly(row.getMediaOnly() == 1);
        }
        if (row.getDownloadConcurrency() != null) {
            cfg.setDownloadConcurrency(row.getDownloadConcurrency());
        }
        if (row.getDownloadReferer() != null) {
            cfg.setDownloadReferer(row.getDownloadReferer());
        }
        if (row.getChunkConcurrency() != null) {
            cfg.setChunkConcurrency(row.getChunkConcurrency());
        }
        if (row.getMultipartMinBytes() != null) {
            cfg.setMultipartMinBytes(row.getMultipartMinBytes());
        }
        parseRemoteAlbumsIntoCfg(cfg, row.getRemark());
    }

    private static void parseRemoteAlbumsIntoCfg(AlbumProperties.AliyunDriveConfig cfg, String remark) {
        if (cfg == null) {
            return;
        }
        List<RemoteAlbumItem> parsed = parseAlbumsJson(remark);
        if (!parsed.isEmpty()) {
            cfg.setRemoteAlbums(parsed);
            RemoteAlbumItem first = parsed.get(0);
            cfg.setRemoteAlbumId(first.getAlbumId());
            cfg.setRemoteAlbumName(first.getName());
        }
    }

    private static List<RemoteAlbumItem> parseAlbumsJson(String remark) {
        if (StringUtils.isEmpty(remark)) {
            return Collections.emptyList();
        }
        String text = remark.trim();
        if (!text.startsWith("[")) {
            return Collections.emptyList();
        }
        try {
            List<RemoteAlbumItem> list = JSON.parseArray(text, RemoteAlbumItem.class);
            if (list == null || list.isEmpty()) {
                return Collections.emptyList();
            }
            List<RemoteAlbumItem> out = new ArrayList<RemoteAlbumItem>();
            for (RemoteAlbumItem item : list) {
                if (item == null || StringUtils.isEmpty(item.getAlbumId())) {
                    continue;
                }
                if (StringUtils.isEmpty(item.getName())) {
                    item.setName(item.getAlbumId());
                }
                out.add(item);
            }
            return out;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private static List<RemoteAlbumItem> parseAlbumsFromCfg(AlbumProperties.AliyunDriveConfig cfg) {
        if (cfg == null) {
            return Collections.emptyList();
        }
        if (cfg.getRemoteAlbums() != null && !cfg.getRemoteAlbums().isEmpty()) {
            return cfg.getRemoteAlbums();
        }
        if (StringUtils.isNotEmpty(cfg.getRemoteAlbumId()) || StringUtils.isNotEmpty(cfg.getRemoteAlbumName())) {
            RemoteAlbumItem one = new RemoteAlbumItem();
            one.setAlbumId(nvl(cfg.getRemoteAlbumId()));
            one.setName(nvl(cfg.getRemoteAlbumName()));
            if (StringUtils.isEmpty(one.getName())) {
                one.setName(one.getAlbumId());
            }
            return Collections.singletonList(one);
        }
        return Collections.emptyList();
    }

    private static AliyunDriveSettingVo toVo(AlbumProperties.AliyunDriveConfig cfg) {
        AliyunDriveSettingVo vo = new AliyunDriveSettingVo();
        vo.setEnabled(cfg.isEnabled());
        vo.setRemoteAlbumName(cfg.getRemoteAlbumName());
        vo.setRemoteAlbumId(cfg.getRemoteAlbumId());
        vo.setRemoteAlbums(parseAlbumsFromCfg(cfg));
        vo.setLocalPath(cfg.getLocalPath());
        vo.setScanPathId(cfg.getScanPathId());
        vo.setTriggerScan(cfg.isTriggerScan());
        vo.setFullScan(cfg.isFullScan());
        vo.setTokenFile(cfg.getTokenFile());
        vo.setConnectTimeoutMs(cfg.getConnectTimeoutMs());
        vo.setReadTimeoutMs(cfg.getReadTimeoutMs());
        vo.setDownloadTimeoutMs(cfg.getDownloadTimeoutMs());
        vo.setMediaOnly(cfg.isMediaOnly());
        vo.setDownloadConcurrency(cfg.getDownloadConcurrency());
        vo.setDownloadReferer(cfg.getDownloadReferer());
        vo.setChunkConcurrency(cfg.getChunkConcurrency());
        vo.setMultipartMinBytes(cfg.getMultipartMinBytes());
        return vo;
    }

    private static BizAliyunDriveSetting fromYml(AlbumProperties.AliyunDriveConfig cfg) {
        BizAliyunDriveSetting row = new BizAliyunDriveSetting();
        if (cfg == null) {
            return row;
        }
        row.setEnabled(cfg.isEnabled() ? 1 : 0);
        row.setRefreshToken(cfg.getRefreshToken());
        row.setRemoteAlbumName(cfg.getRemoteAlbumName());
        row.setRemoteAlbumId(cfg.getRemoteAlbumId());
        row.setLocalPath(cfg.getLocalPath());
        row.setScanPathId(cfg.getScanPathId());
        row.setTriggerScan(cfg.isTriggerScan() ? 1 : 0);
        row.setFullScan(cfg.isFullScan() ? 1 : 0);
        row.setTokenFile(cfg.getTokenFile());
        row.setConnectTimeoutMs(cfg.getConnectTimeoutMs());
        row.setReadTimeoutMs(cfg.getReadTimeoutMs());
        row.setDownloadTimeoutMs(cfg.getDownloadTimeoutMs());
        row.setMediaOnly(cfg.isMediaOnly() ? 1 : 0);
        row.setDownloadConcurrency(cfg.getDownloadConcurrency());
        row.setDownloadReferer(cfg.getDownloadReferer());
        row.setChunkConcurrency(cfg.getChunkConcurrency());
        row.setMultipartMinBytes(cfg.getMultipartMinBytes());
        return row;
    }

    private static void applyVo(BizAliyunDriveSetting row, AliyunDriveSettingVo vo) {
        row.setEnabled(vo.isEnabled() ? 1 : 0);
        row.setRemoteAlbumName(nvl(vo.getRemoteAlbumName()));
        row.setRemoteAlbumId(nvl(vo.getRemoteAlbumId()));
        row.setLocalPath(nvl(vo.getLocalPath()));
        // scanPathId 仅由保存逻辑根据 bindAlbumId 自动写入，不接受页面旧值（如历史 ID 41）
        row.setTriggerScan(vo.isTriggerScan() ? 1 : 0);
        row.setFullScan(vo.isFullScan() ? 1 : 0);
        if (StringUtils.isNotEmpty(vo.getTokenFile())) {
            row.setTokenFile(vo.getTokenFile().trim());
        }
        if (vo.getConnectTimeoutMs() != null) {
            row.setConnectTimeoutMs(vo.getConnectTimeoutMs());
        }
        if (vo.getReadTimeoutMs() != null) {
            row.setReadTimeoutMs(vo.getReadTimeoutMs());
        }
        if (vo.getDownloadTimeoutMs() != null) {
            row.setDownloadTimeoutMs(vo.getDownloadTimeoutMs());
        }
        row.setMediaOnly(vo.isMediaOnly() ? 1 : 0);
        if (vo.getDownloadConcurrency() != null) {
            row.setDownloadConcurrency(Math.max(1, vo.getDownloadConcurrency()));
        }
        if (vo.getDownloadReferer() != null) {
            row.setDownloadReferer(vo.getDownloadReferer().trim());
        }
        if (vo.getChunkConcurrency() != null) {
            row.setChunkConcurrency(Math.max(1, vo.getChunkConcurrency()));
        }
        if (vo.getMultipartMinBytes() != null) {
            row.setMultipartMinBytes(Math.max(0L, vo.getMultipartMinBytes()));
        }
    }

    private static void writeTokenFile(String tokenFile, String refreshToken, List<RemoteAlbumItem> albums) {
        if (StringUtils.isEmpty(tokenFile)) {
            return;
        }
        try {
            Path path = Paths.get(tokenFile);
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            JSONObject obj = new JSONObject();
            if (Files.isRegularFile(path)) {
                try {
                    JSONObject old = JSON.parseObject(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
                    if (old != null) {
                        obj = old;
                    }
                } catch (Exception ignored) {
                    // ignore
                }
            }
            if (StringUtils.isNotEmpty(refreshToken)) {
                obj.put("refreshToken", refreshToken);
            }
            if (obj.getJSONObject("files") == null) {
                obj.put("files", new JSONObject());
            }
            if (albums != null && !albums.isEmpty()) {
                obj.put("syncAlbums", albums);
            }
            Files.write(path, obj.toJSONString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("写入 tokenFile 失败 {}: {}", tokenFile, e.getMessage());
        }
    }

    private static void writeSyncAlbumsToTokenFile(String tokenFile, List<RemoteAlbumItem> albums) {
        writeTokenFile(tokenFile, null, albums);
    }

    private static String maskToken(String token) {
        if (StringUtils.isEmpty(token)) {
            return "";
        }
        String t = token.trim();
        if (t.length() <= 8) {
            return "********";
        }
        return t.substring(0, 4) + "********" + t.substring(t.length() - 4);
    }

    private static boolean looksMasked(String token) {
        return token.contains("*") || token.contains("…") || token.contains("...");
    }

    private static String nvl(String s) {
        return s == null ? "" : s.trim();
    }

    private void fillPathFields(AliyunDriveSettingVo vo) {
        if (vo == null) {
            return;
        }
        List<RemoteAlbumItem> albums = vo.getRemoteAlbums();
        if (albums == null) {
            albums = Collections.emptyList();
        }
        vo.setLocalBasePath(normalizePath(nvl(vo.getLocalPath())));
        if (albums.size() == 1) {
            vo.setLocalBasePath(splitLocalBasePath(nvl(vo.getLocalPath()), albums.get(0).getName()));
        }
        if (albums.size() == 1 && vo.getBindAlbumId() == null) {
            BizAlbum matched = findAlbumByName(albums.get(0).getName());
            if (matched != null) {
                vo.setBindAlbumId(matched.getAlbumId());
            }
        }
    }

    private List<RemoteAlbumItem> normalizeRemoteAlbums(AliyunDriveSettingVo vo) {
        List<RemoteAlbumItem> list = new ArrayList<RemoteAlbumItem>();
        if (vo.getRemoteAlbums() != null) {
            for (RemoteAlbumItem item : vo.getRemoteAlbums()) {
                if (item == null || StringUtils.isEmpty(item.getAlbumId())) {
                    continue;
                }
                RemoteAlbumItem copy = new RemoteAlbumItem();
                copy.setAlbumId(item.getAlbumId().trim());
                copy.setName(StringUtils.isNotEmpty(item.getName()) ? item.getName().trim() : copy.getAlbumId());
                list.add(copy);
            }
        }
        if (list.isEmpty() && StringUtils.isNotEmpty(vo.getRemoteAlbumId())) {
            RemoteAlbumItem one = new RemoteAlbumItem();
            one.setAlbumId(vo.getRemoteAlbumId().trim());
            one.setName(nvl(vo.getRemoteAlbumName()));
            if (StringUtils.isEmpty(one.getName())) {
                one.setName(one.getAlbumId());
            }
            list.add(one);
        }
        return list;
    }

    private static String inferBasePathFromLegacy(String localPath, List<RemoteAlbumItem> albums) {
        String path = normalizePath(localPath);
        if (StringUtils.isEmpty(path) || albums == null || albums.isEmpty()) {
            return path;
        }
        if (albums.size() == 1) {
            return splitLocalBasePathStatic(path, albums.get(0).getName());
        }
        return path;
    }

    private static String composeAlbumLocalPathStatic(String basePath, String albumName) {
        String base = normalizePathStatic(basePath);
        String name = sanitizeFolderName(albumName);
        if (StringUtils.isEmpty(base)) {
            return name;
        }
        if (StringUtils.isEmpty(name)) {
            return base;
        }
        return base + File.separator + name;
    }

    private static String splitLocalBasePathStatic(String localPath, String albumName) {
        return splitLocalBasePath(localPath, albumName);
    }

    private static String normalizePathStatic(String path) {
        return normalizePath(path);
    }

    private BizAlbum findAlbumByName(String albumName) {
        if (StringUtils.isEmpty(albumName)) {
            return null;
        }
        return albumService.getOne(new LambdaQueryWrapper<BizAlbum>()
                .eq(BizAlbum::getAlbumName, albumName.trim())
                .eq(BizAlbum::getDeleted, AlbumDeleted.NORMAL)
                .orderByDesc(BizAlbum::getAlbumId)
                .last("LIMIT 1"), false);
    }

    private static String splitLocalBasePath(String localPath, String albumName) {
        String path = normalizePath(localPath);
        if (StringUtils.isEmpty(path)) {
            return "";
        }
        if (StringUtils.isNotEmpty(albumName)) {
            String suffix = File.separator + sanitizeFolderName(albumName);
            if (path.endsWith(suffix)) {
                return path.substring(0, path.length() - suffix.length());
            }
        }
        int idx = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return idx > 0 ? path.substring(0, idx) : path;
    }

    private static String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        String p = path.trim().replace('/', File.separatorChar).replace('\\', File.separatorChar);
        while (p.endsWith(File.separator)) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

    private static String sanitizeFolderName(String name) {
        if (name == null) {
            return "";
        }
        return name.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
