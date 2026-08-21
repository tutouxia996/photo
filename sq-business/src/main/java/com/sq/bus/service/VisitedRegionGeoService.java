package com.sq.bus.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sq.bus.constants.AlbumDeleted;
import com.sq.bus.domain.BizPhoto;
import com.sq.bus.service.route.AmapDistrictService;
import com.sq.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 足迹图：按媒体坐标逆地理得到省/市/区 adcode，再拉取边界高亮。
 * <p>
 * 说明：很多 EXIF 点只有经纬度、省市区字段为空；仅靠字段名匹配会导致「有照片却无高亮」。
 */
@Service
public class VisitedRegionGeoService {

    private static final Logger log = LoggerFactory.getLogger(VisitedRegionGeoService.class);

    /** 按网格采样逆地理的上限（约 1km 网格） */
    private static final int MAX_REGEO_SAMPLES = 200;

    @Autowired
    private IBizPhotoService photoService;

    @Autowired
    private AmapDistrictService amapDistrictService;

    public Map<String, Object> buildVisitedRegionGeo() {
        try {
            VisitedAdcodes visited = collectVisitedAdcodes();
            List<Map<String, Object>> features = new ArrayList<Map<String, Object>>();
            Set<String> seen = new LinkedHashSet<String>();

            // 省 → 市 → 区，保证区县盖在上层
            addLevelFeatures(features, seen, visited.provinces, "province");
            addLevelFeatures(features, seen, visited.cities, "city");
            addLevelFeatures(features, seen, visited.districts, "district");

            Map<String, Object> geojson = new LinkedHashMap<String, Object>();
            geojson.put("type", "FeatureCollection");
            geojson.put("features", features);

            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("geojson", geojson);
            result.put("provinces", namesOf(visited.provinces));
            result.put("cities", namesOf(visited.cities));
            result.put("districts", namesOf(visited.districts));
            result.put("featureCount", features.size());
            result.put("regeoSamples", visited.regeoSamples);
            return result;
        } catch (Exception e) {
            log.error("buildVisitedRegionGeo failed: {}", e.getMessage(), e);
            Map<String, Object> empty = new LinkedHashMap<String, Object>();
            Map<String, Object> geo = new LinkedHashMap<String, Object>();
            geo.put("type", "FeatureCollection");
            geo.put("features", new ArrayList<Object>());
            empty.put("geojson", geo);
            empty.put("provinces", new ArrayList<String>());
            empty.put("cities", new ArrayList<String>());
            empty.put("districts", new ArrayList<String>());
            empty.put("featureCount", 0);
            empty.put("message", "行政区高亮暂时不可用");
            return empty;
        }
    }

    private void addLevelFeatures(List<Map<String, Object>> features, Set<String> seen,
                                  Map<String, String> adcodeToName, String level) {
        for (Map.Entry<String, String> e : adcodeToName.entrySet()) {
            String adcode = e.getKey();
            if (StringUtils.isEmpty(adcode)) {
                continue;
            }
            String dedupe = level + ":" + adcode;
            if (!seen.add(dedupe)) {
                continue;
            }
            Map<String, Object> geo = amapDistrictService.fetchBoundaryFeatureCollection(
                    adcode, level, e.getValue());
            appendFeatures(features, geo, level, e.getValue(), adcode);
        }
    }

