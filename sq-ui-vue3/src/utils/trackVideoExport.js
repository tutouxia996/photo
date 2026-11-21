import { saveAs } from 'file-saver'
import { travelModeColor, travelModeIconSvg } from '@/utils/photoMapCluster'

/** 高德运动轨迹风：略俯视、看清路网，避免仰到地平线 */
export const FOLLOW_CAM_PITCH = 52
/**
 * 导出缩放：高德卫星瓦片原生约到 18 级，超过会拉伸发糊。
 * 跟拍近景用 17.6–18，兼顾清晰与细节。
 */
export const FOLLOW_CAM_ZOOM = 17.8
export const FOLLOW_CAM_ZOOM_MIN = 16.8
export const FOLLOW_CAM_ZOOM_MAX = 18

/** 导出专用地面速度（m/s），远慢于地图循环演示，避免抽搐 */
const EXPORT_MPS = {
  walk: 4.2,
  bike: 6.5,
  drive: 11,
  bus: 9,
  metro: 10,
  train: 13,
  hsr: 16,
  flight: 26,
  stay: 0,
  drift: 0,
  other: 7
}

const MIME_CANDIDATES = [
  'video/mp4;codecs=avc1.42E01E,mp4a.40.2',
  'video/mp4;codecs=avc1.42E01E',
  'video/mp4;codecs=avc1',
  'video/mp4',
  'video/webm;codecs=vp9,opus',
  'video/webm;codecs=vp9',
  'video/webm;codecs=vp8',
  'video/webm'
]

export function pickRecorderMime() {
  if (typeof MediaRecorder === 'undefined' || typeof MediaRecorder.isTypeSupported !== 'function') {
    return ''
  }
  for (const mime of MIME_CANDIDATES) {
    try {
      if (MediaRecorder.isTypeSupported(mime)) return mime
    } catch (e) { /* ignore */ }
  }
  return ''
}

export function extensionForMime(mime) {
  const m = String(mime || '').toLowerCase()
  if (m.includes('mp4')) return 'mp4'
  return 'webm'
}

export function formatExportKm(meters) {
  const m = Math.max(0, Number(meters) || 0)
  const km = m / 1000
  if (km < 0.05) return `${Math.round(m)} m`
  if (km < 10) return `${km.toFixed(1)} km`
  return `${Math.round(km * 10) / 10} km`
}

/** 在录制画布右下角绘制里程（进 MP4，不跟地图点走） */
export function paintDistanceHud(ctx, width, height, text) {
  if (!ctx || !width || !height) return
  const label = String(text || '')
  if (!label) return
  const padX = Math.max(24, Math.round(width * 0.028))
  const padY = Math.max(22, Math.round(height * 0.036))
  const fontPx = Math.max(28, Math.min(56, Math.round(width * 0.036)))
  ctx.save()
  ctx.font = `italic 800 ${fontPx}px "Segoe UI", system-ui, sans-serif`
  ctx.textAlign = 'right'
  ctx.textBaseline = 'bottom'
  ctx.lineJoin = 'round'
  ctx.miterLimit = 2
  ctx.lineWidth = Math.max(3, Math.round(fontPx * 0.12))
  ctx.strokeStyle = 'rgba(0,0,0,0.55)'
  ctx.fillStyle = '#ffffff'
  ctx.shadowColor = 'rgba(0,0,0,0.45)'
  ctx.shadowBlur = Math.round(fontPx * 0.22)
  ctx.shadowOffsetY = 2
  ctx.strokeText(label, width - padX, height - padY)
  ctx.shadowBlur = 0
  ctx.fillText(label, width - padX, height - padY)
  ctx.restore()
}

