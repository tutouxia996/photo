package com.sq.bus.utils;

import com.sq.bus.utils.GpxParseUtils.GpxSample;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 照片轨迹自动贴路：以 GPS 点为锚点，段间调用高德导航（默认步行）。
 * GPX 叠层：按「连续移动片段」的速度/海拔推断出行方式。
 * <p>
 * <b>不使用</b> GPX 文件头 / metadata 中的 Activity、keywords 等标签
 * （如 walking、running），这些常不准，仅作记录仪备注。
 * 长停顿标为 stay；短停（等红绿灯）并入当前骑行/自驾/步行片段，不单独切开。
 */
public final class TravelModeInferUtils {

    /** 短于该距离（公里）才允许直线，几乎贴在一起的点不必请求导航 */
    public static final double TINY_GAP_KM = 0.03;

    /** 短于该距离默认步行导航贴小路/园路 */
    public static final double WALK_KM = 15.0;

    /**
     * 低于此速度视为停顿（原地 / GPS 漂移）。
     * 故意低于常见慢走（约 2–4 km/h），避免慢走被整段判成停留。
     */
    private static final double STOP_SPEED_KMH = 1.0;
    /**
     * 段距离过短时速度不可靠。
     * 3 秒采样、3 km/h 慢走约 2.5 m/点；过高会把慢走当成停顿。
     */
    private static final double MIN_SEG_KM = 0.0015;
    /** 段时差过短忽略 */
    private static final long MIN_SEG_MS = 1500L;
    /** 明显异常的瞬时速度上限 */
    private static final double MAX_SANE_SPEED_KMH = 400.0;
    /** 坡度绝对值超过此值视为明显坡段 */
    private static final double STEEP_GRADE = 0.04;
    /** 至少这么多有效移动段才用分布推断 */
    private static final int MIN_MOVING_SEGS = 4;

    /**
     * 连续停顿超过此时长则切开移动片段。
     * 短停（等灯）仍算同一趟骑行/步行；切开后的间隙再另判停留或步行。
     */
    private static final long BOUT_SPLIT_STOP_MS = 120_000L;
    /**
     * 连续停顿达到此时长才标为 stay（停留）；更短的停顿算步行。
     * 需明显长于等红绿灯，避免把短歇标成停留。
     */
    private static final long LONG_STAY_MS = 5 * 60_000L;
    /** 骑行片段至少要有这么多真实移动里程，避免 GPS 尖刺误判 */
    private static final double MIN_BIKE_MOVE_KM = 0.20;
    /** 骑行片段至少要有这么长真实移动时间 */
    private static final long MIN_BIKE_MOVE_MS = 60_000L;
    /** 自驾片段门槛 */
    private static final double MIN_DRIVE_MOVE_KM = 0.40;
    private static final long MIN_DRIVE_MOVE_MS = 60_000L;
    /** 骑行/自驾片段前后：局部中位速低于此视为可剥离的步行前缀/后缀 */
    private static final double PEEL_WALK_MEDIAN_KMH = 7.0;
    /** 剥离步行前缀/后缀时，局部窗口半宽 */
    private static final long PEEL_WINDOW_HALF_MS = 45_000L;
    /** 前缀/后缀至少这么长才剥离（避免骑行中短暂减速被切掉） */
    private static final long MIN_PEEL_WALK_MS = 60_000L;

    private TravelModeInferUtils() {
    }

    /** GPX 内一段连续出行方式（样本下标闭区间） */
    public static final class ModeSegment {
        public final int fromIndex;
        public final int toIndex;
        public final String travelMode;

        public ModeSegment(int fromIndex, int toIndex, String travelMode) {
            this.fromIndex = fromIndex;
            this.toIndex = toIndex;
            this.travelMode = travelMode;
        }
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

