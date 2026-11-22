/**
 * 日记页图层模型：归一化坐标 0–1。
 * - space:'page'（默认）：单页坐标
 * - space:'spread'：整幅对开坐标（可跨订口，文字可放中间）
 *
 * 贴图建议用 space:'page'：对开显示时由 layerToSpread 映射，避免 spread 宽高比算错。
 */

/** A5 单页宽高比（宽/高），用于把图片像素比换成页内归一化 h */
export const PAGE_ASPECT = 148 / 210

export function emptyContent() {
  return { v: 1, layers: [] }
}

export function parseContent(raw) {
  if (!raw) return emptyContent()
  try {
    const obj = typeof raw === 'string' ? JSON.parse(raw) : raw
    if (!obj || !Array.isArray(obj.layers)) return emptyContent()
    return {
      v: obj.v || 1,
      layers: obj.layers.filter(Boolean).map(normalizeMediaLayer)
    }
  } catch {
    return emptyContent()
  }
}

/**
 * 修正贴图尺寸；跨订口的保持 space:spread（翻页时左右半幅分别烘焙），
 * 完全在一侧的用 page 坐标。
 */
export function normalizeMediaLayer(layer) {
  if (!layer || layer.type !== 'media') return layer
  const aspect = layer.aspect && layer.aspect > 0 ? layer.aspect : 4 / 3
  const y = Math.min(0.85, Math.max(0, layer.y || 0))

  if (layer.space === 'spread') {
    let x0 = layer.x || 0
    let w0 = layer.w || 0.2
    // 旧 bug：对开宽被存得过大
    if (w0 > 0.5) w0 = 0.22
    w0 = Math.min(0.55, Math.max(0.08, w0))
    x0 = Math.min(1 - w0, Math.max(0, x0))
    const crosses = x0 < 0.5 && x0 + w0 > 0.5
    if (crosses) {
      return {
        ...layer,
        space: 'spread',
        x: x0,
        y,
        w: w0,
        h: mediaHeightForWidth(w0, aspect, true),
        aspect: layer.aspect && layer.aspect > 0 ? layer.aspect : null
      }
    }
    // 不跨缝：落到单页
    const side = x0 + w0 / 2 < 0.5 ? 'left' : 'right'
    let x = side === 'left' ? x0 * 2 : (x0 - 0.5) * 2
    let w = w0 * 2
    if (w > 0.72) w = 0.42
    w = Math.min(0.72, Math.max(0.16, w))
    x = Math.min(1 - w, Math.max(0, x))
    return {
      ...layer,
      space: 'page',
      x,
      y,
      w,
      h: mediaHeightForWidth(w, aspect, false),
      aspect: layer.aspect && layer.aspect > 0 ? layer.aspect : null
    }
  }

  let x = Math.min(0.88, Math.max(0, layer.x || 0))
  let w = Math.min(0.85, Math.max(0.12, layer.w || 0.42))
  // 单页贴图伸出页缘（跨订口残留）：改成 spread，挂在对开坐标
  if (x + w > 1.02) {
    const sx = x * 0.5
    const sw = Math.min(0.55, w * 0.5)
    return {
      ...layer,
      space: 'spread',
      x: sx,
      y,
      w: sw,
      h: mediaHeightForWidth(sw, aspect, true),
      aspect: layer.aspect && layer.aspect > 0 ? layer.aspect : null
    }
  }
  if (x + w > 1) w = Math.max(0.12, 1 - x)

  const expectH = mediaHeightForWidth(w, aspect, false)
  const h = layer.h && layer.h > 0.05 ? layer.h : expectH
  const fixedH =
    layer.aspect && layer.aspect > 0 && Math.abs(h - expectH) / Math.max(expectH, 1e-6) > 0.25
      ? expectH
      : h
  return {
    ...layer,
    space: 'page',
    x,
    w,
    y,
    h: Math.min(0.72, Math.max(0.1, fixedH))
  }
}

