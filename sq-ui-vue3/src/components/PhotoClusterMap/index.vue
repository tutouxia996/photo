<template>
  <div class="photo-cluster-map" :class="{ 'is-editable': editable, 'is-terrain': terrainMode }">
    <div class="map-toolbar-left">
      <slot name="meta" />
    </div>
    <div class="map-toolbar-right">
      <slot name="toolbar-right" />
      <div class="map-style-switch map-view-switch" @click.stop>
        <div class="view-dropdown" :class="{ open: viewMenu === '2d' }">
          <button
            type="button"
            class="style-btn style-btn--view"
            :class="{ active: !terrainMode }"
            :disabled="terrainLoading"
            title="二维地图"
            @click="toggleViewMenu('2d')"
          >
            二维<span class="view-caret">▾</span>
          </button>
          <div v-if="viewMenu === '2d'" class="view-menu">
            <button
              v-for="item in mapStyles"
              :key="'2d-' + item.key"
              type="button"
              class="view-menu-item"
              :class="{ active: !terrainMode && currentStyle === item.key }"
              @click="selectViewMode(false, item.key)"
            >{{ item.label }}</button>
          </div>
        </div>
        <div class="view-dropdown" :class="{ open: viewMenu === '3d' }">
          <button
            type="button"
            class="style-btn style-btn--view style-btn--terrain"
            :class="{ active: terrainMode }"
            :disabled="terrainLoading"
            title="3D 地形：左键平移；中键/Shift+左键绕点俯仰旋转；滚轮绕光标缩放"
            @click="toggleViewMenu('3d')"
          >
            {{ terrainLoading ? '…' : '3D' }}<span class="view-caret">▾</span>
          </button>
          <div v-if="viewMenu === '3d'" class="view-menu">
            <button
              v-for="item in mapStyles"
              :key="'3d-' + item.key"
              type="button"
              class="view-menu-item"
              :class="{ active: terrainMode && currentStyle === item.key }"
              @click="selectViewMode(true, item.key)"
            >{{ item.label }}</button>
          </div>
        </div>
      </div>
    </div>
    <div ref="mapEl" class="map-canvas" :class="{ 'is-hidden': terrainMode }" />
    <div ref="amapEl" class="map-canvas map-canvas--amap" :class="{ 'is-hidden': !terrainMode }" />
    <div v-if="!hasPoints" class="map-empty">{{ emptyText }}</div>
    <div class="pmc-br-stack">
      <button
        type="button"
        class="pmc-compass"
        title="重置视角与方向：正北俯视（上北下南左西右东）"
        @click.stop="resetMapOrientation"
      >
        <span
          class="pmc-compass__dial"
          :style="{ transform: `rotate(${-mapHeading}deg)` }"
        >
          <svg viewBox="0 0 40 40" aria-hidden="true">
            <circle cx="20" cy="20" r="18" class="pmc-compass__ring" />
            <path d="M20 6 L24 20 L20 17 L16 20 Z" class="pmc-compass__north" />
            <path d="M20 34 L16 20 L20 23 L24 20 Z" class="pmc-compass__south" />
            <text x="20" y="12.5" text-anchor="middle" class="pmc-compass__n">N</text>
          </svg>
        </span>
      </button>
      <div v-if="$slots.modeLegend || modeLegendItems.length" class="pmc-mode-legend">
        <slot name="modeLegend">
          <div
            v-for="m in modeLegendItems"
            :key="m.key"
            class="pmc-mode-legend-item"
          >
            <span class="pmc-mode-legend-icon" :style="{ background: m.color }" v-html="travelModeIconSvg(m.key, 12)" />
            {{ m.label }}
          </div>
        </slot>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import L from '@/utils/leafletRotate'
import { loadAmap } from '@/utils/amapLoader'
import {
  MAP_STYLES,
  STYLE_LAYERS,
  buildPopupHtml,
  cleanRailDisplayPath,
  clusterRadiusForZoom,
  createClusterGroup,
  createEstimatedClusterGroup,
  createPhotoMarker,
  estimatedSourceLabel,
  estimatedSourceTitle,
  filterValidPoints,
  formatCount,
  fromMapLatLng,
  isEstimatedLocation,
  isRailTravelMode,
  isWaypointPoint,
  mediaSrc,
  segmentLatLngs,
  toMapLatLng,
  TRAVEL_MODES,
  travelModeColor,
  travelModeIconSvg,
  travelModeLabel,
  travelModeMotion,
  wgs84ToGcj02
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
  showRegionHighlight: { type: Boolean, default: true },
  /**
   * 是否显示照片/视频标记；关闭后仅保留轨迹线、GPX 与途经点
   */
  showMediaMarkers: { type: Boolean, default: true },
  /** 右下角出行方式图例（父级已有图例时可关） */
  showModeLegend: { type: Boolean, default: true },
  /** 按出行方式沿线播放人物/载具动画 */
  showTravelers: { type: Boolean, default: true }
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
  'ready',
  'terrain-mode-change'
])

const mapEl = ref(null)
const amapEl = ref(null)
const currentStyle = ref('normal')
const mapStyles = MAP_STYLES
const terrainMode = ref(false)
/** 地图顺时针旋转角（度）；罗盘表盘反向旋转以保持 N 指向正北 */
const mapHeading = ref(0)
const terrainLoading = ref(false)
/** 二维 / 3D 下拉菜单：null | '2d' | '3d' */
const viewMenu = ref(null)

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
/** @type {{ marker: any, metrics: any, timed: any[], duration: number, mode: string } | null} */
let travelerActor = null
let travelerRaf = 0
let travelerStartedAt = 0

/** 高德 3D 地形只读实例 */
let amap = null
/** @type {any[]} */
let amapOverlays = []
/** 3D 共用信息窗（与 Leaflet 一样：点地图可关、同时只开一个） */
let amapInfoWindow = null
/** 当前打开弹窗对应的 photoId / 坐标，便于再次点击同一张关闭 */
let amapInfoWindowKey = null
/** 可复用的底图图层（勿用 setLayers，会丢掉覆盖物） */
let amapLayerDefault = null
let amapLayerSatellite = null
let amapLayerRoadNet = null
/** 当前挂在地图上的底图图层 */
let amapActiveBaseLayers = []
/** 进入 3D 前 Leaflet 视野 */
let leafletViewBeforeTerrain = null
/** 避免异步进入 3D 时组件已卸载 / 用户已取消 */
let terrainEnterToken = 0
/** 3D 下点位过多时抽稀上限（聚合前） */
const TERRAIN_MARKER_CAP = 2000
/** 与 Leaflet disableClusteringAtZoom 对齐 */
const TERRAIN_CLUSTER_MAX_ZOOM = 16
/** 3D 行进动画 */
let terrainTraveler = null
let terrainTravelerRaf = 0
let terrainTravelerStartedAt = 0
let onViewMenuDocClick = null
let amapStyleRedrawTimer = 0
/** 当前参与聚合的媒体点（缩放时重算） */
let terrainClusterSource = []
/** 仅聚合标记（不含轨迹线），缩放时替换 */
let amapClusterOverlays = []
let terrainClusterZoomBound = false
let terrainSpiderfyActive = false
/** 中键调整俯仰/旋转 */
let terrainViewControlsBound = false
let terrainViewDrag = null
/** @type {{ onDown: Function, onMove: Function, onUp: Function, onAux: Function, onContext: Function } | null} */
let terrainViewHandlers = null
/** 自定义滚轮缩放（比高德默认更灵敏） */
let terrainWheelBound = false
let terrainWheelHandler = null
/** 二维 Leaflet：与 3D 对齐的视角/滚轮手势 */
let leafletViewControlsBound = false
let leafletViewDrag = null
/** @type {Record<string, Function> | null} */
let leafletViewHandlers = null
let leafletWheelBound = false
let leafletWheelHandler = null

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

function collectUsedTravelModes() {
  const keys = new Set()
  if (props.showPolyline) {
    const list = polylineSource.value
    for (let i = 0; i < list.length - 1; i++) {
      const mode = String(list[i]?.travelMode || '').toLowerCase()
      if (mode) keys.add(mode)
    }
  }
  overlayList.value.forEach((o) => {
    if (!o || o.showPath === false) return
    const hasPath = (Array.isArray(o.path) && o.path.length >= 2) || Number(o.pathPointCount) >= 2
    if (!hasPath) return
    const segs = Array.isArray(o.segments) ? o.segments : []
    if (segs.length) {
      segs.forEach((s) => {
        const mode = String(s?.travelMode || '').toLowerCase()
        if (mode) keys.add(mode)
      })
    } else if (o.travelMode) {
      keys.add(String(o.travelMode).toLowerCase())
    }
  })
  return TRAVEL_MODES.filter(m => keys.has(m.key))
}

const modeLegendItems = computed(() => {
  if (!props.showModeLegend) return []
  if (!props.showPolyline && !hasGpxPathOverlay.value) return []
  return collectUsedTravelModes()
})

function isGpxItemVisible(item) {
  const set = modeFilterSet.value
  if (!set) return true
  if (set.has('gpx')) return true
  const segs = Array.isArray(item?.segments) ? item.segments : []
  if (segs.length) {
    return segs.some(s => {
      const mode = String(s?.travelMode || '').toLowerCase()
      return mode && set.has(mode)
    })
  }
  const mode = String(item?.travelMode || '').toLowerCase()
  return mode ? set.has(mode) : false
}

/** GPX 分段列表；无 segments 时回退为整条单色 */
function gpxDrawSegments(item) {
  const segs = Array.isArray(item?.segments) ? item.segments.filter(s => s && (s.path?.length >= 2 || (s.toIndex != null && s.fromIndex != null))) : []
  if (segs.length) {
    return segs.map(s => {
      const path = Array.isArray(s.path) && s.path.length >= 2
        ? s.path
        : (Array.isArray(item.path) ? item.path.slice(s.fromIndex, s.toIndex + 1) : [])
      return {
        travelMode: s.travelMode || item.travelMode,
        color: s.color,
        path
      }
    }).filter(s => Array.isArray(s.path) && s.path.length >= 2)
  }
  return [{
    travelMode: item?.travelMode,
    color: item?.color,
    path: item?.path
  }]
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
  if (regionLayer) regionLayer.bringToFront()
  if (lineLayer) lineLayer.bringToFront()
  if (gpxOverlayLayer) gpxOverlayLayer.bringToFront()
  if (clusterGroup) clusterGroup.bringToFront()
  if (waypointLayer) waypointLayer.bringToFront()
  if (estimatedLayer) estimatedLayer.bringToFront()
}

function switchMapStyle(key) {
  if (currentStyle.value === key) return
  currentStyle.value = key
  if (terrainMode.value && amap) {
    applyAmapLayers(key)
  } else {
    applyBaseLayers(key)
  }
}

function toggleViewMenu(which) {
  if (terrainLoading.value) return
  viewMenu.value = viewMenu.value === which ? null : which
}

function closeViewMenu() {
  viewMenu.value = null
}

/** 选择二维/3D + 底图样式 */
async function selectViewMode(wantTerrain, styleKey) {
  closeViewMenu()
  const styleChanged = !!(styleKey && styleKey !== currentStyle.value)
  if (styleChanged) {
    currentStyle.value = styleKey
  }
  if (wantTerrain && !terrainMode.value) {
    // 进入 3D 时会按 currentStyle 初始化图层并绘制轨迹
    await enterTerrainMode()
    return
  }
  if (!wantTerrain && terrainMode.value) {
    exitTerrainMode()
    if (styleChanged && map) applyBaseLayers(styleKey)
    return
  }
  // 已在目标模式：仅换底图
  if (wantTerrain && terrainMode.value && amap && styleChanged) {
    applyAmapLayers(styleKey)
  } else if (!wantTerrain && !terrainMode.value && styleChanged && map) {
    applyBaseLayers(styleKey)
  }
}

