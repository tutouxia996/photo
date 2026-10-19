package com.sq.bus.service.route;

import com.sq.bus.utils.CoordTransformUtils;
import com.sq.bus.utils.GeoDistanceUtils;
import com.sq.common.exception.ServiceException;
import com.sq.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 车次经停 → 站坐标 → OSM/高德贴轨拼成整段折线。
 */
@Service
public class TrainRoutePlanService {

    private static final Logger log = LoggerFactory.getLogger(TrainRoutePlanService.class);

    private static final Pattern LINE_PATTERN = Pattern.compile(
            "^\\s*(.+?)(?:\\s*[,，\\t]+\\s*|\\s+)(\\d{1,2}:\\d{2})?(?:\\s*[,，\\t]+\\s*|\\s+)?(\\d{1,2}:\\d{2})?\\s*$");

    @Autowired
    private JuheTrainScheduleClient juheTrainScheduleClient;

    @Autowired
    private AmapPlaceSearchService amapPlaceSearchService;

    @Autowired
    private OsmRailwayRouteService osmRailwayRouteService;

    @Autowired
    private AmapDirectionService amapDirectionService;

    public Map<String, Object> apiStatus() {
        return juheTrainScheduleClient.status();
    }

    public List<Map<String, Object>> queryTrains(String fromStation, String toStation, String date, String filter) {
        return juheTrainScheduleClient.queryTrains(fromStation, toStation, date, filter);
    }

    public List<TrainStop> lookupStops(String trainNo, String fromStation, String toStation) {
        return juheTrainScheduleClient.queryStopsByTrainNo(trainNo, fromStation, toStation);
    }

