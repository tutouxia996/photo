package com.sq.bus.service.route;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.sq.bus.config.AlbumProperties;
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
import java.util.Locale;
import java.util.Map;

/**
 * 聚合数据等第三方火车时刻表客户端。
 * <p>无 Key / 关闭时仅手工经停可用；有 Key 时可查站到站班次与按车次经停。
 */
@Service
public class JuheTrainScheduleClient {

    private static final Logger log = LoggerFactory.getLogger(JuheTrainScheduleClient.class);

    @Autowired
    private AlbumProperties albumProperties;

    public boolean isApiEnabled() {
        AlbumProperties.TrainConfig cfg = trainCfg();
        if (cfg == null || !cfg.isEnabled()) {
            return false;
        }
        if (!"juhe".equalsIgnoreCase(String.valueOf(cfg.getProvider()))) {
            return false;
        }
        return StringUtils.isNotEmpty(cfg.getApiKey());
    }

    public Map<String, Object> status() {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        AlbumProperties.TrainConfig cfg = trainCfg();
        map.put("enabled", cfg != null && cfg.isEnabled());
        map.put("provider", cfg == null ? "none" : cfg.getProvider());
        map.put("apiReady", isApiEnabled());
        map.put("manualAlways", true);
        return map;
    }

    /**
     * 站到站查班次列表。
     */
    public List<Map<String, Object>> queryTrains(String fromStation, String toStation, String date, String filter) {
        if (!isApiEnabled()) {
            throw new ServiceException("未配置火车时刻表 API（album.map.train.apiKey），请改用手工经停");
        }
        if (StringUtils.isEmpty(fromStation) || StringUtils.isEmpty(toStation) || StringUtils.isEmpty(date)) {
            throw new ServiceException("出发站、到达站、日期不能为空");
        }
        AlbumProperties.TrainConfig cfg = trainCfg();
        try {
            StringBuilder url = new StringBuilder(trimUrl(cfg.getQueryUrl()))
                    .append(cfg.getQueryUrl().contains("?") ? "&" : "?")
                    .append("key=").append(urlEncode(cfg.getApiKey().trim()))
                    .append("&search_type=1")
                    .append("&departure_station=").append(urlEncode(fromStation.trim()))
                    .append("&arrival_station=").append(urlEncode(toStation.trim()))
                    .append("&date=").append(urlEncode(date.trim()))
                    .append("&enable_booking=2");
            if (StringUtils.isNotEmpty(filter)) {
                url.append("&filter=").append(urlEncode(filter.trim().toUpperCase(Locale.ROOT)));
            }
            JSONObject json = getJson(url.toString());
            assertJuheOk(json);
            return parseTrainList(json);
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.warn("juhe query trains failed: {}", e.getMessage());
            throw new ServiceException("班次查询失败：" + e.getMessage());
        }
    }

    /**
     * 按车次号查全程经停；可选裁剪到 fromStation～toStation。
     */
    public List<TrainStop> queryStopsByTrainNo(String trainNo, String fromStation, String toStation) {
        if (!isApiEnabled()) {
            throw new ServiceException("未配置火车时刻表 API（album.map.train.apiKey），请改用手工经停");
        }
        if (StringUtils.isEmpty(trainNo)) {
            throw new ServiceException("车次不能为空");
        }
        AlbumProperties.TrainConfig cfg = trainCfg();
        try {
            String base = trimUrl(cfg.getDetailUrl());
            String url = base
                    + (base.contains("?") ? "&" : "?")
                    + "key=" + urlEncode(cfg.getApiKey().trim())
                    + "&name=" + urlEncode(trainNo.trim().toUpperCase(Locale.ROOT));
            JSONObject json = getJson(url);
            assertJuheOk(json);
            List<TrainStop> all = parseStationList(json);
            if (all.isEmpty()) {
                throw new ServiceException("未查到车次 " + trainNo + " 的经停站");
            }
            return sliceStops(all, fromStation, toStation);
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.warn("juhe train detail failed: {}", e.getMessage());
            throw new ServiceException("经停查询失败：" + e.getMessage());
        }
    }

    private List<Map<String, Object>> parseTrainList(JSONObject json) {
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        Object result = json.get("result");
        JSONArray arr = null;
        if (result instanceof JSONArray) {
            arr = (JSONArray) result;
        } else if (result instanceof JSONObject) {
            JSONObject obj = (JSONObject) result;
            arr = firstArray(obj, "list", "trains", "data", "train_list");
        }
        if (arr == null) {
            return list;
        }
        for (int i = 0; i < arr.size(); i++) {
            JSONObject row = arr.getJSONObject(i);
            if (row == null) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            String trainNo = firstString(row, "train_no", "trainno", "train_code", "stationtraincode");
            m.put("trainNo", trainNo);
            m.put("fromStation", firstString(row, "departure_station", "start_station", "from_station", "fromstation"));
            m.put("toStation", firstString(row, "arrival_station", "end_station", "to_station", "tostation"));
            m.put("departTime", firstString(row, "departure_time", "start_time", "starttime", "depart_time"));
            m.put("arriveTime", firstString(row, "arrival_time", "arrive_time", "end_time", "arrivetime"));
            m.put("duration", firstString(row, "duration", "lishi", "cost_time"));
            m.put("trainType", firstString(row, "train_type", "traintype", "type"));
            Object flags = row.get("train_flags");
            if (flags != null) {
                m.put("flags", flags);
            }
            if (StringUtils.isNotEmpty(trainNo)) {
                list.add(m);
            }
        }
        return list;
    }

