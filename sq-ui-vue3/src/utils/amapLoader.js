import { getMapJsConfig } from '@/api/album/map'

let loadPromise = null

/**
 * 动态加载高德 JS API v2.1Beta（3D 地形图需要）。
 * 单例 Promise，避免重复注入 script。
 */
export function loadAmap() {
  if (typeof window !== 'undefined' && window.AMap) {
    return Promise.resolve(window.AMap)
  }
  if (loadPromise) return loadPromise

  loadPromise = getMapJsConfig()
    .then((res) => {
      const data = res?.data ?? res ?? {}
      const jsKey = String(data.jsKey || '').trim()
      const securityJsCode = String(data.securityJsCode || '').trim()
      if (!jsKey) {
        throw new Error('未配置高德 JS Key（AMAP_JS_KEY），无法开启 3D 地形')
      }
      if (securityJsCode) {
        window._AMapSecurityConfig = {
          ...(window._AMapSecurityConfig || {}),
          securityJsCode
        }
      }
      return injectAmapScript(jsKey)
    })
    .catch((err) => {
      loadPromise = null
      throw err
    })

  return loadPromise
}

function injectAmapScript(jsKey) {
  return new Promise((resolve, reject) => {
    if (window.AMap) {
      resolve(window.AMap)
      return
    }
    const existing = document.querySelector('script[data-amap-jsapi="1"]')
    if (existing) {
      existing.addEventListener('load', () => {
        if (window.AMap) resolve(window.AMap)
        else reject(new Error('高德 JS API 加载失败'))
      })
      existing.addEventListener('error', () => reject(new Error('高德 JS API 脚本加载失败')))
      return
    }
    const script = document.createElement('script')
    script.type = 'text/javascript'
    script.async = true
    script.dataset.amapJsapi = '1'
    // 3D 地形图要求 v2.1Beta
    script.src = `https://webapi.amap.com/maps?v=2.1Beta&key=${encodeURIComponent(jsKey)}`
    script.onload = () => {
      if (window.AMap) resolve(window.AMap)
      else reject(new Error('高德 JS API 加载失败'))
    }
    script.onerror = () => reject(new Error('高德 JS API 脚本加载失败'))
    document.head.appendChild(script)
  })
}
