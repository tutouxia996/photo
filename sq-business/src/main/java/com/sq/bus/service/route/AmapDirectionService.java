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
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 调用高德路径规划，生成与出行方式一致的真实折线。
 */
@Service
public class AmapDirectionService {

    private static final Logger log = LoggerFactory.getLogger(AmapDirectionService.class);

    @Autowired
    private AlbumProperties albumProperties;

    @Autowired
    private OsmRailwayRouteService osmRailwayRouteService;

    /**
     * @param fromLatWgs 起点纬度（WGS84）
     * @param fromLngWgs 起点经度（WGS84）
     * @param toLatWgs   终点纬度（WGS84）
     * @param toLngWgs   终点经度（WGS84）
     * @param travelMode 出行方式
     */
    public TrackRoutePlanResult plan(double fromLatWgs, double fromLngWgs,
                                     double toLatWgs, double toLngWgs,
                                     String travelMode) {
        return plan(fromLatWgs, fromLngWgs, toLatWgs, toLngWgs, travelMode, true);
    }

    /**
     * @param allowOsmRail 是否允许火车/高铁/地铁走 OSM（批量贴路建议 false，避免打开轨迹极慢）
     */
    public TrackRoutePlanResult plan(double fromLatWgs, double fromLngWgs,
                                     double toLatWgs, double toLngWgs,
                                     String travelMode,
                                     boolean allowOsmRail) {
        return planWithWaypoints(fromLatWgs, fromLngWgs, toLatWgs, toLngWgs, null, travelMode, true, allowOsmRail);
    }

    /**
     * 带途经点的导航规划。
     * <p>入参坐标约定：from/to 为 GCJ-02 纬度、经度；waypoints 每项为 [lng, lat]（GCJ-02）。
     * 当 {@code coordsAreWgs84=true} 时，from/to 按 WGS84 转换（waypoints 仍视为已是 GCJ）。
     */
    public TrackRoutePlanResult planWithWaypoints(double fromLat, double fromLng,
                                                  double toLat, double toLng,
                                                  List<double[]> waypointsGcjLngLat,
                                                  String travelMode,
                                                  boolean coordsAreWgs84) {
        return planWithWaypoints(fromLat, fromLng, toLat, toLng, waypointsGcjLngLat, travelMode, coordsAreWgs84, true);
    }

    public TrackRoutePlanResult planWithWaypoints(double fromLat, double fromLng,
                                                  double toLat, double toLng,
                                                  List<double[]> waypointsGcjLngLat,
                                                  String travelMode,
                                                  boolean coordsAreWgs84,
                                                  boolean allowOsmRail) {
        TrackRoutePlanResult result = new TrackRoutePlanResult();
        String mode = normalizeMode(travelMode);
        result.setTravelMode(mode);

        String key = webKey();
        double fromLngGcj;
        double fromLatGcj;
        double toLngGcj;
        double toLatGcj;
        double fromLatWgs;
        double fromLngWgs;
        double toLatWgs;
        double toLngWgs;
        if (coordsAreWgs84) {
            fromLatWgs = fromLat;
            fromLngWgs = fromLng;
            toLatWgs = toLat;
            toLngWgs = toLng;
            double[] from = CoordTransformUtils.wgs84ToGcj02(fromLng, fromLat);
            double[] to = CoordTransformUtils.wgs84ToGcj02(toLng, toLat);
            fromLngGcj = round6(from[0]);
            fromLatGcj = round6(from[1]);
            toLngGcj = round6(to[0]);
            toLatGcj = round6(to[1]);
        } else {
            fromLatGcj = round6(fromLat);
            fromLngGcj = round6(fromLng);
            toLatGcj = round6(toLat);
            toLngGcj = round6(toLng);
            double[] fromWgs = CoordTransformUtils.gcj02ToWgs84(fromLngGcj, fromLatGcj);
            double[] toWgs = CoordTransformUtils.gcj02ToWgs84(toLngGcj, toLatGcj);
            fromLngWgs = fromWgs[0];
            fromLatWgs = fromWgs[1];
            toLngWgs = toWgs[0];
            toLatWgs = toWgs[1];
        }

        if ("flight".equals(mode)) {
            result.setPath(greatCircle(fromLatGcj, fromLngGcj, toLatGcj, toLngGcj, 48));
            result.setMessage("飞机航线采用大圆航线近似");
            return result;
        }

        // 火车/高铁/地铁：优先 OSM（批量全轨贴路关闭，避免逐段打 Overpass）
        if (allowOsmRail && ("hsr".equals(mode) || "train".equals(mode) || "metro".equals(mode))) {
            TrackRoutePlanResult osm = osmRailwayRouteService.route(
                    fromLatWgs, fromLngWgs, toLatWgs, toLngWgs, mode);
            if (isUsefulOsmPath(osm, fromLatGcj, fromLngGcj, toLatGcj, toLngGcj)) {
                return osm;
            }
            log.info("OSM railway skip mode={} reason={}", mode, osm.getMessage());
        }

        if (StringUtils.isEmpty(key)) {
            result.setMessage("未配置 album.map.webKey（高德 Web Key），无法规划真实路线");
            result.setPath(latLngPath(fromLatGcj, fromLngGcj, toLatGcj, toLngGcj));
            return result;
        }

        try {
            if ("drive".equals(mode) || "other".equals(mode)) {
                return fillDriving(result, key, fromLngGcj, fromLatGcj, toLngGcj, toLatGcj, waypointsGcjLngLat);
            }
            if ("walk".equals(mode)) {
                return fillWalkingVia(result, key, fromLngGcj, fromLatGcj, toLngGcj, toLatGcj, waypointsGcjLngLat);
            }
            if ("bike".equals(mode)) {
                if (waypointsGcjLngLat == null || waypointsGcjLngLat.isEmpty()) {
                    return fillBicycling(result, key, fromLngGcj, fromLatGcj, toLngGcj, toLatGcj);
                }
                return fillWalkingVia(result, key, fromLngGcj, fromLatGcj, toLngGcj, toLatGcj, waypointsGcjLngLat);
            }
            // bus / metro / hsr / train -> 公交/轨道交通综合规划（忽略途经点）
            return fillTransit(result, key, fromLngGcj, fromLatGcj, toLngGcj, toLatGcj, mode);
        } catch (Exception e) {
            log.warn("Amap route plan failed mode={}: {}", mode, e.getMessage());
            result.setMessage("路线规划失败，已回退直线：" + e.getMessage());
            result.setPath(latLngPath(fromLatGcj, fromLngGcj, toLatGcj, toLngGcj));
            return result;
        }
    }

