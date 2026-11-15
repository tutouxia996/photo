/**
 * Canvas painter port of SpektraLab `FilmPainter` (developed style)
 * + perforation / stripe geometry from `overscan.cpp`.
 * GPL-3.0-or-later — derived from JamesQiu2005/SpektraLab.
 */

import { COLORS } from './formats'
import { buildStripLayout, filmToCanvasPx } from './layout'
import { inkCss } from './stocks'

function roundRect(ctx, x, y, w, h, r) {
  const rr = Math.min(Math.abs(r), Math.abs(w) / 2, Math.abs(h) / 2)
  ctx.beginPath()
  ctx.moveTo(x + rr, y)
  ctx.arcTo(x + w, y, x + w, y + h, rr)
  ctx.arcTo(x + w, y + h, x, y + h, rr)
  ctx.arcTo(x, y + h, x, y, rr)
  ctx.arcTo(x, y, x + w, y, rr)
  ctx.closePath()
}

/** Film point (s along, t across) → canvas px */
function st(map, s, t) {
  if (map.vertical) return { x: map.mapT(t), y: map.mapS(s) }
  return { x: map.mapS(s), y: map.mapT(t) }
}

function ppmPair(L, map) {
  return {
    along: map.vertical ? map.h / L.canvasAlong : map.w / L.canvasAlong,
    across: map.vertical ? map.w / L.canvasAcross : map.h / L.canvasAcross
  }
}

/**
 * SpektraLab FilmGeometry.holes:
 *   t = [perf.edge, filmW - perf.edge - perf.across]
 *   rect(s, t, along=1.98, across=2.80)  — s 是齿孔前缘，不是中心
 * 因此齿孔从片边 2.0mm 起，字可以落在 0~2mm 的外侧条带里。
 */
function drawHoles(ctx, L, map) {
  if (!L.perf) return
  const { pitch, w: along, h: across, edge, r } = L.perf
  const ppm = ppmPair(L, map)
  ctx.fillStyle = L.holesWhite ? COLORS.holeWhite : COLORS.holeBlack

  const rowsT = [edge, L.filmW - edge - across]
  // s = leading edge of hole (FilmGeometry)
  let k = Math.floor((L.sLo - 6 - L.perfPhase) / pitch)
  while (L.perfPhase + k * pitch < L.sHi + 6) {
    const s = L.perfPhase + k * pitch
    for (const t of rowsT) {
      const p = st(map, s, t)
      const rw = along * ppm.along
      const rh = across * ppm.across
      const rr = r * Math.min(ppm.along, ppm.across)
      if (map.vertical) {
        roundRect(ctx, p.x, p.y, rh, rw, rr)
      } else {
        roundRect(ctx, p.x, p.y, rw, rh, rr)
      }
      ctx.fill()
    }
    k += 1
  }
}

function drawStripes(ctx, L, map) {
  if (!L.stripes) return
  const ppm = ppmPair(L, map)
  const { g, gw, r, rw } = L.stripes
  const bands = [
    { t: g - gw / 2, th: gw, color: 'rgba(47, 107, 69, 0.55)' },
    { t: L.filmW - g - gw / 2, th: gw, color: 'rgba(47, 107, 69, 0.55)' },
    { t: r - rw / 2, th: rw, color: 'rgba(180, 58, 50, 0.55)' },
    { t: L.filmW - r - rw / 2, th: rw, color: 'rgba(180, 58, 50, 0.55)' }
  ]
  for (const b of bands) {
    ctx.fillStyle = b.color
    const p0 = st(map, L.sLo, b.t)
    if (map.vertical) {
      ctx.fillRect(p0.x, p0.y, b.th * ppm.across, map.h)
    } else {
      ctx.fillRect(p0.x, p0.y, map.w, b.th * ppm.across)
    }
  }
}

