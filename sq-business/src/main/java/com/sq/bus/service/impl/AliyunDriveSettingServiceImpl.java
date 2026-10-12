package com.sq.bus.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sq.bus.config.AlbumProperties;
import com.sq.bus.constants.AlbumDeleted;
import com.sq.bus.domain.BizAlbum;
import com.sq.bus.domain.BizAliyunDriveSetting;
import com.sq.bus.domain.vo.RemoteAlbumItem;
import com.sq.bus.domain.vo.AliyunDriveSettingVo;
import com.sq.bus.mapper.BizAliyunDriveSettingMapper;
import com.sq.bus.service.IAliyunDriveSettingService;
import com.sq.bus.service.IBizAlbumService;
import com.sq.bus.service.cloud.AliyunDriveClient;
import com.sq.bus.service.cloud.AliyunDriveSyncService;
import com.sq.common.exception.ServiceException;
import com.sq.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AliyunDriveSettingServiceImpl
        extends ServiceImpl<BizAliyunDriveSettingMapper, BizAliyunDriveSetting>
        implements IAliyunDriveSettingService {

    private static final Logger log = LoggerFactory.getLogger(AliyunDriveSettingServiceImpl.class);

    @Autowired
    private AlbumProperties albumProperties;

    @Autowired
    private IBizAlbumService albumService;

    @Lazy
    @Autowired(required = false)
    private AliyunDriveSyncService aliyunDriveSyncService;

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
        List<RemoteAlbumItem> excluded = normalizeExcludedAlbums(vo);
        vo.setExcludedAlbums(excluded);
        // 新逻辑：默认同步全部，不再保存白名单
        vo.setRemoteAlbums(Collections.<RemoteAlbumItem>emptyList());
        vo.setRemoteAlbumId("");
        vo.setRemoteAlbumName("");

        String basePath = normalizePath(nvl(vo.getLocalBasePath()));
        if (StringUtils.isEmpty(basePath)) {
            basePath = normalizePath(nvl(vo.getLocalPath()));
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
        row.setRemark(toRemarkJson(excluded));
        String incoming = vo.getRefreshToken() == null ? "" : vo.getRefreshToken().trim();
        boolean tokenUpdated = StringUtils.isNotEmpty(incoming) && !looksMasked(incoming);
        // 不再使用 token 文件，refresh_token 只存数据库
        row.setTokenFile("");

        // 页面粘贴新 token：校验并轮换后写入数据库
        if (tokenUpdated) {
            String verified = verifyAndRotateRefreshToken(incoming);
            row.setRefreshToken(verified);
        }

        row.setUpdateBy(updateBy);
        row.setUpdateTime(new Date());
        // 扫描目录在每次同步时按实际相册自动维护，保存时不预创建
        row.setScanPathId(null);

        saveOrUpdate(row);
        if (tokenUpdated && aliyunDriveSyncService != null) {
            aliyunDriveSyncService.adoptRefreshToken(row.getRefreshToken());
        }
    }

    /**
     * 用页面提交的 refresh_token 调阿里云盘刷新接口；成功则返回轮换后的新 token。
     */
    private String verifyAndRotateRefreshToken(String refreshToken) {
        AlbumProperties.AliyunDriveConfig cfg = getEffectiveConfig();
        try {
            AliyunDriveClient client = new AliyunDriveClient(cfg, refreshToken);
            client.ensureLogin();
            String rotated = client.getRefreshToken();
            if (StringUtils.isEmpty(rotated)) {
                throw new ServiceException("阿里云盘未返回新的 refresh_token，请重新从浏览器复制");
            }
            return rotated.trim();
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("refresh_token 无效或已失效，请重新登录阿里云盘并复制最新值：" + e.getMessage());
        }
    }

    @Override
    public List<RemoteAlbumItem> resolveAlbumsToSync(AlbumProperties.AliyunDriveConfig cfg) {
        if (cfg == null) {
            return Collections.emptyList();
        }
        List<Map<String, String>> remote = listRemoteAlbums(null);
        Set<String> excludedIds = new HashSet<String>();
        if (cfg.getExcludedAlbums() != null) {
            for (RemoteAlbumItem item : cfg.getExcludedAlbums()) {
                if (item != null && StringUtils.isNotEmpty(item.getAlbumId())) {
                    excludedIds.add(item.getAlbumId().trim());
                }
            }
        }
        List<RemoteAlbumItem> out = new ArrayList<RemoteAlbumItem>();
        for (Map<String, String> row : remote) {
            if (row == null) {
                continue;
            }
            String id = row.get("albumId");
            String name = row.get("name");
            if (StringUtils.isEmpty(id) || excludedIds.contains(id)) {
                continue;
            }
            RemoteAlbumItem item = new RemoteAlbumItem();
            item.setAlbumId(id);
            item.setName(StringUtils.isNotEmpty(name) ? name : id);
            out.add(item);
        }
        log.info("云盘同步目标相册 {} 个（已排除 {} 个）", out.size(), excludedIds.size());
        return out;
    }

    @Override
    public List<RemoteAlbumItem> listRemoteAlbumsFromConfig(AlbumProperties.AliyunDriveConfig cfg) {
        // 兼容旧调用：优先返回实时解析结果不可用时的配置列表
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
        try {
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
        } finally {
            // 刷新会使旧 refresh_token 立即失效，必须落盘，否则后续同步必失败
            persistRotatedRefreshToken(client.getRefreshToken());
        }
        return out;
    }

    @Override
    public void persistRotatedRefreshToken(String refreshToken) {
        if (StringUtils.isEmpty(refreshToken)) {
            return;
        }
        String token = refreshToken.trim();
        BizAliyunDriveSetting row = getByIdQuiet();
        if (row == null) {
            return;
        }
        boolean changed = !token.equals(nvl(row.getRefreshToken())) || StringUtils.isNotEmpty(row.getTokenFile());
        if (!changed) {
            return;
        }
        row.setRefreshToken(token);
        row.setTokenFile("");
        row.setUpdateTime(new Date());
        try {
            updateById(row);
        } catch (Exception e) {
            log.warn("回写库 refresh_token 失败：{}", e.getMessage());
        }
    }

    @Override
    public AlbumProperties.AliyunDriveConfig getEffectiveConfig() {
        AlbumProperties.AliyunDriveConfig yml = albumProperties.getAliyunDrive();
        AlbumProperties.AliyunDriveConfig cfg = copyCfg(yml);
        BizAliyunDriveSetting row = getByIdQuiet();
        if (row != null) {
            overlay(cfg, row);
        }
        // refresh_token 只从数据库读取，不再使用 token 文件
        cfg.setTokenFile("");
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
        // 废弃 token 文件：配置层始终为空，refresh_token 仅存库
        cfg.setTokenFile("");
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
        parseRemarkIntoCfg(cfg, row.getRemark());
    }

    /**
     * remark 新格式：{"excludedAlbums":[...]}；旧格式纯数组为白名单，升级后视为无排除（同步全部）。
     */
    private static void parseRemarkIntoCfg(AlbumProperties.AliyunDriveConfig cfg, String remark) {
        if (cfg == null) {
            return;
        }
        RemarkPayload payload = parseRemark(remark);
        cfg.setExcludedAlbums(payload.excludedAlbums);
        // 旧白名单不再作为同步目标
        cfg.setRemoteAlbums(Collections.<RemoteAlbumItem>emptyList());
    }

    private static String toRemarkJson(List<RemoteAlbumItem> excluded) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("excludedAlbums", excluded == null ? Collections.emptyList() : excluded);
        return JSON.toJSONString(payload);
    }

    private static RemarkPayload parseRemark(String remark) {
        RemarkPayload payload = new RemarkPayload();
        if (StringUtils.isEmpty(remark)) {
            return payload;
        }
        String text = remark.trim();
        try {
            if (text.startsWith("{")) {
                JSONObject obj = JSON.parseObject(text);
                if (obj != null) {
                    payload.excludedAlbums = normalizeAlbumList(
                            obj.getList("excludedAlbums", RemoteAlbumItem.class));
                }
                return payload;
            }
            if (text.startsWith("[")) {
                // 旧版白名单：升级后改为同步全部，排除列表为空
                return payload;
            }
        } catch (Exception e) {
            // ignore
        }
        return payload;
    }

    private static List<RemoteAlbumItem> normalizeAlbumList(List<RemoteAlbumItem> list) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        List<RemoteAlbumItem> out = new ArrayList<RemoteAlbumItem>();
        for (RemoteAlbumItem item : list) {
            if (item == null || StringUtils.isEmpty(item.getAlbumId())) {
                continue;
            }
            RemoteAlbumItem copy = new RemoteAlbumItem();
            copy.setAlbumId(item.getAlbumId().trim());
            copy.setName(StringUtils.isNotEmpty(item.getName()) ? item.getName().trim() : copy.getAlbumId());
            out.add(copy);
        }
        return out;
    }

    private static class RemarkPayload {
        private List<RemoteAlbumItem> excludedAlbums = Collections.emptyList();
    }

    private static AliyunDriveSettingVo toVo(AlbumProperties.AliyunDriveConfig cfg) {
        AliyunDriveSettingVo vo = new AliyunDriveSettingVo();
        vo.setEnabled(cfg.isEnabled());
        vo.setRemoteAlbumName(cfg.getRemoteAlbumName());
        vo.setRemoteAlbumId(cfg.getRemoteAlbumId());
        vo.setRemoteAlbums(Collections.<RemoteAlbumItem>emptyList());
        vo.setExcludedAlbums(cfg.getExcludedAlbums() == null
                ? Collections.<RemoteAlbumItem>emptyList()
                : cfg.getExcludedAlbums());
        vo.setLocalPath(cfg.getLocalPath());
        vo.setScanPathId(cfg.getScanPathId());
        vo.setTriggerScan(cfg.isTriggerScan());
        vo.setFullScan(cfg.isFullScan());
        vo.setTokenFile("");
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
        row.setTokenFile("");
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
        row.setRemoteAlbumName("");
        row.setRemoteAlbumId("");
        row.setLocalPath(nvl(vo.getLocalPath()));
        // scanPathId 由同步任务按相册自动维护
        row.setTriggerScan(vo.isTriggerScan() ? 1 : 0);
        row.setFullScan(vo.isFullScan() ? 1 : 0);
        row.setTokenFile("");
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
        if (vo.getExcludedAlbums() == null) {
            vo.setExcludedAlbums(Collections.<RemoteAlbumItem>emptyList());
        }
        // localPath 存的就是本机父目录
        vo.setLocalBasePath(normalizePath(nvl(vo.getLocalPath())));
    }

    private List<RemoteAlbumItem> normalizeExcludedAlbums(AliyunDriveSettingVo vo) {
        List<RemoteAlbumItem> source = vo.getExcludedAlbums();
        if (source == null || source.isEmpty()) {
            // 兼容旧前端仍提交 remoteAlbums 作为排除项的误用：忽略
            return Collections.emptyList();
        }
        return normalizeAlbumList(source);
    }

    private static String composeAlbumLocalPathStatic(String basePath, String albumName) {
        String base = normalizePath(basePath);
        String name = sanitizeFolderName(albumName);
        if (StringUtils.isEmpty(base)) {
            return name;
        }
        if (StringUtils.isEmpty(name)) {
            return base;
        }
        return base + File.separator + name;
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
