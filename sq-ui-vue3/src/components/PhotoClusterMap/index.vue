<template>
  <div class="photo-cluster-map" :class="{ 'is-editable': editable }">
    <div class="map-toolbar-left">
      <slot name="meta" />
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
    <div ref="mapEl" class="map-canvas" />
    <div v-if="!hasPoints" class="map-empty">{{ emptyText }}</div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import L from '@/utils/leaflet'
import {
  MAP_STYLES,
  STYLE_LAYERS,
  cleanRailDisplayPath,
  createClusterGroup,
  createPhotoMarker,
  filterValidPoints,
  fromMapLatLng,
  isEstimatedLocation,
  isRailTravelMode,
  isWaypointPoint,
  segmentLatLngs,
  toMapLatLng,
  travelModeColor,
  travelModeLabel
} from '@/utils/photoMapCluster'

const props = defineProps({
  points: { type: Array, default: () => [] },
  /** 是否绘制轨迹折线（按点序） */
  showPolyline: { type: Boolean, default: false },
  polylineColor: { type: String, default: '#3B82F6' },
  emptyText: { type: String, default: '暂无带定位的照片' },
  fitMaxZoom: { type: Number, default: 17 },
  /** 是否按点位 travelMode 分段着色 */
  segmentByTravelMode: { type: Boolean, default: false },
  /** 编辑模式：可点击路段 */
  editable: { type: Boolean, default: false },
  /** 当前选中的路段起点序号（validPoints 下标） */
  activeSegmentIndex: { type: Number, default: -1 },
  /** 正在拖拽编辑的路段（显示拐点） */
  pathEditIndex: { type: Number, default: -1 },
  /**
   * 路段出行方式文字：none 不显示 / hover 悬停显示 / change 仅在方式变化处常驻
   */
  segmentLabels: { type: String, default: 'hover' },
  /** 是否在数据变化时自动 fitBounds */
  autoFit: { type: Boolean, default: true },
  /** 显示全程行进方向箭头与起终点 */
  showDirection: { type: Boolean, default: false },
  /** 临时预览折线（GCJ [[lat,lng],...]），不落库 */
  previewPath: { type: Array, default: null },
  /**
   * GPX 等叠层折线：[{ path: [[lat,lng],...], color?, fileName? }]
   */
  overlayPaths: { type: Array, default: () => [] },
  /**
   * 出行方式筛选：空数组/null 显示全部；非空则仅绘制 travelMode 命中的路段
   */
  visibleTravelModes: { type: Array, default: null },
  /** 增补路段时：GPX 起/终点标记可点击选用为锚点 */
  gpxEndpointPickable: { type: Boolean, default: false },
  /** 增补路段时：照片/途经点可点击选用为锚点 */
  pointPickable: { type: Boolean, default: false },
  /** 估计坐标点可拖动微调（单独图层，不参与聚合） */
  estimatedDraggable: { type: Boolean, default: false },
  /** 框选模式：拖拽拉矩形选中路段 */
  boxSelectActive: { type: Boolean, default: false },
  /** 框选高亮的路段下标 */
  selectedSegmentIndexes: { type: Array, default: () => [] }
})

const emit = defineEmits([
  'segment-click',
  'segment-path-change',
  'waypoint-click',
  'point-click',
  'gpx-click',
  'gpx-endpoint-click',
  'estimated-drag-end',
  'estimated-select',
  'confirm-estimated',
  'box-select',
  'ready'
])

const mapEl = ref(null)
const currentStyle = ref('normal')
const mapStyles = MAP_STYLES

let map = null
let baseLayer = null
let labelLayer = null
let lineLayer = null
let clusterGroup = null
/** 无照片途经点单独图层，不参与聚合（避免接合处显示「图」缩略图） */
let waypointLayer = null
/** 可拖动的估计坐标点（不参与聚合） */
let estimatedLayer = null
/** GPX 等叠层折线 */
let gpxOverlayLayer = null
/** 轨迹线用 SVG，才能做流动虚线 CSS 动画 */
let svgRenderer = null
let canvasRenderer = null
let resizeObserver = null
let didFit = false
/** @type {{ line: any, hit: any, latlngs: any[] }[]} */
let segmentRefs = []
let pathEditMarkers = []
/** 框选矩形 */
let boxSelectRect = null
let boxSelectStart = null
let boxSelectDragging = false
let boxSelectHandlersBound = false

const selectedSegmentSet = computed(() => {
  const arr = Array.isArray(props.selectedSegmentIndexes) ? props.selectedSegmentIndexes : []
  return new Set(arr.map(n => Number(n)).filter(n => Number.isFinite(n)))
})

const validPoints = computed(() => filterValidPoints(props.points))
const overlayList = computed(() => {
  const list = Array.isArray(props.overlayPaths) ? props.overlayPaths : []
  return list.filter(o => {
    if (!o) return false
    if (o.showPath === false) {
      return Array.isArray(o.matchedPhotos) && o.matchedPhotos.length > 0
    }
    if (Array.isArray(o.path) && o.path.length >= 2) return true
    if (Number(o.pathPointCount) >= 2) return true
    return Array.isArray(o.matchedPhotos) && o.matchedPhotos.length > 0
  })
})

const hasGpxPathOverlay = computed(() => overlayList.value.some(o => {
  if (!o || o.showPath === false) return false
  if (Array.isArray(o.path) && o.path.length >= 2) return true
  return Number(o.pathPointCount) >= 2
}))