/** 只允许画在齿孔外侧两条安全带里（t∈[0,edge) ∪ (W-edge,W]） */
function clipOuterBands(ctx, L, map) {
  if (!L.perf) return
  const edge = L.perf.edge
  const W = L.filmW
  ctx.beginPath()
  if (map.vertical) {
    const a0 = map.mapT(0)
    const a1 = map.mapT(edge * 0.98)
    const b0 = map.mapT(W - edge * 0.98)
    const b1 = map.mapT(W)
    ctx.rect(Math.min(a0, a1), 0, Math.abs(a1 - a0), map.h)
    ctx.rect(Math.min(b0, b1), 0, Math.abs(b1 - b0), map.h)
  } else {
    const a0 = map.mapT(0)
    const a1 = map.mapT(edge * 0.98)
    const b0 = map.mapT(W - edge * 0.98)
    const b1 = map.mapT(W)
    ctx.rect(0, Math.min(a0, a1), map.w, Math.abs(a1 - a0))
    ctx.rect(0, Math.min(b0, b1), map.w, Math.abs(b1 - b0))
  }
  ctx.clip()
}

/**
 * DX 条：整条落在外侧 ~2mm。Ilford 在 lead（顶），Kodak/Fuji 在 trail（底）。
 */
function drawDxBars(ctx, L, map) {
  const look = L.look
  if (!L.perf || look.dx == null) return
  if (!(look.k135 === 'kodak' || look.k135 === 'fujiNeg' || look.k135 === 'ilford')) return
  const ppm = ppmPair(L, map)
  const W = L.filmW
  const edge = L.perf.edge
  const length = look.k135 === 'fujiNeg' ? 14.1 : look.k135 === 'ilford' ? 13.06 : 12.7
  const mod = length / 31
  ctx.fillStyle = inkCss(L.gauge === '120' ? look.ink120 : look.ink135)

  // 避开格号：DX 靠画幅前段；格号在后段
  const s0 = L.gateS0 + 1.2
  const ilford = look.k135 === 'ilford'
  // 两条轨道挤在外侧带内，绝不碰到 edge
  const band = edge * 0.92
  const clockT0 = ilford ? 0.12 : W - band
  const dataT0 = ilford ? band * 0.48 : W - band * 0.52
  const dataT1 = ilford ? band * 0.92 : W - 0.12

  ctx.save()
  clipOuterBands(ctx, L, map)

  let n = look.dx | 0
  for (let i = 0; i < 31; i++) {
    const on = (n >> (i % 16)) & 1
    n = (n * 1103515245 + 12345) >>> 0
    if (!on) continue
    const s = s0 + i * mod
    const barAlong = Math.max(1, mod * 0.62 * ppm.along)
    const c0 = st(map, s, clockT0)
    const c1 = st(map, s, dataT0)
    const d0 = st(map, s, dataT0)
    const d1 = st(map, s, dataT1)
    if (map.vertical) {
      ctx.fillRect(Math.min(c0.x, c1.x), c0.y, Math.abs(c1.x - c0.x), barAlong)
      ctx.fillRect(Math.min(d0.x, d1.x), d0.y, Math.abs(d1.x - d0.x), barAlong)
    } else {
      ctx.fillRect(c0.x, Math.min(c0.y, c1.y), barAlong, Math.abs(c1.y - c0.y))
      ctx.fillRect(d0.x, Math.min(d0.y, d1.y), barAlong, Math.abs(d1.y - d0.y))
    }
  }
  ctx.restore()
}

/**
 * 片边字：em 高度必须 ≤ 外侧带宽，middle 居中。
 * Ilford：名称只在 trail，格号只在 lead，避免「3」压在 HP5 上。
 */
