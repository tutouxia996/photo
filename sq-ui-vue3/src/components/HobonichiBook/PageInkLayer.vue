<template>
  <div
    class="ink-layer"
    :class="[`tool-${tool}`, { active: enabled, spread: isSpread }]"
    :style="wrapStyle"
  >
    <canvas
      ref="drawRef"
      class="ink-draw"
      @pointerdown="onDrawDown"
      @pointermove="onDrawMove"
      @pointerup="onDrawUp"
      @pointercancel="onDrawUp"
    />

    <div
      v-for="box in textboxes"
      v-show="tool === 'text'"
      :key="box.id"
      class="text-box"
      :class="{ selected: selectedId === box.id }"
      :style="boxStyle(box)"
      @pointerdown.stop="onBoxPointerDown($event, box)"
    >
      <div class="text-handle" title="拖动" @pointerdown.stop="onDragStart($event, box)" />
      <div
        class="text-body"
        contenteditable="true"
        spellcheck="false"
        :data-id="box.id"
        @focus="onTextFocus(box)"
        @blur="onTextBlur($event, box)"
        @keydown.stop
      >{{ box.text }}</div>
    </div>

    <div
      v-for="m in mediaBoxes"
      v-show="showMediaOverlay"
      :key="m.id"
      class="media-box"
      :class="{
        selected: selectedId === m.id,
        editable: tool === 'media',
        video: m.kind === 'video'
      }"
      :style="mediaStyle(m)"
      @pointerdown.stop="onMediaPointerDown($event, m)"
    >
      <video
        v-if="m.kind === 'video'"
        class="media-el"
        :src="mediaSrc(m)"
        :poster="mediaPoster(m)"
        controls
        playsinline
        preload="metadata"
        @pointerdown.stop
      />
      <img
        v-else
        class="media-el"
        :src="mediaSrc(m)"
        :alt="m.fileName || ''"
        draggable="false"
        decoding="async"
        @load="onMediaImgLoad($event, m)"
      />
      <template v-if="tool === 'media'">
        <div class="media-handle" title="拖动" @pointerdown.stop="onMediaDragStart($event, m)" />
        <div class="media-resize" title="缩放" @pointerdown.stop="onMediaResizeStart($event, m)" />
      </template>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import {
  hitTestMedia,
  hitTestStroke,
  hitTestTextbox,
  mediaHeightForWidth,
  mergeSpreadLayers,
  newId,
  paintStrokeOnCtx,
  resolveMediaUrl,
  simplifyStroke
} from './pageContent.js'

const props = defineProps({
  pageNo: { type: Number, default: null },
  content: { type: Object, default: null },
  leftPageNo: { type: Number, default: null },
  rightPageNo: { type: Number, default: null },
  leftContent: { type: Object, default: null },
  rightContent: { type: Object, default: null },
  tool: { type: String, default: 'turn' },
  color: { type: String, default: '#2B2A33' },
  penWidth: { type: Number, default: 0.004 },
  rect: { type: Object, default: () => ({ x: 0, y: 0, w: 0, h: 0 }) },
  /** 对开外缘可拖翻的宽度（相对整幅）；略加大便于回翻 */
  previewStrokes: { type: Boolean, default: true },
  interactive: { type: Boolean, default: true }
})

const emit = defineEmits(['update-content', 'request-import'])

const drawRef = ref(null)
const selectedId = ref(null)
const drafting = ref(null)
const dragging = ref(null)
const resizing = ref(null)
/** 橡皮滑动擦除中 */
const erasing = ref(null)

const isSpread = computed(() => !!(props.leftPageNo && props.rightPageNo))

/** 对开图层挂在左页；仅右页时挂右页 */
const hostPageNo = computed(() => {
  if (isSpread.value) return props.leftPageNo
  return props.pageNo || props.rightPageNo || props.leftPageNo
})

const hostContent = computed(() => {
  if (isSpread.value) return props.leftContent
  return props.content || props.rightContent || props.leftContent
})

