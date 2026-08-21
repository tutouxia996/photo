package com.sq.bus.service.route;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.sq.bus.config.AlbumProperties;
import com.sq.bus.utils.CoordTransformUtils;
import com.sq.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 高德行政区 / 逆地理（仅取 adcode 与名称，不拉巨大 polyline，避免接口超时与 OOM）
 */
@Service
public class AmapDistrictService {

    private static final Logger log = LoggerFactory.getLogger(AmapDistrictService.class);

    private final ConcurrentHashMap<String, Map<String, Object>> districtCache = new ConcurrentHashMap<String, Map<String, Object>>();
    private final ConcurrentHashMap<String, Map<String, Object>> regeoCache = new ConcurrentHashMap<String, Map<String, Object>>();
    private final ConcurrentHashMap<String, JSONObject> datavCache = new ConcurrentHashMap<String, JSONObject>();

    @Autowired
    private AlbumProperties albumProperties;

    /**
     * 按关键词查行政区元数据（extensions=base，无边界折线）。
     *
     * @return name/level/adcode；查不到返回 null
     */
    public Map<String, Object> fetchDistrictMeta(String keywords, String level) {
        if (StringUtils.isEmpty(keywords)) {
            return null;
        }
        String key = webKey();
        if (StringUtils.isEmpty(key)) {
            return null;
        }
        String cacheKey = normalize(keywords) + "|" + normalize(level);
        Map<String, Object> cached = districtCache.get(cacheKey);
        if (cached != null) {
            return cached.isEmpty() ? null : cached;
        }
        try {
            String url = "https://restapi.amap.com/v3/config/district?keywords="
                    + urlEncode(keywords.trim())
                    + "&subdistrict=0&extensions=base&key="
                    + urlEncode(key);
            JSONObject json = getJson(url);
            if (!"1".equals(String.valueOf(json.get("status")))) {
                log.warn("Amap district meta failed keywords={}: {}", keywords, json.getString("info"));
                districtCache.put(cacheKey, new LinkedHashMap<String, Object>());
                return null;
            }
            JSONArray districts = json.getJSONArray("districts");
            if (districts == null || districts.isEmpty()) {
                districtCache.put(cacheKey, new LinkedHashMap<String, Object>());
                return null;
            }
            JSONObject best = pickDistrict(districts, level);
            if (best == null) {
                districtCache.put(cacheKey, new LinkedHashMap<String, Object>());
                return null;
            }
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("name", asPlain(best.get("name")));
            row.put("level", asPlain(best.get("level")));
            row.put("adcode", asPlain(best.get("adcode")));
            districtCache.put(cacheKey, row);
            return row;
        } catch (Exception e) {
            log.warn("Amap district meta exception keywords={}: {}", keywords, e.getMessage());
            return null;
        }
    }