function drawEdgePrint(ctx, L, map) {
  const look = L.look
  const ink = inkCss(L.gauge === '120' ? look.ink120 : look.ink135)
  const ppm = ppmPair(L, map)
  const W = L.filmW
  const light = !!look.lightNumbers
  const style = look.k135
  const edge = L.perf?.edge ?? 2.0
  // 可用带宽（略缩，给齿孔留缝）
  const bandH = edge * 0.88
  const bandMid = (side) => (side === 'lead' ? 0.08 + bandH * 0.5 : W - 0.08 - bandH * 0.5)
  // middle + em 高度：字体 px 按 band 限制，不再用 cap/kCap 撑破条带
  const sizeForBand = () => bandH * 0.78

  let nameSide = 'lead'
  let sName = L.gateS0 + (light ? 5.1 : 6.4)
  // 顶带格号靠右，底带 nA 靠左偏中，与 DX（靠左）错开
  let sTopNum = L.gateS0 + L.gateAlong - 5.5
  let sBotA = L.gateS0 + 16.5
  let sArrow = L.gateS0 + 20.5

  if (style === 'ilford') {
    nameSide = 'trail'
    sName = L.gateS0 + 3.5
    sTopNum = L.gateS0 + L.gateAlong - 5.0
  } else if (style === 'cine') {
    nameSide = 'trail'
    sName = L.gateS0 + 10.0
  } else if (style === 'fujiNeg' || style === 'fujiSlide') {
    sName = L.gateS0 + 5.5
  }

  const font =
    style === 'fujiNeg' || style === 'fujiSlide' || style === 'cine'
      ? `"Courier New", monospace`
      : style === 'ilford'
        ? `"Gill Sans", "Trebuchet MS", sans-serif`
        : `"Helvetica Neue", Helvetica, Arial, sans-serif`

  ctx.save()
  clipOuterBands(ctx, L, map)
  ctx.fillStyle = ink

  const fillAlong = (text, s, side, scale = 1, weight = '700') => {
    if (!text) return
    const midT = bandMid(side)
    const sizeMm = sizeForBand() * scale
    const px = Math.max(4, sizeMm * Math.min(ppm.along, ppm.across))
    ctx.font = `${weight} ${px}px ${font}`
    ctx.textBaseline = 'middle'
    ctx.textAlign = 'left'
    const p = st(map, s, midT)
    ctx.save()
    if (map.vertical) {
      ctx.translate(p.x, p.y)
      ctx.rotate(Math.PI / 2)
      ctx.fillText(text, 0, 0)
    } else {
      ctx.fillText(text, p.x, p.y)
    }
    ctx.restore()
  }

  if (style === 'ilford') {
    // 名称独占底带；顶带只放 nA（DX 在顶带前段，字在后段）
    fillAlong(L.edgeText, sName, 'trail', 0.92, '700')
    fillAlong(`${L.frameNo}A`, sTopNum, 'lead', 0.85, light ? '500' : '700')
  } else if (style === 'cine') {
    fillAlong(L.edgeText, sName, 'trail', 0.75, '700')
  } else {
    fillAlong(L.edgeText, sName, nameSide, light ? 0.85 : 0.92, '700')
    fillAlong(String(L.frameNo), sTopNum, 'lead', light ? 0.9 : 1, light ? '500' : '700')
    fillAlong(`${L.frameNo}A`, sBotA, 'trail', 0.72, light ? '500' : '700')
    fillAlong('→', sArrow, 'trail', 0.6, '700')
  }
  ctx.restore()
}

/** 日期后背：相对「画面」底边左右角，不是相对胶片 s/t（竖片时两者不同） */
function drawDateBack(ctx, L, map) {
  if (!L.dateText && !L.placeText) return
  const ppm = ppmPair(L, map)
  const gate = L.picture
  const pad = 1.15

  // 片窗像素矩形
  const p0 = st(map, gate.s0, gate.t0)
  let gx
  let gy
  let gw
  let gh
  if (map.vertical) {
    gx = p0.x
    gy = p0.y
    gw = gate.across * ppm.across
    gh = gate.along * ppm.along
  } else {
    gx = p0.x
    gy = p0.y
    gw = gate.along * ppm.along
    gh = gate.across * ppm.across
  }

  const inset = pad * Math.min(ppm.along, ppm.across)
  ctx.save()
  ctx.beginPath()
  ctx.rect(gx, gy, gw, gh)
  ctx.clip()
  ctx.globalCompositeOperation = 'lighter'
  ctx.fillStyle = COLORS.dateLight
  ctx.textBaseline = 'bottom'
  ctx.shadowColor = 'rgba(255, 120, 30, 0.85)'
  ctx.shadowBlur = 1.2 * Math.min(ppm.along, ppm.across)

  if (L.dateText) {
    ctx.font = `700 ${Math.max(9, 1.45 * Math.min(ppm.along, ppm.across))}px "Courier New", monospace`
    ctx.textAlign = 'right'
    ctx.fillText(L.dateText, gx + gw - inset, gy + gh - inset)
  }
  if (L.placeText) {
    ctx.font = `700 ${Math.max(8, 1.15 * Math.min(ppm.along, ppm.across))}px "Courier New", monospace`
    ctx.textAlign = 'left'
    ctx.fillText(L.placeText, gx + inset, gy + gh - inset)
  }
  ctx.restore()
}