    /** OSM 折线是否明显好于两点直线（至少 3 个点，或里程明显长于直线） */
    private boolean isUsefulOsmPath(TrackRoutePlanResult osm,
                                    double fromLatGcj, double fromLngGcj,
                                    double toLatGcj, double toLngGcj) {
        if (osm == null || !osm.hasPath()) {
            return false;
        }
        List<double[]> path = osm.getPath();
        if (path.size() >= 3) {
            return true;
        }
        if (path.size() < 2) {
            return false;
        }
        double straight = GeoDistanceUtils.haversineKm(fromLatGcj, fromLngGcj, toLatGcj, toLngGcj) * 1000;
        Double meters = osm.getDistanceMeters();
        return meters != null && straight > 500 && meters > straight * 1.08;
    }

    /**
     * 步行 API 无途经点参数：按锚点顺序逐段步行再拼接，效果等同“导航途经拍照点”。
     */
    private TrackRoutePlanResult fillWalkingVia(TrackRoutePlanResult result, String key,
                                                double fromLng, double fromLat, double toLng, double toLat,
                                                List<double[]> waypointsGcjLngLat) throws Exception {
        List<double[]> stops = new ArrayList<double[]>();
        stops.add(new double[]{fromLng, fromLat});
        if (waypointsGcjLngLat != null) {
            for (double[] wp : waypointsGcjLngLat) {
                if (wp != null && wp.length >= 2) {
                    stops.add(new double[]{round6(wp[0]), round6(wp[1])});
                }
            }
        }
        stops.add(new double[]{toLng, toLat});

        List<double[]> path = new ArrayList<double[]>();
        double dist = 0;
        double dur = 0;
        for (int i = 0; i < stops.size() - 1; i++) {
            double[] a = stops.get(i);
            double[] b = stops.get(i + 1);
            TrackRoutePlanResult hop = new TrackRoutePlanResult();
            fillWalking(hop, key, a[0], a[1], b[0], b[1]);
            appendUnique(path, hop.getPath());
            if (hop.getDistanceMeters() != null) {
                dist += hop.getDistanceMeters();
            }
            if (hop.getDurationSeconds() != null) {
                dur += hop.getDurationSeconds();
            }
        }
        result.setPath(path);
        result.setDistanceMeters(dist);
        result.setDurationSeconds(dur);
        if (!result.hasPath()) {
            throw new IllegalStateException("步行折线为空");
        }
        return result;
    }

