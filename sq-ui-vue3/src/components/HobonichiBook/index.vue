<template>
  <div
    ref="stageRef"
    class="hobo-stage"
    :class="{ closed: spread <= 0.02, editing: tool !== 'turn' }"
    tabindex="0"
    role="img"
    :aria-label="ariaLabel"
    @keydown="onKey"
  >
    <div class="desk" aria-hidden="true" />
    <div class="vignette" aria-hidden="true" />

    <div ref="bookAreaRef" class="book-area">
      <div class="book-wrap" :style="bookStyle">
        <div class="book-shadow" :class="{ open: spread > 0.3 }" />
        <canvas ref="bookRef" class="book-canvas" />
        <canvas
          ref="leafRef"
          class="leaf-canvas"
          @pointerdown="onPointerDown"
          @pointermove="onPointerMove"
          @pointerup="onPointerUp"
          @pointercancel="onPointerUp"
        />
        <PageInkLayer
          :left-page-no="spreadPages.left"
          :right-page-no="spreadPages.right"
          :left-content="contentOf(spreadPages.left)"
          :right-content="contentOf(spreadPages.right)"
          :page-no="spreadInkPageNo"
          :content="spreadInkContent"
          :tool="tool"
          :color="inkColor"
          :pen-width="penWidth"
          :rect="spreadOverlayRect"
          :preview-strokes="inkPreview"
          :interactive="inkInteractive"
          @update-content="onInkUpdate"
          @turn-edge="onInkTurnEdge"
        />
      </div>
    </div>

    <div class="chrome">
      <div class="hint-text">
        <span>{{ hintText }}</span>
        <span class="sep">·</span>
        <span>{{ progressText }}</span>
      </div>
      <div class="nav">
        <button type="button" class="nav-btn" :disabled="!canPrev" @click.stop="prev">‹</button>
        <button type="button" class="nav-btn" :disabled="!canNext" @click.stop="next">›</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import {
  PI,
  clamp,
  createEngine,
  createUnit,
  makeCanvas,
  paintBlank,
  paintCoverBack,
  paintCoverFront,
  paintCoverInside,
  release,
  startAuto,
  stepAuto,
  stepUnit
} from './hobonichiEngine.js'
import PageInkLayer from './PageInkLayer.vue'
import {
  emptyContent,
  hasContent,
  pageSide,
  pageToFaceKey,
  paintLayers,
  partnerPageNo,
  spreadPageNos
} from './pageContent.js'

const props = defineProps({
  pages: { type: Array, default: () => [] },
  coverTitle: { type: String, default: '相册日记' },
  coverSub: { type: String, default: 'HOBONICHI STYLE' },
  year: { type: [String, Number], default: () => new Date().getFullYear() },
  initialIndex: { type: Number, default: 0 },
  /** turn | pen | text | eraser */
  tool: { type: String, default: 'turn' },
  inkColor: { type: String, default: '#2B2A33' },
  penWidth: { type: Number, default: 0.004 },
  /** Map 或普通对象：pageNo -> content */
  pageContents: { type: [Map, Object], default: () => new Map() }
})

const emit = defineEmits(['change', 'update-content', 'need-pages'])

const stageRef = ref(null)
const bookAreaRef = ref(null)
const bookRef = ref(null)
const leafRef = ref(null)
const bookSize = ref({ w: 900, h: 650, contentW: 900, contentH: 650, padX: 0, padY: 0 })
const reducedMotion = ref(false)
const index = ref(0)
const drag = ref(null)
const coverOpen = ref(0)
const animBusy = ref(false)
const overlayRects = ref({
  left: { x: 0, y: 0, w: 0, h: 0 },
  right: { x: 0, y: 0, w: 0, h: 0 }
})

/**
 * 书写层矩形：优先用打开态的稳定纸面几何（不跟相机抖动），
 * 避免翻页后 pageRects 短暂错位导致点不到。
 */
const spreadOverlayRect = computed(() => {
  const { left, right } = spreadPages.value
  if (!left && !right) return { x: 0, y: 0, w: 0, h: 0 }

  const bs = bookSize.value
  const padX = bs.padX || 18
  const contentW = bs.contentW || bs.w
  const contentH = bs.contentH || bs.h
  // 与 hobonichiEngine 开本相机 cy 偏移一致：H/2 + (VH-H)*0.2 → 顶边约 (h-contentH)*0.2
  const y = Math.max(0, ((bs.h || contentH) - contentH) * 0.2)

  if (left && right) {
    return { x: padX, y, w: contentW, h: contentH }
  }
  if (right) {
    return { x: padX + contentW / 2, y, w: contentW / 2, h: contentH }
  }
  return { x: padX, y, w: contentW / 2, h: contentH }
})

