# -*- coding: utf-8 -*-
"""更细：按连续移动片段分析骑行/步行。"""
import math
import xml.etree.ElementTree as ET
from datetime import datetime, timedelta

path = r"F:\照片视频备份\GPX轨迹文件\20260830-115148 - 俸伯.gpx"
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
            ts,
            float(tp.get("lat")),
            float(tp.get("lon")),
            float(ele.text) if ele is not None and ele.text else None,
        )
    )


def hav(a, b):
    R = 6371.0
    lat1, lon1 = math.radians(a[1]), math.radians(a[2])
    lat2, lon2 = math.radians(b[1]), math.radians(b[2])
    dlat, dlon = lat2 - lat1, lon2 - lon1
    x = math.sin(dlat / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin(dlon / 2) ** 2
    return 2 * R * math.asin(math.sqrt(x))


def local(t):
    return (t + timedelta(hours=8)).strftime("%H:%M:%S")


# edges
edges = []
for i in range(1, len(pts)):
    dt = (pts[i][0] - pts[i - 1][0]).total_seconds()
    if dt <= 0:
        continue
    d = hav(pts[i - 1], pts[i])
    kmh = d / (dt / 3600.0) if dt > 0 else 0
    if kmh > 60:
        continue
    moving = d >= 0.005 and kmh >= 2.0  # 5m & >=2km/h
    edges.append((i, pts[i][0], dt, d, kmh, moving))

# continuous movement bouts: gap > 120s of non-moving splits bouts
bouts = []
cur = None
last_move_t = None
for i, t, dt, d, kmh, moving in edges:
    if moving:
        if cur is None:
            cur = {
                "start": t - timedelta(seconds=dt),
                "end": t,
                "dist": d,
                "move_s": dt,
                "speeds": [kmh],
            }
        else:
            # if long gap since last move, new bout
            if last_move_t and (t - last_move_t).total_seconds() > 120:
                bouts.append(cur)
                cur = {
                    "start": t - timedelta(seconds=dt),
                    "end": t,
                    "dist": d,
                    "move_s": dt,
                    "speeds": [kmh],
                }
            else:
                cur["end"] = t
                cur["dist"] += d
                cur["move_s"] += dt
                cur["speeds"].append(kmh)
        last_move_t = t
    else:
        # still extend bout clock if short pause inside ride (<120s handled above)
        pass
if cur:
    bouts.append(cur)


def classify_bout(speeds):
    speeds = sorted(speeds)
    if not speeds:
        return "idle", 0, 0, 0
    med = speeds[len(speeds) // 2]
    p75 = speeds[int((len(speeds) - 1) * 0.75)]
    p90 = speeds[int((len(speeds) - 1) * 0.90)]
    # walk vs bike: share of edges
    walk_n = sum(1 for s in speeds if s < 7)
    bike_n = sum(1 for s in speeds if 7 <= s < 22)
    fast_n = sum(1 for s in speeds if s >= 22)
    n = len(speeds)
    if med >= 22 or p75 >= 28:
        mode = "drive"
    elif med >= 8 or p75 >= 11 or (bike_n / n >= 0.45 and med >= 6.5):
        mode = "bike"
    elif walk_n / n >= 0.6 and med < 7.5:
        mode = "walk"
    elif med >= 6.5:
        mode = "bike"
    else:
        mode = "walk"
    return mode, med, p75, p90


print(f"points={len(pts)}  edges={len(edges)}  bouts={len(bouts)}")
print(f"span {local(pts[0][0])} - {local(pts[-1][0])} (UTC+8)\n")

print("=== 连续移动片段（停顿>2分钟切开）===")
total_bike = total_walk = 0.0
for b in bouts:
    mode, med, p75, p90 = classify_bout(b["speeds"])
    span_s = (b["end"] - b["start"]).total_seconds()
    if b["dist"] < 0.05 and b["move_s"] < 60:
        continue
    label = {"bike": "骑行", "walk": "步行", "drive": "自驾"}.get(mode, mode)
    print(
        f"{local(b['start'])} - {local(b['end'])}  {label}  "
        f"里程={b['dist']:.2f}km  移动={int(b['move_s']//60)}分{int(b['move_s']%60):02d}秒  "
        f"跨度={int(span_s//60)}分  "
        f"中位速={med:.1f}  p75={p75:.1f}  p90={p90:.1f} km/h"
    )
    if mode == "bike":
        total_bike += b["dist"]
    elif mode == "walk":
        total_walk += b["dist"]

print(f"\n合计：骑行约 {total_bike:.2f} km，步行约 {total_walk:.2f} km")
print(
    f"整轨：距离 {sum(e[3] for e in edges):.2f} km，"
    f"移动 {sum(e[2] for e in edges if e[5])/60:.1f} 分钟，"
    f"停顿 {sum(e[2] for e in edges if not e[5])/60:.1f} 分钟"
)

# stationary clusters
print("\n=== 主要停留（连续停顿>10分钟）===")
idle_start = None
idle_end = None
idle_d = 0.0


def flush_idle():
    global idle_start, idle_end, idle_d
    if idle_start is None:
        return
    dur = (idle_end - idle_start).total_seconds()
    if dur >= 600:
        print(f"{local(idle_start)} - {local(idle_end)}  停留 {int(dur//60)} 分钟  漂移{idle_d*1000:.0f}m")
    idle_start = idle_end = None
    idle_d = 0.0


for i, t, dt, d, kmh, moving in edges:
    if not moving:
        if idle_start is None:
            idle_start = t - timedelta(seconds=dt)
        idle_end = t
        idle_d += d
    else:
        flush_idle()
flush_idle()

# minute-by-minute during actual movement clusters for first/last hour of activity
print("\n=== 有位移的分钟明细（仅列出移动距离>=30m的分钟）===")
from collections import defaultdict

mins = defaultdict(lambda: {"d": 0.0, "speeds": [], "move": 0.0})
for i, t, dt, d, kmh, moving in edges:
    loc = t + timedelta(hours=8)
    key = loc.replace(second=0, microsecond=0)
    mins[key]["d"] += d
    if moving:
        mins[key]["speeds"].append(kmh)
        mins[key]["move"] += dt

for k in sorted(mins.keys()):
    m = mins[k]
    if m["d"] < 0.03:
        continue
    mode, med, p75, p90 = classify_bout(m["speeds"])
    label = {"bike": "骑行", "walk": "步行", "drive": "自驾", "idle": "停"}.get(mode, mode)
    print(
        f"{k.strftime('%H:%M')}  {label}  {m['d']*1000:5.0f}m  "
        f"中位={med:5.1f} p90={p90:5.1f} km/h"
    )
