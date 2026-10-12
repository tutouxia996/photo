# -*- coding: utf-8 -*-
import math
import xml.etree.ElementTree as ET
from datetime import datetime, timedelta
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


mins = defaultdict(lambda: {"d": 0.0, "sp": []})
for i in range(1, len(pts)):
    t = pts[i][0] + timedelta(hours=8)
    if not (datetime(2026, 8, 30, 17, 50, tzinfo=t.tzinfo) <= t <= datetime(2026, 8, 30, 18, 25, tzinfo=t.tzinfo)):
        continue
    dt = (pts[i][0] - pts[i - 1][0]).total_seconds()
    if dt <= 0:
        continue
    d = hav(pts[i - 1], pts[i])
    kmh = d / (dt / 3600)
    if kmh > 60:
        continue
    key = t.replace(second=0, microsecond=0)
    mins[key]["d"] += d
    if d >= 0.0015 and kmh >= 1:
        mins[key]["sp"].append(kmh)

print("Evening minute detail 17:50-18:25:")
for k in sorted(mins):
    m = mins[k]
    sp = sorted(m["sp"])
    med = sp[len(sp) // 2] if sp else 0
    p90 = sp[int((len(sp) - 1) * 0.9)] if sp else 0
    label = "BIKE?" if med >= 9 else ("WALK?" if med > 0 else "STOP")
    print(
        f"  {k.strftime('%H:%M')}  {label:6s} dist={m['d']*1000:6.0f}m  "
        f"med={med:5.1f}  p90={p90:5.1f}  n={len(sp)}"
    )