function bindViewMenuOutsideClose() {
  if (onViewMenuDocClick) return
  onViewMenuDocClick = () => closeViewMenu()
  document.addEventListener('click', onViewMenuDocClick)
}

function unbindViewMenuOutsideClose() {
  if (!onViewMenuDocClick) return
  document.removeEventListener('click', onViewMenuDocClick)
  onViewMenuDocClick = null
}

function toAmapLngLat(point) {
  const [lng, lat] = wgs84ToGcj02(Number(point.longitude), Number(point.latitude))
  return [lng, lat]
}

function leafletLatLngToAmap(ll) {
  if (!ll) return null
  if (Array.isArray(ll)) {
    // Leaflet [lat, lng] → AMap [lng, lat]
    return [Number(ll[1]), Number(ll[0])]
  }
  if (ll.lat != null && ll.lng != null) return [Number(ll.lng), Number(ll.lat)]
  return null
}

function ensureAmapBaseLayers(AMap) {
  if (!amapLayerDefault) {
    amapLayerDefault = AMap.createDefaultLayer
      ? AMap.createDefaultLayer()
      : new AMap.TileLayer()
  }
  if (!amapLayerSatellite) {
    amapLayerSatellite = new AMap.TileLayer.Satellite()
  }
  if (!amapLayerRoadNet) {
    amapLayerRoadNet = new AMap.TileLayer.RoadNet()
  }
}

function layersForStyle(styleKey) {
  if (styleKey === 'satellite') return [amapLayerSatellite]
  if (styleKey === 'hybrid') return [amapLayerSatellite, amapLayerRoadNet]
  return [amapLayerDefault]
}

/**
 * 切换底图：只用 add/remove，禁止 setLayers（地形模式下会清掉 Polyline/Marker）。
 * 切换后强制重绘轨迹覆盖物。
 */
function applyAmapLayers(styleKey) {
  if (!amap || !window.AMap) return
  try {
    ensureAmapBaseLayers(window.AMap)
    const next = layersForStyle(styleKey).filter(Boolean)
    const prev = amapActiveBaseLayers.slice()
    // 先卸旧底图
    if (prev.length) {
      try { amap.remove(prev) } catch (e) { /* ignore */ }
    }
    // 再挂新底图
    if (next.length) {
      try { amap.add(next) } catch (e) { /* ignore */ }
    }
    amapActiveBaseLayers = next
    scheduleTerrainOverlayRedraw()
  } catch (e) {
    console.warn('[PhotoClusterMap] applyAmapLayers failed', e)
  }
}

function scheduleTerrainOverlayRedraw() {
  if (amapStyleRedrawTimer) {
    clearTimeout(amapStyleRedrawTimer)
    amapStyleRedrawTimer = 0
  }
  // 等底图切换完成后再画，避免同一帧加入被盖住/丢弃
  amapStyleRedrawTimer = setTimeout(() => {
    amapStyleRedrawTimer = 0
    if (terrainMode.value && amap) renderTerrainOverlays()
  }, 50)
}

function clearAmapClusterOverlays() {
  const drop = amapClusterOverlays.slice()
  if (drop.length && amap) {
    try { amap.remove(drop) } catch (e) { /* ignore */ }
    drop.forEach((o) => {
      try { o?.setMap?.(null) } catch (e) { /* ignore */ }
    })
  }
  if (drop.length && amapOverlays.length) {
    const set = new Set(drop)
    amapOverlays = amapOverlays.filter(o => !set.has(o))
  }
  amapClusterOverlays = []
}

function unbindTerrainClusterZoom() {
  if (!amap || !terrainClusterZoomBound) {
    terrainClusterZoomBound = false
    return
  }
  try {
    amap.off('zoomend', onTerrainClusterZoomEnd)
  } catch (e) { /* ignore */ }
  terrainClusterZoomBound = false
}

function clearAmapOverlays() {
  unbindTerrainClusterZoom()
  clearAmapClusterOverlays()
  terrainClusterSource = []
  terrainSpiderfyActive = false
  if (!amap || !amapOverlays.length) {
    amapOverlays = []
    return
  }
  try {
    amap.remove(amapOverlays)
  } catch (e) {
    /* ignore */
  }
  amapOverlays.forEach((o) => {
    try {
      if (o && typeof o.setMap === 'function') o.setMap(null)
    } catch (e) { /* ignore */ }
  })
  amapOverlays = []
}

/**
 * 3D 操作 — 一比一对照谷歌地球（Google Earth User Guide）：
 * - 左键拖动：平移
 * - 中键拖动：上下倾斜 + 左右旋转（绕按下点，出现十字准星）
 * - Shift+左键拖动：同上（无中键时的官方备用）
 * 枢轴钉在按下时鼠标下的地面点，手势中该点不离开准星位置。
 */
function bindTerrainViewControls() {
  const el = amapEl.value
  if (!el || !amap || terrainViewControlsBound) return

  // 谷歌地球手感：约 0.45°/px；俯仰略钝一点更稳
  const PITCH_SENS = 0.40
  const ROTATE_SENS = 0.45
  let rafId = 0
  let pendingPitch = null
  let pendingRotation = null
  let savedAnimateEnable = true
  let crosshairEl = null

  const host = el.parentElement || el
  if (host && getComputedStyle(host).position === 'static') {
    host.style.position = 'relative'
  }

  const isOrbitGesture = (e) => {
    // 谷歌地球：中键；或 Shift+左键（官方无中键备用）
    // 注意：Ctrl+左键在 GE 里是「原地环视」，不是绕点旋转，这里不占用
    return e.button === 1 || (e.button === 0 && e.shiftKey && !e.ctrlKey && !e.metaKey && !e.altKey)
  }

  const clientToContainer = (clientX, clientY) => {
    const rect = el.getBoundingClientRect()
    return [clientX - rect.left, clientY - rect.top]
  }

  const pickPivotLngLat = (containerX, containerY) => {
    try {
      const ll = amap.containerToLngLat([containerX, containerY])
      if (ll && Number.isFinite(ll.lng) && Number.isFinite(ll.lat)) {
        return [ll.lng, ll.lat]
      }
    } catch (err) { /* ignore */ }
    return null
  }

  const showCrosshair = (x, y) => {
    if (!crosshairEl) {
      crosshairEl = document.createElement('div')
      crosshairEl.className = 'ge-orbit-crosshair'
      crosshairEl.innerHTML = '<i class="ge-orbit-crosshair__h"></i><i class="ge-orbit-crosshair__v"></i><i class="ge-orbit-crosshair__dot"></i>'
      host.appendChild(crosshairEl)
    }
    crosshairEl.style.left = `${x}px`
    crosshairEl.style.top = `${y}px`
    crosshairEl.classList.add('is-on')
  }

  const hideCrosshair = () => {
    if (crosshairEl) crosshairEl.classList.remove('is-on')
  }

  /**
   * 高德俯仰/旋转绕地图中心；用 setCenter 迭代把枢轴点锁回准星（3D 透视下比单次 panBy 稳）。
   */
  const lockPivotToCrosshair = () => {
    if (!amap || !terrainViewDrag?.pivotLngLat) return
    const { pivotLngLat, anchorX, anchorY } = terrainViewDrag
    try {
      for (let i = 0; i < 4; i++) {
        const px = amap.lngLatToContainer(pivotLngLat)
        if (!px || !Number.isFinite(px.x) || !Number.isFinite(px.y)) return
        const dx = px.x - anchorX
        const dy = px.y - anchorY
        if (Math.abs(dx) < 0.5 && Math.abs(dy) < 0.5) return
        const size = amap.getSize?.()
        if (!size) {
          amap.panBy(-dx, -dy, 0)
          continue
        }
        const cx = size.width / 2
        const cy = size.height / 2
        // 枢轴偏右 dx → 新中心取「当前中心右侧 dx」处的经纬度，使画面整体左移
        const ll = amap.containerToLngLat([cx + dx, cy + dy])
        if (!ll || !Number.isFinite(ll.lng)) {
          amap.panBy(-dx, -dy, 0)
          continue
        }
        amap.setCenter([ll.lng, ll.lat], true)
      }
    } catch (err) { /* ignore */ }
  }

  const applyView = () => {
    rafId = 0
    if (!amap || pendingPitch == null || !terrainViewDrag) return
    try {
      amap.setPitch(pendingPitch, true)
      amap.setRotation(pendingRotation, true)
      lockPivotToCrosshair()
      mapHeading.value = ((pendingRotation % 360) + 360) % 360
    } catch (err) { /* ignore */ }
    pendingPitch = null
    pendingRotation = null
  }

  const endViewDrag = () => {
    if (!terrainViewDrag) return
    terrainViewDrag = null
    if (rafId) {
      cancelAnimationFrame(rafId)
      rafId = 0
    }
    pendingPitch = null
    pendingRotation = null
    hideCrosshair()
    try {
      amap?.setStatus?.({
        dragEnable: true,
        animateEnable: savedAnimateEnable
      })
    } catch (err) { /* ignore */ }
    if (el) el.style.cursor = ''
  }

  const onDown = (e) => {
    if (!amap || !isOrbitGesture(e)) return
    e.preventDefault()
    e.stopPropagation()
    if (typeof e.stopImmediatePropagation === 'function') e.stopImmediatePropagation()

    try {
      const status = amap.getStatus?.() || {}
      savedAnimateEnable = status.animateEnable !== false
      amap.setStatus?.({ dragEnable: false, animateEnable: false })
    } catch (err) { /* ignore */ }

    const [anchorX, anchorY] = clientToContainer(e.clientX, e.clientY)
    terrainViewDrag = {
      startX: e.clientX,
      startY: e.clientY,
      startPitch: Number(amap.getPitch?.() || 0),
      startRotation: Number(amap.getRotation?.() || 0),
      pivotLngLat: pickPivotLngLat(anchorX, anchorY),
      anchorX,
      anchorY,
      button: e.button
    }
    showCrosshair(anchorX, anchorY)
    el.style.cursor = 'crosshair'
    try {
      el.setPointerCapture?.(e.pointerId)
    } catch (err) { /* ignore */ }
  }

  const onMove = (e) => {
    if (!terrainViewDrag || !amap) return
    e.preventDefault()

    // 相对按下点累计（谷歌地球：一次拖动手势，不跟丢）
    const dx = e.clientX - terrainViewDrag.startX
    const dy = e.clientY - terrainViewDrag.startY

    // 上拖 → 抬高到地平线；下拖 → 俯视；左/右拖 → 绕准星旋转
    const pitch = Math.max(0, Math.min(83, terrainViewDrag.startPitch - dy * PITCH_SENS))
    let rotation = terrainViewDrag.startRotation - dx * ROTATE_SENS
    rotation = ((rotation % 360) + 360) % 360

    pendingPitch = pitch
    pendingRotation = rotation
    if (!rafId) rafId = requestAnimationFrame(applyView)
  }

  const onUp = (e) => {
    if (!terrainViewDrag) return
    if (e && e.button != null && e.button !== terrainViewDrag.button) return
    endViewDrag()
  }

  const onLoseCapture = () => {
    endViewDrag()
  }

  const onAux = (e) => {
    if (e.button === 1) e.preventDefault()
  }

  const onContext = (e) => {
    e.preventDefault()
  }

  const onKeyUp = (e) => {
    // Shift+左键拖动中松开 Shift：结束，避免变成普通平移冲突
    if (terrainViewDrag && terrainViewDrag.button === 0 && e.key === 'Shift') {
      endViewDrag()
    }
  }

  el.addEventListener('pointerdown', onDown, true)
  window.addEventListener('pointermove', onMove, true)
  window.addEventListener('pointerup', onUp, true)
  window.addEventListener('pointercancel', onUp, true)
  window.addEventListener('keyup', onKeyUp, true)
  el.addEventListener('lostpointercapture', onLoseCapture)
  el.addEventListener('auxclick', onAux)
  el.addEventListener('contextmenu', onContext)
  el.addEventListener('mousedown', onAux, true)

  terrainViewHandlers = {
    onDown,
    onMove,
    onUp,
    onAux,
    onContext,
    onLoseCapture,
    onKeyUp,
    endViewDrag,
    disposeCrosshair: () => {
      if (crosshairEl?.parentNode) crosshairEl.parentNode.removeChild(crosshairEl)
      crosshairEl = null
    }
  }
  terrainViewControlsBound = true
}

