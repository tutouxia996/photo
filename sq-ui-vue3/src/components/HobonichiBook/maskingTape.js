/**
 * 美纹纸胶条工具：撕边形状、命中、烘焙绘制。
 * 图层：{ type:'tape', id, space, x, y, w, h, rot, clip }
 * x/y/w/h 为未旋转前的轴对齐框（归一化）；rot 为弧度。
 * 视觉：使用真实美纹纸贴图（assets/masking-tape.png）拉伸铺满胶条。
 */

import maskingTapeUrl from './assets/masking-tape.png'

let tapeImage = null
let tapeImageState = 'idle' // idle | loading | ready | error
const tapeImageWaiters = []

/** 预加载美纹纸贴图；未就绪时返回 null，加载完后回调 onReady */
export function ensureMaskingTapeImage(onReady) {
  if (tapeImageState === 'ready' && tapeImage?.complete && tapeImage.naturalWidth > 0) {
    return tapeImage
  }
  if (typeof onReady === 'function') tapeImageWaiters.push(onReady)
  if (tapeImageState === 'loading' || tapeImageState === 'error') return null
  tapeImageState = 'loading'
  const img = new Image()
  img.decoding = 'async'
  img.onload = () => {
    tapeImage = img
    tapeImageState = 'ready'
    const waiters = tapeImageWaiters.splice(0)
    waiters.forEach((fn) => {
      try {
        fn(img)
      } catch {
        /* ignore */
      }
    })
  }
  img.onerror = () => {
    tapeImageState = 'error'
    tapeImageWaiters.length = 0
  }
  img.src = maskingTapeUrl
  return null
}

// 模块加载时预取，减少首次拖出胶条/翻页烘焙的空白帧
ensureMaskingTapeImage()

export { maskingTapeUrl }

function hashStr(str) {
  let h = 2166136261
  const s = String(str || 'tape')
  for (let i = 0; i < s.length; i++) {
    h = Math.imul(h ^ s.charCodeAt(i), 16777619)
  }
  return h >>> 0
}