    /**
     * 将 GPX 切成多段出行方式。
     * <p>
     * 规则：
     * <ul>
     *   <li>连续停顿 &gt; 2 分钟切开移动片段；片段内短停（等灯）仍属该趟 walk/bike/drive</li>
     *   <li>每个移动片段单独判定 walk/bike/drive</li>
     *   <li>片段外间隙：连续停顿 ≥ 5 分钟才是 stay；更短停顿算 walk</li>
     *   <li>即便时长够「停留」，若路径位移明显（慢走），仍改判 walk</li>
     *   <li>骑行需足够连续移动里程/时长，避免漂移尖刺误判</li>
     *   <li>骑行/自驾片段若前后有持续慢走（先走后骑），剥离为 walk，避免整段染成骑行</li>
     * </ul>
     */
    public static List<ModeSegment> segmentGpx(List<GpxSample> samples) {
        if (samples == null || samples.size() < 2) {
            return Collections.emptyList();
        }
        int n = samples.size();
        Edge[] edges = new Edge[n - 1];
        for (int i = 0; i < edges.length; i++) {
            edges[i] = buildEdge(samples.get(i), samples.get(i + 1));
        }

        List<int[]> bouts = findMovementBouts(edges);
        // 间隙先标 stay，再把短停降为 walk；移动片段覆盖为 walk/bike/drive
        String[] edgeModes = new String[edges.length];
        for (int i = 0; i < edgeModes.length; i++) {
            edgeModes[i] = "stay";
        }

        for (int[] bout : bouts) {
            assignBoutModes(edgeModes, edges, bout[0], bout[1]);
        }
        reclassifyStayGaps(edgeModes, edges);

        List<ModeSegment> segments = mergeEdgesToSegments(edgeModes);
        if (segments.isEmpty()) {
            return Collections.singletonList(new ModeSegment(0, n - 1, "stay"));
        }
        List<ModeSegment> fixed = new ArrayList<ModeSegment>(segments.size());
        for (int i = 0; i < segments.size(); i++) {
            ModeSegment seg = segments.get(i);
            int from = i == 0 ? 0 : seg.fromIndex;
            int to = i == segments.size() - 1 ? n - 1 : seg.toIndex;
            fixed.add(new ModeSegment(from, to, seg.travelMode));
        }
        return coalesceSame(fixed);
    }

    /**
     * 将移动片段写入 edgeModes。
     * <ul>
     *   <li>整体 bike/drive：前后持续慢走剥成 walk（先走后骑）</li>
     *   <li>整体 walk：内部持续较快段提升为 bike/drive（先走后骑、长时间步行夹骑行）</li>
     * </ul>
     */
    private static void assignBoutModes(String[] edgeModes, Edge[] edges, int fromEdge, int toEdge) {
        String mode = classifyBout(edges, fromEdge, toEdge);
        if ("walk".equals(mode) || "stay".equals(mode)) {
            promoteFastInsideWalkBout(edgeModes, edges, fromEdge, toEdge);
            return;
        }
        if (!"bike".equals(mode) && !"drive".equals(mode)) {
            for (int i = fromEdge; i <= toEdge; i++) {
                edgeModes[i] = mode;
            }
            return;
        }
        int fastFrom = fromEdge;
        while (fastFrom <= toEdge && isPeelWalkWindow(edges, fastFrom, fromEdge, toEdge)) {
            fastFrom++;
        }
        int fastTo = toEdge;
        while (fastTo >= fastFrom && isPeelWalkWindow(edges, fastTo, fromEdge, toEdge)) {
            fastTo--;
        }
        // 前缀/后缀过短则不剥，避免骑行中短暂减速被切开
        long prefixMs = spanMs(edges, fromEdge, fastFrom - 1);
        long suffixMs = spanMs(edges, fastTo + 1, toEdge);
        if (prefixMs < MIN_PEEL_WALK_MS) {
            fastFrom = fromEdge;
        }
        if (suffixMs < MIN_PEEL_WALK_MS) {
            fastTo = toEdge;
        }
        // 剥完后中间必须仍够骑行门槛，否则整段保持原判定
        if (fastFrom > fastTo || !isBoutStillFastEnough(edges, fastFrom, fastTo, mode)) {
            for (int i = fromEdge; i <= toEdge; i++) {
                edgeModes[i] = mode;
            }
            return;
        }
        for (int i = fromEdge; i < fastFrom; i++) {
            edgeModes[i] = "walk";
        }
        for (int i = fastFrom; i <= fastTo; i++) {
            edgeModes[i] = mode;
        }
        for (int i = fastTo + 1; i <= toEdge; i++) {
            edgeModes[i] = "walk";
        }
    }

