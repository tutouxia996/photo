import L from '@/utils/leaflet'
import 'leaflet.markercluster'
import 'leaflet.markercluster/dist/MarkerCluster.css'
import { isExternal } from '@/utils/validate'

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

export function toMapLatLng(point) {
  const [lng, lat] = wgs84ToGcj02(Number(point.longitude), Number(point.latitude))
  return [lat, lng]
}

export function resolveUrl(url) {
  if (!url) return ''
  if (isExternal(url)) return url
  return import.meta.env.VITE_APP_BASE_API + url
}

export function mediaSrc(point, original = false) {
  if (!point?.photoId) {
    if (point?.thumbUrl) return resolveUrl(point.thumbUrl)
    return ''
  }
  const q = original ? '?original=true' : ''
  return resolveUrl('/album/photo/media/' + point.photoId + q)
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
export function createThumbDivIcon(point, count = 1) {
  const isVideo = point?.fileType === 2
  const thumb = mediaSrc(point, false)
  const badge = count > 1
    ? `<span class="pmc-badge">${formatCount(count)}</span>`
    : ''
  const video = isVideo && count <= 1 ? '<span class="pmc-video">▶</span>' : ''
  const img = thumb
    ? `<img src="${thumb}" loading="lazy" decoding="async" alt="" />`
    : `<span class="pmc-fallback">${isVideo ? '视频' : '图'}</span>`
  // 角标放在 pin 外，pin 内 overflow:hidden 才能完整包住缩略图
  return L.divIcon({
    className: 'pmc-marker',
    html: `<div class="pmc-wrap"><div class="pmc-pin">${img}${video}</div>${badge}</div>`,
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

export function filterValidPoints(points) {
  return (points || []).filter(p =>
    p && p.latitude != null && p.longitude != null
    && !Number.isNaN(Number(p.latitude)) && !Number.isNaN(Number(p.longitude))
  )
}

export function buildPopupHtml(point) {
  const isVideo = point.fileType === 2
  const thumb = mediaSrc(point, false)
  const original = mediaSrc(point, true)
  const title = escapeHtml(point.fileName || (isVideo ? '视频' : '图片'))
  const time = escapeHtml(point.pointTime || point.shootTime || '')
  const addr = escapeHtml(point.address || '')
  const lat = point.latitude
  const lng = point.longitude
  const media = isVideo
    ? `<video class="pmc-media" controls preload="metadata" poster="${thumb}" src="${original}"></video>`
    : `<img class="pmc-media" src="${original || thumb}" alt="${title}" />`
  return `
    <div class="pmc-popup">
      <div class="pmc-title">${point.sequence != null ? '#' + point.sequence + ' ' : ''}${title}</div>
      ${media}
      ${time ? `<div class="pmc-meta">${time}</div>` : ''}
      ${addr ? `<div class="pmc-meta">${addr}</div>` : ''}
      <div class="pmc-meta">${lat}, ${lng}</div>
    </div>
  `
}

export function createPhotoMarker(point) {
  const marker = L.marker(toMapLatLng(point), {
    icon: createThumbDivIcon(point, 1),
    keyboard: false,
    riseOnHover: true,
    photoPoint: point
  })
  marker.bindPopup(() => buildPopupHtml(point), {
    maxWidth: 320,
    className: 'pmc-popup-wrap',
    autoPan: true
  })
  return marker
}