/** 仅一侧可写时走单页模式 props */
const spreadInkPageNo = computed(() => {
  const { left, right } = spreadPages.value
  if (left && right) return null
  return right || left || null
})

const spreadInkContent = computed(() => {
  const n = spreadInkPageNo.value
  return n ? contentOf(n) : emptyContent()
})

/** 翻页动画时关掉覆盖层预览，改走纸面纹理 */
const inkPreview = computed(() => !animBusy.value && !drag.value && props.tool !== 'turn')

/** 可书写：有纸页且非翻页工具（settle 中也允许，避免翻完假死） */
const inkInteractive = computed(() => {
  if (props.tool === 'turn') return false
  const { left, right } = spreadPages.value
  return !!(left || right)
})

let raf = 0
let lastTs = 0
let dpr = 1
let engine = null
let units = []
let faces = {}
let blankFront = null
let blankBack = null
let now = 0
let areaObserver = null
/** pageNo → dedicated face canvas */
const contentFaces = new Map()

const leavesMeta = computed(() => {
  const sheets = props.pages || []
  const list = [{ id: 'cover', kind: 'board', role: 'cover' }]
  for (let i = 0; i < sheets.length; i += 2) {
    list.push({ id: `p-${i}`, kind: 'paper', role: 'leaf', a: sheets[i], b: sheets[i + 1] })
  }
  list.push({ id: 'back', kind: 'board', role: 'back' })
  return list
})

// 不再用 animBusy 禁用按钮——卡住时会导致彻底翻不了页
const canPrev = computed(() => index.value > 0)
const canNext = computed(() => index.value < leavesMeta.value.length)

const spread = computed(() => coverOpen.value)

const progressText = computed(() => {
  const total = Math.max(1, (props.pages || []).length)
  if (index.value === 0) return '合上'
  if (index.value >= leavesMeta.value.length) return '封底'
  const pageNo = Math.min(total, Math.max(1, (index.value - 1) * 2 + 1))
  return `${pageNo} / ${total}`
})

const hintText = computed(() => {
  if (index.value === 0) return '拖封面右下角或按 → 打开'
  if (props.tool === 'pen') return '可跨左右页连续书写 · 外缘翻页'
  if (props.tool === 'text') return '整幅对开任意放置文字'
  if (props.tool === 'eraser') return '点击笔画或文字删除'
  return '拖页角翻页 · ← →'
})

const ariaLabel = computed(() => `${props.coverTitle}。${hintText.value}`)

const bookStyle = computed(() => ({
  width: `${bookSize.value.w}px`,
  height: `${bookSize.value.h}px`
}))

const spreadPages = computed(() =>
  spreadPageNos(index.value, (props.pages || []).length)
)

function contentOf(pageNo) {
  if (!pageNo) return emptyContent()
  const src = props.pageContents
  if (src instanceof Map) return src.get(pageNo) || emptyContent()
  return src?.[pageNo] || emptyContent()
}

function isBusy() {
  return units.some(u => u.mode !== 'rest' && u.mode !== 'settle')
}

function syncBusy() {
  animBusy.value = isBusy()
}

function pageW() {
  return ((bookSize.value.contentW || bookSize.value.w) * dpr) / 2
}

function pageH() {
  return (bookSize.value.contentH || bookSize.value.h) * dpr
}

function syncUnits() {
  const meta = leavesMeta.value
  const prev = units
  units = meta.map((m, i) => {
    const U = createUnit(m.kind === 'board' ? 'board' : 'paper')
    const turned = i < index.value
    U.th = turned ? PI : 0
    U.goal = turned ? 1 : 0
    U.side = turned ? 1 : 0
    U.mode = 'rest'
    U.frontKey = `${m.id}-f`
    U.backKey = `${m.id}-b`
    if (prev[i] && prev[i].frontKey === U.frontKey) {
      U.th = prev[i].th
      U.om = prev[i].om
      U.de = prev[i].de
      U.tw = prev[i].tw
      U.mode = prev[i].mode
      U.goal = prev[i].goal
      U.side = prev[i].side
      U.st = prev[i].st
      U.tt = prev[i].tt
      U.fresh = prev[i].fresh
    }
    return U
  })
  buildFaces()
}

