package com.sq.bus.service.route;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.sq.bus.config.AlbumProperties;
import com.sq.common.utils.StringUtils;
import crosby.binary.osmosis.OsmosisReader;
import org.openstreetmap.osmosis.core.container.v0_6.EntityContainer;
import org.openstreetmap.osmosis.core.domain.v0_6.Entity;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Tag;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;
import org.openstreetmap.osmosis.core.domain.v0_6.WayNode;
import org.openstreetmap.osmosis.core.task.v0_6.Sink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 从中国 OSM PBF 构建本地铁路索引，按 bbox 查询并输出与 Overpass {@code out geom} 同结构的 JSON，
 * 供 {@link OsmRailwayRouteService} 直接贴轨（不依赖公网镜像）。
 */
@Service
public class LocalOsmRailwayIndex {

    private static final Logger log = LoggerFactory.getLogger(LocalOsmRailwayIndex.class);
    private static final int INDEX_VERSION = 1;

    @Autowired
    private AlbumProperties albumProperties;

    private final Object lock = new Object();
    private final AtomicBoolean ready = new AtomicBoolean(false);
    private final AtomicBoolean building = new AtomicBoolean(false);

    private List<RailWay> ways = Collections.emptyList();
    /** gridKey -> way indices */
    private Map<Long, int[]> grid = Collections.emptyMap();

    public boolean isReady() {
        return ready.get();
    }

