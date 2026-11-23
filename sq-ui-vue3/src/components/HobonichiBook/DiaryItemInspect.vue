<template>
  <Teleport to="body">
    <div
      v-if="item"
      class="inspect-root"
      :class="{ ready }"
      @pointermove="onPointerMove"
      @pointerleave="resetTilt"
      @keydown.esc.prevent="close"
      @keydown.f.prevent="toggleFlip"
      tabindex="-1"
      ref="rootRef"
      role="dialog"
      aria-label="查看物品"
    >
      <div class="inspect-veil" @click="close" />

      <div
        class="inspect-stage"
        :style="stageStyle"
        @click="close"
      >
        <div
          class="inspect-card"
          :class="{ flipped, lifting, 'size-locked': sizeLocked }"
          :style="cardStyle"
          @click.stop
          @dblclick.prevent="toggleFlip"
        >
          <div class="inspect-tilt" :style="tiltStyle">
            <div class="inspect-face front">
              <FilmFrame
                class="inspect-film"
                :photo="filmPhoto"
                mode="viewer"
                :live="item.kind === 'video'"
                :show-border="true"
                :show-info="false"
                :stock-id="filmStockId"
                :frame-no="frameNo"
                :max-long-edge="2200"
              >
                <video
                  v-if="item.kind === 'video'"
                  class="face-media media-image"
                  :src="frontSrc"
                  :poster="posterSrc"
                  controls
                  playsinline
                  preload="metadata"
                  controlslist="nofullscreen"
                  disablepictureinpicture
                  @dblclick.prevent.stop="toggleFlip"
                />
                <img
                  v-else
                  class="face-media media-image is-hires"
                  :src="frontSrc"
                  :alt="item.fileName || '正面'"
                  draggable="false"
                />
              </FilmFrame>
            </div>
            <div class="inspect-face back">
              <FilmFrame
                class="inspect-film"
                :photo="filmPhoto"
                mode="viewer"
                :show-border="true"
                :show-info="false"
                :stock-id="filmStockId"
                :frame-no="frameNo"
                :max-long-edge="2200"
              >
                <img
                  class="face-media media-image is-hires"
                  :src="backDisplaySrc"
                  :alt="backSrc ? '背面' : '胶片背面'"
                  draggable="false"
                />
              </FilmFrame>
            </div>
          </div>
        </div>
      </div>

      <div class="inspect-chrome" aria-hidden="true">
        <span>双击 / F 翻转</span>
        <span class="dot">·</span>
        <span>点击空白放回</span>
        <span class="dot">·</span>
        <span>Esc 关闭</span>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { computed, nextTick, onUnmounted, ref, watch } from 'vue'
import FilmFrame from '@/components/FilmFrame/index.vue'
import {
  filmFrameNo,
  filmStripAspect,
  readAlbumFilmStockId,
  resolveMediaUrl
} from './pageContent.js'

