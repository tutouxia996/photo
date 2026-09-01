/**
 * 百度地图引擎：标准 / 卫星 / 全景 + 点线展示与基础交互
 */
import {
  bd09ToGcj02,
  bd09ToWgs84,
  gcj02ToBd09,
  gcjPathToBdPoints,
  getBaiduMapAk,
  loadBaiduMapApi,
  pointToBd09,
  toAbsoluteUrl,
  wgs84ToBd09
} from '@/utils/baiduMap'
import {
  cleanRailDisplayPath,
  escapeHtml,
  buildPopupHtml,
  clusterRadiusForZoom,
  estimatedSourceLabel,
  estimatedSourceTitle,
  formatCount,
  isConfirmedEstimated,
  isEstimatedLocation,
  isRailTravelMode,
  isWaypointPoint,
  mediaSrc,
  segmentLatLngs,
  toMapLatLng,
  travelModeColor,
  travelModeLabel
} from '@/utils/photoMapCluster'

/** 与高德 createThumbDivIcon 同结构：封面缩略图 + 数量角标 / 估计角标 */
function buildThumbHtml(point, count = 1, options = {}) {
  const isVideo = Number(point?.fileType) === 2
  const estimated = count <= 1 && isEstimatedLocation(point)
  const selected = !!(options.selected && count <= 1)
  const thumb = toAbsoluteUrl(mediaSrc(point, false))
  const badge = count > 1
    ? `<span class="pmc-badge">${formatCount(count)}</span>`
    : (estimated
      ? `<span class="pmc-est-badge pmc-est-badge--${point?.locationSource || 'time_interp'}${isConfirmedEstimated(point) ? ' pmc-est-badge--confirmed' : ''}" title="${escapeHtml(estimatedSourceTitle(point))}">${estimatedSourceLabel(point)}</span>`
      : '')
  const pick = selected ? '<span class="pmc-ai-pick" title="已选入 AI 识别">✓</span>' : ''
  const video = isVideo && count <= 1 ? '<span class="pmc-video">▶</span>' : ''
  const fallbackText = isWaypointPoint(point) ? '点' : (isVideo ? '视频' : '图')
  const img = thumb
    ? `<img src="${escapeHtml(thumb)}" decoding="async" alt="" onerror="this.onerror=null;this.replaceWith(Object.assign(document.createElement('span'),{className:'pmc-fallback',textContent:'${fallbackText}'}))" />`
    : `<span class="pmc-fallback">${fallbackText}</span>`
  return `<div class="pmc-wrap"><div class="pmc-pin">${img}${video}</div>${badge}${pick}</div>`
}

function createThumbOverlayClass(BMap) {
  function ThumbOverlay(point, photoPoint, handlers = {}) {
    this._point = point
    this._photoPoint = photoPoint
    this._handlers = handlers
    this._count = handlers.count || 1
    this._dragging = false
  }
  ThumbOverlay.prototype = new BMap.Overlay()
  ThumbOverlay.prototype.initialize = function initialize(mapInstance) {
    this._map = mapInstance
    const div = document.createElement('div')
    const estimated = this._count <= 1 && isEstimatedLocation(this._photoPoint)
    div.className = [
      'baidu-pmc-marker',
      estimated ? 'pmc-marker--estimated' : '',
      this._handlers.selected ? 'pmc-marker--ai-selected' : '',
      this._count > 1 ? 'is-cluster' : ''
    ].filter(Boolean).join(' ')
    div.innerHTML = buildThumbHtml(this._photoPoint, this._count, {
      selected: !!this._handlers.selected
    })
    div.style.position = 'absolute'
    div.style.zIndex = String(this._handlers.zIndex || (this._count > 1 ? 640 : 620))
    div.style.cursor = this._handlers.draggable ? 'grab' : 'pointer'
    const countTip = this._count > 1 ? `（${this._count}）` : ''
    div.title = String(
      this._photoPoint?.title
      || this._photoPoint?.fileName
      || (Number(this._photoPoint?.fileType) === 2 ? '视频' : '照片')
    ) + countTip
    const onClick = (e) => {
      e.stopPropagation()
      if (this._dragging) return
      this._handlers.onClick?.(this._photoPoint, this._point, this._handlers)
    }
    div.addEventListener('click', onClick)
    if (this._handlers.draggable) {
      let startX = 0
      let startY = 0
      const onDown = (e) => {
        e.stopPropagation()
        this._dragging = false
        startX = e.clientX
        startY = e.clientY
        div.style.cursor = 'grabbing'
        const onMove = (ev) => {
          const dx = Math.abs(ev.clientX - startX)
          const dy = Math.abs(ev.clientY - startY)
          if (dx + dy > 4) this._dragging = true
          if (!this._dragging) return
          const rect = mapInstance.getContainer().getBoundingClientRect()
          const local = new BMap.Pixel(ev.clientX - rect.left, ev.clientY - rect.top)
          const pt = mapInstance.pixelToPoint(local)
          this._point = pt
          this.draw()
        }
        const onUp = () => {
          document.removeEventListener('mousemove', onMove)
          document.removeEventListener('mouseup', onUp)
          div.style.cursor = 'grab'
          if (this._dragging) {
            this._handlers.onDragEnd?.(this._photoPoint, this._point)
          }
          setTimeout(() => { this._dragging = false }, 0)
        }
        document.addEventListener('mousemove', onMove)
        document.addEventListener('mouseup', onUp)
      }
      div.addEventListener('mousedown', onDown)
    }
    mapInstance.getPanes().markerPane.appendChild(div)
    this._div = div
    return div
  }
  ThumbOverlay.prototype.draw = function draw() {
    if (!this._map || !this._div) return
    const pixel = this._map.pointToOverlayPixel(this._point)
    this._div.style.left = `${pixel.x - 26}px`
    this._div.style.top = `${pixel.y - 52}px`
  }
  ThumbOverlay.prototype.setPosition = function setPosition(point) {
    this._point = point
    this.draw()
  }
  return ThumbOverlay
}

