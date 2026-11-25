<template>
  <Teleport to="body">
    <div
      v-if="item"
      ref="rootRef"
      class="ticket-inspect"
      :class="{ ready }"
      tabindex="-1"
      role="dialog"
      aria-label="查看火车票"
      @keydown.esc.prevent="close"
    >
      <div class="ti-veil" @click="close" />

      <div
        ref="viewportRef"
        class="ti-viewport"
        :class="{ lifting }"
        @click.stop
      />

      <div class="ti-chrome" aria-hidden="true">
        <span>拖动旋转正反面</span>
        <span class="dot">·</span>
        <span>滚轮放大细节</span>
        <span class="dot">·</span>
        <span>点击空白放回</span>
        <span class="dot">·</span>
        <span>Esc 关闭</span>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { computed, nextTick, onUnmounted, reactive, ref, watch } from 'vue'
import { renderTicketCanvas, TICKET_CANVAS_H, TICKET_CANVAS_W } from './trainTicket.js'

const props = defineProps({
  item: { type: Object, default: null },
  fromRect: { type: Object, default: null }
})

const emit = defineEmits(['close'])

const rootRef = ref(null)
const viewportRef = ref(null)
const ready = ref(false)
const lifting = ref(true)

let closeTimer = 0
let openRaf = 0
let paintGen = 0
let threeApi = null
let state = null
let unbind = null
const config = reactive({
  autoRotate: false,
  rotationSpeed: 0.5,
  zoom: 1,
  showProtrusions: true
})

const sizeLocked = computed(() => {
  const r = props.fromRect
  return !!(r && r.width > 8 && r.height > 8)
})

function updateConfig(partial) {
  Object.assign(config, partial)
}

function makeFaceCanvas() {
  const cv = document.createElement('canvas')
  cv.width = TICKET_CANVAS_W
  cv.height = TICKET_CANVAS_H
  return cv
}

async function paintFaces() {
  if (!props.item) return null
  const my = ++paintGen
  const front = makeFaceCanvas()
  const back = makeFaceCanvas()
  await Promise.all([
    renderTicketCanvas(front, props.item.data, props.item.style, 'front'),
    renderTicketCanvas(back, props.item.data, props.item.style, 'back')
  ])
  if (my !== paintGen) return null
  return { front, back }
}

function teardownThree() {
  unbind?.()
  unbind = null
  if (threeApi && state) {
    threeApi.disposeThreeJS(viewportRef.value, state)
  }
  state = null
}

async function setupThree() {
  teardownThree()
  const faces = await paintFaces()
  if (!faces || !viewportRef.value) return

  if (!threeApi) {
    threeApi = await import('@/vendor/train-ticket/renderer3d.js')
  }

  // 等 viewport 进入全屏尺寸后再初始化，避免 0 尺寸
  await nextTick()
  await new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r)))

  const el = viewportRef.value
  if (!el || el.clientWidth < 8 || el.clientHeight < 8) return

  state = threeApi.createInitialState()
  config.zoom = 1
  config.autoRotate = false
  config.showProtrusions = props.item?.style?.showProtrusions !== false

  const ok = threeApi.initThreeJS(el, faces.front, faces.back, state, config)
  if (!ok) {
    state = null
    return
  }

  // 初始朝向稍微侧一点，能看到圆角厚度
  state.targetRotation = { x: -0.28, y: 0.55 }
  if (state.ticketGroup) {
    state.ticketGroup.rotation.x = state.targetRotation.x
    state.ticketGroup.rotation.y = state.targetRotation.y
  }

  unbind = threeApi.bindEvents(el, state, config, updateConfig)
  threeApi.animate(state, config)
}

function close() {
  lifting.value = true
  ready.value = false
  window.clearTimeout(closeTimer)
  closeTimer = window.setTimeout(() => {
    teardownThree()
    emit('close')
  }, sizeLocked.value ? 420 : 280)
}

watch(
  () => props.item,
  async (v) => {
    window.cancelAnimationFrame(openRaf)
    window.clearTimeout(closeTimer)
    teardownThree()
    ready.value = false
    lifting.value = true
    if (!v) return

    await nextTick()
    rootRef.value?.focus?.({ preventScroll: true })

    openRaf = requestAnimationFrame(() => {
      ready.value = true
      openRaf = requestAnimationFrame(async () => {
        void rootRef.value?.offsetWidth
        lifting.value = false
        await setupThree()
      })
    })
  },
  { immediate: true }
)

onUnmounted(() => {
  window.cancelAnimationFrame(openRaf)
  window.clearTimeout(closeTimer)
  teardownThree()
})
</script>

<style scoped lang="scss">
.ticket-inspect {
  position: fixed;
  inset: 0;
  z-index: 4000;
  outline: none;
}

.ti-veil {
  position: absolute;
  inset: 0;
  background: rgba(28, 22, 16, 0);
  transition: background 0.35s ease;
}
.ticket-inspect.ready .ti-veil {
  background: rgba(28, 22, 16, 0.55);
}

.ti-viewport {
  position: absolute;
  /* 接近全屏，放大时有足够画布；四边留空可点遮罩放回 */
  inset: 36px 20px 68px;
  width: auto;
  height: auto;
  transform: scale(1);
  cursor: grab;
  touch-action: none;
  opacity: 1;
  transition:
    transform 0.42s cubic-bezier(0.22, 0.61, 0.24, 1),
    opacity 0.35s ease;
  will-change: transform, opacity;

  &:active {
    cursor: grabbing;
  }

  :deep(canvas) {
    display: block;
    width: 100% !important;
    height: 100% !important;
  }

  &.lifting {
    opacity: 0;
    transform: scale(0.92);
  }
}

.ticket-inspect:not(.ready) .ti-viewport {
  opacity: 0;
}

.ti-chrome {
  position: absolute;
  left: 50%;
  bottom: 28px;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  border-radius: 999px;
  background: rgba(20, 16, 12, 0.62);
  color: rgba(247, 242, 232, 0.88);
  font-size: 12px;
  letter-spacing: 0.04em;
  opacity: 0;
  transition: opacity 0.3s ease;
  pointer-events: none;
  white-space: nowrap;
}
.ticket-inspect.ready .ti-chrome {
  opacity: 1;
}
.dot {
  opacity: 0.45;
}

@media (prefers-reduced-motion: reduce) {
  .ti-viewport {
    transition: none;
  }
}
</style>
