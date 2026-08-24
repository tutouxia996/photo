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

    /**
     * 视频浏览代理片缓存目录（与原片分离，不入库、不在相册列表展示）。
     * 例如 1080p30.mp4，可随时删除后按需再生成。
     */
    private String proxyPath = "D:/uploadPath/album/cache/proxy";

    /** 本地文件访问根目录（资源映射） */
    private String localRoot = "D:/uploadPath/album";

    private Thumb thumb = new Thumb();

    private VideoProxy videoProxy = new VideoProxy();

    private MapConfig map = new MapConfig();

    private GpxConfig gpx = new GpxConfig();

    /** 无 EXIF/GPX 时的坐标兜底（默认不进主轨迹/地图） */
    private FallbackLocationConfig fallbackLocation = new FallbackLocationConfig();

    /** AI 地标识别（视觉模型 + 高德地理编码） */
    private AiLandmarkConfig aiLandmark = new AiLandmarkConfig();

    /** 照片出图质量打分（本地算法，合格才允许图生图） */
    private PhotoScoreConfig photoScore = new PhotoScoreConfig();

    /** 万相图生图 + 程序拼版出海报 */
    private PhotoDrawConfig photoDraw = new PhotoDrawConfig();

    /** 阿里云盘个人相册定时同步到本地，再走磁盘扫描入库 */
    private AliyunDriveConfig aliyunDrive = new AliyunDriveConfig();

    /** 磁盘扫描性能（大视频路径跳过 / 采样哈希 / 截帧） */
    private ScanConfig scan = new ScanConfig();

    @Data
    public static class ScanConfig {
        /**
         * 已入库且路径+大小未变时，跳过整文件 MD5（增量扫描大视频的关键路径）。
         */
        private boolean pathSizeSkip = true;
        /**
         * 超过该大小（字节）的媒体改用「头+中+尾」采样 MD5，避免 1～30GB 视频整文件读盘。
         * 默认 256MB；设为 0 表示始终整文件哈希。
         */
        private long sampleHashThresholdBytes = 256L * 1024 * 1024;
        /** 采样哈希每段读取长度（字节），默认 4MB */
        private long sampleHashChunkBytes = 4L * 1024 * 1024;
        /**
         * 扫描入库时，超过该大小的视频不截缩略图（ffmpeg 对超大文件也很慢）。
         * 默认 8GB（大疆 4K 常见 1～4GB）；设为 0 表示始终截帧。
         */
        private long skipVideoThumbAboveBytes = 8L * 1024 * 1024 * 1024;
    }

    @Data
    public static class Thumb {
        private int smallWidth = 300;
        private int mediumWidth = 800;
        private int largeWidth = 1920;
    }

    @Data
    public static class VideoProxy {
        /** 同时转码任务数（家用机器建议 1） */
        private int concurrency = 1;
        /** 720p CRF，越大体积越小 */
        private int crf720 = 26;
        /** 1080p CRF */
        private int crf1080 = 23;
        /** x264 preset：ultrafast～veryslow */
        private String preset = "veryfast";
        /** 音频码率 */
        private String audioBitrate = "128k";
        /** 超过该大小（字节）才可能转码；0 表示不限制体积 */
        private long minBytes = 0L;
        /** 分辨率下限：宽≥minWidth 或 高≥minHeight（默认 1080p） */
        private int minWidth = 1920;
        private int minHeight = 1080;
        /** 源片帧率≥该值才可能转码（默认 30fps） */
        private int minFps = 30;
        /** 磁盘扫描完成后，对本次扫描到的视频自动排队转码 */
        private boolean enqueueOnScan = true;
        /**
         * 转码超时基准：每 GB 源文件允许的秒数（另有下限/上限）。
         * 大疆 4K120 软解很慢，默认偏宽松。
         */
        private long timeoutSecondsPerGb = 2400L;
        private long timeoutMinSeconds = 1800L;
        private long timeoutMaxSeconds = 43200L;
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
        /**
         * 中国 OSM PBF 路径（Geofabrik china-latest.osm.pbf）。
         * 存在时优先本地铁路索引，失败再回退公网 Overpass。
         */
        private String osmPbfPath = "D:/uploadPath/album/osm/china-latest.osm.pbf";
        /**
         * 预处理后的铁路索引（由 PBF 构建；空则默认与 PBF 同目录 china-railway-index.bin.gz）
         */
        private String osmRailwayIndexPath = "";
        /** 是否优先使用本地 PBF/索引（true=本地优先，false=仍打 Overpass） */
        private boolean osmPreferLocal = true;
        /**
         * OSM 贴轨允许的最大起终点直线距离（公里）。
         * 北京→苏州约 1050km；公网 Overpass 时代曾限制较紧，本地 PBF 可放大。
         */
        private double osmMaxSpanKm = 1600;
        /** 地铁模式单独上限（公里） */
        private double osmMaxSpanKmMetro = 120;
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
        /**
         * 两端锚点最大直线距离（公里）。超过则不跨段插值，避免同相册多景点（如天坛↔紫竹院）串线。
         */
        private double maxAnchorSpanKm = 8.0;
        /** 单侧外推最大距离（公里） */
        private double maxExtrapolateKm = 5.0;
        /** 仅一个锚点时，时间差在此分钟内可吸附到该点 */
        private int maxSnapGapMinutes = 30;
        /** 插值/外推允许的最大合理速度（km/h），用于抑制异常跳变 */
        private double maxReasonableSpeedKmh = 200.0;
        /**
         * 精修蓝色「区」点时：GPS 估计须落在区域中心此距离内（公里）才允许覆盖。
         * 同景点可细化；跨景点 GPS 不会把「区」吸走。
         */
        private double maxRefineRegionKm = 3.0;
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

    @Data
    public static class PhotoScoreConfig {
        /** 图生图最低分（0-100） */
        private int passScore = 70;
        /** 分析图最长边，越小越快 */
        private int analyzeWidth = 320;
        /** 短边低于该像素直接不及格 */
        private int minShortSide = 720;
    }

    @Data
    public static class PhotoDrawConfig {
        /** 是否启用 AI 出图 */
        private boolean enabled = true;
        /** 万相 API Key；空则复用 aiLandmark.apiKey */
        private String apiKey = "";
        /** 万相异步任务 API 根路径 */
        private String baseUrl = "https://dashscope.aliyuncs.com/api/v1";
        /** 图生图模型 */
        private String model = "wan2.5-i2i-preview";
        /** 反向提示词 */
        private String negativePrompt = "low quality, blurry, watermark, text, logo, distorted face";
        /** 送检参考图最长边 */
        private int maxInputEdge = 1280;
        private boolean promptExtend = true;
        private int connectTimeoutMs = 15000;
        private int readTimeoutMs = 120000;
        /** 轮询任务间隔 */
        private int pollIntervalMs = 2500;
        /** 轮询最大次数 */
        private int maxPollAttempts = 60;
        /** 成品 JPEG 质量 0~1，原图尺寸保存时建议 0.92+ */
        private float jpegQuality = 0.95f;
    }

    @Data
    public static class AliyunDriveConfig {
        /** 总开关；false 时定时任务直接跳过 */
        private boolean enabled = false;
        /**
         * 网页版 refresh_token（浏览器登录阿里云盘后获取）。
         * 刷新后会轮换，运行时以 tokenFile 中的值为准。
         */
        private String refreshToken = "";
        /** 云盘个人相册名称（与 remoteAlbumId 二选一；id 优先） */
        private String remoteAlbumName = "";
        /** 云盘个人相册 ID（优先于名称；多相册时为首项，兼容旧逻辑） */
        private String remoteAlbumId = "";
        /** 本机下载父目录（多相册时各相册落到其子文件夹） */
        private String localPath = "F:/照片视频备份/平时拍照";
        /** 多相册同步列表（页面保存；优先于单项 remoteAlbumId/Name） */
        private List<com.sq.bus.domain.vo.RemoteAlbumItem> remoteAlbums = new ArrayList<com.sq.bus.domain.vo.RemoteAlbumItem>();
        /**
         * 下载完成后触发扫描的 biz_scan_path.path_id。
         * 为空时按 localPath 匹配已启用的扫描目录。
         */
        private Long scanPathId;
        /** 同步成功后是否触发增量/全量扫描入库 */
        private boolean triggerScan = true;
        /** true=全量扫描，false=增量 */
        private boolean fullScan = false;
        /**
         * refresh_token 与同步状态持久化文件。
         * 必须可写：每次刷新 token 后旧 refresh_token 会失效。
         */
        private String tokenFile = "D:/uploadPath/album/aliyun-drive-token.json";
        private int connectTimeoutMs = 15000;
        private int readTimeoutMs = 120000;
        /** 单文件下载超时（毫秒） */
        private int downloadTimeoutMs = 600000;
        /**
         * true=只下图片/视频（按扩展名+category）；false=相册内全部原文件都下（推荐，格式不限）。
         */
        private boolean mediaOnly = false;
        /**
         * 同时下载的文件数。开启分片后建议 2～3，避免总连接数过高。
         */
        private int downloadConcurrency = 2;
        /** 成功后记住的下载 Referer；空则默认 https://www.alipan.com/ */
        private String downloadReferer = "https://www.aliyundrive.com/";
        /**
         * 单文件内 Range 分片并行数（接近官方多连接加速）。
         * 总连接约 ≈ downloadConcurrency × chunkConcurrency。
         */
        private int chunkConcurrency = 8;
        /** 超过该大小才启用分片（字节）；小文件单连接更快 */
        private long multipartMinBytes = 2L * 1024 * 1024;
    }
}