const enabled = computed(() =>
  props.interactive &&
  (isSpread.value || !!props.pageNo || !!props.leftPageNo || !!props.rightPageNo)
)

const wrapStyle = computed(() => ({
  left: `${props.rect.x || 0}px`,
  top: `${props.rect.y || 0}px`,
  width: `${Math.max(0, props.rect.w || 0)}px`,
  height: `${Math.max(0, props.rect.h || 0)}px`,
  pointerEvents: enabled.value ? 'auto' : 'none',
  zIndex: enabled.value ? 6 : 0
}))

const viewLayers = computed(() => {
  if (isSpread.value) {
    return mergeSpreadLayers(props.leftContent, props.rightContent)
  }
  const c = hostContent.value
  return (c && c.layers) || []
})

const textboxes = computed(() => viewLayers.value.filter((l) => l && l.type === 'textbox'))
const mediaBoxes = computed(() => viewLayers.value.filter((l) => l && l.type === 'media'))
/** 编辑预览时用 HTML 贴图层（视频可播）；翻页时改烘焙进纸面 */
const showMediaOverlay = computed(() => !!props.previewStrokes)

function boxStyle(box) {
  return {
    left: `${(box.x || 0) * 100}%`,
    top: `${(box.y || 0) * 100}%`,
    width: `${(box.w || 0.4) * 100}%`,
    minHeight: `${(box.h || 0.08) * 100}%`,
    color: box.color || props.color,
    fontSize: `${Math.max(12, (box.fontSize || 0.035) * (props.rect.h || 400))}px`
  }
}

function mediaStyle(m) {
  const rot = m.rot ? `rotate(${m.rot}rad)` : null
  return {
    left: `${(m.x || 0) * 100}%`,
    top: `${(m.y || 0) * 100}%`,
    width: `${(m.w || 0.32) * 100}%`,
    height: `${(m.h || 0.28) * 100}%`,
    transform: rot || undefined
  }
}

function mediaSrc(m) {
  // 显示用中图/原图，不用网格小缩略图
  return resolveMediaUrl(m.src || m.thumbSrc || '')
}

function mediaPoster(m) {
  // 仅视频封面可用小图；图片勿走 thumb
  if (m?.kind === 'video') return resolveMediaUrl(m.thumbSrc || '')
  return resolveMediaUrl(m.src || m.thumbSrc || '')
}

/** 贴图是否压住订口（对开坐标） */
function mediaCrossesSpine(x, w) {
  return x < 0.5 - 0.004 && x + w > 0.5 + 0.004
}

/** 对开视图坐标 → 单页坐标（完全落在一侧时） */
function spreadMediaToPage(x, y, w, h) {
  const cx = x + w / 2
  if (cx < 0.5) {
    let px = Math.min(1, Math.max(0, x * 2))
    let pw = Math.min(0.92, Math.max(0.12, w * 2))
    // 钳在单页内，避免「差一点点跨缝」又被裁掉
    if (px + pw > 1) pw = Math.max(0.12, 1 - px)
    return { side: 'left', x: px, w: pw, y, h }
  }
  let px = Math.min(1, Math.max(0, (x - 0.5) * 2))
  let pw = Math.min(0.92, Math.max(0.12, w * 2))
  if (px + pw > 1) pw = Math.max(0.12, 1 - px)
  return { side: 'right', x: px, w: pw, y, h }
}