    private TrackRoutePlanResult fillWalking(TrackRoutePlanResult result, String key,
                                             double fromLng, double fromLat, double toLng, double toLat) throws Exception {
        String url = "https://restapi.amap.com/v3/direction/walking?origin="
                + fromLng + "," + fromLat + "&destination=" + toLng + "," + toLat
                + "&key=" + urlEncode(key);
        JSONObject json = getJson(url);
        assertOk(json);
        JSONObject route = json.getJSONObject("route");
        JSONArray paths = route == null ? null : route.getJSONArray("paths");
        if (paths == null || paths.isEmpty()) {
            throw new IllegalStateException("步行路线为空");
        }
        JSONObject path0 = paths.getJSONObject(0);
        result.setDistanceMeters(asDouble(path0.getString("distance")));
        result.setDurationSeconds(asDouble(path0.getString("duration")));
        result.setPath(extractStepsPolyline(path0.getJSONArray("steps")));
        if (!result.hasPath()) {
            throw new IllegalStateException("步行折线为空");
        }
        return result;
    }

    private TrackRoutePlanResult fillDriving(TrackRoutePlanResult result, String key,
                                             double fromLng, double fromLat, double toLng, double toLat,
                                             List<double[]> waypointsGcjLngLat) throws Exception {
        StringBuilder url = new StringBuilder("https://restapi.amap.com/v3/direction/driving?origin=")
                .append(fromLng).append(",").append(fromLat)
                .append("&destination=").append(toLng).append(",").append(toLat)
                .append("&extensions=base&key=").append(urlEncode(key));
        if (waypointsGcjLngLat != null && !waypointsGcjLngLat.isEmpty()) {
            StringBuilder wp = new StringBuilder();
            int n = Math.min(16, waypointsGcjLngLat.size());
            for (int i = 0; i < n; i++) {
                double[] p = waypointsGcjLngLat.get(i);
                if (p == null || p.length < 2) {
                    continue;
                }
                if (wp.length() > 0) {
                    wp.append(";");
                }
                wp.append(round6(p[0])).append(",").append(round6(p[1]));
            }
            if (wp.length() > 0) {
                url.append("&waypoints=").append(urlEncode(wp.toString()));
            }
        }
        JSONObject json = getJson(url.toString());
        assertOk(json);
        JSONObject route = json.getJSONObject("route");
        JSONArray paths = route == null ? null : route.getJSONArray("paths");
        if (paths == null || paths.isEmpty()) {
            throw new IllegalStateException("驾车路线为空");
        }
        JSONObject path0 = paths.getJSONObject(0);
        result.setDistanceMeters(asDouble(path0.getString("distance")));
        result.setDurationSeconds(asDouble(path0.getString("duration")));
        result.setPath(extractStepsPolyline(path0.getJSONArray("steps")));
        if (!result.hasPath()) {
            throw new IllegalStateException("驾车折线为空");
        }
        return result;
    }

    private TrackRoutePlanResult fillBicycling(TrackRoutePlanResult result, String key,
                                               double fromLng, double fromLat, double toLng, double toLat) throws Exception {
        String url = "https://restapi.amap.com/v4/direction/bicycling?origin="
                + fromLng + "," + fromLat + "&destination=" + toLng + "," + toLat
                + "&key=" + urlEncode(key);
        JSONObject json = getJson(url);
        // v4: errcode=0
        Integer errcode = json.getInteger("errcode");
        if (errcode != null && errcode != 0) {
            throw new IllegalStateException(json.getString("errmsg"));
        }
        JSONObject data = json.getJSONObject("data");
        JSONArray paths = data == null ? null : data.getJSONArray("paths");
        if (paths == null || paths.isEmpty()) {
            // 部分账号无骑行权限时回退步行
            return fillWalking(result, key, fromLng, fromLat, toLng, toLat);
        }
        JSONObject path0 = paths.getJSONObject(0);
        result.setDistanceMeters(asDouble(path0.getString("distance")));
        result.setDurationSeconds(asDouble(path0.getString("duration")));
        result.setPath(extractStepsPolyline(path0.getJSONArray("steps")));
        if (!result.hasPath()) {
            throw new IllegalStateException("骑行折线为空");
        }
        return result;
    }

