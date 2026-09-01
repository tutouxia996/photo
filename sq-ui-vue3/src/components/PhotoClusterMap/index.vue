<template>
  <div class="photo-cluster-map" :class="{ 'is-editable': editable, 'is-baidu': provider === 'baidu' }">
    <div class="map-toolbar-left">
      <slot name="meta" />
    </div>
    <div
      v-show="!(provider === 'baidu' && baiduPanoOpen)"
      class="map-style-switch"
    >
      <div class="provider-group" :class="{ active: provider === 'amap' }">
        <button type="button" class="style-btn provider-btn" @click="selectProvider('amap')">
          高德地图
        </button>
        <el-dropdown trigger="click" @command="onAmapStyleCommand">
          <button type="button" class="style-btn dropdown-btn" :title="amapStyleLabel">
            {{ amapStyleLabel }}
            <span class="caret">▾</span>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item
                v-for="item in amapStyles"
                :key="item.key"
                :command="item.key"
                :class="{ 'is-active-item': amapStyle === item.key }"
              >
                {{ item.label }}
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
      <span class="style-sep" aria-hidden="true" />
      <div class="provider-group" :class="{ active: provider === 'baidu' }">
        <button type="button" class="style-btn provider-btn" @click="selectProvider('baidu')">
          百度地图
        </button>
        <el-dropdown trigger="click" @command="onBaiduStyleCommand">
          <button type="button" class="style-btn dropdown-btn" :title="baiduStyleLabel">
            {{ baiduStyleLabel }}
            <span class="caret">▾</span>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item
                v-for="item in baiduStyles"
                :key="item.key"
                :command="item.key"
                :class="{ 'is-active-item': baiduStyle === item.key }"
              >
                {{ item.label }}
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>
    <div v-show="provider === 'baidu'" class="baidu-street-overlay">
      <div :id="baiduMapDomId" class="baidu-street-canvas" />
      <div v-if="baiduStatus" class="baidu-street-status">
        <p>{{ baiduStatus }}</p>
        <button type="button" class="baidu-street-link is-muted" @click="selectProvider('amap')">
          返回高德地图
        </button>
      </div>
      <div v-else-if="baiduStyle === 'panorama' && !baiduPanoOpen" class="baidu-street-hint">
        蓝色为全景路网 · 点右下角百度「全景」后移到蓝线上进入街景，用百度自带关闭退出
      </div>
    </div>
    <div ref="mapEl" class="map-canvas" :class="{ 'is-hidden-by-street': provider === 'baidu' }" />
    <div v-if="!hasPoints && provider === 'amap'" class="map-empty">{{ emptyText }}</div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import L from '@/utils/leaflet'
import { applyAmapBaseLayers } from '@/utils/mapAdapters/amapLeaflet'
import { BaiduMapEngine, leafletCenterToBd } from '@/utils/mapAdapters/baiduEngine'
import {
  AMAP_STYLES,
  BAIDU_STYLES,
  MAP_PROVIDER_AMAP,
  MAP_PROVIDER_BAIDU,
  loadMapProviderPrefs,
  saveMapProviderPrefs
} from '@/utils/mapAdapters/types'
import { getBaiduMapAk } from '@/utils/baiduMap'
import {
  cleanRailDisplayPath,
  createClusterGroup,
  createEstimatedClusterGroup,
  createPhotoMarker,
  escapeHtml,
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
  /**
   * 可选：折线专用点序。有值时 markers 仍用 points，折线用本数组（照片地图叠轨迹）
   */
  polylinePoints: { type: Array, default: null },
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
  /** 纠正设备 GPS：普通照片/视频点也可点击、拖动 */
  locationCorrectable: { type: Boolean, default: false },
  /** AI 点选模式：点击估计点切换选中，不打开编辑、不拖动 */
  aiPickMode: { type: Boolean, default: false },
  /** AI 点选已选中的 photoId 列表 */
  selectedPhotoIds: { type: Array, default: () => [] },
  /** 框选模式：拖拽拉矩形选中路段 */
  boxSelectActive: { type: Boolean, default: false },
  /** 框选高亮的路段下标 */
  selectedSegmentIndexes: { type: Array, default: () => [] },
  /** 已访问行政区 GeoJSON FeatureCollection（GCJ-02） */
  regionGeoJson: { type: Object, default: null },
  /** 是否绘制行政区蓝色高亮 */
  showRegionHighlight: { type: Boolean, default: true }
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
  'location-drag-end',
  'location-select',
  'confirm-estimated',
  'box-select',
  'ready'
])

