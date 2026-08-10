package com.sq.bus.service.route;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.sq.bus.config.AlbumProperties;
import com.sq.bus.utils.CoordTransformUtils;
import com.sq.bus.utils.GeoDistanceUtils;
import com.sq.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * OSM 铁路贴轨：用 Overpass {@code out geom}（快、不易超时），多镜像重试。
 * <p>入参 WGS84；出参 GCJ-02 [[lat,lng],...]
 */
@Service
public class OsmRailwayRouteService {

    private static final Logger log = LoggerFactory.getLogger(OsmRailwayRouteService.class);

    private static final double MAX_SPAN_KM = 280.0;
    private static final double MAX_SNAP_KM = 3.0;

    private static final String[] DEFAULT_MIRRORS = new String[]{
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
            "https://overpass.openstreetmap.ru/api/interpreter"
    };

    @Autowired
    private AlbumProperties albumProperties;

    public TrackRoutePlanResult route(double fromLatWgs, double fromLngWgs,
                                      double toLatWgs, double toLngWgs,
                                      String travelMode) {
        TrackRoutePlanResult result = new TrackRoutePlanResult();
        String mode = travelMode == null ? "train" : travelMode.trim().toLowerCase(Locale.ROOT);
        result.setTravelMode(mode);
        result.setProvider("osm");

        if (!isEnabled()) {
            result.setMessage("未启用 OSM 铁路贴合");
            return result;
        }

        double spanKm = GeoDistanceUtils.haversineKm(fromLatWgs, fromLngWgs, toLatWgs, toLngWgs);
        if (spanKm > MAX_SPAN_KM) {
            result.setMessage("起终点过远，暂不使用 OSM");
            return result;
        }
        if (spanKm < 0.05) {
            result.setPath(listOf(toGcj(fromLatWgs, fromLngWgs), toGcj(toLatWgs, toLngWgs)));
            result.setDistanceMeters(spanKm * 1000);
            result.setMessage("起终点过近，使用直线");
            return result;
        }

        try {
            double padDeg = Math.max(0.15, Math.min(0.6, spanKm * 0.15 / 111.0));
            double south = Math.min(fromLatWgs, toLatWgs) - padDeg;
            double north = Math.max(fromLatWgs, toLatWgs) + padDeg;
            double west = Math.min(fromLngWgs, toLngWgs) - padDeg;
            double east = Math.max(fromLngWgs, toLngWgs) + padDeg;

            // 高铁：必须先只走 highspeed，避免串到京包老线等 usage=main
            String[] passes;
            if ("metro".equals(mode)) {
                passes = new String[]{"metro"};
            } else if ("hsr".equals(mode)) {
                passes = new String[]{"hsr", "main", "all"};
            } else {
                passes = new String[]{"main", "all"};
            }

            List<Long> bestPath = null;
            RailGraph bestGraph = null;
            String usedPass = null;
            String lastErr = null;

            for (String pass : passes) {
                try {
                    String query = buildQuery(south, west, north, east, pass);
                    JSONObject json = postOverpassWithFallback(query);
                    RailGraph graph = parseGeomGraph(json, mode);
                    if (graph.nodes.size() < 2 || graph.adj.isEmpty()) {
                        lastErr = pass + " 无铁路几何";
                        continue;
                    }
                    Long startId = snap(graph, fromLatWgs, fromLngWgs, true);
                    Long endId = snap(graph, toLatWgs, toLngWgs, true);
                    if (startId == null) {
                        startId = snap(graph, fromLatWgs, fromLngWgs, false);
                    }
                    if (endId == null) {
                        endId = snap(graph, toLatWgs, toLngWgs, false);
                    }
                    if (startId == null || endId == null) {
                        lastErr = pass + " 车站吸附失败";
                        continue;
                    }
                    if (startId.equals(endId)) {
                        lastErr = pass + " 起终点吸附到同一点";
                        continue;
                    }
                    List<Long> path = dijkstra(graph, startId, endId);
                    if (path == null || path.size() < 2) {
                        lastErr = pass + " 铁路不连通";
                        continue;
                    }
                    double pathKm = pathLengthKm(graph, path);
                    if (pathKm > Math.max(spanKm * 4.0, spanKm + 50)) {
                        lastErr = pass + " 绕路过多(" + Math.round(pathKm) + "km)";
                        continue;
                    }
                    bestPath = path;
                    bestGraph = graph;
                    usedPass = pass;
                    break;
                } catch (Exception e) {
                    lastErr = pass + ": " + e.getMessage();
                    log.warn("OSM pass {} failed: {}", pass, e.getMessage());
                }
            }

            if (bestPath == null || bestGraph == null) {
                result.setMessage("OSM 贴轨失败" + (lastErr == null ? "" : "：" + lastErr));
                return result;
            }

            List<double[]> gcjPath = new ArrayList<double[]>();
            append(gcjPath, toGcj(fromLatWgs, fromLngWgs));
            double meters = 0;
            double[] prev = null;
            for (Long id : bestPath) {
                Node n = bestGraph.nodes.get(id);
                if (n == null) {
                    continue;
                }
                double[] p = toGcj(n.lat, n.lng);
                if (prev != null) {
                    meters += GeoDistanceUtils.haversineKm(prev[0], prev[1], p[0], p[1]) * 1000;
                }
                append(gcjPath, p);
                prev = p;
            }
            double[] end = toGcj(toLatWgs, toLngWgs);
            if (prev != null) {
                meters += GeoDistanceUtils.haversineKm(prev[0], prev[1], end[0], end[1]) * 1000;
            }
            append(gcjPath, end);

            if (gcjPath.size() < 3) {
                result.setMessage("OSM 折线过短");
                result.setPath(gcjPath);
                return result;
            }

            result.setPath(gcjPath);
            result.setDistanceMeters(meters);
            String tip = "已按 OSM 铁路贴轨（" + usedPass + "，" + gcjPath.size() + " 点）";
            if ("hsr".equals(mode) || "hsr".equals(usedPass)) {
                tip += "；高铁含隧道段，不会贴着地表京包线转弯";
            }
            result.setMessage(tip);
            log.info("OSM ok mode={} pass={} pts={} m≈{}", mode, usedPass, gcjPath.size(), Math.round(meters));
            return result;
        } catch (Exception e) {
            log.warn("OSM route failed: {}", e.getMessage());
            result.setMessage("OSM 失败：" + e.getMessage());
            return result;
        }
    }