/** GPX 时间匹配到的媒体（挂在 GPX 坐标上） */
const gpxMatchedPhotos = computed(() => {
  const out = []
  const seen = new Set()
  for (const o of overlayList.value) {
    const photos = Array.isArray(o.matchedPhotos) ? o.matchedPhotos : []
    for (const p of photos) {
      const id = p?.photoId
      if (id == null || seen.has(String(id))) continue
      if (p.latitude == null || p.longitude == null) continue
      if (Number.isNaN(Number(p.latitude)) || Number.isNaN(Number(p.longitude))) continue
      seen.add(String(id))
      out.push(p)
    }
  }
  return out
})
const hasPoints = computed(() =>
  validPoints.value.length > 0 || hasGpxPathOverlay.value || gpxMatchedPhotos.value.length > 0
)

const modeFilterSet = computed(() => {
  const list = props.visibleTravelModes
  if (!Array.isArray(list) || !list.length) return null
  return new Set(list.map(m => String(m || '').toLowerCase()).filter(Boolean))
})

function isSegmentModeVisible(travelMode) {
  const set = modeFilterSet.value
  if (!set) return true
  const key = String(travelMode || '').toLowerCase()
  return key ? set.has(key) : false
}

function isGpxItemVisible(item) {
  const set = modeFilterSet.value
  if (!set) return true
  if (set.has('gpx')) return true
  const mode = String(item?.travelMode || '').toLowerCase()
  return mode ? set.has(mode) : false
}

/** 图例只筛线路，不隐藏 GPX 匹配到的照片/视频 */
function shouldShowGpxMatchedPhotos() {
  return gpxMatchedPhotos.value.length > 0
}

function visibleMatchedPhotos() {
  // 始终展示全部 GPX 匹配媒体；出行方式筛选仅作用于折线
  return gpxMatchedPhotos.value
}

function invalidateMapSize() {
  if (!map) return
  map.invalidateSize({ animate: false })
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
  if (lineLayer) lineLayer.bringToFront()
  if (gpxOverlayLayer) gpxOverlayLayer.bringToFront()
  if (clusterGroup) clusterGroup.bringToFront()
  if (waypointLayer) waypointLayer.bringToFront()
  if (estimatedLayer) estimatedLayer.bringToFront()
}

function switchMapStyle(key) {
  if (currentStyle.value === key) return
  currentStyle.value = key
  applyBaseLayers(key)
}

function setMarkerPaneVisible(visible) {
  const pane = map?.getPane?.('markerPane')
  if (pane) pane.style.visibility = visible ? '' : 'hidden'
  const shadow = map?.getPane?.('shadowPane')
  if (shadow) shadow.style.visibility = visible ? '' : 'hidden'
  const estimatedPane = map?.getPane?.('estimatedPane')
  if (estimatedPane) estimatedPane.style.visibility = visible ? '' : 'hidden'
}

function onZoomStart() {
  setMarkerPaneVisible(false)
}

function onZoomEnd() {
  setMarkerPaneVisible(true)
}

function clearPathEditMarkers() {
  pathEditMarkers.forEach(m => {
    try { m.remove() } catch (e) { /* ignore */ }
  })
  pathEditMarkers = []
}

function clearOverlays() {
  clearPathEditMarkers()
  segmentRefs = []
  if (lineLayer) lineLayer.clearLayers()
  if (gpxOverlayLayer) gpxOverlayLayer.clearLayers()
  if (clusterGroup) clusterGroup.clearLayers()
  if (waypointLayer) waypointLayer.clearLayers()
  if (estimatedLayer) estimatedLayer.clearLayers()
}

function distToSegmentSq(p, a, b) {
  const x = p.lat
  const y = p.lng
  const x1 = a.lat
  const y1 = a.lng
  const x2 = b.lat
  const y2 = b.lng
  const dx = x2 - x1
  const dy = y2 - y1
  if (dx === 0 && dy === 0) {
    const ddx = x - x1
    const ddy = y - y1
    return ddx * ddx + ddy * ddy
  }
  let t = ((x - x1) * dx + (y - y1) * dy) / (dx * dx + dy * dy)
  t = Math.max(0, Math.min(1, t))
  const px = x1 + t * dx
  const py = y1 + t * dy
  const ddx = x - px
  const ddy = y - py
  return ddx * ddx + ddy * ddy
}

function emitSegmentPath(index, latlngs) {
  emit('segment-path-change', {
    index,
    path: latlngs.map(ll => [ll.lat, ll.lng])
  })
}