function unbindTerrainViewControls() {
  const el = amapEl.value
  if (terrainViewHandlers?.endViewDrag) {
    try { terrainViewHandlers.endViewDrag() } catch (e) { /* ignore */ }
  } else {
    terrainViewDrag = null
  }
  if (!terrainViewControlsBound || !terrainViewHandlers) {
    terrainViewControlsBound = false
    terrainViewHandlers = null
    return
  }
  const { onDown, onMove, onUp, onAux, onContext, onLoseCapture, onKeyUp, disposeCrosshair } = terrainViewHandlers
  if (el) {
    el.removeEventListener('pointerdown', onDown, true)
    el.removeEventListener('lostpointercapture', onLoseCapture)
    el.removeEventListener('auxclick', onAux)
    el.removeEventListener('mousedown', onAux, true)
    el.removeEventListener('contextmenu', onContext)
    el.style.cursor = ''
  }
  window.removeEventListener('pointermove', onMove, true)
  window.removeEventListener('pointerup', onUp, true)
  window.removeEventListener('pointercancel', onUp, true)
  window.removeEventListener('keyup', onKeyUp, true)
  try { disposeCrosshair?.() } catch (e) { /* ignore */ }
  terrainViewHandlers = null
  terrainViewControlsBound = false
}

/**
 * 3D 自定义滚轮：细腻连续缩放（浮点级），并绕光标点放大缩小。
 * 不改动二维 Leaflet。
 */
function bindTerrainWheelZoom() {
  const el = amapEl.value
  if (!el || !amap || terrainWheelBound) return

  // 细腻：一格约 0.35 级；触控板按像素缓变
  const ZOOM_PER_NOTCH = 0.35
  const ZOOM_PER_PIXEL = 0.0045
  const MAX_STEP = 0.6
  const ZOOM_MIN = 3
  const ZOOM_MAX = 20

  const lockPivotUnderCursor = (pivot, mx, my) => {
    if (!pivot) return
    try {
      for (let i = 0; i < 3; i++) {
        const px = amap.lngLatToContainer(pivot)
        if (!px || !Number.isFinite(px.x)) return
        const dx = px.x - mx
        const dy = px.y - my
        if (Math.abs(dx) < 0.5 && Math.abs(dy) < 0.5) return
        const size = amap.getSize?.()
        if (!size) {
          amap.panBy(-dx, -dy, 0)
          continue
        }
        const ll = amap.containerToLngLat([size.width / 2 + dx, size.height / 2 + dy])
        if (!ll || !Number.isFinite(ll.lng)) {
          amap.panBy(-dx, -dy, 0)
          continue
        }
        amap.setCenter([ll.lng, ll.lat], true)
      }
    } catch (err) { /* ignore */ }
  }

  const onWheel = (e) => {
    if (!amap || terrainViewDrag) return
    e.preventDefault()
    e.stopPropagation()

    let dy = e.deltaY
    if (e.deltaMode === 1) dy *= 16
    else if (e.deltaMode === 2) dy *= 400
    if (!dy) return

    let step
    const abs = Math.abs(dy)
    // 机械滚轮常见一格 ≈ 100 → 小步浮点缩放
    if (abs >= 80 && abs <= 140) {
      step = dy > 0 ? -ZOOM_PER_NOTCH : ZOOM_PER_NOTCH
    } else {
      step = Math.max(-MAX_STEP, Math.min(MAX_STEP, -dy * ZOOM_PER_PIXEL))
    }
    if (!step) return

    const z0 = Number(amap.getZoom?.() || 10)
    const z1 = Math.min(ZOOM_MAX, Math.max(ZOOM_MIN, z0 + step))
    if (Math.abs(z1 - z0) < 0.001) return

    const rect = el.getBoundingClientRect()
    const mx = e.clientX - rect.left
    const my = e.clientY - rect.top
    let pivot = null
    try {
      const ll = amap.containerToLngLat([mx, my])
      if (ll && Number.isFinite(ll.lng)) pivot = [ll.lng, ll.lat]
    } catch (err) { /* ignore */ }

    try {
      amap.setZoom(z1, true)
      lockPivotUnderCursor(pivot, mx, my)
    } catch (err) { /* ignore */ }
  }

  el.addEventListener('wheel', onWheel, { passive: false, capture: true })
  terrainWheelHandler = onWheel
  terrainWheelBound = true
}

function unbindTerrainWheelZoom() {
  const el = amapEl.value
  if (terrainWheelBound && terrainWheelHandler && el) {
    el.removeEventListener('wheel', terrainWheelHandler, true)
  }
  terrainWheelHandler = null
  terrainWheelBound = false
}

/**
 * 二维操作与 3D 对齐：
 * - 左键拖动：平移
 * - 中键 / Shift+左键：绕按下点旋转（二维无俯仰）
 * - 滚轮：细腻绕光标缩放
 */
function bindLeafletViewControls() {
  const el = mapEl.value
  if (!el || !map || leafletViewControlsBound) return

  const ROTATE_SENS = 0.45
  let rafId = 0
  let pendingBearing = null
  let crosshairEl = null
  const host = el.parentElement || el

  const isOrbitGesture = (e) => {
    return e.button === 1 || (e.button === 0 && e.shiftKey && !e.ctrlKey && !e.metaKey && !e.altKey)
  }

  const clientToContainer = (clientX, clientY) => {
    const rect = el.getBoundingClientRect()
    return [clientX - rect.left, clientY - rect.top]
  }

  const showCrosshair = (x, y) => {
    if (!crosshairEl) {
      crosshairEl = document.createElement('div')
      crosshairEl.className = 'ge-orbit-crosshair'
      crosshairEl.innerHTML = '<i class="ge-orbit-crosshair__h"></i><i class="ge-orbit-crosshair__v"></i><i class="ge-orbit-crosshair__dot"></i>'
      host.appendChild(crosshairEl)
    }
    crosshairEl.style.left = `${x}px`
    crosshairEl.style.top = `${y}px`
    crosshairEl.classList.add('is-on')
  }

  const hideCrosshair = () => {
    if (crosshairEl) crosshairEl.classList.remove('is-on')
  }

  /** setBearing 绕地图中心；补偿平移使按下点钉在准星上 */
  const lockPivotToCrosshair = () => {
    if (!map || !leafletViewDrag?.pivotLatLng) return
    try {
      const px = map.latLngToContainerPoint(leafletViewDrag.pivotLatLng)
      if (!px) return
      const dx = px.x - leafletViewDrag.anchorX
      const dy = px.y - leafletViewDrag.anchorY
      if (Math.abs(dx) < 0.5 && Math.abs(dy) < 0.5) return
      map.panBy([dx, dy], { animate: false })
    } catch (err) { /* ignore */ }
  }

  const applyView = () => {
    rafId = 0
    if (!map || pendingBearing == null || !leafletViewDrag) return
    try {
      map.setBearing(pendingBearing)
      lockPivotToCrosshair()
      mapHeading.value = ((pendingBearing % 360) + 360) % 360
    } catch (err) { /* ignore */ }
    pendingBearing = null
  }

  const endViewDrag = () => {
    if (!leafletViewDrag) return
    leafletViewDrag = null
    if (rafId) {
      cancelAnimationFrame(rafId)
      rafId = 0
    }
    pendingBearing = null
    hideCrosshair()
    try {
      // 框选激活时保持禁用平移
      if (!props.boxSelectActive) map?.dragging?.enable()
    } catch (err) { /* ignore */ }
    if (el) el.style.cursor = ''
  }

  const onDown = (e) => {
    if (!map || terrainMode.value || !isOrbitGesture(e)) return
    // 框选模式下：Shift+左键仍可旋转；普通左键留给框选
    if (props.boxSelectActive && e.button === 0 && !e.shiftKey) return
    e.preventDefault()
    e.stopPropagation()
    if (typeof e.stopImmediatePropagation === 'function') e.stopImmediatePropagation()

    try {
      map.dragging?.disable()
    } catch (err) { /* ignore */ }

    const [anchorX, anchorY] = clientToContainer(e.clientX, e.clientY)
    let pivotLatLng = null
    try {
      pivotLatLng = map.containerPointToLatLng([anchorX, anchorY])
    } catch (err) { /* ignore */ }

    leafletViewDrag = {
      startX: e.clientX,
      startY: e.clientY,
      startBearing: Number(map.getBearing?.() || 0),
      pivotLatLng,
      anchorX,
      anchorY,
      button: e.button
    }
    showCrosshair(anchorX, anchorY)
    el.style.cursor = 'crosshair'
    try {
      el.setPointerCapture?.(e.pointerId)
    } catch (err) { /* ignore */ }
  }

  const onMove = (e) => {
    if (!leafletViewDrag || !map || terrainMode.value) return
    e.preventDefault()
    const dx = e.clientX - leafletViewDrag.startX
    // 二维无俯仰，仅左右旋转（与 3D 水平分量一致）
    let bearing = leafletViewDrag.startBearing - dx * ROTATE_SENS
    bearing = ((bearing % 360) + 360) % 360
    pendingBearing = bearing
    if (!rafId) rafId = requestAnimationFrame(applyView)
  }

  const onUp = (e) => {
    if (!leafletViewDrag) return
    if (e && e.button != null && e.button !== leafletViewDrag.button) return
    endViewDrag()
  }

  const onLoseCapture = () => endViewDrag()

  const onAux = (e) => {
    if (e.button === 1) e.preventDefault()
  }

  const onContext = (e) => {
    e.preventDefault()
  }

  const onKeyUp = (e) => {
    if (leafletViewDrag && leafletViewDrag.button === 0 && e.key === 'Shift') {
      endViewDrag()
    }
  }

  el.addEventListener('pointerdown', onDown, true)
  window.addEventListener('pointermove', onMove, true)
  window.addEventListener('pointerup', onUp, true)
  window.addEventListener('pointercancel', onUp, true)
  window.addEventListener('keyup', onKeyUp, true)
  el.addEventListener('lostpointercapture', onLoseCapture)
  el.addEventListener('auxclick', onAux)
  el.addEventListener('contextmenu', onContext)
  el.addEventListener('mousedown', onAux, true)

  leafletViewHandlers = {
    onDown, onMove, onUp, onAux, onContext, onLoseCapture, onKeyUp, endViewDrag,
    disposeCrosshair: () => {
      if (crosshairEl?.parentNode) crosshairEl.parentNode.removeChild(crosshairEl)
      crosshairEl = null
    }
  }
  leafletViewControlsBound = true
}