/** 百度流动虚线（SVG + CSS，对齐 Leaflet pmc-flow-line） */
function createFlowLineOverlayClass(BMap) {
  function FlowLineOverlay(points, color) {
    this._points = points || []
    this._color = color || '#2563eb'
  }
  FlowLineOverlay.prototype = new BMap.Overlay()
  FlowLineOverlay.prototype.initialize = function initialize(mapInstance) {
    this._map = mapInstance
    const root = document.createElement('div')
    root.className = 'baidu-flow-root'
    root.style.cssText = 'position:absolute;left:0;top:0;width:0;height:0;overflow:visible;pointer-events:none;'
    const ns = 'http://www.w3.org/2000/svg'
    const svg = document.createElementNS(ns, 'svg')
    svg.setAttribute('class', 'baidu-flow-svg')
    svg.style.cssText = 'overflow:visible;position:absolute;left:0;top:0;'
    const makePath = (className, stroke, width, opacity) => {
      const path = document.createElementNS(ns, 'path')
      path.setAttribute('class', className)
      path.setAttribute('fill', 'none')
      path.setAttribute('stroke', stroke)
      path.setAttribute('stroke-width', String(width))
      path.setAttribute('stroke-opacity', String(opacity))
      path.setAttribute('stroke-linecap', 'round')
      path.setAttribute('stroke-linejoin', 'round')
      path.setAttribute('stroke-dasharray', '10 22')
      svg.appendChild(path)
      return path
    }
    this._white = makePath('pmc-flow-line', '#ffffff', 3, 0.92)
    this._accent = makePath('pmc-flow-line pmc-flow-line--accent', this._color, 2, 0.85)
    root.appendChild(svg)
    this._div = root
    const pane = mapInstance.getPanes().floatPane || mapInstance.getPanes().markerPane
    pane.appendChild(root)
    return root
  }
  FlowLineOverlay.prototype.draw = function draw() {
    if (!this._map || !this._white || !this._accent) return
    const pts = this._points
    if (!pts || pts.length < 2) {
      this._white.setAttribute('d', '')
      this._accent.setAttribute('d', '')
      return
    }
    let d = ''
    for (let i = 0; i < pts.length; i++) {
      const px = this._map.pointToOverlayPixel(pts[i])
      d += `${i === 0 ? 'M' : 'L'}${px.x} ${px.y}`
    }
    this._white.setAttribute('d', d)
    this._accent.setAttribute('d', d)
  }
  FlowLineOverlay.prototype.setPath = function setPath(points) {
    this._points = points || []
    this.draw()
  }
  return FlowLineOverlay
}

/** 像素距离贪心聚合（对齐 leaflet.markercluster 观感） */
function clusterByPixel(map, items, radiusPx) {
  if (!map || !items.length) return []
  const enriched = items.map((it) => {
    const px = map.pointToOverlayPixel(it.bdPoint)
    return { ...it, x: px.x, y: px.y }
  })
  const used = new Uint8Array(enriched.length)
  const clusters = []
  const r2 = radiusPx * radiusPx
  for (let i = 0; i < enriched.length; i++) {
    if (used[i]) continue
    const group = [enriched[i]]
    used[i] = 1
    for (let j = i + 1; j < enriched.length; j++) {
      if (used[j]) continue
      const dx = enriched[i].x - enriched[j].x
      const dy = enriched[i].y - enriched[j].y
      if (dx * dx + dy * dy <= r2) {
        group.push(enriched[j])
        used[j] = 1
      }
    }
    clusters.push(group)
  }
  return clusters
}

function clusterKeyOf(group) {
  return group
    .map(it => (it.photo?.photoId != null ? `p:${it.photo.photoId}` : `i:${it.index}`))
    .sort()
    .join('|')
}

function pickCoverPhoto(group) {
  for (const it of group) {
    if (it.photo && !isWaypointPoint(it.photo)) return it.photo
  }
  return group[0]?.photo || null
}

function groupCenterPoint(BMap, group) {
  let lng = 0
  let lat = 0
  for (const it of group) {
    lng += it.bdPoint.lng
    lat += it.bdPoint.lat
  }
  const n = group.length || 1
  return new BMap.Point(lng / n, lat / n)
}

function resolveSegmentLatLngs(from, to) {
  const raw = segmentLatLngs(from, to)
  if (!raw || raw.length < 2) return raw
  if (isRailTravelMode(from?.travelMode)) return cleanRailDisplayPath(raw)
  return raw
}

/** GPX / 预览折线统一为 GCJ [[lat,lng],...] */
function normalizeGcjPath(path) {
  if (!Array.isArray(path) || path.length < 2) return []
  const out = []
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
    out.push([lat, lng])
  }
  return out
}

function absolutizePopupHtml(html) {
  if (!html) return html
  return String(html).replace(/\b(src|poster)="(\/[^"]*)"/gi, (_, attr, url) => {
    return `${attr}="${toAbsoluteUrl(url)}"`
  })
}

function regionStyle(feature) {
  const level = String(feature?.properties?.level || '').toLowerCase()
  if (level === 'district') {
    return { strokeWeight: 1, strokeColor: '#2563EB', strokeOpacity: 0.85, fillColor: '#3B82F6', fillOpacity: 0.28 }
  }
  if (level === 'city') {
    return { strokeWeight: 1, strokeColor: '#3B82F6', strokeOpacity: 0.7, fillColor: '#60A5FA', fillOpacity: 0.18 }
  }
  return { strokeWeight: 1, strokeColor: '#93C5FD', strokeOpacity: 0.55, fillColor: '#BFDBFE', fillOpacity: 0.1 }
}

