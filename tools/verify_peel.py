# -*- coding: utf-8 -*-
"""Verify evening walk prefix is peeled from bike bout."""
import math
import xml.etree.ElementTree as ET
from datetime import timedelta
import subprocess
import sys

# reuse verify_gpx_segments by patching assign logic inline
exec(open(r"d:\work\学习\相册网站\tools\verify_gpx_segments.py", encoding="utf-8").read().split("bouts = find_bouts")[0])

PEEL_WALK_MEDIAN = 7.0
PEEL_HALF_MS = 45_000
MIN_PEEL_MS = 60_000


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
    mode, mk, mm = classify_bout(edges, lo, hi)
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
    again, _, _ = classify_bout(edges, fast_from, fast_to) if fast_from <= fast_to else ("walk", 0, 0)
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


edges = [build_edge(pts[i], pts[i + 1]) for i in range(len(pts) - 1)]
bouts = find_bouts(edges)
edge_modes = ["stay"] * len(edges)
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

print("SEGMENTS after peel:")
for a, b, mode in segs:
    if mode == "stay" and (pts[min(b, len(pts)-1)][0] - pts[a][0]) < 5 * 60000 and sum(
        hav(pts[i], pts[i+1]) for i in range(a, min(b, len(pts)-1))
    ) < 0.05:
        pass
    ta = (pts[a][4] + timedelta(hours=8)).strftime("%H:%M:%S")
    tb = (pts[min(b, len(pts) - 1)][4] + timedelta(hours=8)).strftime("%H:%M:%S")
    dist = sum(hav(pts[i], pts[i + 1]) for i in range(a, min(b, len(pts) - 1)))
    print(f"  {ta} - {tb}  {mode:5s}  path_km={dist:.2f}")