    /**
     * WGS84 坐标逆地理 → 省市区 + adcode（内部转 GCJ-02）。
     */
    public Map<String, Object> regeoWgs(double wgsLat, double wgsLng) {
        String key = webKey();
        if (StringUtils.isEmpty(key)) {
            return null;
        }
        // 约 1km 网格缓存，减少调用
        String cacheKey = Math.round(wgsLat * 100) + "," + Math.round(wgsLng * 100);
        Map<String, Object> cached = regeoCache.get(cacheKey);
        if (cached != null) {
            return cached.isEmpty() ? null : cached;
        }
        try {
            double[] gcj = CoordTransformUtils.wgs84ToGcj02(wgsLng, wgsLat);
            String url = "https://restapi.amap.com/v3/geocode/regeo?location="
                    + round6(gcj[0]) + "," + round6(gcj[1])
                    + "&extensions=base&key=" + urlEncode(key);
            JSONObject json = getJson(url);
            if (!"1".equals(String.valueOf(json.get("status")))) {
                regeoCache.put(cacheKey, new LinkedHashMap<String, Object>());
                return null;
            }
            JSONObject regeocode = json.getJSONObject("regeocode");
            JSONObject addr = regeocode == null ? null : regeocode.getJSONObject("addressComponent");
            if (addr == null) {
                regeoCache.put(cacheKey, new LinkedHashMap<String, Object>());
                return null;
            }
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("province", asPlain(addr.get("province")));
            String city = asPlain(addr.get("city"));
            if (StringUtils.isEmpty(city)) {
                city = asPlain(addr.get("province"));
            }
            row.put("city", city);
            row.put("district", asPlain(addr.get("district")));
            row.put("adcode", asPlain(addr.get("adcode")));
            regeoCache.put(cacheKey, row);
            return row;
        } catch (Exception e) {
            log.warn("Amap regeo exception: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 按 adcode 拉取行政区边界，返回可被 Jackson 序列化的 FeatureCollection（Map）。
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchBoundaryFeatureCollection(String adcode, String level, String name) {
        JSONObject raw = fetchBoundaryGeoJson(adcode, level, name);
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            // 转成纯 LinkedHashMap/ArrayList，避免 Fastjson JSONObject 被 Jackson 序列化异常
            return (Map<String, Object>) toPlain(raw);
        } catch (Exception e) {
            log.warn("boundary to Map failed adcode={}: {}", adcode, e.getMessage());
            return null;
        }
    }

    /** Fastjson 结构 → 纯 JDK 集合，保证 Spring MVC(Jackson) 可序列化 */
    private Object toPlain(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof JSONObject) {
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            JSONObject obj = (JSONObject) value;
            for (String key : obj.keySet()) {
                map.put(key, toPlain(obj.get(key)));
            }
            return map;
        }
        if (value instanceof JSONArray) {
            List<Object> list = new ArrayList<Object>();
            JSONArray arr = (JSONArray) value;
            for (int i = 0; i < arr.size(); i++) {
                list.add(toPlain(arr.get(i)));
            }
            return list;
        }
        if (value instanceof Map) {
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            for (Map.Entry<?, ?> e : ((Map<?, ?>) value).entrySet()) {
                map.put(String.valueOf(e.getKey()), toPlain(e.getValue()));
            }
            return map;
        }
        if (value instanceof List) {
            List<Object> list = new ArrayList<Object>();
            for (Object item : (List<?>) value) {
                list.add(toPlain(item));
            }
            return list;
        }
        return value;
    }

    /**
     * 按 adcode 拉取行政区边界 GeoJSON（优先 DataV，失败再试高德 polyline）。
     */
    private JSONObject fetchBoundaryGeoJson(String adcode, String level, String name) {
        if (StringUtils.isEmpty(adcode)) {
            return null;
        }
        String code = adcode.trim();
        JSONObject cached = datavCache.get(code);
        if (cached != null) {
            return cached.isEmpty() ? null : cached;
        }
        JSONObject fromDatav = fetchDatav(code);
        if (fromDatav != null) {
            datavCache.put(code, fromDatav);
            return fromDatav;
        }
        JSONObject fromAmap = fetchAmapPolylineAsGeoJson(code, level, name);
        if (fromAmap != null) {
            datavCache.put(code, fromAmap);
            return fromAmap;
        }
        datavCache.put(code, new JSONObject());
        return null;
    }

    private JSONObject fetchDatav(String adcode) {
        try {
            String url = "https://geo.datav.aliyun.com/areas_v3/bound/" + adcode + ".json";
            JSONObject json = getJson(url);
            if (json == null || json.isEmpty()) {
                return null;
            }
            // DataV 可能是 Feature 或 FeatureCollection
            String type = json.getString("type");
            if ("FeatureCollection".equals(type) || "Feature".equals(type)) {
                return json;
            }
            return null;
        } catch (Exception e) {
            log.warn("DataV boundary failed adcode={}: {}", adcode, e.getMessage());
            return null;
        }
    }

    private JSONObject fetchAmapPolylineAsGeoJson(String adcode, String level, String name) {
        String key = webKey();
        if (StringUtils.isEmpty(key)) {
            return null;
        }
        try {
            String keywords = StringUtils.isNotEmpty(name) ? name : adcode;
            String url = "https://restapi.amap.com/v3/config/district?keywords="
                    + urlEncode(keywords)
                    + "&subdistrict=0&extensions=all&key="
                    + urlEncode(key);
            JSONObject json = getJson(url);
            if (!"1".equals(String.valueOf(json.get("status")))) {
                return null;
            }
            JSONArray districts = json.getJSONArray("districts");
            if (districts == null || districts.isEmpty()) {
                return null;
            }
            JSONObject best = null;
            for (int i = 0; i < districts.size(); i++) {
                JSONObject d = districts.getJSONObject(i);
                if (adcode.equals(asPlain(d.get("adcode")))) {
                    best = d;
                    break;
                }
            }
            if (best == null) {
                best = pickDistrict(districts, level);
            }
            if (best == null) {
                return null;
            }
            String polyline = best.getString("polyline");
            if (StringUtils.isEmpty(polyline)) {
                return null;
            }
            return polylineToGeoJson(polyline, asPlain(best.get("name")), asPlain(best.get("adcode")), level);
        } catch (Exception e) {
            log.warn("Amap polyline boundary failed adcode={}: {}", adcode, e.getMessage());
            return null;
        }
    }

    private JSONObject polylineToGeoJson(String polyline, String name, String adcode, String level) {
        JSONArray coordinates = new JSONArray();
        String[] rings = polyline.split("\\|");
        for (String ring : rings) {
            if (StringUtils.isEmpty(ring)) {
                continue;
            }
            JSONArray coords = new JSONArray();
            for (String pair : ring.split(";")) {
                if (StringUtils.isEmpty(pair) || !pair.contains(",")) {
                    continue;
                }
                String[] xy = pair.split(",");
                if (xy.length < 2) {
                    continue;
                }
                try {
                    double lng = Double.parseDouble(xy[0].trim());
                    double lat = Double.parseDouble(xy[1].trim());
                    JSONArray pt = new JSONArray();
                    pt.add(lng);
                    pt.add(lat);
                    coords.add(pt);
                } catch (NumberFormatException ignore) {
                    // skip
                }
            }
            if (coords.size() >= 3) {
                JSONArray poly = new JSONArray();
                poly.add(coords);
                coordinates.add(poly);
            }
        }
        if (coordinates.isEmpty()) {
            return null;
        }
        JSONObject geometry = new JSONObject();
        if (coordinates.size() == 1) {
            geometry.put("type", "Polygon");
            geometry.put("coordinates", coordinates.get(0));
        } else {
            geometry.put("type", "MultiPolygon");
            geometry.put("coordinates", coordinates);
        }
        JSONObject props = new JSONObject();
        props.put("name", name);
        props.put("adcode", adcode);
        props.put("level", level);
        JSONObject feature = new JSONObject();
        feature.put("type", "Feature");
        feature.put("properties", props);
        feature.put("geometry", geometry);
        JSONObject fc = new JSONObject();
        fc.put("type", "FeatureCollection");
        JSONArray features = new JSONArray();
        features.add(feature);
        fc.put("features", features);
        return fc;
    }

    private JSONObject pickDistrict(JSONArray districts, String preferLevel) {
        String want = normalizeLevel(preferLevel);
        if (StringUtils.isNotEmpty(want)) {
            for (int i = 0; i < districts.size(); i++) {
                JSONObject d = districts.getJSONObject(i);
                if (want.equals(normalizeLevel(asPlain(d.get("level"))))) {
                    return d;
                }
            }
        }
        return districts.getJSONObject(0);
    }

    private String webKey() {
        if (albumProperties.getMap() == null) {
            return "";
        }
        String key = albumProperties.getMap().getWebKey();
        return key == null ? "" : key.trim();
    }

    private JSONObject getJson(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(20000);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");
        int code = conn.getResponseCode();
        InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if (stream == null) {
            stream = conn.getInputStream();
        }
        if (stream == null) {
            conn.disconnect();
            throw new IllegalStateException("empty http body, code=" + code);
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
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

    private String asPlain(Object obj) {
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

    private String normalize(String s) {
        return s == null ? "" : s.trim();
    }

    private String normalizeLevel(String level) {
        if (StringUtils.isEmpty(level)) {
            return "";
        }
        String v = level.trim().toLowerCase();
        if ("province".equals(v)) {
            return "province";
        }
        if ("city".equals(v)) {
            return "city";
        }
        if ("district".equals(v) || "county".equals(v)) {
            return "district";
        }
        return v;
    }

    private double round6(double v) {
        return Math.round(v * 1_000_000d) / 1_000_000d;
    }
}
