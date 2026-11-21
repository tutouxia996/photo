<template>
  <div
    ref="stageRef"
    class="hobo-stage"
    :class="{ closed: spread <= 0.02 }"
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

const props = defineProps({
  pages: { type: Array, default: () => [] },
  coverTitle: { type: String, default: '相册日记' },
  coverSub: { type: String, default: 'HOBONICHI STYLE' },
  year: { type: [String, Number], default: () => new Date().getFullYear() },
  initialIndex: { type: Number, default: 0 }
})

const emit = defineEmits(['change'])

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

let raf = 0
let lastTs = 0
let dpr = 1
let engine = null
let units = []
let faces = {}
let now = 0
let areaObserver = null

const leavesMeta = computed(() => {
  const sheets = props.pages || []
  const list = [{ id: 'cover', kind: 'board', role: 'cover' }]
  for (let i = 0; i < sheets.length; i += 2) {
    list.push({ id: `p-${i}`, kind: 'paper', role: 'leaf', a: sheets[i], b: sheets[i + 1] })
  }
  list.push({ id: 'back', kind: 'board', role: 'back' })
  return list
})

const canPrev = computed(() => !drag.value && !animBusy.value && index.value > 0)
const canNext = computed(() => !drag.value && !animBusy.value && index.value < leavesMeta.value.length)

const spread = computed(() => coverOpen.value)

const progressText = computed(() => {
  const total = Math.max(1, (props.pages || []).length)
  if (index.value === 0) return '合上'
  if (index.value >= leavesMeta.value.length) return '封底'
  // each leaf is two page sides; index 1 = pages 1–2
  const pageNo = Math.min(total, Math.max(1, (index.value - 1) * 2 + 1))
  return `${pageNo} / ${total}`
})

const hintText = computed(() => {
  if (index.value === 0) return '拖封面右下角或按 → 打开'
  return '拖页角翻页 · ← →'
})

const ariaLabel = computed(() => `${props.coverTitle}。${hintText.value}`)

const bookStyle = computed(() => ({
  width: `${bookSize.value.w}px`,
  height: `${bookSize.value.h}px`
}))

function isBusy() {
  return units.some(u => u.mode !== 'rest' && u.mode !== 'settle')
}

function syncBusy() {
  animBusy.value = isBusy()
}

function pageW() {
  // content spread / 2 — canvas is larger to leave room for fore-edge + shadow
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

function buildFaces() {
  const W = Math.round(pageW())
  const H = Math.round(pageH())
  const meta = leavesMeta.value
  const next = {}

  // share one blank sheet texture for all paper leaves (1000 pages would otherwise OOM)
  const blankFront = makeCanvas(W, H)
  const blankBack = makeCanvas(W, H)
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

  // Physical A5: 148×210mm. Open spread = 296×210mm.
  // CSS reference: 1in = 96px → 1mm = 96/25.4
  const MM = 96 / 25.4
  const A5_W = 148 * MM
  const A5_H = 210 * MM
  const SPREAD_W = A5_W * 2
  const SPREAD_H = A5_H

  // Use almost all of the book area; keep a slim gutter only
  const aw = Math.max(320, el.clientWidth - 12)
  const ah = Math.max(360, el.clientHeight - 8)
  // leave CSS room for fore-edge hairlines + foot shadow (otherwise they clip at the canvas edge)
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

function next() {
  if (!canNext.value) return
  const U = units[index.value]
  if (!U) return
  startAuto(U, true, reducedMotion.value)
}

function prev() {
  if (!canPrev.value) return
  const U = units[index.value - 1]
  if (!U) return
  startAuto(U, false, reducedMotion.value)
}

function onKey(e) {
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
  const p = localPoint(e)
  const W = pageW()
  const H = pageH()
  const b = engine.toBook(p.x, p.y, W, H)
  const x = b.x
  const y = b.y
  const pos = index.value

  let leaf = -1
  let fwd = true
  // reference: grab near the free fore-edge (outer third), not the gutter
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
  // closed cover: whole right face is fair game
  if (leaf < 0 && pos === 0 && x >= 0 && x <= W) {
    leaf = 0
    fwd = true
  }
  if (leaf < 0) return

  const U = units[leaf]
  if (!U || (U.mode !== 'rest' && U.mode !== 'settle' && U.mode !== 'peek')) return

  leafRef.value.setPointerCapture?.(e.pointerId)
  drag.value = {
    id: e.pointerId,
    leaf,
    fwd,
    startX: e.clientX,
    lastX: e.clientX,
    lastT: performance.now(),
    moved: false,
    tap: true
  }
  U.mode = 'drag'
  U.tt = U.th
  U.st = null
  U.fresh = true
  U.vl = clamp(y / H, 0.15, 0.92)
  e.preventDefault()
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
  // ease like reference drag mapping
  const e2 = p < 0.5 ? 2 * p * p : 1 - Math.pow(-2 * p + 2, 2) / 2
  U.tt = d.fwd ? PI * e2 : PI * (1 - e2)
  const dt = Math.max(0.008, (t - d.lastT) / 1000)
  const vx = (e.clientX - d.lastX) / dt
  U.om = d.fwd
    ? clamp((-vx / span) * PI * 0.38, -16, 16)
    : clamp((vx / span) * PI * 0.38, -16, 16)
  // catch height near pointer Y
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
  leafRef.value?.releasePointerCapture?.(d.id)
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
    emit('change', i)
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
  }

  raf = requestAnimationFrame(tick)
}

watch(leavesMeta, () => { syncUnits() })

onMounted(() => {
  reducedMotion.value = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  index.value = Math.max(0, Math.min(props.initialIndex, leavesMeta.value.length))
  syncUnits()
  resize()
  window.addEventListener('resize', resize)
  nextTick(() => {
    stageRef.value?.focus?.({ preventScroll: true })
    resize()
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
})

defineExpose({ next, prev, index })
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