function upsertMediaLayer(layer) {
  const id = layer.id
  const base = {
    id,
    type: 'media',
    rot: layer.rot || 0,
    kind: layer.kind || 'image',
    src: layer.src || '',
    thumbSrc: layer.thumbSrc || '',
    photoId: layer.photoId ?? null,
    fileName: layer.fileName || '',
    aspect: layer.aspect || null
  }

  if (isSpread.value) {
    const x = layer.x || 0
    const y = layer.y || 0
    const w = layer.w || 0.32
    const h = layer.h || 0.28

    // 跨订口：必须存 space:spread，翻页时左右页各烘焙半幅，否则右半会被右页盖住
    if (mediaCrossesSpine(x, w)) {
      upsertSpreadLayer({
        ...base,
        space: 'spread',
        x: Math.min(0.82, Math.max(0.02, x)),
        y,
        w: Math.min(0.55, Math.max(0.08, w)),
        h
      })
      return
    }

    const mapped = spreadMediaToPage(x, y, w, h)
    const pageNo = mapped.side === 'left' ? props.leftPageNo : props.rightPageNo
    const src = mapped.side === 'left' ? props.leftContent : props.rightContent
    const otherNo = mapped.side === 'left' ? props.rightPageNo : props.leftPageNo
    const otherSrc = mapped.side === 'left' ? props.rightContent : props.leftContent
    const payload = {
      ...base,
      space: 'page',
      x: mapped.x,
      y: mapped.y,
      w: mapped.w,
      h: mapped.h
    }
    const layers = clonePageLayers(src).filter((l) => l.id !== id)
    layers.push(payload)
    emitPage(pageNo, layers)
    // 从另一页 / 左页 spread 残留里清掉同 id
    if (otherNo && (otherSrc?.layers || []).some((l) => l && l.id === id)) {
      emitPage(
        otherNo,
        clonePageLayers(otherSrc).filter((l) => l.id !== id)
      )
    }
    // 若原先挂在左页 spread，也要清掉
    if (mapped.side === 'right' && props.leftPageNo) {
      const leftLayers = (props.leftContent?.layers || [])
      if (leftLayers.some((l) => l && l.id === id && l.space === 'spread')) {
        emitPage(
          props.leftPageNo,
          clonePageLayers(props.leftContent).filter((l) => l.id !== id)
        )
      }
    }
    return
  }

  const payload = {
    ...base,
    space: 'page',
    x: layer.x,
    y: layer.y,
    w: layer.w,
    h: layer.h
  }
  const layers = clonePageLayers(hostContent.value).filter((l) => l.id !== id)
  layers.push(payload)
  emitPage(hostPageNo.value, layers)
}

function onMediaImgLoad(e, m) {
  const img = e?.target
  if (!img?.naturalWidth || !img.naturalHeight) return
  const aspect = img.naturalWidth / img.naturalHeight
  if (!aspect || !isFinite(aspect)) return
  const w = m.w || 0.42
  // 视图可能是对开坐标：用当前显示宽重算高
  const expectH = mediaHeightForWidth(w, aspect, isSpread.value)
  const curH = m.h || 0
  if (Math.abs(curH - expectH) / Math.max(expectH, 1e-6) < 0.12) {
    if (!m.aspect) upsertMediaLayer({ ...m, aspect })
    return
  }
  upsertMediaLayer({ ...m, aspect, h: expectH })
}

function syncCanvasSize() {
  const cv = drawRef.value
  if (!cv) return
  const w = Math.max(1, Math.round(props.rect.w || 1))
  const h = Math.max(1, Math.round(props.rect.h || 1))
  if (cv.width !== w || cv.height !== h) {
    cv.width = w
    cv.height = h
  }
  redrawInk()
}

function redrawInk() {
  const cv = drawRef.value
  if (!cv) return
  const g = cv.getContext('2d')
  g.clearRect(0, 0, cv.width, cv.height)
  if (props.previewStrokes) {
    for (const layer of viewLayers.value) {
      if (layer && layer.type === 'stroke') {
        paintStrokeOnCtx(g, layer, cv.width, cv.height, 1)
      }
    }
  }
  const d = drafting.value
  if (!d || !d.points || !d.points.length) return
  // 对开：width 用页相对，显示时 *0.5
  const showW = isSpread.value ? (d.width || props.penWidth) * 0.5 : (d.width || props.penWidth)
  paintStrokeOnCtx(g, { ...d, width: showW }, cv.width, cv.height, 1)
}