function drawGateImage(ctx, image, L, map) {
  const ppmAlong = map.vertical ? map.h / L.canvasAlong : map.w / L.canvasAlong
  const ppmAcross = map.vertical ? map.w / L.canvasAcross : map.h / L.canvasAcross
  const gate = L.picture
  const p0 = st(map, gate.s0, gate.t0)
  let gx, gy, gw, gh
  if (map.vertical) {
    gx = p0.x
    gy = p0.y
    gw = gate.across * ppmAcross
    gh = gate.along * ppmAlong
  } else {
    gx = p0.x
    gy = p0.y
    gw = gate.along * ppmAlong
    gh = gate.across * ppmAcross
  }

  const nw = image.naturalWidth || image.videoWidth || image.width
  const nh = image.naturalHeight || image.videoHeight || image.height
  ctx.save()
  ctx.beginPath()
  ctx.rect(gx, gy, gw, gh)
  ctx.clip()
  const ir = nw / nh
  const gr = gw / gh
  let dw, dh, dx, dy
  if (ir > gr) {
    dh = gh
    dw = gh * ir
    dx = gx - (dw - gw) / 2
    dy = gy
  } else {
    dw = gw
    dh = gw / ir
    dx = gx
    dy = gy - (dh - gh) / 2
  }
  ctx.drawImage(image, dx, dy, dw, dh)
  ctx.restore()
}

/**
 * @param {HTMLCanvasElement} canvas
 * @param {CanvasImageSource} image
 * @param {object} opts
 */
export function renderFilmStrip(canvas, image, opts) {
  const showBorder = opts.showBorder !== false
  const showInfo = opts.showInfo !== false
  const nw = image.naturalWidth || image.videoWidth || image.width
  const nh = image.naturalHeight || image.videoHeight || image.height
  if (!nw || !nh) return null

  if (!showBorder && !showInfo) {
    const maxEdge = opts.maxLongEdge || Math.max(nw, nh)
    const scale = maxEdge / Math.max(nw, nh)
    const w = Math.max(1, Math.round(nw * scale))
    const h = Math.max(1, Math.round(nh * scale))
    canvas.width = w
    canvas.height = h
    const ctx = canvas.getContext('2d')
    ctx.clearRect(0, 0, w, h)
    ctx.drawImage(image, 0, 0, w, h)
    return { width: w, height: h }
  }

  const L = buildStripLayout({
    stockId: opts.stockId,
    formatId: opts.formatId,
    frameW: nw,
    frameH: nh,
    frameNo: opts.frameNo,
    holesWhite: opts.holesWhite,
    dateText: showInfo ? opts.dateText : '',
    placeText: showInfo ? opts.placeText : ''
  })

  const maxLong = opts.maxLongEdge || 1200
  const canvasMMLong = Math.max(L.canvasAlong, L.canvasAcross)
  const ppm = maxLong / canvasMMLong
  const map = filmToCanvasPx(L, ppm)
  const outW = Math.max(1, Math.round(map.w))
  const outH = Math.max(1, Math.round(map.h))
  canvas.width = outW
  canvas.height = outH
  const ctx = canvas.getContext('2d')
  ctx.imageSmoothingEnabled = true
  ctx.imageSmoothingQuality = 'high'

  if (showBorder) {
    ctx.fillStyle = COLORS.rebatePrint
    ctx.fillRect(0, 0, outW, outH)
    // 片窗 → 彩条 → 片边字/DX → 最后打齿孔（不透明白盖住任何擦边字墨）
    drawGateImage(ctx, image, L, map)
    drawStripes(ctx, L, map)
    drawDxBars(ctx, L, map)
    drawEdgePrint(ctx, L, map)
    drawHoles(ctx, L, map)
  } else {
    ctx.drawImage(image, 0, 0, outW, outH)
  }

  // 日期后背在片窗内，齿孔打完后再画，避免被孔盖住
  if (showInfo) drawDateBack(ctx, L, map)
  return { width: outW, height: outH, layout: L }
}
