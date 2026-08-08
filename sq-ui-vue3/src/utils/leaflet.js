import L from 'leaflet'
import 'leaflet/dist/leaflet.css'

// 供 leaflet.markercluster 等依赖全局 L 的插件使用
if (typeof window !== 'undefined') {
  window.L = L
}

export default L