function unbindLeafletViewControls() {
  const el = mapEl.value
  if (leafletViewHandlers?.endViewDrag) {
    try { leafletViewHandlers.endViewDrag() } catch (e) { /* ignore */ }
  } else {
    leafletViewDrag = null
  }
  if (!leafletViewControlsBound || !leafletViewHandlers) {
    leafletViewControlsBound = false
    leafletViewHandlers = null
    return
  }
  const { onDown, onMove, onUp, onAux, onContext, onLoseCapture, onKeyUp, disposeCrosshair } = leafletViewHandlers
  if (el) {
    el.removeEventListener('pointerdown', onDown, true)
    el.removeEventListener('lostpointercapture', onLoseCapture)
    el.removeEventListener('auxclick', onAux)
    el.removeEventListener('mousedown', onAux, true)
    el.removeEventListener('contextmenu', onContext)
    el.style.cursor = ''
  }
  window.removeEventListener('pointermove', onMove, true)
  window.removeEventListener('pointerup', onUp, true)
  window.removeEventListener('pointercancel', onUp, true)
  window.removeEventListener('keyup', onKeyUp, true)
  try { disposeCrosshair?.() } catch (e) { /* ignore */ }
  leafletViewHandlers = null
  leafletViewControlsBound = false
}

/** 二维细腻滚轮缩放（参数与 3D 一致），绕光标 */
function bindLeafletWheelZoom() {
  const el = mapEl.value
  if (!el || !map || leafletWheelBound) return

  const ZOOM_PER_NOTCH = 0.35
  const ZOOM_PER_PIXEL = 0.0045
  const MAX_STEP = 0.6
  const ZOOM_MIN = 3
  const ZOOM_MAX = 22

  const onWheel = (e) => {
    if (!map || terrainMode.value || leafletViewDrag) return
    e.preventDefault()
    e.stopPropagation()
    if (typeof e.stopImmediatePropagation === 'function') e.stopImmediatePropagation()

    let dy = e.deltaY
    if (e.deltaMode === 1) dy *= 16
    else if (e.deltaMode === 2) dy *= 400
    if (!dy) return

    let step
    const abs = Math.abs(dy)
    if (abs >= 80 && abs <= 140) {
      step = dy > 0 ? -ZOOM_PER_NOTCH : ZOOM_PER_NOTCH
    } else {
      step = Math.max(-MAX_STEP, Math.min(MAX_STEP, -dy * ZOOM_PER_PIXEL))
    }
    if (!step) return

    const z0 = map.getZoom()
    const z1 = Math.min(ZOOM_MAX, Math.max(ZOOM_MIN, z0 + step))
    if (Math.abs(z1 - z0) < 0.001) return

    const rect = el.getBoundingClientRect()
    const mx = e.clientX - rect.left
    const my = e.clientY - rect.top
    try {
      // 先取光标下经纬度，缩放后再平移锁回光标（兼容 leaflet-rotate）
      const pivot = map.containerPointToLatLng([mx, my])
      map.setZoom(z1, { animate: false })
      // 若被 zoomSnap 吸回原级，绝不做平移，避免「只平移不缩放」
      if (Math.abs(map.getZoom() - z0) < 0.001) return
      const after = map.latLngToContainerPoint(pivot)
      const ox = after.x - mx
      const oy = after.y - my
      if (Math.abs(ox) > 0.5 || Math.abs(oy) > 0.5) {
        map.panBy([ox, oy], { animate: false })
      }
    } catch (err) {
      try { map.setZoom(z1, { animate: false }) } catch (e2) { /* ignore */ }
    }
  }

  el.addEventListener('wheel', onWheel, { passive: false, capture: true })
  leafletWheelHandler = onWheel
  leafletWheelBound = true
}

function unbindLeafletWheelZoom() {
  const el = mapEl.value
  if (leafletWheelBound && leafletWheelHandler && el) {
    el.removeEventListener('wheel', leafletWheelHandler, true)
  }
  leafletWheelHandler = null
  leafletWheelBound = false
}

function destroyAmap() {
  stopTerrainTraveler()
  unbindTerrainWheelZoom()
  unbindTerrainViewControls()
  if (amapStyleRedrawTimer) {
    clearTimeout(amapStyleRedrawTimer)
    amapStyleRedrawTimer = 0
  }
  closeAmapInfoWindow()
  amapInfoWindow = null
  clearAmapOverlays()
  amapActiveBaseLayers = []
  amapLayerDefault = null
  amapLayerSatellite = null
  amapLayerRoadNet = null
  if (amap) {
    try {
      amap.off('rotatechange', syncMapHeading)
      amap.off('mapmove', syncMapHeading)
    } catch (e) { /* ignore */ }
    try {
      amap.destroy()
    } catch (e) {
      /* ignore */
    }
    amap = null
  }
  mapHeading.value = 0
}

/** 与 2D createThumbDivIcon 同结构：缩略图 + 数量角标 */
function buildTerrainMarkerContent(point, count = 1) {
  const isVideo = Number(point?.fileType) === 2
  const estimated = count <= 1 && isEstimatedLocation(point)
  const thumb = mediaSrc(point, false)
  const fallbackText = isVideo ? '视频' : (isWaypointPoint(point) ? '点' : '图')
  const img = thumb
    ? `<img src="${escapeHtml(thumb)}" decoding="async" alt="" onerror="this.onerror=null;this.replaceWith(Object.assign(document.createElement('span'),{className:'pmc-fallback',textContent:'${fallbackText}'}))" />`
    : `<span class="pmc-fallback">${fallbackText}</span>`
  const badge = count > 1
    ? `<span class="pmc-badge">${formatCount(count)}</span>`
    : (estimated
      ? `<span class="pmc-est-badge pmc-est-badge--${point?.locationSource || 'time_interp'}" title="${escapeHtml(estimatedSourceTitle(point))}">${estimatedSourceLabel(point)}</span>`
      : '')
  const video = isVideo && count <= 1 ? '<span class="pmc-video">▶</span>' : ''
  const classes = [
    'pmc-marker',
    estimated ? 'pmc-marker--estimated' : '',
    count > 1 ? 'pmc-marker--cluster' : ''
  ].filter(Boolean).join(' ')
  return `<div class="${classes}"><div class="pmc-wrap"><div class="pmc-pin">${img}${video}</div>${badge}</div></div>`
}

function addAmapOverlay(overlay) {
  if (!overlay || !amap) return
  try {
    amap.add(overlay)
  } catch (e) {
    try {
      if (typeof overlay.setMap === 'function') overlay.setMap(amap)
    } catch (e2) {
      console.warn('[PhotoClusterMap] addAmapOverlay failed', e2)
      return
    }
  }
  amapOverlays.push(overlay)
}

function pathToAmapPath(latlngs) {
  if (!Array.isArray(latlngs)) return []
  const out = []
  for (let i = 0; i < latlngs.length; i++) {
    const p = leafletLatLngToAmap(latlngs[i])
    if (p && Number.isFinite(p[0]) && Number.isFinite(p[1])) out.push(p)
  }
  return out
}

function drawTerrainRegion(AMap) {
  if (!props.showRegionHighlight || !props.regionGeoJson) return
  const features = Array.isArray(props.regionGeoJson.features) ? props.regionGeoJson.features : []
  if (!features.length) return
  const order = { province: 0, city: 1, district: 2 }
  const sorted = features
    .filter(f => f && f.geometry && f.geometry.coordinates)
    .sort((a, b) => {
      const la = order[String(a?.properties?.level || '').toLowerCase()] ?? 0
      const lb = order[String(b?.properties?.level || '').toLowerCase()] ?? 0
      return la - lb
    })
  if (!sorted.length) return
  try {
    const geoLayer = new AMap.GeoJSON({
      geoJSON: { type: 'FeatureCollection', features: sorted },
      getPolygon: (geojson, lnglats) => {
        const level = String(geojson?.properties?.level || '').toLowerCase()
        let strokeColor = '#3B82F6'
        let fillColor = '#60A5FA'
        let fillOpacity = 0.22
        if (level === 'district') {
          strokeColor = '#1D4ED8'
          fillColor = '#2563EB'
          fillOpacity = 0.45
        } else if (level === 'city') {
          strokeColor = '#2563EB'
          fillColor = '#3B82F6'
          fillOpacity = 0.32
        }
        return new AMap.Polygon({
          path: lnglats,
          strokeColor,
          strokeWeight: 1.2,
          strokeOpacity: 0.85,
          fillColor,
          fillOpacity,
          bubble: true,
          zIndex: 40
        })
      }
    })
    addAmapOverlay(geoLayer)
  } catch (e) {
    console.warn('[PhotoClusterMap] terrain region failed', e)
  }
}

/** 与 Leaflet 一致：已挂 GPX 的媒体 photoId 集合 */
function terrainGpxMatchedIdSet() {
  return new Set(visibleMatchedPhotos().map(p => String(p.photoId)).filter(Boolean))
}

/**
 * 3D 折线必须 height:0 贴地。
 * height>0 时整条线相对「起点海拔」抬升，地形起伏时旋转视角会错位。
 */
function buildTerrainPolylineOpts(AMap, path, color) {
  const capped = decimateAmapPath(path, 2500)
  return {
    path: capped.map(p => new AMap.LngLat(p[0], p[1])),
    strokeColor: color,
    strokeWeight: 5,
    strokeOpacity: 0.95,
    lineJoin: 'round',
    lineCap: 'round',
    zIndex: 50,
    bubble: true,
    height: 0
  }
}

function decimateAmapPath(path, maxPoints) {
  if (!Array.isArray(path) || path.length <= maxPoints) return path
  const step = Math.ceil(path.length / maxPoints)
  const out = []
  for (let i = 0; i < path.length; i += step) out.push(path[i])
  const last = path[path.length - 1]
  if (out[out.length - 1] !== last) out.push(last)
  return out
}

function closeAmapInfoWindow() {
  if (amapInfoWindow) {
    try { amapInfoWindow.close() } catch (e) { /* ignore */ }
  }
  amapInfoWindowKey = null
}

function ensureAmapInfoWindow(AMap) {
  if (amapInfoWindow) return amapInfoWindow
  amapInfoWindow = new AMap.InfoWindow({
    isCustom: false,
    autoMove: true,
    // 与 Leaflet 默认一致：点击地图空白处关闭弹窗
    closeWhenClickMap: true,
    offset: new AMap.Pixel(0, -40)
  })
  amapInfoWindow.on('close', () => {
    amapInfoWindowKey = null
  })
  return amapInfoWindow
}

function openTerrainPhotoInfo(AMap, point, position) {
  if (!amap) return
  const key = point?.photoId != null
    ? `p:${point.photoId}`
    : `ll:${position[0]},${position[1]}`
  // 再点同一张：关闭（方便关掉弹窗）
  if (amapInfoWindowKey === key && amapInfoWindow?.getIsOpen?.()) {
    closeAmapInfoWindow()
    return
  }
  const info = ensureAmapInfoWindow(AMap)
  info.setContent(buildPopupHtml(point))
  amapInfoWindowKey = key
  info.open(amap, position)
}

function stopAmapEventBubble(e) {
  try {
    const native = e?.originEvent || e?.domEvent || e?.originalEvent
    if (native) {
      native.stopPropagation?.()
      native.preventDefault?.()
    }
  } catch (err) { /* ignore */ }
}

function bindTerrainMarkerHitDom(wrap, onClick) {
  wrap.className = 'pmc-amap-marker-hit'
  wrap.style.cursor = 'pointer'
  wrap.addEventListener('mousedown', (ev) => ev.stopPropagation())
  wrap.addEventListener('touchstart', (ev) => ev.stopPropagation(), { passive: true })
  wrap.addEventListener('click', (ev) => {
    ev.stopPropagation()
    ev.preventDefault()
    onClick()
  })
}