const mapEl = ref(null)
const prefs = loadMapProviderPrefs()
const provider = ref(prefs?.provider || MAP_PROVIDER_AMAP)
const amapStyle = ref(prefs?.amapStyle || 'normal')
const baiduStyle = ref(prefs?.baiduStyle || 'normal')
const amapStyles = AMAP_STYLES
const baiduStyles = BAIDU_STYLES
const baiduStatus = ref('')
const baiduMapDomId = `baidu-map-${Math.random().toString(36).slice(2, 10)}`
const baiduOpening = ref(false)
/** 百度原生街景打开时隐藏地图切换，避免挡住官方关闭按钮 */
const baiduPanoOpen = ref(false)

const amapStyleLabel = computed(() =>
  amapStyles.find(s => s.key === amapStyle.value)?.label || '标准'
)
const baiduStyleLabel = computed(() =>
  baiduStyles.find(s => s.key === baiduStyle.value)?.label || '标准'
)

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
/** 已访问省市区高亮 */
let regionLayer = null
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
/** @type {BaiduMapEngine | null} */
let baiduEngine = null

const selectedSegmentSet = computed(() => {
  const arr = Array.isArray(props.selectedSegmentIndexes) ? props.selectedSegmentIndexes : []
  return new Set(arr.map(n => Number(n)).filter(n => Number.isFinite(n)))
})

const selectedIdSet = computed(() => {
  const arr = Array.isArray(props.selectedPhotoIds) ? props.selectedPhotoIds : []
  return new Set(arr.map(id => String(id)).filter(Boolean))
})

const validPoints = computed(() => filterValidPoints(props.points))
/** 折线点序：优先 polylinePoints，否则与 markers 同源 */
const polylineSource = computed(() => {
  if (Array.isArray(props.polylinePoints) && props.polylinePoints.length) {
    return filterValidPoints(props.polylinePoints)
  }
  return validPoints.value
})
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

/** 容器尚未撑开时（弹窗/过渡），延后校准，避免灰屏无瓦片 */
function ensureMapSized(attempt = 0) {
  if (!map || !mapEl.value) return
  const w = mapEl.value.clientWidth
  const h = mapEl.value.clientHeight
  invalidateMapSize()
  if ((w < 32 || h < 32) && attempt < 8) {
    setTimeout(() => ensureMapSized(attempt + 1), 80 + attempt * 40)
  }
}

function persistPrefs() {
  saveMapProviderPrefs({
    provider: provider.value,
    amapStyle: amapStyle.value,
    baiduStyle: baiduStyle.value
  })
}

function applyBaseLayers(styleKey) {
  if (!map) return
  const layers = applyAmapBaseLayers(map, styleKey, { baseLayer, labelLayer })
  baseLayer = layers.baseLayer
  labelLayer = layers.labelLayer
  if (regionLayer) regionLayer.bringToFront()
  if (lineLayer) lineLayer.bringToFront()
  if (gpxOverlayLayer) gpxOverlayLayer.bringToFront()
  if (clusterGroup) clusterGroup.bringToFront()
  if (waypointLayer) waypointLayer.bringToFront()
  if (estimatedLayer) estimatedLayer.bringToFront()
}

function buildBaiduScene() {
  const matched = visibleMatchedPhotos()
  const matchedIds = new Set(matched.map(p => String(p.photoId)).filter(Boolean))
  const points = []
  const seen = new Set()
  for (const p of validPoints.value) {
    if (p.photoId != null && matchedIds.has(String(p.photoId))) continue
    const key = p.photoId != null ? `p:${p.photoId}` : `c:${p.latitude},${p.longitude}`
    if (seen.has(key)) continue
    seen.add(key)
    points.push(p)
  }
  for (const p of matched) {
    const key = p.photoId != null ? `p:${p.photoId}` : `c:${p.latitude},${p.longitude}`
    if (seen.has(key)) continue
    seen.add(key)
    points.push(p)
  }
  return {
    points,
    polylinePoints: polylineSource.value,
    showPolyline: props.showPolyline,
    polylineColor: props.polylineColor,
    segmentByTravelMode: props.segmentByTravelMode,
    showDirection: props.showDirection,
    previewPath: props.previewPath,
    overlayPaths: overlayList.value,
    visibleTravelModes: props.visibleTravelModes,
    editable: props.editable,
    activeSegmentIndex: props.activeSegmentIndex,
    pathEditIndex: props.pathEditIndex,
    selectedSegmentIndexes: props.selectedSegmentIndexes,
    estimatedDraggable: props.estimatedDraggable,
    locationCorrectable: props.locationCorrectable,
    aiPickMode: props.aiPickMode,
    selectedPhotoIds: props.selectedPhotoIds,
    pointPickable: props.pointPickable,
    gpxEndpointPickable: props.gpxEndpointPickable,
    regionGeoJson: props.regionGeoJson,
    showRegionHighlight: props.showRegionHighlight,
    gpxMatchedIds: matchedIds
  }
}

