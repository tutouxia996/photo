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
}