const props = defineProps({
  item: { type: Object, default: null },
  /** 贴图在页面上的初始屏幕矩形，用于拿起动画 */
  fromRect: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['close'])

const rootRef = ref(null)
const ready = ref(false)
const flipped = ref(false)
const flipping = ref(false)
const lifting = ref(true)
const tilt = ref({ x: 0, y: 0 })
const filmStockId = ref(readAlbumFilmStockId())
let closeTimer = 0
let flipTimer = 0

const frontSrc = computed(() =>
  resolveMediaUrl(props.item?.src || props.item?.thumbSrc || '')
)
const posterSrc = computed(() =>
  resolveMediaUrl(props.item?.thumbSrc || props.item?.src || '')
)
const backSrc = computed(() =>
  resolveMediaUrl(props.item?.backSrc || '')
)

const filmPhoto = computed(() => ({
  photoId: props.item?.photoId,
  fileName: props.item?.fileName,
  shootTime: props.item?.shootTime || null,
  location: props.item?.location || null
}))

const frameNo = computed(() => filmFrameNo(props.item))

/** 无背面图时用同比例深色占位，仍走胶片合成 */
const blankFilmSrc = computed(() => {
  const a = props.item?.aspect && props.item.aspect > 0 ? props.item.aspect : 3 / 4
  const w = a >= 1 ? 1200 : Math.round(1200 * a)
  const h = a >= 1 ? Math.round(1200 / a) : 1200
  const name = String(props.item?.fileName || '')
    .replace(/[<>&"']/g, '')
    .slice(0, 42)
  const nameLine = name
    ? `<text x="50%" y="58%" text-anchor="middle" fill="rgba(196,184,154,0.55)" font-size="28" font-family="Helvetica,Arial,sans-serif">${name}</text>`
    : ''
  const svg =
    `<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}">` +
    `<rect width="100%" height="100%" fill="#14110e"/>` +
    `<text x="50%" y="48%" text-anchor="middle" fill="rgba(220,210,190,0.72)" font-size="42" letter-spacing="0.35em" font-family="Helvetica,Arial,sans-serif">BACK</text>` +
    nameLine +
    `</svg>`
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`
})

const backDisplaySrc = computed(() => backSrc.value || blankFilmSrc.value)

const sizeLocked = computed(() => {
  const r = props.fromRect
  return !!(r && r.width > 8 && r.height > 8)
})

const stageStyle = computed(() => {
  const r = props.fromRect
  if (!r) return {}
  const cx = r.centerX != null ? r.centerX : r.left + r.width / 2
  const cy = r.centerY != null ? r.centerY : r.top + r.height / 2
  return {
    '--ox': `${cx}px`,
    '--oy': `${cy}px`,
    '--ow': `${Math.max(40, r.width)}px`,
    '--oh': `${Math.max(40, r.height)}px`
  }
})

const cardStyle = computed(() => {
  const r = props.fromRect
  // 与日记页贴图同尺寸拿起，不再放大成固定大卡
  if (r && r.width > 8 && r.height > 8) {
    return {
      width: `${r.width}px`,
      height: `${r.height}px`,
      maxWidth: 'none',
      maxHeight: 'none',
      aspectRatio: 'auto'
    }
  }
  const photoA = props.item?.aspect && props.item.aspect > 0 ? props.item.aspect : 3 / 4
  const aspect = filmStripAspect(photoA, filmStockId.value)
  const maxW = aspect < 1 ? 'min(42vw, 340px)' : 'min(52vw, 460px)'
  return {
    aspectRatio: `${aspect}`,
    width: maxW,
    maxHeight: '78vh'
  }
})

const tiltStyle = computed(() => {
  // 加大视差：约 ±26° / ±34°
  const rx = tilt.value.y * -26
  const ry = tilt.value.x * 34
  const base = flipped.value ? 180 : 0
  const liftZ = Math.abs(tilt.value.x) * 18 + Math.abs(tilt.value.y) * 12
  return {
    transform: `translateZ(${liftZ}px) rotateX(${rx}deg) rotateY(${base + ry}deg)`,
    transition: flipping.value
      ? 'transform 0.55s cubic-bezier(0.22, 0.61, 0.24, 1)'
      : 'transform 0.12s ease-out'
  }
})

function onPointerMove(e) {
  if (!rootRef.value) return
  const rect = rootRef.value.getBoundingClientRect()
  // 相对卡片中心灵敏度更高：用整屏归一化后再放大
  const nx = ((e.clientX - rect.left) / Math.max(1, rect.width) - 0.5) * 2.4
  const ny = ((e.clientY - rect.top) / Math.max(1, rect.height) - 0.5) * 2.4
  tilt.value = {
    x: Math.max(-1, Math.min(1, nx)),
    y: Math.max(-1, Math.min(1, ny))
  }
}

function resetTilt() {
  tilt.value = { x: 0, y: 0 }
}

function toggleFlip() {
  flipping.value = true
  flipped.value = !flipped.value
  window.clearTimeout(flipTimer)
  flipTimer = window.setTimeout(() => {
    flipping.value = false
  }, 560)
}

let openRaf = 0

function close() {
  resetTilt()
  flipping.value = false
  // 先回到页上原位，再卸掉弹层
  lifting.value = true
  ready.value = false
  window.clearTimeout(closeTimer)
  closeTimer = window.setTimeout(() => emit('close'), sizeLocked.value ? 480 : 300)
}

watch(
  () => props.item,
  async (v) => {
    window.cancelAnimationFrame(openRaf)
    window.clearTimeout(closeTimer)
    flipped.value = false
    flipping.value = false
    tilt.value = { x: 0, y: 0 }
    ready.value = false
    lifting.value = true
    if (!v) return
    await nextTick()
    rootRef.value?.focus?.({ preventScroll: true })
    // 1) 先画在页上原位 2) 再放开 lifting，过渡到屏幕中心
    openRaf = requestAnimationFrame(() => {
      ready.value = true
      openRaf = requestAnimationFrame(() => {
        // 强制一次布局，确保浏览器吃到 lifting 起始帧
        void rootRef.value?.offsetWidth
        lifting.value = false
      })
    })
  },
  { immediate: true }
)

onUnmounted(() => {
  window.cancelAnimationFrame(openRaf)
  window.clearTimeout(closeTimer)
  window.clearTimeout(flipTimer)
})

defineExpose({ close, toggleFlip })
</script>

<style scoped lang="scss">
.inspect-root {
  position: fixed;
  inset: 0;
  z-index: 4200;
  outline: none;
  cursor: default;
  perspective: 1100px;
}

.inspect-veil {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse at 50% 42%, rgba(28, 22, 16, 0.18), rgba(18, 14, 10, 0.55) 70%),
    rgba(16, 12, 8, 0.42);
  backdrop-filter: blur(3px) saturate(0.88);
  opacity: 0;
  transition: opacity 0.35s ease;
}

.inspect-root.ready .inspect-veil {
  opacity: 1;
}

.inspect-stage {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
  perspective: 1100px;
}

.inspect-card {
  position: relative;
  pointer-events: auto;
  transform-style: preserve-3d;
  transform-origin: center center;
  filter: drop-shadow(0 22px 32px rgba(12, 8, 4, 0.48)) drop-shadow(3px 8px 14px rgba(40, 28, 12, 0.3));
  transition:
    transform 0.48s cubic-bezier(0.22, 0.61, 0.24, 1),
    width 0.48s cubic-bezier(0.22, 0.61, 0.24, 1),
    height 0.48s cubic-bezier(0.22, 0.61, 0.24, 1),
    opacity 0.35s ease;
  will-change: transform, opacity;
  cursor: grab;

  /* 起始/放回：对齐页上贴图中心（flex 居中点 ≈ 50vw / 50vh） */
  &.lifting.size-locked {
    transform: translate(
      calc(var(--ox, 50vw) - 50vw),
      calc(var(--oy, 50vh) - 50vh)
    );
    opacity: 1;
  }
  &.lifting:not(.size-locked) {
    transform: translate(
        calc(var(--ox, 50vw) - 50vw),
        calc(var(--oy, 50vh) - 50vh)
      )
      scale(0.42);
    opacity: 0.85;
  }
}

/* 无页上锚点时才用淡入；有 size-locked 时靠位移过渡，避免突然闪到中间 */
.inspect-root:not(.ready) .inspect-card:not(.size-locked) {
  opacity: 0;
  transform: translateY(12px) scale(0.92);
}

.inspect-root:not(.ready) .inspect-card.size-locked.lifting {
  opacity: 1;
}

.inspect-tilt {
  width: 100%;
  height: 100%;
  position: relative;
  transform-style: preserve-3d;
  will-change: transform;
}

.inspect-face {
  position: absolute;
  inset: 0;
  backface-visibility: hidden;
  -webkit-backface-visibility: hidden;
  border-radius: 0;
  overflow: visible;
}

.inspect-face.back {
  transform: rotateY(180deg);
}

.inspect-film {
  width: 100%;
  height: 100%;
  max-width: none;
  max-height: none;
  display: flex !important;
}

/* 覆盖 FilmFrame viewer 内联 max 尺寸，铺满拿起卡片；底透明透出齿孔 */
.inspect-film :deep(.film-canvas) {
  width: 100% !important;
  height: 100% !important;
  max-width: none !important;
  max-height: none !important;
  object-fit: contain;
  background: transparent;
}

.inspect-film :deep(.film-live-strip) {
  width: 100%;
  height: 100%;
  max-width: none;
  max-height: none;
}

.inspect-film :deep(.live-rebate) {
  flex: 0 0 var(--rebate-px, 36px);
}

.inspect-film :deep(.live-gate) {
  flex: 1 1 auto;
  min-height: 0;
  height: 0;
  width: 100%;
}

.inspect-film :deep(.film-slot.is-live-slot) {
  width: 100%;
  height: 100%;
  max-width: none;
  max-height: none;
}

.inspect-film :deep(.film-slot.is-live-slot video),
.inspect-film :deep(.film-slot.is-live-slot img) {
  width: 100%;
  height: 100%;
  max-width: none;
  max-height: none;
  object-fit: cover;
}

.face-media {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center;
  border-radius: 0;
  background: #111;
  pointer-events: auto;
}

.inspect-chrome {
  position: absolute;
  left: 50%;
  bottom: calc(18px + env(safe-area-inset-bottom));
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  border-radius: 999px;
  background: rgba(20, 14, 10, 0.48);
  color: rgba(245, 236, 220, 0.82);
  font-family: 'Zen Kaku Gothic New', 'Noto Sans SC', sans-serif;
  font-size: 12px;
  letter-spacing: 0.08em;
  pointer-events: none;
  opacity: 0;
  transition: opacity 0.35s ease 0.15s;
  .dot { opacity: 0.4; }
}

.inspect-root.ready .inspect-chrome {
  opacity: 1;
}

@media (max-width: 768px) {
  .inspect-card:not(.size-locked) {
    width: min(78vw, 340px) !important;
  }
  .inspect-chrome {
    font-size: 11px;
    gap: 6px;
    padding: 7px 12px;
  }
}
</style>
