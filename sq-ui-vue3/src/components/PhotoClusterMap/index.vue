<template>
  <div class="photo-cluster-map">
    <div class="map-toolbar">
      <div class="toolbar-left">
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
  toMapLatLng
} from '@/utils/photoMapCluster'

const props = defineProps({
  points: { type: Array, default: () => [] },
  /** 是否绘制轨迹折线（按点序） */
  showPolyline: { type: Boolean, default: false },
  polylineColor: { type: String, default: '#3B82F6' },
  emptyText: { type: String, default: '暂无带定位的照片' },
  fitMaxZoom: { type: Number, default: 17 }
})

const mapEl = ref(null)
const currentStyle = ref('normal')
const mapStyles = MAP_STYLES

let map = null
let baseLayer = null
let labelLayer = null
let lineLayer = null
let clusterGroup = null
let canvasRenderer = null

const validPoints = computed(() => filterValidPoints(props.points))
const hasPoints = computed(() => validPoints.value.length > 0)

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

function clearOverlays() {
  if (lineLayer) lineLayer.clearLayers()
  if (clusterGroup) clusterGroup.clearLayers()
}

function renderPoints() {
  if (!map || !clusterGroup) return
  clearOverlays()
  const list = validPoints.value
  if (!list.length) {
    map.setView([35.0, 105.0], 4)
    return
  }

  const latlngs = list.map(p => toMapLatLng(p))
  if (props.showPolyline && latlngs.length > 1) {
    L.polyline(latlngs, {
      renderer: canvasRenderer,
      color: props.polylineColor || '#3B82F6',
      weight: 4,
      opacity: 0.85,
      lineJoin: 'round',
      smoothFactor: 1.2
    }).addTo(lineLayer)
  }

  const markers = list.map(p => createPhotoMarker(p))
  clusterGroup.addLayers(markers)

  map.fitBounds(L.latLngBounds(latlngs), {
    padding: [48, 48],
    maxZoom: props.fitMaxZoom
  })
  nextTick(() => map?.invalidateSize())
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
  applyBaseLayers(currentStyle.value)
  lineLayer = L.layerGroup().addTo(map)
  clusterGroup = createClusterGroup().addTo(map)
  map.on('zoomstart', onZoomStart)
  map.on('zoomend', onZoomEnd)
}

function refresh() {
  nextTick(() => {
    if (!map) initMap()
    renderPoints()
    setTimeout(() => map?.invalidateSize(), 200)
  })
}

watch(() => [props.points, props.showPolyline, props.polylineColor], () => refresh(), { deep: true })

onMounted(() => refresh())

onBeforeUnmount(() => {
  if (map) {
    map.off('zoomstart', onZoomStart)
    map.off('zoomend', onZoomEnd)
    map.remove()
    map = null
    baseLayer = null
    labelLayer = null
    lineLayer = null
    clusterGroup = null
    canvasRenderer = null
  }
})

defineExpose({ refresh })
</script>

<style scoped>
.photo-cluster-map {
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

.toolbar-left {
  min-width: 0;
  pointer-events: auto;
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
