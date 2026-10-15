/**
 * 在 window.L 就绪后再打补丁（须先 import @/utils/leaflet）。
 * leaflet-rotate 无 ESM 干净导出，依赖全局 L。
 */
import L from '@/utils/leaflet'
import 'leaflet-rotate/dist/leaflet-rotate-src.js'

export default L