    private boolean isEnabled() {
        AlbumProperties.MapConfig map = albumProperties.getMap();
        return map == null || map.isOsmRailwayEnabled();
    }

    private List<String> mirrorUrls() {
        List<String> list = new ArrayList<String>();
        AlbumProperties.MapConfig map = albumProperties.getMap();
        if (map != null && StringUtils.isNotEmpty(map.getOverpassUrl())) {
            list.add(map.getOverpassUrl().trim());
        }
        for (String u : DEFAULT_MIRRORS) {
            if (!list.contains(u)) {
                list.add(u);
            }
        }
        return list;
    }

    private String buildQuery(double south, double west, double north, double east, String pass) {
        String bbox = "(" + south + "," + west + "," + north + "," + east + ")";
        if ("metro".equals(pass)) {
            return "[out:json][timeout:45];("
                    + "way[\"railway\"~\"^(subway|light_rail|monorail)$\"]" + bbox + ";"
                    + ");out geom;";
        }
        // 仅高铁/城际（京张等），不含京包老线
        if ("hsr".equals(pass)) {
            return "[out:json][timeout:45];("
                    + "way[\"railway\"=\"rail\"][\"highspeed\"=\"yes\"]" + bbox + ";"
                    + ");out geom;";
        }
        if ("main".equals(pass)) {
            return "[out:json][timeout:45];("
                    + "way[\"railway\"=\"rail\"][\"highspeed\"=\"yes\"]" + bbox + ";"
                    + "way[\"railway\"=\"rail\"][\"usage\"=\"main\"]" + bbox + ";"
                    + "way[\"railway\"=\"rail\"][\"usage\"=\"branch\"]" + bbox + ";"
                    + ");out geom;";
        }
        return "[out:json][timeout:45];("
                + "way[\"railway\"=\"rail\"][\"railway\"!~\"abandoned|disused|construction|razed|proposed\"]" + bbox + ";"
                + ");out geom;";
    }

