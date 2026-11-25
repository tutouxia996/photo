<template>
  <div class="train-ticket-card" :class="{ flat }">
    <div class="tt-slab">
      <canvas ref="cvRef" class="tt-canvas" :width="cw" :height="ch" />
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import {
  normalizeStyleConfig,
  normalizeTicketData,
  renderTicketCanvas,
  TICKET_CANVAS_H,
  TICKET_CANVAS_W
} from './trainTicket.js'

const props = defineProps({
  data: { type: Object, default: () => ({}) },
  styleConfig: { type: Object, default: null },
  face: { type: String, default: 'front' },
  /** 仅用于触发父级感知尺寸，实际按 canvas 固有比缩放 */
  widthPx: { type: Number, default: 280 },
  /** 编辑器预览等平面场景关掉立体 */
  flat: { type: Boolean, default: false }
})

const emit = defineEmits(['rendered'])

const cvRef = ref(null)
const cw = TICKET_CANVAS_W
const ch = TICKET_CANVAS_H
let gen = 0

async function paint() {
  const cv = cvRef.value
  if (!cv) return
  const my = ++gen
  try {
    await renderTicketCanvas(
      cv,
      normalizeTicketData(props.data),
      normalizeStyleConfig(props.styleConfig),
      props.face === 'back' ? 'back' : 'front'
    )
    if (my === gen) emit('rendered')
  } catch (e) {
    console.warn('[TrainTicketCard] render failed', e)
  }
}

onMounted(paint)
watch(
  () => JSON.stringify({ d: props.data, s: props.styleConfig, f: props.face }),
  () => paint()
)
</script>

<style scoped>
.train-ticket-card {
  width: 100%;
  height: 100%;
  position: relative;
  pointer-events: none;
  user-select: none;
  background: transparent;
  perspective: 900px;
  perspective-origin: 40% 30%;
}

.tt-slab {
  position: relative;
  width: 100%;
  height: 100%;
  transform-style: preserve-3d;
  transform: rotateX(3deg) rotateY(-2deg) translateZ(0);
  /* 贴近纸面的轻微落影：偏下一点，几乎贴边 */
  filter: drop-shadow(0 1px 1.5px rgba(40, 32, 24, 0.22));
}

.tt-canvas {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: contain;
}

.train-ticket-card.flat {
  perspective: none;
}
.train-ticket-card.flat .tt-slab {
  transform: none;
  filter: drop-shadow(0 1px 1.5px rgba(40, 32, 24, 0.18));
}
</style>