function mulberry32(seed) {
  let a = seed >>> 0
  return () => {
    a = (a + 0x6d2b79f5) >>> 0
    let t = a
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

/**
 * 撕边 clip-path polygon（百分比）。
 * 两端做浅锯齿（相对胶条长度约 1.5%–4.5%），避免细长尖刺。
 */
export function createTapeClip(seed) {
  const rng = mulberry32(hashStr(seed))
  const pts = []

  const topN = 5 + Math.floor(rng() * 3)
  pts.push([0, 2.5 + rng() * 3.5])
  for (let i = 1; i < topN; i++) {
    pts.push([(i / topN) * 100, 1 + rng() * 4.5])
  }
  pts.push([100, 2.5 + rng() * 3.5])

  // 右端：密而浅的撕口，像手撕胶带而非尖刺
  const rightN = 6 + Math.floor(rng() * 3)
  for (let i = 1; i < rightN; i++) {
    const y = (i / rightN) * 100 + (rng() - 0.5) * 3
    const depth = 1.5 + rng() * 2.8 + (i % 2 ? 0.6 : 0)
    pts.push([100 - depth, Math.min(97.5, Math.max(2.5, y))])
  }

  pts.push([100, 97.5 - rng() * 3.5])
  for (let i = topN - 1; i >= 1; i--) {
    pts.push([(i / topN) * 100, 95.5 + rng() * 3.5])
  }
  pts.push([0, 97.5 - rng() * 3.5])

  const leftN = 6 + Math.floor(rng() * 3)
  for (let i = leftN - 1; i >= 1; i--) {
    const y = (i / leftN) * 100 + (rng() - 0.5) * 3
    const depth = 1.5 + rng() * 2.8 + (i % 2 ? 0.6 : 0)
    pts.push([depth, Math.min(97.5, Math.max(2.5, y))])
  }

  return pts.map((p) => `${p[0].toFixed(1)}% ${p[1].toFixed(1)}%`).join(', ')
}

/** 按 id 重算撕边，算法升级后旧胶条也会更新 */
export function resolveTapeClip(layer) {
  if (!layer) return createTapeClip('tape')
  return createTapeClip(layer.id || layer.clip || 'tape')
}

/** 默认胶条宽度（相对页高；对开时 y/h 仍按页高归一化） */
export function defaultTapeThick(_spread) {
  // 约页高 5.8%，接近常见美纹纸/和纸胶带观感
  return 0.058
}

/** 由两点拖出胶条几何 */
export function tapeFromDrag(x0, y0, x1, y1, thick) {
  const dx = x1 - x0
  const dy = y1 - y0
  const len = Math.max(0.04, Math.hypot(dx, dy))
  const rot = Math.atan2(dy, dx)
  const cx = (x0 + x1) / 2
  const cy = (y0 + y1) / 2
  const h = thick
  return {
    x: cx - len / 2,
    y: cy - h / 2,
    w: len,
    h,
    rot
  }
}

/** 旋转后的轴对齐包围盒 */
export function tapeAabb(layer) {
  const w = layer.w || 0.12
  const h = layer.h || 0.058
  const cx = (layer.x || 0) + w / 2
  const cy = (layer.y || 0) + h / 2
  const rot = layer.rot || 0
  const c = Math.abs(Math.cos(rot))
  const s = Math.abs(Math.sin(rot))
  const hw = (w * c + h * s) / 2
  const hh = (w * s + h * c) / 2
  return { x: cx - hw, y: cy - hh, w: hw * 2, h: hh * 2 }
}

/**
 * 按旋转后 AABB 钳制胶条位置，四边最多探出 hangFrac。
 * 斜放长胶带若只按未旋转 h 钳 Y，两端会大量甩出页外被 overflow 裁掉。
 */
export function clampTapePosition(x, y, w, h, rot, { hangFrac = 0.35 } = {}) {
  const tw = Math.max(0.04, w || 0.12)
  const th = Math.max(0.02, h || 0.058)
  const r = rot || 0
  const c = Math.abs(Math.cos(r))
  const s = Math.abs(Math.sin(r))
  const hw = (tw * c + th * s) / 2
  const hh = (tw * s + th * c) / 2
  const hangX = Math.min(0.08, Math.max(0.01, hw * 2 * hangFrac))
  const hangY = Math.min(0.08, Math.max(0.01, hh * 2 * hangFrac))
  let cx = (x || 0) + tw / 2
  let cy = (y || 0) + th / 2
  const minCx = -hangX + hw
  const maxCx = 1 + hangX - hw
  const minCy = -hangY + hh
  const maxCy = 1 + hangY - hh
  cx = minCx <= maxCx ? Math.min(maxCx, Math.max(minCx, cx)) : 0.5
  cy = minCy <= maxCy ? Math.min(maxCy, Math.max(minCy, cy)) : 0.5
  return { x: cx - tw / 2, y: cy - th / 2, w: tw, h: th }
}

function aabbOverlap(a, b, pad = 0) {
  if (!a || !b) return false
  return !(
    a.x + a.w < b.x - pad ||
    b.x + b.w < a.x - pad ||
    a.y + a.h < b.y - pad ||
    b.y + b.h < a.y - pad
  )
}

function layerAabb(layer) {
  if (!layer) return null
  if (layer.type === 'tape') return tapeAabb(layer)
  return {
    x: layer.x || 0,
    y: layer.y || 0,
    w: layer.w || 0.3,
    h:
      layer.h ||
      (layer.type === 'textbox' ? 0.08 : layer.type === 'ticket' ? 0.25 : 0.28)
  }
}

/** 胶条是否与某贴图/文字框/车票几何重叠（同坐标系） */
export function tapePinsLayer(tape, target) {
  if (!tape || !target) return false
  if (target.type !== 'media' && target.type !== 'textbox' && target.type !== 'ticket') {
    return false
  }
  return aabbOverlap(tapeAabb(tape), layerAabb(target), -0.002)
}

/**
 * 目标是否被钉住：必须有胶条叠在它「上面」（图层顺序更后），
 * 盖在胶条之上、自身没有胶条的图/卡片仍可拿起。
 * @param {object} target
 * @param {Array} orderedLayers 按从下到上的图层列表（含 media / tape / ticket）
 */
export function isLayerPinned(target, orderedLayers) {
  if (!target || !orderedLayers?.length) return false
  if (target.type !== 'media' && target.type !== 'textbox' && target.type !== 'ticket') {
    return false
  }
  const tid = target._pageId || target.id
  const targetIdx = orderedLayers.findIndex((l) => l && (l._pageId || l.id) === tid)
  if (targetIdx < 0) return false
  for (let i = targetIdx + 1; i < orderedLayers.length; i++) {
    const l = orderedLayers[i]
    if (l && l.type === 'tape' && tapePinsLayer(l, target)) return true
  }
  return false
}

/**
 * 显示用叠放顺序 = layers 数组顺序（后写入的在上）。
 * 不要按几何重叠把胶条抬到贴图上方，否则后导入的图一拖到胶条区会「钻到下面」。
 * 钉住规则见 isLayerPinned：只有胶条在目标之上且重叠才固定。
 */
export function buildOverlayStack(layers) {
  return (layers || []).filter(
    (l) => l && (l.type === 'media' || l.type === 'tape' || l.type === 'ticket')
  )
}

/** 点是否命中胶条（本地未旋转框近似 + 旋转逆变换） */
export function hitTestTape(layer, x, y) {
  if (!layer || layer.type !== 'tape') return false
  const w = layer.w || 0.12
  const h = layer.h || 0.058
  const cx = (layer.x || 0) + w / 2
  const cy = (layer.y || 0) + h / 2
  const rot = layer.rot || 0
  const dx = x - cx
  const dy = y - cy
  const c = Math.cos(-rot)
  const s = Math.sin(-rot)
  const lx = dx * c - dy * s
  const ly = dx * s + dy * c
  const pad = Math.max(0.008, h * 0.28)
  return Math.abs(lx) <= w / 2 + pad * 0.2 && Math.abs(ly) <= h / 2 + pad
}

function parseClipPoints(clip, tw, th) {
  if (!clip) {
    return [
      [0, 0],
      [tw, 0],
      [tw, th],
      [0, th]
    ]
  }
  return String(clip)
    .split(',')
    .map((part) => {
      const [a, b] = part.trim().split(/\s+/)
      return [(parseFloat(a) / 100) * tw, (parseFloat(b) / 100) * th]
    })
    .filter((p) => Number.isFinite(p[0]) && Number.isFinite(p[1]))
}

/** 贴图未就绪时的占位色（接近素材灰白） */
const TAPE_FALLBACK = '#d8d4cc'

/** 在页画布上绘制一条胶条图层（与 HTML 贴图层观感一致） */
export function drawTapeLayerOnCtx(g, layer, W, H, onReady) {
  if (!layer || layer.type !== 'tape') return
  const w = Math.max(4, (layer.w || 0.12) * W)
  const h = Math.max(4, (layer.h || 0.058) * H)
  const cx = ((layer.x || 0) + (layer.w || 0.12) / 2) * W
  const cy = ((layer.y || 0) + (layer.h || 0.058) / 2) * H
  const rot = layer.rot || 0
  const img = ensureMaskingTapeImage(onReady)

  g.save()
  g.translate(cx, cy)
  g.rotate(rot)
  g.translate(-w / 2, -h / 2)

  if (img) {
    g.imageSmoothingEnabled = true
    g.imageSmoothingQuality = 'high'
    // 普通 alpha 叠放（勿用 multiply，否则浅色胶带在车票上会「消失」像被压住）
    g.globalAlpha = 0.96
    g.drawImage(img, 0, 0, w, h)
  } else {
    // 贴图加载前用撕边 clip + 灰白底占位，避免翻页烘焙空白
    const pts = parseClipPoints(resolveTapeClip(layer), w, h)
    g.fillStyle = TAPE_FALLBACK
    g.globalAlpha = 0.96
    if (pts.length >= 3) {
      g.beginPath()
      pts.forEach((p, i) => (i === 0 ? g.moveTo(p[0], p[1]) : g.lineTo(p[0], p[1])))
      g.closePath()
      g.fill()
    } else {
      g.fillRect(0, 0, w, h)
    }
  }
  g.restore()
}

/** 对开半页烘焙 */
export function drawSpreadTapeOnPage(g, layer, W, H, side, onReady) {
  if (!layer || layer.type !== 'tape') return
  const x0 = layer.x || 0
  const y0 = layer.y || 0
  const w0 = layer.w || 0.12
  const h0 = layer.h || 0.058
  const pageX0 = side === 'left' ? 0 : 0.5
  const pageX1 = side === 'left' ? 0.5 : 1
  const aabb = tapeAabb(layer)
  if (aabb.x + aabb.w < pageX0 - 0.01 || aabb.x > pageX1 + 0.01) return

  const local = {
    ...layer,
    x: side === 'left' ? x0 * 2 : (x0 - 0.5) * 2,
    w: w0 * 2,
    y: y0,
    h: h0
  }
  g.save()
  g.beginPath()
  g.rect(0, 0, W, H)
  g.clip()
  drawTapeLayerOnCtx(g, local, W, H, onReady)
  g.restore()
}