function localNorm(e) {
  const el = drawRef.value
  const r = el.getBoundingClientRect()
  const x = (e.clientX - r.left) / Math.max(1, r.width)
  const y = (e.clientY - r.top) / Math.max(1, r.height)
  return {
    x: Math.min(1, Math.max(0, x)),
    y: Math.min(1, Math.max(0, y)),
    nx: x,
    ny: y
  }
}

function clonePageLayers(content) {
  const layers = (content && content.layers) || []
  return layers.map((l) => ({
    ...l,
    points: l.points ? l.points.map((p) => [...p]) : undefined
  }))
}

function emitPage(pageNo, layers) {
  if (!pageNo) return
  emit('update-content', {
    pageNo,
    content: { v: 1, layers }
  })
}

/** 在宿主页写入/更新一条 space:spread 图层；并清掉左右页旧版拆笔 */
function upsertSpreadLayer(layer) {
  const hostNo = hostPageNo.value
  const leftNo = props.leftPageNo
  const rightNo = props.rightPageNo

  if (isSpread.value) {
    const leftLayers = clonePageLayers(props.leftContent).filter((l) => {
      if (l.id === layer.id) return false
      if (typeof l.id === 'string' && (l.id === `${layer.id}_L` || l.id === `${layer.id}_R`)) return false
      return true
    })
    leftLayers.push({ ...layer, space: 'spread' })
    emitPage(leftNo, leftLayers)

    // 仅当右页仍残留同 id / 旧拆笔时才回写，避免无意义覆盖
    const rightSrc = (props.rightContent && props.rightContent.layers) || []
    const needClean = rightSrc.some(
      (l) =>
        l &&
        (l.id === layer.id ||
          l.id === `${layer.id}_L` ||
          l.id === `${layer.id}_R`)
    )
    if (needClean) {
      const rightLayers = clonePageLayers(props.rightContent).filter((l) => {
        if (l.id === layer.id) return false
        if (typeof l.id === 'string' && (l.id === `${layer.id}_L` || l.id === `${layer.id}_R`)) {
          return false
        }
        return true
      })
      emitPage(rightNo, rightLayers)
    }
  } else {
    const layers = clonePageLayers(hostContent.value).filter((l) => l.id !== layer.id)
    layers.push({ ...layer, space: layer.space || 'page' })
    emitPage(hostNo, layers)
  }
}

function removeViewLayer(viewLayer) {
  const id = viewLayer._pageId || viewLayer.id
  if (viewLayer.space === 'spread' || viewLayer._side === 'spread') {
    const leftLayers = clonePageLayers(props.leftContent).filter(
      (l) => l.id !== id && l.id !== `${id}_L` && l.id !== `${id}_R`
    )
    const rightLayers = clonePageLayers(props.rightContent).filter(
      (l) => l.id !== id && l.id !== `${id}_L` && l.id !== `${id}_R`
    )
    if (props.leftPageNo) emitPage(props.leftPageNo, leftLayers)
    if (props.rightPageNo) emitPage(props.rightPageNo, rightLayers)
    return
  }
  if (isSpread.value) {
    const side = viewLayer._side
    const pageNo = side === 'left' ? props.leftPageNo : props.rightPageNo
    const src = side === 'left' ? props.leftContent : props.rightContent
    emitPage(pageNo, clonePageLayers(src).filter((l) => l.id !== id))
    return
  }
  emitPage(hostPageNo.value, clonePageLayers(hostContent.value).filter((l) => l.id !== id))
}

