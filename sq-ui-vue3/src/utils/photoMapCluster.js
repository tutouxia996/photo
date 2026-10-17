import L from '@/utils/leaflet'
import 'leaflet.markercluster'
import 'leaflet.markercluster/dist/MarkerCluster.css'
import { isExternal } from '@/utils/validate'
import { VIDEO_QUALITY_OPTIONS, videoPlaySrc } from '@/utils/videoProxy'

/** 高德公开瓦片原生约到 18 级；maxZoom=22 + maxNativeZoom=18 可继续放大 */
export const TILE_OPTS = {
  maxZoom: 22,
  maxNativeZoom: 18,
  updateWhenZooming: false,
  keepBuffer: 2,
  subdomains: '1234',
  attribution: '&copy; 高德地图'
}

export const STYLE_LAYERS = {
  normal: {
    url: 'https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}',
    options: { ...TILE_OPTS }
  },
  satellite: {
    url: 'https://webst0{s}.is.autonavi.com/appmaptile?style=6&x={x}&y={y}&z={z}',
    options: { ...TILE_OPTS }
  },
  hybrid: {
    url: 'https://webst0{s}.is.autonavi.com/appmaptile?style=6&x={x}&y={y}&z={z}',
    options: { ...TILE_OPTS },
    labelUrl: 'https://webst0{s}.is.autonavi.com/appmaptile?style=8&x={x}&y={y}&z={z}',
    labelOptions: { ...TILE_OPTS }
  }
}

export const MAP_STYLES = [
  { key: 'normal', label: '标准' },
  { key: 'satellite', label: '卫星' },
  { key: 'hybrid', label: '混合' }
]

/** 轨迹路段出行方式（存于起点 travelMode，表示到下一站） */
/** 各方式颜色刻意拉开色相，避免火车/自驾等暖色撞车 */
export const TRAVEL_MODES = [
  { key: 'hsr', label: '高铁', color: '#E11D48' },
  { key: 'train', label: '火车', color: '#92400E' },
  { key: 'bus', label: '公交', color: '#CA8A04' },
  { key: 'metro', label: '地铁', color: '#7C3AED' },
  { key: 'walk', label: '步行', color: '#16A34A' },
  { key: 'drive', label: '自驾', color: '#F97316' },
  { key: 'bike', label: '骑行', color: '#2563EB' },
  { key: 'flight', label: '飞机', color: '#0891B2' },
  { key: 'stay', label: '停留', color: '#57534E' },
  { key: 'other', label: '其他', color: '#94A3B8' }
]

const TRAVEL_MODE_MAP = Object.fromEntries(TRAVEL_MODES.map(m => [m.key, m]))

export function getTravelMode(mode) {
  if (!mode) return null
  return TRAVEL_MODE_MAP[mode] || null
}

export function travelModeLabel(mode) {
  return getTravelMode(mode)?.label || ''
}

export function travelModeColor(mode, fallback = '#3B82F6') {
  return getTravelMode(mode)?.color || fallback
}

