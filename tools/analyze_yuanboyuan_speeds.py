# -*- coding: utf-8 -*-
import math
import xml.etree.ElementTree as ET
from datetime import datetime, timedelta
from collections import defaultdict

path = r"C:\Users\guoxinpeng\Desktop\20260925-095221 - 园博园.gpx"
root = ET.parse(path).getroot()
pts = []
for tp in root.findall(".//{http://www.topografix.com/GPX/1/1}trkpt"):
    t = tp.find("{http://www.topografix.com/GPX/1/1}time")
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


edges = []
for i in range(1, len(pts)):
    dt = (pts[i][0] - pts[i - 1][0]).total_seconds()
    if dt <= 0:
        continue
    d = hav(pts[i - 1], pts[i])
    kmh = d / (dt / 3600)
    if kmh > 80:
        continue
    edges.append((pts[i][0], dt, d, kmh))

moving = sorted(e[3] for e in edges if e[2] >= 0.0015 and e[3] >= 1)
print("moving n", len(moving))
print("p50", round(moving[len(moving)//2], 1))
print("p75", round(moving[int((len(moving)-1)*0.75)], 1))
print("p90", round(moving[int((len(moving)-1)*0.9)], 1))
print("p95", round(moving[int((len(moving)-1)*0.95)], 1))
print("max", round(moving[-1], 1))
print("edges >=8 km/h", sum(1 for s in moving if s >= 8))
print("edges >=10", sum(1 for s in moving if s >= 10))
print("edges >=12", sum(1 for s in moving if s >= 12))

# 2-min windows with med>=8
print("\n2-min windows with median>=7.5:")
t0, t1 = pts[0][0], pts[-1][0]
t = t0
found = 0
while t <= t1:
    te = t + timedelta(seconds=120)
    sp = [e[3] for e in edges if t <= e[0] < te and e[2] >= 0.0015 and e[3] >= 1]
    dist = sum(e[2] for e in edges if t <= e[0] < te)
    if len(sp) >= 4:
        sp.sort()
        med = sp[len(sp)//2]
        p90 = sp[int((len(sp)-1)*0.9)]
        if med >= 7.5 or p90 >= 12:
            loc = t + timedelta(hours=8)
            print(f"  {loc.strftime('%H:%M')}  med={med:.1f} p90={p90:.1f} dist={dist*1000:.0f}m n={len(sp)}")
            found += 1
    t += timedelta(seconds=30)
if not found:
    print("  (none)")

# header comment of gpx
with open(path, "r", encoding="utf-8") as f:
    head = "".join(f.readline() for _ in range(20))
print("\nGPX header:\n", head)