function pageHasDrawable(pageNo) {
  if (!pageNo) return false
  if (hasContent(contentOf(pageNo))) return true
  // 右页可能只靠左页上的 spread 图层
  const partner = partnerPageNo(pageNo)
  if (pageSide(pageNo) === 'right' && partner) {
    const layers = contentOf(partner).layers || []
    return layers.some((l) => l && l.space === 'spread')
  }
  return false
}

function getContentFace(pageNo, W, H, { skipTextbox = false, skipStroke = false } = {}) {
  const key = pageToFaceKey(pageNo)
  if (!key) return null
  let c = contentFaces.get(pageNo)
  if (!c || c.width !== W || c.height !== H) {
    c = makeCanvas(W, H)
    contentFaces.set(pageNo, c)
  }
  const side = pageSide(pageNo)
  paintBlank(c, side === 'right' ? 'right' : 'left', 11 + pageNo)
  const partner = partnerPageNo(pageNo)
  paintLayers(c, contentOf(pageNo), {
    skipTextbox,
    skipStroke,
    pageSide: side,
    extraSpreadContent: side === 'right' && partner ? contentOf(partner) : null
  })
  return { key, canvas: c }
}

function applyPageContent(pageNo, { skipTextbox = false, skipStroke = false } = {}) {
  const W = Math.round(pageW())
  const H = Math.round(pageH())
  if (W < 2 || H < 2) return
  if (!pageNo || !pageHasDrawable(pageNo)) {
    const key = pageToFaceKey(pageNo)
    if (key && blankFront && blankBack) {
      faces[key] = key.endsWith('-f') ? blankFront : blankBack
      contentFaces.delete(pageNo)
      engine?.invalidateFace?.(key)
    }
    return
  }
  const face = getContentFace(pageNo, W, H, { skipTextbox, skipStroke })
  if (!face) return
  faces[face.key] = face.canvas
  engine?.invalidateFace?.(face.key)
  units.forEach((U) => {
    if (U.frontKey === face.key || U.backKey === face.key) U.fresh = true
  })
}

function rebakeVisible() {
  const { left, right } = spreadPages.value
  const visible = new Set([left, right].filter(Boolean))
  // 覆盖层预览开启时：可见页纹理跳过 stroke/textbox，避免订口断缝与叠绘
  const preview = inkPreview.value
  for (const n of [...contentFaces.keys()]) {
    if (!visible.has(n) && pageHasDrawable(n)) {
      applyPageContent(n, { skipTextbox: false, skipStroke: false })
    }
  }
  // 文字工具：HTML 编辑层显示文字 → 纹理跳过 textbox 防叠字
  // 画笔等：文字烘焙进纸面，避免切工具后“文字消失”
  const skipText = preview && props.tool === 'text'
  if (left) applyPageContent(left, { skipTextbox: skipText, skipStroke: preview })
  if (right) applyPageContent(right, { skipTextbox: skipText, skipStroke: preview })
}

function buildFaces() {
  const W = Math.round(pageW())
  const H = Math.round(pageH())
  const meta = leavesMeta.value
  const next = {}

  blankFront = makeCanvas(W, H)
  blankBack = makeCanvas(W, H)
  paintBlank(blankFront, 'right', 11)
  paintBlank(blankBack, 'left', 22)

  meta.forEach((m) => {
    if (m.role === 'cover') {
      const front = makeCanvas(W, H)
      const back = makeCanvas(W, H)
      paintCoverFront(front, props.coverTitle, props.coverSub, props.year)
      paintCoverInside(back, false)
      next[`${m.id}-f`] = front
      next[`${m.id}-b`] = back
    } else if (m.role === 'back') {
      const front = makeCanvas(W, H)
      const back = makeCanvas(W, H)
      paintCoverInside(front, true)
      paintCoverBack(back, props.year)
      next[`${m.id}-f`] = front
      next[`${m.id}-b`] = back
    } else {
      next[`${m.id}-f`] = blankFront
      next[`${m.id}-b`] = blankBack
    }
  })

  const coverFI = makeCanvas(W, H)
  const coverBI = makeCanvas(W, H)
  paintCoverInside(coverFI, false)
  paintCoverInside(coverBI, true)
  next.coverFI = coverFI
  next.coverBI = coverBI
  faces = next

  // 重建后把已有内容页重新 bake
  contentFaces.clear()
  const src = props.pageContents
  const nos = src instanceof Map ? [...src.keys()] : Object.keys(src || {}).map(Number)
  const visible = new Set([spreadPages.value.left, spreadPages.value.right].filter(Boolean))
  const preview = inkPreview.value
  nos.forEach((n) => {
    if (pageHasDrawable(n)) {
      const onSpread = visible.has(n)
      applyPageContent(n, {
        skipTextbox: preview && onSpread && props.tool === 'text',
        skipStroke: preview && onSpread
      })
    }
  })
}