    /**
     * 长步行片段内找出持续较快的子段，提升为 bike/drive（园博园傍晚骑行这类）。
     */
    private static void promoteFastInsideWalkBout(String[] edgeModes, Edge[] edges,
                                                 int fromEdge, int toEdge) {
        for (int i = fromEdge; i <= toEdge; i++) {
            edgeModes[i] = "walk";
        }
        int i = fromEdge;
        while (i <= toEdge) {
            if (isPeelWalkWindow(edges, i, fromEdge, toEdge)) {
                i++;
                continue;
            }
            int j = i;
            while (j <= toEdge && !isPeelWalkWindow(edges, j, fromEdge, toEdge)) {
                j++;
            }
            int hi = j - 1;
            long dur = spanMs(edges, i, hi);
            String fastMode = classifyBout(edges, i, hi);
            if (dur >= MIN_BIKE_MOVE_MS
                    && ("bike".equals(fastMode) || "drive".equals(fastMode))) {
                for (int k = i; k <= hi; k++) {
                    edgeModes[k] = fastMode;
                }
            }
            i = j;
        }
    }

    private static long spanMs(Edge[] edges, int from, int to) {
        if (from > to || from < 0 || to >= edges.length) {
            return 0L;
        }
        long ms = 0L;
        for (int i = from; i <= to; i++) {
            ms += Math.max(edges[i].dtMs, 0L);
        }
        return ms;
    }

    private static boolean isBoutStillFastEnough(Edge[] edges, int from, int to, String mode) {
        String again = classifyBout(edges, from, to);
        if ("bike".equals(mode)) {
            return "bike".equals(again) || "drive".equals(again);
        }
        return mode.equals(again);
    }

    /** 以 center 为中心的局部窗口是否像慢走（可从骑行/自驾片段剥离） */
    private static boolean isPeelWalkWindow(Edge[] edges, int center, int boutFrom, int boutTo) {
        // 用累计时间近似窗口：向两侧扩到半宽
        List<Double> speeds = new ArrayList<Double>();
        long before = 0L;
        for (int i = center; i >= boutFrom; i--) {
            Edge e = edges[i];
            if (!e.stop && e.speedKmh >= STOP_SPEED_KMH && e.distKm >= MIN_SEG_KM) {
                speeds.add(e.speedKmh);
            }
            if (i < center) {
                before += e.dtMs;
                if (before >= PEEL_WINDOW_HALF_MS) {
                    break;
                }
            }
        }
        long after = 0L;
        for (int i = center + 1; i <= boutTo; i++) {
            Edge e = edges[i];
            after += e.dtMs;
            if (!e.stop && e.speedKmh >= STOP_SPEED_KMH && e.distKm >= MIN_SEG_KM) {
                speeds.add(e.speedKmh);
            }
            if (after >= PEEL_WINDOW_HALF_MS) {
                break;
            }
        }
        if (speeds.size() < 2) {
            // 几乎不动：当作可剥离的步行侧
            return true;
        }
        Collections.sort(speeds);
        double med = percentile(speeds, 0.50);
        return med < PEEL_WALK_MEDIAN_KMH;
    }