function bindPathEditor(list, index) {
  if (!lineLayer || !map || index < 0 || index >= list.length - 1) return
  const from = list[index]
  const to = list[index + 1]
  const ref = segmentRefs[index]
  if (!ref) return

  map.doubleClickZoom.disable()

  const start = L.latLng(toMapLatLng(from))
  const end = L.latLng(toMapLatLng(to))
  const latlngs = ref.latlngs.map((ll, i, arr) => {
    if (i === 0) return start
    if (i === arr.length - 1) return end
    return L.latLng(ll[0], ll[1])
  })
  if (latlngs.length < 2) {
    latlngs.splice(0, latlngs.length, start, end)
  } else {
    latlngs[0] = start
    latlngs[latlngs.length - 1] = end
  }

  const syncLine = () => {
    const arr = latlngs.map(ll => [ll.lat, ll.lng])
    ref.line.setLatLngs(arr)
    if (ref.hit) ref.hit.setLatLngs(arr)
    ref.latlngs = arr
  }
  syncLine()

  const rebuildMarkers = () => {
    clearPathEditMarkers()
    latlngs.forEach((ll, vi) => {
      const isEnd = vi === 0 || vi === latlngs.length - 1
      const marker = L.marker(ll, {
        draggable: !isEnd,
        keyboard: false,
        zIndexOffset: 900,
        icon: L.divIcon({
          className: 'pmc-vertex-marker',
          html: `<div class="pmc-vertex ${isEnd ? 'is-end' : 'is-mid'}" title="${isEnd ? '照片锚点（固定）' : '拖动调整；双击删除'}"></div>`,
          iconSize: [16, 16],
          iconAnchor: [8, 8]
        })
      }).addTo(lineLayer)
      pathEditMarkers.push(marker)

      if (isEnd) return
      marker.on('drag', (e) => {
        latlngs[vi] = e.target.getLatLng()
        syncLine()
      })
      marker.on('dragend', () => {
        emitSegmentPath(index, latlngs)
      })
      marker.on('dblclick', (e) => {
        L.DomEvent.stop(e)
        if (latlngs.length <= 2) return
        latlngs.splice(vi, 1)
        syncLine()
        emitSegmentPath(index, latlngs)
      })
      marker.on('click', (e) => L.DomEvent.stop(e))
    })
  }
  rebuildMarkers()

  const insertAt = (e) => {
    L.DomEvent.stop(e)
    const ll = e.latlng
    let bestIdx = 1
    let best = Infinity
    for (let i = 0; i < latlngs.length - 1; i++) {
      const d = distToSegmentSq(ll, latlngs[i], latlngs[i + 1])
      if (d < best) {
        best = d
        bestIdx = i + 1
      }
    }
    latlngs.splice(bestIdx, 0, ll)
    syncLine()
    emitSegmentPath(index, latlngs)
  }
  ref.line.off('dblclick')
  ref.hit?.off('dblclick')
  ref.line.on('dblclick', insertAt)
  ref.hit?.on('dblclick', insertAt)
}

/** 沿线流动虚线（dash 为屏幕像素，任意缩放都保持密度） */
function drawFlowOverlay(latlngs, baseColor) {
  if (!lineLayer || !latlngs || latlngs.length < 2) return
  const common = {
    renderer: svgRenderer || undefined,
    interactive: false,
    bubblingMouseEvents: false,
    lineCap: 'round',
    lineJoin: 'round'
  }
  // 浅色流动层：看起来像光点沿路径前进
  L.polyline(latlngs, {
    ...common,
    color: '#ffffff',
    weight: 3,
    opacity: 0.92,
    dashArray: '10 22',
    className: 'pmc-flow-line'
  }).addTo(lineLayer)
  // 与路段同色的细流动层，增强方向感
  L.polyline(latlngs, {
    ...common,
    color: baseColor || '#2563eb',
    weight: 2,
    opacity: 0.85,
    dashArray: '10 22',
    className: 'pmc-flow-line pmc-flow-line--accent'
  }).addTo(lineLayer)
}

function resolveSegmentLatLngs(from, to) {
  const raw = segmentLatLngs(from, to)
  if (!raw || raw.length < 2) return raw
  if (isRailTravelMode(from?.travelMode)) {
    return cleanRailDisplayPath(raw)
  }
  return raw
}

function drawDirectionDecorations(fullPath, list) {
  if (!props.showDirection || !lineLayer) return

  if (fullPath && fullPath.length >= 2) {
    const startIcon = L.divIcon({
      className: 'pmc-end-marker',
      html: '<div class="pmc-end-badge start">起</div>',
      iconSize: [22, 22],
      iconAnchor: [11, 11]
    })
    const endIcon = L.divIcon({
      className: 'pmc-end-marker',
      html: '<div class="pmc-end-badge finish">终</div>',
      iconSize: [22, 22],
      iconAnchor: [11, 11]
    })
    L.marker(fullPath[0], { icon: startIcon, interactive: false, zIndexOffset: 600 }).addTo(lineLayer)
    L.marker(fullPath[fullPath.length - 1], { icon: endIcon, interactive: false, zIndexOffset: 600 }).addTo(lineLayer)
  }

  // 每段叠加流动方向；仅已贴合路网的实线段，避免未贴合虚线被流动层「永远像虚线」
  if (list && list.length > 1) {
    const defaultColor = props.polylineColor || '#3B82F6'
    for (let i = 0; i < list.length - 1; i++) {
      const from = list[i]
      if (!from?.routePath) continue
      if (!isSegmentModeVisible(from.travelMode)) continue
      const latlngs = resolveSegmentLatLngs(from, list[i + 1])
      if (!latlngs || latlngs.length < 2) continue
      const color = props.segmentByTravelMode
        ? travelModeColor(from.travelMode, defaultColor)
        : defaultColor
      drawFlowOverlay(latlngs, color)
    }
  } else if (fullPath && fullPath.length >= 2) {
    drawFlowOverlay(fullPath, props.polylineColor || '#3B82F6')
  }
}

function shouldBindSegmentLabel(i, from, list) {
  const mode = props.segmentLabels || 'hover'
  if (mode === 'none') return false
  const modeText = travelModeLabel(from.travelMode)
  const desc = from.description ? String(from.description).slice(0, 24) : ''
  if (!modeText && !desc) return false
  if (mode === 'hover') return true
  if (mode === 'change') {
    if (i === 0) return true
    const prev = (list[i - 1]?.travelMode || '').toLowerCase()
    const cur = (from.travelMode || '').toLowerCase()
    return prev !== cur
  }
  return false
}

