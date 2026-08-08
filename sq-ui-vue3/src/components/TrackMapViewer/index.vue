<template>
  <div class="track-map-viewer">
    <div class="map-toolbar">
      <div v-if="track" class="track-meta">
        <span class="name">{{ track.trackName || '未命名轨迹' }}</span>
        <span class="stat">点位 {{ track.pointCount ?? points.length }}</span>
        <span class="stat">里程 {{ formatDistance(track.totalDistance) }}</span>
        <span class="stat">时长 {{ formatDuration(track.totalDuration) }}</span>
      </div>
      <div class="map-style-switch">
        <button
          v-for="item in mapStyles"
          :key="item.key"
          type="button"
          class="style-btn"
          :class="{ active: currentStyle === item.key }"
          @click="switchMapStyle(item.key)"
        >
          {{ item.label }}
        </button>
      </div>
    </div>
    <div ref="mapEl" class="map-canvas" />
    <div v-if="!points.length" class="map-empty">暂无轨迹点位</div>
  </div>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { isExternal } from '@/utils/validate'

const props = defineProps({
  track: { type: Object, default: null },
  points: { type: Array, default: () => [] }
})

const mapEl = ref(null)
const currentStyle = ref('normal')
let map = null
let layerGroup = null
let baseLayer = null
let labelLayer = null

const mapStyles = [
  { key: 'normal', label: '标准' },
  { key: 'satellite', label: '卫星' },
  { key: 'hybrid', label: '混合' }
]

/** 高德公开瓦片原生约到 18 级；maxZoom=22 + maxNativeZoom=18 可继续放大（瓦片拉伸） */
const TILE_OPTS = {
  maxZoom: 22,
  maxNativeZoom: 18,
  subdomains: '1234',
  attribution: '&copy; 高德地图'
}

