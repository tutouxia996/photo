# -*- coding: utf-8 -*-
"""Analyze 园博园 GPX with current bout+peel algorithm."""
import math
import xml.etree.ElementTree as ET
from datetime import datetime, timedelta
from collections import Counter

path = r"C:\Users\guoxinpeng\Desktop\20260925-095221 - 园博园.gpx"
STOP_SPEED = 1.0
MIN_SEG_KM = 0.0015
MIN_SEG_MS = 1500
BOUT_SPLIT_STOP_MS = 120_000
LONG_STAY_MS = 5 * 60_000
MIN_BIKE_MOVE_KM = 0.20
MIN_BIKE_MOVE_MS = 60_000
PEEL_WALK_MEDIAN = 7.0
PEEL_HALF_MS = 45_000
MIN_PEEL_MS = 60_000
STEEP = 0.04

ns = {"g": "http://www.topografix.com/GPX/1/1"}
# also try without ns
root = ET.parse(path).getroot()
pts = []
trkpts = root.findall(".//{http://www.topografix.com/GPX/1/1}trkpt")
if not trkpts:
    trkpts = root.findall(".//trkpt")
for tp in trkpts:
    t_el = tp.find("{http://www.topografix.com/GPX/1/1}time")
    if t_el is None:
        t_el = tp.find("time")
    if t_el is None or not t_el.text:
        continue
    ts = datetime.fromisoformat(t_el.text.replace("Z", "+00:00"))
    ele_el = tp.find("{http://www.topografix.com/GPX/1/1}ele")
    if ele_el is None:
        ele_el = tp.find("ele")
    ele = float(ele_el.text) if ele_el is not None and ele_el.text else None
    pts.append((int(ts.timestamp() * 1000), float(tp.get("lat")), float(tp.get("lon")), ele, ts))

print("file:", path)
print("points:", len(pts))
if len(pts) < 2:
    raise SystemExit("too few points")
print("local:", (pts[0][4] + timedelta(hours=8)).strftime("%Y-%m-%d %H:%M:%S"),
      "-", (pts[-1][4] + timedelta(hours=8)).strftime("%H:%M:%S"))


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
        if speed <= 0 or speed > 400 or math.isnan(speed):
            speed = 0
            stop = True
        else:
            stop = speed < STOP_SPEED
    if a[3] is not None and b[3] is not None and d > 0:
        g = (b[3] - a[3]) / (d * 1000)
        if g >= STEEP:
            slope = 1
        elif g <= -STEEP:
            slope = -1
    return dict(dt=dt, d=d, kmh=speed, stop=stop, slope=slope)


edges = [build_edge(pts[i], pts[i + 1]) for i in range(len(pts) - 1)]
print("total_path_km:", round(sum(e["d"] for e in edges), 3))
print("move_min:", round(sum(e["dt"] for e in edges if not e["stop"]) / 60000, 1))
print("stop_min:", round(sum(e["dt"] for e in edges if e["stop"]) / 60000, 1))


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
            elif stop_run >= BOUT_SPLIT_STOP_MS:
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
    if c75 >= 70 or c50 >= 55:
        return "train"
    if (stop_ratio >= 0.12 and (c75 >= 22 or c90 >= 32)) or c90 >= 40 or c75 >= 30 or (
        a90 >= 48 and c50 >= 18
    ):
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


def classify_bout(edges, lo, hi):
    moving, cruise = [], []
    move_km = move_ms = stop_ms = 0
    steep_up = steep_down = 0
    for i in range(lo, hi + 1):
        e = edges[i]
        if e["stop"] or e["kmh"] < STOP_SPEED or e["d"] < MIN_SEG_KM:
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
        return "walk", move_km, move_ms, 0, 0
    mode = classify_profile(moving, cruise, steep_up, steep_down, stop_ms, move_ms)
    if mode == "bike" and (move_km < MIN_BIKE_MOVE_KM or move_ms < MIN_BIKE_MOVE_MS):
        mode = "walk"
    if mode == "drive" and (move_km < 0.40 or move_ms < MIN_BIKE_MOVE_MS):
        mode = "bike" if move_km >= MIN_BIKE_MOVE_KM and move_ms >= MIN_BIKE_MOVE_MS else "walk"
    med = pct(moving, 0.5)
    p90 = pct(moving, 0.9)
    return mode, move_km, move_ms, med, p90


def span_ms(edges, lo, hi):
    if lo > hi:
        return 0
    return sum(max(edges[i]["dt"], 0) for i in range(lo, hi + 1))