function drawSegments(list, matchedIds = null) {
  if (!lineLayer || list.length < 2) return []
  const matched = matchedIds instanceof Set ? matchedIds : null
  const defaultColor = props.polylineColor || '#3B82F6'
  const fullPath = []
  const filtering = !!modeFilterSet.value
  segmentRefs = []
  for (let i = 0; i < list.length - 1; i++) {
    const from = list[i]
    const to = list[i + 1]
    // 任一端已挂 GPX：不画照片轨路段，避免与 GPX 重复；索引仍保留供其余路段编辑
    const fromMatched = matched && from?.photoId != null && matched.has(String(from.photoId))
    const toMatched = matched && to?.photoId != null && matched.has(String(to.photoId))
    if (fromMatched || toMatched) {
      segmentRefs[i] = null
      continue
    }
    const latlngs = resolveSegmentLatLngs(from, to)
    if (!latlngs || latlngs.length < 2) {
      segmentRefs[i] = null
      continue
    }
    const visible = isSegmentModeVisible(from.travelMode)
    if (visible) {
      if (!fullPath.length) {
        fullPath.push(...latlngs)
      } else {
        fullPath.push(...latlngs.slice(1))
      }
    }
    if (!visible) {
      segmentRefs[i] = null
      continue
    }
    const color = props.segmentByTravelMode
      ? travelModeColor(from.travelMode, defaultColor)
      : defaultColor
    const active = props.activeSegmentIndex === i || props.pathEditIndex === i
      || selectedSegmentSet.value.has(i)
    const dashed = !from.routePath
    const line = L.polyline(latlngs, {
      renderer: svgRenderer || undefined,
      color: selectedSegmentSet.value.has(i) ? '#F59E0B' : color,
      weight: active ? 7 : 5,
      opacity: active ? 1 : 0.9,
      lineJoin: 'round',
      lineCap: 'round',
      smoothFactor: 1.2,
      dashArray: dashed ? '8 8' : null,
      interactive: !!props.editable && !props.boxSelectActive,
      className: dashed ? 'pmc-seg-line is-dashed' : 'pmc-seg-line'
    }).addTo(lineLayer)

    if (shouldBindSegmentLabel(i, from, list)) {
      const modeText = travelModeLabel(from.travelMode)
      const tipParts = []
      if (modeText) tipParts.push(modeText)
      if (from.description) tipParts.push(String(from.description).slice(0, 24))
      const permanent = props.segmentLabels === 'change'
      line.bindTooltip(tipParts.join(' · '), {
        permanent,
        sticky: !permanent,
        direction: 'center',
        className: 'pmc-seg-label',
        opacity: 1
      })
    }

    let hit = null
    if (props.editable) {
      hit = L.polyline(latlngs, {
        renderer: svgRenderer || undefined,
        color: '#000',
        weight: 22,
        opacity: 0.01,
        interactive: true,
        bubblingMouseEvents: false
      }).addTo(lineLayer)
      const onClick = (e) => {
        L.DomEvent.stopPropagation(e)
        emit('segment-click', {
          index: i,
          fromPoint: from,
          toPoint: to
        })
      }
      line.on('click', onClick)
      hit.on('click', onClick)
      hit.on('mouseover', () => {
        if (map) map.getContainer().style.cursor = 'pointer'
      })
      hit.on('mouseout', () => {
        if (map) map.getContainer().style.cursor = ''
      })
    }
    segmentRefs[i] = { line, hit, latlngs }
  }
  // 筛选中不画全程起终点，避免落在被隐藏路段上
  if (filtering && fullPath.length < 2) return []
  return fullPath
}

function drawPreviewPath() {
  const path = props.previewPath
  if (!lineLayer || !Array.isArray(path) || path.length < 2) return
  // 白底描边 + 高对比橙色虚线，在标准地图上更醒目
  L.polyline(path, {
    renderer: svgRenderer || undefined,
    color: '#ffffff',
    weight: 10,
    opacity: 0.95,
    lineJoin: 'round',
    lineCap: 'round',
    interactive: false
  }).addTo(lineLayer)
  L.polyline(path, {
    renderer: svgRenderer || undefined,
    color: '#ea580c',
    weight: 6,
    opacity: 1,
    dashArray: '14 8',
    lineJoin: 'round',
    lineCap: 'round',
    interactive: false
  }).addTo(lineLayer)
}

function normalizeOverlayLatLngs(path) {
  if (!Array.isArray(path) || path.length < 2) return []
  const latlngs = []
  for (const p of path) {
    let lat
    let lng
    if (Array.isArray(p) && p.length >= 2) {
      lat = Number(p[0])
      lng = Number(p[1])
    } else if (p && typeof p === 'object') {
      lat = Number(p.lat != null ? p.lat : p.latitude)
      lng = Number(p.lng != null ? p.lng : p.longitude)
    } else {
      continue
    }
    if (Number.isNaN(lat) || Number.isNaN(lng)) continue
    latlngs.push([lat, lng])
  }
  return latlngs
}

function findNearestGpxPoint(path, latlng) {
  if (!Array.isArray(path) || !latlng) return null
  let best = null
  let bestD = Infinity
  for (const p of path) {
    const lat = Number(p?.lat != null ? p.lat : p?.[0])
    const lng = Number(p?.lng != null ? p.lng : p?.[1])
    if (Number.isNaN(lat) || Number.isNaN(lng)) continue
    const dlat = lat - latlng.lat
    const dlng = lng - latlng.lng
    const d = dlat * dlat + dlng * dlng
    if (d < bestD) {
      bestD = d
      best = p
    }
  }
  return best
}