    /**
     * 修正 stay 间隙：
     * <ul>
     *   <li>时长 &lt; {@link #LONG_STAY_MS} → walk（短歇）</li>
     *   <li>时长够长，但路径里程/均速像慢走（非原地漂移）→ walk</li>
     * </ul>
     */
    private static void reclassifyStayGaps(String[] edgeModes, Edge[] edges) {
        int i = 0;
        while (i < edgeModes.length) {
            if (!"stay".equals(edgeModes[i])) {
                i++;
                continue;
            }
            int j = i;
            long durMs = 0L;
            double pathKm = 0D;
            while (j < edgeModes.length && "stay".equals(edgeModes[j])) {
                durMs += Math.max(edges[j].dtMs, 0L);
                pathKm += Math.max(edges[j].distKm, 0D);
                j++;
            }
            boolean shortStay = durMs < LONG_STAY_MS;
            // 慢走：5 分钟走 100m+（约 ≥1.2 km/h），或累计超过 150m
            double hours = durMs > 0 ? durMs / 3600000.0 : 0D;
            double pathKmh = hours > 0 ? pathKm / hours : 0D;
            boolean slowWalk = pathKm >= 0.15 || (pathKm >= 0.10 && pathKmh >= 1.2);
            if (shortStay || slowWalk) {
                for (int k = i; k < j; k++) {
                    edgeModes[k] = "walk";
                }
            }
            i = j;
        }
    }

    /**
     * 找出连续移动片段：边索引闭区间 [fromEdge, toEdge]。
     * 中间允许短停顿（&lt; {@link #BOUT_SPLIT_STOP_MS}）；长停顿切开。
     */
    private static List<int[]> findMovementBouts(Edge[] edges) {
        List<int[]> bouts = new ArrayList<int[]>();
        int boutStart = -1;
        int lastMoving = -1;
        long stopRunMs = 0L;

        for (int i = 0; i < edges.length; i++) {
            Edge e = edges[i];
            boolean moving = !e.stop && e.speedKmh >= STOP_SPEED_KMH && e.distKm >= MIN_SEG_KM;
            if (moving) {
                if (boutStart < 0) {
                    boutStart = i;
                } else if (stopRunMs >= BOUT_SPLIT_STOP_MS) {
                    // 长停之后的新移动：结束上一段（到 lastMoving）
                    if (lastMoving >= boutStart) {
                        bouts.add(new int[]{boutStart, lastMoving});
                    }
                    boutStart = i;
                }
                lastMoving = i;
                stopRunMs = 0L;
            } else if (boutStart >= 0) {
                stopRunMs += Math.max(e.dtMs, 0L);
            }
        }
        if (boutStart >= 0 && lastMoving >= boutStart) {
            bouts.add(new int[]{boutStart, lastMoving});
        }
        return bouts;
    }

    /** 对单个移动片段分类；里程/时长不够的「骑行样」尖刺降为步行 */
    private static String classifyBout(Edge[] edges, int fromEdge, int toEdge) {
        List<Double> moving = new ArrayList<Double>();
        List<Double> cruise = new ArrayList<Double>();
        double moveKm = 0D;
        long moveMs = 0L;
        long stopMs = 0L;
        int steepUp = 0;
        int steepDown = 0;

        for (int i = fromEdge; i <= toEdge; i++) {
            Edge e = edges[i];
            if (e.stop || e.speedKmh < STOP_SPEED_KMH || e.distKm < MIN_SEG_KM) {
                stopMs += e.dtMs;
                continue;
            }
            moving.add(e.speedKmh);
            moveKm += e.distKm;
            moveMs += e.dtMs;
            if (e.slope > 0) {
                steepUp++;
            } else if (e.slope < 0) {
                steepDown++;
            } else {
                cruise.add(e.speedKmh);
            }
        }
        if (moving.size() < 2 || moveKm < 0.03) {
            return "walk";
        }
        Collections.sort(moving);
        Collections.sort(cruise);
        double stopRatio = (moveMs + stopMs) > 0 ? stopMs * 1.0 / (moveMs + stopMs) : 0D;
        long durationSec = Math.max(0L, (moveMs + stopMs) / 1000L);
        double avg = durationSec > 0 ? moveKm / (durationSec / 3600.0) : 0D;
        SpeedProfile profile = new SpeedProfile(moving, cruise, moveKm, durationSec, avg, stopRatio,
                steepUp, steepDown);
        String mode = classifyByProfile(profile);

        // 硬门槛：骑行/自驾必须是「像样的一趟」，否则算步行（含原地晃、短挪）
        if ("bike".equals(mode) && (moveKm < MIN_BIKE_MOVE_KM || moveMs < MIN_BIKE_MOVE_MS)) {
            return "walk";
        }
        if ("drive".equals(mode) && (moveKm < MIN_DRIVE_MOVE_KM || moveMs < MIN_DRIVE_MOVE_MS)) {
            // 短距离高速尖刺：更可能是骑行冲刺或噪声
            if (moveKm >= MIN_BIKE_MOVE_KM && moveMs >= MIN_BIKE_MOVE_MS) {
                return "bike";
            }
            return "walk";
        }
        return mode;
    }