function ringToBdPoints(BMap, ring) {
  const pts = []
  if (!Array.isArray(ring)) return pts
  for (const c of ring) {
    if (!Array.isArray(c) || c.length < 2) continue
    // GeoJSON 为 [lng,lat]，足迹数据为 GCJ
    const [bdLng, bdLat] = gcj02ToBd09(Number(c[0]), Number(c[1]))
    pts.push(new BMap.Point(bdLng, bdLat))
  }
  return pts
}

export class BaiduMapEngine {
  constructor() {
    this.map = null
    this.BMap = null
    this.containerId = ''
    this.styleKey = 'normal'
    this.coverageLayer = null
    this.panoramaControl = null
    this.panorama = null
    this._panoObserver = null
    this._panoWatchTimer = null
    this.navControl = null
    this.overlays = []
    this.photoOverlays = []
    this.segmentRefs = []
    this.pathEditMarkers = []
    this.boxRect = null
    this.boxStart = null
    this.boxDragging = false
    this.boxHandlers = null
    this.didFit = false
    this.ThumbOverlay = null
    this.FlowLineOverlay = null
    this.callbacks = {}
    this._lastScene = null
    this._spiderfyKey = null
    this._suppressMapClick = false
    this._zoomHandler = null
    this._mapClickHandler = null
  }

  async init(containerId, options = {}) {
    this.containerId = containerId
    this.callbacks = options.callbacks || {}
    const ak = getBaiduMapAk()
    if (!ak) throw new Error('未配置 VITE_BAIDU_MAP_AK')
    this.BMap = await loadBaiduMapApi(ak)
    const el = document.getElementById(containerId)
    if (!el) throw new Error('百度地图容器不存在')
    el.innerHTML = ''
    const BMap = this.BMap
    this.ThumbOverlay = createThumbOverlayClass(BMap)
    this.FlowLineOverlay = createFlowLineOverlayClass(BMap)
    const center = options.centerBd || [116.404, 39.915]
    const zoom = options.zoom || 12
    this.map = new BMap.Map(containerId, { enableMapClick: true })
    this.map.enableScrollWheelZoom(true)
    this.map.centerAndZoom(new BMap.Point(center[0], center[1]), zoom)
    this.navControl = new BMap.NavigationControl({
      anchor: typeof BMAP_ANCHOR_BOTTOM_RIGHT !== 'undefined' ? BMAP_ANCHOR_BOTTOM_RIGHT : undefined,
      type: typeof BMAP_NAVIGATION_CONTROL_ZOOM !== 'undefined' ? BMAP_NAVIGATION_CONTROL_ZOOM : undefined,
      // 高于出行方式图例与全景入口按钮
      offset: new BMap.Size(18, 200)
    })
    this.map.addControl(this.navControl)
    this._zoomHandler = () => {
      // 缩放后重新聚合；蜘蛛腿收起
      this._spiderfyKey = null
      if (this._lastScene) this._drawPhotoMarkers(this._lastScene)
    }
    this._mapClickHandler = () => {
      if (this._suppressMapClick) return
      if (!this._spiderfyKey) return
      this._spiderfyKey = null
      if (this._lastScene) this._drawPhotoMarkers(this._lastScene)
    }
    this.map.addEventListener('zoomend', this._zoomHandler)
    this.map.addEventListener('click', this._mapClickHandler)
    this.setBasemap(options.styleKey || 'normal')
    this.didFit = false
    return this.map
  }

  destroy() {
    this.clearBoxSelect()
    this.clearOverlays()
    try {
      if (this.map && this._zoomHandler) this.map.removeEventListener('zoomend', this._zoomHandler)
    } catch (_) { /* ignore */ }
    try {
      if (this.map && this._mapClickHandler) this.map.removeEventListener('click', this._mapClickHandler)
    } catch (_) { /* ignore */ }
    try {
      if (this.map && this.coverageLayer) this.map.removeTileLayer(this.coverageLayer)
    } catch (_) { /* ignore */ }
    try {
      if (this.map && this.panoramaControl) this.map.removeControl(this.panoramaControl)
    } catch (_) { /* ignore */ }
    this.closePanorama()
    this._unbindNativePanoramaWatch()
    try {
      if (this.map && this.navControl) this.map.removeControl(this.navControl)
    } catch (_) { /* ignore */ }
    this.coverageLayer = null
    this.panoramaControl = null
    this.navControl = null
    this._zoomHandler = null
    this._mapClickHandler = null
    this._lastScene = null
    this._spiderfyKey = null
    this.photoOverlays = []
    this.map = null
    this.BMap = null
    this.ThumbOverlay = null
    this.FlowLineOverlay = null
    this.didFit = false
    const el = document.getElementById(this.containerId)
    if (el) el.innerHTML = ''
  }

  checkResize() {
    try {
      this.map?.checkResize?.()
    } catch (_) { /* ignore */ }
  }

  getCenterBd() {
    if (!this.map) return null
    const c = this.map.getCenter()
    if (!c) return null
    return [c.lng, c.lat]
  }

  getZoom() {
    return this.map?.getZoom?.() || 12
  }

  setViewBd(lng, lat, zoom) {
    if (!this.map || !this.BMap) return
    this.map.centerAndZoom(new this.BMap.Point(lng, lat), zoom || this.getZoom())
  }

  focusWgs(lat, lng, zoom = 16) {
    const [bdLng, bdLat] = wgs84ToBd09(lng, lat)
    this.setViewBd(bdLng, bdLat, Math.min(Math.max(Number(zoom) || 16, 3), 19))
  }