function baiduCallbacks() {
  return {
    onSegmentClick: (p) => emit('segment-click', p),
    onSegmentPathChange: (p) => emit('segment-path-change', p),
    onWaypointClick: (p) => emit('waypoint-click', p),
    onPointClick: (p) => emit('point-click', p),
    onGpxClick: (p) => emit('gpx-click', p),
    onGpxEndpointClick: (p) => emit('gpx-endpoint-click', p),
    onEstimatedSelect: (p) => {
      emit('estimated-select', p)
      emit('location-select', p)
    },
    onLocationSelect: (p) => emit('location-select', p),
    onEstimatedDragEnd: (p) => emit('estimated-drag-end', p),
    onLocationDragEnd: (p) => emit('location-drag-end', p),
    onBoxSelect: (p) => emit('box-select', p),
    onNativePanoramaChange: (open) => {
      baiduPanoOpen.value = !!open
    }
  }
}

async function ensureBaiduEngine({ fit = false } = {}) {
  if (baiduOpening.value) return
  baiduOpening.value = true
  baiduStatus.value = '正在加载百度地图…'
  try {
    if (!baiduEngine) {
      baiduEngine = new BaiduMapEngine()
    }
    if (!baiduEngine.map) {
      await nextTick()
      const centerBd = leafletCenterToBd(map) || [116.404, 39.915]
      const zoom = map ? Math.min(Math.max(Math.round(map.getZoom() || 12), 4), 18) : 12
      await baiduEngine.init(baiduMapDomId, {
        centerBd,
        zoom,
        styleKey: baiduStyle.value,
        callbacks: baiduCallbacks()
      })
    } else {
      baiduEngine.callbacks = baiduCallbacks()
      baiduEngine.setBasemap(baiduStyle.value)
    }
    baiduEngine.render(buildBaiduScene(), { fit: fit || !baiduEngine.didFit })
    baiduEngine.setBoxSelectActive(!!props.boxSelectActive)
    baiduStatus.value = ''
    setTimeout(() => baiduEngine?.checkResize(), 120)
  } catch (err) {
    const msg = err?.message || '百度地图加载失败'
    baiduStatus.value = msg
    ElMessage.error(msg)
    provider.value = MAP_PROVIDER_AMAP
    persistPrefs()
  } finally {
    baiduOpening.value = false
  }
}

function destroyBaiduEngine() {
  if (baiduEngine) {
    baiduEngine.destroy()
    baiduEngine = null
  }
  baiduStatus.value = ''
  baiduPanoOpen.value = false
}

async function selectProvider(next) {
  if (next === provider.value) return
  if (next === MAP_PROVIDER_BAIDU && !getBaiduMapAk()) {
    ElMessage.warning('未配置百度地图 AK（VITE_BAIDU_MAP_AK）')
    return
  }
  // 切换引擎前清交互，避免脏状态
  if (props.boxSelectActive) syncBoxSelectMode(false)
  provider.value = next
  persistPrefs()
  if (next === MAP_PROVIDER_BAIDU) {
    await ensureBaiduEngine({ fit: true })
  } else {
    destroyBaiduEngine()
    await nextTick()
    invalidateMapSize()
    refresh({ fit: false })
  }
}

function onAmapStyleCommand(key) {
  if (provider.value !== MAP_PROVIDER_AMAP) {
    selectProvider(MAP_PROVIDER_AMAP).then(() => {
      amapStyle.value = key
      persistPrefs()
      applyBaseLayers(key)
    })
    return
  }
  if (amapStyle.value === key) return
  amapStyle.value = key
  persistPrefs()
  applyBaseLayers(key)
}

