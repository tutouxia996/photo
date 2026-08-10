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
  createClusterGroup,
  createPhotoMarker,
  filterValidPoints,
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
  previewPath: { type: Array, default: null }
})

const emit = defineEmits(['segment-click', 'segment-path-change', 'waypoint-click', 'ready'])

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
/** 轨迹线用 SVG，才能做流动虚线 CSS 动画 */
let svgRenderer = null
let canvasRenderer = null
let resizeObserver = null
let didFit = false
/** @type {{ line: any, hit: any, latlngs: any[] }[]} */
let segmentRefs = []
let pathEditMarkers = []

const validPoints = computed(() => filterValidPoints(props.points))
const hasPoints = computed(() => validPoints.value.length > 0)

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
  if (clusterGroup) clusterGroup.bringToFront()
  if (waypointLayer) waypointLayer.bringToFront()
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
  if (clusterGroup) clusterGroup.clearLayers()
  if (waypointLayer) waypointLayer.clearLayers()
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

function drawDirectionDecorations(fullPath, list) {
  if (!props.showDirection || !lineLayer || !fullPath || fullPath.length < 2) return

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

  // 每段叠加流动方向；编辑拐点时不画（外层已判断 pathEditIndex）
  if (list && list.length > 1) {
    const defaultColor = props.polylineColor || '#3B82F6'
    for (let i = 0; i < list.length - 1; i++) {
      const from = list[i]
      const latlngs = segmentLatLngs(from, list[i + 1])
      const color = props.segmentByTravelMode
        ? travelModeColor(from.travelMode, defaultColor)
        : defaultColor
      drawFlowOverlay(latlngs, color)
    }
  } else {
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

function drawSegments(list) {
  if (!lineLayer || list.length < 2) return []
  const defaultColor = props.polylineColor || '#3B82F6'
  const fullPath = []
  segmentRefs = []
  for (let i = 0; i < list.length - 1; i++) {
    const from = list[i]
    const to = list[i + 1]
    const latlngs = segmentLatLngs(from, to)
    if (!fullPath.length) {
      fullPath.push(...latlngs)
    } else {
      fullPath.push(...latlngs.slice(1))
    }
    const color = props.segmentByTravelMode
      ? travelModeColor(from.travelMode, defaultColor)
      : defaultColor
    const active = props.activeSegmentIndex === i || props.pathEditIndex === i
    const dashed = !from.routePath
    const line = L.polyline(latlngs, {
      renderer: svgRenderer || undefined,
      color,
      weight: active ? 7 : 5,
      opacity: active ? 1 : 0.9,
      lineJoin: 'round',
      lineCap: 'round',
      smoothFactor: 1.2,
      dashArray: dashed ? '8 8' : null,
      interactive: !!props.editable,
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

function renderPoints({ fit = props.autoFit } = {}) {
  if (!map || !clusterGroup) return
  clearOverlays()
  const list = validPoints.value
  const preview = Array.isArray(props.previewPath) && props.previewPath.length >= 2
    ? props.previewPath
    : null

  if (!list.length && !preview) {
    map.setView([35.0, 105.0], 4)
    return
  }

  if (map && props.pathEditIndex < 0) {
    map.doubleClickZoom.enable()
  }

  let fullPath = []
  if (props.showPolyline && list.length > 1) {
    fullPath = drawSegments(list)
    // 编辑拐点时隐藏方向装饰，避免遮挡拖拽
    if (props.showDirection && props.pathEditIndex < 0) {
      drawDirectionDecorations(fullPath, list)
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
      const marker = createPhotoMarker(p)
      if (isWaypointPoint(p)) {
        if (props.editable) {
          marker.on('click', (e) => {
            L.DomEvent.stopPropagation(e)
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
        photoMarkers.push(marker)
      }
    })
    if (photoMarkers.length && clusterGroup) clusterGroup.addLayers(photoMarkers)
  }

  const boundsPts = preview
    ? preview
    : (fullPath.length >= 2 ? fullPath : list.map(p => toMapLatLng(p)))
  if ((fit || !didFit) && boundsPts.length) {
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
  lineLayer = L.layerGroup().addTo(map)
  clusterGroup = createClusterGroup().addTo(map)
  waypointLayer = L.layerGroup().addTo(map)
  map.on('zoomstart', onZoomStart)
  map.on('zoomend', onZoomEnd)
  bindResizeObserver()
  emit('ready')
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
    props.previewPath
  ],
  () => refresh({ fit: props.autoFit && !props.editable }),
  { deep: true }
)

watch(() => props.editable, (val) => {
  if (map) map.getContainer().classList.toggle('pmc-edit-cursor', !!val)
})

onMounted(() => refresh({ fit: true }))

onBeforeUnmount(() => {
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
    svgRenderer = null
    canvasRenderer = null
  }
})

defineExpose({ refresh, invalidateMapSize })
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
</style>
