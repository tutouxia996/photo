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

        geocodeStops(stops);
        TrackRoutePlanResult planned = stitchRailway(stops, travelMode);

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.putAll(amapDirectionService.toPreviewMap(planned));
        result.put("trainNo", StringUtils.isEmpty(trainNo) ? null : trainNo.trim().toUpperCase(Locale.ROOT));
        result.put("travelMode", travelMode);
        result.put("source", source);
        result.put("stops", toStopViews(stops));
        result.put("description", buildDescription(trainNo, stops));
        return result;
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
            stop.setLatWgs(toDouble(m.get("latWgs")));
            stop.setLngWgs(toDouble(m.get("lngWgs")));
            stop.setLatGcj(toDouble(m.get("latGcj") != null ? m.get("latGcj") : m.get("lat")));
            stop.setLngGcj(toDouble(m.get("lngGcj") != null ? m.get("lngGcj") : m.get("lng")));
            stop.setSequence(seq++);
            list.add(stop);
        }
        if (list.size() < 2) {
            throw new ServiceException("stops 至少需要 2 个站");
        }
        return list;
    }

    private void geocodeStops(List<TrainStop> stops) {
        for (TrainStop stop : stops) {
            if (stop.getLatWgs() != null && stop.getLngWgs() != null) {
                continue;
            }
            if (stop.getLatGcj() != null && stop.getLngGcj() != null) {
                double[] wgs = com.sq.bus.utils.CoordTransformUtils.gcj02ToWgs84(
                        stop.getLngGcj(), stop.getLatGcj());
                stop.setLngWgs(wgs[0]);
                stop.setLatWgs(wgs[1]);
                continue;
            }
            Map<String, Object> poi = findStationPoi(stop.getName());
            if (poi == null) {
                throw new ServiceException("无法定位车站：" + stop.getName() + "（请补全站名或检查高德 Key）");
            }
            stop.setLatGcj(toDouble(poi.get("lat")));
            stop.setLngGcj(toDouble(poi.get("lng")));
            stop.setLatWgs(toDouble(poi.get("wgsLat")));
            stop.setLngWgs(toDouble(poi.get("wgsLng")));
            // 用搜索到的规范名
            String poiName = str(poi.get("name"));
            if (StringUtils.isNotEmpty(poiName)) {
                stop.setName(poiName);
            }
        }
    }

    private Map<String, Object> findStationPoi(String name) {
        String base = cleanStationName(name);
        String[] keywords = new String[]{
                base.endsWith("站") ? base : base + "站",
                base + "火车站",
                base + "高铁站",
                base
        };
        for (String kw : keywords) {
            try {
                List<Map<String, Object>> list = amapPlaceSearchService.search(kw, null, 8);
                if (list == null || list.isEmpty()) {
                    continue;
                }
                Map<String, Object> best = list.get(0);
                String key = JuheTrainScheduleClient.normalizeStationKey(base);
                for (Map<String, Object> row : list) {
                    String n = JuheTrainScheduleClient.normalizeStationKey(str(row.get("name")));
                    if (n.equals(key) || n.contains(key) || key.contains(n)) {
                        return row;
                    }
                }
                return best;
            } catch (Exception e) {
                log.debug("geocode station {} failed: {}", kw, e.getMessage());
            }
        }
        return null;
    }

    private TrackRoutePlanResult stitchRailway(List<TrainStop> stops, String travelMode) {
        List<double[]> full = new ArrayList<double[]>();
        double meters = 0;
        List<String> notes = new ArrayList<String>();
        for (int i = 0; i < stops.size() - 1; i++) {
            TrainStop a = stops.get(i);
            TrainStop b = stops.get(i + 1);
            TrackRoutePlanResult seg = osmRailwayRouteService.route(
                    a.getLatWgs(), a.getLngWgs(), b.getLatWgs(), b.getLngWgs(), travelMode);
            if ((!seg.hasPath() || seg.getPath().size() < 3)
                    && ("walk".equals(travelMode) || "drive".equals(travelMode)
                    || "bike".equals(travelMode) || "bus".equals(travelMode))) {
                // 非铁路方式才回退高德路网
                seg = amapDirectionService.plan(
                        a.getLatWgs(), a.getLngWgs(), b.getLatWgs(), b.getLngWgs(), travelMode);
            }
            if (!seg.hasPath() || seg.getPath().size() < 2) {
                // 铁路方式失败：站到站直线，避免误画成公路
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
                notes.add("直线");
            } else if (StringUtils.isNotEmpty(seg.getMessage()) && seg.getMessage().contains("OSM")) {
                notes.add("OSM");
            }
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
        result.setProvider(notes.contains("OSM") ? "osm+train" : "train");
        result.setMessage("已按 " + stops.size() + " 个经停站贴轨（" + full.size() + " 点）");
        return result;
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
        return name.trim().replaceAll("\\s+", "");
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
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }
}
