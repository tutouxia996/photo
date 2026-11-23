/**
 * Hobonichi-style page curl — strip projection + soft lighting
 * (adapted from the hinged-leaf approach used by Claude's Hobonichi)
 */

const HC = 2800

export function clamp(v, a, b) {
  return v < a ? a : v > b ? b : v
}

export function turnEase(u) {
  u = clamp(u, 0, 1)
  return u * u * (2.2 - 1.2 * u)
}

export function smoothstep(u) {
  u = clamp(u, 0, 1)
  return u * u * (3 - 2 * u)
}

/** curl state from progress 0..1 */
export function curlState(u, isBoard = false) {
  u = clamp(u, 0, 1)
  const th = Math.PI * (isBoard ? smoothstep(u) : turnEase(u))
  const C = isBoard ? 0.18 : 0.58
  let c = C * Math.sin((Math.PI * (u - 0.14)) / 1.0)
  if (u < 0.14) c = -C * 0.55 * Math.sin((Math.PI * u) / 0.14)
  if (u > 0.92) c *= (1 - u) / 0.08
  return { th, c, u }
}

export function makePhi(th, c) {
  return (t) => clamp(th - c * Math.pow(t, 1.7), 0, Math.PI)
}

export function leafPoints(W, phi, N = 72) {
  const X = [0]
  const Z = [0]
  const PH = []
  const d = W / N
  for (let i = 0; i < N; i++) {
    const p = clamp(phi((i + 0.5) / N), 0, Math.PI)
    PH.push(p)
    X.push(X[i] + Math.cos(p) * d)
    Z.push(Z[i] + Math.sin(p) * d)
  }
  return { X, Z, PH, d, N }
}

function proj(camX, camY, x, y, z) {
  const f = HC / (HC - z)
  return [camX + (x - camX) * f, camY + (y - camY) * f, f]
}

/**
 * Draw a curling leaf into ctx.
 * Single curl profile across the full height (like the Hobonichi canvas fallback),
 * so the page stays one continuous sheet — no per-row lag strips.
 * Spine at x=0; page extends +W when flat.
 */