  setBasemap(styleKey) {
    if (!this.map || !this.BMap) return
    this.styleKey = styleKey
    const BMap = this.BMap
    // 切换底图时退出街景与选点
    if (styleKey !== 'panorama') {
      this._unbindNativePanoramaWatch()
      this.closePanorama()
    }
    // 退出全景覆盖层（若从全景切回）
    try {
      if (this.coverageLayer) {
        this.map.removeTileLayer(this.coverageLayer)
        this.coverageLayer = null
      }
    } catch (_) { /* ignore */ }
    try {
      if (this.panoramaControl) {
        this.map.removeControl(this.panoramaControl)
        this.panoramaControl = null
      }
    } catch (_) { /* ignore */ }

    if (styleKey === 'satellite') {
      if (typeof BMAP_SATELLITE_MAP !== 'undefined') {
        this.map.setMapType(BMAP_SATELLITE_MAP)
      } else if (typeof BMAP_HYBRID_MAP !== 'undefined') {
        this.map.setMapType(BMAP_HYBRID_MAP)
      }
      return
    }

    if (styleKey === 'panorama') {
      if (typeof BMAP_NORMAL_MAP !== 'undefined') {
        this.map.setMapType(BMAP_NORMAL_MAP)
      }
      try {
        if (typeof BMap.PanoramaCoverageLayer === 'function') {
          this.coverageLayer = new BMap.PanoramaCoverageLayer()
          this.map.addTileLayer(this.coverageLayer)
        }
      } catch (e) {
        console.warn('[BaiduMapEngine] PanoramaCoverageLayer failed', e)
      }
      try {
        this.panoramaControl = new BMap.PanoramaControl()
        if (typeof BMAP_ANCHOR_BOTTOM_RIGHT !== 'undefined') {
          this.panoramaControl.setAnchor(BMAP_ANCHOR_BOTTOM_RIGHT)
        }
        // 上移避开右下角出行方式图例
        this.panoramaControl.setOffset(new BMap.Size(18, 128))
        this.map.addControl(this.panoramaControl)
        this._bindNativePanoramaWatch()
        setTimeout(() => this.checkResize(), 80)
      } catch (e) {
        console.warn('[BaiduMapEngine] PanoramaControl failed', e)
      }
      return
    }

    // normal
    if (typeof BMAP_NORMAL_MAP !== 'undefined') {
      this.map.setMapType(BMAP_NORMAL_MAP)
    }
  }

  /** 监听百度原生街景层出现/关闭，便于 UI 避开关闭按钮 */
  _bindNativePanoramaWatch() {
    this._unbindNativePanoramaWatch()
    const root = this.map?.getContainer?.()
    if (!root || typeof MutationObserver === 'undefined') return
    const hosts = [root, root.parentElement, document.body].filter(Boolean)
    const sync = () => {
      const open = hosts.some(h => this._detectNativePanoramaOpen(h))
      this.callbacks.onNativePanoramaChange?.(open)
    }
    this._panoObserver = new MutationObserver(() => {
      if (this._panoWatchTimer) clearTimeout(this._panoWatchTimer)
      this._panoWatchTimer = setTimeout(sync, 40)
    })
    for (const host of hosts) {
      this._panoObserver.observe(host, {
        childList: true,
        subtree: true,
        attributes: true,
        attributeFilter: ['style', 'class']
      })
    }
    sync()
  }

  _unbindNativePanoramaWatch() {
    if (this._panoWatchTimer) {
      clearTimeout(this._panoWatchTimer)
      this._panoWatchTimer = null
    }
    try {
      this._panoObserver?.disconnect?.()
    } catch (_) { /* ignore */ }
    this._panoObserver = null
    this.callbacks.onNativePanoramaChange?.(false)
  }

  _detectNativePanoramaOpen(root) {
    if (!root) return false
    // 仅认「已进入街景」的关闭/主视图，避开右下角 PanoramaControl / 路网层误判
    const closeBtn = root.querySelector(
      '.pano_close, .BMap_pano_close, .pano-close, [class*="pano_close"], [class*="PanoClose"]'
    )
    if (closeBtn) {
      const style = window.getComputedStyle?.(closeBtn)
      if (!style || (style.display !== 'none' && style.visibility !== 'hidden' && style.opacity !== '0')) {
        return true
      }
    }
    const stage = root.querySelector('.pano_outer, .BMap_pano_box, #panoFlashContent')
    if (!stage) return false
    const style = window.getComputedStyle?.(stage)
    if (!style || style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0') {
      return false
    }
    const rect = stage.getBoundingClientRect?.()
    return !!(rect && rect.width > 80 && rect.height > 80)
  }

  /** 仅用于切换底图/销毁时尽量关掉原生街景 */
  closePanorama() {
    try {
      const root = this.map?.getContainer?.()
      const btn = root?.querySelector(
        '.pano_close, .BMap_pano_close, .pano-close, [class*="pano_close"], [class*="PanoClose"]'
      )
      if (btn && typeof btn.click === 'function') btn.click()
    } catch (_) { /* ignore */ }
    this.panorama = null
    this.callbacks.onNativePanoramaChange?.(false)
  }

  _openHtmlInfo(html, point) {
    if (!this.map || !this.BMap || !point) return
    try {
      const info = new this.BMap.InfoWindow(absolutizePopupHtml(html), {
        width: 320,
        maxWidth: 340,
        enableMessage: false
      })
      this.map.openInfoWindow(info, point)
    } catch (e) {
      console.warn('[BaiduMapEngine] InfoWindow failed', e)
    }
  }

  clearOverlays() {
    this.clearPathEditMarkers()
    this.segmentRefs = []
    this.photoOverlays = []
    this._spiderfyKey = null
    if (!this.map) {
      this.overlays = []
      return
    }
    try {
      this.map.clearOverlays()
    } catch (_) { /* ignore */ }
    this.overlays = []
  }

