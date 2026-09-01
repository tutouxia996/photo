/**
 * 高德底图（Leaflet + 公开瓦片）辅助。
 * 详细绘制/交互仍由 PhotoClusterMap 内 Leaflet 路径执行，保证零回归。
 */
import L from '@/utils/leaflet'
import { STYLE_LAYERS } from '@/utils/photoMapCluster'

export { STYLE_LAYERS }

export function applyAmapBaseLayers(map, styleKey, layersRef) {
  if (!map) return layersRef
  const conf = STYLE_LAYERS[styleKey] || STYLE_LAYERS.normal
  if (layersRef.baseLayer) {
    map.removeLayer(layersRef.baseLayer)
    layersRef.baseLayer = null
  }
  if (layersRef.labelLayer) {
    map.removeLayer(layersRef.labelLayer)
    layersRef.labelLayer = null
  }
  layersRef.baseLayer = L.tileLayer(conf.url, conf.options).addTo(map)
  if (conf.labelUrl) {
    layersRef.labelLayer = L.tileLayer(conf.labelUrl, conf.labelOptions || conf.options).addTo(map)
  }
  return layersRef
}

export function createLeafletMap(container, options = {}) {
  return L.map(container, {
    zoomControl: true,
    attributionControl: true,
    maxZoom: 22,
    ...options
  })
}