function onDrawDown(e) {
  if (!enabled.value || props.tool === 'turn') return
  if (e.button != null && e.button !== 0) return
  if ((props.rect.w || 0) < 8 || (props.rect.h || 0) < 8) return
  const p = localNorm(e)

  // 模式隔离：非翻页工具不在页缘转发翻页，各工具只做自己的事
  if (props.tool === 'eraser') {
    erasing.value = {
      id: e.pointerId,
      removed: new Set()
    }
    eraseAtPoint(p.x, p.y, erasing.value.removed)
    try {
      drawRef.value?.setPointerCapture?.(e.pointerId)
    } catch { /* ignore */ }
    e.preventDefault()
    e.stopPropagation()
    return
  }

  if (props.tool === 'text') {
    const box = {
      id: newId('t'),
      type: 'textbox',
      space: isSpread.value ? 'spread' : 'page',
      x: Math.min(0.85, Math.max(0.02, p.x - 0.02)),
      y: Math.min(0.88, Math.max(0.02, p.y - 0.02)),
      w: isSpread.value ? 0.28 : 0.4,
      h: 0.1,
      text: '',
      fontSize: 0.035,
      color: props.color
    }
    if (isSpread.value) upsertSpreadLayer(box)
    else {
      const layers = clonePageLayers(hostContent.value)
      layers.push(box)
      emitPage(hostPageNo.value, layers)
    }
    selectedId.value = box.id
    nextTick(() => {
      const el = drawRef.value?.parentElement?.querySelector(`.text-body[data-id="${box.id}"]`)
      el?.focus?.()
    })
    e.preventDefault()
    e.stopPropagation()
    return
  }

  if (props.tool === 'pen') {
    drafting.value = {
      id: newId('s'),
      type: 'stroke',
      tool: 'pen',
      color: props.color,
      width: props.penWidth,
      points: [[p.x, p.y]]
    }
    try {
      drawRef.value?.setPointerCapture?.(e.pointerId)
    } catch { /* ignore */ }
    redrawInk()
    e.preventDefault()
    e.stopPropagation()
    return
  }

  // 图片模式点空白处 → 打开导入
  if (props.tool === 'media') {
    selectedId.value = null
    emit('request-import')
    e.preventDefault()
    e.stopPropagation()
  }
}

function eraseAtPoint(x, y, removedSet) {
  const hitR = isSpread.value ? 0.018 : 0.022
  const layers = viewLayers.value
  for (let i = layers.length - 1; i >= 0; i--) {
    const l = layers[i]
    const id = l._pageId || l.id
    if (removedSet && removedSet.has(id)) continue
    const hit =
      (l.type === 'textbox' && hitTestTextbox(l, x, y)) ||
      (l.type === 'media' && hitTestMedia(l, x, y)) ||
      (l.type === 'stroke' && hitTestStroke(l, x, y, hitR))
    if (!hit) continue
    if (removedSet) removedSet.add(id)
    removeViewLayer(l)
    break
  }
}

function onDrawMove(e) {
  if (erasing.value && (e.pointerId == null || e.pointerId === erasing.value.id)) {
    const p = localNorm(e)
    eraseAtPoint(p.x, p.y, erasing.value.removed)
    return
  }
  if (!drafting.value) return
  const p = localNorm(e)
  drafting.value.points.push([p.x, p.y])
  redrawInk()
}

function onDrawUp(e) {
  if (erasing.value) {
    if (e?.pointerId != null && e.pointerId !== erasing.value.id) return
    erasing.value = null
    return
  }
  const d = drafting.value
  if (!d) return
  drafting.value = null
  const pts = simplifyStroke(d.points, isSpread.value ? 0.0015 : 0.002)
  redrawInk()
  if (!pts.length) return

  if (isSpread.value) {
    upsertSpreadLayer({
      id: d.id,
      type: 'stroke',
      space: 'spread',
      tool: 'pen',
      color: d.color,
      width: d.width || props.penWidth,
      points: pts
    })
    return
  }
  const layers = clonePageLayers(hostContent.value)
  layers.push({ ...d, space: 'page', points: pts })
  emitPage(hostPageNo.value, layers)
}

function onTextFocus(box) {
  selectedId.value = box.id
}