const ICON_SVG = {
  walk: '<path d="M14.2 4.6a1.7 1.7 0 1 1-3.4 0 1.7 1.7 0 0 1 3.4 0Zm-2.6 2.4 2.1 2.6 2.4.5v1.8l-3.1-.7-1.3 1.9 3.4 3.7-1.6 1.5-3-3.4-2.3 3.4-1.7-1.2 3-4.4-.8-2.3-2 .9-.9-1.6 3.8-2.7Z"/>',
  bike: '<path d="M6.2 13.6a3.3 3.3 0 1 1 0 6.6 3.3 3.3 0 0 1 0-6.6Zm11.6 0a3.3 3.3 0 1 1 0 6.6 3.3 3.3 0 0 1 0-6.6ZM8.4 15.6h3.2l1.6-3.2 2.3 2.1h2.2l-3.3-3.1 1-2h2.3V7.6h-3.4l-1.5 3.1H9.6L8.2 8.2H5.8l2 4.2H6.8v1.8h3.3l-.7 1.4H8.4v1.8Z"/>',
  drive: '<path d="M5.4 10.2 6.8 7.4A1.8 1.8 0 0 1 8.4 6.4h7.2c.6 0 1.2.4 1.5 1l1.5 2.8H20v6.4h-1.6a2.2 2.2 0 0 1-4.4 0H10a2.2 2.2 0 0 1-4.4 0H4v-6.4h1.4Zm2.4 6.8a1 1 0 1 0 0-2 1 1 0 0 0 0 2Zm8.4 0a1 1 0 1 0 0-2 1 1 0 0 0 0 2ZM7.2 10.4h9.6l-.9-1.8H8.1l-.9 1.8Z"/>',
  bus: '<path d="M6 5.4h12c1 0 1.8.8 1.8 1.8v9.2h-1.4a2 2 0 0 1-4 0H9.6a2 2 0 0 1-4 0H4.2V7.2C4.2 6.2 5 5.4 6 5.4Zm1.2 12.2a.8.8 0 1 0 0-1.6.8.8 0 0 0 0 1.6Zm9.6 0a.8.8 0 1 0 0-1.6.8.8 0 0 0 0 1.6ZM6.2 7.4v4.6h11.6V7.4H6.2Zm1.4 1.2h3.2v2.2H7.6V8.6Zm5.6 0h3.2v2.2h-3.2V8.6Z"/>',
  metro: '<path d="M7.2 4.6h9.6c1.3 0 2.4 1.1 2.4 2.4v8.2c0 1.3-1 2.4-2.3 2.4l1.3 1.6v.8H15l-1.2-1.6H10.2L9 20v-.8l1.3-1.6c-1.3 0-2.3-1.1-2.3-2.4V7c0-1.3 1.1-2.4 2.4-2.4Zm0 2.4v4.4h9.6V7H7.2Zm1.8 6.6a1.3 1.3 0 1 0 0 2.6 1.3 1.3 0 0 0 0-2.6Zm6 0a1.3 1.3 0 1 0 0 2.6 1.3 1.3 0 0 0 0-2.6Z"/>',
  train: '<path d="M6.6 5h10.8c1.2 0 2.2 1 2.2 2.2v8.4c0 1.2-1 2.2-2.2 2.2l1.2 1.6v.8h-2.2L15 18H9l-1.4 1.8H5.4v-.8L6.6 17.8A2.2 2.2 0 0 1 4.4 15.6V7.2C4.4 6 5.4 5 6.6 5Zm.2 2.4v4.2h10.4V7.4H6.8Zm1.8 6.4a1.3 1.3 0 1 0 0 2.6 1.3 1.3 0 0 0 0-2.6Zm6.8 0a1.3 1.3 0 1 0 0 2.6 1.3 1.3 0 0 0 0-2.6Z"/>',
  hsr: '<path d="M5.2 10.2c.6-3 3.4-4.8 7.6-4.8 3.2 0 5.6 1 7.2 2.4l1.2 3.8H19v5.2h-1.6a2 2 0 0 1-4 0h-3.6a2 2 0 0 1-4 0H4.4v-3.2c0-1.4.3-2.6.8-3.4Zm2.4 7.2a.9.9 0 1 0 0-1.8.9.9 0 0 0 0 1.8Zm8.4 0a.9.9 0 1 0 0-1.8.9.9 0 0 0 0 1.8ZM7 11.4h10.6l-.6-1.8c-1-.8-2.6-1.4-4.8-1.4-2.6 0-4.2.8-4.8 2.4L7 11.4Z"/>',
  flight: '<path d="M12 3.6 13.6 9H21v1.8l-6.2 2.1L12 20.4h-1.6l-1.2-5.6L4.2 13.2v-1.8H11L12 3.6Z"/>',
  stay: '<path d="M12 6.2a1.8 1.8 0 1 0 0-3.6 1.8 1.8 0 0 0 0 3.6Zm-2.4 2.2h4.8c.7 0 1.2.5 1.2 1.2v4.4h-1.6V20H8.8v-6h-1.6V9.6c0-.7.5-1.2 1.2-1.2Z"/>',
  other: '<path d="M12 4.2a7.8 7.8 0 1 1 0 15.6 7.8 7.8 0 0 1 0-15.6Zm0 2.2a5.6 5.6 0 1 0 0 11.2 5.6 5.6 0 0 0 0-11.2Zm.1 7.8c.6 0 1 .4 1 1v.2c0 .6-.4 1-1 1h-.2c-.6 0-1-.4-1-1v-.2c0-.6.4-1 1-1Zm0-6c.8 0 1.5.7 1.5 1.6 0 .6-.3 1-.8 1.5l-.6.5c-.2.2-.3.4-.3.7v.2h-1.6v-.4c0-.7.3-1.2.8-1.7l.6-.5c.2-.2.3-.4.3-.6 0-.3-.2-.5-.5-.5s-.6.2-.6.6H8.7c0-1.2 1-2.1 2.4-2.1Z"/>'
}

export function travelModeIconSvg(mode, size = 16) {
  const key = String(mode || '').toLowerCase()
  const path = ICON_SVG[key] || ICON_SVG.other
  return `<svg viewBox="0 0 24 24" width="${size}" height="${size}" aria-hidden="true" fill="#fff">${path}</svg>`
}

/** 沿线人物/载具动画：是否随航向旋转、画面速度（米/秒，仅展示） */
export function travelModeMotion(mode) {
  const key = String(mode || '').toLowerCase()
  const table = {
    walk: { rotate: 'flip', mps: 22, headingOffset: 0 },
    bike: { rotate: 'flip', mps: 40, headingOffset: 0 },
    drive: { rotate: 'heading', mps: 65, headingOffset: -90 },
    bus: { rotate: 'heading', mps: 48, headingOffset: -90 },
    metro: { rotate: 'heading', mps: 55, headingOffset: -90 },
    train: { rotate: 'heading', mps: 75, headingOffset: -90 },
    hsr: { rotate: 'heading', mps: 100, headingOffset: -90 },
    flight: { rotate: 'heading', mps: 160, headingOffset: 0 },
    stay: { rotate: 'none', mps: 0, headingOffset: 0 },
    other: { rotate: 'flip', mps: 50, headingOffset: 0 }
  }
  return table[key] || table.other
}

/** 火车/高铁/地铁：绘制前清洗折线几何 */
export function isRailTravelMode(mode) {
  const m = String(mode || '').toLowerCase()
  return m === 'hsr' || m === 'train' || m === 'metro'
}

function approxMeters(a, b) {
  const lat1 = Number(a[0])
  const lng1 = Number(a[1])
  const lat2 = Number(b[0])
  const lng2 = Number(b[1])
  const mid = ((lat1 + lat2) / 2) * Math.PI / 180
  const dx = (lng2 - lng1) * Math.cos(mid) * 111320
  const dy = (lat2 - lat1) * 110540
  return Math.sqrt(dx * dx + dy * dy)
}

function turnDegrees(a, b, c) {
  const mid = (Number(b[0]) * Math.PI) / 180
  const x1 = (Number(b[1]) - Number(a[1])) * Math.cos(mid)
  const y1 = Number(b[0]) - Number(a[0])
  const x2 = (Number(c[1]) - Number(b[1])) * Math.cos(mid)
  const y2 = Number(c[0]) - Number(b[0])
  const n1 = Math.hypot(x1, y1)
  const n2 = Math.hypot(x2, y2)
  if (n1 < 1e-12 || n2 < 1e-12) return 0
  let dot = (x1 * x2 + y1 * y2) / (n1 * n2)
  if (dot > 1) dot = 1
  if (dot < -1) dot = -1
  return Math.acos(dot) * 180 / Math.PI
}

