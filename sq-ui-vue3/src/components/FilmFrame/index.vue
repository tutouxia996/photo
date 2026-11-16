<template>
  <div
    ref="rootRef"
    class="film-frame"
    :class="[
      `mode-${mode}`,
      {
        'is-live': isLive,
        'is-canvas': useCanvas,
        'is-canvas-ready': useCanvas && canvasReady,
        'is-passthrough': !chromeOn,
        'has-border': showBorder,
        'has-info': showInfo
      }
    ]"
    :style="liveCssVars"
  >
    <!-- 图片：canvas 合成（可随缩放一起变） -->
    <canvas
      v-show="useCanvas && canvasReady"
      ref="canvasRef"
      class="film-canvas"
      :class="{ 'is-thumb': mode === 'thumb' }"
      draggable="false"
    />

    <!-- 视频 live：真 video 在片窗里播，周围套片边 -->
    <div v-if="isLive && chromeOn" class="film-live-strip">
      <div v-if="showBorder" class="live-rebate lead" aria-hidden="true">
        <div class="live-edge top">
          <span class="edge-stock">{{ edgeText }}</span>
          <span class="edge-frame">{{ frameLabel }}</span>
        </div>
        <svg class="live-holes" :viewBox="`0 0 ${liveAlongMm} ${rebateMm}`" preserveAspectRatio="none">
          <rect class="rebate-fill" x="0" y="0" :width="liveAlongMm" :height="rebateMm" />
          <rect
            v-for="(h, i) in liveHoles"
            :key="'t' + i"
            class="hole"
            :x="h.s"
            :y="h.t"
            :width="perf.w"
            :height="perf.h"
            :rx="perf.r"
            :ry="perf.r"
          />
        </svg>
      </div>

      <div ref="gateRef" class="live-gate">
        <div ref="slotRef" class="film-slot is-live-slot">
          <slot />
        </div>
        <div v-if="showInfo && dateBackText" class="date-back">{{ dateBackText }}</div>
        <div v-if="showInfo && placeBackText" class="place-back">{{ placeBackText }}</div>
      </div>

      <div v-if="showBorder" class="live-rebate trail" aria-hidden="true">
        <svg class="live-holes" :viewBox="`0 0 ${liveAlongMm} ${rebateMm}`" preserveAspectRatio="none">
          <rect class="rebate-fill" x="0" y="0" :width="liveAlongMm" :height="rebateMm" />
          <rect
            v-for="(h, i) in liveHoles"
            :key="'b' + i"
            class="hole"
            :x="h.s"
            :y="rebateMm - perf.edge - perf.h"
            :width="perf.w"
            :height="perf.h"
            :rx="perf.r"
            :ry="perf.r"
          />
        </svg>
        <div class="live-edge bottom">
          <span class="edge-frame">{{ frameAlt }}</span>
          <span class="edge-arrow">→</span>
        </div>
      </div>
    </div>

    <!-- 无片边 / 图片 canvas 的源 slot；缩略图在合成完成前先显示原图，避免黑块 -->
    <div
      v-if="!isLive"
      ref="slotRef"
      class="film-slot"
      :class="{ 'is-source-only': useCanvas && canvasReady }"
    >
      <slot />
    </div>
    <div v-else-if="!chromeOn" ref="slotRef" class="film-slot is-passthrough-slot">
      <slot />
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { formatFilmDate, formatFilmLocation } from '@/utils/filmCaption'
import { COLORS, getFormat, PERF_135 } from '@/utils/spektra/formats'
import { renderFilmStrip } from '@/utils/spektra/renderStrip'
import { getEdgeLook, getEdgeText, getStockPreset, inkCss } from '@/utils/spektra/stocks'
import { enqueueThumbPaint } from '@/utils/spektra/thumbQueue'

const props = defineProps({
  photo: { type: Object, default: null },
  mode: { type: String, default: 'viewer' },
  showBorder: { type: Boolean, default: true },
  showInfo: { type: Boolean, default: true },
  stockId: { type: String, default: 'kodak_portra_400|135' },
  frameNo: { type: [Number, String], default: null },
  /** 视频等：保留真实媒体播放，片边用 DOM/SVG 套在外面 */
  live: { type: Boolean, default: false }
})

const emit = defineEmits(['rendered'])

const rootRef = ref(null)
const canvasRef = ref(null)
const slotRef = ref(null)
const gateRef = ref(null)
const gateH = ref(0)
/** 缩略图：合成完成前先露原图，避免整屏黑块 */
const canvasReady = ref(false)
const inView = ref(props.mode !== 'thumb')

