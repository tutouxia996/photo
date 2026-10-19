# -*- coding: utf-8 -*-
"""Prototype hardened segmentGpx against Baoding 20261018 GPX."""
import math
import xml.etree.ElementTree as ET
from collections import Counter
from datetime import datetime, timedelta

path = r"D:\uploadPath\album\upload\gpx\76\ef0b6d4cca2e4863a38a8acf2ce44e42.gpx"

STOP_SPEED = 1.0
MIN_SEG_KM = 0.0015
MIN_SEG_MS = 1500
MAX_SANE = 400
BOUT_SPLIT = 120_000
LONG_STAY = 300_000
MIN_BIKE_KM = 0.20
MIN_BIKE_MS = 60_000
MIN_DRIVE_KM = 0.40
MIN_DRIVE_MS = 60_000
MIN_RAIL_KM = 2.0
MIN_RAIL_MS = 120_000
PEEL_MED = 7.0
PEEL_HALF = 45_000
MIN_PEEL = 45_000
STEEP = 0.04
SPIKE_KMH = 45.0
SPIKE_NEIGHBOR_KMH = 18.0
SPIKE_WINDOW = 2
DRIFT_NET_RATIO = 0.20
DRIFT_MAX_MED = 12.0
MIN_DRIVE_NET_KM = 0.80
MIN_BIKE_NET_KM = 0.12
SLOW_INTERIOR_MS = 90_000

ns = {"g": "http://www.topografix.com/GPX/1/1"}
root = ET.parse(path).getroot()
pts = []
for tp in root.findall(".//g:trkpt", ns):
    t = tp.find("g:time", ns)
    if t is None:
        continue
    ts = datetime.fromisoformat(t.text.replace("Z", "+00:00"))
    ele = tp.find("g:ele", ns)
    pts.append(
        (
            int(ts.timestamp() * 1000),
            float(tp.get("lat")),
            float(tp.get("lon")),
            float(ele.text) if ele is not None and ele.text else None,
            ts,
        )
    )


