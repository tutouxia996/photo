/** 百度地图 JS API / 街景路网辅助 */

const PI = Math.PI
const A = 6378245.0
const EE = 0.00669342162296594323
const X_PI = (PI * 3000.0) / 180.0

function outOfChina(lng, lat) {
  return lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271
}

function transformLat(lng, lat) {
  let ret = -100.0 + 2.0 * lng + 3.0 * lat + 0.2 * lat * lat + 0.1 * lng * lat + 0.2 * Math.sqrt(Math.abs(lng))
  ret += ((20.0 * Math.sin(6.0 * lng * PI) + 20.0 * Math.sin(2.0 * lng * PI)) * 2.0) / 3.0
  ret += ((20.0 * Math.sin(lat * PI) + 40.0 * Math.sin((lat / 3.0) * PI)) * 2.0) / 3.0
  ret += ((160.0 * Math.sin((lat / 12.0) * PI) + 320.0 * Math.sin((lat * PI) / 30.0)) * 2.0) / 3.0
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

/** GCJ-02 → BD-09 */
export function gcj02ToBd09(lng, lat) {
  const x = Number(lng)
  const y = Number(lat)
  const z = Math.sqrt(x * x + y * y) + 0.00002 * Math.sin(y * X_PI)
  const theta = Math.atan2(y, x) + 0.000003 * Math.cos(x * X_PI)
  return [z * Math.cos(theta) + 0.0065, z * Math.sin(theta) + 0.006]
}

/** WGS84 → BD-09 */
export function wgs84ToBd09(lng, lat) {
  const [glng, glat] = wgs84ToGcj02(Number(lng), Number(lat))
  return gcj02ToBd09(glng, glat)
}

/** BD-09 → GCJ-02 */
export function bd09ToGcj02(bdLng, bdLat) {
  const x = Number(bdLng) - 0.0065
  const y = Number(bdLat) - 0.006
  const z = Math.sqrt(x * x + y * y) - 0.00002 * Math.sin(y * X_PI)
  const theta = Math.atan2(y, x) - 0.000003 * Math.cos(x * X_PI)
  return [z * Math.cos(theta), z * Math.sin(theta)]
}

/** BD-09 → WGS84（近似） */
export function bd09ToWgs84(bdLng, bdLat) {
  const [glng, glat] = bd09ToGcj02(bdLng, bdLat)
  if (outOfChina(glng, glat)) return [glng, glat]
  const [mgLng, mgLat] = wgs84ToGcj02(glng, glat)
  return [glng * 2 - mgLng, glat * 2 - mgLat]
}

/** GCJ [[lat,lng],...] 或 [{lat,lng},...] → BD-09 BMap.Point[] */
export function gcjPathToBdPoints(BMap, latlngs) {
  if (!BMap || !Array.isArray(latlngs)) return []
  const out = []
  for (const p of latlngs) {
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
    const [bdLng, bdLat] = gcj02ToBd09(lng, lat)
    out.push(new BMap.Point(bdLng, bdLat))
  }
  return out
}

export function pointToBd09(point) {
  if (!point || point.latitude == null || point.longitude == null) return null
  const [bdLng, bdLat] = wgs84ToBd09(point.longitude, point.latitude)
  if (Number.isNaN(bdLng) || Number.isNaN(bdLat)) return null
  return [bdLng, bdLat]
}

export function toAbsoluteUrl(url) {
  if (!url) return ''
  const s = String(url)
  if (/^https?:\/\//i.test(s) || s.startsWith('data:')) return s
  if (typeof window === 'undefined') return s
  if (s.startsWith('//')) return `${window.location.protocol}${s}`
  if (s.startsWith('/')) return `${window.location.origin}${s}`
  return `${window.location.origin}/${s}`
}

export function getBaiduMapAk() {
  return String(import.meta.env.VITE_BAIDU_MAP_AK || '').trim()
}

let loadPromise = null

/** 懒加载百度地图 JavaScript API 3.0（含全景路网 / Panorama） */
export function loadBaiduMapApi(ak = getBaiduMapAk()) {
  if (typeof window === 'undefined') {
    return Promise.reject(new Error('非浏览器环境'))
  }
  if (window.BMap && window.BMap.Map) {
    return Promise.resolve(window.BMap)
  }
  if (!ak) {
    return Promise.reject(new Error('未配置 VITE_BAIDU_MAP_AK'))
  }
  if (loadPromise) return loadPromise

  loadPromise = new Promise((resolve, reject) => {
    const callbackName = `__onBMapCallback_${Date.now()}`
    const cleanup = () => {
      try {
        delete window[callbackName]
      } catch (_) {
        window[callbackName] = undefined
      }
    }
    window[callbackName] = () => {
      cleanup()
      if (window.BMap) resolve(window.BMap)
      else {
        loadPromise = null
        reject(new Error('百度地图 API 加载异常'))
      }
    }
    const script = document.createElement('script')
    script.async = true
    script.src = `https://api.map.baidu.com/api?v=3.0&ak=${encodeURIComponent(ak)}&callback=${callbackName}`
    script.onerror = () => {
      cleanup()
      loadPromise = null
      reject(new Error('百度地图脚本加载失败'))
    }
    document.head.appendChild(script)
  })
  return loadPromise
}

/**
 * 在百度地图官网打开 BD-09 坐标。
 * @param {number} bdLng
 * @param {number} bdLat
 * @param {number} [zoom=19]
 */
export function openBaiduMapSite(bdLng, bdLat, zoom = 19) {
  const x = (Number(bdLng) * 20037508.34) / 180
  let y = Math.log(Math.tan(((90 + Number(bdLat)) * Math.PI) / 360)) / (Math.PI / 180)
  y = (y * 20037508.34) / 180
  const url = `https://map.baidu.com/@${Math.round(x)},${Math.round(y)},${zoom}z`
  window.open(url, '_blank', 'noopener,noreferrer')
}
