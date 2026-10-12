# -*- coding: utf-8 -*-
import math
import xml.etree.ElementTree as ET
from datetime import datetime, timedelta, timezone
from collections import defaultdict

path = r"F:\照片视频备份\GPX轨迹文件\20260830-115148 - 俸伯.gpx"
ns = {"g": "http://www.topografix.com/GPX/1/1"}
root = ET.parse(path).getroot()
pts = []
for tp in root.findall(".//g:trkpt", ns):
    t = tp.find("g:time", ns)
    if t is None:
        continue
    ts = datetime.fromisoformat(t.text.replace("Z", "+00:00"))
    pts.append((ts, float(tp.get("lat")), float(tp.get("lon"))))


def hav(a, b):
    R = 6371.0
    lat1, lon1 = math.radians(a[1]), math.radians(a[2])
    lat2, lon2 = math.radians(b[1]), math.radians(b[2])
    dlat, dlon = lat2 - lat1, lon2 - lon1
    x = math.sin(dlat / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin(dlon / 2) ** 2
    return 2 * R * math.asin(math.sqrt(x))


def loc(t):
    return (t + timedelta(hours=8)).strftime("%H:%M:%S")


edges = []
for i in range(1, len(pts)):
    dt = (pts[i][0] - pts[i - 1][0]).total_seconds()
    if dt <= 0:
        continue
    d = hav(pts[i - 1], pts[i])
    kmh = d / (dt / 3600)
    if kmh > 60:
        continue
    moving = d >= 0.005 and kmh >= 2.0
    edges.append((pts[i][0], dt, d, kmh, moving))

print("FILE:", path)
print("points", len(pts))
print("local", loc(pts[0][0]), "-", loc(pts[-1][0]))
print("dist_km", round(sum(e[2] for e in edges), 3))
print("move_min", round(sum(e[1] for e in edges if e[4]) / 60, 1))
print("stop_min", round(sum(e[1] for e in edges if not e[4]) / 60, 1))
mv = sorted(e[3] for e in edges if e[4])
print(
    "move_speed p50/p75/p90/max",
    round(mv[len(mv) // 2], 1),
    round(mv[int((len(mv) - 1) * 0.75)], 1),
    round(mv[int((len(mv) - 1) * 0.9)], 1),
    round(mv[-1], 1),
)

bouts = []
cur = None
last = None
for t, dt, d, kmh, moving in edges:
    if not moving:
        continue
    if cur is None or (last and (t - last).total_seconds() > 120):
        if cur:
            bouts.append(cur)
        cur = {"s": t - timedelta(seconds=dt), "e": t, "d": d, "ms": dt, "sp": [kmh]}
    else:
        cur["e"] = t
        cur["d"] += d
        cur["ms"] += dt
        cur["sp"].append(kmh)
    last = t
if cur:
    bouts.append(cur)

print("\nREAL_MOVEMENT_BOUTS (>50m):")
for b in bouts:
    if b["d"] < 0.05:
        continue
    sp = sorted(b["sp"])
    med = sp[len(sp) // 2]
    p90 = sp[int((len(sp) - 1) * 0.9)]
    if med >= 8 or p90 >= 12:
        mode = "BIKE"
    elif med >= 6.5:
        mode = "BIKE_SLOW"
    else:
        mode = "WALK"
    print(
        f"  {loc(b['s'])}-{loc(b['e'])}  {mode:9s}  {b['d']:.2f}km  "
        f"move={int(b['ms'] // 60)}m{int(b['ms'] % 60):02d}s  med={med:.1f} p90={p90:.1f}"
    )

print("\nLONG_STOPS (>10min) with GPS drift:")
idle_s = idle_e = None
idle_d = 0.0


def flush():
    global idle_s, idle_e, idle_d
    if idle_s is None:
        return
    dur = (idle_e - idle_s).total_seconds()
    if dur >= 600:
        print(f"  {loc(idle_s)}-{loc(idle_e)}  stop {int(dur // 60)}min  drift={idle_d * 1000:.0f}m")
    idle_s = idle_e = None
    idle_d = 0.0


for t, dt, d, kmh, moving in edges:
    if not moving:
        if idle_s is None:
            idle_s = t - timedelta(seconds=dt)
        idle_e = t
        idle_d += d
    else:
        flush()
flush()

print("\nAFTERNOON real-move minutes 12:15-18:05:")
tz = pts[0][0].tzinfo
mins = defaultdict(list)
for t, dt, d, kmh, moving in edges:
    loc_t = t + timedelta(hours=8)
    if loc_t < datetime(2026, 8, 30, 12, 15, tzinfo=tz):
        continue
    if loc_t > datetime(2026, 8, 30, 18, 5, tzinfo=tz):
        continue
    if moving and d >= 0.01:
        key = loc_t.replace(second=0, microsecond=0)
        mins[key].append((d, kmh))

for k in sorted(mins):
    arr = mins[k]
    dist = sum(x[0] for x in arr) * 1000
    sp = sorted(x[1] for x in arr)
    med = sp[len(sp) // 2]
    if med >= 8:
        mode = "BIKE"
    elif med < 6.5:
        mode = "WALK?"
    else:
        mode = "SLOW"
    print(f"  {k.strftime('%H:%M')}  {mode:6s}  {dist:5.0f}m  med={med:.1f}")