export function drawCurlingLeaf(ctx, {
  front,
  back,
  W,
  H,
  y0 = 0,
  progress = 0,
  isBoard = false,
  camX = 0,
  camY = null,
  strips = 96,
  shade = 1,
  edge = 'rgba(110,92,62,.28)',
  edgeW = 0.7,
  curlBias = 0
}) {
  if (camY == null) camY = y0 + H * 0.5
  const layer = getLayer(ctx.canvas.width, ctx.canvas.height)
  const lc = layer.getContext('2d')
  const T = ctx.getTransform()
  lc.setTransform(1, 0, 0, 1, 0, 0)
  lc.clearRect(0, 0, layer.width, layer.height)
  lc.setTransform(T.a, T.b, T.c, T.d, T.e, T.f)

  const amp = isBoard ? 0.35 : 1
  const st = curlState(progress, isBoard)
  const phi = makePhi(st.th, (st.c + curlBias) * amp)
  const N = Math.max(24, strips | 0)
  const L = leafPoints(W, phi, N)
  const light = [-0.34, 0.94]
  const sx = front.width / W
  const order = []
  for (let i = 0; i < L.N; i++) order.push(i)
  order.sort((a, b) => (L.Z[a] + L.Z[a + 1]) - (L.Z[b] + L.Z[b + 1]))

  const stops = []
  order.forEach((i) => {
    const p = L.PH[i]
    const up = Math.cos(p) >= 0
    const A = proj(camX, camY, L.X[i], 0, L.Z[i])
    const B = proj(camX, camY, L.X[i + 1], 0, L.Z[i + 1])
    const fm = (A[2] + B[2]) / 2
    const top = camY + (y0 - camY) * fm
    const bot = camY + (y0 + H - camY) * fm
    const x0 = Math.min(A[0], B[0])
    const x1 = Math.max(A[0], B[0])
    // 略加重叠，避免条带缝隙
    const dw = x1 - x0 + 1.15
    const src = up ? front : back
    const u = up ? i * L.d : W - (i + 1) * L.d
    if (dw > 0.05) {
      lc.drawImage(
        src,
        u * sx, 0, Math.max(0.5, L.d * sx), src.height,
        x0 - 0.55, top, dw, bot - top
      )
    }
    const n = up ? [-Math.sin(p), Math.cos(p)] : [Math.sin(p), -Math.cos(p)]
    const lam = Math.max(0, n[0] * light[0] + n[1] * light[1])
    stops.push([(x0 + x1) / 2, (1 - (0.78 + 0.22 * lam)) * shade, lam])
  })

  // 整页一次打光，避免条带接缝露馅
  if (stops.length) {
    stops.sort((a, b) => a[0] - b[0])
    const X0 = stops[0][0]
    const X1 = stops[stops.length - 1][0]
    if (X1 - X0 > 0.5) {
      const gr = lc.createLinearGradient(X0, 0, X1, 0)
      const gh = lc.createLinearGradient(X0, 0, X1, 0)
      let last = -1
      stops.forEach((s) => {
        const t = (s[0] - X0) / (X1 - X0)
        if (t <= last) return
        last = t
        gr.addColorStop(t, `rgba(70,54,32,${Math.min(0.48, s[1]).toFixed(3)})`)
        // 纸页高光阈值：反转片光泽在胶片贴图本身，不靠翻页条带抛光
        gh.addColorStop(t, `rgba(255,252,240,${Math.max(0, (s[2] - 0.96) * 0.55).toFixed(3)})`)
      })
      lc.save()
      lc.globalCompositeOperation = 'source-atop'
      lc.fillStyle = gr
      lc.fillRect(X0 - 60, y0 - 40, X1 - X0 + 120, H + 80)
      lc.fillStyle = gh
      lc.fillRect(X0 - 60, y0 - 40, X1 - X0 + 120, H + 80)
      lc.restore()
    }
  }

  // 纸边轮廓
  lc.save()
  lc.strokeStyle = edge
  lc.lineWidth = isBoard ? Math.max(1.1, edgeW) : edgeW
  lc.lineJoin = 'round'
  lc.beginPath()
  for (let i = 0; i <= L.N; i++) {
    const q = proj(camX, camY, L.X[i], 0, L.Z[i])
    const yy = camY + (y0 - camY) * q[2]
    if (i) lc.lineTo(q[0], yy)
    else lc.moveTo(q[0], yy)
  }
  const E = proj(camX, camY, L.X[L.N], 0, L.Z[L.N])
  lc.lineTo(E[0], camY + (y0 + H - camY) * E[2])
  for (let i = L.N; i >= 0; i--) {
    const q2 = proj(camX, camY, L.X[i], 0, L.Z[i])
    lc.lineTo(q2[0], camY + (y0 + H - camY) * q2[2])
  }
  lc.closePath()
  lc.stroke()
  lc.restore()

  ctx.save()
  ctx.setTransform(1, 0, 0, 1, 0, 0)
  ctx.drawImage(layer, 0, 0)
  ctx.restore()

  return {
    edgeX: L.X[L.N],
    edgeZ: L.Z[L.N],
    th: st.th
  }
}

