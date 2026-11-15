<template>
  <div
    ref="rootRef"
    class="film-loupe"
    :class="{ 'is-on': active && alpha > 0.01 }"
    :style="rootStyle"
    aria-hidden="true"
  >
    <canvas ref="canvasRef" class="film-loupe-glass" />
    <div class="film-loupe-rim" />
    <div class="film-loupe-glint" />
  </div>
</template>

<script setup>
/**
 * SpektraLab Light Table Loupe 的 Web 近似：
 * 相对「当前屏幕所见」再放大 MAG 倍（默认 3×），圆形镜片跟指针。
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps({
  active: { type: Boolean, default: false },
  /** .media-canvas 元素；也可不传，自动用父节点 */
  stageEl: { type: [Object, null], default: null },
  /** 含 FilmFrame / img 的层 */
  sourceRoot: { type: [Object, null], default: null },
  mag: { type: Number, default: 3 },
  radius: { type: Number, default: 0 }
})

const rootRef = ref(null)
const canvasRef = ref(null)
const alpha = ref(0)
const pointer = ref({ x: 0, y: 0 })
const stageSize = ref({ w: 1200, h: 800 })

let raf = 0
let lastTs = 0
let boundStage = null
let pointerInside = false

const mag = computed(() => Math.max(2, Number(props.mag) || 3))

const lensRadius = computed(() => {
  if (props.radius > 0) return props.radius
  return stageSize.value.w < 600 ? 72 : 102
})

const lensRim = computed(() => (stageSize.value.w < 600 ? 5 : 7))

const rootStyle = computed(() => {
  const r = lensRadius.value
  const rim = lensRim.value
  return {
    width: `${2 * (r + rim)}px`,
    height: `${2 * (r + rim)}px`,
    left: `${pointer.value.x}px`,
    top: `${pointer.value.y}px`,
    opacity: String(alpha.value),
    '--loupe-rim': `${rim}px`
  }
})

function asEl(v) {
  if (!v) return null
  if (v instanceof Element) return v
  // 偶尔模板传入未解包的 ref
  if (v.value instanceof Element) return v.value
  return null
}

function resolveStage() {
  return asEl(props.stageEl) || rootRef.value?.parentElement || null
}

function resolveSourceRoot() {
  return asEl(props.sourceRoot) || resolveStage()
}

/** 取真正画在屏幕上的胶片/图片，跳过隐藏的源 img */
function findSource() {
  const root = resolveSourceRoot()
  if (!root) return null

  const canvases = root.querySelectorAll('canvas.film-canvas')
  for (const c of canvases) {
    if (!(c.width > 2 && c.height > 2)) continue
    if (getComputedStyle(c).display === 'none') continue
    const r = c.getBoundingClientRect()
    if (r.width > 16 && r.height > 16) return c
  }

  const medias = root.querySelectorAll('img.media-image, img, video.media-video, video')
  for (const el of medias) {
    if (el.closest?.('.is-source-only')) continue
    const r = el.getBoundingClientRect()
    if (r.width < 32 || r.height < 32) continue
    if (el.tagName === 'VIDEO') {
      if (el.videoWidth > 0) return el
    } else if (el.naturalWidth > 0 || el.complete) {
      return el
    }
  }
  return null
}

function bitmapSize(source) {
  if (source.tagName === 'CANVAS') return { w: source.width, h: source.height }
  if (source.tagName === 'VIDEO') return { w: source.videoWidth, h: source.videoHeight }
  return { w: source.naturalWidth || 0, h: source.naturalHeight || 0 }
}

function paint() {
  const canvas = canvasRef.value
  const stage = resolveStage()
  if (!canvas || !stage || alpha.value < 0.01) return

  const source = findSource()
  if (!source) return

  const r = lensRadius.value
  const dpr = Math.min(2.5, window.devicePixelRatio || 1)
  // 镜片可见直径 2R；位图按 2R 建，采样 2R/mag → 刚好 mag 倍
  const side = Math.max(64, Math.round(2 * r * dpr))
  if (canvas.width !== side || canvas.height !== side) {
    canvas.width = side
    canvas.height = side
  }

  const ctx = canvas.getContext('2d')
  if (!ctx) return

  const srcRect = source.getBoundingClientRect()
  const stageRect = stage.getBoundingClientRect()
  const { w: bw, h: bh } = bitmapSize(source)
  if (srcRect.width < 2 || srcRect.height < 2 || !bw || !bh) return

  const clientX = stageRect.left + pointer.value.x
  const clientY = stageRect.top + pointer.value.y
  const u = (clientX - srcRect.left) / srcRect.width
  const v = (clientY - srcRect.top) / srcRect.height

  ctx.save()
  ctx.clearRect(0, 0, side, side)
  ctx.fillStyle = '#0e0b09'
  ctx.fillRect(0, 0, side, side)
  ctx.beginPath()
  ctx.arc(side / 2, side / 2, side / 2 - 0.5, 0, Math.PI * 2)
  ctx.clip()

  if (u < -0.02 || u > 1.02 || v < -0.02 || v > 1.02) {
    ctx.restore()
    return
  }

  // 屏幕上镜片直径 2R 对应源上 (2R/mag) 显示像素 → 再换成位图像素
  const sampleCss = (2 * r) / mag.value
  const sw = Math.max(1, (sampleCss / srcRect.width) * bw)
  const sh = Math.max(1, (sampleCss / srcRect.height) * bh)
  const sx = u * bw - sw / 2
  const sy = v * bh - sh / 2

  ctx.imageSmoothingEnabled = true
  ctx.imageSmoothingQuality = 'high'
  try {
    ctx.drawImage(source, sx, sy, sw, sh, 0, 0, side, side)
  } catch {
    /* ignore */
  }

  const g = ctx.createRadialGradient(side / 2, side / 2, side * 0.4, side / 2, side / 2, side / 2)
  g.addColorStop(0, 'rgba(0,0,0,0)')
  g.addColorStop(0.75, 'rgba(0,0,0,0)')
  g.addColorStop(1, 'rgba(0,0,0,0.16)')
  ctx.fillStyle = g
  ctx.fillRect(0, 0, side, side)
  ctx.restore()
}