    @SuppressWarnings("unchecked")
    private void appendFeatures(List<Map<String, Object>> out, Map<String, Object> geo,
                                String level, String name, String adcode) {
        if (geo == null || geo.isEmpty()) {
            return;
        }
        String type = asString(geo.get("type"));
        if ("Feature".equals(type)) {
            out.add(normalizeFeature(geo, level, name, adcode));
            return;
        }
        Object rawFeatures = geo.get("features");
        if (!(rawFeatures instanceof List)) {
            return;
        }
        for (Object item : (List<?>) rawFeatures) {
            if (item instanceof Map) {
                out.add(normalizeFeature((Map<String, Object>) item, level, name, adcode));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeFeature(Map<String, Object> feature, String level,
                                                 String name, String adcode) {
        Map<String, Object> props = new LinkedHashMap<String, Object>();
        Object rawProps = feature.get("properties");
        if (rawProps instanceof Map) {
            props.putAll((Map<String, Object>) rawProps);
        }
        props.put("level", level);
        if (StringUtils.isNotEmpty(name)) {
            props.put("name", name);
        }
        if (StringUtils.isNotEmpty(adcode)) {
            props.put("adcode", adcode);
        }
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("type", "Feature");
        out.put("properties", props);
        out.put("geometry", feature.get("geometry"));
        return out;
    }

    /**
     * 以坐标逆地理为主：凡是有定位的媒体所在网格都采样，拿到区县 adcode 后再推导省市。
     * 字段里的省市区仅作补充（逆地理失败时再按名称查 adcode）。
     */
    private VisitedAdcodes collectVisitedAdcodes() {
        List<BizPhoto> list = photoService.list(new LambdaQueryWrapper<BizPhoto>()
                .eq(BizPhoto::getDeleted, AlbumDeleted.NORMAL)
                .isNotNull(BizPhoto::getLatitude)
                .isNotNull(BizPhoto::getLongitude)
                .select(BizPhoto::getProvince, BizPhoto::getCity, BizPhoto::getDistrict,
                        BizPhoto::getLatitude, BizPhoto::getLongitude));
        VisitedAdcodes visited = new VisitedAdcodes();
        if (list == null || list.isEmpty()) {
            return visited;
        }

        Set<String> gridSeen = new LinkedHashSet<String>();
        Set<String> nameDistrictKeys = new LinkedHashSet<String>();
        List<String[]> nameFallback = new ArrayList<String[]>();

        for (BizPhoto photo : list) {
            BigDecimal lat = photo.getLatitude();
            BigDecimal lng = photo.getLongitude();
            if (lat == null || lng == null) {
                continue;
            }

            // ~1km 网格：同一片区域只逆地理一次
            String grid = Math.round(lat.doubleValue() * 100) + "," + Math.round(lng.doubleValue() * 100);
            if (!gridSeen.add(grid)) {
                // 该网格已处理
                continue;
            }

            boolean regeoOk = false;
            if (visited.regeoSamples < MAX_REGEO_SAMPLES) {
                Map<String, Object> regeo = amapDistrictService.regeoWgs(lat.doubleValue(), lng.doubleValue());
                if (regeo != null) {
                    visited.regeoSamples++;
                    absorbRegeo(visited, regeo);
                    regeoOk = true;
                }
            }
            if (regeoOk) {
                continue;
            }

            // 逆地理失败或超采样上限：用库里的省市区名称补
            String province = trim(photo.getProvince());
            String city = trim(photo.getCity());
            String district = trim(photo.getDistrict());
            if (district != null || city != null || province != null) {
                String key = safe(province) + "\0" + safe(city) + "\0" + safe(district);
                if (nameDistrictKeys.add(key)) {
                    nameFallback.add(new String[]{province, city, district});
                }
            }
        }

        // 名称回退（字段有值但该点未成功逆地理时）
        for (String[] row : nameFallback) {
            resolveByName(visited, row[0], row[1], row[2]);
        }
        return visited;
    }

    private void absorbRegeo(VisitedAdcodes visited, Map<String, Object> regeo) {
        String province = trim(asString(regeo.get("province")));
        String city = trim(asString(regeo.get("city")));
        String district = trim(asString(regeo.get("district")));
        String adcode = trim(asString(regeo.get("adcode")));
        if (StringUtils.isEmpty(adcode) || adcode.length() < 6) {
            // 无 adcode 时用名称查
            resolveByName(visited, province, city, district);
            return;
        }
        // 区县 adcode（6 位）；市 = 前 4 位 + 00；省 = 前 2 位 + 0000
        String districtCode = adcode.substring(0, 6);
        String cityCode = adcode.substring(0, 4) + "00";
        String provinceCode = adcode.substring(0, 2) + "0000";

        // 直辖市等：市辖区 adcode 如 110101，市级 110100
        if (district != null) {
            visited.districts.put(districtCode, district);
        } else if (city != null) {
            // 无区名时按市级处理
            visited.cities.put(cityCode, city);
        }
        if (city != null) {
            visited.cities.put(cityCode, city);
        }
        if (province != null) {
            visited.provinces.put(provinceCode, province);
        } else {
            visited.provinces.put(provinceCode, provinceCode);
        }
    }

    private void resolveByName(VisitedAdcodes visited, String province, String city, String district) {
        if (StringUtils.isNotEmpty(province)) {
            Map<String, Object> meta = amapDistrictService.fetchDistrictMeta(province, "province");
            putMeta(visited.provinces, meta, province);
        }
        if (StringUtils.isNotEmpty(city)) {
            String kw = (StringUtils.isNotEmpty(province) ? province : "") + city;
            Map<String, Object> meta = amapDistrictService.fetchDistrictMeta(kw, "city");
            if (meta == null) {
                meta = amapDistrictService.fetchDistrictMeta(city, "city");
            }
            putMeta(visited.cities, meta, city);
        }
        if (StringUtils.isNotEmpty(district)) {
            StringBuilder kw = new StringBuilder();
            if (StringUtils.isNotEmpty(province)) {
                kw.append(province);
            }
            if (StringUtils.isNotEmpty(city) && !city.equals(province)) {
                kw.append(city);
            }
            kw.append(district);
            Map<String, Object> meta = amapDistrictService.fetchDistrictMeta(kw.toString(), "district");
            if (meta == null && StringUtils.isNotEmpty(city)) {
                meta = amapDistrictService.fetchDistrictMeta(city + district, "district");
            }
            putMeta(visited.districts, meta, district);
        }
    }

    private void putMeta(Map<String, String> target, Map<String, Object> meta, String fallbackName) {
        if (meta == null) {
            return;
        }
        String adcode = trim(asString(meta.get("adcode")));
        String name = trim(asString(meta.get("name")));
        if (StringUtils.isEmpty(adcode)) {
            return;
        }
        target.put(adcode, name != null ? name : fallbackName);
    }

    private List<String> namesOf(Map<String, String> map) {
        List<String> out = new ArrayList<String>();
        Set<String> seen = new LinkedHashSet<String>();
        for (String name : map.values()) {
            if (name != null && seen.add(name)) {
                out.add(name);
            }
        }
        return out;
    }

    private String trim(String s) {
        if (StringUtils.isEmpty(s)) {
            return null;
        }
        String v = s.trim();
        return v.isEmpty() || "[]".equals(v) ? null : v;
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private String asString(Object obj) {
        return obj == null ? null : String.valueOf(obj);
    }

    private static class VisitedAdcodes {
        private final Map<String, String> provinces = new LinkedHashMap<String, String>();
        private final Map<String, String> cities = new LinkedHashMap<String, String>();
        private final Map<String, String> districts = new LinkedHashMap<String, String>();
        private int regeoSamples = 0;
    }
}