export function sanitizeFileName(name) {
  const base = String(name || '轨迹').replace(/[\\/:*?"<>|]+/g, '_').trim()
  return base || '轨迹'
}

export function downloadBlob(blob, filename) {
  saveAs(blob, filename)
}

/**
 * 生成出行方式图标 data URL，供高德 LabelsLayer 使用（进 WebGL 画布，可被录制）。
 */
export function travelerIconDataUrl(mode, size = 56) {
  const color = travelModeColor(mode)
  const inner = travelModeIconSvg(mode, Math.round(size * 0.55))
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 ${size} ${size}">
    <defs>
      <filter id="s" x="-30%" y="-30%" width="160%" height="160%">
        <feDropShadow dx="0" dy="1" stdDeviation="1.6" flood-color="#000" flood-opacity="0.35"/>
      </filter>
    </defs>
    <circle cx="${size / 2}" cy="${size / 2}" r="${size / 2 - 2}" fill="${color}" stroke="#fff" stroke-width="3" filter="url(#s)"/>
    <g transform="translate(${(size - size * 0.55) / 2} ${(size - size * 0.55) / 2})">${inner.replace(/fill="#fff"/g, 'fill="#ffffff"')}</g>
  </svg>`
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`
}

/**
 * 从高德容器里挑主画布（面积最大的 canvas）。
 */
export function pickAmapRecordCanvas(container) {
  if (!container) return null
  const list = Array.from(container.querySelectorAll('canvas'))
  let best = null
  let bestArea = 0
  for (const c of list) {
    const w = c.width || 0
    const h = c.height || 0
    const area = w * h
    if (area > bestArea) {
      bestArea = area
      best = c
    }
  }
  return best
}

/** 按分辨率与帧率估算码率，避免视网膜大画布仍用 8Mbps 导致发糊 */
export function suggestExportBitrate(width, height, fps = 30) {
  const w = Math.max(1, Number(width) || 1280)
  const h = Math.max(1, Number(height) || 720)
  const f = Math.max(24, Number(fps) || 30)
  // 约 0.12 bit/pixel/frame，并抬高下限
  const raw = Math.round(w * h * f * 0.12)
  return Math.min(48_000_000, Math.max(16_000_000, raw))
}

/**
 * 创建导出合成画布：长边不超过 maxEdge（默认 1920），避免 4K 视网膜画布低码率糊成一片。
 * 从地图画布高质量缩放到该尺寸再编码，观感通常比「超大分辨率 + 低码率」更清晰。
 */
export function createExportComposite(sourceCanvas, maxEdge = 1920) {
  const sw = sourceCanvas?.width || 0
  const sh = sourceCanvas?.height || 0
  if (!sw || !sh) {
    const c = document.createElement('canvas')
    c.width = 1280
    c.height = 720
    return { canvas: c, ctx: c.getContext('2d'), scale: 1 }
  }
  const edge = Math.max(1280, Number(maxEdge) || 1920)
  const long = Math.max(sw, sh)
  const scale = long > edge ? edge / long : 1
  const tw = Math.max(2, Math.round(sw * scale))
  const th = Math.max(2, Math.round(sh * scale))
  // 保证偶数尺寸，部分编码器更稳
  const canvas = document.createElement('canvas')
  canvas.width = tw - (tw % 2)
  canvas.height = th - (th % 2)
  const ctx = canvas.getContext('2d', { alpha: false })
  if (ctx) {
    ctx.imageSmoothingEnabled = true
    ctx.imageSmoothingQuality = 'high'
  }
  return { canvas, ctx, scale }
}

/** 把地图画布画到导出合成画布（保持清晰缩放） */
export function blitMapToExportComposite(ctx, composite, sourceCanvas) {
  if (!ctx || !composite || !sourceCanvas) return false
  try {
    ctx.imageSmoothingEnabled = true
    ctx.imageSmoothingQuality = 'high'
    ctx.drawImage(sourceCanvas, 0, 0, composite.width, composite.height)
    return true
  } catch (e) {
    return false
  }
}

function createMediaRecorder(stream, mimeType, bitrate) {
  try {
    return new MediaRecorder(stream, {
      mimeType,
      videoBitsPerSecond: bitrate
    })
  } catch (e) {
    try {
      return new MediaRecorder(stream, { videoBitsPerSecond: bitrate })
    } catch (e2) {
      return new MediaRecorder(stream)
    }
  }
}

/**
 * 用 MediaRecorder 录制 canvas 流，返回 stop() → Blob。
 */
export function startCanvasRecorder(canvas, options = {}) {
  const fps = options.fps || 30
  const mimeType = options.mimeType || pickRecorderMime()
  if (!canvas || typeof canvas.captureStream !== 'function') {
    throw new Error('当前环境无法从地图画布录制视频')
  }
  if (!mimeType) {
    throw new Error('当前浏览器不支持视频录制，请使用较新的 Chrome / Edge')
  }
  const stream = canvas.captureStream(fps)
  const chunks = []
  const bitrate = options.videoBitsPerSecond
    || suggestExportBitrate(canvas.width, canvas.height, fps)
  const recorder = createMediaRecorder(stream, mimeType, bitrate)
  recorder.ondataavailable = (e) => {
    if (e.data && e.data.size > 0) chunks.push(e.data)
  }
  const done = new Promise((resolve, reject) => {
    recorder.onerror = () => reject(new Error('录制失败'))
    recorder.onstop = () => {
      try {
        stream.getTracks().forEach((t) => t.stop())
      } catch (e) { /* ignore */ }
      const type = recorder.mimeType || mimeType || 'video/webm'
      resolve(new Blob(chunks, { type }))
    }
  })
  recorder.start(250)
  return {
    mimeType: recorder.mimeType || mimeType,
    stop() {
      if (recorder.state !== 'inactive') recorder.stop()
      return done
    },
    cancel() {
      try {
        if (recorder.state !== 'inactive') recorder.stop()
      } catch (e) { /* ignore */ }
      try {
        stream.getTracks().forEach((t) => t.stop())
      } catch (e) { /* ignore */ }
    }
  }
}

/**
 * 平滑录制：地图 canvas.captureStream（GPU 出帧）→ video → 合成画布叠里程 → MediaRecorder。
 * 比每帧 drawImage(WebGL) 更不容易后期掉帧。
 * getDistance: () => meters
 */
export async function startSmoothMapRecorder(mapCanvas, options = {}) {
  const fps = options.fps || 29.97
  const mimeType = options.mimeType || pickRecorderMime()
  if (!mapCanvas?.captureStream) throw new Error('当前环境无法从地图画布录制视频')
  if (!mimeType) throw new Error('当前浏览器不支持视频录制，请使用较新的 Chrome / Edge')

  const composed = createExportComposite(mapCanvas, options.maxEdge || 1920)
  const outCanvas = composed.canvas
  const ctx = composed.ctx
  if (!ctx) throw new Error('无法创建导出画布')

  const mapStream = mapCanvas.captureStream(fps)
  const video = document.createElement('video')
  video.muted = true
  video.playsInline = true
  video.setAttribute('playsinline', 'true')
  video.srcObject = mapStream
  video.width = mapCanvas.width
  video.height = mapCanvas.height
  await video.play().catch(() => {})
  // 等首帧，避免片头黑屏
  await new Promise((resolve) => {
    let done = false
    const finish = () => {
      if (done) return
      done = true
      resolve()
    }
    if (typeof video.requestVideoFrameCallback === 'function') {
      video.requestVideoFrameCallback(() => finish())
    } else {
      video.addEventListener('loadeddata', finish, { once: true })
    }
    setTimeout(finish, 400)
  })

  const bitrate = options.videoBitsPerSecond
    || suggestExportBitrate(outCanvas.width, outCanvas.height, fps)
  const outStream = outCanvas.captureStream(fps)
  const chunks = []
  const recorder = createMediaRecorder(outStream, mimeType, bitrate)
  recorder.ondataavailable = (e) => {
    if (e.data && e.data.size > 0) chunks.push(e.data)
  }

  let stopped = false
  let raf = 0
  let rvfc = 0
  const getDistance = typeof options.getDistance === 'function'
    ? options.getDistance
    : () => 0

  const paint = () => {
    if (stopped) return
    try {
      ctx.imageSmoothingEnabled = true
      ctx.imageSmoothingQuality = 'high'
      if (video.readyState >= 2) {
        ctx.drawImage(video, 0, 0, outCanvas.width, outCanvas.height)
      } else {
        // 兜底：直接从地图 canvas 抽一帧（可能偶发空白，但好过全黑）
        try {
          ctx.drawImage(mapCanvas, 0, 0, outCanvas.width, outCanvas.height)
        } catch (e2) { /* tainted / webgl */ }
      }
      paintDistanceHud(ctx, outCanvas.width, outCanvas.height, formatExportKm(getDistance()))
    } catch (e) { /* ignore frame */ }
  }

  const loopRvfc = () => {
    if (stopped) return
    paint()
    if (typeof video.requestVideoFrameCallback === 'function') {
      rvfc = video.requestVideoFrameCallback(loopRvfc)
    }
  }
  const loopRaf = () => {
    if (stopped) return
    paint()
    raf = requestAnimationFrame(loopRaf)
  }

  if (typeof video.requestVideoFrameCallback === 'function') {
    rvfc = video.requestVideoFrameCallback(loopRvfc)
  } else {
    raf = requestAnimationFrame(loopRaf)
  }

  const done = new Promise((resolve, reject) => {
    recorder.onerror = () => reject(new Error('录制失败'))
    recorder.onstop = () => {
      stopped = true
      if (raf) cancelAnimationFrame(raf)
      if (rvfc && video.cancelVideoFrameCallback) {
        try { video.cancelVideoFrameCallback(rvfc) } catch (e) { /* ignore */ }
      }
      try { mapStream.getTracks().forEach((t) => t.stop()) } catch (e) { /* ignore */ }
      try { outStream.getTracks().forEach((t) => t.stop()) } catch (e) { /* ignore */ }
      try { video.pause(); video.srcObject = null } catch (e) { /* ignore */ }
      resolve(new Blob(chunks, { type: recorder.mimeType || mimeType || 'video/webm' }))
    }
  })

  recorder.start(250)
  // 先推一帧，避免片头黑屏
  paint()

  return {
    mimeType: recorder.mimeType || mimeType,
    canvas: outCanvas,
    stop() {
      if (recorder.state !== 'inactive') recorder.stop()
      return done
    },
    cancel() {
      stopped = true
      try {
        if (recorder.state !== 'inactive') recorder.stop()
      } catch (e) { /* ignore */ }
      if (raf) cancelAnimationFrame(raf)
      try { mapStream.getTracks().forEach((t) => t.stop()) } catch (e) { /* ignore */ }
      try { outStream.getTracks().forEach((t) => t.stop()) } catch (e) { /* ignore */ }
      try { video.pause(); video.srcObject = null } catch (e) { /* ignore */ }
    }
  }
}

/** 最短角差，范围 (-180, 180] */
export function shortestAngleDelta(from, to) {
  return ((Number(to) - Number(from) + 540) % 360) - 180
}

/** 航向角平滑（无速率限制） */
export function smoothHeading(prev, next, alpha = 0.12) {
  if (prev == null || Number.isNaN(prev)) return next
  const delta = shortestAngleDelta(prev, next)
  return (prev + delta * alpha + 360) % 360
}

/**
 * 带角速度上限的航向平滑，避免急弯/高速时镜头抽搐。
 * @param {number} maxDegPerSec 最大旋转速度（度/秒）
 */
export function smoothHeadingLimited(prev, next, dtMs, maxDegPerSec = 48, alpha = 0.14) {
  if (prev == null || Number.isNaN(prev)) return next
  const dt = Math.max(0.008, (Number(dtMs) || 16) / 1000)
  let delta = shortestAngleDelta(prev, next)
  const maxStep = Math.max(0.05, maxDegPerSec * dt)
  if (delta > maxStep) delta = maxStep
  if (delta < -maxStep) delta = -maxStep
  const blended = delta * Math.min(1, alpha + dt * 1.2)
  return (prev + blended + 360) % 360
}

/**
 * 高德/运动轨迹跟拍风：用「身后→前方」长弦定朝向，而不是折线每段瞬时航向。
 * behind/ahead 越大越稳，越接近官方里程视频的沉稳跟拍。
 */
export function chordHeading(metrics, dist, behind = 55, ahead = 150) {
  if (!metrics?.segs?.length) return 0
  const d0 = Math.max(0, (Number(dist) || 0) - Math.max(10, behind))
  const d1 = Math.min(metrics.total, (Number(dist) || 0) + Math.max(40, ahead))
  // 两端太近时拉长采样，避免近距噪声
  const span = Math.max(40, d1 - d0)
  const from = pointLatLngAtDistance(metrics, Math.max(0, d1 - span))
  const to = pointLatLngAtDistance(metrics, d1)
  if (!from || !to) return headingAtDistance(metrics, dist)
  return headingBetween(from, to)
}

function pointLatLngAtDistance(metrics, dist) {
  const segs = metrics?.segs
  if (!segs?.length) return null
  let d = Math.max(0, Math.min(Number(dist) || 0, metrics.total))
  if (d <= 0) return segs[0].from
  if (d >= metrics.total) return segs[segs.length - 1].to
  for (const seg of segs) {
    if (d <= seg.start + seg.len) {
      const t = seg.len <= 0 ? 1 : (d - seg.start) / seg.len
      return {
        lat: seg.from.lat + (seg.to.lat - seg.from.lat) * t,
        lng: seg.from.lng + (seg.to.lng - seg.from.lng) * t
      }
    }
  }
  return segs[segs.length - 1].to
}

/**
 * 导出镜头航向（对齐高德里程视频 / 运动跟拍常见做法）：
 * 1) 目标朝向 = 长弦方向（身后数十米 → 前方百米级）
 * 2) 死区：误差小于约 12° 完全不转
 * 3) 指数平滑时间常数 ~1.5s + 角速度上限，大弯才慢慢跟上
 * 4) 不做激进左右甩镜，避免鬼畜
 * state: { heading, sideBias }
 */
export function updateExportCameraHeading(state, metrics, dist, dtMs) {
  const cam = state || { heading: 0, sideBias: 0 }
  const dt = Math.max(0.008, (Number(dtMs) || 16) / 1000)
  const desired = chordHeading(metrics, dist, 60, 160)
  const err = shortestAngleDelta(cam.heading, desired)

  // 死区：小路弯/折线锯齿一律忽略
  const DEADBAND = 12
  if (Math.abs(err) < DEADBAND) {
    cam.sideBias *= 0.92
    if (Math.abs(cam.sideBias) < 0.3) cam.sideBias = 0
    return cam
  }

  // 超出死区的部分才追：等效「误差减去死区」再平滑，手感更圆
  const tracked = err > 0 ? err - DEADBAND : err + DEADBAND
  const target = (cam.heading + tracked + 360) % 360

  // τ≈1.6s 指数平滑 + 最大约 20°/s（大弯圆润，不抽）
  const TAU = 1.6
  const alpha = 1 - Math.exp(-dt / TAU)
  const maxRate = 20
  cam.heading = smoothHeadingLimited(cam.heading, target, dtMs, maxRate, alpha)

  // 仅持续大弯时轻微侧偏（远弱于旧逻辑），直行回正
  const turnAhead = shortestAngleDelta(
    chordHeading(metrics, dist, 40, 40),
    chordHeading(metrics, dist + 90, 40, 40)
  )
  let wantSide = 0
  if (Math.abs(turnAhead) > 40) {
    wantSide = Math.max(-18, Math.min(18, -turnAhead * 0.28))
  }
  cam.sideBias += (wantSide - cam.sideBias) * Math.min(0.06, dt / 1.2)
  if (Math.abs(cam.sideBias) < 0.3) cam.sideBias = 0

  return cam
}

function headingBetween(from, to) {
  const φ1 = from.lat * Math.PI / 180
  const φ2 = to.lat * Math.PI / 180
  const Δλ = (to.lng - from.lng) * Math.PI / 180
  const y = Math.sin(Δλ) * Math.cos(φ2)
  const x = Math.cos(φ1) * Math.sin(φ2) - Math.sin(φ1) * Math.cos(φ2) * Math.cos(Δλ)
  return (Math.atan2(y, x) * 180 / Math.PI + 360) % 360
}

/** 路径上某距离处的航向 */
export function headingAtDistance(metrics, dist) {
  const segs = metrics?.segs
  if (!segs?.length) return 0
  let d = Math.max(0, Math.min(Number(dist) || 0, metrics.total))
  if (d <= 0) return headingBetween(segs[0].from, segs[0].to)
  if (d >= metrics.total) {
    const last = segs[segs.length - 1]
    return headingBetween(last.from, last.to)
  }
  for (const seg of segs) {
    if (d <= seg.start + seg.len) return headingBetween(seg.from, seg.to)
  }
  const last = segs[segs.length - 1]
  return headingBetween(last.from, last.to)
}

/** 前瞻航向：把前方一段路的方向做向量平均，转弯更像导航跟拍 */
export function lookaheadHeading(metrics, dist, lookMeters = 90) {
  if (!metrics?.segs?.length) return 0
  const look = Math.max(20, Number(lookMeters) || 90)
  const samples = [
    headingAtDistance(metrics, dist),
    headingAtDistance(metrics, dist + look * 0.35),
    headingAtDistance(metrics, dist + look * 0.7),
    headingAtDistance(metrics, dist + look)
  ]
  let x = 0
  let y = 0
  for (const h of samples) {
    const r = h * Math.PI / 180
    x += Math.cos(r)
    y += Math.sin(r)
  }
  if (Math.abs(x) < 1e-8 && Math.abs(y) < 1e-8) return samples[0]
  return (Math.atan2(y, x) * 180 / Math.PI + 360) % 360
}

/** 按瞬时地面速度微调缩放：仍保持近景，仅极快时略拉远 */
export function exportZoomForMps(mps, baseZoom = FOLLOW_CAM_ZOOM) {
  const v = Math.max(0, Number(mps) || 0)
  const base = Number(baseZoom) > 0 ? Number(baseZoom) : FOLLOW_CAM_ZOOM
  let z = base
  if (v > 25) z = base - 0.25
  if (v > 50) z = base - 0.55
  if (v > 90) z = base - 0.9
  return Math.max(FOLLOW_CAM_ZOOM_MIN, Math.min(FOLLOW_CAM_ZOOM_MAX, z))
}

/**
 * 转弯侧视偏移（度）：右转时从左侧看、左转时从右侧看；直行接近 0。
 * 与前进航向相加得到镜头 rotation。
 */
export function turnSideOffset(metrics, dist, lookMeters = 50) {
  if (!metrics?.segs?.length) return 0
  const look = Math.max(18, Number(lookMeters) || 50)
  const h0 = headingAtDistance(metrics, dist)
  const h1 = headingAtDistance(metrics, dist + look)
  let delta = ((h1 - h0 + 540) % 360) - 180
  // 弯越急侧视越大，上限约 ±34°
  const raw = -delta * 0.95
  return Math.max(-34, Math.min(34, raw))
}

/**
 * 导出专用时间轴：比地图循环动画慢很多；超长轨迹压缩到可接受片长，但不把速度拉到抽搐。
 */
export function planExportTimeline(spans) {
  let t = 0
  const timed = (spans || []).map((s) => {
    const len = Math.max(0, s.end - s.start)
    const mode = String(s.mode || 'other').toLowerCase()
    const mps = EXPORT_MPS[mode] ?? EXPORT_MPS.other
    let dt
    if (mode === 'stay' || mode === 'drift') {
      dt = Math.max(900, Math.min(2200, len > 0 ? (len / 5) * 1000 : 1400))
    } else {
      dt = (len / Math.max(0.8, mps)) * 1000
    }
    dt = Math.max(dt, 120)
    const item = { ...s, t0: t, t1: t + dt }
    t += dt
    return item
  })
  const raw = t || 20000
  // 近景跟拍：允许更长片长，避免 50km 被压成十几秒导致狂转
  const duration = Math.min(240000, Math.max(22000, raw))
  const scale = duration / raw
  timed.forEach((s) => {
    s.t0 *= scale
    s.t1 *= scale
  })
  return { timed, duration, rawDuration: raw, speedScale: scale }
}

/** 时间轴上某时刻的瞬时速度（米/秒） */
export function exportSpeedAt(timed, elapsedMs) {
  const t = Math.max(0, Number(elapsedMs) || 0)
  for (const s of timed || []) {
    if (t <= s.t1) {
      const span = Math.max(1, s.t1 - s.t0)
      const len = Math.max(0, s.end - s.start)
      return len / (span / 1000)
    }
  }
  return 0
}

function latLngAtDist(metrics, dist) {
  const segs = metrics?.segs
  if (!segs?.length) return null
  const d = Math.max(0, Math.min(Number(dist) || 0, metrics.total))
  if (d <= 0) return [segs[0].from.lat, segs[0].from.lng]
  if (d >= metrics.total) {
    const last = segs[segs.length - 1]
    return [last.to.lat, last.to.lng]
  }
  for (const seg of segs) {
    if (d <= seg.start + seg.len) {
      const t = seg.len <= 0 ? 1 : (d - seg.start) / seg.len
      return [
        seg.from.lat + (seg.to.lat - seg.from.lat) * t,
        seg.from.lng + (seg.to.lng - seg.from.lng) * t
      ]
    }
  }
  const last = segs[segs.length - 1]
  return [last.to.lat, last.to.lng]
}

/**
 * 按路程截取折线前缀（Leaflet latlng 或 {lat,lng}），用于渐显轨迹尾迹。
 */
export function pathPrefixByDistance(metrics, dist) {
  return pathBetweenDistance(metrics, 0, dist)
}

/** 截取路程区间 [d0, d1] 的折线点 */
export function pathBetweenDistance(metrics, d0, d1) {
  if (!metrics?.segs?.length) return []
  const a = Math.max(0, Math.min(Number(d0) || 0, metrics.total))
  const b = Math.max(a, Math.min(Number(d1) || 0, metrics.total))
  if (b - a < 1e-6) {
    const p = latLngAtDist(metrics, a)
    return p ? [p, p] : []
  }
  const out = []
  const startPt = latLngAtDist(metrics, a)
  if (startPt) out.push(startPt)
  for (const seg of metrics.segs) {
    const segEnd = seg.start + seg.len
    if (segEnd <= a) continue
    if (seg.start >= b) break
    // 顶点落在开区间内则纳入
    if (segEnd > a && segEnd < b) {
      out.push([seg.to.lat, seg.to.lng])
    }
  }
  const endPt = latLngAtDist(metrics, b)
  if (endPt) {
    const last = out[out.length - 1]
    if (!last || Math.abs(last[0] - endPt[0]) > 1e-9 || Math.abs(last[1] - endPt[1]) > 1e-9) {
      out.push(endPt)
    }
  }
  if (out.length < 2 && startPt) return [startPt, endPt || startPt]
  return out
}

/** 沿折线按米级步长加密，减少「折一下一下」的生硬感 */
export function densifyLatLngPath(latlngs, stepMeters = 6) {
  if (!Array.isArray(latlngs) || latlngs.length < 2) return latlngs || []
  const step = Math.max(2, Number(stepMeters) || 6)
  const out = []
  for (let i = 0; i < latlngs.length - 1; i++) {
    const a = latlngs[i]
    const b = latlngs[i + 1]
    const lat1 = Number(a[0])
    const lng1 = Number(a[1])
    const lat2 = Number(b[0])
    const lng2 = Number(b[1])
    if (!out.length) out.push([lat1, lng1])
    const mid = ((lat1 + lat2) / 2) * Math.PI / 180
    const dx = (lng2 - lng1) * Math.cos(mid) * 111320
    const dy = (lat2 - lat1) * 110540
    const len = Math.sqrt(dx * dx + dy * dy)
    if (!(len > step)) {
      out.push([lat2, lng2])
      continue
    }
    const n = Math.min(40, Math.ceil(len / step))
    for (let k = 1; k <= n; k++) {
      const t = k / n
      out.push([lat1 + (lat2 - lat1) * t, lng1 + (lng2 - lng1) * t])
    }
  }
  return out
}

/** Chaikin 切角平滑（轻度，避免轨迹离开路面） */
export function smoothLatLngPath(latlngs, iterations = 2) {
  if (!Array.isArray(latlngs) || latlngs.length < 3) return latlngs || []
  let pts = latlngs.map((p) => [Number(p[0]), Number(p[1])])
  const times = Math.max(0, Math.min(3, Number(iterations) || 0))
  for (let it = 0; it < times; it++) {
    if (pts.length < 3) break
    const next = [pts[0]]
    for (let i = 0; i < pts.length - 1; i++) {
      const a = pts[i]
      const b = pts[i + 1]
      next.push([
        a[0] * 0.75 + b[0] * 0.25,
        a[1] * 0.75 + b[1] * 0.25
      ])
      next.push([
        a[0] * 0.25 + b[0] * 0.75,
        a[1] * 0.25 + b[1] * 0.75
      ])
    }
    next.push(pts[pts.length - 1])
    pts = next
  }
  return pts
}

/** 导出尾迹展示用：加密 + 轻度平滑 */
export function polishExportPath(latlngs, stepMeters = 8) {
  const step = Math.max(5, Number(stepMeters) || 8)
  // 长段少加密、少切角，避免后期点数爆炸卡帧
  const n = Array.isArray(latlngs) ? latlngs.length : 0
  const iterations = n > 800 ? 1 : 2
  return smoothLatLngPath(densifyLatLngPath(latlngs, step), iterations)
}

function approxPathMeters(a, b) {
  const lat1 = Number(a[0])
  const lng1 = Number(a[1])
  const lat2 = Number(b[0])
  const lng2 = Number(b[1])
  const mid = ((lat1 + lat2) / 2) * Math.PI / 180
  const dx = (lng2 - lng1) * Math.cos(mid) * 111320
  const dy = (lat2 - lat1) * 110540
  return Math.sqrt(dx * dx + dy * dy)
}

/**
 * 导出前一次性预计算各出行方式段：已转成 AMap [lng,lat]，并带累计路程便于快速切片。
 * @returns {{ mode: string, start: number, end: number, path: number[][], cum: number[], total: number }[]}
 */
export function buildExportTrailCache(metrics, timed) {
  const list = []
  for (const span of timed || []) {
    const d0 = Number(span.start) || 0
    const d1 = Number(span.end) || 0
    if (!(d1 > d0)) continue
    const len = d1 - d0
    const step = len > 8000 ? 16 : (len > 2500 ? 12 : 8)
    const raw = pathBetweenDistance(metrics, d0, d1)
    if (!raw || raw.length < 2) continue
    // latlng -> 轻度平滑 -> AMap [lng,lat]
    const polished = polishExportPath(raw, step)
    const path = []
    const cum = [0]
    for (let i = 0; i < polished.length; i++) {
      const lat = Number(polished[i][0])
      const lng = Number(polished[i][1])
      if (!Number.isFinite(lat) || !Number.isFinite(lng)) continue
      if (path.length) {
        cum.push(cum[cum.length - 1] + approxPathMeters(polished[i - 1], polished[i]))
      }
      path.push([lng, lat])
    }
    if (path.length < 2) continue
    // cum 与 path 对齐（跳过非法点时重算）
    if (cum.length !== path.length) {
      cum.length = 0
      cum.push(0)
      for (let i = 1; i < path.length; i++) {
        const a = path[i - 1]
        const b = path[i]
        cum.push(cum[i - 1] + approxPathMeters([a[1], a[0]], [b[1], b[0]]))
      }
    }
    list.push({
      mode: span.mode || 'other',
      start: d0,
      end: d1,
      path,
      cum,
      total: cum[cum.length - 1] || 0
    })
  }
  return list
}

/** 按路程截取预计算 AMap 路径前缀（含插值端点） */
export function sliceExportTrailPrefix(cached, distMeters) {
  if (!cached?.path?.length) return []
  const pts = cached.path
  if (pts.length < 2) return pts.slice()
  const spanLen = Math.max(1e-6, cached.end - cached.start)
  const u = Math.max(0, Math.min(1, ((Number(distMeters) || 0) - cached.start) / spanLen))
  if (u <= 0) return [pts[0], pts[0]]
  if (u >= 1 || !cached.total) return pts
  const target = cached.total * u
  const cum = cached.cum
  let i = 1
  while (i < cum.length && cum[i] < target) i++
  const out = pts.slice(0, Math.max(1, i))
  if (i > 0 && i < pts.length) {
    const c0 = cum[i - 1] || 0
    const c1 = cum[i] || c0
    const t = c1 > c0 ? (target - c0) / (c1 - c0) : 1
    const a = pts[i - 1]
    const b = pts[i]
    const tip = [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t]
    const last = out[out.length - 1]
    if (!last || last[0] !== tip[0] || last[1] !== tip[1]) out.push(tip)
  }
  return out.length >= 2 ? out : pts.slice(0, 2)
}