/** 按目标显示宽（归一化）与图片宽高比，计算页内/对开归一化高度 */
export function mediaHeightForWidth(w, aspect, spread) {
  const a = aspect > 0 ? aspect : 4 / 3
  if (spread) {
    // 视觉比 = (w * 2 * pageW) / (h * pageH) = a  →  h = 2*w*PAGE_ASPECT/a
    return Math.min(0.62, Math.max(0.1, (2 * w * PAGE_ASPECT) / a))
  }
  return Math.min(0.72, Math.max(0.1, (w * PAGE_ASPECT) / a))
}

export function mediaWidthForPage(spread) {
  // 单页约 40% 宽；对开坐标系下为单页一半 → 0.2
  return spread ? 0.2 : 0.42
}

export function serializeContent(content) {
  const c = content && Array.isArray(content.layers) ? content : emptyContent()
  return JSON.stringify({ v: c.v || 1, layers: c.layers })
}

export function hasContent(content) {
  return !!(content && Array.isArray(content.layers) && content.layers.length)
}

export function newId(prefix = 'l') {
  return `${prefix}_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 8)}`
}

/** pageNo(1-based) → Hobonichi face key */
export function pageToFaceKey(pageNo) {
  const sheetIdx = pageNo - 1
  if (sheetIdx < 0) return null
  const leafStart = sheetIdx - (sheetIdx % 2)
  const id = `p-${leafStart}`
  return sheetIdx % 2 === 0 ? `${id}-f` : `${id}-b`
}

/** 偶数页为左、奇数页为右；返回对开伙伴页码 */
export function partnerPageNo(pageNo) {
  if (!pageNo || pageNo < 1) return null
  return pageNo % 2 === 1 ? (pageNo > 1 ? pageNo - 1 : null) : pageNo + 1
}

export function pageSide(pageNo) {
  if (!pageNo) return null
  return pageNo % 2 === 1 ? 'right' : 'left'
}

/** 打开的对开：left/right 页码（封面内页等不可编辑则为 null） */
export function spreadPageNos(bookIndex, pageCount) {
  const total = Math.max(0, pageCount | 0)
  const idx = bookIndex | 0
  let left = null
  let right = null
  if (idx >= 2) {
    const n = (idx - 1) * 2
    if (n >= 1 && n <= total) left = n
  }
  if (idx >= 1) {
    const n = (idx - 1) * 2 + 1
    if (n >= 1 && n <= total) right = n
  }
  return { left, right }
}

export function pagePointToSpread(x, y, side) {
  return [side === 'left' ? x * 0.5 : 0.5 + x * 0.5, y]
}

/** 把单页 layer 映射到对开坐标 */
export function layerToSpread(layer, side) {
  if (!layer) return null
  if (layer.space === 'spread') {
    return {
      ...layer,
      _side: 'spread',
      _pageId: layer.id,
      _host: side,
      // 存盘 width 按单页；对开画布宽度约 2 倍，显示时减半
      width: layer.type === 'stroke' ? (layer.width || 0.004) * 0.5 : layer.width
    }
  }
  if (layer.type === 'stroke') {
    return {
      ...layer,
      _side: side,
      _pageId: layer.id,
      points: (layer.points || []).map((p) => pagePointToSpread(p[0], p[1], side)),
      width: (layer.width || 0.004) * 0.5
    }
  }
  if (layer.type === 'textbox') {
    return {
      ...layer,
      _side: side,
      _pageId: layer.id,
      x: side === 'left' ? (layer.x || 0) * 0.5 : 0.5 + (layer.x || 0) * 0.5,
      w: (layer.w || 0.4) * 0.5
    }
  }
  if (layer.type === 'media') {
    return {
      ...layer,
      _side: side,
      _pageId: layer.id,
      x: side === 'left' ? (layer.x || 0) * 0.5 : 0.5 + (layer.x || 0) * 0.5,
      w: (layer.w || 0.32) * 0.5
    }
  }
  return { ...layer, _side: side, _pageId: layer.id }
}