const chromeOn = computed(() => props.showBorder || props.showInfo)
const isLive = computed(() => !!props.live)
const useCanvas = computed(() => chromeOn.value && !isLive.value)
const isThumb = computed(() => props.mode === 'thumb')

const preset = computed(() => getStockPreset(props.stockId))
const fmt = computed(() => getFormat(preset.value.formatId))
const perf = PERF_135
const edgeInk = computed(() => {
  const look = getEdgeLook(preset.value.id)
  return inkCss(fmt.value.perforated ? look.ink135 : look.ink120)
})

const edgeText = computed(() => getEdgeText(preset.value.id, fmt.value.perforated ? '135' : '120'))
const frameNum = computed(() => {
  if (props.frameNo != null && props.frameNo !== '') {
    const n = Number(props.frameNo)
    return Number.isFinite(n) ? Math.max(0, Math.floor(n)) : 1
  }
  return ((Number(props.photo?.photoId) || 0) % 36) + 1
})
const frameLabel = computed(() => String(frameNum.value))
const frameAlt = computed(() => `${frameNum.value}A`)

const dateBackText = computed(() => {
  const raw = formatFilmDate(props.photo?.shootTime)
  return raw ? raw.replace(/\./g, ' ') : ''
})
const placeBackText = computed(() => formatFilmLocation(props.photo))

/** 片基条带高度（mm）：整条片宽减去片窗 across，再对半分到上下 */
const rebateMm = computed(() => Math.max(0.1, (fmt.value.filmW - fmt.value.across) / 2))
/** 片条沿片长度（mm）：片窗 along + 两侧边距 */
const liveAlongMm = computed(() => fmt.value.along + 1.7)

const liveHoles = computed(() => {
  if (!fmt.value.perforated) return []
  const holes = []
  const phase = -3.2
  const pitch = perf.pitch
  const sLo = -0.85
  const sHi = fmt.value.along + 0.85
  let k = Math.floor((sLo - 6 - phase) / pitch)
  while (phase + k * pitch < sHi + 6) {
    const s = phase + k * pitch
    holes.push({ s, t: perf.edge })
    k += 1
  }
  return holes
})

const liveCssVars = computed(() => {
  if (!isLive.value) return {}
  const across = fmt.value.across
  const rebate = rebateMm.value
  const rebatePx = gateH.value > 0 ? Math.round(gateH.value * (rebate / across)) : 0
  const rebateCss = `${Math.min(56, Math.max(28, rebatePx || 36))}px`
  return {
    '--rebate-ratio': String(rebate / across),
    '--rebate-px': rebateCss,
    '--film-base': COLORS.rebatePrint,
    '--film-hole': COLORS.holeWhite,
    '--film-ink': edgeInk.value,
    '--date-glow': COLORS.dateLight
  }
})

let mo = null
let gateRo = null
let io = null
let boundMedia = null
let renderToken = 0
let thumbJob = null

function observeGate() {
  if (gateRo) {
    gateRo.disconnect()
    gateRo = null
  }
  if (!gateRef.value || !isLive.value) return
  gateRo = new ResizeObserver((entries) => {
    const h = entries[0]?.contentRect?.height || 0
    if (h > 0) gateH.value = h
  })
  gateRo.observe(gateRef.value)
}

function observeView() {
  if (io) {
    io.disconnect()
    io = null
  }
  if (!isThumb.value || !rootRef.value || typeof IntersectionObserver === 'undefined') {
    inView.value = true
    return
  }
  io = new IntersectionObserver(
    (entries) => {
      const hit = entries.some((e) => e.isIntersecting)
      inView.value = hit
      if (hit) schedulePaint()
    },
    { root: null, rootMargin: '120px 0px', threshold: 0.01 }
  )
  io.observe(rootRef.value)
}

function mediaEl() {
  const root = slotRef.value
  if (!root) return null
  const hires = root.querySelector?.('img.media-image.is-hires')
  // 高清已就绪才用；否则先用缩略预览合成，避免干等
  if (hires && hires.complete && hires.naturalWidth > 0) return hires
  const preview = root.querySelector?.('img.media-image-preview')
  if (preview && (preview.complete ? preview.naturalWidth > 0 : true)) return preview
  if (hires) return hires
  return root.querySelector?.('img, video') || null
}

function allMediaEls() {
  const root = slotRef.value
  if (!root) return []
  return Array.from(root.querySelectorAll?.('img, video') || [])
}

function cancelThumbJob() {
  if (thumbJob) {
    thumbJob.cancel()
    thumbJob = null
  }
}