function tick(ts) {
  raf = 0
  const dt = lastTs ? Math.min(0.05, (ts - lastTs) / 1000) : 0.016
  lastTs = ts
  const on = props.active && pointerInside ? 1 : 0
  const next = alpha.value + (on - alpha.value) * (1 - Math.exp(-dt / 0.07))
  alpha.value = Math.abs(next - on) < 0.004 ? on : next
  if (alpha.value > 0.01) paint()
  if (props.active || alpha.value > 0.003) {
    raf = requestAnimationFrame(tick)
  } else {
    lastTs = 0
  }
}

function ensureLoop() {
  if (!raf) raf = requestAnimationFrame(tick)
}

function onMove(e) {
  if (!props.active) return
  const stage = resolveStage()
  if (!stage) return
  const rect = stage.getBoundingClientRect()
  stageSize.value = { w: rect.width, h: rect.height }
  pointer.value = {
    x: e.clientX - rect.left,
    y: e.clientY - rect.top
  }
  pointerInside = true
  // 立刻画一帧，避免只靠 rAF 淡入时看起来「没有放大」
  if (alpha.value < 0.3) alpha.value = Math.max(alpha.value, 0.35)
  paint()
  ensureLoop()
}

function onLeave() {
  pointerInside = false
  ensureLoop()
}

function bind() {
  const stage = resolveStage()
  if (!stage) return
  if (boundStage === stage) return
  unbind()
  stage.addEventListener('pointermove', onMove, { passive: true })
  stage.addEventListener('pointerdown', onMove, { passive: true })
  stage.addEventListener('pointerleave', onLeave)
  boundStage = stage
}

function unbind() {
  if (!boundStage) return
  boundStage.removeEventListener('pointermove', onMove)
  boundStage.removeEventListener('pointerdown', onMove)
  boundStage.removeEventListener('pointerleave', onLeave)
  boundStage = null
}

watch(
  () => [props.active, props.stageEl, props.sourceRoot],
  async ([active]) => {
    await nextTick()
    unbind()
    if (!active) {
      pointerInside = false
      ensureLoop()
      return
    }
    bind()
    const stage = resolveStage()
    if (stage) {
      const rect = stage.getBoundingClientRect()
      stageSize.value = { w: rect.width, h: rect.height }
      pointer.value = { x: rect.width / 2, y: rect.height / 2 }
      pointerInside = true
      alpha.value = Math.max(alpha.value, 0.4)
      paint()
    }
    ensureLoop()
  },
  { immediate: true }
)

onMounted(async () => {
  await nextTick()
  if (props.active) {
    bind()
    paint()
    ensureLoop()
  }
})

onBeforeUnmount(() => {
  unbind()
  if (raf) cancelAnimationFrame(raf)
})

defineExpose({ repaint: paint })
</script>

<style scoped lang="scss">
.film-loupe {
  position: absolute;
  z-index: 8;
  pointer-events: none;
  transform: translate(-50%, -50%);
  border-radius: 50%;
  opacity: 0;
  will-change: left, top, opacity;
  overflow: hidden;
  filter: drop-shadow(0 10px 16px rgba(0, 0, 0, 0.28)) drop-shadow(0 2px 4px rgba(0, 0, 0, 0.18));
}

.film-loupe-glass {
  position: absolute;
  inset: var(--loupe-rim, 7px);
  width: calc(100% - 2 * var(--loupe-rim, 7px));
  height: calc(100% - 2 * var(--loupe-rim, 7px));
  border-radius: 50%;
  display: block;
  background: #0e0b09;
}

.film-loupe-rim {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  box-sizing: border-box;
  border: var(--loupe-rim, 7px) solid rgba(236, 232, 224, 0.94);
  box-shadow:
    inset 0 0 0 1px rgba(255, 255, 255, 0.55),
    inset 0 0 0 2px rgba(40, 36, 30, 0.35);
  pointer-events: none;
}

.film-loupe-glint {
  position: absolute;
  inset: var(--loupe-rim, 7px);
  border-radius: 50%;
  background: radial-gradient(
    circle at 32% 28%,
    rgba(255, 255, 255, 0.22) 0%,
    rgba(255, 255, 255, 0.05) 28%,
    rgba(255, 255, 255, 0) 52%
  );
  pointer-events: none;
}
</style>