    /**
     * 手工经停文本解析。
     * 每行：站名 [到达] [发车]，分隔符为空格/逗号/制表符。
     * 例：清河站 08:12 08:14
     */
    public List<TrainStop> parseManualStops(String text) {
        if (StringUtils.isEmpty(text)) {
            throw new ServiceException("请填写经停站，每行一个站");
        }
        List<TrainStop> list = new ArrayList<TrainStop>();
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        int seq = 1;
        for (String raw : lines) {
            String line = raw == null ? "" : raw.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) {
                continue;
            }
            Matcher m = LINE_PATTERN.matcher(line);
            TrainStop stop = new TrainStop();
            if (m.matches()) {
                stop.setName(cleanStationName(m.group(1)));
                stop.setArriveTime(emptyToNull(m.group(2)));
                stop.setDepartTime(emptyToNull(m.group(3)));
            } else {
                stop.setName(cleanStationName(line));
            }
            if (StringUtils.isEmpty(stop.getName())) {
                continue;
            }
            stop.setSequence(seq++);
            list.add(stop);
        }
        if (list.size() < 2) {
            throw new ServiceException("至少需要 2 个经停站");
        }
        return list;
    }

    /**
     * 解析经停（API 或手工）→ 地理编码 → 逐段贴轨 → 返回预览。
     */
    public Map<String, Object> plan(Map<String, Object> body) {
        String trainNo = str(body.get("trainNo"));
        String travelMode = str(body.get("travelMode"));
        String fromStation = str(body.get("fromStation"));
        String toStation = str(body.get("toStation"));
        String manualText = str(body.get("manualStops"));

        List<TrainStop> stops;
        String source;
        if (body.get("stops") instanceof List && !((List<?>) body.get("stops")).isEmpty()) {
            stops = fromStopMaps((List<?>) body.get("stops"));
            source = "stops";
        } else if (StringUtils.isNotEmpty(manualText)) {
            stops = parseManualStops(manualText);
            source = "manual";
        } else if (StringUtils.isNotEmpty(trainNo)) {
            stops = lookupStops(trainNo, fromStation, toStation);
            source = "api";
        } else {
            throw new ServiceException("请填写车次（API）或手工经停站");
        }
        if (stops.size() < 2) {
            throw new ServiceException("经停站不足 2 个");
        }

        if (StringUtils.isEmpty(travelMode)) {
            travelMode = inferMode(trainNo);
        }
        travelMode = travelMode.toLowerCase(Locale.ROOT);

        geocodeStops(stops, travelMode);
        TrackRoutePlanResult planned;
        if (allSegmentsHaveRoutePath(stops)) {
            // 前端预览已算好的折线：原样拼回，保证「预览即所得」
            planned = assembleFromStopPaths(stops, travelMode);
        } else {
            planned = stitchRailway(stops, travelMode);
        }

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.putAll(amapDirectionService.toPreviewMap(planned));
        result.put("trainNo", StringUtils.isEmpty(trainNo) ? null : trainNo.trim().toUpperCase(Locale.ROOT));
        result.put("travelMode", travelMode);
        result.put("source", source);
        // 站间 routePath 已写入 stops，插入轨迹时可直接复用，避免再次规划漂移/回退公路
        result.put("stops", toStopViews(stops));
        result.put("description", buildDescription(trainNo, stops));
        result.put("straightSegments", planned.getMessage() != null && planned.getMessage().contains("直线")
                ? Boolean.TRUE : Boolean.FALSE);
        return result;
    }

    private boolean allSegmentsHaveRoutePath(List<TrainStop> stops) {
        if (stops == null || stops.size() < 2) {
            return false;
        }
        for (int i = 0; i < stops.size() - 1; i++) {
            if (StringUtils.isEmpty(stops.get(i).getRoutePath())) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private TrackRoutePlanResult assembleFromStopPaths(List<TrainStop> stops, String travelMode) {
        List<double[]> full = new ArrayList<double[]>();
        double meters = 0;
        for (int i = 0; i < stops.size() - 1; i++) {
            List<double[]> path = parseRoutePathJson(stops.get(i).getRoutePath());
            if (path == null || path.size() < 2) {
                return stitchRailway(stops, travelMode);
            }
            if (full.isEmpty()) {
                full.addAll(path);
            } else {
                full.addAll(path.subList(1, path.size()));
            }
            for (int j = 1; j < path.size(); j++) {
                meters += GeoDistanceUtils.haversineKm(
                        path.get(j - 1)[0], path.get(j - 1)[1],
                        path.get(j)[0], path.get(j)[1]) * 1000;
            }
        }
        TrackRoutePlanResult result = new TrackRoutePlanResult();
        result.setTravelMode(travelMode);
        result.setPath(full);
        result.setDistanceMeters(meters);
        result.setProvider("preview");
        result.setMessage("复用预览折线（" + stops.size() + " 站，" + full.size() + " 点）");
        return result;
    }

    private List<double[]> parseRoutePathJson(String json) {
        if (StringUtils.isEmpty(json)) {
            return null;
        }
        try {
            com.alibaba.fastjson2.JSONArray arr = com.alibaba.fastjson2.JSON.parseArray(json);
            if (arr == null || arr.isEmpty()) {
                return null;
            }
            List<double[]> path = new ArrayList<double[]>(arr.size());
            for (int i = 0; i < arr.size(); i++) {
                Object row = arr.get(i);
                if (row instanceof com.alibaba.fastjson2.JSONArray) {
                    com.alibaba.fastjson2.JSONArray p = (com.alibaba.fastjson2.JSONArray) row;
                    if (p.size() >= 2) {
                        path.add(new double[]{p.getDoubleValue(0), p.getDoubleValue(1)});
                    }
                } else if (row instanceof List) {
                    List<?> p = (List<?>) row;
                    if (p.size() >= 2) {
                        path.add(new double[]{
                                Double.parseDouble(String.valueOf(p.get(0))),
                                Double.parseDouble(String.valueOf(p.get(1)))
                        });
                    }
                }
            }
            return path.size() >= 2 ? path : null;
        } catch (Exception e) {
            return null;
        }
    }

    private List<TrainStop> fromStopMaps(List<?> raw) {
        List<TrainStop> list = new ArrayList<TrainStop>();
        int seq = 1;
        for (Object item : raw) {
            if (!(item instanceof Map)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) item;
            String name = str(m.get("name"));
            if (StringUtils.isEmpty(name)) {
                continue;
            }
            TrainStop stop = new TrainStop();
            stop.setName(cleanStationName(name));
            stop.setArriveTime(emptyToNull(str(m.get("arriveTime"))));
            stop.setDepartTime(emptyToNull(str(m.get("departTime"))));
            // 前端/预览用 wgsLat；个别调用方用 latWgs，两种都认
            stop.setLatWgs(firstDouble(m, "wgsLat", "latWgs"));
            stop.setLngWgs(firstDouble(m, "wgsLng", "lngWgs"));
            stop.setLatGcj(firstDouble(m, "latGcj", "lat"));
            stop.setLngGcj(firstDouble(m, "lngGcj", "lng"));
            stop.setRoutePath(str(m.get("routePath")));
            stop.setSequence(seq++);
            list.add(stop);
        }
        if (list.size() < 2) {
            throw new ServiceException("stops 至少需要 2 个站");
        }
        return list;
    }

    private void geocodeStops(List<TrainStop> stops, String travelMode) {
        boolean preferHsr = "hsr".equals(travelMode);
        for (TrainStop stop : stops) {
            if (stop.getLatWgs() != null && stop.getLngWgs() != null) {
                if (stop.getLatGcj() == null || stop.getLngGcj() == null) {
                    double[] gcj = CoordTransformUtils.wgs84ToGcj02(stop.getLngWgs(), stop.getLatWgs());
                    stop.setLngGcj(gcj[0]);
                    stop.setLatGcj(gcj[1]);
                }
                continue;
            }
            if (stop.getLatGcj() != null && stop.getLngGcj() != null) {
                double[] wgs = CoordTransformUtils.gcj02ToWgs84(
                        stop.getLngGcj(), stop.getLatGcj());
                stop.setLngWgs(wgs[0]);
                stop.setLatWgs(wgs[1]);
                continue;
            }
            // 已知枢纽站先用内置坐标，避免部署机上高德 CUQPS 把整次预览打挂
            Map<String, Object> poi = builtinStationPoi(stop.getName());
            if (poi == null) {
                poi = findStationPoi(stop.getName(), preferHsr);
            }
            if (poi == null) {
                throw new ServiceException("无法定位车站：" + stop.getName()
                        + "（可改写成「朝阳站」等官方站名，或检查高德 Key）");
            }
            Double latGcj = toDouble(poi.get("lat"));
            Double lngGcj = toDouble(poi.get("lng"));
            Double latWgs = toDouble(poi.get("wgsLat"));
            Double lngWgs = toDouble(poi.get("wgsLng"));
            if (latWgs == null || lngWgs == null) {
                if (latGcj != null && lngGcj != null) {
                    double[] wgs = CoordTransformUtils.gcj02ToWgs84(lngGcj, latGcj);
                    lngWgs = wgs[0];
                    latWgs = wgs[1];
                }
            }
            if (latGcj == null || lngGcj == null) {
                if (latWgs != null && lngWgs != null) {
                    double[] gcj = CoordTransformUtils.wgs84ToGcj02(lngWgs, latWgs);
                    lngGcj = gcj[0];
                    latGcj = gcj[1];
                }
            }
            if (latWgs == null || lngWgs == null || latGcj == null || lngGcj == null) {
                throw new ServiceException("无法定位车站：" + stop.getName() + "（搜索结果无有效坐标）");
            }
            stop.setLatGcj(latGcj);
            stop.setLngGcj(lngGcj);
            stop.setLatWgs(latWgs);
            stop.setLngWgs(lngWgs);
            String poiName = str(poi.get("name"));
            if (StringUtils.isNotEmpty(poiName)) {
                stop.setName(poiName);
            }
        }
    }

    /**
     * 优先高德「火车站」类型，再按站名匹配；高铁偏好名称含高铁/动车的 POI，
     * 避免吸到同名商业体或老站。
     */
    private Map<String, Object> findStationPoi(String name, boolean preferHsr) {
        String base = cleanStationName(name);
        List<String> keywords = stationSearchKeywords(base, preferHsr);
        // 用所有别名参与打分，避免「北京朝阳」对不上「朝阳站」
        List<String> wantKeys = new ArrayList<String>();
        for (String kw : keywords) {
            String k = JuheTrainScheduleClient.normalizeStationKey(kw);
            if (StringUtils.isNotEmpty(k) && !wantKeys.contains(k)) {
                wantKeys.add(k);
            }
        }
        Map<String, Object> bestOverall = null;
        int bestScore = Integer.MIN_VALUE;
        for (String kw : keywords) {
            try {
                List<Map<String, Object>> typed = amapPlaceSearchService.search(
                        kw, null, 12, false, "150200");
                List<Map<String, Object>> list = (typed != null && !typed.isEmpty())
                        ? typed
                        : amapPlaceSearchService.search(kw, null, 12);
                if (list == null || list.isEmpty()) {
                    continue;
                }
                for (Map<String, Object> row : list) {
                    int score = 0;
                    for (String wantKey : wantKeys) {
                        score = Math.max(score, scoreStationPoi(row, wantKey, preferHsr));
                    }
                    if (score > bestScore) {
                        bestScore = score;
                        bestOverall = row;
                    }
                }
                if (bestScore >= 90) {
                    return bestOverall;
                }
            } catch (Exception e) {
                String msg = e.getMessage() == null ? "" : e.getMessage();
                log.warn("geocode station {} failed: {}", kw, msg);
                if (msg.contains("CUQPS") || msg.contains("EXCEEDED")) {
                    log.warn("高德地点搜索 QPS 超限，停止继续搜站，改用内置坐标（若有）");
                    return null;
                }
            }
        }
        // 拒绝明显不相关的弱匹配（如只命中同名商圈）
        if (bestOverall != null && bestScore < 25) {
            log.info("discard weak station match name={} score={} poi={}",
                    name, bestScore, bestOverall.get("name"));
            return null;
        }
        return bestOverall;
    }

    /**
     * 生成搜索词：官方短名优先（朝阳站），再试「北京朝阳站」等。
     * 票面/聚合常写「北京朝阳」，高德 POI 多为「朝阳站」。
     */
    private List<String> stationSearchKeywords(String raw, boolean preferHsr) {
        LinkedHashMap<String, Boolean> ordered = new LinkedHashMap<String, Boolean>();
        String base = cleanStationName(raw);
        for (String alias : stationAliases(base)) {
            String a = cleanStationName(alias);
            if (StringUtils.isEmpty(a)) {
                continue;
            }
            ordered.put(a.endsWith("站") ? a : a + "站", Boolean.TRUE);
            ordered.put(a, Boolean.TRUE);
            if (preferHsr) {
                ordered.put(a + "高铁站", Boolean.TRUE);
            }
            ordered.put(a + "火车站", Boolean.TRUE);
        }
        return new ArrayList<String>(ordered.keySet());
    }

    private List<String> stationAliases(String name) {
        List<String> list = new ArrayList<String>();
        String base = cleanStationName(name);
        if (StringUtils.isEmpty(base)) {
            return list;
        }
        list.add(base);
        String key = JuheTrainScheduleClient.normalizeStationKey(base);
        // 常见「市名+站名」票面写法 → 官方短名
        String[][] pairs = new String[][]{
                {"北京朝阳", "朝阳"},
                {"北京丰台", "丰台"},
                {"北京清河", "清河"},
                {"北京北", "北京北"},
                {"上海虹桥", "上海虹桥"},
                {"上海松江", "松江"},
                {"天津西", "天津西"},
                {"广州南", "广州南"},
                {"深圳北", "深圳北"},
                {"星火", "朝阳"},
        };
        for (String[] p : pairs) {
            if (key.equals(p[0]) || key.equals(JuheTrainScheduleClient.normalizeStationKey(p[0]))) {
                if (!list.contains(p[1])) {
                    list.add(p[1]);
                }
                if (!list.contains(p[1] + "站")) {
                    list.add(p[1] + "站");
                }
            }
        }
        // 去掉省会前缀再搜：北京朝阳 → 朝阳
        for (String prefix : new String[]{"北京", "上海", "天津", "重庆", "广州", "深圳", "成都", "武汉", "西安", "南京", "杭州"}) {
            if (key.startsWith(JuheTrainScheduleClient.normalizeStationKey(prefix))
                    && key.length() > prefix.length()) {
                String shortName = base;
                if (shortName.startsWith(prefix)) {
                    shortName = shortName.substring(prefix.length());
                } else if (key.length() > prefix.length()) {
                    // key 已去「站」，用去掉前缀后的汉字近似
                    shortName = base.replaceFirst("^" + prefix, "");
                }
                shortName = cleanStationName(shortName);
                if (StringUtils.isNotEmpty(shortName) && !list.contains(shortName)) {
                    list.add(shortName);
                    list.add(shortName.endsWith("站") ? shortName : shortName + "站");
                }
            }
        }
        return list;
    }

    /**
     * 高德偶发搜不到时的内置坐标（WGS84）。仅作兜底，优先仍走搜索。
     */
    private Map<String, Object> builtinStationPoi(String name) {
        String key = JuheTrainScheduleClient.normalizeStationKey(name);
        // latWgs, lngWgs
        double[] ll = null;
        String official = null;
        if ("朝阳".equals(key) || "北京朝阳".equals(key) || "星火".equals(key)) {
            // 北京朝阳站（原星火站）
            ll = new double[]{39.9428, 116.5079};
            official = "朝阳站";
        } else if ("清河".equals(key) || "北京清河".equals(key)) {
            ll = new double[]{40.0416, 116.3145};
            official = "清河站";
        } else if ("丰台".equals(key) || "北京丰台".equals(key)) {
            ll = new double[]{39.8498, 116.3042};
            official = "丰台站";
        }
        if (ll == null) {
            return null;
        }
        double[] gcj = CoordTransformUtils.wgs84ToGcj02(ll[1], ll[0]);
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("name", official);
        row.put("type", "火车站");
        row.put("wgsLat", ll[0]);
        row.put("wgsLng", ll[1]);
        row.put("lat", gcj[1]);
        row.put("lng", gcj[0]);
        log.info("use builtin station coords for {} → {}", name, official);
        return row;
    }

    private int scoreStationPoi(Map<String, Object> row, String wantKey, boolean preferHsr) {
        String n = JuheTrainScheduleClient.normalizeStationKey(str(row.get("name")));
        String type = str(row.get("type"));
        String name = str(row.get("name"));
        int score = 0;
        if (n.equals(wantKey)) {
            score += 50;
        } else if (n.startsWith(wantKey) || wantKey.startsWith(n)) {
            score += 35;
        } else if (n.contains(wantKey) || wantKey.contains(n)) {
            score += 20;
        } else {
            score -= 30;
        }
        if (type != null && (type.contains("火车站") || type.contains("高铁"))) {
            score += 40;
        } else if (name != null && (name.contains("火车站") || name.contains("高铁站") || name.endsWith("站"))) {
            score += 15;
        }
        if (preferHsr) {
            if (name != null && (name.contains("高铁") || name.contains("动车"))) {
                score += 25;
            }
            // 同城老站常与高铁站同名关键词，略降「仅火车站」无高铁字样的分
            if (name != null && name.contains("火车站") && !name.contains("高铁") && !name.contains("南")
                    && !name.contains("东") && !name.contains("西") && !name.contains("北")) {
                score -= 5;
            }
        }
        return score;
    }

    private TrackRoutePlanResult stitchRailway(List<TrainStop> stops, String travelMode) {
        List<double[]> full = new ArrayList<double[]>();
        double meters = 0;
        int osmN = 0;
        int straightN = 0;
        for (int i = 0; i < stops.size() - 1; i++) {
            TrainStop a = stops.get(i);
            TrainStop b = stops.get(i + 1);
            TrackRoutePlanResult seg = routeRailSegment(
                    a.getLatWgs(), a.getLngWgs(), b.getLatWgs(), b.getLngWgs(), travelMode);
            if (!seg.hasPath() || seg.getPath().size() < 2) {
                double[] ga = CoordTransformUtils.wgs84ToGcj02(a.getLngWgs(), a.getLatWgs());
                double[] gb = CoordTransformUtils.wgs84ToGcj02(b.getLngWgs(), b.getLatWgs());
                List<double[]> straight = new ArrayList<double[]>(2);
                straight.add(new double[]{ga[1], ga[0]});
                straight.add(new double[]{gb[1], gb[0]});
                seg = new TrackRoutePlanResult();
                seg.setPath(straight);
                seg.setTravelMode(travelMode);
                seg.setProvider("straight");
                seg.setDistanceMeters(GeoDistanceUtils.haversineKm(
                        a.getLatWgs(), a.getLngWgs(), b.getLatWgs(), b.getLngWgs()) * 1000);
                seg.setMessage("该段 OSM 未连通，暂用站间直线");
                straightN++;
            } else if ("straight".equals(seg.getProvider())) {
                straightN++;
            } else {
                osmN++;
            }
            a.setRoutePath(amapDirectionService.toRoutePathJson(seg.getPath()));
            List<double[]> path = seg.getPath();
            if (full.isEmpty()) {
                full.addAll(path);
            } else {
                full.addAll(path.subList(1, path.size()));
            }
            if (seg.getDistanceMeters() != null) {
                meters += seg.getDistanceMeters();
            } else {
                meters += GeoDistanceUtils.haversineKm(
                        a.getLatWgs(), a.getLngWgs(), b.getLatWgs(), b.getLngWgs()) * 1000;
            }
        }
        TrackRoutePlanResult result = new TrackRoutePlanResult();
        result.setTravelMode(travelMode);
        result.setPath(full);
        result.setDistanceMeters(meters);
        result.setProvider(osmN > 0 ? "osm+train" : "train");
        String tip = "已按 " + stops.size() + " 个经停站贴轨（" + full.size() + " 点";
        if (osmN > 0) {
            tip += "，OSM " + osmN + " 段";
        }
        if (straightN > 0) {
            tip += "，直线 " + straightN + " 段";
        }
        tip += "）";
        result.setMessage(tip);
        return result;
    }

    /**
     * 铁路段：只走 OSM；高铁失败再试普铁图（站场引线）；绝不回退高德驾车。
     */
    private TrackRoutePlanResult routeRailSegment(double fromLat, double fromLng,
                                                  double toLat, double toLng,
                                                  String travelMode) {
        TrackRoutePlanResult seg = osmRailwayRouteService.route(
                fromLat, fromLng, toLat, toLng, travelMode);
        if (seg.hasPath() && seg.getPath().size() >= 3) {
            return seg;
        }
        if ("hsr".equals(travelMode)) {
            TrackRoutePlanResult soft = osmRailwayRouteService.route(
                    fromLat, fromLng, toLat, toLng, "train");
            if (soft.hasPath() && soft.getPath().size() >= 3) {
                soft.setTravelMode("hsr");
                soft.setMessage((soft.getMessage() == null ? "" : soft.getMessage())
                        + "；高铁段改用正线/支线连通");
                return soft;
            }
        }
        if ("walk".equals(travelMode) || "drive".equals(travelMode)
                || "bike".equals(travelMode) || "bus".equals(travelMode)) {
            return amapDirectionService.plan(fromLat, fromLng, toLat, toLng, travelMode);
        }
        return seg;
    }

    private List<Map<String, Object>> toStopViews(List<TrainStop> stops) {
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        for (TrainStop s : stops) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("name", s.getName());
            m.put("arriveTime", s.getArriveTime());
            m.put("departTime", s.getDepartTime());
            m.put("lat", s.getLatGcj());
            m.put("lng", s.getLngGcj());
            m.put("wgsLat", s.getLatWgs());
            m.put("wgsLng", s.getLngWgs());
            m.put("sequence", s.getSequence());
            if (StringUtils.isNotEmpty(s.getRoutePath())) {
                m.put("routePath", s.getRoutePath());
            }
            list.add(m);
        }
        return list;
    }

    private String buildDescription(String trainNo, List<TrainStop> stops) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.isNotEmpty(trainNo)) {
            sb.append(trainNo.trim().toUpperCase(Locale.ROOT));
        }
        TrainStop first = stops.get(0);
        TrainStop last = stops.get(stops.size() - 1);
        String time = "";
        if (StringUtils.isNotEmpty(first.getDepartTime()) || StringUtils.isNotEmpty(first.getArriveTime())) {
            String a = StringUtils.isNotEmpty(first.getDepartTime()) ? first.getDepartTime() : first.getArriveTime();
            String b = StringUtils.isNotEmpty(last.getArriveTime()) ? last.getArriveTime() : last.getDepartTime();
            if (StringUtils.isNotEmpty(a) && StringUtils.isNotEmpty(b)) {
                time = a + "–" + b;
            }
        }
        if (sb.length() > 0 && StringUtils.isNotEmpty(time)) {
            sb.append(' ').append(time);
        } else if (StringUtils.isNotEmpty(time)) {
            sb.append(time);
        }
        if (sb.length() > 0) {
            sb.append(' ');
        }
        sb.append(shortName(first.getName())).append('→').append(shortName(last.getName()));
        if (stops.size() > 2) {
            sb.append("（经停").append(stops.size()).append("站）");
        }
        return sb.toString();
    }

    private String shortName(String name) {
        if (name == null) {
            return "";
        }
        return name.replace("火车站", "").replace("高铁站", "");
    }

    public static String inferMode(String trainNo) {
        if (StringUtils.isEmpty(trainNo)) {
            return "hsr";
        }
        char c = Character.toUpperCase(trainNo.trim().charAt(0));
        if (c == 'G' || c == 'C' || c == 'D') {
            return "hsr";
        }
        if (c == 'S') {
            return "train";
        }
        return "train";
    }

    private String cleanStationName(String name) {
        if (name == null) {
            return "";
        }
        String s = name.trim().replaceAll("\\s+", "");
        s = s.replace("（出站口）", "").replace("(出站口)", "").replace("出站口", "");
        s = s.replace("（进站口）", "").replace("(进站口)", "");
        return s;
    }

    private String emptyToNull(String s) {
        return StringUtils.isEmpty(s) ? null : s.trim();
    }

    private String str(Object v) {
        return v == null ? null : String.valueOf(v).trim();
    }

    private Double toDouble(Object v) {
        if (v == null || "".equals(String.valueOf(v).trim()) || "null".equalsIgnoreCase(String.valueOf(v))) {
            return null;
        }
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }

    private Double firstDouble(Map<String, Object> m, String k1, String k2) {
        Double a = toDouble(m.get(k1));
        if (a != null) {
            return a;
        }
        return toDouble(m.get(k2));
    }
}