const STYLE_LAYERS = {
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

/** EXIF/GPS 多为 WGS84，高德底图为 GCJ-02，显示前需纠偏 */
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

function wgs84ToGcj02(lng, lat) {
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

function toMapLatLng(point) {
  const [lng, lat] = wgs84ToGcj02(Number(point.longitude), Number(point.latitude))
  return [lat, lng]
}

function resolveUrl(url) {
  if (!url) return ''
  if (isExternal(url)) return url
  return import.meta.env.VITE_APP_BASE_API + url
}

function mediaSrc(point, original = false) {
  if (!point?.photoId) return ''
  const q = original ? '?original=true' : ''
  return resolveUrl('/album/photo/media/' + point.photoId + q)
}

function formatDistance(km) {
  if (km == null || km === '') return '-'
  const n = Number(km)
  if (Number.isNaN(n)) return '-'
  return n < 1 ? `${(n * 1000).toFixed(0)} m` : `${n.toFixed(2)} km`
}

function formatDuration(sec) {
  if (sec == null || sec === '') return '-'
  const total = Math.max(0, Math.floor(Number(sec) || 0))
  const h = Math.floor(total / 3600)
  const m = Math.floor((total % 3600) / 60)
  const r = total % 60
  return `${h}小时${m}分钟${r}秒`
}

function escapeHtml(str) {
  return String(str ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

function buildPopupHtml(point) {
  const isVideo = point.fileType === 2
  const thumb = mediaSrc(point, false)
  const original = mediaSrc(point, true)
  const title = escapeHtml(point.fileName || (isVideo ? '视频' : '图片'))
  const time = escapeHtml(point.pointTime || '')
  const addr = escapeHtml(point.address || '')
  const lat = point.latitude
  const lng = point.longitude
  const media = isVideo
    ? `<video class="tmv-media" controls preload="metadata" poster="${thumb}" src="${original}"></video>`
    : `<img class="tmv-media" src="${original || thumb}" alt="${title}" />`
  return `
    <div class="tmv-popup">
      <div class="tmv-title">#${point.sequence || ''} ${title}</div>
      ${media}
      <div class="tmv-meta">${time}</div>
      ${addr ? `<div class="tmv-meta">${addr}</div>` : ''}
      <div class="tmv-meta">${lat}, ${lng}</div>
    </div>
  `
}

function createThumbIcon(point) {
  const isVideo = point.fileType === 2
  const thumb = mediaSrc(point, false)
  const badge = isVideo ? '<span class="tmv-badge">▶</span>' : ''
  const img = thumb
    ? `<img src="${thumb}" alt="" />`
    : `<span class="tmv-fallback">${isVideo ? '视频' : '图'}</span>`
  return L.divIcon({
    className: 'tmv-marker',
    html: `<div class="tmv-pin">${img}${badge}<i class="tmv-seq">${point.sequence || ''}</i></div>`,
    iconSize: [48, 56],
    iconAnchor: [24, 56],
    popupAnchor: [0, -50]
  })
}

function applyBaseLayers(styleKey) {
  if (!map) return
  const conf = STYLE_LAYERS[styleKey] || STYLE_LAYERS.normal
  if (baseLayer) {
    map.removeLayer(baseLayer)
    baseLayer = null
  }
  if (labelLayer) {
    map.removeLayer(labelLayer)
    labelLayer = null
  }
  baseLayer = L.tileLayer(conf.url, conf.options).addTo(map)
  if (conf.labelUrl) {
    labelLayer = L.tileLayer(conf.labelUrl, conf.labelOptions || conf.options).addTo(map)
  }
  // 轨迹图层始终在底图之上
  if (layerGroup) layerGroup.bringToFront()
}

function switchMapStyle(key) {
  if (currentStyle.value === key) return
  currentStyle.value = key
  applyBaseLayers(key)
}

function initMap() {
  if (!mapEl.value || map) return
  map = L.map(mapEl.value, {
    zoomControl: false,
    attributionControl: true,
    maxZoom: 22,
    minZoom: 3
  }).setView([35.0, 105.0], 4)
  // 放到左下，避免与顶部信息条重叠
  L.control.zoom({ position: 'bottomleft' }).addTo(map)
  applyBaseLayers(currentStyle.value)
  layerGroup = L.layerGroup().addTo(map)
}

function clearLayers() {
  if (layerGroup) layerGroup.clearLayers()
}

function renderTrack() {
  if (!map) return
  clearLayers()
  const valid = (props.points || []).filter(p =>
    p && p.latitude != null && p.longitude != null
    && !Number.isNaN(Number(p.latitude)) && !Number.isNaN(Number(p.longitude))
  )
  if (!valid.length) {
    map.setView([35.0, 105.0], 4)
    return
  }

  const color = props.track?.trackColor || '#3B82F6'
  const latlngs = valid.map(p => toMapLatLng(p))

  L.polyline(latlngs, {
    color,
    weight: 4,
    opacity: 0.85,
    lineJoin: 'round'
  }).addTo(layerGroup)

  valid.forEach(point => {
    const marker = L.marker(toMapLatLng(point), {
      icon: createThumbIcon(point),
      riseOnHover: true
    })
    marker.bindPopup(buildPopupHtml(point), {
      maxWidth: 320,
      className: 'tmv-popup-wrap'
    })
    marker.addTo(layerGroup)
  })

  map.fitBounds(L.latLngBounds(latlngs), { padding: [48, 48], maxZoom: 17 })
  nextTick(() => {
    map.invalidateSize()
  })
}

function refresh() {
  nextTick(() => {
    if (!map) initMap()
    renderTrack()
    setTimeout(() => {
      if (map) map.invalidateSize()
    }, 200)
  })
}

watch(() => [props.track, props.points], () => refresh(), { deep: true })

onMounted(() => {
  refresh()
})

onBeforeUnmount(() => {
  if (map) {
    map.remove()
    map = null
    layerGroup = null
    baseLayer = null
    labelLayer = null
  }
})

defineExpose({ refresh })
</script>

<style scoped>
.track-map-viewer {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.map-toolbar {
  position: absolute;
  z-index: 500;
  top: 10px;
  left: 10px;
  right: 54px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  pointer-events: none;
}

.track-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: baseline;
  max-width: min(100%, 720px);
  padding: 8px 12px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.12);
  color: #606266;
  font-size: 13px;
  pointer-events: auto;
}

.track-meta .name {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.map-style-switch {
  display: inline-flex;
  padding: 3px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.12);
  pointer-events: auto;
}

.style-btn {
  border: 0;
  background: transparent;
  color: #606266;
  font-size: 13px;
  line-height: 1;
  padding: 7px 12px;
  border-radius: 6px;
  cursor: pointer;
}

.style-btn:hover {
  color: #303133;
  background: rgba(0, 0, 0, 0.04);
}

.style-btn.active {
  color: #fff;
  background: var(--el-color-primary, #409eff);
}

.map-canvas {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  background: #e8eef5;
  z-index: 0;
}

.map-empty {
  position: absolute;
  left: 50%;
  top: 50%;
  z-index: 400;
  transform: translate(-50%, -50%);
  color: #909399;
  pointer-events: none;
}
</style>

<style>
/* Leaflet 默认图标路径在 Vite 下会失效，改用自定义 divIcon，此处仅样式 */
.tmv-marker {
  background: transparent;
  border: none;
}

.tmv-pin {
  position: relative;
  width: 44px;
  height: 44px;
  border-radius: 8px;
  overflow: hidden;
  border: 2px solid #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.28);
  background: #dcdfe6;
}

.tmv-pin img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.tmv-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  font-size: 12px;
  color: #606266;
}

.tmv-badge {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.35);
  color: #fff;
  font-size: 14px;
}

.tmv-seq {
  position: absolute;
  right: 2px;
  bottom: 2px;
  min-width: 16px;
  height: 16px;
  padding: 0 3px;
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.65);
  color: #fff;
  font-size: 10px;
  line-height: 16px;
  text-align: center;
  font-style: normal;
}

.tmv-popup {
  min-width: 220px;
  max-width: 300px;
}

.tmv-popup .tmv-title {
  font-weight: 600;
  margin-bottom: 6px;
  word-break: break-all;
}

.tmv-popup .tmv-media {
  display: block;
  width: 100%;
  max-height: 200px;
  object-fit: contain;
  background: #111;
  border-radius: 4px;
}

.tmv-popup .tmv-meta {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
  word-break: break-all;
}

.tmv-popup-wrap .leaflet-popup-content-wrapper {
  border-radius: 8px;
}
</style>