    /**
     * 全程主出行方式：按分段里程加权多数；样本不足时回退均速。
     */
    public static String inferFromGpx(List<GpxSample> samples) {
        if (samples == null || samples.size() < 2) {
            return "walk";
        }
        List<ModeSegment> segments = segmentGpx(samples);
        Map<String, Double> distByMode = new HashMap<String, Double>();
        for (ModeSegment seg : segments) {
            // 停留折线常含 GPS 漂移，主方式只看真实移动段
            if ("stay".equals(seg.travelMode)) {
                continue;
            }
            double d = pathDistanceKm(samples, seg.fromIndex, seg.toIndex);
            Double prev = distByMode.get(seg.travelMode);
            distByMode.put(seg.travelMode, (prev == null ? 0D : prev) + d);
        }
        String best = null;
        double bestDist = -1;
        for (Map.Entry<String, Double> e : distByMode.entrySet()) {
            if (e.getValue() > bestDist) {
                bestDist = e.getValue();
                best = e.getKey();
            }
        }
        return best != null ? best : inferWholeProfile(samples);
    }

    private static String inferWholeProfile(List<GpxSample> samples) {
        SpeedProfile profile = buildSpeedProfile(samples, 0, samples.size() - 1);
        if (profile.movingSpeeds.size() < MIN_MOVING_SEGS) {
            return inferFromGpx(profile.avgSpeedKmh, profile.distanceKm, profile.durationSec);
        }
        return classifyByProfile(profile);
    }

    /**
     * 根据 GPX 平均速度推断出行方式（样本不足时的回退）。
     */
    public static String inferFromGpx(double avgSpeedKmh, double distanceKm, Long durationSec) {
        double kmh = avgSpeedKmh;
        if ((kmh <= 0 || Double.isNaN(kmh) || Double.isInfinite(kmh))
                && durationSec != null && durationSec > 0 && distanceKm > 0) {
            kmh = distanceKm / (durationSec / 3600.0);
        }
        if (kmh <= 0 || Double.isNaN(kmh) || Double.isInfinite(kmh)) {
            return infer(distanceKm, durationSec);
        }
        if (kmh < 6.5) {
            return "walk";
        }
        if (kmh < 22) {
            return "bike";
        }
        if (kmh < 85) {
            return "drive";
        }
        if (kmh < 140) {
            return "train";
        }
        if (kmh < 320) {
            return "hsr";
        }
        return "flight";
    }

    private static List<ModeSegment> mergeEdgesToSegments(String[] modes) {
        List<ModeSegment> list = new ArrayList<ModeSegment>();
        if (modes.length == 0) {
            return list;
        }
        int startEdge = 0;
        String cur = modes[0];
        for (int i = 1; i < modes.length; i++) {
            if (modes[i].equals(cur)) {
                continue;
            }
            // 边 startEdge..i-1 → 样本 startEdge..i
            list.add(new ModeSegment(startEdge, i, cur));
            startEdge = i;
            cur = modes[i];
        }
        list.add(new ModeSegment(startEdge, modes.length, cur));
        return list;
    }