function onTextBlur(e, box) {
  selectedId.value = null
  const text = (e.target.innerText || '').replace(/\u00a0/g, ' ')
  const id = box._pageId || box.id
  if (!text.trim()) {
    removeViewLayer(box)
    return
  }
  if (box.space === 'spread' || box._side === 'spread' || isSpread.value && (box.space === 'spread' || box._host)) {
    const cur = viewLayers.value.find((l) => (l._pageId || l.id) === id)
    if (cur && cur.text === text) return
    upsertSpreadLayer({
      id,
      type: 'textbox',
      space: 'spread',
      x: box.x,
      y: box.y,
      w: box.w,
      h: box.h,
      text,
      fontSize: box.fontSize || 0.035,
      color: box.color || props.color
    })
    return
  }
  if (isSpread.value) {
    const side = box._side
    const pageNo = side === 'left' ? props.leftPageNo : props.rightPageNo
    const src = side === 'left' ? props.leftContent : props.rightContent
    const layers = clonePageLayers(src)
    const idx = layers.findIndex((l) => l.id === id)
    if (idx < 0) return
    if (layers[idx].text === text) return
    layers[idx] = { ...layers[idx], text }
    emitPage(pageNo, layers)
    return
  }
  const layers = clonePageLayers(hostContent.value)
  const idx = layers.findIndex((l) => l.id === id)
  if (idx < 0) return
  if (layers[idx].text === text) return
  layers[idx] = { ...layers[idx], text }
  emitPage(hostPageNo.value, layers)
}

function onBoxPointerDown(e, box) {
  if (props.tool === 'eraser') {
    removeViewLayer(box)
    e.preventDefault()
  }
}

function onMediaPointerDown(e, m) {
  if (props.tool === 'eraser') {
    removeViewLayer(m)
    e.preventDefault()
    return
  }
  if (props.tool === 'media') {
    selectedId.value = m.id
    onMediaDragStart(e, m)
  }
}

function onMediaDragStart(e, m) {
  if (props.tool === 'eraser') return
  if (props.tool !== 'media') return
  const p = localNorm(e)
  selectedId.value = m.id
  dragging.value = {
    kind: 'media',
    id: e.pointerId,
    box: { ...m },
    ox: p.x - (m.x || 0),
    oy: p.y - (m.y || 0)
  }
  e.currentTarget?.setPointerCapture?.(e.pointerId)
  window.addEventListener('pointermove', onDragMove)
  window.addEventListener('pointerup', onDragEnd)
  window.addEventListener('pointercancel', onDragEnd)
  e.preventDefault()
}

function onMediaResizeStart(e, m) {
  if (props.tool !== 'media') return
  const p = localNorm(e)
  selectedId.value = m.id
  resizing.value = {
    id: e.pointerId,
    box: { ...m },
    startX: p.x,
    startY: p.y,
    startW: m.w || 0.32,
    startH: m.h || 0.28
  }
  e.currentTarget?.setPointerCapture?.(e.pointerId)
  window.addEventListener('pointermove', onResizeMove)
  window.addEventListener('pointerup', onResizeEnd)
  window.addEventListener('pointercancel', onResizeEnd)
  e.preventDefault()
}