/**
 * 铁路展示折线清洗：去掉站场打结、急折返与过密点（旧 routePath 立刻生效）。
 * 入参/出参均为地图坐标 [[lat,lng],...]
 */
export function cleanRailDisplayPath(latlngs, options = {}) {
  if (!Array.isArray(latlngs) || latlngs.length < 3) return latlngs
  const minMeters = options.minMeters != null ? options.minMeters : 40
  const loopClose = options.loopCloseMeters != null ? options.loopCloseMeters : 90
  const loopDetour = options.loopDetourMeters != null ? options.loopDetourMeters : 220

  let pts = []
  for (const p of latlngs) {
    if (!Array.isArray(p) || p.length < 2) continue
    const lat = Number(p[0])
    const lng = Number(p[1])
    if (Number.isNaN(lat) || Number.isNaN(lng)) continue
    pts.push([lat, lng])
  }
  if (pts.length < 3) return pts.length >= 2 ? pts : latlngs

  // 局部闭环（平行轨「泡泡」）
  let changed = true
  let guard = 0
  while (changed && guard++ < 10) {
    changed = false
    for (let i = 0; i < pts.length; i++) {
      let along = 0
      let bestJ = -1
      let bestDetour = 0
      for (let j = i + 2; j < pts.length; j++) {
        along += approxMeters(pts[j - 1], pts[j])
        if (along > 4000) break
        const close = approxMeters(pts[i], pts[j])
        if (close <= loopClose && along >= loopDetour && along - close > bestDetour) {
          bestDetour = along - close
          bestJ = j
        }
      }
      if (bestJ > i + 1) {
        pts = pts.slice(0, i + 1).concat(pts.slice(bestJ))
        changed = true
        break
      }
    }
  }

  // 急折返尖刺
  const noSpike = [pts[0]]
  for (let i = 1; i < pts.length - 1; i++) {
    const a = noSpike[noSpike.length - 1]
    const b = pts[i]
    const c = pts[i + 1]
    const turn = turnDegrees(a, b, c)
    const ab = approxMeters(a, b)
    const bc = approxMeters(b, c)
    const ac = approxMeters(a, c)
    if (turn >= 110 && ab + bc > ac * 1.25 && ab + bc - ac > 35) continue
    noSpike.push(b)
  }
  noSpike.push(pts[pts.length - 1])
  pts = noSpike

  // 距离抽稀
  const out = [pts[0]]
  let last = pts[0]
  for (let i = 1; i < pts.length - 1; i++) {
    if (approxMeters(last, pts[i]) >= minMeters) {
      out.push(pts[i])
      last = pts[i]
    }
  }
  const end = pts[pts.length - 1]
  if (approxMeters(out[out.length - 1], end) > 1) out.push(end)
  return out.length >= 2 ? out : latlngs
}

const PI = Math.PI
const A = 6378245.0
const EE = 0.00669342162296594323

function outOfChina(lng, lat) {
  return lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271
}

function transformLat(lng, lat) {
  let ret = -100.0 + 2.0 * lng + 3.0 * lat + 0.2 * lat * lat + 0.1 * lng * lat + 0.2 * Math.sqrt(Math.abs(lng))
  ret += ((20.0 * Math.sin(6.0 * lng * PI) + 20.0 * Math.sin(2.0 * lng * PI)) * 2.0) / 3.0
  ret += ((20.0 * Math.sin(lat * PI) + 40.0 * Math.sin((lat / 3.0) * PI)) * 2.0) / 3.0
  ret += ((160.0 * Math.sin((lat / 12.0) * PI) + 320 * Math.sin((lat * PI) / 30.0)) * 2.0) / 3.0
  return ret
}

function transformLng(lng, lat) {
  let ret = 300.0 + lng + 2.0 * lat + 0.1 * lng * lng + 0.1 * lng * lat + 0.1 * Math.sqrt(Math.abs(lng))
  ret += ((20.0 * Math.sin(6.0 * lng * PI) + 20.0 * Math.sin(2.0 * lng * PI)) * 2.0) / 3.0
  ret += ((20.0 * Math.sin(lng * PI) + 40.0 * Math.sin((lng / 3.0) * PI)) * 2.0) / 3.0
  ret += ((150.0 * Math.sin((lng / 12.0) * PI) + 300.0 * Math.sin((lng / 30.0) * PI)) * 2.0) / 3.0
  return ret
}

/** EXIF/GPS 多为 WGS84，高德底图为 GCJ-02 */
export function wgs84ToGcj02(lng, lat) {
  if (outOfChina(lng, lat)) return [lng, lat]
  let dLat = transformLat(lng - 105.0, lat - 35.0)
  let dLng = transformLng(lng - 105.0, lat - 35.0)
  const radLat = (lat / 180.0) * PI
  let magic = Math.sin(radLat)
  magic = 1 - EE * magic * magic
  const sqrtMagic = Math.sqrt(magic)
  dLat = (dLat * 180.0) / (((A * (1 - EE)) / (magic * sqrtMagic)) * PI)
  dLng = (dLng * 180.0) / ((A / sqrtMagic) * Math.cos(radLat) * PI)
  return [lng + dLng, lat + dLat]
}

/** 地图 GCJ-02 → 库内 WGS84 */
export function gcj02ToWgs84(lng, lat) {
  if (outOfChina(lng, lat)) return [lng, lat]
  const [mgLng, mgLat] = wgs84ToGcj02(lng, lat)
  return [lng * 2 - mgLng, lat * 2 - mgLat]
}