function sizeCanvases(w, h) {
  ;[bookRef.value, leafRef.value].forEach((cv) => {
    if (!cv) return
    cv.width = Math.round(w * dpr)
    cv.height = Math.round(h * dpr)
    cv.style.width = `${Math.round(w)}px`
    cv.style.height = `${Math.round(h)}px`
  })
}

function resize() {
  const el = bookAreaRef.value || stageRef.value
  if (!el || !bookRef.value || !leafRef.value) return
  dpr = Math.min(2, window.devicePixelRatio || 1)

  const MM = 96 / 25.4
  const A5_W = 148 * MM
  const A5_H = 210 * MM
  const SPREAD_W = A5_W * 2
  const SPREAD_H = A5_H

  const aw = Math.max(320, el.clientWidth - 12)
  const ah = Math.max(360, el.clientHeight - 8)
  const padX = 18
  const padY = 32
  const scale = Math.min((aw - padX * 2) / SPREAD_W, (ah - padY) / SPREAD_H, 1.2)
  const contentW = SPREAD_W * scale
  const contentH = SPREAD_H * scale

  bookSize.value = {
    w: Math.round(contentW + padX * 2),
    h: Math.round(contentH + padY),
    contentW: Math.round(contentW),
    contentH: Math.round(contentH),
    padX,
    padY
  }
  sizeCanvases(bookSize.value.w, bookSize.value.h)
  if (!engine) {
    engine = createEngine({ bookCanvas: bookRef.value, leafCanvas: leafRef.value })
  }
  buildFaces()
}

function updateOverlayRects() {
  if (!engine || !leafRef.value) return
  const W = pageW()
  const H = pageH()
  const rects = engine.pageRects(W, H)
  const cw = leafRef.value.width || 1
  const ch = leafRef.value.height || 1
  const sw = bookSize.value.w || 1
  const sh = bookSize.value.h || 1
  const sx = sw / cw
  const sy = sh / ch
  overlayRects.value = {
    left: {
      x: rects.left.x * sx,
      y: rects.left.y * sy,
      w: rects.left.w * sx,
      h: rects.left.h * sy
    },
    right: {
      x: rects.right.x * sx,
      y: rects.right.y * sy,
      w: rects.right.w * sx,
      h: rects.right.h * sy
    }
  }
}

function emitNeedPages() {
  const { left, right } = spreadPages.value
  const nos = [left, right].filter(Boolean)
  if (!nos.length) return
  emit('need-pages', {
    from: Math.min(...nos),
    to: Math.max(...nos),
    index: index.value
  })
}

function next() {
  if (!canNext.value) return
  recoverStuckInteraction(true)
  const U = units[index.value]
  if (!U || (U.mode !== 'rest' && U.mode !== 'settle' && U.mode !== 'peek')) {
    snapUnitFlat(U)
  }
  if (!U) return
  startAuto(U, true, reducedMotion.value)
}

function prev() {
  if (!canPrev.value) return
  recoverStuckInteraction(true)
  const U = units[index.value - 1]
  if (!U) return
  if (U.mode !== 'rest' && U.mode !== 'settle' && U.mode !== 'peek') {
    snapUnitFlat(U)
  }
  startAuto(U, false, reducedMotion.value)
}

function onKey(e) {
  if (e.target && e.target.isContentEditable) return
  const k = e.key
  if (k === 'ArrowRight' || k === 'PageDown' || (k === ' ' && !e.shiftKey)) {
    e.preventDefault()
    next()
  } else if (k === 'ArrowLeft' || k === 'PageUp' || (k === ' ' && e.shiftKey)) {
    e.preventDefault()
    prev()
  }
}