  clearPhotoOverlays() {
    if (!this.map) {
      this.photoOverlays = []
      return
    }
    for (const o of this.photoOverlays) {
      try {
        this.map.removeOverlay(o)
      } catch (_) { /* ignore */ }
    }
    this.photoOverlays = []
  }

  _trackPhoto(overlay) {
    if (!overlay) return overlay
    this.photoOverlays.push(overlay)
    return this._track(overlay)
  }

  clearPathEditMarkers() {
    if (!this.map) {
      this.pathEditMarkers = []
      return
    }
    for (const m of this.pathEditMarkers) {
      try {
        this.map.removeOverlay(m)
      } catch (_) { /* ignore */ }
    }
    this.pathEditMarkers = []
  }

  _track(overlay) {
    if (overlay) this.overlays.push(overlay)
    return overlay
  }

  _addPolyline(latlngsGcj, color, weight = 5, dashed = false) {
    const BMap = this.BMap
    const pts = gcjPathToBdPoints(BMap, latlngsGcj)
    if (pts.length < 2) return null
    const poly = new BMap.Polyline(pts, {
      strokeColor: color || '#3B82F6',
      strokeWeight: weight,
      strokeOpacity: 0.92,
      strokeStyle: dashed ? 'dashed' : 'solid',
      enableClicking: true
    })
    this.map.addOverlay(poly)
    this._track(poly)
    return { poly, pts }
  }

  /** 叠加流动虚线层（仅实线/已贴合或 GPX） */
  _addFlowOverlay(pts, color) {
    if (!this.map || !this.FlowLineOverlay || !pts || pts.length < 2) return null
    try {
      const flow = new this.FlowLineOverlay(pts, color || '#2563eb')
      this.map.addOverlay(flow)
      this._track(flow)
      return flow
    } catch (e) {
      console.warn('[BaiduMapEngine] flow overlay failed', e)
      return null
    }
  }