/**
 * 合并左右页 → 对开显示 layers。
 * space:spread 只出现一次；旧版 _L/_R 拆笔尝试拼回。
 */
export function mergeSpreadLayers(leftContent, rightContent) {
  const out = []
  const seenSpread = new Set()
  const strokeParts = new Map() // baseId -> { L, R }

  function absorb(content, side) {
    for (const l of (content && content.layers) || []) {
      if (!l) continue
      if (l.space === 'spread') {
        if (seenSpread.has(l.id)) continue
        seenSpread.add(l.id)
        out.push(layerToSpread(l, side))
        continue
      }
      // 旧拆笔：id_L / id_R
      const m = typeof l.id === 'string' && l.id.match(/^(.*)_([LR])$/)
      if (l.type === 'stroke' && m) {
        const base = m[1]
        const part = m[2]
        if (!strokeParts.has(base)) strokeParts.set(base, {})
        strokeParts.get(base)[part] = { layer: l, side }
        continue
      }
      out.push(layerToSpread(l, side))
    }
  }

  absorb(leftContent, 'left')
  absorb(rightContent, 'right')

  for (const [base, parts] of strokeParts) {
    const pts = []
    const sample = (parts.L || parts.R).layer
    if (parts.L) {
      for (const p of parts.L.layer.points || []) pts.push(pagePointToSpread(p[0], p[1], 'left'))
    }
    if (parts.R) {
      for (const p of parts.R.layer.points || []) pts.push(pagePointToSpread(p[0], p[1], 'right'))
    }
    out.push({
      id: base,
      type: 'stroke',
      space: 'spread',
      tool: sample.tool || 'pen',
      color: sample.color || '#2B2A33',
      width: (sample.width || 0.004) * 0.5,
      points: pts,
      _side: 'spread',
      _pageId: base,
      _legacyParts: true
    })
  }
  return out.filter(Boolean)
}

export function simplifyStroke(points, minDist = 0.002) {
  if (!points || points.length <= 2) return points || []
  const out = [points[0]]
  for (let i = 1; i < points.length - 1; i++) {
    const a = out[out.length - 1]
    const b = points[i]
    const dx = b[0] - a[0]
    const dy = b[1] - a[1]
    if (dx * dx + dy * dy >= minDist * minDist) out.push(b)
  }
  out.push(points[points.length - 1])
  return out
}

export function hitTestStroke(layer, x, y, threshold = 0.012) {
  if (!layer || layer.type !== 'stroke' || !layer.points) return false
  const pts = layer.points
  const t = Math.max(threshold, (layer.width || 0.004) * 2.5)
  if (pts.length === 1) {
    return Math.hypot(x - pts[0][0], y - pts[0][1]) <= t
  }
  for (let i = 1; i < pts.length; i++) {
    if (distToSeg(x, y, pts[i - 1][0], pts[i - 1][1], pts[i][0], pts[i][1]) <= t) {
      return true
    }
  }
  return false
}

export function hitTestTextbox(layer, x, y) {
  if (!layer || layer.type !== 'textbox') return false
  const w = layer.w || 0.3
  const h = layer.h || 0.08
  return x >= layer.x && x <= layer.x + w && y >= layer.y && y <= layer.y + h
}

export function hitTestMedia(layer, x, y) {
  if (!layer || layer.type !== 'media') return false
  const w = layer.w || 0.32
  const h = layer.h || 0.28
  return x >= layer.x && x <= layer.x + w && y >= layer.y && y <= layer.y + h
}