function localPoint(e) {
  const cv = leafRef.value
  const r = cv.getBoundingClientRect()
  return {
    x: ((e.clientX - r.left) / r.width) * cv.width,
    y: ((e.clientY - r.top) / r.height) * cv.height
  }
}

function onPointerDown(e) {
  if (e.button != null && e.button !== 0) return
  if (isBusy() && !drag.value) return
  if (!engine) return
  // 编辑工具下，中部交给 ink 层；此处仍处理页角/封面
  const p = localPoint(e)
  const W = pageW()
  const H = pageH()
  const b = engine.toBook(p.x, p.y, W, H)
  const x = b.x
  const y = b.y
  const pos = index.value

  let leaf = -1
  let fwd = true
  if (x >= 0 && pos <= units.length - 1) {
    const U = units[pos]
    if (U && x > W * 0.28 && x < W + 40) {
      leaf = pos
      fwd = true
    }
  }
  if (leaf < 0 && x < 0 && pos > 0) {
    const U = units[pos - 1]
    if (U && -x > W * 0.28 && -x < W + 40) {
      leaf = pos - 1
      fwd = false
    }
  }
  if (leaf < 0 && pos === 0 && x >= 0 && x <= W) {
    leaf = 0
    fwd = true
  }
  if (leaf < 0) return

  // 编辑模式：仅允许外缘抓取（封面除外）
  if (props.tool !== 'turn' && pos > 0) {
    const nx = x >= 0 ? x / W : -x / W
    if (nx < 0.88) return
  }

  beginDrag(e, leaf, fwd, y, H)
}

function clearDragListeners() {
  window.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('pointerup', onPointerUp)
  window.removeEventListener('pointercancel', onPointerUp)
}

/** 把某页落到侧边并 rest（仅用于真正卡死时，翻页动画中途禁止调用） */
function snapUnitFlat(U) {
  if (!U) return
  const side = U.th > PI * 0.5 ? 1 : 0
  U.th = side ? PI : 0
  U.side = side
  U.goal = side
  U.om = 0
  U.de = U.tw = U.dv = U.tv = 0
  U.tt = U.th
  U.st = null
  U._auto = null
  U.mode = 'rest'
}

/** 仅清理「无主 drag」：mode=drag 但 drag.value 已空 */
function healOrphanDrag() {
  if (drag.value) return
  let healed = false
  units.forEach((U) => {
    if (U.mode === 'drag') {
      snapUnitFlat(U)
      healed = true
    }
  })
  if (healed) {
    clearDragListeners()
    syncBusy()
  }
}

/** settle 完成态收成 rest；不动 auto/free/drag（避免半页瞬翻） */
function settleToRest() {
  units.forEach((U) => {
    if (U.mode === 'settle' || U.mode === 'peek') snapUnitFlat(U)
  })
  syncBusy()
}

/** 按钮翻页前：强制打断当前交互并摊平 */
function recoverStuckInteraction(force = false) {
  if (!force) {
    healOrphanDrag()
    settleToRest()
    return
  }
  if (drag.value) {
    const U = units[drag.value.leaf]
    drag.value = null
    clearDragListeners()
    snapUnitFlat(U)
  }
  units.forEach((U) => {
    if (U.mode !== 'rest') snapUnitFlat(U)
  })
  clearDragListeners()
  syncBusy()
}

function forceRestIfFlat() {
  healOrphanDrag()
  settleToRest()
}

function beginDrag(e, leaf, fwd, y, H, { useWindow = false } = {}) {
  const U = units[leaf]
  if (!U || (U.mode !== 'rest' && U.mode !== 'settle' && U.mode !== 'peek')) return

  if (!useWindow) {
    try {
      leafRef.value.setPointerCapture?.(e.pointerId)
    } catch {
      useWindow = true
    }
  }
  drag.value = {
    id: e.pointerId,
    leaf,
    fwd,
    startX: e.clientX,
    lastX: e.clientX,
    lastT: performance.now(),
    moved: false,
    tap: true,
    useWindow
  }
  U.mode = 'drag'
  U.tt = U.th
  U.st = null
  U.fresh = true
  U.vl = clamp((y != null ? y : pageH() * 0.7) / (H || pageH()), 0.15, 0.92)
  if (useWindow) {
    clearDragListeners()
    window.addEventListener('pointermove', onPointerMove)
    window.addEventListener('pointerup', onPointerUp)
    window.addEventListener('pointercancel', onPointerUp)
  }
  e.preventDefault?.()
}