  /**
   * @param {object} scene
   * @param {{ fit?: boolean }} options
   */
  render(scene, options = {}) {
    if (!this.map || !this.BMap) return
    const fit = !!options.fit
    this.clearOverlays()
    const BMap = this.BMap
    const viewport = []
    const defaultColor = scene.polylineColor || '#3B82F6'
    const modeFilter = scene.visibleTravelModes
    const modeSet = Array.isArray(modeFilter) && modeFilter.length
      ? new Set(modeFilter.map(m => String(m).toLowerCase()))
      : null
    const isModeVisible = (mode) => {
      if (!modeSet) return true
      return modeSet.has(String(mode || '').toLowerCase())
    }

    // 行政区
    if (scene.showRegionHighlight && scene.regionGeoJson?.features?.length) {
      for (const feature of scene.regionGeoJson.features) {
        const geom = feature?.geometry
        if (!geom) continue
        const style = regionStyle(feature)
        const polys = []
        if (geom.type === 'Polygon') polys.push(geom.coordinates)
        else if (geom.type === 'MultiPolygon') polys.push(...(geom.coordinates || []))
        for (const coords of polys) {
          const outer = ringToBdPoints(BMap, coords?.[0])
          if (outer.length < 3) continue
          const polygon = new BMap.Polygon(outer, {
            strokeColor: style.strokeColor,
            strokeWeight: style.strokeWeight,
            strokeOpacity: style.strokeOpacity,
            fillColor: style.fillColor,
            fillOpacity: style.fillOpacity,
            enableClicking: false
          })
          this.map.addOverlay(polygon)
          this._track(polygon)
          viewport.push(...outer)
        }
      }
    }

    const lineList = Array.isArray(scene.polylinePoints) ? scene.polylinePoints : []
    const matchedIds = scene.gpxMatchedIds instanceof Set
      ? scene.gpxMatchedIds
      : new Set()
    this.segmentRefs = []
    if (scene.showPolyline && lineList.length > 1) {
      let drawn = 0
      for (let i = 0; i < lineList.length - 1; i++) {
        const from = lineList[i]
        const to = lineList[i + 1]
        const fromMatched = from?.photoId != null && matchedIds.has(String(from.photoId))
        const toMatched = to?.photoId != null && matchedIds.has(String(to.photoId))
        if (fromMatched || toMatched) {
          this.segmentRefs[i] = null
          continue
        }
        if (!isModeVisible(from?.travelMode)) {
          this.segmentRefs[i] = null
          continue
        }
        const latlngs = resolveSegmentLatLngs(from, to)
        if (!latlngs || latlngs.length < 2) {
          this.segmentRefs[i] = null
          continue
        }
        const color = scene.segmentByTravelMode
          ? travelModeColor(from.travelMode, defaultColor)
          : defaultColor
        const active = scene.activeSegmentIndex === i
          || scene.pathEditIndex === i
          || (Array.isArray(scene.selectedSegmentIndexes) && scene.selectedSegmentIndexes.includes(i))
        const dashed = !from.routePath
        const lineColor = active ? '#F59E0B' : color
        const added = this._addPolyline(latlngs, lineColor, active ? 7 : 6, dashed)
        if (!added) {
          this.segmentRefs[i] = null
          continue
        }
        drawn += 1
        viewport.push(...added.pts)
        // 与高德一致：仅已贴合路网的实线段叠加流动方向
        if (!dashed) {
          this._addFlowOverlay(added.pts, lineColor)
        }
        const segIndex = i
        if (scene.editable) {
          added.poly.addEventListener('click', () => {
            this.callbacks.onSegmentClick?.({
              index: segIndex,
              fromPoint: from,
              toPoint: to
            })
          })
        }
        this.segmentRefs[i] = { poly: added.poly, latlngs, from, to }
      }
      if (!drawn) {
        const bridgeGcj = lineList
          .map(p => toMapLatLng(p))
          .filter(Boolean)
        const added = this._addPolyline(bridgeGcj, defaultColor, 5, true)
        if (added) viewport.push(...added.pts)
      }
    }

    // GPX（path 为 GCJ：[[lat,lng]] 或 [{lat,lng}]）
    const overlays = Array.isArray(scene.overlayPaths) ? scene.overlayPaths : []
    for (const o of overlays) {
      if (!o || o.showPath === false) continue
      // 与 Leaflet isGpxItemVisible 对齐：筛选时仍可用 gpx 通配
      if (modeSet) {
        const mode = String(o.travelMode || '').toLowerCase()
        if (!modeSet.has('gpx') && !(mode && modeSet.has(mode))) continue
      }
      let path = normalizeGcjPath(o.path)
      if (path.length < 2) continue
      if (isRailTravelMode(o.travelMode)) {
        path = cleanRailDisplayPath(path, { minMeters: 35 })
        if (!path || path.length < 2) continue
      }
      const color = travelModeColor(o.travelMode, o.color || '#10B981')
      const added = this._addPolyline(path, color, 5, false)
      if (added) {
        viewport.push(...added.pts)
        this._addFlowOverlay(added.pts, color)
        // 加宽透明命中线，便于点击（对齐 Leaflet hit）
        const hit = this._addPolyline(path, color, 16, false)
        if (hit) {
          try {
            hit.poly.setStrokeOpacity(0.01)
          } catch (_) { /* ignore */ }
        }
        const onGpxLineClick = (e) => {
          const latlng = e?.point ? { lat: e.point.lat, lng: e.point.lng } : null
          this.callbacks.onGpxClick?.({
            overlay: o,
            latlng
          })
          if (!scene.gpxEndpointPickable && e?.point) {
            const modeText = travelModeLabel(o.travelMode) || o.travelMode || 'GPX'
            this._openHtmlInfo(
              `<div class="pmc-popup">`
                + `<div class="pmc-title">GPX：${escapeHtml(o.fileName || '')}</div>`
                + `<div class="pmc-mode">${escapeHtml(modeText)}</div>`
                + `<div class="pmc-meta">${path.length} 点</div>`
                + `</div>`,
              e.point
            )
          }
        }
        added.poly.addEventListener('click', onGpxLineClick)
        if (hit) hit.poly.addEventListener('click', onGpxLineClick)
      }
      if (scene.gpxEndpointPickable && added?.pts?.length >= 2) {
        const ends = [
          { kind: 'start', pt: added.pts[0] },
          { kind: 'end', pt: added.pts[added.pts.length - 1] }
        ]
        for (const end of ends) {
          const m = new BMap.Marker(end.pt)
          m.addEventListener('click', () => {
            this.callbacks.onGpxEndpointClick?.({
              overlay: o,
              endpoint: end.kind,
              point: end.pt
            })
          })
          this.map.addOverlay(m)
          this._track(m)
        }
      }
    }

    // preview
    if (Array.isArray(scene.previewPath) && scene.previewPath.length >= 2) {
      const preview = normalizeGcjPath(scene.previewPath)
      const added = this._addPolyline(preview.length >= 2 ? preview : scene.previewPath, '#F97316', 6, true)
      if (added) viewport.push(...added.pts)
    }

    // direction ends
    if (scene.showDirection && scene.showPolyline && lineList.length > 1) {
      const first = pointToBd09(lineList[0])
      const last = pointToBd09(lineList[lineList.length - 1])
      if (first) {
        const label = new BMap.Label('起', {
          position: new BMap.Point(first[0], first[1]),
          offset: new BMap.Size(-10, -10)
        })
        label.setStyle({
          color: '#fff',
          backgroundColor: '#16A34A',
          border: 'none',
          borderRadius: '10px',
          padding: '2px 6px',
          fontSize: '12px'
        })
        this.map.addOverlay(label)
        this._track(label)
      }
      if (last) {
        const label = new BMap.Label('终', {
          position: new BMap.Point(last[0], last[1]),
          offset: new BMap.Size(-10, -10)
        })
        label.setStyle({
          color: '#fff',
          backgroundColor: '#DC2626',
          border: 'none',
          borderRadius: '10px',
          padding: '2px 6px',
          fontSize: '12px'
        })
        this.map.addOverlay(label)
        this._track(label)
      }
    }

    // markers（聚合 + 蜘蛛腿，对齐高德 leaflet.markercluster）
    const pointsForFit = Array.isArray(scene.points) ? scene.points : []
    for (const p of pointsForFit) {
      const bd = pointToBd09(p)
      if (!bd) continue
      viewport.push(new BMap.Point(bd[0], bd[1]))
    }
    this._lastScene = scene
    this._drawPhotoMarkers(scene)

    // path edit vertices
    if (scene.pathEditIndex >= 0 && this.segmentRefs[scene.pathEditIndex]?.latlngs) {
      this._bindPathEditor(scene.pathEditIndex, this.segmentRefs[scene.pathEditIndex].latlngs)
    }

    if ((fit || !this.didFit) && viewport.length) {
      try {
        this.map.setViewport(viewport)
        this.didFit = true
      } catch (_) {
        try {
          this.map.centerAndZoom(viewport[0], 14)
          this.didFit = true
        } catch (e2) { /* ignore */ }
      }
    }
  }