/** 解析媒体相对路径为可请求 URL */
export function resolveMediaUrl(url) {
  if (!url) return ''
  if (/^https?:\/\//i.test(url) || url.startsWith('blob:') || url.startsWith('data:')) return url
  return (import.meta.env.VITE_APP_BASE_API || '') + url
}

/** 纸面烘焙用图片缓存 */
const mediaImageCache = new Map()

/**
 * 确保 URL 对应图片已加载；未就绪时返回 null 并触发加载，完成后 onReady。
 */
export function ensureMediaImage(url, onReady) {
  if (!url) return null
  const key = String(url)
  const cached = mediaImageCache.get(key)
  if (cached instanceof HTMLImageElement && cached.complete && cached.naturalWidth > 0) {
    return cached
  }
  if (cached === 'loading' || cached === 'error') return null
  mediaImageCache.set(key, 'loading')
  const img = new Image()
  img.decoding = 'async'
  img.onload = () => {
    mediaImageCache.set(key, img)
    onReady?.(img, key)
  }
  img.onerror = () => {
    mediaImageCache.set(key, 'error')
  }
  img.src = resolveMediaUrl(key)
  return null
}

/** 纸面烘焙用图：图片优先中图/原图；视频用封面缩略图 */
export function mediaBakeSrc(layer) {
  if (!layer) return ''
  if (layer.kind === 'video') return layer.thumbSrc || layer.src || ''
  // 切勿优先 thumbSrc：网格小图放大后会糊
  return layer.src || layer.thumbSrc || ''
}

function distToSeg(px, py, x1, y1, x2, y2) {
  const dx = x2 - x1
  const dy = y2 - y1
  const len2 = dx * dx + dy * dy
  if (len2 < 1e-12) return Math.hypot(px - x1, py - y1)
  let t = ((px - x1) * dx + (py - y1) * dy) / len2
  t = t < 0 ? 0 : t > 1 ? 1 : t
  return Math.hypot(px - (x1 + t * dx), py - (y1 + t * dy))
}

/**
 * 把 layers 画到纸面 canvas。
 * pageSide: 当前烘焙的是左/右页；会绘制本页 layers + 对开 space:spread 的半幅。
 * extraSpreadContent: 伙伴页上可能挂着的 spread 图层来源（通常是左页 content）
 * onMediaReady: 某张贴图首次加载完成时回调（用于触发重新烘焙）
 */
export function paintLayers(
  canvas,
  content,
  {
    skipTextbox = false,
    skipStroke = false,
    skipMedia = false,
    pageSide: side = null,
    extraSpreadContent = null,
    onMediaReady = null
  } = {}
) {
  if (!canvas) return
  const g = canvas.getContext('2d')
  const W = canvas.width
  const H = canvas.height

  const pageLayers = (content && content.layers) || []
  for (const layer of pageLayers) {
    if (!layer) continue
    if (layer.space === 'spread') {
      if (side && layer.type === 'stroke' && !skipStroke) {
        drawSpreadStrokeOnPage(g, layer, W, H, side)
      } else if (side && layer.type === 'textbox' && !skipTextbox) {
        drawSpreadTextOnPage(g, layer, W, H, side)
      } else if (side && layer.type === 'media' && !skipMedia) {
        drawSpreadMediaOnPage(g, layer, W, H, side, onMediaReady)
      } else if (!side) {
        if (layer.type === 'stroke' && !skipStroke) drawStroke(g, layer, W, H)
        else if (layer.type === 'textbox' && !skipTextbox) drawTextbox(g, layer, W, H)
        else if (layer.type === 'media' && !skipMedia) drawMedia(g, layer, W, H, onMediaReady)
      }
      continue
    }
    if (layer.type === 'stroke' && !skipStroke) drawStroke(g, layer, W, H)
    else if (layer.type === 'textbox' && !skipTextbox) drawTextbox(g, layer, W, H)
    else if (layer.type === 'media' && !skipMedia) drawMedia(g, layer, W, H, onMediaReady)
  }

  // 右页烘焙时，把左页里的 spread 图层半幅画上来
  if (side === 'right' && extraSpreadContent) {
    for (const layer of extraSpreadContent.layers || []) {
      if (!layer) continue
      if (layer.space === 'spread') {
        if (layer.type === 'stroke' && !skipStroke) drawSpreadStrokeOnPage(g, layer, W, H, 'right')
        else if (layer.type === 'textbox' && !skipTextbox) drawSpreadTextOnPage(g, layer, W, H, 'right')
        else if (layer.type === 'media' && !skipMedia) drawSpreadMediaOnPage(g, layer, W, H, 'right', onMediaReady)
        continue
      }
      // 左页 page 贴图伸出右缘（旧数据）：把溢出部分画到右页
      if (layer.type === 'media' && !skipMedia) {
        const x = layer.x || 0
        const w = layer.w || 0
        if (x + w > 1.002) {
          drawMedia(
            g,
            { ...layer, x: x - 1, w },
            W,
            H,
            onMediaReady
          )
        }
      }
    }
  }
}

/** 覆盖层画笔（坐标已是画布 0–1） */
export function paintStrokeOnCtx(g, layer, W, H, widthScale = 1) {
  if (!layer || !layer.points || !layer.points.length) return
  const pts = layer.points
  g.save()
  g.strokeStyle = layer.color || '#2B2A33'
  g.fillStyle = layer.color || '#2B2A33'
  g.lineWidth = Math.max(1, (layer.width || 0.004) * widthScale * W)
  g.lineCap = 'round'
  g.lineJoin = 'round'
  if (pts.length === 1) {
    g.beginPath()
    g.arc(pts[0][0] * W, pts[0][1] * H, g.lineWidth / 2, 0, Math.PI * 2)
    g.fill()
  } else {
    g.beginPath()
    g.moveTo(pts[0][0] * W, pts[0][1] * H)
    for (let i = 1; i < pts.length; i++) g.lineTo(pts[i][0] * W, pts[i][1] * H)
    g.stroke()
  }
  g.restore()
}

function drawSpreadStrokeOnPage(g, layer, W, H, side) {
  const pts = layer.points || []
  if (!pts.length) return
  // spread width 存的是相对整幅；页内线宽 ≈ width*2*W_page，但我们约定 width 为页相对
  // 存盘时 width 用页相对值；spread 显示时 *0.5。烘焙用页相对。
  const width = layer.width || 0.004
  const mapped = []
  for (const p of pts) {
    const sx = p[0]
    const sy = p[1]
    if (side === 'left') {
      if (sx > 0.5 + 0.002) continue
      mapped.push([Math.min(1, Math.max(0, sx * 2)), sy])
    } else {
      if (sx < 0.5 - 0.002) continue
      mapped.push([Math.min(1, Math.max(0, (sx - 0.5) * 2)), sy])
    }
  }
  // 补订口接点，避免半笔缺端点
  if (pts.length >= 2) {
    for (let i = 1; i < pts.length; i++) {
      const a = pts[i - 1]
      const b = pts[i]
      if ((a[0] < 0.5 && b[0] >= 0.5) || (a[0] >= 0.5 && b[0] < 0.5)) {
        const t = (0.5 - a[0]) / (b[0] - a[0] || 1e-9)
        const iy = a[1] + t * (b[1] - a[1])
        const edge = side === 'left' ? [1, iy] : [0, iy]
        // 插入到 mapped 合理位置较难，直接多画一段
        mapped.push(edge)
      }
    }
  }
  if (!mapped.length) return
  drawStroke(g, { ...layer, points: mapped, width }, W, H)
}

function drawSpreadTextOnPage(g, layer, W, H, side) {
  const text = (layer.text || '').trim()
  if (!text) return
  // 对开文字：整框在 spread 坐标，烘焙时按半页裁剪绘制
  const x0 = layer.x || 0
  const y0 = layer.y || 0
  const w0 = layer.w || 0.4
  const pageX0 = side === 'left' ? 0 : 0.5
  const pageX1 = side === 'left' ? 0.5 : 1
  const overlapL = Math.max(x0, pageX0)
  const overlapR = Math.min(x0 + w0, pageX1)
  if (overlapR <= overlapL) return

  g.save()
  // 裁到本页对应的半幅（画布即单页）
  g.beginPath()
  g.rect(0, 0, W, H)
  g.clip()

  const fontPx = Math.max(10, (layer.fontSize || 0.035) * H)
  g.fillStyle = layer.color || '#2B2A33'
  g.font = `${fontPx}px "Zen Kaku Gothic New", "Noto Sans SC", "Microsoft YaHei", sans-serif`
  g.textBaseline = 'top'
  const lineH = fontPx * 1.35
  // 文字起点：spread → page
  const localX = side === 'left' ? x0 * 2 : (x0 - 0.5) * 2
  const boxW = w0 * 2 * W
  const lines = wrapText(g, text, Math.max(8, boxW))
  let cy = y0 * H
  for (const line of lines) {
    g.fillText(line, localX * W, cy)
    cy += lineH
  }
  g.restore()
}

function drawStroke(g, layer, W, H) {
  const pts = layer.points
  if (!pts || pts.length < 1) return
  g.save()
  g.strokeStyle = layer.color || '#2B2A33'
  g.fillStyle = layer.color || '#2B2A33'
  g.lineWidth = Math.max(1, (layer.width || 0.004) * W)
  g.lineCap = 'round'
  g.lineJoin = 'round'
  if (pts.length === 1) {
    g.beginPath()
    g.arc(pts[0][0] * W, pts[0][1] * H, g.lineWidth / 2, 0, Math.PI * 2)
    g.fill()
  } else {
    g.beginPath()
    g.moveTo(pts[0][0] * W, pts[0][1] * H)
    for (let i = 1; i < pts.length; i++) g.lineTo(pts[i][0] * W, pts[i][1] * H)
    g.stroke()
  }
  g.restore()
}

function drawTextbox(g, layer, W, H) {
  const text = (layer.text || '').trim()
  if (!text) return
  const x = (layer.x || 0) * W
  const y = (layer.y || 0) * H
  const boxW = Math.max(8, (layer.w || 0.4) * W)
  const fontPx = Math.max(10, (layer.fontSize || 0.035) * H)
  g.save()
  g.fillStyle = layer.color || '#2B2A33'
  g.font = `${fontPx}px "Zen Kaku Gothic New", "Noto Sans SC", "Microsoft YaHei", sans-serif`
  g.textBaseline = 'top'
  const lineH = fontPx * 1.35
  const lines = wrapText(g, text, boxW)
  let cy = y
  for (const line of lines) {
    g.fillText(line, x, cy)
    cy += lineH
  }
  g.restore()
}

function drawMedia(g, layer, W, H, onMediaReady) {
  const x = (layer.x || 0) * W
  const y = (layer.y || 0) * H
  const boxW = Math.max(8, (layer.w || 0.32) * W)
  const boxH = Math.max(8, (layer.h || 0.28) * H)
  const pad = Math.max(3, Math.min(boxW, boxH) * 0.04) // 与 HTML 贴图层 4% 内边距一致
  const rot = layer.rot || 0
  const src = mediaBakeSrc(layer)
  const img = ensureMediaImage(src, onMediaReady)

  g.save()
  g.translate(x + boxW / 2, y + boxH / 2)
  if (rot) g.rotate(rot)
  g.translate(-boxW / 2, -boxH / 2)

  g.shadowColor = 'rgba(62, 50, 32, 0.28)'
  g.shadowBlur = Math.max(4, boxW * 0.04)
  g.shadowOffsetX = 1.5
  g.shadowOffsetY = 3
  g.fillStyle = '#FDF8EC'
  roundRectPath(g, 0, 0, boxW, boxH, Math.min(8, boxW * 0.04))
  g.fill()
  g.shadowColor = 'transparent'

  const ix = pad
  const iy = pad
  const iw = Math.max(1, boxW - pad * 2)
  const ih = Math.max(1, boxH - pad * 2)
  if (img) {
    drawCoverImage(g, img, ix, iy, iw, ih)
    g.fillStyle = 'rgba(236, 214, 172, 0.22)'
    g.globalCompositeOperation = 'multiply'
    g.fillRect(ix, iy, iw, ih)
    g.globalCompositeOperation = 'source-over'
  } else {
    g.fillStyle = 'rgba(210, 198, 178, 0.55)'
    g.fillRect(ix, iy, iw, ih)
  }

  if (layer.kind === 'video') {
    const r = Math.min(iw, ih) * 0.14
    g.fillStyle = 'rgba(30, 24, 18, 0.48)'
    g.beginPath()
    g.arc(ix + iw / 2, iy + ih / 2, r, 0, Math.PI * 2)
    g.fill()
    g.fillStyle = '#F7F2E8'
    g.beginPath()
    g.moveTo(ix + iw / 2 - r * 0.28, iy + ih / 2 - r * 0.42)
    g.lineTo(ix + iw / 2 - r * 0.28, iy + ih / 2 + r * 0.42)
    g.lineTo(ix + iw / 2 + r * 0.48, iy + ih / 2)
    g.closePath()
    g.fill()
  }
  g.restore()
}

function drawSpreadMediaOnPage(g, layer, W, H, side, onMediaReady) {
  const x0 = layer.x || 0
  const y0 = layer.y || 0
  const w0 = layer.w || 0.32
  const h0 = layer.h || 0.28
  const pageX0 = side === 'left' ? 0 : 0.5
  const pageX1 = side === 'left' ? 0.5 : 1
  const overlapL = Math.max(x0, pageX0)
  const overlapR = Math.min(x0 + w0, pageX1)
  if (overlapR <= overlapL) return

  // 整框按对开→单页映射后绘制，画布自然裁掉另一半，左右页拼成完整贴图
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
  drawMedia(g, local, W, H, onMediaReady)
  g.restore()
}

function drawCoverImage(g, img, x, y, w, h) {
  const iw = img.naturalWidth || img.width || 1
  const ih = img.naturalHeight || img.height || 1
  const scale = Math.max(w / iw, h / ih)
  const dw = iw * scale
  const dh = ih * scale
  const dx = x + (w - dw) / 2
  const dy = y + (h - dh) / 2
  g.save()
  g.beginPath()
  g.rect(x, y, w, h)
  g.clip()
  g.imageSmoothingEnabled = true
  g.imageSmoothingQuality = 'high'
  g.drawImage(img, dx, dy, dw, dh)
  g.restore()
}

function roundRectPath(g, x, y, w, h, r) {
  const rr = Math.max(0, Math.min(r, w / 2, h / 2))
  g.beginPath()
  g.moveTo(x + rr, y)
  g.lineTo(x + w - rr, y)
  g.quadraticCurveTo(x + w, y, x + w, y + rr)
  g.lineTo(x + w, y + h - rr)
  g.quadraticCurveTo(x + w, y + h, x + w - rr, y + h)
  g.lineTo(x + rr, y + h)
  g.quadraticCurveTo(x, y + h, x, y + h - rr)
  g.lineTo(x, y + rr)
  g.quadraticCurveTo(x, y, x + rr, y)
  g.closePath()
}

function wrapText(g, text, maxW) {
  const paragraphs = String(text).split(/\n/)
  const lines = []
  for (const para of paragraphs) {
    if (!para) {
      lines.push('')
      continue
    }
    let line = ''
    for (const ch of para) {
      const test = line + ch
      if (g.measureText(test).width > maxW && line) {
        lines.push(line)
        line = ch
      } else {
        line = test
      }
    }
    if (line) lines.push(line)
  }
  return lines.length ? lines : ['']
}