def hav(a, b):
    R = 6371.0
    lat1, lon1 = math.radians(a[1]), math.radians(a[2])
    lat2, lon2 = math.radians(b[1]), math.radians(b[2])
    dlat, dlon = lat2 - lat1, lon2 - lon1
    x = math.sin(dlat / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin(dlon / 2) ** 2
    return 2 * R * math.asin(math.sqrt(x))


def build_edge(a, b):
    dt = max(0, b[0] - a[0])
    d = hav(a, b)
    speed = 0.0
    stop = True
    slope = 0
    if dt >= MIN_SEG_MS and d >= MIN_SEG_KM:
        speed = d / (dt / 3600000.0)
        if speed <= 0 or speed > MAX_SANE or math.isnan(speed):
            speed = 0
            stop = True
        else:
            stop = speed < STOP_SPEED
    elif d < MIN_SEG_KM:
        stop = True
    if a[3] is not None and b[3] is not None and d > 0:
        g = (b[3] - a[3]) / (d * 1000)
        if g >= STEEP:
            slope = 1
        elif g <= -STEEP:
            slope = -1
    return dict(dt=dt, d=d, kmh=speed, stop=stop, slope=slope)


edges = [build_edge(pts[i], pts[i + 1]) for i in range(len(pts) - 1)]


def is_spike(i):
    e = edges[i]
    if e["stop"] or e["kmh"] < SPIKE_KMH:
        return False
    lo = max(0, i - SPIKE_WINDOW)
    hi = min(len(edges) - 1, i + SPIKE_WINDOW)
    nbr = 0
    for j in range(lo, hi + 1):
        if j == i:
            continue
        if (not edges[j]["stop"]) and edges[j]["kmh"] >= SPIKE_NEIGHBOR_KMH:
            nbr += 1
    return nbr < 2


spike_flags = [is_spike(i) for i in range(len(edges))]
print("points", len(pts), "spikes", sum(spike_flags))


def find_bouts(edges):
    bouts = []
    bout_start = -1
    last_moving = -1
    stop_run = 0
    for i, e in enumerate(edges):
        moving = (not e["stop"]) and e["kmh"] >= STOP_SPEED and e["d"] >= MIN_SEG_KM
        if moving:
            if bout_start < 0:
                bout_start = i
            elif stop_run >= BOUT_SPLIT:
                if last_moving >= bout_start:
                    bouts.append((bout_start, last_moving))
                bout_start = i
            last_moving = i
            stop_run = 0
        elif bout_start >= 0:
            stop_run += max(e["dt"], 0)
    if bout_start >= 0 and last_moving >= bout_start:
        bouts.append((bout_start, last_moving))
    return bouts


def pct(arr, p):
    if not arr:
        return 0
    arr = sorted(arr)
    idx = (len(arr) - 1) * p
    lo = int(math.floor(idx))
    hi = int(math.ceil(idx))
    if lo == hi:
        return arr[lo]
    f = idx - lo
    return arr[lo] * (1 - f) + arr[hi] * f


def classify_profile(moving, cruise, steep_up, steep_down, stop_ms, move_ms):
    cruise = cruise or moving
    c50, c75, c90 = pct(cruise, 0.5), pct(cruise, 0.75), pct(cruise, 0.9)
    a50, a75, a90 = pct(moving, 0.5), pct(moving, 0.75), pct(moving, 0.9)
    stop_ratio = stop_ms / (move_ms + stop_ms) if (move_ms + stop_ms) else 0
    if c75 >= 200 or c50 >= 160:
        return "flight"
    if c75 >= 140 or c50 >= 110:
        return "hsr"
    if c75 >= 70 or c50 >= 55:
        return "train"
    city = stop_ratio >= 0.12 and (c75 >= 22 or c90 >= 32)
    sust = c90 >= 40 or c75 >= 30 or (a90 >= 48 and c50 >= 18)
    if sust or city:
        return "drive"
    uphill = steep_up >= 2 and c50 >= 8 and (c90 >= 10 or a90 >= 12)
    downhill = steep_down >= 2 and 7 <= c50 < 28 and a90 >= c90 + 5
    if c90 >= 14 or c75 >= 11 or c50 >= 9 or uphill or downhill:
        return "bike"
    if c50 < 7.5 and c90 < 12 and a75 < 14:
        return "walk"
    if c50 >= 6 or a50 >= 6.5:
        return "bike"
    return "walk"


def net_km(lo, hi):
    return hav(pts[lo], pts[min(hi + 1, len(pts) - 1)])


def classify_bout(lo, hi):
    moving, cruise = [], []
    move_km = move_ms = stop_ms = 0
    steep_up = steep_down = 0
    path_km = 0
    for i in range(lo, hi + 1):
        e = edges[i]
        path_km += e["d"]
        if e["stop"] or e["kmh"] < STOP_SPEED or e["d"] < MIN_SEG_KM or spike_flags[i]:
            stop_ms += e["dt"]
            continue
        moving.append(e["kmh"])
        move_km += e["d"]
        move_ms += e["dt"]
        if e["slope"] > 0:
            steep_up += 1
        elif e["slope"] < 0:
            steep_down += 1
        else:
            cruise.append(e["kmh"])
    if len(moving) < 2 or move_km < 0.03:
        return "walk"
    nkm = net_km(lo, hi)
    med = pct(moving, 0.5)
    # 原地漂移：路径长但净位移很小（含漂移抬高的瞬时速度）
    if path_km >= 0.20 and path_km > 0 and nkm / path_km < DRIFT_NET_RATIO and med < DRIFT_MAX_MED:
        return "walk"
    mode = classify_profile(moving, cruise, steep_up, steep_down, stop_ms, move_ms)
    if mode in ("train", "hsr", "flight"):
        if move_km < MIN_RAIL_KM or move_ms < MIN_RAIL_MS or nkm < MIN_RAIL_KM * 0.5:
            if med < 7.5:
                return "walk"
            if med < 22:
                return "bike" if nkm >= MIN_BIKE_NET_KM else "walk"
            return "drive" if nkm >= MIN_DRIVE_NET_KM else "walk"
    if mode == "bike" and (
        move_km < MIN_BIKE_KM or move_ms < MIN_BIKE_MS or nkm < MIN_BIKE_NET_KM
    ):
        return "walk"
    if mode == "drive":
        if move_km < MIN_DRIVE_KM or move_ms < MIN_DRIVE_MS or nkm < MIN_DRIVE_NET_KM:
            if (
                move_km >= MIN_BIKE_KM
                and move_ms >= MIN_BIKE_MS
                and nkm >= MIN_BIKE_NET_KM
            ):
                return "bike"
            return "walk"
        if med < 7.0 and pct(moving, 0.75) < 14:
            return "walk"
    return mode


def span_ms(lo, hi):
    if lo > hi:
        return 0
    return sum(max(edges[i]["dt"], 0) for i in range(lo, hi + 1))


def robust_window_med(center, bf, bt):
    speeds = []
    before = 0
    for i in range(center, bf - 1, -1):
        e = edges[i]
        if (
            (not e["stop"])
            and e["kmh"] >= STOP_SPEED
            and e["d"] >= MIN_SEG_KM
            and not spike_flags[i]
        ):
            speeds.append(e["kmh"])
        if i < center:
            before += e["dt"]
            if before >= PEEL_HALF:
                break
    after = 0
    for i in range(center + 1, bt + 1):
        e = edges[i]
        after += e["dt"]
        if (
            (not e["stop"])
            and e["kmh"] >= STOP_SPEED
            and e["d"] >= MIN_SEG_KM
            and not spike_flags[i]
        ):
            speeds.append(e["kmh"])
        if after >= PEEL_HALF:
            break
    if len(speeds) < 2:
        return 0.0
    speeds.sort()
    return speeds[len(speeds) // 2]


def is_peel_walk(center, bf, bt):
    return robust_window_med(center, bf, bt) < PEEL_MED


def promote_fast(edge_modes, lo, hi):
    for i in range(lo, hi + 1):
        edge_modes[i] = "walk"
    i = lo
    while i <= hi:
        if is_peel_walk(i, lo, hi):
            i += 1
            continue
        j = i
        while j <= hi and not is_peel_walk(j, lo, hi):
            j += 1
        hi2 = j - 1
        dur = span_ms(i, hi2)
        fast = classify_bout(i, hi2)
        if dur >= MIN_BIKE_MS and fast in ("bike", "drive"):
            for k in range(i, hi2 + 1):
                edge_modes[k] = fast
        i = j


def demote_slow_interior(edge_modes, lo, hi):
    i = lo
    while i <= hi:
        if not is_peel_walk(i, lo, hi):
            i += 1
            continue
        j = i
        while j <= hi and is_peel_walk(j, lo, hi):
            j += 1
        if span_ms(i, j - 1) >= SLOW_INTERIOR_MS:
            for k in range(i, j):
                edge_modes[k] = "walk"
        i = j


def assign_bout(edge_modes, lo, hi):
    mode = classify_bout(lo, hi)
    if mode in ("walk", "stay"):
        promote_fast(edge_modes, lo, hi)
        return mode
    if mode not in ("bike", "drive"):
        for i in range(lo, hi + 1):
            edge_modes[i] = mode
        return mode
    fast_from = lo
    while fast_from <= hi and is_peel_walk(fast_from, lo, hi):
        fast_from += 1
    fast_to = hi
    while fast_to >= fast_from and is_peel_walk(fast_to, lo, hi):
        fast_to -= 1
    if span_ms(lo, fast_from - 1) < MIN_PEEL:
        fast_from = lo
    if span_ms(fast_to + 1, hi) < MIN_PEEL:
        fast_to = hi
    if fast_from <= fast_to:
        again = classify_bout(fast_from, fast_to)
    else:
        again = "walk"
    ok = (mode == "bike" and again in ("bike", "drive")) or again == mode
    if fast_from > fast_to or not ok:
        for i in range(lo, hi + 1):
            edge_modes[i] = mode
        demote_slow_interior(edge_modes, lo, hi)
        return mode
    for i in range(lo, fast_from):
        edge_modes[i] = "walk"
    for i in range(fast_from, fast_to + 1):
        edge_modes[i] = mode
    for i in range(fast_to + 1, hi + 1):
        edge_modes[i] = "walk"
    demote_slow_interior(edge_modes, fast_from, fast_to)
    return mode


def demote_tiny_fast_runs(edge_modes):
    """剥离后残留的短 drive/bike 碎片降为 walk。"""
    i = 0
    while i < len(edge_modes):
        mode = edge_modes[i]
        if mode not in ("bike", "drive"):
            i += 1
            continue
        j = i
        while j < len(edge_modes) and edge_modes[j] == mode:
            j += 1
        again = classify_bout(i, j - 1)
        if again != mode:
            for k in range(i, j):
                edge_modes[k] = "walk" if again == "walk" else again
        i = j


bouts = find_bouts(edges)
edge_modes = ["stay"] * len(edges)
for lo, hi in bouts:
    assign_bout(edge_modes, lo, hi)
demote_tiny_fast_runs(edge_modes)

i = 0
while i < len(edge_modes):
    if edge_modes[i] != "stay":
        i += 1
        continue
    j = i
    dur = 0
    path_km = 0
    while j < len(edge_modes) and edge_modes[j] == "stay":
        dur += max(edges[j]["dt"], 0)
        path_km += max(edges[j]["d"], 0)
        j += 1
    hours = dur / 3600000.0 if dur else 0
    path_kmh = path_km / hours if hours else 0
    if dur < LONG_STAY or path_km >= 0.15 or (path_km >= 0.10 and path_kmh >= 1.2):
        for k in range(i, j):
            edge_modes[k] = "walk"
    i = j

segs = []
start = 0
cur = edge_modes[0]
for i in range(1, len(edge_modes)):
    if edge_modes[i] == cur:
        continue
    segs.append((start, i, cur))
    start = i
    cur = edge_modes[i]
segs.append((start, len(edge_modes), cur))

print("SEGMENTS:")
for a, b, mode in segs:
    ta = (pts[a][4] + timedelta(hours=8)).strftime("%H:%M:%S")
    tb = (pts[min(b, len(pts) - 1)][4] + timedelta(hours=8)).strftime("%H:%M:%S")
    dist = sum(hav(pts[i], pts[i + 1]) for i in range(a, min(b, len(pts) - 1)))
    n = hav(pts[a], pts[min(b, len(pts) - 1)])
    if dist < 0.03 and mode == "stay":
        continue
    print(f"  {ta}-{tb}  {mode:5s}  path={dist:.2f} net={n:.2f}")

c = Counter(m for _, _, m in segs)
km = {
    m: round(
        sum(
            sum(hav(pts[i], pts[i + 1]) for i in range(a, min(b, len(pts) - 1)))
            for a, b, mm in segs
            if mm == m
        ),
        2,
    )
    for m in c
}
print("km", km)
