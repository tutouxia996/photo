package com.sq.bus.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sq.bus.config.AlbumProperties;
import com.sq.bus.domain.BizAliyunDriveSetting;
import com.sq.bus.domain.vo.AliyunDriveSettingVo;
import com.sq.bus.mapper.BizAliyunDriveSettingMapper;
import com.sq.bus.service.IAliyunDriveSettingService;
import com.sq.common.exception.ServiceException;
import com.sq.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;

@Service
public class AliyunDriveSettingServiceImpl
        extends ServiceImpl<BizAliyunDriveSettingMapper, BizAliyunDriveSetting>
        implements IAliyunDriveSettingService {

    private static final Logger log = LoggerFactory.getLogger(AliyunDriveSettingServiceImpl.class);

    @Autowired
    private AlbumProperties albumProperties;

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
        return vo;
    }

    @Override
    public void saveFromPage(AliyunDriveSettingVo vo, String updateBy) {
        if (vo == null) {
            throw new ServiceException("配置不能为空");
        }
        if (StringUtils.isEmpty(vo.getLocalPath())) {
            throw new ServiceException("本机下载目录不能为空");
        }
        if (StringUtils.isEmpty(vo.getRemoteAlbumName()) && StringUtils.isEmpty(vo.getRemoteAlbumId())) {
            throw new ServiceException("请填写云盘相册名称或相册 ID");
        }

        BizAliyunDriveSetting row = getById(BizAliyunDriveSetting.SINGLE_ID);
        if (row == null) {
            row = fromYml(albumProperties.getAliyunDrive());
            row.setId(BizAliyunDriveSetting.SINGLE_ID);
        }
        applyVo(row, vo);
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
        saveOrUpdate(row);

        if (StringUtils.isNotEmpty(row.getRefreshToken())) {
            writeTokenFile(row.getTokenFile(), row.getRefreshToken());
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
    }

    private static AliyunDriveSettingVo toVo(AlbumProperties.AliyunDriveConfig cfg) {
        AliyunDriveSettingVo vo = new AliyunDriveSettingVo();
        vo.setEnabled(cfg.isEnabled());
        vo.setRemoteAlbumName(cfg.getRemoteAlbumName());
        vo.setRemoteAlbumId(cfg.getRemoteAlbumId());
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
        row.setScanPathId(vo.getScanPathId());
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

    private static void writeTokenFile(String tokenFile, String refreshToken) {
        if (StringUtils.isEmpty(tokenFile) || StringUtils.isEmpty(refreshToken)) {
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
            obj.put("refreshToken", refreshToken);
            if (obj.getJSONObject("files") == null) {
                obj.put("files", new JSONObject());
            }
            Files.write(path, obj.toJSONString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("写入 tokenFile 失败 {}: {}", tokenFile, e.getMessage());
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
}