export function drawLeafShadow(ctx, {
  W, H, y0 = 0, edgeX, edgeZ, th, camX = 0, camY = null, halfWidth
}) {
  if (edgeZ < 0.5) return
  if (camY == null) camY = y0 + H * 0.5
  const k = Math.sin(clamp(th, 0, Math.PI))
  const hi = Math.min(1, edgeZ / W)
  const a = 0.28 * Math.min(1, k * 1.35 + 0.15) * (1 - 0.55 * hi)
  const s = 14 + edgeZ * 0.22
  const px = proj(camX, camY, edgeX, 0, edgeZ)[0]
  const dir = edgeX >= 0 ? 1 : -1

  ctx.save()
  if (halfWidth != null) {
    ctx.beginPath()
    ctx.rect(dir > 0 ? 0 : -halfWidth, y0, halfWidth, H)
    ctx.clip()
  }
  ctx.filter = `blur(${(3 + edgeZ * 0.014).toFixed(1)}px)`
  const x0 = px - dir * 6
  const x1 = px + dir * s
  const gr = ctx.createLinearGradient(x0, 0, x1, 0)
  gr.addColorStop(0, `rgba(60,45,25,${a.toFixed(3)})`)
  gr.addColorStop(0.4, `rgba(60,45,25,${(a * 0.5).toFixed(3)})`)
  gr.addColorStop(1, 'rgba(60,45,25,0)')
  ctx.fillStyle = gr
  ctx.fillRect(Math.min(x0, x1), y0 + 2, Math.abs(x1 - x0), H - 4)

  const reach = Math.min(Math.abs(px), W * 0.22) * dir
  const gs = ctx.createLinearGradient(0, 0, reach, 0)
  gs.addColorStop(0, `rgba(60,45,25,${(0.12 * k).toFixed(3)})`)
  gs.addColorStop(1, 'rgba(60,45,25,0)')
  ctx.filter = 'none'
  ctx.fillStyle = gs
  ctx.fillRect(Math.min(0, reach), y0, Math.abs(reach), H)
  ctx.restore()
}

/** paint a blank Tomoe-like page */
export function paintBlankPage(c, w, h) {
  const g = c.getContext('2d')
  g.fillStyle = '#fbf9f1'
  g.fillRect(0, 0, w, h)
  g.fillStyle = 'rgba(146,128,92,0.03)'
  for (let i = 0; i < 1200; i++) {
    g.fillRect(Math.random() * w, Math.random() * h, 1, 1)
  }
}

export function paintCoverFront(c, w, h, title, sub) {
  const g = c.getContext('2d')
  const grd = g.createLinearGradient(0, 0, w, h)
  grd.addColorStop(0, '#1c2a4a')
  grd.addColorStop(0.5, '#15233d')
  grd.addColorStop(1, '#101a30')
  g.fillStyle = grd
  g.fillRect(0, 0, w, h)
  g.fillStyle = 'rgba(255,255,255,0.06)'
  g.beginPath()
  g.ellipse(w * 0.3, h * 0.2, w * 0.35, h * 0.2, 0, 0, Math.PI * 2)
  g.fill()
  g.fillStyle = '#f4efe4'
  g.textAlign = 'center'
  g.font = `500 ${Math.round(w * 0.09)}px "Zen Kaku Gothic New", "Noto Sans SC", sans-serif`
  g.fillText(title || '相册日记', w / 2, h * 0.46)
  g.globalAlpha = 0.55
  g.font = `300 ${Math.round(w * 0.032)}px Jost, sans-serif`
  g.fillText(sub || 'HOBONICHI STYLE', w / 2, h * 0.52)
  g.globalAlpha = 1
}

export function paintCoverInside(c, w, h) {
  const g = c.getContext('2d')
  g.fillStyle = '#f3eee3'
  g.fillRect(0, 0, w, h)
}

export function paintCoverBack(c, w, h, year) {
  const g = c.getContext('2d')
  const grd = g.createLinearGradient(0, 0, w, h)
  grd.addColorStop(0, '#1c2a4a')
  grd.addColorStop(1, '#101a30')
  g.fillStyle = grd
  g.fillRect(0, 0, w, h)
  g.fillStyle = 'rgba(244,239,228,0.65)'
  g.textAlign = 'center'
  g.font = `300 ${Math.round(w * 0.08)}px Jost, sans-serif`
  g.fillText(String(year || ''), w / 2, h * 0.5)
}

export function makeCanvas(w, h) {
  const c = document.createElement('canvas')
  c.width = Math.max(2, Math.round(w))
  c.height = Math.max(2, Math.round(h))
  return c
}

let _layer = null
function getLayer(w, h) {
  if (!_layer || _layer.width !== w || _layer.height !== h) {
    _layer = makeCanvas(w, h)
  }
  return _layer
}