function createTerrainDomMarker(AMap, { position, html, zIndex = 100, onClick }) {
  const wrap = document.createElement('div')
  wrap.innerHTML = html
  bindTerrainMarkerHitDom(wrap, onClick)
  const marker = new AMap.Marker({
    position,
    content: wrap,
    offset: new AMap.Pixel(-26, -52),
    height: 0,
    zIndex,
    bubble: false,
    clickable: true,
    cursor: 'pointer'
  })
  marker.on('mousedown', stopAmapEventBubble)
  marker.on('touchstart', stopAmapEventBubble)
  marker.on('click', (e) => {
    stopAmapEventBubble(e)
    onClick()
  })
  return marker
}

function createTerrainPhotoMarker(AMap, point, position, count = 1) {
  return createTerrainDomMarker(AMap, {
    position,
    html: buildTerrainMarkerContent(point, count),
    zIndex: isEstimatedLocation(point) ? 120 : 100,
    onClick: () => openTerrainPhotoInfo(AMap, point, position)
  })
}

function addAmapClusterOverlay(overlay) {
  if (!overlay || !amap) return
  try {
    amap.add(overlay)
  } catch (e) {
    try { overlay.setMap?.(amap) } catch (e2) { return }
  }
  amapClusterOverlays.push(overlay)
  amapOverlays.push(overlay)
}

/** 收集 3D 可聚合媒体点（与 Leaflet 照片轨 + GPX 匹配一致） */
function collectTerrainClusterItems() {
  const matched = terrainGpxMatchedIdSet()
  const items = []
  let list = validPoints.value.filter(p => !isWaypointPoint(p)
    && !(p.photoId != null && matched.has(String(p.photoId))))
  if (list.length > TERRAIN_MARKER_CAP) {
    const step = Math.ceil(list.length / TERRAIN_MARKER_CAP)
    list = list.filter((_, i) => i % step === 0)
  }
  list.forEach((point) => {
    items.push({ point, lnglat: toAmapLngLat(point) })
  })
  let matchedList = visibleMatchedPhotos()
  if (matchedList.length > TERRAIN_MARKER_CAP) {
    const step = Math.ceil(matchedList.length / TERRAIN_MARKER_CAP)
    matchedList = matchedList.filter((_, i) => i % step === 0)
  }
  matchedList.forEach((p) => {
    const point = {
      ...p,
      description: p.description || (p.matchDeltaSec != null
        ? `GPX对齐（Δ${p.matchDeltaSec}s${p.interpolated ? '·插值' : ''}）`
        : 'GPX对齐')
    }
    items.push({ point, lnglat: toAmapLngLat(point) })
  })
  return items
}

/**
 * 像素网格聚合，半径与 2D Leaflet clusterRadiusForZoom 一致。
 * @returns {{ lnglat: number[], count: number, cover: any, members: any[] }[]}
 */
function clusterTerrainItems(items, zoom) {
  if (!amap || !items.length) return []
  // 高缩放不再聚合（对齐 disableClusteringAtZoom: 16）
  if (zoom >= TERRAIN_CLUSTER_MAX_ZOOM) {
    return items.map((it) => ({
      lnglat: it.lnglat,
      count: 1,
      cover: it.point,
      members: [it]
    }))
  }
  const radius = clusterRadiusForZoom(zoom)
  const r2 = radius * radius
  const buckets = []
  for (const it of items) {
    let px
    try {
      px = amap.lngLatToContainer(it.lnglat)
    } catch (e) {
      buckets.push({
        x: 0,
        y: 0,
        lngSum: it.lnglat[0],
        latSum: it.lnglat[1],
        members: [it]
      })
      continue
    }
    const x = px.x
    const y = px.y
    let hit = null
    for (let i = 0; i < buckets.length; i++) {
      const b = buckets[i]
      const dx = b.x - x
      const dy = b.y - y
      if (dx * dx + dy * dy <= r2) {
        hit = b
        break
      }
    }
    if (hit) {
      hit.members.push(it)
      const n = hit.members.length
      hit.x += (x - hit.x) / n
      hit.y += (y - hit.y) / n
      hit.lngSum += it.lnglat[0]
      hit.latSum += it.lnglat[1]
    } else {
      buckets.push({
        x,
        y,
        lngSum: it.lnglat[0],
        latSum: it.lnglat[1],
        members: [it]
      })
    }
  }
  return buckets.map((b) => {
    const n = b.members.length
    return {
      lnglat: [b.lngSum / n, b.latSum / n],
      count: n,
      cover: b.members[0].point,
      members: b.members
    }
  })
}

function onTerrainClusterClick(AMap, cluster) {
  if (!amap || !cluster) return
  terrainSpiderfyActive = false
  const zoom = amap.getZoom()
  // 未到解聚级别：放大靠近（与 Leaflet zoomToBoundsOnClick 类似）
  if (zoom < TERRAIN_CLUSTER_MAX_ZOOM - 0.2 && cluster.count > 1) {
    const nextZoom = Math.min(TERRAIN_CLUSTER_MAX_ZOOM, zoom + 2)
    amap.setZoomAndCenter(nextZoom, cluster.lnglat)
    return
  }
  // 已足够大仍叠在一起：蜘蛛腿散开
  if (cluster.count > 1) {
    spiderfyTerrainCluster(AMap, cluster)
    return
  }
  openTerrainPhotoInfo(AMap, cluster.cover, cluster.lnglat)
}

function spiderfyTerrainCluster(AMap, cluster) {
  if (!amap || !cluster?.members?.length) return
  terrainSpiderfyActive = true
  clearAmapClusterOverlays()
  const center = cluster.lnglat
  const n = cluster.members.length
  const radiusPx = Math.min(80, 28 + n * 4)
  let origin
  try {
    origin = amap.lngLatToContainer(center)
  } catch (e) {
    cluster.members.forEach((m) => {
      addAmapClusterOverlay(createTerrainPhotoMarker(AMap, m.point, m.lnglat))
    })
    return
  }
  cluster.members.forEach((m, i) => {
    const ang = (Math.PI * 2 * i) / n - Math.PI / 2
    const x = origin.x + radiusPx * Math.cos(ang)
    const y = origin.y + radiusPx * Math.sin(ang)
    let pos = m.lnglat
    try {
      const ll = amap.containerToLngLat([x, y])
      pos = [ll.lng, ll.lat]
    } catch (e) { /* keep */ }
    addAmapClusterOverlay(new AMap.Polyline({
      path: [center, pos],
      strokeColor: '#9b59b6',
      strokeWeight: 2,
      strokeOpacity: 0.7,
      height: 0,
      zIndex: 40,
      bubble: false
    }))
    addAmapClusterOverlay(createTerrainPhotoMarker(AMap, m.point, pos))
  })
}

function paintTerrainClusterMarkers(AMap) {
  if (!amap || !props.showMediaMarkers) return
  const zoom = amap.getZoom()
  // 蜘蛛腿展开中：仅缩到解聚级别以下时才重新聚合
  if (terrainSpiderfyActive) {
    if (zoom >= TERRAIN_CLUSTER_MAX_ZOOM) return
    terrainSpiderfyActive = false
  }
  clearAmapClusterOverlays()
  const clusters = clusterTerrainItems(terrainClusterSource, zoom)
  clusters.forEach((c) => {
    if (c.count <= 1) {
      addAmapClusterOverlay(createTerrainPhotoMarker(AMap, c.cover, c.lnglat, 1))
      return
    }
    addAmapClusterOverlay(createTerrainDomMarker(AMap, {
      position: c.lnglat,
      html: buildTerrainMarkerContent(c.cover, c.count),
      zIndex: 110,
      onClick: () => onTerrainClusterClick(AMap, c)
    }))
  })
}

function onTerrainClusterZoomEnd() {
  if (!amap || !window.AMap || !terrainMode.value) return
  paintTerrainClusterMarkers(window.AMap)
}

function bindTerrainClusterZoom() {
  if (!amap || terrainClusterZoomBound) return
  amap.on('zoomend', onTerrainClusterZoomEnd)
  terrainClusterZoomBound = true
}

function drawTerrainPolylines(AMap) {
  if (!props.showPolyline) return
  const source = polylineSource.value
  if (source.length < 2) return
  // 与 Leaflet drawSegments 一致：任一端已挂 GPX 则不画照片轨，避免与 GPX 叠线错乱
  const matched = terrainGpxMatchedIdSet()

  for (let i = 0; i < source.length - 1; i++) {
    const from = source[i]
    const to = source[i + 1]
    const fromMatched = from?.photoId != null && matched.has(String(from.photoId))
    const toMatched = to?.photoId != null && matched.has(String(to.photoId))
    if (fromMatched || toMatched) continue
    if (!isSegmentModeVisible(from?.travelMode)) continue
    const latlngs = resolveSegmentLatLngs(from, to)
    if (!latlngs || latlngs.length < 2) continue
    const path = pathToAmapPath(latlngs)
    if (path.length < 2) continue
    const color = props.segmentByTravelMode
      ? travelModeColor(from.travelMode, props.polylineColor)
      : (props.polylineColor || '#3B82F6')
    addAmapOverlay(new AMap.Polyline(buildTerrainPolylineOpts(AMap, path, color)))
  }
}

function drawTerrainGpx(AMap) {
  if (!hasGpxPathOverlay.value) return
  overlayList.value.forEach((item) => {
    if (item?.showPath === false) return
    if (!isGpxItemVisible(item)) return
    const segments = gpxDrawSegments(item)
    segments.forEach((seg) => {
      if (!isSegmentModeVisible(seg.travelMode)) return
      let latlngs = normalizeOverlayLatLngs(seg.path)
      if (latlngs.length < 2) return
      if (isRailTravelMode(seg.travelMode)) {
        latlngs = cleanRailDisplayPath(latlngs, { minMeters: 35 })
      }
      const path = pathToAmapPath(latlngs)
      if (path.length < 2) return
      const color = travelModeColor(seg.travelMode, seg.color || item.color || '#10B981')
      addAmapOverlay(new AMap.Polyline({
        ...buildTerrainPolylineOpts(AMap, path, color),
        zIndex: 55
      }))
    })
  })
}

function drawTerrainMarkers(AMap) {
  // 途经点不参与聚合
  validPoints.value.filter(isWaypointPoint).forEach((point) => {
    const position = toAmapLngLat(point)
    addAmapOverlay(new AMap.CircleMarker({
      center: position,
      radius: 5,
      strokeColor: '#fff',
      strokeWeight: 2,
      fillColor: travelModeColor(point.travelMode, '#64748B'),
      fillOpacity: 1,
      zIndex: 90,
      bubble: false
    }))
  })

  if (!props.showMediaMarkers) {
    terrainClusterSource = []
    clearAmapClusterOverlays()
    unbindTerrainClusterZoom()
    return
  }

  terrainClusterSource = collectTerrainClusterItems()
  terrainSpiderfyActive = false
  paintTerrainClusterMarkers(AMap)
  bindTerrainClusterZoom()
}

function stopTerrainTraveler() {
  if (terrainTravelerRaf) {
    cancelAnimationFrame(terrainTravelerRaf)
    terrainTravelerRaf = 0
  }
  if (terrainTraveler?.marker && amap) {
    try { amap.remove(terrainTraveler.marker) } catch (e) { /* ignore */ }
  }
  terrainTraveler = null
}