    private static List<ModeSegment> coalesceSame(List<ModeSegment> segments) {
        if (segments.isEmpty()) {
            return segments;
        }
        List<ModeSegment> out = new ArrayList<ModeSegment>();
        ModeSegment prev = segments.get(0);
        for (int i = 1; i < segments.size(); i++) {
            ModeSegment s = segments.get(i);
            if (s.travelMode.equals(prev.travelMode) && s.fromIndex <= prev.toIndex + 1) {
                prev = new ModeSegment(prev.fromIndex, Math.max(prev.toIndex, s.toIndex), prev.travelMode);
            } else {
                out.add(prev);
                prev = s;
            }
        }
        out.add(prev);
        return out;
    }

    private static double pathDistanceKm(List<GpxSample> samples, int from, int to) {
        double d = 0D;
        int end = Math.min(to, samples.size() - 1);
        for (int i = from + 1; i <= end; i++) {
            GpxSample a = samples.get(i - 1);
            GpxSample b = samples.get(i);
            d += GeoDistanceUtils.haversineKm(a.latWgs, a.lngWgs, b.latWgs, b.lngWgs);
        }
        return d;
    }

    private static Edge buildEdge(GpxSample a, GpxSample b) {
        long dtMs = Math.max(0L, b.timeMs - a.timeMs);
        double distKm = GeoDistanceUtils.haversineKm(a.latWgs, a.lngWgs, b.latWgs, b.lngWgs);
        if (distKm < 0 || Double.isNaN(distKm)) {
            distKm = 0D;
        }
        double speed = 0D;
        boolean stop = true;
        int slope = 0;
        if (dtMs >= MIN_SEG_MS && distKm >= MIN_SEG_KM) {
            speed = distKm / (dtMs / 3600000.0);
            if (Double.isNaN(speed) || Double.isInfinite(speed) || speed <= 0 || speed > MAX_SANE_SPEED_KMH) {
                speed = 0D;
                stop = true;
            } else {
                stop = speed < STOP_SPEED_KMH;
            }
        } else if (distKm < MIN_SEG_KM) {
            stop = true;
        }
        if (a.ele != null && b.ele != null && distKm > 0) {
            double grade = (b.ele - a.ele) / (distKm * 1000.0);
            if (grade >= STEEP_GRADE) {
                slope = 1;
            } else if (grade <= -STEEP_GRADE) {
                slope = -1;
            }
        }
        return new Edge(dtMs, distKm, speed, stop, slope != 0, slope);
    }

    private static String classifyByProfile(SpeedProfile p) {
        List<Double> cruise = p.cruiseSpeeds.isEmpty() ? p.movingSpeeds : p.cruiseSpeeds;
        double cruiseP50 = percentile(cruise, 0.50);
        double cruiseP75 = percentile(cruise, 0.75);
        double cruiseP90 = percentile(cruise, 0.90);

        double allP50 = percentile(p.movingSpeeds, 0.50);
        double allP75 = percentile(p.movingSpeeds, 0.75);
        double allP90 = percentile(p.movingSpeeds, 0.90);

        if (cruiseP75 >= 200 || cruiseP50 >= 160) {
            return "flight";
        }
        if (cruiseP75 >= 140 || cruiseP50 >= 110) {
            return "hsr";
        }
        if (cruiseP75 >= 70 || cruiseP50 >= 55) {
            return "train";
        }

        boolean cityDrive = p.stopTimeRatio >= 0.12
                && (cruiseP75 >= 22 || cruiseP90 >= 32);
        boolean sustainedDrive = cruiseP90 >= 40 || cruiseP75 >= 30
                || (allP90 >= 48 && cruiseP50 >= 18);
        if (sustainedDrive || cityDrive) {
            return "drive";
        }

        boolean uphillBike = p.steepUpSegs >= 2
                && cruiseP50 >= 8
                && (cruiseP90 >= 10 || allP90 >= 12);
        boolean downhillBike = p.steepDownSegs >= 2
                && cruiseP50 >= 7
                && cruiseP50 < 28
                && allP90 >= cruiseP90 + 5;
        if (cruiseP90 >= 14 || cruiseP75 >= 11 || cruiseP50 >= 9
                || uphillBike || downhillBike) {
            return "bike";
        }

        if (cruiseP50 < 7.5 && cruiseP90 < 12 && allP75 < 14) {
            return "walk";
        }
        if (cruiseP50 >= 6 || allP50 >= 6.5) {
            return "bike";
        }
        return "walk";
    }