function onInkTurnEdge({ pageNo, event }) {
  if (!event || isBusy()) return
  const pos = index.value
  const rightPage = pageNo % 2 === 1
  let leaf = -1
  let fwd = true
  if (rightPage) {
    leaf = pos
    fwd = true
  } else {
    leaf = pos - 1
    fwd = false
  }
  if (leaf < 0 || leaf > units.length - 1) return
  const lp = localPoint(event)
  beginDrag(event, leaf, fwd, lp.y, pageH(), { useWindow: true })
}

function onInkUpdate({ pageNo, content }) {
  emit('update-content', { pageNo, content })
}

function onPointerMove(e) {
  if (!drag.value || e.pointerId !== drag.value.id) return
  const d = drag.value
  const U = units[d.leaf]
  if (!U) return
  const t = performance.now()
  const dx = e.clientX - d.startX
  const span = Math.max(120, bookSize.value.w * 0.42)
  let p
  if (d.fwd) p = clamp(-dx / span, 0, 1)
  else p = clamp(dx / span, 0, 1)
  if (Math.abs(dx) > 4) {
    d.moved = true
    d.tap = false
  }
  const e2 = p < 0.5 ? 2 * p * p : 1 - Math.pow(-2 * p + 2, 2) / 2
  U.tt = d.fwd ? PI * e2 : PI * (1 - e2)
  const dt = Math.max(0.008, (t - d.lastT) / 1000)
  const vx = (e.clientX - d.lastX) / dt
  U.om = d.fwd
    ? clamp((-vx / span) * PI * 0.38, -16, 16)
    : clamp((vx / span) * PI * 0.38, -16, 16)
  const lp = localPoint(e)
  U.vl = clamp(lp.y / pageH(), 0.15, 0.92)
  d.lastX = e.clientX
  d.lastT = t
}

function onPointerUp(e) {
  if (!drag.value || (e.pointerId != null && e.pointerId !== drag.value.id)) return
  const d = drag.value
  const U = units[d.leaf]
  drag.value = null
  clearDragListeners()
  if (!d.useWindow) {
    try {
      leafRef.value?.releasePointerCapture?.(d.id)
    } catch { /* ignore */ }
  }
  if (!U) return
  if (d.tap && !d.moved) {
    U.mode = 'rest'
    U.om = 0
    startAuto(U, d.fwd, reducedMotion.value)
    return
  }
  release(U)
}

function recomputeIndex() {
  let i = 0
  while (i < units.length && units[i].th > PI * 0.5) i++
  if (i !== index.value) {
    index.value = i
    // 跨半页只更新 index，绝不打断正在进行的 drag/auto/free 动画
    emit('change', i)
    emitNeedPages()
    rebakeVisible()
  }
}

function tick(ts) {
  const dt = Math.min(0.033, lastTs ? (ts - lastTs) / 1000 : 0.016)
  lastTs = ts
  now += dt

  units.forEach((U) => {
    stepAuto(U, dt)
    if (U.mode !== 'rest') stepUnit(U, dt, now, reducedMotion.value)
  })
  healOrphanDrag()
  syncBusy()
  recomputeIndex()

  if (engine) {
    const out = engine.render({
      units,
      faces,
      pageW: pageW(),
      pageH: pageH(),
      now,
      reducedMotion: reducedMotion.value,
      dpr
    })
    if (out) coverOpen.value = out.coverOpen
    updateOverlayRects()
  }

  raf = requestAnimationFrame(tick)
}

watch(leavesMeta, () => { syncUnits() })

watch(
  () => props.pageContents,
  () => { rebakeVisible() },
  { deep: true }
)

watch(
  () => props.tool,
  (tool, prev) => {
    // 从文字切到画笔时立刻把文字烘焙进纸面
    if (prev === 'text' && tool !== 'text') {
      nextTick(() => rebakeVisible())
    } else {
      rebakeVisible()
    }
  }
)

watch(inkPreview, () => {
  rebakeVisible()
})