async function onBaiduStyleCommand(key) {
  if (provider.value !== MAP_PROVIDER_BAIDU) {
    baiduStyle.value = key
    persistPrefs()
    await selectProvider(MAP_PROVIDER_BAIDU)
    return
  }
  if (baiduStyle.value === key) return
  if (key !== 'panorama') {
    baiduEngine?.closePanorama()
    baiduPanoOpen.value = false
  }
  baiduStyle.value = key
  persistPrefs()
  if (baiduEngine?.map) {
    baiduEngine.setBasemap(key)
    baiduEngine.render(buildBaiduScene(), { fit: false })
  } else {
    await ensureBaiduEngine({ fit: false })
  }
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
  // regionLayer 由 drawRegionHighlight 单独刷新，避免每次点位刷新闪烁
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

/** 省/市/区蓝色高亮样式（区最深，省最浅） */
function regionStyle(feature) {
  const level = String(feature?.properties?.level || '').toLowerCase()
  if (level === 'district') {
    return { color: '#1D4ED8', weight: 1.5, opacity: 0.95, fillColor: '#2563EB', fillOpacity: 0.45 }
  }
  if (level === 'city') {
    return { color: '#2563EB', weight: 1.2, opacity: 0.85, fillColor: '#3B82F6', fillOpacity: 0.32 }
  }
  return { color: '#3B82F6', weight: 1, opacity: 0.7, fillColor: '#60A5FA', fillOpacity: 0.22 }
}

function drawRegionHighlight() {
  if (!map) return
  if (regionLayer) {
    regionLayer.clearLayers()
  }
  if (!props.showRegionHighlight) return
  const geo = props.regionGeoJson
  const features = geo && Array.isArray(geo.features) ? geo.features : null
  if (!features || !features.length) return
  if (!regionLayer) {
    if (!map.getPane('regionPane')) {
      map.createPane('regionPane')
      const pane = map.getPane('regionPane')
      pane.style.zIndex = 350
      pane.style.pointerEvents = 'none'
    }
    regionLayer = L.geoJSON(null, {
      pane: 'regionPane',
      style: regionStyle,
      interactive: false,
      // 跳过坏几何，避免整批 addData 抛错
      filter: (feature) => !!(feature && feature.geometry && feature.geometry.coordinates)
    }).addTo(map)
  }
  const order = { province: 0, city: 1, district: 2 }
  const sorted = features
    .filter(f => f && f.geometry && f.geometry.coordinates)
    .sort((a, b) => {
      const la = order[String(a?.properties?.level || '').toLowerCase()] ?? 0
      const lb = order[String(b?.properties?.level || '').toLowerCase()] ?? 0
      return la - lb
    })
  if (!sorted.length) return
  regionLayer.addData({ type: 'FeatureCollection', features: sorted })
  if (lineLayer) lineLayer.bringToFront()
  if (gpxOverlayLayer) gpxOverlayLayer.bringToFront()
  if (clusterGroup) clusterGroup.bringToFront()
  if (waypointLayer) waypointLayer.bringToFront()
  if (estimatedLayer) estimatedLayer.bringToFront()
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
    const payload = {
      index,
      point,
      photoId: point.photoId,
      latitude: wgs.latitude,
      longitude: wgs.longitude,
      aiPickMode: !!props.aiPickMode
    }
    emit('estimated-select', payload)
    emit('location-select', payload)
  })
  const allowDrag = (props.estimatedDraggable || props.locationCorrectable) && !props.aiPickMode
  if (!allowDrag) {
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
    const payload = {
      index,
      point,
      photoId: point.photoId,
      latitude: wgs.latitude,
      longitude: wgs.longitude,
      mapLatLng: [ll.lat, ll.lng]
    }
    emit('estimated-drag-end', payload)
    emit('location-drag-end', payload)
  })
  marker.on('mouseover', () => {
    if (map) map.getContainer().style.cursor = 'grab'
  })
  marker.on('mouseout', () => {
    if (map) map.getContainer().style.cursor = ''
  })
}