async function paintNow(token) {
  const canvas = canvasRef.value
  const media = mediaEl()
  if (!canvas || !media) return false

  // 记录当前 src，避免切图瞬间用上一张的位图合成片边
  const srcAtStart = media.currentSrc || media.src || ''

  const ready = () => {
    if (token !== renderToken) return false
    const srcNow = media.currentSrc || media.src || ''
    if (srcAtStart && srcNow && srcNow !== srcAtStart) return false
    if (media.tagName === 'VIDEO') return media.readyState >= 2 && media.videoWidth > 0
    return media.complete && media.naturalWidth > 0
  }

  if (!ready()) {
    await new Promise((resolve) => {
      const done = () => {
        media.removeEventListener('load', done)
        media.removeEventListener('loadeddata', done)
        resolve()
      }
      media.addEventListener('load', done)
      media.addEventListener('loadeddata', done)
      setTimeout(done, 2500)
    })
  }
  if (token !== renderToken || !ready()) return false

  // 缩略图小边；全屏用更高边长，避免片边合成发糊
  const maxLongEdge = isThumb.value
    ? Math.min(220, Math.max(media.naturalWidth || 160, 120))
    : Math.min(1600, Math.max(media.naturalWidth || 1000, 900))

  const result = renderFilmStrip(canvas, media, {
    stockId: preset.value.id,
    formatId: preset.value.formatId,
    frameNo: frameNum.value,
    showBorder: props.showBorder,
    // 网格缩略图关闭日期字，显著减负；全屏 viewer 仍画
    showInfo: isThumb.value ? false : props.showInfo,
    dateText: isThumb.value ? '' : dateBackText.value,
    placeText: isThumb.value ? '' : placeBackText.value,
    maxLongEdge,
    holesWhite: true
  })

  if (!result || token !== renderToken) return false
  const srcAfter = media.currentSrc || media.src || ''
  if (srcAtStart && srcAfter && srcAfter !== srcAtStart) return false

  if (isThumb.value) {
    canvas.style.width = '100%'
    canvas.style.height = '100%'
    canvas.style.objectFit = 'cover'
  } else {
    canvas.style.width = 'auto'
    canvas.style.height = 'auto'
    canvas.style.maxWidth = 'min(88vw, 1120px)'
    canvas.style.maxHeight = 'min(74vh, 740px)'
  }
  canvasReady.value = true
  emit('rendered', true)
  return true
}

function schedulePaint() {
  if (isLive.value) {
    emit('rendered', true)
    return
  }
  if (!useCanvas.value) {
    canvasReady.value = false
    emit('rendered', false)
    return
  }
  // 缩略图不在视口内：保持原图，不排队合成
  if (isThumb.value && !inView.value) return

  const token = ++renderToken
  cancelThumbJob()

  const run = () => paintNow(token)

  if (isThumb.value) {
    thumbJob = enqueueThumbPaint(run)
    thumbJob.promise.catch(() => {})
  } else {
    run()
  }
}

function unbindAllMedia() {
  allMediaEls().forEach((el) => {
    el.removeEventListener('load', schedulePaint)
    el.removeEventListener('loadeddata', schedulePaint)
  })
  if (boundMedia) {
    boundMedia.removeEventListener('load', schedulePaint)
    boundMedia.removeEventListener('loadeddata', schedulePaint)
    boundMedia = null
  }
}

function bindMedia() {
  unbindAllMedia()
  const nodes = allMediaEls()
  nodes.forEach((el) => {
    el.addEventListener('load', schedulePaint)
    el.addEventListener('loadeddata', schedulePaint)
  })
  boundMedia = mediaEl()
  schedulePaint()
}

onMounted(async () => {
  await nextTick()
  observeView()
  bindMedia()
  observeGate()
  // viewer 才盯 src 变化；缩略图靠虚拟列表 key 重建即可，避免 MO 风暴
  if (!isThumb.value && rootRef.value) {
    mo = new MutationObserver(() => bindMedia())
    mo.observe(rootRef.value, { childList: true, subtree: true, attributes: true, attributeFilter: ['src'] })
  }
})

onBeforeUnmount(() => {
  cancelThumbJob()
  renderToken += 1
  unbindAllMedia()
  if (mo) mo.disconnect()
  if (gateRo) gateRo.disconnect()
  if (io) io.disconnect()
})

watch(
  () => [props.photo?.photoId, props.stockId, props.showBorder, props.showInfo, props.frameNo, props.mode, props.live],
  async () => {
    canvasReady.value = false
    await nextTick()
    observeGate()
    observeView()
    bindMedia()
  }
)

defineExpose({ repaint: schedulePaint })
</script>

<style scoped lang="scss">
.film-frame {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  max-width: 100%;
  max-height: 100%;
  box-sizing: border-box;
}

.film-frame.mode-thumb {
  display: flex;
  width: 100%;
  height: 100%;
  max-width: none;
  max-height: none;
}