export function toMapLatLng(point) {
  const [lng, lat] = wgs84ToGcj02(Number(point.longitude), Number(point.latitude))
  return [lat, lng]
}

/** Leaflet latlng(GCJ) → 库内 latitude/longitude(WGS84) */
export function fromMapLatLng(lat, lng) {
  const [wgsLng, wgsLat] = gcj02ToWgs84(Number(lng), Number(lat))
  return {
    latitude: Number(wgsLat.toFixed(7)),
    longitude: Number(wgsLng.toFixed(7))
  }
}

/** 时间插值 / AI / 区域中心等兜底坐标（含已确认上主轨迹的，仍显示来源角标） */
export function isEstimatedLocation(point) {
  const s = point?.locationSource
  return s === 'time_interp' || s === 'ai_landmark' || s === 'region_center'
}

/** 已确认上主轨迹（置信度≈1），仍保留 AI/估/区 标记 */
export function isConfirmedEstimated(point) {
  if (!isEstimatedLocation(point)) return false
  const c = Number(point?.locationConfidence)
  return Number.isFinite(c) && c >= 0.999
}

/** 尚未确认、仍待处理的估计点 */
export function isPendingEstimated(point) {
  return isEstimatedLocation(point) && !isConfirmedEstimated(point)
}

/** 估计来源短标签：区 / AI / 估 */
export function estimatedSourceLabel(point) {
  const s = point?.locationSource
  if (s === 'region_center') return '区'
  if (s === 'ai_landmark') return 'AI'
  if (s === 'time_interp') return '估'
  return '估'
}

export function estimatedSourceTitle(point) {
  const s = point?.locationSource
  const confirmed = isConfirmedEstimated(point)
  if (s === 'region_center') {
    return confirmed ? '区域粗定位（已确认上主轨迹）' : '区域粗定位，可拖动微调后确认上主轨迹'
  }
  if (s === 'ai_landmark') {
    return confirmed ? 'AI 地标（已确认上主轨迹）' : 'AI 地标估计，可拖动微调后确认上主轨迹'
  }
  if (s === 'time_interp') {
    return confirmed ? '时间推算（已确认上主轨迹）' : '按拍摄时间估计，可拖动微调后确认上主轨迹'
  }
  return confirmed ? '估计位置（已确认上主轨迹）' : '估计位置，可拖动微调后确认上主轨迹'
}

/**
 * 解析点位上缓存的真实路线（GCJ-02，[[lat,lng],...]）
 * 无有效路径时返回 null
 */
export function parseRoutePath(routePath) {
  if (!routePath) return null
  let raw = routePath
  if (typeof raw === 'string') {
    try {
      raw = JSON.parse(raw)
    } catch (e) {
      return null
    }
  }
  if (!Array.isArray(raw) || raw.length < 2) return null
  const latlngs = []
  for (const item of raw) {
    if (Array.isArray(item) && item.length >= 2) {
      const lat = Number(item[0])
      const lng = Number(item[1])
      if (!Number.isNaN(lat) && !Number.isNaN(lng)) latlngs.push([lat, lng])
    } else if (item && item.lat != null && item.lng != null) {
      const lat = Number(item.lat)
      const lng = Number(item.lng)
      if (!Number.isNaN(lat) && !Number.isNaN(lng)) latlngs.push([lat, lng])
    }
  }
  return latlngs.length >= 2 ? latlngs : null
}

/** 路段折线：有 routePath 则绘制（含手动连接的照片↔自定义/GPX 锚点）；无折线不自动跨类型拉线 */
export function segmentLatLngs(fromPoint, toPoint) {
  const routed = parseRoutePath(fromPoint?.routePath)
  if (routed) return routed
  const fromWp = isWaypointPoint(fromPoint)
  const toWp = isWaypointPoint(toPoint)
  // 无显式折线：触及自定义点则断开；照片↔照片仍可画虚直线
  if (fromWp || toWp) return null
  if (fromPoint?.latitude == null || fromPoint?.longitude == null
    || toPoint?.latitude == null || toPoint?.longitude == null) {
    return null
  }
  return [toMapLatLng(fromPoint), toMapLatLng(toPoint)]
}

/**
 * 地图 GPX 叠层展示：显示轨迹时原样返回；关闭轨迹线时只保留 matchedPhotos，
 * 让时间匹配到的照片/视频继续停在 GPX 坐标，避免估点/漂移 GPS 回到错误位置。
 */
export function overlaysForMapDisplay(overlays, showPath) {
  const list = Array.isArray(overlays) ? overlays : []
  if (!list.length) return []
  if (showPath) return list
  return list
    .map((o) => {
      if (!o) return null
      const matched = Array.isArray(o.matchedPhotos) ? o.matchedPhotos : []
      if (!matched.length) return null
      return {
        gpxId: o.gpxId,
        fileName: o.fileName,
        showPath: false,
        matchedPhotos: matched
      }
    })
    .filter(Boolean)
}

/** 轨迹是否还有未规划真实路线的路段（触及自定义途经点的空隙不算缺失） */
export function hasMissingRoutePaths(points) {
  const list = filterValidPoints(points)
  for (let i = 0; i < list.length - 1; i++) {
    const from = list[i]
    const to = list[i + 1]
    if (isWaypointPoint(from) || isWaypointPoint(to)) {
      // 照片↔自定义 / 自定义无折线：刻意断开
      continue
    }
    if (!parseRoutePath(from.routePath)) return true
  }
  return false
}

function approxKmBetween(a, b) {
  const dlat = Number(a[0]) - Number(b[0])
  const dlng = Number(a[1]) - Number(b[1])
  return Math.sqrt(dlat * dlat + dlng * dlng) * 111
}