  /**
   * 绘制照片/视频点：缩略图聚合角标，点击放大或蜘蛛腿摊开
   */
  _drawPhotoMarkers(scene) {
    if (!this.map || !this.BMap || !this.ThumbOverlay) return
    this.clearPhotoOverlays()
    const BMap = this.BMap
    const selectedIds = new Set((scene.selectedPhotoIds || []).map(String))
    const points = Array.isArray(scene.points) ? scene.points : []
    const items = []
    points.forEach((p, index) => {
      const bd = pointToBd09(p)
      if (!bd) return
      const bdPoint = new BMap.Point(bd[0], bd[1])
      if (isWaypointPoint(p)) {
        const marker = new BMap.Marker(bdPoint)
        marker.setTitle(String(p.title || '途经点'))
        marker.addEventListener('click', () => {
          if (scene.pointPickable || scene.editable) {
            this.callbacks.onWaypointClick?.({ index, point: p })
            this.callbacks.onPointClick?.({ index, point: p })
          }
        })
        this.map.addOverlay(marker)
        this._trackPhoto(marker)
        return
      }
      items.push({
        photo: p,
        index,
        bdPoint,
        estimated: isEstimatedLocation(p),
        canCorrect: !!(scene.locationCorrectable && p.photoId != null),
        selected: selectedIds.has(String(p.photoId))
      })
    })
    if (!items.length) return

    const zoom = this.map.getZoom()
    // 对齐 disableClusteringAtZoom: 16 —— 高缩放下仍合并近乎重叠的点，便于蜘蛛腿
    const radiusPx = zoom >= 16 ? 12 : clusterRadiusForZoom(zoom)
    const clusters = clusterByPixel(this.map, items, radiusPx)

    for (const group of clusters) {
      const key = clusterKeyOf(group)
      const spiderfyThis = this._spiderfyKey && this._spiderfyKey === key && group.length > 1

      if (spiderfyThis) {
        this._drawSpiderfiedGroup(scene, group)
        continue
      }

      if (group.length === 1) {
        this._drawSinglePhotoMarker(scene, group[0], group[0].bdPoint)
        continue
      }

      const cover = pickCoverPhoto(group)
      const center = groupCenterPoint(BMap, group)
      const overlay = new this.ThumbOverlay(center, cover, {
        count: group.length,
        selected: false,
        draggable: false,
        zIndex: 650,
        onClick: () => this._onClusterClick(group, key)
      })
      this.map.addOverlay(overlay)
      this._trackPhoto(overlay)
    }
  }

  _onClusterClick(group, key) {
    if (!this.map || !group?.length) return
    const zoom = this.map.getZoom()
    const pts = group.map(g => g.bdPoint)
    // 像素跨度：较大则先放大视野（对齐 zoomToBoundsOnClick）
    let minX = Infinity
    let maxX = -Infinity
    let minY = Infinity
    let maxY = -Infinity
    for (const p of pts) {
      const px = this.map.pointToOverlayPixel(p)
      minX = Math.min(minX, px.x)
      maxX = Math.max(maxX, px.x)
      minY = Math.min(minY, px.y)
      maxY = Math.max(maxY, px.y)
    }
    const span = Math.max(maxX - minX, maxY - minY)
    if (zoom < 16 && span > 48) {
      try {
        this.map.setViewport(pts)
      } catch (_) {
        this.map.centerAndZoom(pts[0], Math.min(zoom + 2, 18))
      }
      return
    }
    // 已放大或点几乎重叠：蜘蛛腿摊开
    this._suppressMapClick = true
    this._spiderfyKey = key
    if (this._lastScene) this._drawPhotoMarkers(this._lastScene)
    setTimeout(() => { this._suppressMapClick = false }, 50)
  }

  _drawSpiderfiedGroup(scene, group) {
    const BMap = this.BMap
    const center = groupCenterPoint(BMap, group)
    const centerPx = this.map.pointToOverlayPixel(center)
    const n = group.length
    const radiusPx = Math.max(42, 28 + n * 7)
    group.forEach((it, i) => {
      const angle = ((Math.PI * 2) * i) / n - Math.PI / 2
      const px = new BMap.Pixel(
        centerPx.x + Math.cos(angle) * radiusPx,
        centerPx.y + Math.sin(angle) * radiusPx
      )
      const tip = this.map.overlayPixelToPoint(px)
      const leg = new BMap.Polyline([center, tip], {
        strokeColor: '#64748B',
        strokeWeight: 1.5,
        strokeOpacity: 0.75,
        enableClicking: false
      })
      this.map.addOverlay(leg)
      this._trackPhoto(leg)
      this._drawSinglePhotoMarker(scene, it, tip, { zIndex: 700 })
    })
  }

  _drawSinglePhotoMarker(scene, item, atPoint, extra = {}) {
    const p = item.photo
    const index = item.index
    const estimated = item.estimated
    const canCorrect = item.canCorrect
    const draggable = ((estimated && scene.estimatedDraggable) || canCorrect) && !scene.aiPickMode
    const overlay = new this.ThumbOverlay(atPoint, p, {
      count: 1,
      selected: !!item.selected,
      draggable,
      zIndex: extra.zIndex || (estimated || canCorrect ? 680 : 620),
      onClick: (photoPoint, bdPoint) => {
        const openAt = bdPoint || atPoint
        if (scene.pointPickable) {
          this.callbacks.onPointClick?.({ index, point: photoPoint })
          return
        }
        if (scene.aiPickMode || estimated) {
          this.callbacks.onEstimatedSelect?.({
            index,
            point: photoPoint,
            photoId: photoPoint.photoId,
            latitude: photoPoint.latitude,
            longitude: photoPoint.longitude,
            aiPickMode: !!scene.aiPickMode
          })
          this.callbacks.onLocationSelect?.({
            index,
            point: photoPoint,
            photoId: photoPoint.photoId,
            latitude: photoPoint.latitude,
            longitude: photoPoint.longitude
          })
          if (!scene.aiPickMode) {
            this._openHtmlInfo(buildPopupHtml(photoPoint), openAt)
          }
          return
        }
        if (canCorrect) {
          this.callbacks.onLocationSelect?.({
            index,
            point: photoPoint,
            photoId: photoPoint.photoId,
            latitude: photoPoint.latitude,
            longitude: photoPoint.longitude
          })
        }
        this._openHtmlInfo(buildPopupHtml(photoPoint), openAt)
      },
      onDragEnd: (photoPoint, bdPoint) => {
        const [wgsLng, wgsLat] = bd09ToWgs84(bdPoint.lng, bdPoint.lat)
        const [gcjLng, gcjLat] = bd09ToGcj02(bdPoint.lng, bdPoint.lat)
        const payload = {
          index,
          point: photoPoint,
          photoId: photoPoint.photoId,
          latitude: Number(wgsLat.toFixed(7)),
          longitude: Number(wgsLng.toFixed(7)),
          mapLatLng: [gcjLat, gcjLng]
        }
        if (estimated) this.callbacks.onEstimatedDragEnd?.(payload)
        this.callbacks.onLocationDragEnd?.(payload)
      }
    })
    this.map.addOverlay(overlay)
    this._trackPhoto(overlay)
  }