function onResizeMove(e) {
  const d = resizing.value
  if (!d || (e.pointerId != null && e.pointerId !== d.id)) return
  const p = localNorm(e)
  const dx = p.x - d.startX
  const dy = p.y - d.startY
  const minW = isSpread.value ? 0.1 : 0.16
  const minH = 0.1
  const maxW = isSpread.value ? 0.45 : 0.85
  const maxH = 0.7
  let nw = Math.min(maxW, Math.max(minW, d.startW + dx))
  let nh = Math.min(maxH, Math.max(minH, d.startH + dy))
  const aspect = d.box.aspect || (d.startW / Math.max(d.startH, 1e-6))
  // 等比缩放，保持贴图框贴合照片比例
  if (Math.abs(dx) >= Math.abs(dy)) {
    nh = mediaHeightForWidth(nw, aspect, isSpread.value)
    nh = Math.min(maxH, Math.max(minH, nh))
    nw = Math.min(maxW, Math.max(minW, nw))
  } else {
    // 由高反推宽：spread 时 h = 2*w*PAGE_AR/a → w = h*a/(2*PAGE_AR)
    const PAGE_AR = 148 / 210
    nw = isSpread.value
      ? (nh * aspect) / (2 * PAGE_AR)
      : (nh * aspect) / PAGE_AR
    nw = Math.min(maxW, Math.max(minW, nw))
    nh = mediaHeightForWidth(nw, aspect, isSpread.value)
  }
  const nx = Math.min(1 - nw, Math.max(0, d.box.x || 0))
  const ny = Math.min(1 - nh * 0.5, Math.max(0, d.box.y || 0))
  upsertMediaLayer({
    ...d.box,
    x: nx,
    y: ny,
    w: nw,
    h: nh,
    aspect
  })
}

function onResizeEnd() {
  resizing.value = null
  window.removeEventListener('pointermove', onResizeMove)
  window.removeEventListener('pointerup', onResizeEnd)
  window.removeEventListener('pointercancel', onResizeEnd)
}

function onDragStart(e, box) {
  if (props.tool === 'eraser') return
  const p = localNorm(e)
  dragging.value = {
    id: e.pointerId,
    box: { ...box },
    ox: p.x - (box.x || 0),
    oy: p.y - (box.y || 0)
  }
  e.currentTarget.setPointerCapture?.(e.pointerId)
  window.addEventListener('pointermove', onDragMove)
  window.addEventListener('pointerup', onDragEnd)
  window.addEventListener('pointercancel', onDragEnd)
  e.preventDefault()
}

function onDragMove(e) {
  const d = dragging.value
  if (!d || (e.pointerId != null && e.pointerId !== d.id)) return
  const p = localNorm(e)
  const isMedia = d.kind === 'media' || d.box?.type === 'media'
  const w = d.box.w || (isMedia ? 0.32 : 0.28)
  const h = d.box.h || (isMedia ? 0.28 : 0.1)
  const nx = Math.min(1 - w, Math.max(0, p.x - d.ox))
  const ny = Math.min(1 - h * 0.5, Math.max(0, p.y - d.oy))
  d.box = { ...d.box, x: nx, y: ny }

  const id = d.box._pageId || d.box.id
  if (isMedia) {
    upsertMediaLayer({ ...d.box, id, x: nx, y: ny, w, h })
    return
  }
  if (isSpread.value || d.box.space === 'spread' || d.box._side === 'spread') {
    upsertSpreadLayer({
      id,
      type: 'textbox',
      space: 'spread',
      x: nx,
      y: ny,
      w,
      h: d.box.h || 0.1,
      text: d.box.text || '',
      fontSize: d.box.fontSize || 0.035,
      color: d.box.color || props.color
    })
    return
  }
  const layers = clonePageLayers(hostContent.value)
  const idx = layers.findIndex((l) => l.id === id)
  if (idx < 0) return
  layers[idx] = { ...layers[idx], x: nx, y: ny }
  emitPage(hostPageNo.value, layers)
}

function onDragEnd() {
  dragging.value = null
  window.removeEventListener('pointermove', onDragMove)
  window.removeEventListener('pointerup', onDragEnd)
  window.removeEventListener('pointercancel', onDragEnd)
}

watch(() => [props.rect.w, props.rect.h], () => syncCanvasSize(), { immediate: true })
watch(
  () => [props.leftContent, props.rightContent, props.content, props.previewStrokes],
  () => {
    drafting.value = null
    redrawInk()
  },
  { deep: true }
)
watch(
  () => [props.leftPageNo, props.rightPageNo, props.pageNo],
  () => {
    drafting.value = null
    nextTick(() => syncCanvasSize())
  }
)