watch(animBusy, (busy, was) => {
  // 翻页物理结束（进入 settle/rest）后再收尾，勿在半页时打断
  if (was && !busy) {
    nextTick(() => {
      settleToRest()
      rebakeVisible()
    })
  }
})

onMounted(() => {
  reducedMotion.value = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  index.value = Math.max(0, Math.min(props.initialIndex, leavesMeta.value.length))
  syncUnits()
  resize()
  window.addEventListener('resize', resize)
  nextTick(() => {
    stageRef.value?.focus?.({ preventScroll: true })
    resize()
    emitNeedPages()
    if (bookAreaRef.value && typeof ResizeObserver !== 'undefined') {
      areaObserver = new ResizeObserver(() => resize())
      areaObserver.observe(bookAreaRef.value)
    }
  })
  raf = requestAnimationFrame(tick)
})

onUnmounted(() => {
  cancelAnimationFrame(raf)
  window.removeEventListener('resize', resize)
  areaObserver?.disconnect()
  areaObserver = null
  engine = null
  contentFaces.clear()
})

defineExpose({ next, prev, index, rebakeVisible })
</script>

<style scoped lang="scss">
.hobo-stage {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  outline: none;
  user-select: none;
  touch-action: none;
  background: #ebe6dc;
  color: #3e3426;
}

.desk {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse at 50% 42%, rgba(255, 250, 240, 0.9), transparent 58%),
    linear-gradient(180deg, #f0ebe1 0%, #ebe6dc 42%, #e3ddd2 100%);
  pointer-events: none;
}

.vignette {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: radial-gradient(ellipse at 50% 50%, transparent 58%, rgba(120, 104, 80, 0.07) 100%);
}

.book-area {
  position: relative;
  z-index: 1;
  flex: 1;
  min-height: 0;
}

.book-wrap {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  &::before {
    content: '';
    display: block;
    width: 100%;
    height: 100%;
  }
}

.book-shadow {
  position: absolute;
  left: 50%;
  right: 2%;
  bottom: 0;
  height: 18%;
  background: radial-gradient(ellipse at 50% 30%, rgba(70, 56, 36, 0.34), transparent 70%);
  filter: blur(12px);
  pointer-events: none;
  z-index: 0;
  opacity: 0.9;
  transition: left 0.55s cubic-bezier(.22,.61,.24,1), right 0.55s cubic-bezier(.22,.61,.24,1), opacity 0.3s;
  &.open {
    left: 2%;
    right: 2%;
    opacity: 1;
  }
}

.book-canvas,
.leaf-canvas {
  position: absolute;
  inset: 0;
  display: block;
  width: 100%;
  height: 100%;
  touch-action: none;
}

.book-canvas {
  z-index: 1;
  pointer-events: none;
}

.leaf-canvas {
  z-index: 2;
  cursor: grab;
  &:active { cursor: grabbing; }
}

/* 翻页模式：leaf 必须在最上面可拖 */
.hobo-stage:not(.editing) .leaf-canvas {
  z-index: 8;
  pointer-events: auto !important;
  cursor: grab;
}
.hobo-stage:not(.editing) .ink-layer {
  z-index: 0 !important;
  pointer-events: none !important;
}

.hobo-stage.editing .leaf-canvas {
  /* 书写模式：中部交给 ink；外缘翻页仍由 ink 转发 */
  pointer-events: none !important;
  z-index: 2;
  cursor: default;
}

.chrome {
  position: relative;
  z-index: 2;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  padding: 6px 12px calc(8px + env(safe-area-inset-bottom));
  pointer-events: none;
}

.hint-text {
  font-family: 'Jost', 'Zen Kaku Gothic New', sans-serif;
  font-size: 13px;
  letter-spacing: 0.06em;
  color: rgba(62, 52, 38, 0.52);
  .sep { margin: 0 8px; opacity: 0.4; }
}

.nav {
  display: flex;
  gap: 6px;
  pointer-events: auto;
}

.nav-btn {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  border: 1px solid rgba(62, 52, 38, 0.16);
  background: rgba(255, 252, 245, 0.72);
  color: rgba(62, 52, 38, 0.72);
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
  backdrop-filter: blur(4px);
  &:hover:not(:disabled) {
    background: rgba(255, 252, 245, 0.95);
    color: #2b2a33;
  }
  &:disabled {
    opacity: 0.32;
    cursor: default;
  }
}
</style>
