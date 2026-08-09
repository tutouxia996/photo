package com.sq.bus.service.route;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.sq.bus.config.AlbumProperties;
import com.sq.bus.utils.CoordTransformUtils;
import com.sq.common.exception.ServiceException;
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
import java.util.Map;

/**
 * 高德地点搜索（Web 服务 Key）
 */
@Service
public class AmapPlaceSearchService {

    private static final Logger log = LoggerFactory.getLogger(AmapPlaceSearchService.class);

    @Autowired
    private AlbumProperties albumProperties;

    /**
     * @param keywords 关键词
     * @param city     城市（可选，如 北京 / 010）
     * @param offset   返回条数，默认 10，最大 25
     */
    public List<Map<String, Object>> search(String keywords, String city, Integer offset) {
        if (StringUtils.isEmpty(keywords)) {
            throw new ServiceException("搜索关键词不能为空");
        }
        String key = webKey();
        if (StringUtils.isEmpty(key)) {
            throw new ServiceException("未配置 album.map.webKey（高德 Web Key）");
        }
        int pageSize = offset == null ? 10 : Math.max(1, Math.min(25, offset));
        try {
            StringBuilder url = new StringBuilder("https://restapi.amap.com/v3/place/text?keywords=")
                    .append(urlEncode(keywords.trim()))
                    .append("&offset=").append(pageSize)
                    .append("&page=1&extensions=base&key=").append(urlEncode(key));
            if (StringUtils.isNotEmpty(city)) {
                url.append("&city=").append(urlEncode(city.trim()));
                url.append("&citylimit=false");
            }
            JSONObject json = getJson(url.toString());
            if (!"1".equals(String.valueOf(json.get("status")))) {
                String info = json.getString("info");
                throw new ServiceException(info == null ? "高德地点搜索失败" : info);
            }
            JSONArray pois = json.getJSONArray("pois");
            List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
            if (pois == null) {
                return list;
            }
            for (int i = 0; i < pois.size(); i++) {
                JSONObject poi = pois.getJSONObject(i);
                String location = poi.getString("location");
                if (StringUtils.isEmpty(location) || !location.contains(",")) {
                    continue;
                }
                String[] parts = location.split(",");
                double gcjLng = Double.parseDouble(parts[0]);
                double gcjLat = Double.parseDouble(parts[1]);
                double[] wgs = CoordTransformUtils.gcj02ToWgs84(gcjLng, gcjLat);
                Map<String, Object> row = new LinkedHashMap<String, Object>();
                row.put("id", poi.getString("id"));
                row.put("name", poi.getString("name"));
                row.put("address", buildAddress(poi));
                row.put("cityname", asPlain(poi.get("cityname")));
                row.put("adname", asPlain(poi.get("adname")));
                row.put("lng", round6(gcjLng));
                row.put("lat", round6(gcjLat));
                row.put("wgsLng", round6(wgs[0]));
                row.put("wgsLat", round6(wgs[1]));
                list.add(row);
            }
            return list;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Amap place search failed: {}", e.getMessage());
            throw new ServiceException("地点搜索失败：" + e.getMessage());
        }
    }

    private String buildAddress(JSONObject poi) {
        StringBuilder sb = new StringBuilder();
        String city = asPlain(poi.get("cityname"));
        String ad = asPlain(poi.get("adname"));
        String addr = asPlain(poi.get("address"));
        if (StringUtils.isNotEmpty(city)) {
            sb.append(city);
        }
        if (StringUtils.isNotEmpty(ad)) {
            sb.append(ad);
        }
        if (StringUtils.isNotEmpty(addr) && !"[]".equals(addr)) {
            sb.append(addr);
        }
        return sb.length() == 0 ? "" : sb.toString();
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

    private double round6(double v) {
        return Math.round(v * 1_000_000d) / 1_000_000d;
    }
}