  _bindPathEditor(index, latlngsGcj) {
    this.clearPathEditMarkers()
    if (!this.map || !this.BMap || !Array.isArray(latlngsGcj)) return
    const BMap = this.BMap
    const pts = gcjPathToBdPoints(BMap, latlngsGcj)
    const working = latlngsGcj.map(p => [Number(p[0]), Number(p[1])])
    pts.forEach((pt, vi) => {
      const marker = new BMap.Marker(pt, { enableDragging: true })
      marker.addEventListener('dragend', () => {
        const p = marker.getPosition()
        const [gcjLng, gcjLat] = bd09ToGcj02(p.lng, p.lat)
        working[vi] = [gcjLat, gcjLng]
        this.callbacks.onSegmentPathChange?.({ index, latlngs: working.slice() })
      })
      marker.addEventListener('dblclick', () => {
        if (working.length <= 2) return
        working.splice(vi, 1)
        this.callbacks.onSegmentPathChange?.({ index, latlngs: working.slice() })
      })
      this.map.addOverlay(marker)
      this.pathEditMarkers.push(marker)
    })
  }

  setBoxSelectActive(active) {
    if (!this.map) return
    this.clearBoxSelect()
    if (!active) {
      this.map.enableDragging()
      return
    }
    this.map.disableDragging()
    const onDown = (e) => {
      if (!e?.point) return
      this.boxDragging = true
      this.boxStart = e.point
      if (this.boxRect) {
        try { this.map.removeOverlay(this.boxRect) } catch (_) { /* ignore */ }
      }
      this.boxRect = new this.BMap.Polygon([
        e.point, e.point, e.point, e.point
      ], {
        strokeColor: '#F59E0B',
        strokeWeight: 2,
        strokeStyle: 'dashed',
        fillColor: '#F59E0B',
        fillOpacity: 0.12,
        enableClicking: false
      })
      this.map.addOverlay(this.boxRect)
    }
    const onMove = (e) => {
      if (!this.boxDragging || !this.boxStart || !e?.point || !this.boxRect) return
      const a = this.boxStart
      const b = e.point
      const sw = new this.BMap.Point(Math.min(a.lng, b.lng), Math.min(a.lat, b.lat))
      const ne = new this.BMap.Point(Math.max(a.lng, b.lng), Math.max(a.lat, b.lat))
      const nw = new this.BMap.Point(sw.lng, ne.lat)
      const se = new this.BMap.Point(ne.lng, sw.lat)
      this.boxRect.setPath([sw, nw, ne, se])
    }
    const onUp = (e) => {
      if (!this.boxDragging || !this.boxStart) return
      this.boxDragging = false
      const end = e?.point || this.boxStart
      const southBd = Math.min(this.boxStart.lat, end.lat)
      const northBd = Math.max(this.boxStart.lat, end.lat)
      const westBd = Math.min(this.boxStart.lng, end.lng)
      const eastBd = Math.max(this.boxStart.lng, end.lng)
      if (this.boxRect) {
        try { this.map.removeOverlay(this.boxRect) } catch (_) { /* ignore */ }
        this.boxRect = null
      }
      // 转为 GCJ 边界，与 Leaflet box-select 一致
      const sw = (() => {
        const [lng, lat] = bd09ToGcj02(westBd, southBd)
        return { lng, lat }
      })()
      const ne = (() => {
        const [lng, lat] = bd09ToGcj02(eastBd, northBd)
        return { lng, lat }
      })()
      this.callbacks.onBoxSelect?.({
        south: sw.lat,
        north: ne.lat,
        west: sw.lng,
        east: ne.lng
      })
      this.boxStart = null
    }
    this.map.addEventListener('mousedown', onDown)
    this.map.addEventListener('mousemove', onMove)
    this.map.addEventListener('mouseup', onUp)
    this.boxHandlers = { onDown, onMove, onUp }
  }

  clearBoxSelect() {
    if (this.map && this.boxHandlers) {
      try {
        this.map.removeEventListener('mousedown', this.boxHandlers.onDown)
        this.map.removeEventListener('mousemove', this.boxHandlers.onMove)
        this.map.removeEventListener('mouseup', this.boxHandlers.onUp)
      } catch (_) { /* ignore */ }
    }
    this.boxHandlers = null
    this.boxDragging = false
    this.boxStart = null
    if (this.boxRect && this.map) {
      try { this.map.removeOverlay(this.boxRect) } catch (_) { /* ignore */ }
    }
    this.boxRect = null
  }
}

export function leafletCenterToBd(map) {
  if (!map?.getCenter) return null
  const c = map.getCenter()
  if (!c) return null
  return gcj02ToBd09(c.lng, c.lat)
}
