package com.sq.bus.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 相册业务配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "album")
public class AlbumProperties {

    /** 兼容配置：扫描根目录列表（也可通过 biz_scan_path 管理） */
    private List<String> scanPaths = new ArrayList<String>();

    /** 上传文件存储根目录 */
    private String uploadPath = "D:/uploadPath/album/upload";

    /** 缩略图缓存目录 */
    private String thumbPath = "D:/uploadPath/album/cache/thumb";

    /** 本地文件访问根目录（资源映射） */
    private String localRoot = "D:/uploadPath/album";

    private Thumb thumb = new Thumb();

    private MapConfig map = new MapConfig();

    private GpxConfig gpx = new GpxConfig();

    /** 无 EXIF/GPX 时的坐标兜底（默认不进主轨迹/地图） */
    private FallbackLocationConfig fallbackLocation = new FallbackLocationConfig();

    @Data
    public static class Thumb {
        private int smallWidth = 300;
        private int mediumWidth = 800;
        private int largeWidth = 1920;
    }

    @Data
    public static class MapConfig {
        private String type = "amap";
        private String webKey = "";
        private String jsKey = "";
        /** 火车/高铁是否优先用 OSM 铁路网贴轨 */
        private boolean osmRailwayEnabled = true;
        /** Overpass API 地址（主） */
        private String overpassUrl = "https://overpass-api.de/api/interpreter";
        /**
         * Overpass HTTPS 是否信任所有证书。
         * 本机 Java 信任库缺证书时会出现 PKIX path building failed，学习/内网环境建议 true。
         */
        private boolean overpassTrustAllSsl = true;
        /** Overpass 结果本地缓存目录（空则用 java.io.tmpdir/album-osm-cache） */
        private String osmCacheDir = "";
        /** 火车车次/时刻表 */
        private TrainConfig train = new TrainConfig();
    }

    @Data
    public static class TrainConfig {
        /** 是否启用第三方车次查询（关闭时仍可用手工经停） */
        private boolean enabled = true;
        /** juhe | none */
        private String provider = "juhe";
        private String apiKey = "";
        /** 站到站班次查询（聚合火车订票 query） */
        private String queryUrl = "https://apis.juhe.cn/fapigw/train/query";
        /** 按车次查经停（聚合经典 train/s，可选） */
        private String detailUrl = "https://apis.juhe.cn/train/s";
    }

    @Data
    public static class GpxConfig {
        /** 媒体拍摄时间与 GPX 点匹配窗口（秒） */
        private int matchWindowSeconds = 10;
        /**
         * 将 GPX 时间（多为 UTC）平移到与 EXIF 本地时间对齐的小时数。
         * 中国常见为 8；若录制设备与照片时区已一致可设 0。
         */
        private int timeOffsetHours = 8;
        /** 抽稀：相邻保留点最小间距（米）；0 表示不按距离抽稀，保留全部点 */
        private double simplifyMinMeters = 0;
        /** 单段折线最大点数；0 或负数表示不限制，保留全部点 */
        private int maxPathPoints = 0;
    }

    @Data
    public static class FallbackLocationConfig {
        /** 是否启用相册内权威 GPS 时间插值兜底 */
        private boolean enabled = true;
        /** 两端锚点最大时间跨度（小时），超过则不插值 */
        private int maxInterpGapHours = 24;
        /** 单侧外推最大距离（公里） */
        private double maxExtrapolateKm = 5.0;
        /** 仅一个锚点时，时间差在此分钟内可吸附到该点 */
        private int maxSnapGapMinutes = 30;
        /** 插值/外推允许的最大合理速度（km/h），用于抑制异常跳变 */
        private double maxReasonableSpeedKmh = 200.0;
        /** 默认地图是否包含兜底坐标（估计点需在地图标记并可微调） */
        private boolean includeInMap = true;
        /** 主轨迹是否包含兜底坐标（false=确认采纳后才进主轨迹） */
        private boolean includeInTrack = false;
    }
}
