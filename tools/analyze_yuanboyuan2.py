# -*- coding: utf-8 -*-
"""园博园 GPX：含 walk 内提升 bike 的完整分段。"""
import math
import xml.etree.ElementTree as ET
from datetime import timedelta

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

root = ET.parse(path).getroot()
pts = []
for tp in root.findall(".//{http://www.topografix.com/GPX/1/1}trkpt"):
    t = tp.find("{http://www.topografix.com/GPX/1/1}time")
    if t is None:
        continue
    ts = __import__("datetime").datetime.fromisoformat(t.text.replace("Z", "+00:00"))
    ele = tp.find("{http://www.topografix.com/GPX/1/1}ele")
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
    speed, stop, slope = 0.0, True, 0
    if dt >= MIN_SEG_MS and d >= MIN_SEG_KM:
        speed = d / (dt / 3600000.0)
        if 0 < speed <= 400 and not math.isnan(speed):
            stop = speed < STOP_SPEED
        else:
            speed = 0
            stop = True
    if a[3] is not None and b[3] is not None and d > 0:
        g = (b[3] - a[3]) / (d * 1000)
        if g >= STEEP:
            slope = 1
        elif g <= -STEEP:
            slope = -1
    return dict(dt=dt, d=d, kmh=speed, stop=stop, slope=slope)


edges = [build_edge(pts[i], pts[i + 1]) for i in range(len(pts) - 1)]


def find_bouts(edges):
    bouts, bout_start, last_moving, stop_run = [], -1, -1, 0
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
    lo, hi = int(math.floor(idx)), int(math.ceil(idx))
    if lo == hi:
        return arr[lo]
    f = idx - lo
    return arr[lo] * (1 - f) + arr[hi] * f


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
    src = cruise or moving
    c50, c75, c90 = pct(src, 0.5), pct(src, 0.75), pct(src, 0.9)
    a50, a75, a90 = pct(moving, 0.5), pct(moving, 0.75), pct(moving, 0.9)
    stop_ratio = stop_ms / (move_ms + stop_ms) if (move_ms + stop_ms) else 0
    if (stop_ratio >= 0.12 and (c75 >= 22 or c90 >= 32)) or c90 >= 40 or c75 >= 30:
        mode = "drive"
    elif c90 >= 14 or c75 >= 11 or c50 >= 9 or (
        steep_up >= 2 and c50 >= 8 and (c90 >= 10 or a90 >= 12)
    ):
        mode = "bike"
    elif c50 < 7.5 and c90 < 12 and a75 < 14:
        mode = "walk"
    elif c50 >= 6 or a50 >= 6.5:
        mode = "bike"
    else:
        mode = "walk"
    if mode == "bike" and (move_km < MIN_BIKE_MOVE_KM or move_ms < MIN_BIKE_MOVE_MS):
        mode = "walk"
    return mode, move_km, move_ms, pct(moving, 0.5), pct(moving, 0.9)


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
            fmode, fkm, fmm, fmed, fp90 = classify_bout(edges, i, j - 1)
            if span_ms(edges, i, j - 1) >= MIN_BIKE_MOVE_MS and fmode in ("bike", "drive"):
                for k in range(i, j):
                    edge_modes[k] = fmode
            i = j
        return
    if mode not in ("bike", "drive"):
        for i in range(lo, hi + 1):
            edge_modes[i] = mode
        return
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
    again = classify_bout(edges, fast_from, fast_to)[0] if fast_from <= fast_to else "walk"
    ok = (mode == "bike" and again in ("bike", "drive")) or again == mode
    if fast_from > fast_to or not ok:
        for i in range(lo, hi + 1):
            edge_modes[i] = mode
        return
    for i in range(lo, fast_from):
        edge_modes[i] = "walk"
    for i in range(fast_from, fast_to + 1):
        edge_modes[i] = mode
    for i in range(fast_to + 1, hi + 1):
        edge_modes[i] = "walk"


bouts = find_bouts(edges)
edge_modes = ["stay"] * len(edges)
for lo, hi in bouts:
    assign_bout(edge_modes, edges, lo, hi)

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
    if dur < LONG_STAY_MS or path_km >= 0.15 or (path_km >= 0.10 and hours and path_km / hours >= 1.2):
        for k in range(i, j):
            edge_modes[k] = "walk"
    i = j

segs = []
start, cur = 0, edge_modes[0]
for i in range(1, len(edge_modes)):
    if edge_modes[i] == cur:
        continue
    segs.append((start, i, cur))
    start, cur = i, edge_modes[i]
segs.append((start, len(edge_modes), cur))

print("points", len(pts), "path_km", round(sum(e["d"] for e in edges), 2))
print("\nSEGMENTS:")
for a, b, mode in segs:
    ta = (pts[a][4] + timedelta(hours=8)).strftime("%H:%M:%S")
    tb = (pts[min(b, len(pts) - 1)][4] + timedelta(hours=8)).strftime("%H:%M:%S")
    dist = sum(hav(pts[i], pts[i + 1]) for i in range(a, min(b, len(pts) - 1)))
    dur = (pts[min(b, len(pts) - 1)][0] - pts[a][0]) / 60000.0
    if mode != "bike" and dist < 0.08 and dur < 3:
        continue
    mark = " <<<" if mode == "bike" else ""
    print(f"  {ta} - {tb}  {mode:5s}  {dist:.2f}km  {dur:.1f}min{mark}")

print("\nBIKE only:")
for a, b, mode in segs:
    if mode != "bike":
        continue
    ta = pts[a][4] + timedelta(hours=8)
    tb = pts[min(b, len(pts) - 1)][4] + timedelta(hours=8)
    dist = sum(hav(pts[i], pts[i + 1]) for i in range(a, min(b, len(pts) - 1)))
    # moving stats
    sp = []
    move_km = move_ms = 0
    for i in range(a, min(b, len(edges))):
        e = edges[i]
        if not e["stop"] and e["kmh"] >= STOP_SPEED:
            sp.append(e["kmh"])
            move_km += e["d"]
            move_ms += e["dt"]
    med = pct(sp, 0.5)
    p90 = pct(sp, 0.9)
    print(
        f"  {ta.strftime('%H:%M:%S')} - {tb.strftime('%H:%M:%S')}  "
        f"约 {dist:.2f} km，移动 {move_ms/60000:.1f} 分钟，中位 {med:.1f} / p90 {p90:.1f} km/h"
    )