    private JSONObject postOverpassWithFallback(String query) throws Exception {
        // 先读本地缓存，避免每次打 Overpass（也避开偶发证书/超时）
        JSONObject cached = readCache(query);
        if (cached != null) {
            JSONArray elements = cached.getJSONArray("elements");
            if (elements != null && !elements.isEmpty()) {
                log.info("OSM Overpass hit local cache, elements={}", elements.size());
                return cached;
            }
        }

        Exception last = null;
        for (String endpoint : mirrorUrls()) {
            try {
                JSONObject json = postOverpass(endpoint, query);
                JSONArray elements = json.getJSONArray("elements");
                if (elements != null && !elements.isEmpty()) {
                    writeCache(query, json);
                    return json;
                }
                last = new IllegalStateException(endpoint + " 返回空 elements");
            } catch (Exception e) {
                last = e;
                log.warn("Overpass mirror fail {}: {}", endpoint, e.getMessage());
            }
        }
        throw last == null ? new IllegalStateException("Overpass 全部失败") : last;
    }

    private JSONObject postOverpass(String endpoint, String query) throws Exception {
        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        if (conn instanceof HttpsURLConnection && trustAllSsl()) {
            applyTrustAll((HttpsURLConnection) conn);
        }
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(50000);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "AlbumTrackOsmRailway/1.2");
        byte[] body = ("data=" + java.net.URLEncoder.encode(query, "UTF-8")).getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body);
        }
        int code = conn.getResponseCode();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                code >= 400 ? conn.getErrorStream() : conn.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        conn.disconnect();
        if (code >= 400) {
            throw new IllegalStateException("HTTP " + code + " " + truncate(sb.toString(), 120));
        }
        JSONObject json = JSON.parseObject(sb.toString());
        if (json == null) {
            throw new IllegalStateException("空响应");
        }
        return json;
    }

    private boolean trustAllSsl() {
        AlbumProperties.MapConfig map = albumProperties.getMap();
        return map == null || map.isOverpassTrustAllSsl();
    }

    private static volatile SSLContext trustAllContext;

    private void applyTrustAll(HttpsURLConnection conn) throws Exception {
        if (trustAllContext == null) {
            synchronized (OsmRailwayRouteService.class) {
                if (trustAllContext == null) {
                    TrustManager[] trustAll = new TrustManager[]{
                            new X509TrustManager() {
                                @Override
                                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                                }

                                @Override
                                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                                }

                                @Override
                                public X509Certificate[] getAcceptedIssuers() {
                                    return new X509Certificate[0];
                                }
                            }
                    };
                    SSLContext ctx = SSLContext.getInstance("TLS");
                    ctx.init(null, trustAll, new SecureRandom());
                    trustAllContext = ctx;
                }
            }
        }
        conn.setSSLSocketFactory(trustAllContext.getSocketFactory());
        conn.setHostnameVerifier(new HostnameVerifier() {
            @Override
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }
        });
    }

    private Path cacheDir() {
        AlbumProperties.MapConfig map = albumProperties.getMap();
        String dir = map == null ? null : map.getOsmCacheDir();
        if (StringUtils.isEmpty(dir)) {
            dir = System.getProperty("java.io.tmpdir") + "/album-osm-cache";
        }
        return Paths.get(dir);
    }

    private String cacheKey(String query) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] dig = md.digest(query.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : dig) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(query.hashCode());
        }
    }

    private JSONObject readCache(String query) {
        try {
            Path file = cacheDir().resolve(cacheKey(query) + ".json");
            if (!Files.isRegularFile(file)) {
                return null;
            }
            // 缓存 14 天
            long age = System.currentTimeMillis() - Files.getLastModifiedTime(file).toMillis();
            if (age > 14L * 24 * 3600 * 1000) {
                return null;
            }
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                StringBuilder sb = new StringBuilder();
                char[] buf = new char[8192];
                int n;
                while ((n = r.read(buf)) >= 0) {
                    sb.append(buf, 0, n);
                }
                return JSON.parseObject(sb.toString());
            }
        } catch (Exception e) {
            return null;
        }
    }

    private void writeCache(String query, JSONObject json) {
        try {
            Path dir = cacheDir();
            Files.createDirectories(dir);
            Path file = dir.resolve(cacheKey(query) + ".json");
            Files.write(file, json.toJSONString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.debug("write osm cache failed: {}", e.getMessage());
        }
    }

    private String truncate(String s, int n) {
        if (s == null) {
            return "";
        }
        return s.length() <= n ? s : s.substring(0, n);
    }

    /** 从 out geom 的 geometry 建图 */
    private RailGraph parseGeomGraph(JSONObject json, String mode) {
        RailGraph graph = new RailGraph();
        JSONArray elements = json.getJSONArray("elements");
        if (elements == null) {
            return graph;
        }
        for (int i = 0; i < elements.size(); i++) {
            JSONObject el = elements.getJSONObject(i);
            if (!"way".equals(el.getString("type"))) {
                continue;
            }
            JSONArray geom = el.getJSONArray("geometry");
            if (geom == null || geom.size() < 2) {
                continue;
            }
            JSONObject tags = el.getJSONObject("tags");
            double factor = edgeCostFactor(tags, mode);
            int quality = wayQuality(tags);
            if ("hsr".equals(mode) && factor >= 20) {
                continue;
            }
            Long prevId = null;
            double prevLat = 0;
            double prevLng = 0;
            for (int j = 0; j < geom.size(); j++) {
                JSONObject pt = geom.getJSONObject(j);
                Double lat = pt.getDouble("lat");
                Double lng = pt.getDouble("lon");
                if (lat == null || lng == null) {
                    continue;
                }
                long id = nodeKey(lat, lng);
                if (!graph.nodes.containsKey(id)) {
                    graph.nodes.put(id, new Node(id, lat, lng));
                }
                bumpQuality(graph, id, quality);
                if (prevId != null) {
                    double meters = GeoDistanceUtils.haversineKm(prevLat, prevLng, lat, lng) * 1000;
                    if (meters >= 0.2) {
                        double cost = meters * factor;
                        addEdge(graph, prevId, id, cost);
                        addEdge(graph, id, prevId, cost);
                    }
                }
                prevId = id;
                prevLat = lat;
                prevLng = lng;
            }
        }
        return graph;
    }

    private long nodeKey(double lat, double lng) {
        // ~1.1m 量化，保证接头重合
        long la = Math.round(lat * 1e5);
        long lo = Math.round(lng * 1e5);
        return (la << 32) ^ (lo & 0xffffffffL);
    }

    private double edgeCostFactor(JSONObject tags, String mode) {
        if (tags == null) {
            return "hsr".equals(mode) ? 8.0 : 1.4;
        }
        String service = lower(tags.getString("service"));
        if ("yard".equals(service) || "siding".equals(service) || "spur".equals(service)
                || "crossover".equals(service)) {
            return "hsr".equals(mode) ? 25.0 : 8.0;
        }
        if ("yes".equals(lower(tags.getString("highspeed")))) {
            return 0.85;
        }
        // 高铁模式下：非高铁正线大幅惩罚，避免串京包老线
        if ("hsr".equals(mode)) {
            String usage = lower(tags.getString("usage"));
            if ("main".equals(usage) || "branch".equals(usage)) {
                return 6.0;
            }
            return 10.0;
        }
        String usage = lower(tags.getString("usage"));
        if ("main".equals(usage)) {
            return 1.0;
        }
        if ("branch".equals(usage)) {
            return 1.2;
        }
        if ("industrial".equals(usage) || "military".equals(usage)) {
            return 12.0;
        }
        return 1.45;
    }

    private int wayQuality(JSONObject tags) {
        if (tags == null) {
            return 1;
        }
        if ("yes".equals(lower(tags.getString("highspeed")))) {
            return 100;
        }
        String usage = lower(tags.getString("usage"));
        if ("main".equals(usage)) {
            return 80;
        }
        if ("branch".equals(usage)) {
            return 50;
        }
        String service = lower(tags.getString("service"));
        if ("yard".equals(service) || "siding".equals(service) || "spur".equals(service)) {
            return 0;
        }
        return 20;
    }

    private void bumpQuality(RailGraph graph, long nodeId, int quality) {
        Integer old = graph.nodeQuality.get(nodeId);
        if (old == null || quality > old) {
            graph.nodeQuality.put(nodeId, quality);
        }
    }

    private void addEdge(RailGraph graph, long from, long to, double cost) {
        List<Edge> list = graph.adj.get(from);
        if (list == null) {
            list = new ArrayList<Edge>();
            graph.adj.put(from, list);
        }
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).to == to) {
                if (cost < list.get(i).cost) {
                    list.set(i, new Edge(to, cost));
                }
                return;
            }
        }
        list.add(new Edge(to, cost));
    }

    private Long snap(RailGraph graph, double lat, double lng, boolean preferMain) {
        Long best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (Node n : graph.nodes.values()) {
            if (!graph.adj.containsKey(n.id) || graph.adj.get(n.id).isEmpty()) {
                continue;
            }
            int q = graph.nodeQuality.getOrDefault(n.id, 0);
            if (preferMain && q < 50) {
                continue;
            }
            double km = GeoDistanceUtils.haversineKm(lat, lng, n.lat, n.lng);
            if (km > MAX_SNAP_KM) {
                continue;
            }
            double score = km - Math.min(q, 100) * 0.002;
            if (score < bestScore) {
                bestScore = score;
                best = n.id;
            }
        }
        return best;
    }

    private List<Long> dijkstra(RailGraph graph, long start, long end) {
        Map<Long, Double> dist = new HashMap<Long, Double>();
        Map<Long, Long> prev = new HashMap<Long, Long>();
        PriorityQueue<long[]> pq = new PriorityQueue<long[]>(new Comparator<long[]>() {
            @Override
            public int compare(long[] a, long[] b) {
                return Double.compare(Double.longBitsToDouble(a[1]), Double.longBitsToDouble(b[1]));
            }
        });
        dist.put(start, 0.0);
        pq.offer(new long[]{start, Double.doubleToRawLongBits(0.0)});
        Set<Long> settled = new HashSet<Long>();
        while (!pq.isEmpty()) {
            long[] cur = pq.poll();
            long u = cur[0];
            if (!settled.add(u)) {
                continue;
            }
            if (u == end) {
                break;
            }
            double du = dist.getOrDefault(u, Double.POSITIVE_INFINITY);
            List<Edge> edges = graph.adj.get(u);
            if (edges == null) {
                continue;
            }
            for (Edge e : edges) {
                if (settled.contains(e.to)) {
                    continue;
                }
                double nd = du + e.cost;
                Double old = dist.get(e.to);
                if (old == null || nd < old) {
                    dist.put(e.to, nd);
                    prev.put(e.to, u);
                    pq.offer(new long[]{e.to, Double.doubleToRawLongBits(nd)});
                }
            }
        }
        if (!dist.containsKey(end)) {
            return null;
        }
        List<Long> path = new ArrayList<Long>();
        Long c = end;
        while (c != null) {
            path.add(c);
            if (c == start) {
                break;
            }
            c = prev.get(c);
        }
        if (path.isEmpty() || path.get(path.size() - 1) != start) {
            return null;
        }
        Collections.reverse(path);
        return path;
    }

    private double pathLengthKm(RailGraph graph, List<Long> path) {
        double km = 0;
        for (int i = 1; i < path.size(); i++) {
            Node a = graph.nodes.get(path.get(i - 1));
            Node b = graph.nodes.get(path.get(i));
            if (a != null && b != null) {
                km += GeoDistanceUtils.haversineKm(a.lat, a.lng, b.lat, b.lng);
            }
        }
        return km;
    }

    private void append(List<double[]> path, double[] p) {
        if (path.isEmpty()) {
            path.add(p);
            return;
        }
        double[] last = path.get(path.size() - 1);
        if (Math.abs(last[0] - p[0]) > 1e-7 || Math.abs(last[1] - p[1]) > 1e-7) {
            path.add(p);
        }
    }

    private double[] toGcj(double latWgs, double lngWgs) {
        double[] gcj = CoordTransformUtils.wgs84ToGcj02(lngWgs, latWgs);
        return new double[]{round6(gcj[1]), round6(gcj[0])};
    }

    private List<double[]> listOf(double[] a, double[] b) {
        List<double[]> path = new ArrayList<double[]>(2);
        path.add(a);
        path.add(b);
        return path;
    }

    private String lower(String s) {
        return s == null ? null : s.trim().toLowerCase(Locale.ROOT);
    }

    private double round6(double v) {
        return Math.round(v * 1_000_000d) / 1_000_000d;
    }

    private static final class Node {
        final long id;
        final double lat;
        final double lng;

        Node(long id, double lat, double lng) {
            this.id = id;
            this.lat = lat;
            this.lng = lng;
        }
    }

    private static final class Edge {
        final long to;
        final double cost;

        Edge(long to, double cost) {
            this.to = to;
            this.cost = cost;
        }
    }

    private static final class RailGraph {
        final Map<Long, Node> nodes = new HashMap<Long, Node>();
        final Map<Long, List<Edge>> adj = new HashMap<Long, List<Edge>>();
        final Map<Long, Integer> nodeQuality = new HashMap<Long, Integer>();
    }
}
