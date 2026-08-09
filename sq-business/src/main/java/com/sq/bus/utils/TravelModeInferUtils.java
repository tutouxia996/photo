package com.sq.bus.utils;

/**
 * 照片轨迹自动贴路：以 GPS 点为锚点，段间调用高德导航（默认步行）。
 */
public final class TravelModeInferUtils {

    /** 短于该距离（公里）才允许直线，几乎贴在一起的点不必请求导航 */
    public static final double TINY_GAP_KM = 0.03;

    /** 短于该距离默认步行导航贴小路/园路 */
    public static final double WALK_KM = 15.0;

    private TravelModeInferUtils() {
    }

    /**
     * @param distanceKm  两点球面距离（公里）
     * @param durationSec 两点拍摄时间差（秒），未知时传 null
     */
    public static String infer(double distanceKm, Long durationSec) {
        if (distanceKm < 0) {
            distanceKm = 0;
        }
        if (distanceKm <= WALK_KM) {
            return "walk";
        }
        double hours = durationSec != null && durationSec > 0 ? durationSec / 3600.0 : -1;
        double kmh = hours > 0.05 ? distanceKm / hours : -1;
        if (kmh > 0 && kmh < 10 && distanceKm < 25) {
            return "walk";
        }
        return "drive";
    }

    /** 极近点：可不调导航，直接连 GPS */
    public static boolean isTinyGap(double distanceKm) {
        return distanceKm <= TINY_GAP_KM;
    }

    /**
     * @deprecated 用户可选手动设置地铁/高铁等，不应再据此覆盖出行方式。
     */
    @Deprecated
    public static boolean isUnstableAutoMode(String mode, double distanceKm) {
        return false;
    }
}