    private TrackRoutePlanResult fillTransit(TrackRoutePlanResult result, String key,
                                             double fromLng, double fromLat, double toLng, double toLat,
                                             String mode) throws Exception {
        String city = regeoCity(key, fromLng, fromLat);
        String cityd = regeoCity(key, toLng, toLat);
        if (StringUtils.isEmpty(city)) {
            city = "全国";
        }
        // strategy: 0 最快捷；地铁优先用 0，火车/高铁也走综合公交
        String url = "https://restapi.amap.com/v3/direction/transit/integrated?origin="
                + fromLng + "," + fromLat + "&destination=" + toLng + "," + toLat
                + "&city=" + urlEncode(city)
                + "&cityd=" + urlEncode(StringUtils.isEmpty(cityd) ? city : cityd)
                + "&strategy=0&extensions=all&key=" + urlEncode(key);
        JSONObject json = getJson(url);
        assertOk(json);
        JSONObject route = json.getJSONObject("route");
        JSONArray transits = route == null ? null : route.getJSONArray("transits");
        if (transits == null || transits.isEmpty()) {
            // 公交失败时：高铁/火车回退驾车（路网近似），地铁/公交回退步行
            if ("hsr".equals(mode) || "train".equals(mode)) {
                TrackRoutePlanResult drive = fillDriving(result, key, fromLng, fromLat, toLng, toLat, null);
                drive.setMessage("未找到轨道交通方案，已用驾车路网近似");
                return drive;
            }
            TrackRoutePlanResult walk = fillWalking(result, key, fromLng, fromLat, toLng, toLat);
            walk.setMessage("未找到公交方案，已用步行路线");
            return walk;
        }

        JSONObject best = pickTransit(transits, mode);
        result.setDistanceMeters(asDouble(best.getString("distance")));
        result.setDurationSeconds(asDouble(best.getString("duration")));
        List<double[]> path = extractTransitPolyline(best.getJSONArray("segments"));
        if (path.size() < 2) {
            path = latLngPath(fromLat, fromLng, toLat, toLng);
            result.setMessage("公交方案无完整折线，已用站点连线");
        }
        result.setPath(path);
        return result;
    }

    private JSONObject pickTransit(JSONArray transits, String mode) {
        if (!"metro".equals(mode) && !"hsr".equals(mode) && !"train".equals(mode)) {
            return transits.getJSONObject(0);
        }
        JSONObject best = transits.getJSONObject(0);
        int bestScore = scoreTransit(best, mode);
        for (int i = 1; i < transits.size(); i++) {
            JSONObject t = transits.getJSONObject(i);
            int score = scoreTransit(t, mode);
            if (score > bestScore) {
                best = t;
                bestScore = score;
            }
        }
        return best;
    }

    private int scoreTransit(JSONObject transit, String mode) {
        int score = 0;
        JSONArray segments = transit.getJSONArray("segments");
        if (segments == null) {
            return 0;
        }
        for (int i = 0; i < segments.size(); i++) {
            JSONObject seg = segments.getJSONObject(i);
            if (seg.containsKey("railway") && !isEmptyJson(seg.get("railway"))) {
                score += "hsr".equals(mode) || "train".equals(mode) ? 10 : 2;
            }
            JSONObject bus = seg.getJSONObject("bus");
            if (bus != null) {
                JSONArray lines = bus.getJSONArray("buslines");
                if (lines != null) {
                    for (int j = 0; j < lines.size(); j++) {
                        String type = lines.getJSONObject(j).getString("type");
                        if (type != null && type.contains("地铁")) {
                            score += "metro".equals(mode) ? 8 : 1;
                        } else if (type != null && (type.contains("火车") || type.contains("高铁") || type.contains("动车"))) {
                            score += "hsr".equals(mode) || "train".equals(mode) ? 10 : 1;
                        } else {
                            score += "bus".equals(mode) ? 5 : 1;
                        }
                    }
                }
            }
        }
        return score;
    }

    private List<double[]> extractTransitPolyline(JSONArray segments) {
        List<double[]> path = new ArrayList<double[]>();
        if (segments == null) {
            return path;
        }
        for (int i = 0; i < segments.size(); i++) {
            JSONObject seg = segments.getJSONObject(i);
            JSONObject walking = seg.getJSONObject("walking");
            if (walking != null) {
                appendUnique(path, extractStepsPolyline(walking.getJSONArray("steps")));
            }
            JSONObject bus = seg.getJSONObject("bus");
            if (bus != null) {
                JSONArray lines = bus.getJSONArray("buslines");
                if (lines != null) {
                    for (int j = 0; j < lines.size(); j++) {
                        appendUnique(path, parseAmapPolyline(lines.getJSONObject(j).getString("polyline")));
                    }
                }
            }
            Object railwayObj = seg.get("railway");
            if (!isEmptyJson(railwayObj) && railwayObj instanceof JSONObject) {
                JSONObject railway = (JSONObject) railwayObj;
                appendUnique(path, extractRailwayPath(railway));
            }
        }
        return path;
    }