/**
 * 是否需要重新贴路。
 * 注意：地铁/高铁/公交等是用户可选手动方式，不能仅因 mode 就判定需重算（否则会被改回步行）。
 * 火车/高铁/飞机两点直线也可能是刻意结果，打开轨迹时勿因此强制全量重算。
 */
export function hasUnstableAutoRoutes(points) {
  const list = filterValidPoints(points)
  for (let i = 0; i < list.length - 1; i++) {
    const from = list[i]
    const to = list[i + 1]
    if (isWaypointPoint(from) || isWaypointPoint(to)) continue
    const mode = String(from?.travelMode || '').toLowerCase()
    // 用户手选长途方式：两点折线不视为「损坏」
    if (mode === 'hsr' || mode === 'train' || mode === 'flight' || mode === 'metro' || mode === 'bus') {
      continue
    }
    const path = parseRoutePath(from.routePath)
    const a = toMapLatLng(from)
    const b = toMapLatLng(to)
    const approxKm = approxKmBetween(a, b)
    if (approxKm <= 0.03) continue
    // 有一定距离却只有直线两点（步行/驾车等应贴路）
    if (!path || path.length <= 2) {
      if (approxKm > 0.04) return true
      continue
    }
    // 折线端点偏离照片标记（旧版切片串段）
    const p0 = path[0]
    const p1 = path[path.length - 1]
    const fwdOk = approxKmBetween(p0, a) < 0.12 && approxKmBetween(p1, b) < 0.12
    const revOk = approxKmBetween(p0, b) < 0.12 && approxKmBetween(p1, a) < 0.12
    if (!fwdOk && !revOk) return true
  }
  return false
}

/** 方位角（度，0=北，顺时针），输入 [lat,lng] */
export function bearingDeg(from, to) {
  const lat1 = (Number(from[0]) * PI) / 180
  const lat2 = (Number(to[0]) * PI) / 180
  const dLng = ((Number(to[1]) - Number(from[1])) * PI) / 180
  const y = Math.sin(dLng) * Math.cos(lat2)
  const x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLng)
  return (Math.atan2(y, x) * 180 / PI + 360) % 360
}

function haversineMeters(a, b) {
  const R = 6371000
  const lat1 = (Number(a[0]) * PI) / 180
  const lat2 = (Number(b[0]) * PI) / 180
  const dLat = lat2 - lat1
  const dLng = ((Number(b[1]) - Number(a[1])) * PI) / 180
  const h = Math.sin(dLat / 2) ** 2
    + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) ** 2
  return 2 * R * Math.asin(Math.min(1, Math.sqrt(h)))
}

/**
 * 沿折线按间距取样箭头位置
 * @returns {{ latlng: [lat,lng], bearing: number }[]}
 */
export function sampleDirectionArrows(latlngs, gapMeters = 900) {
  if (!latlngs || latlngs.length < 2) return []
  const gap = Math.max(200, Number(gapMeters) || 900)
  const arrows = []
  let acc = 0
  let nextAt = gap * 0.55
  for (let i = 1; i < latlngs.length; i++) {
    const a = latlngs[i - 1]
    const b = latlngs[i]
    const seg = haversineMeters(a, b)
    if (seg < 1) continue
    const br = bearingDeg(a, b)
    while (acc + seg >= nextAt) {
      const t = (nextAt - acc) / seg
      const lat = Number(a[0]) + (Number(b[0]) - Number(a[0])) * t
      const lng = Number(a[1]) + (Number(b[1]) - Number(a[1])) * t
      arrows.push({ latlng: [lat, lng], bearing: br })
      nextAt += gap
      if (arrows.length > 80) return arrows
    }
    acc += seg
  }
  // 保证至少有一个方向箭头
  if (!arrows.length && latlngs.length >= 2) {
    const mid = Math.floor(latlngs.length / 2)
    const i = Math.max(1, mid)
    arrows.push({
      latlng: latlngs[i],
      bearing: bearingDeg(latlngs[i - 1], latlngs[i])
    })
  }
  return arrows
}

export function resolveUrl(url) {
  if (!url) return ''
  if (isExternal(url)) return url
  return import.meta.env.VITE_APP_BASE_API + url
}

function pointThumbUrl(point) {
  const u = point?.thumbUrl || point?.thumb_url
  return u && String(u).trim() ? String(u).trim() : ''
}

export function mediaSrc(point, original = false) {
  if (original) {
    if (point?.photoId) {
      // 地图弹层默认播 480p30 浏览档，避免直接拉原片
      if (Number(point?.fileType) === 2) {
        return resolveUrl('/album/photo/media/' + point.photoId + '?quality=480p&fps=30')
      }
      return resolveUrl('/album/photo/media/' + point.photoId + '?original=true')
    }
    if (point?.fileUrl) return resolveUrl(point.fileUrl)
    return ''
  }
  const thumb = pointThumbUrl(point)
  if (Number(point?.fileType) === 2 && !thumb && point?.photoId) {
    return resolveUrl('/album/photo/thumb/' + point.photoId)
  }
  if (thumb) return resolveUrl(thumb)
  if (point?.photoId) {
    return resolveUrl('/album/photo/media/' + point.photoId)
  }
  return ''
}