function bindCorrectableMarker(marker, point, index) {
  marker.on('click', (e) => {
    L.DomEvent.stopPropagation(e)
    if (props.pointPickable) {
      emit('point-click', { index, point })
      return
    }
    const ll = marker.getLatLng()
    const wgs = fromMapLatLng(ll.lat, ll.lng)
    emit('location-select', {
      index,
      point,
      photoId: point.photoId,
      latitude: wgs.latitude,
      longitude: wgs.longitude
    })
  })
  if (!props.locationCorrectable || props.aiPickMode) {
    if (props.pointPickable) {
      marker.on('mouseover', () => {
        if (map) map.getContainer().style.cursor = 'pointer'
      })
      marker.on('mouseout', () => {
        if (map) map.getContainer().style.cursor = ''
      })
    }
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
    emit('location-drag-end', {
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

  const hasRegions = props.showRegionHighlight
    && props.regionGeoJson
    && Array.isArray(props.regionGeoJson.features)
    && props.regionGeoJson.features.length > 0

  if (!list.length && !preview && !gpxPath.length && !matchedBounds.length) {
    // 无点位时仍尝试画行政区；高亮失败不能影响后续逻辑
    try { drawRegionHighlight() } catch (e) { console.warn('[PhotoClusterMap] region highlight failed', e) }
    if (hasRegions && regionLayer) {
      try {
        const rb = regionLayer.getBounds?.()
        if (fit && rb && rb.isValid()) {
          map.fitBounds(rb, { padding: [48, 48], maxZoom: 8 })
          didFit = true
        }
      } catch (e) { /* ignore */ }
    } else if (!hasRegions) {
      map.setView([35.0, 105.0], 4)
    }
    return
  }

  if (map && props.pathEditIndex < 0) {
    map.doubleClickZoom.enable()
  }

  let fullPath = []
  const lineList = polylineSource.value
  if (props.showPolyline && lineList.length > 1) {
    // 保留原始点序索引，便于编辑出行方式/贴合路网；仅跳过已挂 GPX 的媒体相关路段
    fullPath = drawSegments(lineList, matchedIds)
    // 编辑拐点时隐藏方向装饰，避免遮挡拖拽
    if (props.showDirection && props.pathEditIndex < 0 && fullPath.length >= 2) {
      // 筛选时只画可见段的流动箭头，起终点仅在未筛选时显示
      if (modeFilterSet.value) {
        drawDirectionDecorations(null, lineList)
      } else {
        drawDirectionDecorations(fullPath, lineList)
      }
    }
    if (props.pathEditIndex >= 0) {
      bindPathEditor(lineList, props.pathEditIndex)
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
      const canCorrect = props.locationCorrectable && !isWaypointPoint(p) && p.photoId != null
      // 估计点 / 纠正模式点：单独图层，避免聚合后无法拖动
      if ((estimated || canCorrect) && estimatedLayer && !isWaypointPoint(p)) {
        const selected = selectedIdSet.value.has(String(p.photoId))
        const marker = createPhotoMarker(p, {
          draggable: ((estimated && props.estimatedDraggable) || canCorrect) && !props.aiPickMode,
          pane: 'estimatedPane',
          selected,
          correcting: canCorrect,
          aiPickMode: props.aiPickMode
        })
        if (estimated) {
          bindEstimatedMarker(marker, p, index)
        } else {
          bindCorrectableMarker(marker, p, index)
        }
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

  // 行政区高亮放在点位之后：失败不影响照片/视频标记
  try { drawRegionHighlight() } catch (e) { console.warn('[PhotoClusterMap] region highlight failed', e) }

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
  applyBaseLayers(amapStyle.value)
  // 估计点专用最高图层（高于普通 marker 600，低于 popup 700）
  if (!map.getPane('estimatedPane')) {
    map.createPane('estimatedPane')
    const pane = map.getPane('estimatedPane')
    pane.style.zIndex = 650
    pane.style.pointerEvents = 'auto'
  }
  if (!map.getPane('regionPane')) {
    map.createPane('regionPane')
    const regionPane = map.getPane('regionPane')
    regionPane.style.zIndex = 350
    regionPane.style.pointerEvents = 'none'
  }
  regionLayer = L.geoJSON(null, {
    pane: 'regionPane',
    style: regionStyle,
    interactive: false
  }).addTo(map)
  lineLayer = L.layerGroup().addTo(map)
  gpxOverlayLayer = L.layerGroup().addTo(map)
  clusterGroup = createClusterGroup().addTo(map)
  waypointLayer = L.layerGroup().addTo(map)
  // 估计点用聚合层：缩小合并、点击展开/蜘蛛腿，避免叠点点不开
  estimatedLayer = createEstimatedClusterGroup().addTo(map)
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
    || t.closest('.baidu-street-overlay')
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
    try {
      if (provider.value === MAP_PROVIDER_BAIDU) {
        if (baiduEngine?.map) {
          baiduEngine.callbacks = baiduCallbacks()
          baiduEngine.render(buildBaiduScene(), {
            fit: !!options.fit && !baiduEngine.didFit
          })
          baiduEngine.setBoxSelectActive(!!props.boxSelectActive)
        } else {
          ensureBaiduEngine({ fit: !!options.fit })
        }
        return
      }
      if (!map) initMap()
      renderPoints(options)
    } catch (e) {
      console.error('[PhotoClusterMap] refresh failed', e)
    }
    ensureMapSized(0)
    setTimeout(() => ensureMapSized(0), 200)
  })
}

watch(
  () => [
    props.points,
    props.showPolyline,
    props.polylinePoints,
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
    props.locationCorrectable,
    props.aiPickMode,
    props.selectedPhotoIds,
    props.selectedSegmentIndexes
  ],
  () => refresh({ fit: props.autoFit && !props.editable && !props.boxSelectActive }),
  { deep: true }
)

/** 行政区 GeoJSON 很大，禁止 deep watch（会遍历全部坐标导致卡死、点位不渲染） */
watch(
  () => [props.regionGeoJson, props.showRegionHighlight],
  () => refresh({ fit: false }),
  { deep: false }
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
  if (provider.value === MAP_PROVIDER_BAIDU) {
    baiduEngine?.setBoxSelectActive(!!val)
    return
  }
  syncBoxSelectMode(!!val)
}, { immediate: true })

onMounted(() => {
  refresh({ fit: true })
  setTimeout(() => ensureMapSized(0), 360)
})

onBeforeUnmount(() => {
  destroyBaiduEngine()
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
    regionLayer = null
    svgRenderer = null
    canvasRenderer = null
  }
})

defineExpose({
  refresh,
  invalidateMapSize,
  /** 将地图中心移到 WGS84 坐标（用于估计点预览） */
  focusWgs(lat, lng, zoom = 16) {
    if (lat == null || lng == null) return
    if (provider.value === MAP_PROVIDER_BAIDU) {
      if (baiduEngine?.map) baiduEngine.focusWgs(lat, lng, zoom)
      return
    }
    if (!map) return
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
  right: 360px;
  max-width: min(560px, calc(100% - 370px));
  pointer-events: none;
}

.map-toolbar-left > :deep(*) {
  pointer-events: auto;
}

.map-style-switch {
  position: absolute;
  z-index: 520;
  top: 10px;
  right: 10px;
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 3px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.12);
  pointer-events: auto;
}

.provider-group {
  display: inline-flex;
  align-items: center;
  border-radius: 6px;
  padding: 1px;
}

.provider-group.active {
  background: rgba(64, 158, 255, 0.12);
}

.provider-btn {
  font-weight: 600;
}

.dropdown-btn {
  padding-left: 8px;
  padding-right: 8px;
}

.dropdown-btn .caret {
  margin-left: 2px;
  font-size: 11px;
  opacity: 0.75;
}

.provider-group.active .provider-btn,
.provider-group.active .dropdown-btn {
  color: var(--el-color-primary, #409eff);
}

.style-sep {
  width: 1px;
  align-self: stretch;
  margin: 2px 4px;
  background: rgba(0, 0, 0, 0.12);
}

.style-btn {
  border: 0;
  background: transparent;
  color: #606266;
  font-size: 13px;
  line-height: 1;
  padding: 7px 10px;
  border-radius: 6px;
  cursor: pointer;
}

.style-btn:hover {
  color: #303133;
  background: rgba(0, 0, 0, 0.04);
}

.baidu-street-overlay {
  position: absolute;
  inset: 0;
  z-index: 450;
  background: #e8eef5;
}

.baidu-street-canvas {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.baidu-street-hint {
  position: absolute;
  left: 12px;
  bottom: 12px;
  z-index: 460;
  max-width: min(420px, calc(100% - 120px));
  padding: 8px 12px;
  border-radius: 8px;
  background: rgba(15, 23, 42, 0.78);
  color: #e2e8f0;
  font-size: 12px;
  line-height: 1.4;
  pointer-events: none;
}

.baidu-street-status {
  position: absolute;
  left: 50%;
  top: 50%;
  z-index: 2;
  transform: translate(-50%, -50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  border-radius: 10px;
  background: rgba(15, 23, 42, 0.82);
  color: #e2e8f0;
  text-align: center;
  max-width: min(360px, 86%);
}

.baidu-street-status p {
  margin: 0;
  font-size: 14px;
  line-height: 1.5;
}

.baidu-street-link {
  border: 0;
  border-radius: 6px;
  padding: 7px 14px;
  font-size: 13px;
  color: #0f172a;
  background: #93c5fd;
  cursor: pointer;
}

.baidu-street-link:hover {
  background: #bfdbfe;
}

.baidu-street-link.is-muted {
  background: #e2e8f0;
}

.baidu-street-link.is-muted:hover {
  background: #fff;
}

/* Label 内复用 pmc 缩略图样式 */
.baidu-street-overlay :deep(.pmc-wrap) {
  position: relative;
  width: 52px;
  height: 52px;
  overflow: visible;
}

.baidu-street-overlay :deep(.baidu-pmc-marker) {
  width: 52px;
  height: 52px;
  overflow: visible;
  pointer-events: auto;
}

.baidu-street-overlay :deep(.baidu-pmc-marker .pmc-wrap) {
  position: relative;
  width: 52px;
  height: 52px;
  overflow: visible;
}

.baidu-street-overlay :deep(.baidu-pmc-marker .pmc-badge) {
  position: absolute;
  z-index: 2;
  top: -8px;
  right: -8px;
  min-width: 22px;
  height: 22px;
  padding: 0 6px;
  border-radius: 11px;
  background: #2f6bff;
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  line-height: 22px;
  text-align: center;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.25);
  white-space: nowrap;
  pointer-events: none;
}

/* 百度轨迹流动虚线（对齐 Leaflet pmc-flow-line） */
.baidu-street-overlay :deep(path.pmc-flow-line) {
  pointer-events: none !important;
  animation: pmc-flow-dash 0.9s linear infinite;
}

.baidu-street-overlay :deep(path.pmc-flow-line--accent) {
  animation-duration: 0.9s;
}

@media (prefers-reduced-motion: reduce) {
  .baidu-street-overlay :deep(path.pmc-flow-line) {
    animation: none;
  }
}

.baidu-street-overlay :deep(.baidu-flow-root) {
  pointer-events: none !important;
}

.baidu-street-overlay :deep(.BMapLabel) {
  max-width: none !important;
}

/* 百度 InfoWindow 复用 pmc 弹层样式 */
.baidu-street-overlay :deep(.BMap_bubble_content),
.baidu-street-overlay :deep(.BMap_pop .BMap_top),
.photo-cluster-map.is-baidu :deep(.BMap_bubble_content) {
  font-size: 13px;
  line-height: 1.4;
}

.baidu-street-overlay :deep(.pmc-popup .pmc-media),
.photo-cluster-map.is-baidu :deep(.pmc-popup .pmc-media) {
  display: block;
  width: 100%;
  max-height: 200px;
  object-fit: contain;
  background: #111;
  border-radius: 4px;
}

.baidu-street-status {
  position: absolute;
  left: 50%;
  top: 50%;
  z-index: 2;
  transform: translate(-50%, -50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  border-radius: 10px;
  background: rgba(15, 23, 42, 0.82);
  color: #e2e8f0;
  text-align: center;
  max-width: min(360px, 86%);
}

.baidu-street-status p {
  margin: 0;
  font-size: 14px;
  line-height: 1.5;
}

.baidu-street-link {
  border: 0;
  border-radius: 6px;
  padding: 7px 14px;
  font-size: 13px;
  color: #0f172a;
  background: #93c5fd;
  cursor: pointer;
}

.baidu-street-link:hover {
  background: #bfdbfe;
}

.baidu-street-link.is-muted {
  background: #e2e8f0;
}

.baidu-street-link.is-muted:hover {
  background: #fff;
}

.map-canvas {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  background: #e8eef5;
  z-index: 0;
}

.map-canvas.is-hidden-by-street {
  visibility: hidden;
  pointer-events: none;
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
