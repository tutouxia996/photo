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

    /** AI 地标识别（视觉模型 + 高德地理编码） */
    private AiLandmarkConfig aiLandmark = new AiLandmarkConfig();

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
        /**
         * 媒体拍摄时间与最近 GPX 采样点的最大时间差（秒）。
         * 落在相邻采样点之间时优先按时间插值，不受此窗口限制（见 maxInterpGapSeconds）。
         */
        private int matchWindowSeconds = 10;
        /**
         * 将 GPX 时间（多为 UTC）平移到与 EXIF 本地时间对齐的小时数。
         * 中国常见为 8；若录制设备与照片时区已一致可设 0。
         */
        private int timeOffsetHours = 8;
        /**
         * 在 timeOffsetHours 之外的精细秒级平移（可正可负），用于校准设备钟差。
         * 总偏移 = hours*3600 + seconds。
         */
        private int timeOffsetSeconds = 0;
        /**
         * 是否用「带设备 GPS 且靠近 GPX」的媒体自动估计剩余钟差（秒级）。
         * 可修正手机与 GPS 记录仪之间的漂移。
         */
        private boolean autoClockSkew = true;
        /** 自动钟差绝对值上限（秒），超出则忽略估计值 */
        private int maxClockSkewSeconds = 900;
        /** 作为钟差锚点时，媒体 GPS 与最近 GPX 点的最大距离（米） */
        private double skewAnchorMaxMeters = 200;
        /**
         * 相邻 GPX 采样点时间间隔不超过该值（秒）时，按拍摄时间在两点间线性插值坐标。
         * 比「吸附到最近采样点」更贴近真实位置。
         */
        private int maxInterpGapSeconds = 120;
        /** 抽稀：相邻保留点最小间距（米）；0 表示不按距离抽稀，保留全部点 */
        private double simplifyMinMeters = 0;
        /** 单段折线最大点数；0 或负数表示不限制，保留全部点 */
        private int maxPathPoints = 0;
        /**
         * 已与启用 GPX 对应上的媒体不再写入照片轨，
         * 避免「贴合路网」在 GPX 覆盖段再画一条重复折线。
         */
        private boolean excludeMatchedFromPhotoTrack = true;
        /**
         * 照片轨排除：拍摄时间落在 GPX [start,end] 外扩该分钟数内，
         * 且设备 GPS 靠近轨迹时也排除（解决时区导致精匹配失败但仍在 GPX 附近的情况）。
         */
        private int excludeCoverPadMinutes = 60;
        /** 判定「靠近 GPX」的最大距离（米） */
        private double excludeNearTrackMeters = 300;
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

    @Data
    public static class AiLandmarkConfig {
        /** 是否启用 AI 地标识别 */
        private boolean enabled = true;
        /** OpenAI 兼容接口 Key（如通义 DashScope） */
        private String apiKey = "";
        /** 兼容模式 Base URL，默认通义 */
        private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
        /** 多模态模型名 */
        private String model = "qwen-vl-plus";
        /** 单次请求最多识别张数（可多次点选识别；过大易超时/超免费额度） */
        private int maxSample = 40;
        /** 送检图最大边（像素） */
        private int maxImageWidth = 896;
        /**
         * AI/时间推算结果相对「区域粗定位中心」的最大偏离（公里）。
         * 景区级默认 0.45km，避免同城其它景点（地坛↔雍和宫/国子监）。
         */
        private double maxOffsetKm = 0.45;
        /** 按相册粗定位中心拉取周边 POI 白名单的半径（米） */
        private int poiRadiusMeters = 800;
        /** 白名单最多保留 POI 数 */
        private int poiMaxCount = 40;
        /** 同批识别多图投票：达到该票数则弱识别结果可吸附到该 POI */
        private int voteMinCount = 2;
        private int connectTimeoutMs = 15000;
        private int readTimeoutMs = 90000;
    }
}
