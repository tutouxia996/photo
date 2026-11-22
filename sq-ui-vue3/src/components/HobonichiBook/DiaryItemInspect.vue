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
          :class="{ flipped, lifting }"
          :style="cardStyle"
          @click.stop
          @dblclick.prevent="toggleFlip"
        >
          <div class="inspect-tilt" :style="tiltStyle">
            <div class="inspect-face front">
              <div class="paper-frame" :class="frameKind">
                <video
                  v-if="item.kind === 'video'"
                  class="face-media"
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
                  class="face-media"
                  :src="frontSrc"
                  :alt="item.fileName || '正面'"
                  draggable="false"
                />
                <div class="paper-gloss" aria-hidden="true" />
              </div>
            </div>
            <div class="inspect-face back">
              <div class="paper-frame back-paper" :class="frameKind">
                <img
                  v-if="backSrc"
                  class="face-media"
                  :src="backSrc"
                  alt="背面"
                  draggable="false"
                />
                <div v-else class="back-blank">
                  <div class="back-grain" aria-hidden="true" />
                  <div class="back-stain s1" aria-hidden="true" />
                  <div class="back-stain s2" aria-hidden="true" />
                  <p v-if="item.fileName" class="back-caption">{{ item.fileName }}</p>
                  <p class="back-hint">背面</p>
                </div>
                <div class="paper-gloss" aria-hidden="true" />
              </div>
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
import { resolveMediaUrl } from './pageContent.js'

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

const frameKind = computed(() => {
  const k = props.item?.kind || 'image'
  if (k === 'video') return 'kind-video'
  const a = props.item?.aspect
  if (a && a < 0.85) return 'kind-note'
  return 'kind-photo'
})

const stageStyle = computed(() => {
  const r = props.fromRect
  if (!r) return {}
  return {
    '--ox': `${r.left + r.width / 2}px`,
    '--oy': `${r.top + r.height / 2}px`,
    '--ow': `${Math.max(40, r.width)}px`,
    '--oh': `${Math.max(40, r.height)}px`
  }
})

const cardStyle = computed(() => {
  const a = props.item?.aspect
  const aspect = a && a > 0 ? a : 3 / 4
  const maxW = aspect < 1 ? 'min(42vw, 340px)' : 'min(48vw, 420px)'
  return {
    aspectRatio: `${aspect}`,
    width: maxW,
    maxHeight: '72vh'
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

function close() {
  ready.value = false
  lifting.value = false
  window.clearTimeout(closeTimer)
  closeTimer = window.setTimeout(() => emit('close'), 280)
}

watch(
  () => props.item,
  async (v) => {
    flipped.value = false
    tilt.value = { x: 0, y: 0 }
    ready.value = false
    lifting.value = true
    if (!v) return
    await nextTick()
    rootRef.value?.focus?.({ preventScroll: true })
    requestAnimationFrame(() => {
      ready.value = true
      requestAnimationFrame(() => {
        lifting.value = false
      })
    })
  },
  { immediate: true }
)

onUnmounted(() => {
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
    transform 0.55s cubic-bezier(0.22, 0.61, 0.24, 1),
    width 0.55s cubic-bezier(0.22, 0.61, 0.24, 1),
    opacity 0.35s ease;
  cursor: grab;
  &.lifting {
    transform: translate(
        calc(var(--ox, 50vw) - 50vw),
        calc(var(--oy, 48vh) - 48vh)
      )
      scale(0.42);
    opacity: 0.85;
  }
}

.inspect-root:not(.ready) .inspect-card {
  opacity: 0;
  transform: translateY(12px) scale(0.92);
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
  border-radius: 3px;
  overflow: visible;
}

.inspect-face.back {
  transform: rotateY(180deg);
}

.paper-frame {
  position: relative;
  width: 100%;
  height: 100%;
  box-sizing: border-box;
  padding: 4.5%;
  background:
    linear-gradient(145deg, #f7f0df 0%, #efe4cc 48%, #e8d9b8 100%);
  border-radius: 2px;
  overflow: hidden;
  box-shadow:
    inset 0 0 0 1px rgba(90, 70, 40, 0.12),
    inset 0 0 40px rgba(160, 120, 70, 0.08);

  &.kind-note {
    padding: 5.5%;
    background:
      linear-gradient(160deg, #f3e6c4 0%, #e8d29a 55%, #dfc484 100%);
    clip-path: polygon(
      1% 0.8%,
      98.5% 0%,
      100% 2%,
      99.2% 97.5%,
      97% 100%,
      2% 99.4%,
      0% 97%,
      0.6% 2%
    );
  }

  &.kind-photo {
    border-radius: 3px;
  }

  &.kind-video {
    background: linear-gradient(145deg, #f4eee0, #e8dcc4);
  }
}

.back-paper {
  background:
    linear-gradient(155deg, #efe2c4 0%, #e4d3a8 40%, #dcc890 100%);
}

.face-media {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center;
  border-radius: 1px;
  background: rgba(180, 160, 130, 0.25);
  filter: contrast(1.02) saturate(0.96);
  pointer-events: auto;
}

.paper-gloss {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background:
    linear-gradient(
      125deg,
      rgba(255, 250, 235, 0.28) 0%,
      transparent 38%,
      transparent 62%,
      rgba(90, 60, 30, 0.08) 100%
    );
  mix-blend-mode: soft-light;
}

.back-blank {
  position: relative;
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  overflow: hidden;
}

.back-grain {
  position: absolute;
  inset: 0;
  opacity: 0.35;
  background-image:
    radial-gradient(circle at 20% 30%, rgba(120, 90, 50, 0.12) 0 1px, transparent 2px),
    radial-gradient(circle at 70% 60%, rgba(90, 70, 40, 0.1) 0 1px, transparent 2px),
    radial-gradient(circle at 40% 80%, rgba(140, 100, 60, 0.08) 0 1.5px, transparent 2.5px);
  background-size: 18px 18px, 22px 22px, 28px 28px;
}

.back-stain {
  position: absolute;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(150, 100, 50, 0.18), transparent 70%);
  pointer-events: none;
  &.s1 {
    width: 38%;
    height: 28%;
    top: 12%;
    right: 8%;
  }
  &.s2 {
    width: 26%;
    height: 22%;
    bottom: 18%;
    left: 10%;
    opacity: 0.7;
  }
}

.back-caption {
  position: relative;
  z-index: 1;
  margin: 0;
  max-width: 80%;
  text-align: center;
  font-family: 'Zen Kaku Gothic New', 'Noto Sans SC', sans-serif;
  font-size: 12px;
  letter-spacing: 0.04em;
  color: rgba(70, 55, 35, 0.55);
  word-break: break-all;
}

.back-hint {
  position: relative;
  z-index: 1;
  margin: 0;
  font-family: 'Jost', 'Zen Kaku Gothic New', sans-serif;
  font-size: 11px;
  letter-spacing: 0.28em;
  text-transform: uppercase;
  color: rgba(70, 55, 35, 0.38);
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
  .inspect-card {
    width: min(78vw, 340px) !important;
  }
  .inspect-chrome {
    font-size: 11px;
    gap: 6px;
    padding: 7px 12px;
  }
}
</style>