    private List<double[]> extractRailwayPath(JSONObject railway) {
        List<double[]> path = new ArrayList<double[]>();
        String poly = railway.getString("polyline");
        if (StringUtils.isNotEmpty(poly)) {
            return parseAmapPolyline(poly);
        }
        appendLocation(path, railway.getJSONObject("departure_stop"));
        JSONArray via = railway.getJSONArray("via_stops");
        if (via != null) {
            for (int i = 0; i < via.size(); i++) {
                appendLocation(path, via.getJSONObject(i));
            }
        }
        appendLocation(path, railway.getJSONObject("arrival_stop"));
        return path;
    }

    private void appendLocation(List<double[]> path, JSONObject stop) {
        if (stop == null) {
            return;
        }
        String loc = stop.getString("location");
        if (StringUtils.isEmpty(loc)) {
            return;
        }
        String[] parts = loc.split(",");
        if (parts.length < 2) {
            return;
        }
        try {
            double lng = Double.parseDouble(parts[0]);
            double lat = Double.parseDouble(parts[1]);
            appendUnique(path, singleton(lat, lng));
        } catch (NumberFormatException ignore) {
            // skip
        }
    }

    private String regeoCity(String key, double lng, double lat) {
        try {
            String url = "https://restapi.amap.com/v3/geocode/regeo?location="
                    + lng + "," + lat + "&extensions=base&key=" + urlEncode(key);
            JSONObject json = getJson(url);
            if (!"1".equals(String.valueOf(json.get("status")))) {
                return null;
            }
            JSONObject regeocode = json.getJSONObject("regeocode");
            JSONObject addr = regeocode == null ? null : regeocode.getJSONObject("addressComponent");
            if (addr == null) {
                return null;
            }
            String city = asPlainString(addr.get("city"));
            if (StringUtils.isEmpty(city)) {
                city = asPlainString(addr.get("province"));
            }
            return city;
        } catch (Exception e) {
            log.debug("regeo failed: {}", e.getMessage());
            return null;
        }
    }

    private List<double[]> extractStepsPolyline(JSONArray steps) {
        List<double[]> path = new ArrayList<double[]>();
        if (steps == null) {
            return path;
        }
        for (int i = 0; i < steps.size(); i++) {
            JSONObject step = steps.getJSONObject(i);
            appendUnique(path, parseAmapPolyline(step.getString("polyline")));
        }
        return path;
    }

    /** 高德 polyline: lng,lat;lng,lat -> [[lat,lng],...] */
    private List<double[]> parseAmapPolyline(String polyline) {
        List<double[]> path = new ArrayList<double[]>();
        if (StringUtils.isEmpty(polyline)) {
            return path;
        }
        String[] pairs = polyline.split(";");
        for (String pair : pairs) {
            if (StringUtils.isEmpty(pair)) {
                continue;
            }
            String[] xy = pair.split(",");
            if (xy.length < 2) {
                continue;
            }
            try {
                double lng = Double.parseDouble(xy[0]);
                double lat = Double.parseDouble(xy[1]);
                path.add(new double[]{lat, lng});
            } catch (NumberFormatException ignore) {
                // skip bad point
            }
        }
        return path;
    }

    private void appendUnique(List<double[]> target, List<double[]> extra) {
        if (extra == null || extra.isEmpty()) {
            return;
        }
        for (double[] p : extra) {
            if (target.isEmpty()) {
                target.add(p);
                continue;
            }
            double[] last = target.get(target.size() - 1);
            if (Math.abs(last[0] - p[0]) > 1e-7 || Math.abs(last[1] - p[1]) > 1e-7) {
                target.add(p);
            }
        }
    }