def is_peel_walk(edges, center, bf, bt):
    speeds = []
    before = 0
    for i in range(center, bf - 1, -1):
        e = edges[i]
        if (not e["stop"]) and e["kmh"] >= STOP_SPEED and e["d"] >= MIN_SEG_KM:
            speeds.append(e["kmh"])
        if i < center:
            before += e["dt"]
            if before >= PEEL_HALF_MS:
                break
    after = 0
    for i in range(center + 1, bt + 1):
        e = edges[i]
        after += e["dt"]
        if (not e["stop"]) and e["kmh"] >= STOP_SPEED and e["d"] >= MIN_SEG_KM:
            speeds.append(e["kmh"])
        if after >= PEEL_HALF_MS:
            break
    if len(speeds) < 2:
        return True
    speeds.sort()
    return speeds[len(speeds) // 2] < PEEL_WALK_MEDIAN


def assign_bout(edge_modes, edges, lo, hi):
    mode, mk, mm, med, p90 = classify_bout(edges, lo, hi)
    if mode in ("walk", "stay"):
        for i in range(lo, hi + 1):
            edge_modes[i] = "walk"
        i = lo
        while i <= hi:
            if is_peel_walk(edges, i, lo, hi):
                i += 1
                continue
            j = i
            while j <= hi and not is_peel_walk(edges, j, lo, hi):
                j += 1
            sub_hi = j - 1
            dur = span_ms(edges, i, sub_hi)
            fast, fmk, fmm, fmed, fp90 = classify_bout(edges, i, sub_hi)
            if dur >= MIN_BIKE_MOVE_MS and fast in ("bike", "drive"):
                for k in range(i, sub_hi + 1):
                    edge_modes[k] = fast
            i = j
        return mode, mk, mm, med, p90, lo, hi
    if mode not in ("bike", "drive"):
        for i in range(lo, hi + 1):
            edge_modes[i] = mode
        return mode, mk, mm, med, p90, lo, hi
    fast_from = lo
    while fast_from <= hi and is_peel_walk(edges, fast_from, lo, hi):
        fast_from += 1
    fast_to = hi
    while fast_to >= fast_from and is_peel_walk(edges, fast_to, lo, hi):
        fast_to -= 1
    if span_ms(edges, lo, fast_from - 1) < MIN_PEEL_MS:
        fast_from = lo
    if span_ms(edges, fast_to + 1, hi) < MIN_PEEL_MS:
        fast_to = hi
    if fast_from <= fast_to:
        again, mk2, mm2, med2, p902 = classify_bout(edges, fast_from, fast_to)
    else:
        again, mk2, mm2, med2, p902 = "walk", 0, 0, 0, 0
    ok = (mode == "bike" and again in ("bike", "drive")) or again == mode
    if fast_from > fast_to or not ok:
        for i in range(lo, hi + 1):
            edge_modes[i] = mode
        return mode, mk, mm, med, p90, lo, hi
    for i in range(lo, fast_from):
        edge_modes[i] = "walk"
    for i in range(fast_from, fast_to + 1):
        edge_modes[i] = mode
    for i in range(fast_to + 1, hi + 1):
        edge_modes[i] = "walk"
    return again, mk2, mm2, med2, p902, fast_from, fast_to


bouts = find_bouts(edges)
edge_modes = ["stay"] * len(edges)
print("\nbouts:", len(bouts))
for lo, hi in bouts:
    assign_bout(edge_modes, edges, lo, hi)

# reclassify stay
i = 0
while i < len(edge_modes):
    if edge_modes[i] != "stay":
        i += 1
        continue
    j = i
    dur = path_km = 0
    while j < len(edge_modes) and edge_modes[j] == "stay":
        dur += max(edges[j]["dt"], 0)
        path_km += max(edges[j]["d"], 0)
        j += 1
    hours = dur / 3600000.0 if dur else 0
    path_kmh = path_km / hours if hours else 0
    if dur < LONG_STAY_MS or path_km >= 0.15 or (path_km >= 0.10 and path_kmh >= 1.2):
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

print("\n=== All segments (local +8) ===")
for a, b, mode in segs:
    ta = (pts[a][4] + timedelta(hours=8)).strftime("%H:%M:%S")
    tb = (pts[min(b, len(pts) - 1)][4] + timedelta(hours=8)).strftime("%H:%M:%S")
    dist = sum(hav(pts[i], pts[i + 1]) for i in range(a, min(b, len(pts) - 1)))
    dur = (pts[min(b, len(pts) - 1)][0] - pts[a][0]) / 60000.0
    if mode == "stay" and dist < 0.05 and dur < 5:
        continue
    if mode == "walk" and dist < 0.05 and dur < 2:
        continue
    print(f"  {ta} - {tb}  {mode:5s}  {dist:.2f}km  {dur:.1f}min")

print("\n=== Cycling summary ===")
if not bike_parts:
    print("  (no bike segment detected)")
else:
    for ta, tb, mk, mm, med, p90 in bike_parts:
        print(
            f"  {ta.strftime('%H:%M:%S')} - {tb.strftime('%H:%M:%S')}  "
            f"约 {mk:.2f} km，移动 {mm/60000:.1f} 分钟，中位速 {med:.1f} km/h，p90 {p90:.1f}"
        )

c = Counter()
for a, b, mode in segs:
    dist = sum(hav(pts[i], pts[i + 1]) for i in range(a, min(b, len(pts) - 1)))
    c[mode] += dist
print("\npath_km by mode:", {k: round(v, 2) for k, v in c.items()})