.film-canvas {
  display: block;
  max-width: 100%;
  max-height: 100%;
  user-select: none;
  -webkit-user-drag: none;
  pointer-events: none;
}

.film-canvas.is-thumb {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.film-slot {
  display: flex;
  align-items: center;
  justify-content: center;
  max-width: 100%;
  max-height: 100%;
}

.film-slot.is-source-only {
  position: absolute;
  left: -99999px;
  top: 0;
  width: 2px;
  height: 2px;
  overflow: hidden;
  opacity: 0;
  pointer-events: none;
}

/* 缩略图：合成完成前显示原图；完成后才藏源图、露 canvas */
.film-frame.mode-thumb .film-slot {
  width: 100%;
  height: 100%;
}

.film-frame.mode-thumb .film-slot :deep(img),
.film-frame.mode-thumb .film-slot :deep(video),
.film-frame.mode-thumb .film-slot :deep(.thumb-placeholder) {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  background: #111;
}

.film-frame.mode-thumb.is-canvas-ready .film-slot {
  position: absolute;
  left: -99999px;
  width: 2px;
  height: 2px;
  overflow: hidden;
  opacity: 0;
  pointer-events: none;
}

.film-frame.mode-thumb.is-passthrough .film-slot,
.film-slot.is-passthrough-slot {
  width: 100%;
  height: 100%;
}

.film-frame.mode-thumb.is-passthrough :deep(img),
.film-frame.mode-thumb.is-passthrough :deep(video),
.film-frame.mode-thumb.is-passthrough :deep(.thumb-placeholder) {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

/* —— live 视频片边 —— */
.film-live-strip {
  display: flex;
  flex-direction: column;
  background: var(--film-base, #0e0b09);
  max-width: 100%;
  max-height: 100%;
  box-shadow: 0 1px 0 rgba(255, 255, 255, 0.04) inset;
}

.live-rebate {
  position: relative;
  flex: 0 0 auto;
  height: var(--rebate-px, 36px);
  overflow: hidden;
  background: var(--film-base, #0e0b09);
}

/* 齿孔盖在字之上：字即使擦边也不会露进白孔 */
.live-holes {
  position: absolute;
  inset: 0;
  z-index: 2;
  width: 100%;
  height: 100%;
  display: block;
  pointer-events: none;
}

.rebate-fill {
  fill: var(--film-base, #0e0b09);
}

.hole {
  fill: var(--film-hole, #f6f4f0);
}

/*
 * 片边字只占外侧 ~2mm / 5.35mm ≈ 37% 的安全带（齿孔从 edge=2mm 起）。
 * lead：字贴顶外沿；trail：字贴底外沿。
 */
.live-edge {
  position: absolute;
  left: 0;
  right: 0;
  z-index: 1;
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 37%;
  padding: 0 12px;
  box-sizing: border-box;
  color: var(--film-ink, #d9a04a);
  font-family: "Helvetica Neue", Helvetica, Arial, sans-serif;
  font-weight: 700;
  font-size: clamp(8px, 1.1vw, 11px);
  letter-spacing: 0.1em;
  text-transform: uppercase;
  line-height: 1;
  pointer-events: none;
  user-select: none;
  overflow: hidden;
}

.live-edge.top {
  top: 0;
}

.live-edge.bottom {
  bottom: 0;
}

.live-gate {
  position: relative;
  flex: 0 1 auto;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #050505;
  min-width: 0;
  min-height: 0;
}

.film-slot.is-live-slot {
  display: flex;
  max-width: 100%;
  max-height: 100%;
}

.film-slot.is-live-slot :deep(video),
.film-slot.is-live-slot :deep(img) {
  display: block;
  max-width: min(88vw, 1200px);
  /* 给上下片基留出高度，避免整条胶片撑出视口 */
  max-height: min(calc(72vh - 2 * var(--rebate-px, 36px)), 720px);
  width: auto;
  height: auto;
  object-fit: contain;
  background: #000;
}

.date-back,
.place-back {
  position: absolute;
  z-index: 2;
  pointer-events: none;
  font-family: "OCR A Std", "OCR A", "Courier New", monospace;
  font-weight: 700;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--date-glow, #ff6624);
  text-shadow:
    0 0 4px rgba(255, 170, 60, 0.95),
    0 0 10px rgba(255, 140, 40, 0.55);
  font-size: clamp(10px, 1.4vw, 14px);
}

.date-back {
  right: 3.2%;
  bottom: 3.6%;
}

.place-back {
  left: 3.2%;
  bottom: 3.6%;
  max-width: 55%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: clamp(9px, 1.2vw, 12px);
}

</style>