function buildGpxPopupHtml(item, nearest) {
  const stats = item.stats || {}
  const mode = item.travelMode || ''
  const modeText = travelModeLabel(mode) || mode || '-'
  const rows = []
  rows.push(`<div class="pmc-gpx-title">GPX：${escapeHtml(item.fileName || '')}</div>`)
  rows.push(`<div class="pmc-gpx-tag">线路不可贴合路网 · 可修改出行方式</div>`)
  rows.push(metaRow('出行方式', modeText))
  if (stats.startTime) rows.push(metaRow('开始', stats.startTime))
  if (stats.endTime) rows.push(metaRow('结束', stats.endTime))
  if (stats.durationText) rows.push(metaRow('时长', stats.durationText))
  if (stats.distanceText) rows.push(metaRow('里程', stats.distanceText))
  if (stats.pointCount != null) rows.push(metaRow('点数', String(stats.pointCount)))
  if (stats.minEle != null) rows.push(metaRow('海拔', `${stats.minEle} ~ ${stats.maxEle} m`))
  if (stats.ascent != null) rows.push(metaRow('爬升/下降', `↑${stats.ascent} m / ↓${stats.descent} m`))
  if (nearest) {
    rows.push(`<div class="pmc-gpx-section">点击最近点 #${nearest.seq || '-'}</div>`)
    if (nearest.time) rows.push(metaRow('时间', nearest.time))
    if (nearest.rawTime) rows.push(metaRow('原始UTC', nearest.rawTime))
    if (nearest.ele != null) rows.push(metaRow('海拔', `${nearest.ele} m`))
    if (nearest.sat != null) rows.push(metaRow('卫星', String(nearest.sat)))
    if (nearest.speedKmh != null) rows.push(metaRow('速度', `${nearest.speedKmh} km/h`))
    if (nearest.segDistM != null) rows.push(metaRow('段距', `${nearest.segDistM} m`))
    if (nearest.latWgs != null) rows.push(metaRow('WGS84', `${Number(nearest.latWgs).toFixed(6)}, ${Number(nearest.lngWgs).toFixed(6)}`))
  }
  return `<div class="pmc-popup pmc-gpx-popup">${rows.join('')}</div>`
}

function metaRow(label, value) {
  return `<div class="pmc-meta"><span class="pmc-gpx-k">${escapeHtml(label)}</span>${escapeHtml(String(value))}</div>`
}

