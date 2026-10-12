# -*- coding: utf-8 -*-
import math
import xml.etree.ElementTree as ET
from datetime import datetime, timedelta, timezone
from collections import Counter

path = r"F:\照片视频备份\GPX轨迹文件\20260830-115148 - 俸伯.gpx"
ns = {"g": "http://www.topografix.com/GPX/1/1"}
root = ET.parse(path).getroot()
pts = []
for tp in root.findall(".//g:trkpt", ns):
    lat = float(tp.get("lat"))
    lon = float(tp.get("lon"))
    t = tp.find("g:time", ns)
    e = tp.find("g:ele", ns)
    if t is None:
        continue
    ts = datetime.fromisoformat(t.text.replace("Z", "+00:00"))
    ele = float(e.text) if e is not None and e.text else None
    pts.append((ts, lat, lon, ele))

print("points", len(pts))
print("start local+8", pts[0][0] + timedelta(hours=8))
print("end   local+8", pts[-1][0] + timedelta(hours=8))


def hav(a, b):
    R = 6371.0
    lat1, lon1 = math.radians(a[1]), math.radians(a[2])
    lat2, lon2 = math.radians(b[1]), math.radians(b[2])
    dlat = lat2 - lat1
    dlon = lon2 - lon1
    x = math.sin(dlat / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin(dlon / 2) ** 2
    return 2 * R * math.asin(math.sqrt(x))


edges = []
for i in range(1, len(pts)):
    dt = (pts[i][0] - pts[i - 1][0]).total_seconds()
    if dt <= 0:
        continue
    d = hav(pts[i - 1], pts[i])
    kmh = d / (dt / 3600)
    if kmh > 80:
        continue
    grade = None
    if pts[i][3] is not None and pts[i - 1][3] is not None and d > 0.005:
        grade = (pts[i][3] - pts[i - 1][3]) / (d * 1000)
    edges.append(
        {
            "i": i,
            "t": pts[i][0],
            "dt": dt,
            "d": d,
            "kmh": kmh,
            "grade": grade,
            "stop": kmh < 2.0 or d < 0.008,
        }
    )


def classify(med, p75, p90):
    if p75 >= 28 or p90 >= 40 or med >= 22:
        return "drive"
    if med >= 8 or p75 >= 11 or p90 >= 14:
        return "bike"
    return "walk"


# 2-min window every 30s
t0, t1 = pts[0][0], pts[-1][0]
win, step = 120, 30
rows = []
t = t0
while t <= t1:
    te = t + timedelta(seconds=win)
    speeds = [
        e["kmh"]
        for e in edges
        if t <= e["t"] < te and not e["stop"] and e["d"] >= 0.008
    ]
    dist = sum(e["d"] for e in edges if t <= e["t"] < te)
    if speeds:
        speeds.sort()
        med = speeds[len(speeds) // 2]
        p75 = speeds[int((len(speeds) - 1) * 0.75)]
        p90 = speeds[int((len(speeds) - 1) * 0.90)]
        mode = classify(med, p75, p90)
    else:
        med = p75 = p90 = 0.0
        mode = "idle"
    rows.append((t, mode, med, p75, p90, dist))
    t += timedelta(seconds=step)

segs = []
for r in rows:
    mode = r[1]
    if mode == "idle":
        if segs:
            segs[-1]["end"] = r[0] + timedelta(seconds=win)
            segs[-1]["dist"] += r[5]
        continue
    if not segs or segs[-1]["mode"] != mode:
        segs.append(
            {
                "mode": mode,
                "start": r[0],
                "end": r[0] + timedelta(seconds=win),
                "dist": r[5],
                "meds": [r[2]],
            }
        )
    else:
        segs[-1]["end"] = r[0] + timedelta(seconds=win)
        segs[-1]["dist"] += r[5]
        segs[-1]["meds"].append(r[2])

merged = []
for s in segs:
    dur = (s["end"] - s["start"]).total_seconds()
    if merged and dur < 90 and s["dist"] < 0.15:
        merged[-1]["end"] = s["end"]
        merged[-1]["dist"] += s["dist"]
        merged[-1]["meds"] += s["meds"]
    elif merged and merged[-1]["mode"] == s["mode"]:
        merged[-1]["end"] = s["end"]
        merged[-1]["dist"] += s["dist"]
        merged[-1]["meds"] += s["meds"]
    else:
        merged.append(s)

# coalesce same after merge
coalesced = []
for s in merged:
    if coalesced and coalesced[-1]["mode"] == s["mode"]:
        coalesced[-1]["end"] = s["end"]
        coalesced[-1]["dist"] += s["dist"]
        coalesced[-1]["meds"] += s["meds"]
    else:
        coalesced.append(s)

print("\n=== Segments (local +8) ===")
for s in coalesced:
    dur = (s["end"] - s["start"]).total_seconds()
    if dur < 60 and s["dist"] < 0.05:
        continue
    meds = sorted(s["meds"])
    med = meds[len(meds) // 2] if meds else 0
    print(
        f"{(s['start'] + timedelta(hours=8)).strftime('%H:%M:%S')} - "
        f"{(s['end'] + timedelta(hours=8)).strftime('%H:%M:%S')}  "
        f"{s['mode']:5s}  dist~{s['dist']:.2f}km  winMed~{med:.1f}km/h  "
        f"dur={int(dur // 60)}m{int(dur % 60):02d}s"
    )

moving = [e["kmh"] for e in edges if not e["stop"] and e["d"] >= 0.008]
moving.sort()


def pct(p):
    if not moving:
        return 0
    return moving[int((len(moving) - 1) * p)]


print(
    "\nmoving speed: n=",
    len(moving),
    "p50=",
    round(pct(0.5), 1),
    "p75=",
    round(pct(0.75), 1),
    "p90=",
    round(pct(0.9), 1),
    "max=",
    round(moving[-1], 1) if moving else 0,
)
print("total dist", round(sum(e["d"] for e in edges), 3), "km")
print(
    "move min",
    round(sum(e["dt"] for e in edges if not e["stop"]) / 60, 1),
    "stop min",
    round(sum(e["dt"] for e in edges if e["stop"]) / 60, 1),
)

# hourly speed bands
print("\n=== Speed band minutes (local) ===")
bands = [("0-2", 0, 2), ("2-6 walk", 2, 6), ("6-10", 6, 10), ("10-15 bike", 10, 15), ("15-22", 15, 22), ("22+", 22, 999)]
# accumulate by 30-min clock buckets
bucket = {}
for e in edges:
    if e["d"] < 0.008:
        continue
    local = e["t"] + timedelta(hours=8)
    key = local.strftime("%H:%M")[:4] + "0"  # 10-min
    key = local.replace(minute=(local.minute // 10) * 10, second=0, microsecond=0)
    bucket.setdefault(key, []).append(e)

for k in sorted(bucket.keys()):
    es = bucket[k]
    dist = sum(e["d"] for e in es)
    speeds = [e["kmh"] for e in es if e["kmh"] >= 2]
    if not speeds and dist < 0.05:
        continue
    speeds.sort()
    med = speeds[len(speeds) // 2] if speeds else 0
    p90 = speeds[int((len(speeds) - 1) * 0.9)] if speeds else 0
    mode = classify(med, speeds[int((len(speeds) - 1) * 0.75)] if speeds else 0, p90) if speeds else "idle"
    cnt = Counter()
    for e in es:
        if e["kmh"] < 2:
            cnt["stop"] += e["dt"]
        elif e["kmh"] < 6.5:
            cnt["walk"] += e["dt"]
        elif e["kmh"] < 22:
            cnt["bike"] += e["dt"]
        else:
            cnt["fast"] += e["dt"]
    parts = " ".join(f"{m}:{int(v/60)}m" for m, v in cnt.most_common() if v >= 30)
    print(
        f"{k.strftime('%H:%M')}  {mode:5s} med={med:5.1f} p90={p90:5.1f} "
        f"dist={dist:5.2f}km  [{parts}]"
    )

# Also run current Java-like segmentGpx logic in python for comparison
print("\n=== Edge-level smoothed modes (sample transitions) ===")
# window classify per edge
WINDOW_HALF_MS = 75000


def edge_classify(idx):
    mid = (pts[idx][0].timestamp() + pts[idx + 1][0].timestamp()) / 2 * 1000
    # find edges in window by matching edges list
    lo = hi = None
    speeds_m = []
    cruise = []
    stop_ms = dur_ms = 0
    dist = 0
    for e in edges:
        # edge ends at sample e['i']
        a = pts[e["i"] - 1][0].timestamp() * 1000
        b = pts[e["i"]][0].timestamp() * 1000
        emid = (a + b) / 2
        if abs(emid - mid) > WINDOW_HALF_MS and (emid < mid - WINDOW_HALF_MS or emid > mid + WINDOW_HALF_MS):
            continue
        if emid < mid - WINDOW_HALF_MS or emid > mid + WINDOW_HALF_MS:
            continue
        dur_ms += e["dt"] * 1000
        dist += e["d"]
        if e["stop"]:
            stop_ms += e["dt"] * 1000
            continue
        speeds_m.append(e["kmh"])
        g = e["grade"]
        if g is None or abs(g) < 0.04:
            cruise.append(e["kmh"])
    if len(speeds_m) < 2:
        e0 = next((e for e in edges if e["i"] == idx + 1), None)
        if not e0 or e0["stop"]:
            return "walk"
        return classify(e0["kmh"], e0["kmh"], e0["kmh"])
    speeds_m.sort()
    cruise.sort()
    src = cruise if cruise else speeds_m
    med = src[len(src) // 2]
    p75 = src[int((len(src) - 1) * 0.75)]
    p90 = src[int((len(src) - 1) * 0.90)]
    return classify(med, p75, p90)


# Sample every 20th edge for mode
raw = []
for e in edges[::5]:
    # idx for samples: edge from i-1 to i, center index i-1
    mode = edge_classify(e["i"] - 1)
    raw.append((e["t"], mode, e["kmh"], e["d"]))

prev = None
print("transitions:")
for t, mode, kmh, d in raw:
    if mode != prev:
        print(f"  {(t + timedelta(hours=8)).strftime('%H:%M:%S')} -> {mode} (edgeSpeed={kmh:.1f})")
        prev = mode
