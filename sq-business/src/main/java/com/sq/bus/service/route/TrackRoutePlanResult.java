package com.sq.bus.service.route;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 轨迹路段规划结果（坐标为 GCJ-02，[lat, lng]）
 */
@Data
public class TrackRoutePlanResult {

    private String travelMode;

    /** Leaflet 友好：[[lat, lng], ...] */
    private List<double[]> path = new ArrayList<double[]>();

    /** 米 */
    private Double distanceMeters;

    /** 秒 */
    private Double durationSeconds;

    private String provider = "amap";

    private String message;

    public boolean hasPath() {
        return path != null && path.size() >= 2;
    }
}