    /**
     * 确保索引可用：优先加载已有索引；若仅有 PBF 则后台构建（当前请求仍可回退 Overpass）。
     */
    public boolean ensureReady() {
        if (ready.get()) {
            return true;
        }
        if (!preferLocal()) {
            return false;
        }
        synchronized (lock) {
            if (ready.get()) {
                return true;
            }
            Path indexPath = resolveIndexPath();
            if (indexPath != null && Files.isRegularFile(indexPath)) {
                try {
                    loadIndex(indexPath);
                    ready.set(true);
                    log.info("OSM 本地铁路索引已加载: ways={} path={}", ways.size(), indexPath);
                    return true;
                } catch (Exception e) {
                    log.warn("加载本地铁路索引失败，将尝试从 PBF 重建: {}", e.getMessage());
                }
            }
            Path pbf = resolvePbfPath();
            if (pbf == null || !Files.isRegularFile(pbf)) {
                log.debug("未配置或找不到中国 PBF（album.map.osmPbfPath），跳过本地铁路索引");
                return false;
            }
            // 首次构建较慢：后台跑，避免拖死贴轨 HTTP 请求
            if (building.compareAndSet(false, true)) {
                final Path pbfPath = pbf;
                final Path outIndex = indexPath;
                Thread t = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            log.info("开始从 PBF 构建本地铁路索引（后台，可能需数分钟）: {}", pbfPath);
                            buildFromPbf(pbfPath);
                            if (outIndex != null) {
                                Files.createDirectories(outIndex.getParent());
                                saveIndex(outIndex);
                                log.info("本地铁路索引已写入: {}", outIndex);
                            }
                            ready.set(true);
                            log.info("OSM 本地铁路索引就绪: ways={}", ways.size());
                        } catch (Exception e) {
                            log.warn("从 PBF 构建铁路索引失败: {}", e.toString());
                        } finally {
                            building.set(false);
                        }
                    }
                }, "osm-railway-index-builder");
                t.setDaemon(true);
                t.start();
            } else {
                log.debug("本地铁路索引正在构建中");
            }
            return false;
        }
    }

    /**
     * 按 bbox + pass 过滤，返回 Overpass 风格 JSON（elements 为带 geometry 的 way）。
     */
    public JSONObject query(double south, double west, double north, double east, String pass) {
        if (!ensureReady() || ways.isEmpty()) {
            return null;
        }
        Set<Integer> hit = new HashSet<Integer>();
        int lat0 = (int) Math.floor(south);
        int lat1 = (int) Math.floor(north);
        int lng0 = (int) Math.floor(west);
        int lng1 = (int) Math.floor(east);
        for (int la = lat0; la <= lat1; la++) {
            for (int lo = lng0; lo <= lng1; lo++) {
                int[] idxs = grid.get(gridKey(la, lo));
                if (idxs == null) {
                    continue;
                }
                for (int idx : idxs) {
                    hit.add(idx);
                }
            }
        }
        JSONArray elements = new JSONArray();
        for (Integer idx : hit) {
            RailWay w = ways.get(idx);
            if (!bboxIntersects(w, south, west, north, east)) {
                continue;
            }
            if (!matchPass(w, pass)) {
                continue;
            }
            if (isExcluded(w)) {
                continue;
            }
            elements.add(toOverpassWay(w));
        }
        JSONObject json = new JSONObject();
        json.put("elements", elements);
        json.put("generator", "local-osm-pbf");
        return json;
    }

    private boolean preferLocal() {
        AlbumProperties.MapConfig map = albumProperties.getMap();
        return map == null || map.isOsmPreferLocal();
    }

    private Path resolvePbfPath() {
        AlbumProperties.MapConfig map = albumProperties.getMap();
        if (map == null || StringUtils.isEmpty(map.getOsmPbfPath())) {
            return null;
        }
        return Paths.get(map.getOsmPbfPath().trim());
    }

    private Path resolveIndexPath() {
        AlbumProperties.MapConfig map = albumProperties.getMap();
        if (map != null && StringUtils.isNotEmpty(map.getOsmRailwayIndexPath())) {
            return Paths.get(map.getOsmRailwayIndexPath().trim());
        }
        Path pbf = resolvePbfPath();
        if (pbf == null) {
            return Paths.get("D:/uploadPath/album/osm/china-railway-index.bin.gz");
        }
        Path parent = pbf.getParent();
        if (parent == null) {
            return Paths.get("china-railway-index.bin.gz");
        }
        return parent.resolve("china-railway-index.bin.gz");
    }

    private void buildFromPbf(Path pbf) throws Exception {
        // Pass1: 铁路 way + 所需 nodeId
        final List<RawWay> rawWays = new ArrayList<RawWay>();
        final Set<Long> neededNodes = new HashSet<Long>();
        readPbf(pbf, new Sink() {
            @Override
            public void initialize(Map<String, Object> metaData) {
            }

            @Override
            public void process(EntityContainer entityContainer) {
                Entity entity = entityContainer.getEntity();
                if (!(entity instanceof Way)) {
                    return;
                }
                Way way = (Way) entity;
                Map<String, String> tags = tagsOf(way.getTags());
                if (!isRailwayWay(tags)) {
                    return;
                }
                List<WayNode> nodes = way.getWayNodes();
                if (nodes == null || nodes.size() < 2) {
                    return;
                }
                long[] refs = new long[nodes.size()];
                for (int i = 0; i < nodes.size(); i++) {
                    refs[i] = nodes.get(i).getNodeId();
                    neededNodes.add(refs[i]);
                }
                RawWay rw = new RawWay();
                rw.id = way.getId();
                rw.tags = tags;
                rw.nodeRefs = refs;
                rawWays.add(rw);
            }

            @Override
            public void complete() {
            }

            @Override
            public void close() {
            }
        });
        log.info("PBF pass1 完成: railwayWays={} neededNodes={}", rawWays.size(), neededNodes.size());

        // Pass2: 只收需要的节点坐标
        final Map<Long, double[]> coords = new HashMap<Long, double[]>(Math.max(16, neededNodes.size() * 2));
        readPbf(pbf, new Sink() {
            @Override
            public void initialize(Map<String, Object> metaData) {
            }

            @Override
            public void process(EntityContainer entityContainer) {
                Entity entity = entityContainer.getEntity();
                if (!(entity instanceof Node)) {
                    return;
                }
                Node node = (Node) entity;
                if (!neededNodes.contains(node.getId())) {
                    return;
                }
                coords.put(node.getId(), new double[]{node.getLatitude(), node.getLongitude()});
            }

            @Override
            public void complete() {
            }

            @Override
            public void close() {
            }
        });
        log.info("PBF pass2 完成: resolvedNodes={}", coords.size());

        List<RailWay> built = new ArrayList<RailWay>(rawWays.size());
        Map<Long, List<Integer>> gridMap = new HashMap<Long, List<Integer>>();
        for (RawWay rw : rawWays) {
            List<Double> lats = new ArrayList<Double>();
            List<Double> lngs = new ArrayList<Double>();
            for (long ref : rw.nodeRefs) {
                double[] c = coords.get(ref);
                if (c == null) {
                    continue;
                }
                lats.add(c[0]);
                lngs.add(c[1]);
            }
            if (lats.size() < 2) {
                continue;
            }
            RailWay w = new RailWay();
            w.id = rw.id;
            w.tags = rw.tags;
            w.lats = toArray(lats);
            w.lngs = toArray(lngs);
            w.minLat = min(w.lats);
            w.maxLat = max(w.lats);
            w.minLng = min(w.lngs);
            w.maxLng = max(w.lngs);
            int idx = built.size();
            built.add(w);
            int la0 = (int) Math.floor(w.minLat);
            int la1 = (int) Math.floor(w.maxLat);
            int lo0 = (int) Math.floor(w.minLng);
            int lo1 = (int) Math.floor(w.maxLng);
            for (int la = la0; la <= la1; la++) {
                for (int lo = lo0; lo <= lo1; lo++) {
                    long key = gridKey(la, lo);
                    List<Integer> list = gridMap.get(key);
                    if (list == null) {
                        list = new ArrayList<Integer>();
                        gridMap.put(key, list);
                    }
                    list.add(idx);
                }
            }
        }
        Map<Long, int[]> gridArr = new HashMap<Long, int[]>(gridMap.size() * 2);
        for (Map.Entry<Long, List<Integer>> e : gridMap.entrySet()) {
            List<Integer> list = e.getValue();
            int[] arr = new int[list.size()];
            for (int i = 0; i < list.size(); i++) {
                arr[i] = list.get(i);
            }
            gridArr.put(e.getKey(), arr);
        }
        this.ways = built;
        this.grid = gridArr;
    }

    private void readPbf(Path pbf, Sink sink) throws Exception {
        try (InputStream in = new BufferedInputStream(new FileInputStream(pbf.toFile()), 1 << 20)) {
            OsmosisReader reader = new OsmosisReader(in);
            reader.setSink(sink);
            reader.run();
        }
    }

    private void saveIndex(Path path) throws Exception {
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                new GZIPOutputStream(new FileOutputStream(path.toFile())), 1 << 20))) {
            out.writeInt(INDEX_VERSION);
            out.writeInt(ways.size());
            for (RailWay w : ways) {
                out.writeLong(w.id);
                out.writeInt(w.tags == null ? 0 : w.tags.size());
                if (w.tags != null) {
                    for (Map.Entry<String, String> e : w.tags.entrySet()) {
                        out.writeUTF(nullToEmpty(e.getKey()));
                        out.writeUTF(nullToEmpty(e.getValue()));
                    }
                }
                out.writeInt(w.lats.length);
                for (int i = 0; i < w.lats.length; i++) {
                    out.writeFloat((float) w.lats[i]);
                    out.writeFloat((float) w.lngs[i]);
                }
            }
        }
    }

    private void loadIndex(Path path) throws Exception {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                new GZIPInputStream(new FileInputStream(path.toFile())), 1 << 20))) {
            int ver = in.readInt();
            if (ver != INDEX_VERSION) {
                throw new IllegalStateException("unsupported index version " + ver);
            }
            int n = in.readInt();
            List<RailWay> built = new ArrayList<RailWay>(n);
            Map<Long, List<Integer>> gridMap = new HashMap<Long, List<Integer>>();
            for (int i = 0; i < n; i++) {
                RailWay w = new RailWay();
                w.id = in.readLong();
                int tc = in.readInt();
                w.tags = new HashMap<String, String>(Math.max(4, tc * 2));
                for (int t = 0; t < tc; t++) {
                    w.tags.put(in.readUTF(), in.readUTF());
                }
                int pc = in.readInt();
                w.lats = new double[pc];
                w.lngs = new double[pc];
                for (int p = 0; p < pc; p++) {
                    w.lats[p] = in.readFloat();
                    w.lngs[p] = in.readFloat();
                }
                w.minLat = min(w.lats);
                w.maxLat = max(w.lats);
                w.minLng = min(w.lngs);
                w.maxLng = max(w.lngs);
                int idx = built.size();
                built.add(w);
                int la0 = (int) Math.floor(w.minLat);
                int la1 = (int) Math.floor(w.maxLat);
                int lo0 = (int) Math.floor(w.minLng);
                int lo1 = (int) Math.floor(w.maxLng);
                for (int la = la0; la <= la1; la++) {
                    for (int lo = lo0; lo <= lo1; lo++) {
                        long key = gridKey(la, lo);
                        List<Integer> list = gridMap.get(key);
                        if (list == null) {
                            list = new ArrayList<Integer>();
                            gridMap.put(key, list);
                        }
                        list.add(idx);
                    }
                }
            }
            Map<Long, int[]> gridArr = new HashMap<Long, int[]>(gridMap.size() * 2);
            for (Map.Entry<Long, List<Integer>> e : gridMap.entrySet()) {
                List<Integer> list = e.getValue();
                int[] arr = new int[list.size()];
                for (int i = 0; i < list.size(); i++) {
                    arr[i] = list.get(i);
                }
                gridArr.put(e.getKey(), arr);
            }
            this.ways = built;
            this.grid = gridArr;
        }
    }

    private static JSONObject toOverpassWay(RailWay w) {
        JSONObject el = new JSONObject();
        el.put("type", "way");
        el.put("id", w.id);
        JSONObject tags = new JSONObject();
        if (w.tags != null) {
            for (Map.Entry<String, String> e : w.tags.entrySet()) {
                tags.put(e.getKey(), e.getValue());
            }
        }
        el.put("tags", tags);
        JSONArray geom = new JSONArray(w.lats.length);
        for (int i = 0; i < w.lats.length; i++) {
            JSONObject pt = new JSONObject();
            pt.put("lat", w.lats[i]);
            pt.put("lon", w.lngs[i]);
            geom.add(pt);
        }
        el.put("geometry", geom);
        return el;
    }

    private static boolean matchPass(RailWay w, String pass) {
        Map<String, String> tags = w.tags;
        String railway = lower(tags == null ? null : tags.get("railway"));
        String highspeed = lower(tags == null ? null : tags.get("highspeed"));
        String usage = lower(tags == null ? null : tags.get("usage"));
        if ("metro".equals(pass)) {
            return "subway".equals(railway) || "light_rail".equals(railway) || "monorail".equals(railway);
        }
        if (!"rail".equals(railway)) {
            // 普通铁路 pass 只吃 railway=rail；地铁另议
            if ("hsr".equals(pass) || "hsr_main".equals(pass) || "main".equals(pass) || "main_ext".equals(pass)) {
                return false;
            }
        }
        if ("hsr".equals(pass)) {
            return "yes".equals(highspeed);
        }
        if ("hsr_main".equals(pass)) {
            return "yes".equals(highspeed) || "main".equals(usage);
        }
        if ("main".equals(pass)) {
            return "yes".equals(highspeed) || "main".equals(usage) || "branch".equals(usage);
        }
        // main_ext
        if ("industrial".equals(usage) || "military".equals(usage)) {
            return false;
        }
        return "rail".equals(railway);
    }

    private static boolean isExcluded(RailWay w) {
        if (w.tags == null) {
            return false;
        }
        String service = lower(w.tags.get("service"));
        if ("yard".equals(service) || "siding".equals(service) || "spur".equals(service)
                || "crossover".equals(service)) {
            return true;
        }
        String usage = lower(w.tags.get("usage"));
        return "industrial".equals(usage) || "military".equals(usage);
    }

    private static boolean isRailwayWay(Map<String, String> tags) {
        if (tags == null) {
            return false;
        }
        String railway = lower(tags.get("railway"));
        return "rail".equals(railway) || "subway".equals(railway)
                || "light_rail".equals(railway) || "monorail".equals(railway);
    }

    private static Map<String, String> tagsOf(Collection<Tag> tags) {
        Map<String, String> map = new HashMap<String, String>();
        if (tags == null) {
            return map;
        }
        for (Tag tag : tags) {
            if (tag == null || tag.getKey() == null) {
                continue;
            }
            map.put(tag.getKey(), tag.getValue());
        }
        return map;
    }

    private static boolean bboxIntersects(RailWay w, double south, double west, double north, double east) {
        return w.maxLat >= south && w.minLat <= north && w.maxLng >= west && w.minLng <= east;
    }

    private static long gridKey(int latFloor, int lngFloor) {
        return (((long) latFloor) << 32) ^ (lngFloor & 0xffffffffL);
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static double[] toArray(List<Double> list) {
        double[] a = new double[list.size()];
        for (int i = 0; i < list.size(); i++) {
            a[i] = list.get(i);
        }
        return a;
    }

    private static double min(double[] a) {
        double m = a[0];
        for (double v : a) {
            if (v < m) {
                m = v;
            }
        }
        return m;
    }

    private static double max(double[] a) {
        double m = a[0];
        for (double v : a) {
            if (v > m) {
                m = v;
            }
        }
        return m;
    }

    private static final class RawWay {
        long id;
        Map<String, String> tags;
        long[] nodeRefs;
    }

    private static final class RailWay {
        long id;
        Map<String, String> tags;
        double[] lats;
        double[] lngs;
        double minLat;
        double maxLat;
        double minLng;
        double maxLng;
    }
}