function terrainTravelerContent(mode) {
  const color = travelModeColor(mode)
  return `<div class="pmc-traveler is-${mode}" style="--mode-color:${color}">`
    + `<div class="pmc-traveler-badge">${travelModeIconSvg(mode, 16)}</div></div>`
}

function startTerrainTraveler(journey) {
  stopTerrainTraveler()
  if (!amap || !window.AMap || !journey || !shouldAnimateTravelers()) return
  const { metrics, spans } = journey
  if (!metrics || !spans?.length) return
  const { timed, duration } = planTimeline(spans)
  if (!timed.length || !(duration > 0)) return
  const firstMode = timed[0].mode
  const start = metrics.segs[0].from
  const marker = new window.AMap.Marker({
    position: [start.lng, start.lat],
    content: terrainTravelerContent(firstMode),
    offset: new window.AMap.Pixel(-14, -14),
    height: 0,
    zIndex: 200,
    bubble: false
  })
  amap.add(marker)
  terrainTraveler = { marker, metrics, timed, duration, mode: firstMode }
  terrainTravelerStartedAt = performance.now()
  const tick = (now) => {
    if (!terrainTraveler || !amap) return
    const state = journeyStateAt(terrainTraveler.timed, terrainTraveler.duration, now - terrainTravelerStartedAt)
    const pos = pointAlongPath(terrainTraveler.metrics, state.dist)
    terrainTraveler.marker.setPosition([pos.latlng.lng, pos.latlng.lat])
    if (state.mode !== terrainTraveler.mode) {
      terrainTraveler.mode = state.mode
      terrainTraveler.marker.setContent(terrainTravelerContent(state.mode))
    }
    terrainTravelerRaf = requestAnimationFrame(tick)
  }
  terrainTravelerRaf = requestAnimationFrame(tick)
}

function drawTerrainTravelers() {
  if (!amap || !window.AMap || !shouldAnimateTravelers()) {
    stopTerrainTraveler()
    return
  }
  const matched = terrainGpxMatchedIdSet()
  const journey = buildJourney(collectJourneyLegs(polylineSource.value, matched))
  startTerrainTraveler(journey)
}

function renderTerrainOverlays() {
  if (!amap || !window.AMap || !terrainMode.value) return
  stopTerrainTraveler()
  closeAmapInfoWindow()
  clearAmapOverlays()
  const AMap = window.AMap
  try {
    drawTerrainRegion(AMap)
    drawTerrainPolylines(AMap)
    drawTerrainGpx(AMap)
    drawTerrainMarkers(AMap)
    drawTerrainTravelers()
  } catch (e) {
    console.warn('[PhotoClusterMap] renderTerrainOverlays failed', e)
  }
}

function captureLeafletView() {
  if (!map) return null
  const c = map.getCenter()
  return {
    center: [c.lng, c.lat],
    zoom: map.getZoom()
  }
}

async function enterTerrainMode() {
  if (terrainMode.value || terrainLoading.value) return
  const token = ++terrainEnterToken
  terrainLoading.value = true
  try {
    const AMap = await loadAmap()
    if (token !== terrainEnterToken) return
    if (!amapEl.value) throw new Error('地图容器未就绪')

    leafletViewBeforeTerrain = captureLeafletView()
    const center = leafletViewBeforeTerrain?.center || [105.0, 35.0]
    const zoom = Math.min(Math.max(leafletViewBeforeTerrain?.zoom || 5, 3), 18)

    // 3D 只读：关闭框选等 Leaflet 编辑交互
    syncBoxSelectMode(false)

    // 先显示 AMap 容器再初始化，避免 hidden 时宽高为 0
    destroyAmap()
    terrainMode.value = true
    emit('terrain-mode-change', true)
    await nextTick()
    if (token !== terrainEnterToken) return
    if (!amapEl.value) throw new Error('地图容器未就绪')

    ensureAmapBaseLayers(AMap)
    const initLayers = layersForStyle(currentStyle.value).filter(Boolean)
    amap = new AMap.Map(amapEl.value, {
      viewMode: '3D',
      terrain: true,
      pitch: 50,
      rotation: 0,
      zoom,
      center,
      resizeEnable: true,
      dragEnable: true,
      // 关闭官方滚轮，改用更灵敏的自定义缩放（绕光标）
      scrollWheel: false,
      // 关闭官方「右键/Ctrl+左键」调视角，改由中键自定义控制
      pitchEnable: false,
      rotateEnable: false,
      // 使用可复用图层实例；后续切换只 add/remove，不用 setLayers
      layers: initLayers.length ? initLayers : undefined
    })
    amapActiveBaseLayers = initLayers.slice()
    bindTerrainViewControls()
    bindTerrainWheelZoom()
    try {
      amap.on('rotatechange', syncMapHeading)
      amap.on('mapmove', syncMapHeading)
    } catch (e) { /* ignore */ }
    syncMapHeading()
    try {
      amap.resize?.()
    } catch (e) { /* ignore */ }
    // 等地形就绪后再画，height:0 贴地才稳
    whenAmapReady(() => {
      if (token !== terrainEnterToken || !terrainMode.value) return
      renderTerrainOverlays()
    })
  } catch (e) {
    console.error('[PhotoClusterMap] enterTerrainMode failed', e)
    destroyAmap()
    if (terrainMode.value) {
      terrainMode.value = false
      emit('terrain-mode-change', false)
    }
    ElMessage.error(e?.message || '无法开启 3D 地形图')
  } finally {
    if (token === terrainEnterToken) terrainLoading.value = false
  }
}

function exitTerrainMode() {
  terrainEnterToken += 1
  terrainLoading.value = false
  const view = leafletViewBeforeTerrain
  destroyAmap()
  terrainMode.value = false
  emit('terrain-mode-change', false)
  nextTick(() => {
    if (!map) return
    if (view?.center) {
      try {
        map.setView([view.center[1], view.center[0]], view.zoom, { animate: false })
      } catch (e) { /* ignore */ }
    }
    invalidateMapSize()
    setTimeout(() => invalidateMapSize(), 120)
  })
}

function toggleTerrainMode() {
  if (terrainLoading.value) return
  if (terrainMode.value) exitTerrainMode()
  else enterTerrainMode()
}

function syncMapHeading() {
  if (terrainMode.value && amap) {
    const r = Number(amap.getRotation?.() || 0)
    mapHeading.value = ((r % 360) + 360) % 360
    return
  }
  if (map && typeof map.getBearing === 'function') {
    const r = Number(map.getBearing() || 0)
    mapHeading.value = ((r % 360) + 360) % 360
    return
  }
  mapHeading.value = 0
}

/**
 * 重置为正北俯视：上北下南、左西右东（rotation=0, pitch=0）
 */
function resetMapOrientation() {
  if (terrainMode.value && amap) {
    try {
      amap.setRotation(0, true)
      amap.setPitch(0, true)
    } catch (e) { /* ignore */ }
    mapHeading.value = 0
    return
  }
  if (map && typeof map.setBearing === 'function') {
    try {
      map.setBearing(0)
    } catch (e) { /* ignore */ }
  }
  mapHeading.value = 0
}

/** 地形瓦片就绪后再画覆盖物，贴地更稳 */
function whenAmapReady(cb) {
  if (!amap) return
  let done = false
  const run = () => {
    if (done) return
    done = true
    try { cb() } catch (e) {
      console.warn('[PhotoClusterMap] amap ready callback failed', e)
    }
  }
  try {
    amap.on('complete', run)
  } catch (e) { /* ignore */ }
  // 兜底：部分环境 complete 已触发或不触发
  setTimeout(run, 600)
}