// 切走文字工具前先 blur，把 DOM 里的字写回 layers
watch(
  () => props.tool,
  (tool, prev) => {
    if (prev === 'text' && tool !== 'text') {
      const active = document.activeElement
      if (active && active.classList?.contains('text-body')) {
        active.blur()
      }
    }
  }
)

onMounted(() => syncCanvasSize())
onUnmounted(() => {
  erasing.value = null
  drafting.value = null
  onDragEnd()
  onResizeEnd()
})
</script>

<style scoped lang="scss">
.ink-layer {
  position: absolute;
  z-index: 0;
  overflow: hidden;
  border-radius: 0;
  touch-action: none;
}

/* 未激活时必须让子元素也不拦截事件，否则挡翻页拖动 */
.ink-layer:not(.active),
.ink-layer:not(.active) .ink-draw,
.ink-layer:not(.active) .text-box,
.ink-layer:not(.active) .media-box {
  pointer-events: none !important;
}

.ink-layer.active {
  z-index: 6;
}

.ink-draw {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  display: block;
  touch-action: none;
}

.tool-pen .ink-draw {
  cursor: crosshair;
}

.tool-eraser .ink-draw {
  cursor: cell;
}

.tool-text .ink-draw {
  cursor: text;
}

.tool-media .ink-draw {
  cursor: default;
}

.text-box {
  position: absolute;
  z-index: 2;
  min-width: 48px;
  pointer-events: auto;
  background: transparent;
  &.selected {
    outline: 1px dashed rgba(62, 52, 38, 0.28);
    outline-offset: 2px;
  }
}

.text-handle {
  height: 10px;
  margin-bottom: 2px;
  border-radius: 2px;
  background: rgba(62, 52, 38, 0.12);
  cursor: grab;
  &:active { cursor: grabbing; }
}

.text-body {
  outline: none;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.35;
  font-family: 'Zen Kaku Gothic New', 'Noto Sans SC', 'Microsoft YaHei', sans-serif;
  min-height: 1.2em;
  padding: 0 2px;
  caret-color: #2b2a33;
}

.media-box {
  position: absolute;
  z-index: 3;
  box-sizing: border-box;
  padding: 0;
  background: #fdf8ec;
  border-radius: 4px;
  box-shadow: 1px 3px 10px rgba(62, 50, 32, 0.22);
  overflow: hidden;
  pointer-events: auto;
  user-select: none;
  &.editable {
    cursor: grab;
    &:active { cursor: grabbing; }
  }
  &.selected {
    outline: 1.5px dashed rgba(62, 52, 38, 0.42);
    outline-offset: 2px;
  }
  /* 非贴图工具：贴图不抢画笔/橡皮/文字操作 */
  &:not(.editable) {
    pointer-events: none;
  }
}

.media-el {
  position: absolute;
  left: 4%;
  top: 4%;
  width: 92%;
  height: 92%;
  display: block;
  object-fit: cover;
  object-position: center;
  border-radius: 2px;
  background: rgba(210, 198, 178, 0.35);
  pointer-events: auto;
}

.media-box:not(.editable) .media-el {
  pointer-events: none;
}

.media-handle {
  position: absolute;
  left: 8%;
  right: 8%;
  top: 3px;
  height: 10px;
  border-radius: 2px;
  background: rgba(62, 52, 38, 0.14);
  cursor: grab;
  z-index: 2;
  &:active { cursor: grabbing; }
}

.media-resize {
  position: absolute;
  right: 2px;
  bottom: 2px;
  width: 14px;
  height: 14px;
  border-radius: 2px;
  background:
    linear-gradient(135deg, transparent 45%, rgba(62, 52, 38, 0.55) 46%, rgba(62, 52, 38, 0.55) 54%, transparent 55%),
    linear-gradient(135deg, transparent 62%, rgba(62, 52, 38, 0.4) 63%, rgba(62, 52, 38, 0.4) 70%, transparent 71%);
  cursor: nwse-resize;
  z-index: 2;
}
</style>