    private List<double[]> greatCircle(double lat1, double lng1, double lat2, double lng2, int n) {
        List<double[]> path = new ArrayList<double[]>();
        double φ1 = Math.toRadians(lat1);
        double λ1 = Math.toRadians(lng1);
        double φ2 = Math.toRadians(lat2);
        double λ2 = Math.toRadians(lng2);
        double Δ = 2 * Math.asin(Math.sqrt(
                Math.pow(Math.sin((φ2 - φ1) / 2), 2)
                        + Math.cos(φ1) * Math.cos(φ2) * Math.pow(Math.sin((λ2 - λ1) / 2), 2)));
        if (Δ < 1e-10) {
            return latLngPath(lat1, lng1, lat2, lng2);
        }
        for (int i = 0; i <= n; i++) {
            double f = i * 1.0 / n;
            double A = Math.sin((1 - f) * Δ) / Math.sin(Δ);
            double B = Math.sin(f * Δ) / Math.sin(Δ);
            double x = A * Math.cos(φ1) * Math.cos(λ1) + B * Math.cos(φ2) * Math.cos(λ2);
            double y = A * Math.cos(φ1) * Math.sin(λ1) + B * Math.cos(φ2) * Math.sin(λ2);
            double z = A * Math.sin(φ1) + B * Math.sin(φ2);
            double φ = Math.atan2(z, Math.sqrt(x * x + y * y));
            double λ = Math.atan2(y, x);
            path.add(new double[]{Math.toDegrees(φ), Math.toDegrees(λ)});
        }
        return path;
    }

    private List<double[]> latLngPath(double lat1, double lng1, double lat2, double lng2) {
        List<double[]> path = new ArrayList<double[]>();
        path.add(new double[]{lat1, lng1});
        path.add(new double[]{lat2, lng2});
        return path;
    }

    private List<double[]> singleton(double lat, double lng) {
        List<double[]> path = new ArrayList<double[]>();
        path.add(new double[]{lat, lng});
        return path;
    }

    private String webKey() {
        if (albumProperties.getMap() == null) {
            return "";
        }
        String key = albumProperties.getMap().getWebKey();
        return key == null ? "" : key.trim();
    }

    private String normalizeMode(String travelMode) {
        if (StringUtils.isEmpty(travelMode)) {
            return "other";
        }
        return travelMode.trim().toLowerCase(Locale.ROOT);
    }

    private void assertOk(JSONObject json) {
        if (json == null || !"1".equals(String.valueOf(json.get("status")))) {
            String info = json == null ? "null" : json.getString("info");
            throw new IllegalStateException(info == null ? "高德接口失败" : info);
        }
    }

    private JSONObject getJson(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(12000);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");
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
        return JSON.parseObject(sb.toString());
    }

    private String urlEncode(String s) throws Exception {
        return URLEncoder.encode(s, "UTF-8");
    }

    private Double asDouble(String s) {
        if (StringUtils.isEmpty(s)) {
            return null;
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private double round6(double v) {
        return Math.round(v * 1_000_000d) / 1_000_000d;
    }

    private boolean isEmptyJson(Object obj) {
        if (obj == null) {
            return true;
        }
        if (obj instanceof String) {
            String s = ((String) obj).trim();
            return s.isEmpty() || "[]".equals(s) || "{}".equals(s);
        }
        if (obj instanceof JSONArray) {
            return ((JSONArray) obj).isEmpty();
        }
        if (obj instanceof JSONObject) {
            return ((JSONObject) obj).isEmpty();
        }
        if (obj instanceof Map) {
            return ((Map<?, ?>) obj).isEmpty();
        }
        if (obj instanceof List) {
            return ((List<?>) obj).isEmpty();
        }
        return false;
    }

    private String asPlainString(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof JSONArray) {
            JSONArray arr = (JSONArray) obj;
            return arr.isEmpty() ? null : String.valueOf(arr.get(0));
        }
        String s = String.valueOf(obj).trim();
        if (s.isEmpty() || "[]".equals(s)) {
            return null;
        }
        return s;
    }

    public String toRoutePathJson(List<double[]> path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        // 压缩为 LinkedHashMap 列表，避免 double[] 序列化成对象
        List<List<Double>> list = new ArrayList<List<Double>>();
        for (double[] p : path) {
            List<Double> row = new ArrayList<Double>(2);
            row.add(p[0]);
            row.add(p[1]);
            list.add(row);
        }
        return JSON.toJSONString(list);
    }

    public Map<String, Object> toPreviewMap(TrackRoutePlanResult result) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("travelMode", result.getTravelMode());
        map.put("path", JSON.parseArray(toRoutePathJson(result.getPath())));
        map.put("distanceMeters", result.getDistanceMeters());
        map.put("durationSeconds", result.getDurationSeconds());
        map.put("provider", result.getProvider());
        map.put("message", result.getMessage());
        return map;
    }
}