function setMarkerPaneVisible(visible) {
  const pane = map?.getPane?.('markerPane')
  if (pane) pane.style.visibility = visible ? '' : 'hidden'
  const shadow = map?.getPane?.('shadowPane')
  if (shadow) shadow.style.visibility = visible ? '' : 'hidden'
  const estimatedPane = map?.getPane?.('estimatedPane')
  if (estimatedPane) estimatedPane.style.visibility = visible ? '' : 'hidden'
  const travelerPane = map?.getPane?.('travelerPane')
  if (travelerPane) travelerPane.style.visibility = visible ? '' : 'hidden'
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
  stopTravelers()
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

function prefersReducedMotion() {
  return typeof window !== 'undefined'
    && !!window.matchMedia
    && window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

function shouldAnimateTravelers() {
  if (!props.showTravelers) return false
  if (!props.showPolyline && !hasGpxPathOverlay.value) return false
  if (props.editable || props.boxSelectActive || props.pathEditIndex >= 0) return false
  if (props.locationCorrectable) return false
  if (prefersReducedMotion()) return false
  return true
}

function pathMetrics(latlngs) {
  if (!latlngs || latlngs.length < 2) return null
  const pts = []
  for (const p of latlngs) {
    const lat = Number(Array.isArray(p) ? p[0] : p.lat)
    const lng = Number(Array.isArray(p) ? p[1] : p.lng)
    if (Number.isNaN(lat) || Number.isNaN(lng)) continue
    pts.push(L.latLng(lat, lng))
  }
  if (pts.length < 2) return null
  const segs = []
  let total = 0
  for (let i = 0; i < pts.length - 1; i++) {
    const len = pts[i].distanceTo(pts[i + 1])
    segs.push({ from: pts[i], to: pts[i + 1], start: total, len })
    total += len
  }
  if (total <= 0) return null
  return { segs, total }
}

function headingDeg(from, to) {
  const φ1 = from.lat * Math.PI / 180
  const φ2 = to.lat * Math.PI / 180
  const Δλ = (to.lng - from.lng) * Math.PI / 180
  const y = Math.sin(Δλ) * Math.cos(φ2)
  const x = Math.cos(φ1) * Math.sin(φ2) - Math.sin(φ1) * Math.cos(φ2) * Math.cos(Δλ)
  return Math.atan2(y, x) * 180 / Math.PI
}

function pointAlongPath(metrics, dist) {
  const segs = metrics.segs
  let d = dist
  if (d <= 0) {
    return { latlng: segs[0].from, heading: headingDeg(segs[0].from, segs[0].to) }
  }
  if (d >= metrics.total) {
    const last = segs[segs.length - 1]
    return { latlng: last.to, heading: headingDeg(last.from, last.to) }
  }
  for (const seg of segs) {
    if (d <= seg.start + seg.len) {
      const t = seg.len <= 0 ? 0 : (d - seg.start) / seg.len
      return {
        latlng: L.latLng(
          seg.from.lat + (seg.to.lat - seg.from.lat) * t,
          seg.from.lng + (seg.to.lng - seg.from.lng) * t
        ),
        heading: headingDeg(seg.from, seg.to)
      }
    }
  }
  const last = segs[segs.length - 1]
  return { latlng: last.to, heading: headingDeg(last.from, last.to) }
}

function pushLeg(legs, mode, latlngs) {
  const key = String(mode || 'other').toLowerCase() || 'other'
  const last = legs.length ? legs[legs.length - 1] : null
  if (last && last.mode === key) {
    last.latlngs.push(...latlngs.slice(1))
    return
  }
  legs.push({ mode: key, latlngs: latlngs.slice() })
}

function collectPhotoJourneyLegs(list, matchedIds) {
  const legs = []
  const matched = matchedIds instanceof Set ? matchedIds : null
  for (let i = 0; i < list.length - 1; i++) {
    const from = list[i]
    const to = list[i + 1]
    const fromMatched = matched && from?.photoId != null && matched.has(String(from.photoId))
    const toMatched = matched && to?.photoId != null && matched.has(String(to.photoId))
    if (fromMatched || toMatched) continue
    if (!isSegmentModeVisible(from.travelMode)) continue
    const latlngs = resolveSegmentLatLngs(from, to)
    if (!latlngs || latlngs.length < 2) continue
    pushLeg(legs, from.travelMode || 'other', latlngs)
  }
  return legs
}

function gpxItemJourneyLegs(item) {
  const legs = []
  if (!item || item.showPath === false || !isGpxItemVisible(item)) return legs
  const segments = gpxDrawSegments(item)
  segments.forEach((seg) => {
    if (!isSegmentModeVisible(seg.travelMode)) return
    let latlngs = normalizeOverlayLatLngs(seg.path)
    if (latlngs.length < 2) return
    if (isRailTravelMode(seg.travelMode)) {
      latlngs = cleanRailDisplayPath(latlngs, { minMeters: 35 })
    }
    if (!latlngs || latlngs.length < 2) return
    pushLeg(legs, seg.travelMode || item.travelMode || 'other', latlngs)
  })
  return legs
}

function journeyTipLatLng(legs) {
  if (!legs.length) return null
  const last = legs[legs.length - 1]
  const path = last?.latlngs
  if (!path || path.length < 1) return null
  const p = path[path.length - 1]
  return L.latLng(Number(p[0]), Number(p[1]))
}

function pathEndpoints(latlngs) {
  if (!latlngs || latlngs.length < 2) return null
  return {
    start: L.latLng(Number(latlngs[0][0]), Number(latlngs[0][1])),
    end: L.latLng(Number(latlngs[latlngs.length - 1][0]), Number(latlngs[latlngs.length - 1][1]))
  }
}

function collectGpxJourneyLegs(afterLegs = []) {
  const pending = []
  overlayList.value.forEach((item) => {
    const legs = gpxItemJourneyLegs(item)
    if (!legs.length) return
    const flat = []
    legs.forEach((leg) => {
      if (!flat.length) flat.push(...leg.latlngs)
      else flat.push(...leg.latlngs.slice(1))
    })
    if (flat.length < 2) return
    pending.push({ item, legs, flat })
  })
  if (!pending.length) return []

  const out = []
  let tip = journeyTipLatLng(afterLegs)
  // 按与当前末端的距离贪心拼接，避免只走第一条 GPX 就折返
  while (pending.length) {
    let bestIdx = 0
    let bestDist = Infinity
    let bestReverse = false
    for (let i = 0; i < pending.length; i++) {
      const ends = pathEndpoints(pending[i].flat)
      if (!ends) continue
      if (!tip) {
        bestIdx = i
        bestDist = 0
        bestReverse = false
        break
      }
      const dStart = tip.distanceTo(ends.start)
      const dEnd = tip.distanceTo(ends.end)
      const d = Math.min(dStart, dEnd)
      if (d < bestDist) {
        bestDist = d
        bestIdx = i
        bestReverse = dEnd + 25 < dStart
      }
    }
    const picked = pending.splice(bestIdx, 1)[0]
    const legs = bestReverse
      ? picked.legs.slice().reverse().map((leg) => ({
          mode: leg.mode,
          latlngs: leg.latlngs.slice().reverse()
        }))
      : picked.legs
    // 桥接小缝隙（途经点「GPX起」与绿色「起」差几米）
    if (tip && legs.length) {
      const first = legs[0].latlngs[0]
      const gap = tip.distanceTo(L.latLng(Number(first[0]), Number(first[1])))
      if (gap > 2 && gap < 800) {
        out.push({
          mode: legs[0].mode || 'walk',
          latlngs: [
            [tip.lat, tip.lng],
            [Number(first[0]), Number(first[1])]
          ]
        })
      }
    }
    legs.forEach((leg) => {
      pushLeg(out, leg.mode, leg.latlngs)
    })
    tip = journeyTipLatLng(out)
  }
  return out
}

function collectJourneyLegs(list, matchedIds) {
  const legs = []
  if (props.showPolyline && list && list.length > 1) {
    legs.push(...collectPhotoJourneyLegs(list, matchedIds))
  }
  if (hasGpxPathOverlay.value) {
    legs.push(...collectGpxJourneyLegs(legs))
  }
  return legs
}

function appendJourneyLatLngs(dest, src) {
  if (!src || src.length < 2) return 0
  if (!dest.length) {
    dest.push(...src)
    return pathMetrics(src)?.total || 0
  }
  const last = dest[dest.length - 1]
  const first = src[0]
  const same = last && first
    && Math.abs(Number(last[0]) - Number(first[0])) < 1e-7
    && Math.abs(Number(last[1]) - Number(first[1])) < 1e-7
  // 未重合时插入桥接点，保证动画连续走过缝隙而不是瞬移后折返
  if (!same) {
    dest.push([Number(first[0]), Number(first[1])])
  }
  const added = src.slice(1)
  const chunk = [last, ...src]
  dest.push(...added)
  return pathMetrics(chunk)?.total || 0
}

function buildJourney(legs) {
  if (!legs.length) return null
  const latlngs = []
  const spans = []
  let dist = 0
  for (const leg of legs) {
    const len = appendJourneyLatLngs(latlngs, leg.latlngs)
    if (len <= 0 && !spans.length) continue
    spans.push({ start: dist, end: dist + len, mode: leg.mode })
    dist += len
  }
  const metrics = pathMetrics(latlngs)
  if (!metrics || !spans.length || metrics.total < 40) return null
  return { metrics, spans }
}

function planTimeline(spans) {
  let t = 0
  const timed = spans.map((s) => {
    const len = Math.max(0, s.end - s.start)
    const motion = travelModeMotion(s.mode)
    let dt
    if (s.mode === 'stay') {
      dt = Math.max(1200, Math.min(2800, len > 0 ? (len / 8) * 1000 : 1800))
    } else {
      dt = (len / (motion.mps || 50)) * 1000
    }
    dt = Math.max(dt, 80)
    const item = { ...s, t0: t, t1: t + dt }
    t += dt
    return item
  })
  const raw = t || 12000
  // 照片轨 + GPX 接起来会很长，放宽上限避免被压得过快、看起来像折返
  const duration = Math.min(120000, Math.max(12000, raw))
  const scale = duration / raw
  timed.forEach((s) => {
    s.t0 *= scale
    s.t1 *= scale
  })
  return { timed, duration }
}

function journeyStateAt(timed, duration, elapsed) {
  const t = elapsed % duration
  for (const s of timed) {
    if (t <= s.t1) {
      const span = s.t1 - s.t0
      const u = span <= 0 ? 1 : Math.min(1, Math.max(0, (t - s.t0) / span))
      return { dist: s.start + (s.end - s.start) * u, mode: s.mode }
    }
  }
  const last = timed[timed.length - 1]
  return { dist: last.end, mode: last.mode }
}

function travelerIcon(mode) {
  const color = travelModeColor(mode)
  return L.divIcon({
    className: 'pmc-traveler-marker',
    html: `<div class="pmc-traveler is-${mode}" style="--mode-color:${color}">`
      + `<div class="pmc-traveler-badge">${travelModeIconSvg(mode, 16)}</div></div>`,
    iconSize: [28, 28],
    iconAnchor: [14, 14]
  })
}

function applyTravelerHeading(el, motion, heading) {
  if (!el) return
  const rotate = motion?.rotate || 'none'
  const offset = Number(motion?.headingOffset) || 0
  if (rotate === 'heading') {
    el.style.setProperty('--heading', `${heading + offset}deg`)
    el.classList.remove('is-flip')
    return
  }
  if (rotate === 'flip') {
    el.style.setProperty('--heading', '0deg')
    el.classList.toggle('is-flip', heading > 90 || heading < -90)
    return
  }
  el.style.setProperty('--heading', '0deg')
  el.classList.remove('is-flip')
}

function stopTravelers() {
  if (travelerRaf) {
    cancelAnimationFrame(travelerRaf)
    travelerRaf = 0
  }
  if (travelerActor?.marker) {
    try { travelerActor.marker.remove() } catch (e) { /* ignore */ }
  }
  travelerActor = null
}

function startTraveler(journey) {
  stopTravelers()
  if (!map || !journey) return
  const { metrics, spans } = journey
  const { timed, duration } = planTimeline(spans)
  if (!timed.length) return
  const firstMode = timed[0].mode
  const start = pointAlongPath(metrics, 0)
  const marker = L.marker(start.latlng, {
    icon: travelerIcon(firstMode),
    interactive: false,
    keyboard: false,
    pane: 'travelerPane',
    zIndexOffset: 500
  }).addTo(map)
  travelerActor = { marker, metrics, timed, duration, mode: firstMode }
  nextTick(() => applyTravelerHeading(marker.getElement(), travelModeMotion(firstMode), start.heading))
  travelerStartedAt = performance.now()
  const tick = (now) => {
    if (!travelerActor) return
    const state = journeyStateAt(travelerActor.timed, travelerActor.duration, now - travelerStartedAt)
    const pos = pointAlongPath(travelerActor.metrics, state.dist)
    travelerActor.marker.setLatLng(pos.latlng)
    if (state.mode !== travelerActor.mode) {
      travelerActor.mode = state.mode
      travelerActor.marker.setIcon(travelerIcon(state.mode))
    }
    applyTravelerHeading(travelerActor.marker.getElement(), travelModeMotion(state.mode), pos.heading)
    travelerRaf = requestAnimationFrame(tick)
  }
  travelerRaf = requestAnimationFrame(tick)
}

function drawTravelers(list, matchedIds) {
  if (!shouldAnimateTravelers()) return
  startTraveler(buildJourney(collectJourneyLegs(list, matchedIds)))
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

function buildGpxPopupHtml(item, nearest, segmentMode) {
  const stats = item.stats || {}
  const pointMode = nearest?.travelMode || segmentMode || ''
  const primaryMode = item.travelMode || ''
  const segs = Array.isArray(item.segments) ? item.segments : []
  const modeSet = [...new Set(segs.map(s => s?.travelMode).filter(Boolean))]
  const mixText = modeSet.length > 1
    ? modeSet.map(m => travelModeLabel(m) || m).join(' → ')
    : (travelModeLabel(primaryMode) || primaryMode || '-')
  const rows = []
  rows.push(`<div class="pmc-gpx-title">GPX：${escapeHtml(item.fileName || '')}</div>`)
  rows.push(`<div class="pmc-gpx-tag">线路不可贴合路网 · 可修改出行方式</div>`)
  rows.push(metaRow(modeSet.length > 1 ? '行程构成' : '出行方式', mixText))
  if (pointMode) {
    rows.push(metaRow('当前段', travelModeLabel(pointMode) || pointMode))
  }
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
    const fullLatLngs = normalizeOverlayLatLngs(item.path)
    if (fullLatLngs.length < 2) {
      console.warn('[GPX] path points too few', item.fileName, item.pathPointCount, item.path?.length)
      return
    }
    all.push(...(all.length ? fullLatLngs.slice(1) : fullLatLngs))

    const segments = gpxDrawSegments(item)
    segments.forEach((seg) => {
      if (!isSegmentModeVisible(seg.travelMode)) return
      let latlngs = normalizeOverlayLatLngs(seg.path)
      if (latlngs.length < 2) return
      if (isRailTravelMode(seg.travelMode)) {
        latlngs = cleanRailDisplayPath(latlngs, { minMeters: 35 })
      }
      const color = travelModeColor(seg.travelMode, seg.color || item.color || '#10B981')
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
      const hit = L.polyline(latlngs, {
        color,
        weight: 16,
        opacity: 0,
        smoothFactor: 0,
        interactive: true,
        className: 'pmc-gpx-hit'
      }).addTo(gpxOverlayLayer)

      const openPopup = (e) => {
        if (props.gpxEndpointPickable) return
        L.DomEvent.stopPropagation(e)
        const nearest = findNearestGpxPoint(item.path, e.latlng)
        const html = buildGpxPopupHtml(item, nearest, seg.travelMode)
        L.popup({ maxWidth: 320, className: 'pmc-popup-wrap', autoPan: true })
          .setLatLng(e.latlng)
          .setContent(html)
          .openOn(map)
        emit('gpx-click', { overlay: item, nearest, latlng: e.latlng, segmentMode: seg.travelMode })
      }
      line.on('click', openPopup)
      hit.on('click', openPopup)
      const modeText = travelModeLabel(seg.travelMode) || seg.travelMode || 'GPX'
      line.bindTooltip(
        props.gpxEndpointPickable
          ? `GPX：${item.fileName || ''} · 请点「起/终」标记选用为增补锚点`
          : `GPX：${item.fileName || ''}（${modeText}）· 点击查看/修改`,
        { sticky: true, direction: 'top' }
      )
      drawGpxFlow(latlngs, color)
    })

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
    const startMarker = L.marker(fullLatLngs[0], {
      icon: startIcon,
      interactive: pickable,
      zIndexOffset: 650,
      keyboard: false
    }).addTo(gpxOverlayLayer)
    const endMarker = L.marker(fullLatLngs[fullLatLngs.length - 1], {
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
          latlng: e.latlng || (kind === 'start' ? fullLatLngs[0] : fullLatLngs[fullLatLngs.length - 1])
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
  if (!props.showMediaMarkers) return []
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
  drawTravelers(lineList, matchedIds)

  if (list.length) {
    const photoMarkers = []
    const showMedia = props.showMediaMarkers !== false
    list.forEach((p, index) => {
      // 已挂到 GPX 上的媒体不再在照片轨坐标重复打点
      if (p.photoId != null && matchedIds.has(String(p.photoId))) {
        return
      }
      // 途经点属于轨迹，始终保留；照片/视频可按开关隐藏
      if (!isWaypointPoint(p) && !showMedia) {
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
    if (terrainMode.value && amap) {
      try { amap.resize?.() } catch (e) { /* ignore */ }
    } else {
      invalidateMapSize()
    }
  })
  resizeObserver.observe(mapEl.value)
  if (amapEl.value) resizeObserver.observe(amapEl.value)
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
    markerZoomAnimation: false,
    // 与 3D 手势对齐：启用旋转；关闭 Shift 框选/滚轮改向（改由自定义中键/Shift+左键）
    rotate: true,
    bearing: 0,
    touchRotate: false,
    shiftKeyRotate: false,
    boxZoom: false,
    // 关闭默认滚轮，改用与 3D 相同的细腻绕光标缩放
    scrollWheelZoom: false,
    // 必须允许浮点缩放，否则自定义 0.35 级会被 snap 掉，只剩平移
    zoomSnap: 0,
    zoomDelta: 0.35
  }).setView([35.0, 105.0], 4)
  L.control.zoom({ position: 'bottomleft' }).addTo(map)
  try {
    map.on('rotate', syncMapHeading)
  } catch (e) { /* ignore */ }
  bindLeafletViewControls()
  bindLeafletWheelZoom()
  syncMapHeading()
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
  if (!map.getPane('travelerPane')) {
    map.createPane('travelerPane')
    const travelerPane = map.getPane('travelerPane')
    travelerPane.style.zIndex = 580
    travelerPane.style.pointerEvents = 'none'
  }
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
  // Shift+左键留给「绕点旋转」（与 3D 一致），不启动框选
  if (e.originalEvent && e.originalEvent.shiftKey) return
  // 点在地图控件/侧栏上时不开始框选
  const t = e.originalEvent && e.originalEvent.target
  if (t && t.closest && (
    t.closest('.seg-panel')
    || t.closest('.map-toolbar-right')
    || t.closest('.map-style-switch')
    || t.closest('.pmc-mode-legend')
    || t.closest('.pmc-br-stack')
    || t.closest('.pmc-compass')
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
    props.selectedSegmentIndexes,
    props.showMediaMarkers,
    props.showTravelers,
    props.showModeLegend
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
  // 3D 只读视图下不做框选
  if (terrainMode.value) {
    syncBoxSelectMode(false)
    return
  }
  syncBoxSelectMode(!!val)
}, { immediate: true })

/** 3D 模式下数据变化时重绘只读覆盖物 */
watch(
  () => [
    props.points,
    props.showPolyline,
    props.polylinePoints,
    props.polylineColor,
    props.segmentByTravelMode,
    props.overlayPaths,
    props.visibleTravelModes,
    props.showMediaMarkers,
    props.regionGeoJson,
    props.showRegionHighlight
  ],
  () => {
    if (terrainMode.value && amap) renderTerrainOverlays()
  },
  { deep: true }
)

onMounted(() => {
  bindViewMenuOutsideClose()
  refresh({ fit: true })
  // 过渡动画结束后再校准一次尺寸
  setTimeout(() => ensureMapSized(0), 360)
})

onBeforeUnmount(() => {
  terrainEnterToken += 1
  closeViewMenu()
  unbindViewMenuOutsideClose()
  syncBoxSelectMode(false)
  stopTravelers()
  stopTerrainTraveler()
  destroyAmap()
  terrainMode.value = false
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
  unbindLeafletWheelZoom()
  unbindLeafletViewControls()
  if (map) {
    try { map.off('rotate', syncMapHeading) } catch (e) { /* ignore */ }
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
  mapHeading.value = 0
})

defineExpose({
  refresh,
  invalidateMapSize,
  terrainMode,
  exitTerrainMode,
  /** 将地图中心移到 WGS84 坐标（用于估计点预览） */
  focusWgs(lat, lng, zoom = 16) {
    if (lat == null || lng == null) return
    const z = Math.min(Math.max(Number(zoom) || 16, 3), 18)
    if (terrainMode.value && amap) {
      const [gcjLng, gcjLat] = wgs84ToGcj02(Number(lng), Number(lat))
      amap.setZoomAndCenter(z, [gcjLng, gcjLat])
      return
    }
    if (!map) return
    const ll = toMapLatLng({ latitude: lat, longitude: lng })
    const mapZ = Math.max(z, map.getZoom() || 3)
    map.setView(ll, Math.min(mapZ, 18), { animate: true })
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

.photo-cluster-map.is-terrain {
  /* 3D 只读：禁用编辑类光标暗示 */
  cursor: default;
}

.map-toolbar-left {
  position: absolute;
  z-index: 500;
  top: 10px;
  left: 10px;
  right: 320px;
  max-width: min(560px, calc(100% - 330px));
  pointer-events: none;
}

.map-toolbar-left > :deep(*) {
  pointer-events: auto;
}

.map-toolbar-right {
  position: absolute;
  z-index: 500;
  top: 10px;
  right: 10px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  pointer-events: none;
}

.map-toolbar-right > :deep(*) {
  pointer-events: auto;
}

.map-style-switch,
.map-view-switch {
  display: inline-flex;
  padding: 3px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.12);
  pointer-events: auto;
  gap: 2px;
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

.style-btn--view {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-width: 52px;
  justify-content: center;
}

.style-btn--terrain {
  font-weight: 600;
  letter-spacing: 0.02em;
}

.style-btn--terrain:disabled,
.style-btn--view:disabled {
  opacity: 0.55;
  cursor: wait;
}

.style-btn--terrain.active {
  background: #0f766e;
}

.view-caret {
  font-size: 10px;
  opacity: 0.75;
  transform: translateY(-0.5px);
}

.view-dropdown {
  position: relative;
}

.view-menu {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  min-width: 88px;
  padding: 4px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.14);
  z-index: 600;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.view-menu-item {
  border: 0;
  background: transparent;
  color: #606266;
  font-size: 13px;
  line-height: 1;
  padding: 8px 12px;
  border-radius: 6px;
  cursor: pointer;
  text-align: left;
  white-space: nowrap;
}

.view-menu-item:hover {
  background: rgba(0, 0, 0, 0.04);
  color: #303133;
}

.view-menu-item.active {
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

.map-canvas.is-hidden {
  visibility: hidden;
  pointer-events: none;
  z-index: -1;
}

.map-canvas--amap {
  z-index: 1;
}

.map-canvas--amap.is-hidden {
  z-index: -1;
}

/* 谷歌地球式绕点旋转十字准星 */
.ge-orbit-crosshair {
  position: absolute;
  z-index: 450;
  width: 28px;
  height: 28px;
  margin-left: -14px;
  margin-top: -14px;
  pointer-events: none;
  opacity: 0;
  visibility: hidden;
  transition: opacity 0.08s ease;
}

.ge-orbit-crosshair.is-on {
  opacity: 1;
  visibility: visible;
}

.ge-orbit-crosshair__h,
.ge-orbit-crosshair__v {
  position: absolute;
  background: rgba(255, 255, 255, 0.95);
  box-shadow: 0 0 0 1px rgba(0, 0, 0, 0.55);
}

.ge-orbit-crosshair__h {
  left: 0;
  top: 50%;
  width: 100%;
  height: 2px;
  margin-top: -1px;
}

.ge-orbit-crosshair__v {
  top: 0;
  left: 50%;
  width: 2px;
  height: 100%;
  margin-left: -1px;
}

.ge-orbit-crosshair__dot {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 6px;
  height: 6px;
  margin: -3px 0 0 -3px;
  border-radius: 50%;
  background: #ff3b30;
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.9);
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

.pmc-br-stack {
  position: absolute;
  z-index: 500;
  right: 12px;
  bottom: 12px;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
  max-width: calc(100% - 24px);
  pointer-events: none;
}

.pmc-compass {
  pointer-events: auto;
  width: 40px;
  height: 40px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.16);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.12s ease, box-shadow 0.12s ease;
}

.pmc-compass:hover {
  box-shadow: 0 3px 14px rgba(0, 0, 0, 0.22);
  transform: scale(1.04);
}

.pmc-compass:active {
  transform: scale(0.96);
}

.pmc-compass__dial {
  display: block;
  width: 32px;
  height: 32px;
  transition: transform 0.15s ease-out;
  will-change: transform;
}

.pmc-compass__dial svg {
  display: block;
  width: 100%;
  height: 100%;
}

.pmc-compass__ring {
  fill: #f5f7fa;
  stroke: #909399;
  stroke-width: 1.5;
}

.pmc-compass__north {
  fill: #e74c3c;
}

.pmc-compass__south {
  fill: #606266;
}

.pmc-compass__n {
  fill: #e74c3c;
  font-size: 7px;
  font-weight: 700;
  font-family: system-ui, sans-serif;
  dominant-baseline: middle;
}

.pmc-mode-legend {
  position: relative;
  z-index: 1;
  display: inline-flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  width: fit-content;
  max-width: 100%;
  padding: 5px 6px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.12);
  pointer-events: none;
  box-sizing: border-box;
}

.pmc-mode-legend :deep(button),
.pmc-mode-legend :deep(.legend-item),
.pmc-mode-legend :deep(.legend-reset) {
  pointer-events: auto;
}

.pmc-mode-legend-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  border-radius: 999px;
  color: #606266;
  font-size: 12px;
  line-height: 1;
  background: transparent;
  white-space: nowrap;
}

.pmc-mode-legend-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  color: #fff;
  flex-shrink: 0;
}

.pmc-mode-legend-icon :deep(svg) {
  display: block;
  fill: #fff;
}
</style>
