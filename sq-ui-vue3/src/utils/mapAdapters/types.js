/** 地图引擎：高德（Leaflet）/ 百度（BMap） */

export const MAP_PROVIDER_AMAP = 'amap'
export const MAP_PROVIDER_BAIDU = 'baidu'

export const AMAP_STYLES = [
  { key: 'normal', label: '标准' },
  { key: 'satellite', label: '卫星' },
  { key: 'hybrid', label: '混合' }
]

export const BAIDU_STYLES = [
  { key: 'normal', label: '标准' },
  { key: 'satellite', label: '卫星' },
  { key: 'panorama', label: '全景' }
]

const STORAGE_KEY = 'album.map.providerPrefs'

export function loadMapProviderPrefs() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const data = JSON.parse(raw)
    if (!data || typeof data !== 'object') return null
    return {
      provider: data.provider === MAP_PROVIDER_BAIDU ? MAP_PROVIDER_BAIDU : MAP_PROVIDER_AMAP,
      amapStyle: AMAP_STYLES.some(s => s.key === data.amapStyle) ? data.amapStyle : 'normal',
      baiduStyle: BAIDU_STYLES.some(s => s.key === data.baiduStyle) ? data.baiduStyle : 'normal'
    }
  } catch (_) {
    return null
  }
}

export function saveMapProviderPrefs(prefs) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({
      provider: prefs.provider,
      amapStyle: prefs.amapStyle,
      baiduStyle: prefs.baiduStyle
    }))
  } catch (_) { /* ignore */ }
}