    private static SpeedProfile buildSpeedProfile(List<GpxSample> samples, int from, int to) {
        List<Double> moving = new ArrayList<Double>();
        List<Double> cruise = new ArrayList<Double>();
        double distKm = 0D;
        long durationMs = 0L;
        long stopMs = 0L;
        int steepUpSegs = 0;
        int steepDownSegs = 0;
        int end = Math.min(to, samples.size() - 1);
        for (int i = from + 1; i <= end; i++) {
            Edge e = buildEdge(samples.get(i - 1), samples.get(i));
            distKm += e.distKm;
            durationMs += e.dtMs;
            if (e.stop) {
                stopMs += e.dtMs;
                continue;
            }
            if (e.speedKmh <= 0) {
                continue;
            }
            moving.add(e.speedKmh);
            if (e.slope > 0) {
                steepUpSegs++;
            } else if (e.slope < 0) {
                steepDownSegs++;
            } else {
                cruise.add(e.speedKmh);
            }
        }
        Collections.sort(moving);
        Collections.sort(cruise);
        long durationSec = Math.max(0L, durationMs / 1000L);
        double avg = durationSec > 0 ? distKm / (durationSec / 3600.0) : 0D;
        double stopRatio = durationMs > 0 ? (stopMs * 1.0 / durationMs) : 0D;
        return new SpeedProfile(moving, cruise, distKm, durationSec, avg, stopRatio,
                steepUpSegs, steepDownSegs);
    }

    private static double percentile(List<Double> sortedAsc, double p) {
        if (sortedAsc == null || sortedAsc.isEmpty()) {
            return 0D;
        }
        if (p <= 0) {
            return sortedAsc.get(0);
        }
        if (p >= 1) {
            return sortedAsc.get(sortedAsc.size() - 1);
        }
        double idx = (sortedAsc.size() - 1) * p;
        int lo = (int) Math.floor(idx);
        int hi = (int) Math.ceil(idx);
        if (lo == hi) {
            return sortedAsc.get(lo);
        }
        double f = idx - lo;
        return sortedAsc.get(lo) * (1.0 - f) + sortedAsc.get(hi) * f;
    }

    private static final class Edge {
        final long dtMs;
        final double distKm;
        final double speedKmh;
        final boolean stop;
        final boolean steep;
        final int slope;

        Edge(long dtMs, double distKm, double speedKmh, boolean stop, boolean steep, int slope) {
            this.dtMs = dtMs;
            this.distKm = distKm;
            this.speedKmh = speedKmh;
            this.stop = stop;
            this.steep = steep;
            this.slope = slope;
        }
    }

    private static final class SpeedProfile {
        final List<Double> movingSpeeds;
        final List<Double> cruiseSpeeds;
        final double distanceKm;
        final long durationSec;
        final double avgSpeedKmh;
        final double stopTimeRatio;
        final int steepUpSegs;
        final int steepDownSegs;

        SpeedProfile(List<Double> movingSpeeds, List<Double> cruiseSpeeds,
                     double distanceKm, long durationSec, double avgSpeedKmh,
                     double stopTimeRatio, int steepUpSegs, int steepDownSegs) {
            this.movingSpeeds = movingSpeeds;
            this.cruiseSpeeds = cruiseSpeeds;
            this.distanceKm = distanceKm;
            this.durationSec = durationSec;
            this.avgSpeedKmh = avgSpeedKmh;
            this.stopTimeRatio = stopTimeRatio;
            this.steepUpSegs = steepUpSegs;
            this.steepDownSegs = steepDownSegs;
        }
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