function escapeHtml(s) {
  return String(s ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

function drawGpxFlow(latlngs, color) {
  if (!gpxOverlayLayer || !latlngs || latlngs.length < 2) return
  const common = {
    renderer: svgRenderer || undefined,
    interactive: false,
    bubblingMouseEvents: false,
    smoothFactor: 0,
    lineCap: 'round',
    lineJoin: 'round'
  }
  L.polyline(latlngs, {
    ...common,
    color: '#ffffff',
    weight: 3,
    opacity: 0.9,
    dashArray: '10 22',
    className: 'pmc-flow-line'
  }).addTo(gpxOverlayLayer)
  L.polyline(latlngs, {
    ...common,
    color: color || '#059669',
    weight: 2,
    opacity: 0.9,
    dashArray: '10 22',
    className: 'pmc-flow-line pmc-flow-line--accent'
  }).addTo(gpxOverlayLayer)
}

function drawGpxOverlays() {
  if (!gpxOverlayLayer || !hasGpxPathOverlay.value) return []
  const all = []
  overlayList.value.forEach((item) => {
    if (item?.showPath === false) return
    if (!isGpxItemVisible(item)) return
    let latlngs = normalizeOverlayLatLngs(item.path)
    if (latlngs.length < 2) {
      console.warn('[GPX] path points too few', item.fileName, item.pathPointCount, item.path?.length)
      return
    }
    if (isRailTravelMode(item.travelMode)) {
      latlngs = cleanRailDisplayPath(latlngs, { minMeters: 35 })
    }
    all.push(...(all.length ? latlngs.slice(1) : latlngs))
    const color = travelModeColor(item.travelMode, item.color || '#10B981')
    // 底线：可点击查看/改出行方式，不可贴合路网编辑折线
    const line = L.polyline(latlngs, {
      color,
      weight: 5,
      opacity: 0.92,
      smoothFactor: 0,
      noClip: true,
      lineJoin: 'round',
      lineCap: 'round',
      interactive: true,
      className: 'pmc-gpx-overlay'
    }).addTo(gpxOverlayLayer)
    // 加宽命中层，方便点击
    const hit = L.polyline(latlngs, {
      color,
      weight: 16,
      opacity: 0,
      smoothFactor: 0,
      interactive: true,
      className: 'pmc-gpx-hit'
    }).addTo(gpxOverlayLayer)

    const openPopup = (e) => {
      // 增补选点模式下点线路不弹窗，避免挡住选端点
      if (props.gpxEndpointPickable) return
      L.DomEvent.stopPropagation(e)
      const nearest = findNearestGpxPoint(item.path, e.latlng)
      const html = buildGpxPopupHtml(item, nearest)
      L.popup({ maxWidth: 320, className: 'pmc-popup-wrap', autoPan: true })
        .setLatLng(e.latlng)
        .setContent(html)
        .openOn(map)
      emit('gpx-click', { overlay: item, nearest, latlng: e.latlng })
    }
    line.on('click', openPopup)
    hit.on('click', openPopup)
    const modeText = travelModeLabel(item.travelMode) || item.travelMode || 'GPX'
    line.bindTooltip(
      props.gpxEndpointPickable
        ? `GPX：${item.fileName || ''} · 请点「起/终」标记选用为增补锚点`
        : `GPX：${item.fileName || ''}（${modeText}，${item.pathPointCount || latlngs.length}点）· 点击查看/修改`,
      { sticky: true, direction: 'top' }
    )

    drawGpxFlow(latlngs, color)

    const pickable = !!props.gpxEndpointPickable
    const pinClass = pickable ? 'pmc-gpx-pin is-pickable' : 'pmc-gpx-pin'
    const startIcon = L.divIcon({
      className: 'pmc-end-marker',
      html: `<div class="${pinClass} start" title="${pickable ? '点击选用为增补锚点（GPX起点）' : 'GPX起点'}"><span class="pmc-gpx-pin-dot"></span><span class="pmc-gpx-pin-label">起</span></div>`,
      iconSize: [40, 28],
      iconAnchor: [12, 14]
    })
    const endIcon = L.divIcon({
      className: 'pmc-end-marker',
      html: `<div class="${pinClass} end" title="${pickable ? '点击选用为增补锚点（GPX终点）' : 'GPX终点'}"><span class="pmc-gpx-pin-dot"></span><span class="pmc-gpx-pin-label">终</span></div>`,
      iconSize: [40, 28],
      iconAnchor: [12, 14]
    })
    const startMarker = L.marker(latlngs[0], {
      icon: startIcon,
      interactive: pickable,
      zIndexOffset: 650,
      keyboard: false
    }).addTo(gpxOverlayLayer)
    const endMarker = L.marker(latlngs[latlngs.length - 1], {
      icon: endIcon,
      interactive: pickable,
      zIndexOffset: 650,
      keyboard: false
    }).addTo(gpxOverlayLayer)
    if (pickable) {
      const emitEndpoint = (kind, e) => {
        L.DomEvent.stopPropagation(e)
        emit('gpx-endpoint-click', {
          overlay: item,
          kind,
          latlng: e.latlng || (kind === 'start' ? latlngs[0] : latlngs[latlngs.length - 1])
        })
      }
      startMarker.on('click', (e) => emitEndpoint('start', e))
      endMarker.on('click', (e) => emitEndpoint('end', e))
    }
  })
  return all
}

/** 把时间匹配的照片/视频画在 GPX 坐标上 */
function drawGpxMatchedPhotos() {
  const photos = visibleMatchedPhotos()
  if (!clusterGroup || !photos.length) return []
  const markers = []
  const bounds = []
  photos.forEach(p => {
    const point = {
      ...p,
      description: p.description || (p.matchDeltaSec != null
        ? `GPX对齐（Δ${p.matchDeltaSec}s${p.interpolated ? '·插值' : ''}）`
        : 'GPX对齐')
    }
    const marker = createPhotoMarker(point)
    markers.push(marker)
    bounds.push(toMapLatLng(point))
  })
  if (markers.length) {
    clusterGroup.addLayers(markers)
  }
  return bounds
}

function bindEstimatedMarker(marker, point, index) {
  marker.on('click', (e) => {
    L.DomEvent.stopPropagation(e)
    const ll = marker.getLatLng()
    const wgs = fromMapLatLng(ll.lat, ll.lng)
    emit('estimated-select', {
      index,
      point,
      photoId: point.photoId,
      latitude: wgs.latitude,
      longitude: wgs.longitude
    })
  })
  if (!props.estimatedDraggable) {
    marker.on('mouseover', () => {
      if (map) map.getContainer().style.cursor = 'pointer'
    })
    marker.on('mouseout', () => {
      if (map) map.getContainer().style.cursor = ''
    })
    return
  }
  marker.on('dragstart', () => {
    try { marker.closePopup() } catch (e) { /* ignore */ }
    if (map) map.getContainer().style.cursor = 'grabbing'
  })
  marker.on('dragend', (e) => {
    if (map) map.getContainer().style.cursor = ''
    const ll = e.target.getLatLng()
    const wgs = fromMapLatLng(ll.lat, ll.lng)
    emit('estimated-drag-end', {
      index,
      point,
      photoId: point.photoId,
      latitude: wgs.latitude,
      longitude: wgs.longitude,
      mapLatLng: [ll.lat, ll.lng]
    })
  })
  marker.on('mouseover', () => {
    if (map) map.getContainer().style.cursor = 'grab'
  })
  marker.on('mouseout', () => {
    if (map) map.getContainer().style.cursor = ''
  })
}

function renderPoints({ fit = props.autoFit } = {}) {
  if (!map || !clusterGroup) return
  clearOverlays()
  const list = validPoints.value
  const preview = Array.isArray(props.previewPath) && props.previewPath.length >= 2
    ? props.previewPath
    : null
  const gpxPath = drawGpxOverlays()
  const matchedBounds = drawGpxMatchedPhotos()
  const matchedIds = new Set(visibleMatchedPhotos().map(p => String(p.photoId)).filter(Boolean))

  if (!list.length && !preview && !gpxPath.length && !matchedBounds.length) {
    map.setView([35.0, 105.0], 4)
    return
  }

  if (map && props.pathEditIndex < 0) {
    map.doubleClickZoom.enable()
  }

  let fullPath = []
  if (props.showPolyline && list.length > 1) {
    // 保留原始点序索引，便于编辑出行方式/贴合路网；仅跳过已挂 GPX 的媒体相关路段
    fullPath = drawSegments(list, matchedIds)
    // 编辑拐点时隐藏方向装饰，避免遮挡拖拽
    if (props.showDirection && props.pathEditIndex < 0 && fullPath.length >= 2) {
      // 筛选时只画可见段的流动箭头，起终点仅在未筛选时显示
      if (modeFilterSet.value) {
        drawDirectionDecorations(null, list)
      } else {
        drawDirectionDecorations(fullPath, list)
      }
    }
    if (props.pathEditIndex >= 0) {
      bindPathEditor(list, props.pathEditIndex)
    }
  }
  if (preview) {
    drawPreviewPath()
  }

  if (list.length) {
    const photoMarkers = []
    list.forEach((p, index) => {
      // 已挂到 GPX 上的媒体不再在照片轨坐标重复打点
      if (p.photoId != null && matchedIds.has(String(p.photoId))) {
        return
      }
      const estimated = isEstimatedLocation(p)
      // 估计点始终置于最高图层，避免被普通点聚合遮挡
      if (estimated && estimatedLayer && !isWaypointPoint(p)) {
        const marker = createPhotoMarker(p, {
          draggable: props.estimatedDraggable,
          pane: 'estimatedPane'
        })
        bindEstimatedMarker(marker, p, index)
        estimatedLayer.addLayer(marker)
        return
      }
      const marker = createPhotoMarker(p)
      if (isWaypointPoint(p)) {
        if (props.pointPickable || props.editable) {
          marker.on('click', (e) => {
            L.DomEvent.stopPropagation(e)
            if (props.pointPickable) {
              emit('point-click', { index, point: p })
              return
            }
            emit('waypoint-click', { index, point: p })
          })
          marker.on('mouseover', () => {
            if (map) map.getContainer().style.cursor = 'pointer'
          })
          marker.on('mouseout', () => {
            if (map) map.getContainer().style.cursor = ''
          })
        }
        if (waypointLayer) waypointLayer.addLayer(marker)
      } else {
        if (props.pointPickable) {
          marker.on('click', (e) => {
            L.DomEvent.stopPropagation(e)
            emit('point-click', { index, point: p })
          })
        }
        photoMarkers.push(marker)
      }
    })
    if (photoMarkers.length && clusterGroup) clusterGroup.addLayers(photoMarkers)
  }

  let boundsPts = preview
    ? preview
    : (fullPath.length >= 2 ? fullPath : list.map(p => toMapLatLng(p)))
  if (gpxPath.length) {
    boundsPts = boundsPts && boundsPts.length ? boundsPts.concat(gpxPath) : gpxPath
  }
  if (matchedBounds.length) {
    boundsPts = boundsPts && boundsPts.length ? boundsPts.concat(matchedBounds) : matchedBounds
  }
  // 仅当显式要求 fit 时缩放；避免数据微更新（拖动同步）导致视野被拉远
  if (fit && boundsPts.length) {
    map.fitBounds(L.latLngBounds(boundsPts), {
      padding: [48, 48],
      maxZoom: props.fitMaxZoom
    })
    didFit = true
  }
  nextTick(() => invalidateMapSize())
}

function bindResizeObserver() {
  if (resizeObserver || !mapEl.value || typeof ResizeObserver === 'undefined') return
  resizeObserver = new ResizeObserver(() => {
    invalidateMapSize()
  })
  resizeObserver.observe(mapEl.value)
}

function initMap() {
  if (!mapEl.value || map) return
  map = L.map(mapEl.value, {
    zoomControl: false,
    attributionControl: true,
    preferCanvas: true,
    maxZoom: 22,
    minZoom: 3,
    fadeAnimation: false,
    markerZoomAnimation: false
  }).setView([35.0, 105.0], 4)
  L.control.zoom({ position: 'bottomleft' }).addTo(map)
  canvasRenderer = L.canvas({ padding: 0.5 })
  svgRenderer = L.svg({ padding: 0.5 })
  applyBaseLayers(currentStyle.value)
  // 估计点专用最高图层（高于普通 marker 600，低于 popup 700）
  if (!map.getPane('estimatedPane')) {
    map.createPane('estimatedPane')
    const pane = map.getPane('estimatedPane')
    pane.style.zIndex = 650
    pane.style.pointerEvents = 'auto'
  }
  lineLayer = L.layerGroup().addTo(map)
  gpxOverlayLayer = L.layerGroup().addTo(map)
  clusterGroup = createClusterGroup().addTo(map)
  waypointLayer = L.layerGroup().addTo(map)
  estimatedLayer = L.layerGroup().addTo(map)
  map.on('zoomstart', onZoomStart)
  map.on('zoomend', onZoomEnd)
  map.getContainer().classList.toggle('pmc-edit-cursor', !!props.editable)
  map.getContainer().classList.toggle('pmc-gpx-pick', !!props.gpxEndpointPickable)
  map.getContainer().classList.toggle('pmc-box-select', !!props.boxSelectActive)
  bindResizeObserver()
  syncBoxSelectMode(!!props.boxSelectActive)
  emit('ready')
}

function clearBoxSelectRect() {
  if (boxSelectRect && map) {
    map.removeLayer(boxSelectRect)
  }
  boxSelectRect = null
  boxSelectStart = null
  boxSelectDragging = false
}

function onBoxSelectMouseDown(e) {
  if (!props.boxSelectActive || !map || !e.latlng) return
  // 仅左键
  if (e.originalEvent && e.originalEvent.button !== 0) return
  // 点在地图控件/侧栏上时不开始框选
  const t = e.originalEvent && e.originalEvent.target
  if (t && t.closest && (
    t.closest('.seg-panel')
    || t.closest('.map-style-switch')
    || t.closest('.leaflet-control')
    || t.closest('.track-meta')
  )) {
    return
  }
  L.DomEvent.stopPropagation(e)
  clearBoxSelectRect()
  boxSelectStart = e.latlng
  boxSelectDragging = true
  boxSelectRect = L.rectangle(L.latLngBounds(e.latlng, e.latlng), {
    color: '#F59E0B',
    weight: 2,
    dashArray: '4 4',
    fillOpacity: 0.12,
    interactive: false
  }).addTo(map)
}

function onBoxSelectMouseMove(e) {
  if (!boxSelectDragging || !boxSelectStart || !boxSelectRect || !e.latlng) return
  boxSelectRect.setBounds(L.latLngBounds(boxSelectStart, e.latlng))
}

function onBoxSelectMouseUp(e) {
  if (!boxSelectDragging || !boxSelectStart) return
  boxSelectDragging = false
  const native = e && (e.originalEvent || e)
  // 若在侧栏松开，取消本次框选，避免抢走面板点击
  if (native && native.target && native.target.closest
      && native.target.closest('.seg-panel')) {
    clearBoxSelectRect()
    return
  }
  let end = e && e.latlng ? e.latlng : null
  if (!end && native && map) {
    try {
      end = map.mouseEventToLatLng(native)
    } catch (err) {
      end = boxSelectStart
    }
  }
  if (!end) end = boxSelectStart
  const bounds = L.latLngBounds(boxSelectStart, end)
  clearBoxSelectRect()
  // 太小的框忽略（误点击）
  const sw = bounds.getSouthWest()
  const ne = bounds.getNorthEast()
  if (Math.abs(ne.lat - sw.lat) < 1e-5 && Math.abs(ne.lng - sw.lng) < 1e-5) {
    return
  }
  emit('box-select', {
    south: bounds.getSouth(),
    north: bounds.getNorth(),
    west: bounds.getWest(),
    east: bounds.getEast()
  })
}

function syncBoxSelectMode(active) {
  if (!map) return
  const container = map.getContainer()
  container.classList.toggle('pmc-box-select', !!active)
  if (active) {
    map.dragging.disable()
    map.doubleClickZoom.disable()
    if (!boxSelectHandlersBound) {
      map.on('mousedown', onBoxSelectMouseDown)
      map.on('mousemove', onBoxSelectMouseMove)
      map.on('mouseup', onBoxSelectMouseUp)
      // 鼠标拖出地图外松手
      L.DomEvent.on(document, 'mouseup', onBoxSelectMouseUp)
      boxSelectHandlersBound = true
    }
  } else {
    map.dragging.enable()
    map.doubleClickZoom.enable()
    if (boxSelectHandlersBound) {
      map.off('mousedown', onBoxSelectMouseDown)
      map.off('mousemove', onBoxSelectMouseMove)
      map.off('mouseup', onBoxSelectMouseUp)
      L.DomEvent.off(document, 'mouseup', onBoxSelectMouseUp)
      boxSelectHandlersBound = false
    }
    clearBoxSelectRect()
  }
}

function refresh(options = {}) {
  nextTick(() => {
    if (!map) initMap()
    renderPoints(options)
    invalidateMapSize()
    setTimeout(() => invalidateMapSize(), 200)
  })
}

watch(
  () => [
    props.points,
    props.showPolyline,
    props.polylineColor,
    props.segmentByTravelMode,
    props.editable,
    props.activeSegmentIndex,
    props.pathEditIndex,
    props.segmentLabels,
    props.showDirection,
    props.previewPath,
    props.overlayPaths,
    props.gpxEndpointPickable,
    props.pointPickable,
    props.estimatedDraggable,
    props.selectedSegmentIndexes
  ],
  () => refresh({ fit: props.autoFit && !props.editable && !props.boxSelectActive }),
  { deep: true }
)

watch(
  () => props.visibleTravelModes,
  () => refresh({ fit: false }),
  { deep: true }
)

watch(() => props.editable, (val) => {
  if (map) map.getContainer().classList.toggle('pmc-edit-cursor', !!val)
})

watch(() => props.gpxEndpointPickable, (val) => {
  if (map) map.getContainer().classList.toggle('pmc-gpx-pick', !!val)
}, { immediate: true })

watch(() => props.boxSelectActive, (val) => {
  syncBoxSelectMode(!!val)
}, { immediate: true })

onMounted(() => refresh({ fit: true }))

onBeforeUnmount(() => {
  syncBoxSelectMode(false)
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
  if (map) {
    map.off('zoomstart', onZoomStart)
    map.off('zoomend', onZoomEnd)
    map.remove()
    map = null
    baseLayer = null
    labelLayer = null
    lineLayer = null
    clusterGroup = null
    waypointLayer = null
    estimatedLayer = null
    gpxOverlayLayer = null
    svgRenderer = null
    canvasRenderer = null
  }
})

defineExpose({
  refresh,
  invalidateMapSize,
  /** 将地图中心移到 WGS84 坐标（用于估计点预览） */
  focusWgs(lat, lng, zoom = 16) {
    if (!map || lat == null || lng == null) return
    const ll = toMapLatLng({ latitude: lat, longitude: lng })
    const z = Math.max(Number(zoom) || 16, map.getZoom() || 3)
    map.setView(ll, Math.min(z, 18), { animate: true })
  }
})
</script>

<style scoped>
.photo-cluster-map {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.map-toolbar-left {
  position: absolute;
  z-index: 500;
  top: 10px;
  left: 10px;
  right: 200px;
  max-width: min(560px, calc(100% - 210px));
  pointer-events: none;
}

.map-toolbar-left > :deep(*) {
  pointer-events: auto;
}

.map-style-switch {
  position: absolute;
  z-index: 500;
  top: 10px;
  right: 10px;
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

:deep(.pmc-box-select) {
  cursor: crosshair !important;
}
</style>