export function escapeHtml(str) {
  return String(str ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

export function formatCount(n) {
  const num = Number(n) || 0
  return num.toLocaleString('en-US')
}

function pickCoverPoint(markers) {
  const list = markers || []
  for (let i = 0; i < list.length; i++) {
    const p = list[i].options?.photoPoint
    if (p && (p.photoId || p.thumbUrl)) return p
  }
  return list[0]?.options?.photoPoint || null
}

/** 缩略图 DivIcon；count>1 时显示数量角标 */
export function createThumbDivIcon(point, count = 1, options = {}) {
  const isVideo = Number(point?.fileType) === 2
  const estimated = count <= 1 && isEstimatedLocation(point)
  const selected = !!(options.selected && count <= 1)
  const thumb = mediaSrc(point, false)
  const badge = count > 1
    ? `<span class="pmc-badge">${formatCount(count)}</span>`
    : (estimated
      ? `<span class="pmc-est-badge pmc-est-badge--${point?.locationSource || 'time_interp'}${isConfirmedEstimated(point) ? ' pmc-est-badge--confirmed' : ''}" title="${escapeHtml(estimatedSourceTitle(point))}">${estimatedSourceLabel(point)}</span>`
      : '')
  const pick = selected ? '<span class="pmc-ai-pick" title="已选入 AI 识别">✓</span>' : ''
  const video = isVideo && count <= 1 ? '<span class="pmc-video">▶</span>' : ''
  // Leaflet DivIcon 在 transform/overflow 容器里，native lazy 常不触发加载 → 一直灰底
  const fallbackText = isVideo ? '视频' : '图'
  const img = thumb
    ? `<img src="${escapeHtml(thumb)}" decoding="async" alt="" onerror="this.onerror=null;this.replaceWith(Object.assign(document.createElement('span'),{className:'pmc-fallback',textContent:'${fallbackText}'}))" />`
    : `<span class="pmc-fallback">${fallbackText}</span>`
  const classes = [
    'pmc-marker',
    estimated ? 'pmc-marker--estimated' : '',
    selected ? 'pmc-marker--ai-selected' : ''
  ].filter(Boolean).join(' ')
  return L.divIcon({
    className: classes,
    html: `<div class="pmc-wrap"><div class="pmc-pin">${img}${video}</div>${badge}${pick}</div>`,
    iconSize: [52, 52],
    iconAnchor: [26, 52],
    popupAnchor: [0, -48]
  })
}

export function clusterRadiusForZoom(zoom) {
  if (zoom <= 5) return 80
  if (zoom <= 8) return 60
  if (zoom <= 11) return 50
  if (zoom <= 14) return 40
  return 30
}

export function createClusterGroup() {
  return L.markerClusterGroup({
    showCoverageOnHover: false,
    zoomToBoundsOnClick: true,
    spiderfyOnMaxZoom: true,
    disableClusteringAtZoom: 16,
    maxClusterRadius: zoom => clusterRadiusForZoom(zoom),
    animate: true,
    animateAddingMarkers: false,
    chunkedLoading: true,
    iconCreateFunction(cluster) {
      const markers = cluster.getAllChildMarkers()
      const cover = pickCoverPoint(markers)
      return createThumbDivIcon(cover, cluster.getChildCount())
    }
  })
}

/** 估计点专用聚合：缩小后合并，放大/点开时蜘蛛腿散开，便于点选叠在一起的照片 */
export function createEstimatedClusterGroup() {
  return L.markerClusterGroup({
    showCoverageOnHover: false,
    zoomToBoundsOnClick: true,
    spiderfyOnMaxZoom: true,
    disableClusteringAtZoom: 17,
    maxClusterRadius: zoom => {
      if (zoom <= 12) return 48
      if (zoom <= 15) return 36
      return 22
    },
    spiderfyDistanceMultiplier: 2.2,
    animate: true,
    animateAddingMarkers: false,
    chunkedLoading: true,
    clusterPane: 'estimatedPane',
    spiderLegPolylineOptions: { weight: 2, color: '#9b59b6', opacity: 0.7 },
    iconCreateFunction(cluster) {
      const markers = cluster.getAllChildMarkers()
      const cover = pickCoverPoint(markers)
      const icon = createThumbDivIcon(cover, cluster.getChildCount())
      if (icon?.options) {
        icon.options.className = `${icon.options.className || 'pmc-marker'} pmc-marker--estimated pmc-marker--est-cluster`
      }
      return icon
    }
  })
}

export function filterValidPoints(points) {
  return (points || []).filter(p =>
    p && p.latitude != null && p.longitude != null
    && !Number.isNaN(Number(p.latitude)) && !Number.isNaN(Number(p.longitude))
  )
}

/** 无关联照片的自定义途经点（增补路段插入） */
export function isWaypointPoint(point) {
  if (!point) return false
  const pid = point.photoId
  const hasPhoto = pid != null && pid !== '' && Number(pid) !== 0
  if (hasPhoto) return false
  // 有缩略图/原图的一律按照片点处理
  if (point.fileUrl || point.thumbUrl) return false
  return true
}

const MAP_VIDEO_DEFAULT_QUALITY = '480p'

function buildMapVideoHtml(point, thumb, src) {
  bindMapVideoQualityControls()
  const photoId = point?.photoId != null ? escapeHtml(String(point.photoId)) : ''
  const items = VIDEO_QUALITY_OPTIONS.map(opt => {
    const active = opt.value === MAP_VIDEO_DEFAULT_QUALITY ? ' is-active' : ''
    return `<button type="button" class="pmc-quality-item${active}" data-quality="${escapeHtml(opt.value)}">${escapeHtml(opt.label)}</button>`
  }).join('')
  return `
    <div class="pmc-video-box" data-photo-id="${photoId}">
      <video class="pmc-media" preload="metadata" poster="${thumb}" src="${src}" data-quality="${MAP_VIDEO_DEFAULT_QUALITY}" playsinline></video>
      <div class="pmc-video-bar">
        <button type="button" class="pmc-video-play" title="播放" aria-label="播放">
          <svg class="icon-play" viewBox="0 0 24 24" aria-hidden="true"><path d="M8 5v14l11-7z"/></svg>
          <svg class="icon-pause" viewBox="0 0 24 24" aria-hidden="true"><path d="M6 5h4v14H6zm8 0h4v14h-4z"/></svg>
        </button>
        <input class="pmc-video-progress" type="range" min="0" max="1000" value="0" aria-label="进度" />
        <button type="button" class="pmc-video-mute" title="静音" aria-label="静音">
          <svg class="icon-vol" viewBox="0 0 24 24" aria-hidden="true"><path d="M3 10v4h4l5 4V6L7 10H3zm13.5 2a4.5 4.5 0 0 0-2.5-4.03v8.06A4.5 4.5 0 0 0 16.5 12z"/></svg>
          <svg class="icon-mute" viewBox="0 0 24 24" aria-hidden="true"><path d="M3 10v4h4l5 4V6L7 10H3zm13.59 2.41 2.12-2.12 1.41 1.41-2.12 2.12 2.12 2.12-1.41 1.41-2.12-2.12-2.12 2.12-1.41-1.41 2.12-2.12-2.12-2.12 1.41-1.41z"/></svg>
        </button>
        <button type="button" class="pmc-video-full" title="全屏" aria-label="全屏">
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M7 14H5v5h5v-2H7v-3zm0-4h3V7h2V5H5v5zm10 7h-3v2h5v-5h-2v3zM14 5v2h3v3h2V5h-5z"/></svg>
        </button>
        <button type="button" class="pmc-video-more" title="清晰度" aria-label="清晰度">⋮</button>
      </div>
      <div class="pmc-video-menu" role="menu">
        <div class="pmc-video-menu-title">清晰度</div>
        ${items}
        <button type="button" class="pmc-quality-item pmc-video-download">下载原片</button>
      </div>
    </div>
  `
}

let mapVideoMenuBound = false

function closeMapVideoMenus(except) {
  document.querySelectorAll('.pmc-video-menu.is-open').forEach(menu => {
    if (menu !== except) menu.classList.remove('is-open')
  })
}

function applyMapVideoQuality(item) {
  const box = item.closest('.pmc-video-box')
  const video = box?.querySelector('video')
  const photoId = box?.dataset.photoId
  const quality = item.dataset.quality
  if (!video || !photoId || !quality) return
  box.querySelectorAll('.pmc-quality-item').forEach(btn => {
    btn.classList.toggle('is-active', btn === item)
  })
  closeMapVideoMenus()
  if (video.dataset.quality === quality) return
  const time = video.currentTime
  const wasPlaying = !video.paused && !video.ended
  video.dataset.quality = quality
  video.src = videoPlaySrc(photoId, quality, 30)
  const resume = () => {
    if (Number.isFinite(time) && time > 0.2) {
      try {
        const end = Number.isFinite(video.duration) ? Math.max(0, video.duration - 0.05) : time
        video.currentTime = Math.min(time, end)
      } catch (_) { /* ignore */ }
    }
    if (wasPlaying) video.play().catch(() => {})
  }
  video.addEventListener('loadedmetadata', resume, { once: true })
}

function mapVideoOf(node) {
  return node?.closest?.('.pmc-video-box')?.querySelector('video') || null
}

function syncMapVideoChrome(video) {
  const box = video?.closest?.('.pmc-video-box')
  if (!box) return
  box.classList.toggle('is-playing', !video.paused && !video.ended)
  box.classList.toggle('is-muted', !!video.muted || video.volume === 0)
  const range = box.querySelector('.pmc-video-progress')
  if (!range || range.dataset.scrubbing === '1') return
  const dur = video.duration
  if (!Number.isFinite(dur) || dur <= 0) return
  range.value = String(Math.round((video.currentTime / dur) * 1000))
}

function onMapVideoMedia(event) {
  const video = event.target
  if (!video?.classList?.contains('pmc-media')) return
  syncMapVideoChrome(video)
}

function onMapVideoPointer(event) {
  const target = event.target
  if (!target?.closest) return
  if (target.classList?.contains('pmc-media') && target.closest('.pmc-video-box')) {
    event.preventDefault()
    event.stopPropagation()
    if (target.paused || target.ended) target.play().catch(() => {})
    else target.pause()
    return
  }
  const playBtn = target.closest('.pmc-video-play')
  if (playBtn) {
    event.preventDefault()
    event.stopPropagation()
    const video = mapVideoOf(playBtn)
    if (!video) return
    if (video.paused || video.ended) video.play().catch(() => {})
    else video.pause()
    return
  }
  const muteBtn = target.closest('.pmc-video-mute')
  if (muteBtn) {
    event.preventDefault()
    event.stopPropagation()
    const video = mapVideoOf(muteBtn)
    if (!video) return
    video.muted = !video.muted
    syncMapVideoChrome(video)
    return
  }
  const fullBtn = target.closest('.pmc-video-full')
  if (fullBtn) {
    event.preventDefault()
    event.stopPropagation()
    const box = fullBtn.closest('.pmc-video-box')
    if (!box) return
    if (document.fullscreenElement === box) document.exitFullscreen?.()
    else box.requestFullscreen?.()
    return
  }
  const more = target.closest('.pmc-video-more')
  if (more) {
    event.preventDefault()
    event.stopPropagation()
    const menu = more.closest('.pmc-video-box')?.querySelector('.pmc-video-menu')
    if (!menu) return
    const willOpen = !menu.classList.contains('is-open')
    closeMapVideoMenus()
    if (willOpen) menu.classList.add('is-open')
    return
  }
  const downloadBtn = target.closest('.pmc-video-download')
  if (downloadBtn) {
    event.preventDefault()
    event.stopPropagation()
    const photoId = downloadBtn.closest('.pmc-video-box')?.dataset.photoId
    closeMapVideoMenus()
    if (!photoId) return
    const link = document.createElement('a')
    link.href = `${videoPlaySrc(photoId, 'original')}&download=true`
    link.target = '_blank'
    link.rel = 'noopener'
    link.click()
    return
  }
  const item = target.closest('.pmc-quality-item')
  if (item) {
    event.preventDefault()
    event.stopPropagation()
    applyMapVideoQuality(item)
    return
  }
  if (!target.closest('.pmc-video-menu')) closeMapVideoMenus()
}

function onMapVideoScrub(event) {
  const range = event.target?.closest?.('.pmc-video-progress')
  if (!range) return
  event.stopPropagation()
  const video = mapVideoOf(range)
  const dur = video?.duration
  if (!video || !Number.isFinite(dur) || dur <= 0) return
  video.currentTime = (Number(range.value) / 1000) * dur
}

/** 地图弹层是静态 HTML，清晰度菜单用一次文档委托（Leaflet / 高德共用） */
export function bindMapVideoQualityControls() {
  if (mapVideoMenuBound || typeof document === 'undefined') return
  mapVideoMenuBound = true
  document.addEventListener('click', onMapVideoPointer, true)
  document.addEventListener('input', onMapVideoScrub, true)
  document.addEventListener('pointerdown', (event) => {
    if (event.target?.closest?.('.pmc-video-progress')) {
      event.target.dataset.scrubbing = '1'
    }
  }, true)
  document.addEventListener('pointerup', () => {
    document.querySelectorAll('.pmc-video-progress[data-scrubbing]').forEach((range) => {
      delete range.dataset.scrubbing
    })
  }, true)
  document.addEventListener('timeupdate', onMapVideoMedia, true)
  document.addEventListener('play', onMapVideoMedia, true)
  document.addEventListener('pause', onMapVideoMedia, true)
  document.addEventListener('ended', onMapVideoMedia, true)
  document.addEventListener('volumechange', onMapVideoMedia, true)
}

export function buildPopupHtml(point) {
  const waypoint = isWaypointPoint(point)
  const isVideo = Number(point.fileType) === 2
  const thumb = mediaSrc(point, false)
  const original = mediaSrc(point, true)
  const title = escapeHtml(
    point.fileName
      || point.description
      || (waypoint ? '途经点' : (isVideo ? '视频' : '图片'))
  )
  const time = escapeHtml(point.pointTime || point.shootTime || '')
  const addr = escapeHtml(point.address || '')
  const desc = escapeHtml(point.description || '')
  const modeLabel = escapeHtml(travelModeLabel(point.travelMode))
  const lat = point.latitude
  const lng = point.longitude
  const estimated = isEstimatedLocation(point)
  const estTip = estimated
    ? `<div class="pmc-est-tip">${escapeHtml(estimatedSourceTitle(point))}</div>`
    : ''
  if (waypoint) {
    return `
      <div class="pmc-popup">
        <div class="pmc-title">${point.sequence != null ? '#' + point.sequence + ' ' : ''}${title}</div>
        <div class="pmc-meta">自定义途经点（无照片）</div>
        ${modeLabel ? `<div class="pmc-mode">下一程：${modeLabel}</div>` : ''}
        ${desc && desc !== title ? `<div class="pmc-desc">${desc}</div>` : ''}
        ${time ? `<div class="pmc-meta">${time}</div>` : ''}
        <div class="pmc-meta">${lat}, ${lng}</div>
      </div>
    `
  }
  // 弹层视频优先 480p30；浏览档未就绪时后端回退原片（此前 409 导致地图无法播放）
  const media = isVideo
    ? buildMapVideoHtml(point, thumb, original)
    : `<img class="pmc-media" src="${thumb || original}" alt="${title}" loading="eager" decoding="async" />`
  return `
    <div class="pmc-popup">
      <div class="pmc-title">${point.sequence != null ? '#' + point.sequence + ' ' : ''}${title}</div>
      ${estTip}
      ${media}
      ${modeLabel ? `<div class="pmc-mode">下一程：${modeLabel}</div>` : ''}
      ${desc ? `<div class="pmc-desc">${desc}</div>` : ''}
      ${time ? `<div class="pmc-meta">${time}</div>` : ''}
      ${addr ? `<div class="pmc-meta">${addr}</div>` : ''}
      <div class="pmc-meta">${lat}, ${lng}</div>
    </div>
  `
}

function createWaypointDivIcon(point) {
  const label = escapeHtml((point.description || '途经').slice(0, 10))
  return L.divIcon({
    className: 'pmc-waypoint-marker',
    html: `<div class="pmc-waypoint" title="${label}"><span class="pmc-waypoint-dot"></span><span class="pmc-waypoint-label">${label}</span></div>`,
    iconSize: [88, 28],
    iconAnchor: [12, 14],
    popupAnchor: [0, -12]
  })
}

export function createPhotoMarker(point, options = {}) {
  const waypoint = isWaypointPoint(point)
  const estimated = isEstimatedLocation(point)
  const selected = !!options.selected
  // locationCorrectable / estimated 微调：由调用方传入 draggable
  const draggable = !!(options.draggable && !waypoint && !options.aiPickMode)
  const markerOpts = {
    icon: waypoint
      ? createWaypointDivIcon(point)
      : createThumbDivIcon(point, 1, { selected }),
    keyboard: false,
    riseOnHover: true,
    draggable,
    autoPan: draggable,
    photoPoint: point,
    zIndexOffset: estimated || options.correcting
      ? (selected ? 2600 : 2000)
      : 0
  }
  if (options.pane) {
    markerOpts.pane = options.pane
  }
  const marker = L.marker(toMapLatLng(point), markerOpts)
  marker.bindPopup(() => buildPopupHtml(point), {
    maxWidth: 320,
    className: 'pmc-popup-wrap',
    autoPan: true
  })
  return marker
}