    private List<TrainStop> parseStationList(JSONObject json) {
        List<TrainStop> list = new ArrayList<TrainStop>();
        Object result = json.get("result");
        JSONArray arr = null;
        if (result instanceof JSONObject) {
            JSONObject obj = (JSONObject) result;
            arr = firstArray(obj, "station_list", "stationlist", "stations", "list", "data");
            // 部分接口 result 本身就是数组包一层
            if (arr == null && obj.get("train_no") != null) {
                arr = firstArray(obj, "station_list", "stationlist");
            }
        } else if (result instanceof JSONArray) {
            arr = (JSONArray) result;
        }
        if (arr == null) {
            return list;
        }
        for (int i = 0; i < arr.size(); i++) {
            JSONObject row = arr.getJSONObject(i);
            if (row == null) {
                continue;
            }
            String name = firstString(row, "station_name", "station", "name", "stationname");
            if (StringUtils.isEmpty(name)) {
                continue;
            }
            TrainStop stop = new TrainStop();
            stop.setName(name.trim());
            stop.setArriveTime(normalizeTime(firstString(row, "arrive_time", "arrivetime", "arrive")));
            stop.setDepartTime(normalizeTime(firstString(row, "start_time", "depart_time", "departure_time", "starttime")));
            stop.setSequence(i + 1);
            list.add(stop);
        }
        return list;
    }

    static List<TrainStop> sliceStops(List<TrainStop> all, String fromStation, String toStation) {
        if (all == null || all.isEmpty()) {
            return all;
        }
        if (StringUtils.isEmpty(fromStation) && StringUtils.isEmpty(toStation)) {
            return all;
        }
        int fromIdx = 0;
        int toIdx = all.size() - 1;
        if (StringUtils.isNotEmpty(fromStation)) {
            int idx = findStopIndex(all, fromStation);
            if (idx >= 0) {
                fromIdx = idx;
            }
        }
        if (StringUtils.isNotEmpty(toStation)) {
            int idx = findStopIndex(all, toStation);
            if (idx >= fromIdx) {
                toIdx = idx;
            }
        }
        return new ArrayList<TrainStop>(all.subList(fromIdx, toIdx + 1));
    }

    static int findStopIndex(List<TrainStop> stops, String name) {
        String key = normalizeStationKey(name);
        for (int i = 0; i < stops.size(); i++) {
            if (normalizeStationKey(stops.get(i).getName()).equals(key)) {
                return i;
            }
        }
        for (int i = 0; i < stops.size(); i++) {
            String n = normalizeStationKey(stops.get(i).getName());
            if (n.contains(key) || key.contains(n)) {
                return i;
            }
        }
        return -1;
    }

    static String normalizeStationKey(String name) {
        if (name == null) {
            return "";
        }
        String s = name.trim()
                .replace("站", "")
                .replace("火车站", "")
                .replace("高铁站", "")
                .replace(" ", "")
                .toLowerCase(Locale.ROOT);
        return s;
    }

    private String normalizeTime(String t) {
        if (StringUtils.isEmpty(t)) {
            return null;
        }
        String s = t.trim();
        if ("----".equals(s) || "--".equals(s) || "null".equalsIgnoreCase(s)) {
            return null;
        }
        return s;
    }

    private void assertJuheOk(JSONObject json) {
        if (json == null) {
            throw new ServiceException("时刻表接口返回空");
        }
        // 新接口 error_code=0；旧接口 resultcode=200
        String errorCode = String.valueOf(json.get("error_code"));
        String resultCode = String.valueOf(json.get("resultcode"));
        boolean ok = "0".equals(errorCode)
                || "200".equals(resultCode)
                || "0".equals(resultCode)
                || (json.get("error_code") == null && json.get("resultcode") == null && json.get("result") != null);
        if (!ok) {
            String reason = firstString(json, "reason", "error", "msg", "message");
            throw new ServiceException(StringUtils.isEmpty(reason) ? "时刻表接口失败" : reason);
        }
    }

    private JSONArray firstArray(JSONObject obj, String... keys) {
        for (String k : keys) {
            JSONArray arr = obj.getJSONArray(k);
            if (arr != null && !arr.isEmpty()) {
                return arr;
            }
        }
        return null;
    }

    private String firstString(JSONObject obj, String... keys) {
        for (String k : keys) {
            Object v = obj.get(k);
            if (v == null) {
                continue;
            }
            String s = String.valueOf(v).trim();
            if (!s.isEmpty() && !"null".equalsIgnoreCase(s) && !"[]".equals(s)) {
                return s;
            }
        }
        return null;
    }

    private AlbumProperties.TrainConfig trainCfg() {
        if (albumProperties.getMap() == null) {
            return null;
        }
        return albumProperties.getMap().getTrain();
    }

    private String trimUrl(String url) {
        if (StringUtils.isEmpty(url)) {
            throw new ServiceException("未配置火车 API 地址");
        }
        return url.trim();
    }

    private JSONObject getJson(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(20000);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "AlbumTrackTrain/1.0");
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
            throw new IllegalStateException("HTTP " + code);
        }
        return JSON.parseObject(sb.toString());
    }

    private String urlEncode(String s) throws Exception {
        return URLEncoder.encode(s, "UTF-8");
    }
}
