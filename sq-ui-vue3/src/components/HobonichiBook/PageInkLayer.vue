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

    <!-- 贴图与美纹纸按图层顺序统一叠放，后加的盖住先加的（含胶条） -->
    <template v-for="(item, idx) in overlayStack" :key="item.id">
      <div
        v-if="item.type === 'media'"
        v-show="showMediaOverlay"
        class="media-box"
        :class="{
          selected: selectedId === item.id,
          editable: tool === 'media' && !isPinned(item),
          inspectable: tool === 'view' && !isPinned(item),
          erasable: tool === 'eraser',
          pinned: isPinned(item),
          lifted: isInspectHidden(item),
          video: item.kind === 'video'
        }"
        :style="mediaStyle(item, idx)"
        :title="isPinned(item) ? '已用美纹纸固定，不可移动或拿起' : undefined"
        @pointerdown.stop="onMediaPointerDown($event, item)"
        @dblclick.prevent.stop="onMediaDblClick($event, item)"
      >
        <video
          v-if="item.kind === 'video'"
          class="media-el"
          :src="mediaSrc(item)"
          :poster="mediaPoster(item)"
          controls
          playsinline
          preload="metadata"
          controlslist="nofullscreen noremoteplayback"
          disablepictureinpicture
          @pointerdown.stop="onVideoPointerDown($event, item)"
          @dblclick.prevent.stop="onMediaDblClick($event, item)"
        />
        <template v-else>
          <img
            v-if="mediaThumbSrc(item) && !isMediaReady(item)"
            class="media-el media-el--thumb"
            :src="mediaThumbSrc(item)"
            alt=""
            draggable="false"
            decoding="async"
          />
          <img
            class="media-el media-el--full"
            :class="{ ready: isMediaReady(item) }"
            :src="mediaSrc(item)"
            :alt="item.fileName || ''"
            draggable="false"
            decoding="async"
            @load="onMediaFullLoad($event, item)"
            @dblclick.prevent.stop="onMediaDblClick($event, item)"
          />
        </template>
        <template v-if="tool === 'media' && !isPinned(item)">
          <div class="media-handle" title="拖动" @pointerdown.stop="onMediaDragStart($event, item)" />
          <div class="media-resize" title="缩放" @pointerdown.stop="onMediaResizeStart($event, item)" />
        </template>
      </div>

      <div
        v-else-if="item.type === 'tape'"
        v-show="showMediaOverlay"
        class="tape-layer"
        :class="{
          selected: selectedId === item.id,
          editable: tool === 'tape',
          interactive: tool === 'tape' || tool === 'eraser'
        }"
        :style="tapeStyle(item, idx)"
        @pointerdown.stop="onTapePointerDown($event, item)"
      >
        <div class="masking-tape-body" :style="tapeBodyStyle(item)">
          <span class="tape-fiber" />
        </div>
        <template v-if="tool === 'tape'">
          <div class="tape-move" title="拖动位置" @pointerdown.stop="onTapeDragStart($event, item)" />
          <div class="tape-len a" title="调整长度" @pointerdown.stop="onTapeLenStart($event, item, 'a')" />
          <div class="tape-len b" title="调整长度" @pointerdown.stop="onTapeLenStart($event, item, 'b')" />
        </template>
      </div>
    </template>

    <div
      v-if="draftingTape"
      class="tape-layer drafting"
      :style="tapeStyle(draftingTape, overlayStack.length + 2)"
    >
      <div class="masking-tape-body" :style="tapeBodyStyle(draftingTape)">
        <span class="tape-fiber" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import {
  buildOverlayStack,
  clampMediaRect,
  hitTestMedia,
  hitTestStroke,
  hitTestTape,
  hitTestTextbox,
  isLayerPinned,
  mediaHeightForWidth,
  mergeSpreadLayers,
  newId,
  paintStrokeOnCtx,
  resolveMediaUrl,
  simplifyStroke,
  tapePinsLayer
} from './pageContent.js'
import {
  createTapeClip,
  defaultTapeThick,
  resolveTapeClip,
  tapeFromDrag
} from './maskingTape.js'

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
  interactive: { type: Boolean, default: true },
  /** 查看模式拿起中：隐藏页上对应贴图，留下「被拿起」空位 */
  inspectHideId: { type: String, default: null },
  /** 翻页中只保留静止半页的 HTML 层：'left' | 'right' | null */
  clipSide: { type: String, default: null }
})

const emit = defineEmits(['update-content', 'request-import', 'inspect-media'])

const drawRef = ref(null)
const selectedId = ref(null)
const drafting = ref(null)
const draftingTape = ref(null)
const dragging = ref(null)
const resizing = ref(null)
/** 橡皮滑动擦除中 */
const erasing = ref(null)
/** 已加载清晰大图的贴图 id（blur-up） */
const mediaReadyIds = ref(new Set())

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

const wrapStyle = computed(() => {
  const style = {
    left: `${props.rect.x || 0}px`,
    top: `${props.rect.y || 0}px`,
    width: `${Math.max(0, props.rect.w || 0)}px`,
    height: `${Math.max(0, props.rect.h || 0)}px`,
    pointerEvents: enabled.value ? 'auto' : 'none',
    zIndex: enabled.value ? 6 : 0
  }
  // 翻页时只露出静止半页；略越过中线 2px，避免订口刀切竖缝
  if (props.clipSide === 'left') style.clipPath = 'inset(0 calc(50% - 2px) 0 0)'
  else if (props.clipSide === 'right') style.clipPath = 'inset(0 0 0 calc(50% - 2px))'
  return style
})

const viewLayers = computed(() => {
  if (isSpread.value) {
    return mergeSpreadLayers(props.leftContent, props.rightContent)
  }
  const c = hostContent.value
  return (c && c.layers) || []
})

const textboxes = computed(() => viewLayers.value.filter((l) => l && l.type === 'textbox'))
/** 贴图保持相对顺序；胶条插到所压贴图上方，保证能贴住 */
const overlayStack = computed(() => buildOverlayStack(viewLayers.value))
/**
 * 翻页工具：不叠 HTML，只看全量烘焙（层级与翻页中一致）。
 * 贴图/胶带/查看/橡皮：用 HTML 方便操作；拖页动画中一律隐藏。
 */
const showMediaOverlay = computed(() => {
  if (!props.previewStrokes) return false
  const t = props.tool
  return t === 'media' || t === 'tape' || t === 'view' || t === 'eraser'
})

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

function stackZ(idx) {
  return 10 + (idx | 0)
}

function mediaStyle(m, idx = 0) {
  const rot = m.rot ? `rotate(${m.rot}rad)` : null
  return {
    left: `${(m.x || 0) * 100}%`,
    top: `${(m.y || 0) * 100}%`,
    width: `${(m.w || 0.32) * 100}%`,
    height: `${(m.h || 0.28) * 100}%`,
    transform: rot || undefined,
    zIndex: stackZ(idx)
  }
}

function tapeStyle(t, idx = 0) {
  const rot = t.rot || 0
  return {
    left: `${((t.x || 0) + (t.w || 0.12) / 2) * 100}%`,
    top: `${((t.y || 0) + (t.h || 0.058) / 2) * 100}%`,
    width: `${(t.w || 0.12) * 100}%`,
    height: `${(t.h || 0.058) * 100}%`,
    transform: `translate(-50%, -50%) rotate(${rot}rad)`,
    zIndex: stackZ(idx)
  }
}

function tapeBodyStyle(t) {
  const clip = resolveTapeClip(t)
  return clip ? { clipPath: `polygon(${clip})` } : undefined
}

function isInspectHidden(m) {
  if (!props.inspectHideId) return false
  const id = m._pageId || m.id
  return id === props.inspectHideId
}

function isPinned(layer) {
  // 只有胶条压在自身上方才固定；盖在上面且无胶条的仍可拿起/移动
  return isLayerPinned(layer, overlayStack.value)
}

/**
 * 胶条插到「被压住的贴图」紧上方。
 * pinProbe：用于命中检测的坐标（对开视图时可用 spread 坐标探针对 page 存盘胶条）
 */
function placeTapeInLayers(layers, payload, pinProbe = payload) {
  const without = layers.filter((l) => {
    if (!l) return false
    if (l.id === payload.id) return false
    if (typeof l.id === 'string' && (l.id === `${payload.id}_L` || l.id === `${payload.id}_R`)) {
      return false
    }
    return true
  })

  const pinnedIds = new Set()
  for (const l of viewLayers.value) {
    if (!l || (l._pageId || l.id) === payload.id) continue
    if ((l.type === 'media' || l.type === 'textbox') && tapePinsLayer(pinProbe, l)) {
      pinnedIds.add(l._pageId || l.id)
    }
  }

  let insertAt = without.length
  let best = -1
  for (let i = 0; i < without.length; i++) {
    if (pinnedIds.has(without[i].id)) best = i
  }
  if (best >= 0) insertAt = best + 1
  without.splice(insertAt, 0, payload)
  return without
}

function upsertTapeLayer(layer) {
  const payload = {
    id: layer.id,
    type: 'tape',
    space: isSpread.value ? 'spread' : (layer.space || 'page'),
    x: layer.x,
    y: layer.y,
    w: layer.w,
    h: layer.h,
    rot: layer.rot || 0,
    clip: layer.clip || createTapeClip(layer.id)
  }

  if (isSpread.value) {
    // 优先写到「被压住的最上层贴图」所在页，避免胶条挂在左页、右页图却盖在胶条上
    let hostSide = null
    for (const l of overlayStack.value) {
      if (l?.type === 'media' && tapePinsLayer(payload, l)) {
        hostSide = l._side === 'right' ? 'right' : l._side === 'left' ? 'left' : hostSide
      }
    }
    if (!hostSide) {
      const cx = (payload.x || 0) + (payload.w || 0) / 2
      hostSide = cx >= 0.5 ? 'right' : 'left'
    }

    const crosses = mediaCrossesSpine(payload.x || 0, payload.w || 0)
    if (!crosses && (hostSide === 'left' || hostSide === 'right')) {
      const pageNo = hostSide === 'left' ? props.leftPageNo : props.rightPageNo
      const src = hostSide === 'left' ? props.leftContent : props.rightContent
      const mapped =
        hostSide === 'left'
          ? {
              x: Math.min(1, Math.max(0, (payload.x || 0) * 2)),
              w: Math.min(0.95, Math.max(0.06, (payload.w || 0.12) * 2)),
              y: payload.y,
              h: payload.h
            }
          : {
              x: Math.min(1, Math.max(0, ((payload.x || 0) - 0.5) * 2)),
              w: Math.min(0.95, Math.max(0.06, (payload.w || 0.12) * 2)),
              y: payload.y,
              h: payload.h
            }
      const pageTape = {
        ...payload,
        space: 'page',
        x: mapped.x,
        y: mapped.y,
        w: mapped.w,
        h: mapped.h
      }
      // pinProbe 用对开坐标，才能和 viewLayers 里的贴图对齐
      if (pageNo) {
        emitPage(pageNo, placeTapeInLayers(clonePageLayers(src), pageTape, payload))
      }
      // 清掉另一侧 / 左页 spread 残留
      const otherNo = hostSide === 'left' ? props.rightPageNo : props.leftPageNo
      const otherSrc = hostSide === 'left' ? props.rightContent : props.leftContent
      if (otherNo && (otherSrc?.layers || []).some((l) => l && l.id === payload.id)) {
        emitPage(
          otherNo,
          clonePageLayers(otherSrc).filter((l) => l.id !== payload.id)
        )
      }
      if (hostSide === 'right' && props.leftPageNo) {
        const leftHas = (props.leftContent?.layers || []).some(
          (l) => l && l.id === payload.id && l.space === 'spread'
        )
        if (leftHas) {
          emitPage(
            props.leftPageNo,
            clonePageLayers(props.leftContent).filter((l) => l.id !== payload.id)
          )
        }
      }
      return
    }

    const leftNo = props.leftPageNo
    const rightNo = props.rightPageNo
    const placed = placeTapeInLayers(clonePageLayers(props.leftContent), {
      ...payload,
      space: 'spread'
    })
    if (leftNo) emitPage(leftNo, placed)

    const rightSrc = (props.rightContent && props.rightContent.layers) || []
    const needClean = rightSrc.some(
      (l) =>
        l &&
        (l.id === payload.id ||
          l.id === `${payload.id}_L` ||
          l.id === `${payload.id}_R`)
    )
    if (needClean && rightNo) {
      emitPage(
        rightNo,
        clonePageLayers(props.rightContent).filter(
          (l) =>
            l.id !== payload.id &&
            l.id !== `${payload.id}_L` &&
            l.id !== `${payload.id}_R`
        )
      )
    }
    return
  }

  emitPage(hostPageNo.value, placeTapeInLayers(clonePageLayers(hostContent.value), payload))
}

function exitFsIfNeeded() {
  const fs = document.fullscreenElement || document.webkitFullscreenElement
  if (!fs) return
  const exit = document.exitFullscreen || document.webkitExitFullscreen
  try {
    exit?.call(document)
  } catch { /* ignore */ }
}

function mediaSrc(m) {
  // 显示用中图/原图，不用网格小缩略图
  return resolveMediaUrl(m.src || m.thumbSrc || '')
}

function mediaThumbSrc(m) {
  if (!m || m.kind === 'video') return ''
  const thumb = m.thumbSrc || ''
  const full = m.src || ''
  if (!thumb || thumb === full) return ''
  return resolveMediaUrl(thumb)
}

function mediaPoster(m) {
  // 仅视频封面可用小图；图片勿走 thumb
  if (m?.kind === 'video') return resolveMediaUrl(m.thumbSrc || '')
  return resolveMediaUrl(m.src || m.thumbSrc || '')
}

function isMediaReady(m) {
  if (!m?.id) return false
  // 无独立缩略图时直接显示大图，避免一直透明
  if (!mediaThumbSrc(m)) return true
  return mediaReadyIds.value.has(m.id)
}

function markMediaReady(id) {
  if (!id || mediaReadyIds.value.has(id)) return
  const next = new Set(mediaReadyIds.value)
  next.add(id)
  mediaReadyIds.value = next
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

/** 原地更新图层；toFront 时才提到最上层（避免盖住已贴的美纹纸） */
function replaceOrAppendLayer(layers, payload, toFront = false) {
  const idx = layers.findIndex((l) => l && l.id === payload.id)
  if (idx < 0) return [...layers, payload]
  if (toFront) {
    const next = layers.filter((l) => l.id !== payload.id)
    next.push(payload)
    return next
  }
  const next = layers.slice()
  next[idx] = payload
  return next
}

function upsertMediaLayer(layer, { toFront = false } = {}) {
  const id = layer.id
  const base = {
    id,
    type: 'media',
    rot: layer.rot || 0,
    kind: layer.kind || 'image',
    src: layer.src || '',
    thumbSrc: layer.thumbSrc || '',
    backSrc: layer.backSrc || '',
    photoId: layer.photoId ?? null,
    fileName: layer.fileName || '',
    aspect: layer.aspect || null
  }

  if (isSpread.value) {
    const box0 = clampMediaRect(
      layer.x || 0,
      layer.y || 0,
      layer.w || 0.32,
      layer.h || 0.28,
      { maxW: 0.55, maxH: 0.7 }
    )
    const x = box0.x
    const y = box0.y
    const w = box0.w
    const h = box0.h

    // 跨订口：必须存 space:spread，翻页时左右页各烘焙半幅，否则右半会被右页盖住
    if (mediaCrossesSpine(x, w)) {
      const spreadBox = clampMediaRect(x, y, Math.min(0.55, Math.max(0.08, w)), h, {
        maxW: 0.55,
        maxH: 0.62
      })
      upsertSpreadLayer(
        {
          ...base,
          space: 'spread',
          ...spreadBox
        },
        { toFront }
      )
      return
    }

    const mapped = spreadMediaToPage(x, y, w, h)
    const pageNo = mapped.side === 'left' ? props.leftPageNo : props.rightPageNo
    const src = mapped.side === 'left' ? props.leftContent : props.rightContent
    const otherNo = mapped.side === 'left' ? props.rightPageNo : props.leftPageNo
    const otherSrc = mapped.side === 'left' ? props.rightContent : props.leftContent
    const pageBox = clampMediaRect(mapped.x, mapped.y, mapped.w, mapped.h)
    const payload = {
      ...base,
      space: 'page',
      ...pageBox
    }
    emitPage(pageNo, replaceOrAppendLayer(clonePageLayers(src), payload, toFront))
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

  const pageBox = clampMediaRect(layer.x, layer.y, layer.w, layer.h)
  const payload = {
    ...base,
    space: 'page',
    ...pageBox
  }
  emitPage(
    hostPageNo.value,
    replaceOrAppendLayer(clonePageLayers(hostContent.value), payload, toFront)
  )
}

function onMediaFullLoad(e, m) {
  markMediaReady(m?.id)
  onMediaImgLoad(e, m)
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
  // 仅画笔/橡皮工具用 HTML 笔迹；翻页工具走纸面烘焙，避免压住贴图
  const strokeOnHtml = props.previewStrokes && (props.tool === 'pen' || props.tool === 'eraser')
  if (strokeOnHtml) {
    for (const layer of viewLayers.value) {
      if (layer && layer.type === 'stroke') {
        paintStrokeOnCtx(g, layer, cv.width, cv.height, 1)
      }
    }
  }
  const d = drafting.value
  if (!d || !d.points || !d.points.length) return
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
function upsertSpreadLayer(layer, { toFront = false } = {}) {
  const hostNo = hostPageNo.value
  const leftNo = props.leftPageNo
  const rightNo = props.rightPageNo
  const payload = { ...layer, space: 'spread' }

  if (isSpread.value) {
    emitPage(leftNo, replaceOrAppendLayer(clonePageLayers(props.leftContent), payload, toFront))

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
    emitPage(
      hostNo,
      replaceOrAppendLayer(
        clonePageLayers(hostContent.value),
        { ...layer, space: layer.space || 'page' },
        toFront
      )
    )
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

  // 美纹纸：在空白处拖出一条（长度/角度随拖动）
  if (props.tool === 'tape') {
    selectedId.value = null
    const id = newId('tp')
    const thick = defaultTapeThick(isSpread.value)
    const geo = tapeFromDrag(p.x, p.y, p.x + 0.001, p.y, thick)
    draftingTape.value = {
      id,
      pointerId: e.pointerId,
      x0: p.x,
      y0: p.y,
      ...geo,
      clip: createTapeClip(id)
    }
    try {
      drawRef.value?.setPointerCapture?.(e.pointerId)
    } catch { /* ignore */ }
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
      (l.type === 'tape' && hitTestTape(l, x, y)) ||
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
  if (draftingTape.value && (e.pointerId == null || e.pointerId === draftingTape.value.pointerId)) {
    const p = localNorm(e)
    const d = draftingTape.value
    const thick = d.h || defaultTapeThick(isSpread.value)
    const geo = tapeFromDrag(d.x0, d.y0, p.x, p.y, thick)
    draftingTape.value = { ...d, ...geo }
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
  if (draftingTape.value) {
    if (e?.pointerId != null && e.pointerId !== draftingTape.value.pointerId) return
    const d = draftingTape.value
    draftingTape.value = null
    if ((d.w || 0) < 0.035) return
    upsertTapeLayer({
      id: d.id,
      x: d.x,
      y: d.y,
      w: d.w,
      h: d.h,
      rot: d.rot,
      clip: d.clip
    })
    selectedId.value = d.id
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
    return
  }
  if (isPinned(box)) {
    e.preventDefault()
  }
}

function emitInspect(m, el) {
  const rect = el?.getBoundingClientRect?.()
  emit('inspect-media', {
    layer: m,
    rect: rect
      ? { left: rect.left, top: rect.top, width: rect.width, height: rect.height }
      : null
  })
}

function onVideoPointerDown(e, m) {
  if (isPinned(m)) {
    e.preventDefault()
    return
  }
  if (props.tool === 'view') {
    emitInspect(m, e.currentTarget?.closest?.('.media-box') || e.currentTarget)
    e.preventDefault()
  }
  // 贴图模式留给原生控件，不开始拖动
}

/** 日记本内双击：禁止视频全屏，改为拿起查看 */
function onMediaDblClick(e, m) {
  e.preventDefault()
  e.stopPropagation()
  exitFsIfNeeded()
  if (props.tool === 'eraser') return
  if (isPinned(m)) return
  const box = e.currentTarget?.closest?.('.media-box') || e.currentTarget
  emitInspect(m, box)
}

function onMediaPointerDown(e, m) {
  if (props.tool === 'eraser') {
    removeViewLayer(m)
    e.preventDefault()
    return
  }
  if (isPinned(m)) {
    e.preventDefault()
    return
  }
  if (props.tool === 'view') {
    emitInspect(m, e.currentTarget)
    e.preventDefault()
    return
  }
  if (props.tool === 'media') {
    selectedId.value = m.id
    onMediaDragStart(e, m)
  }
}

function onTapePointerDown(e, t) {
  if (props.tool === 'eraser') {
    removeViewLayer(t)
    e.preventDefault()
    return
  }
  if (props.tool !== 'tape') return
  selectedId.value = t.id
  onTapeDragStart(e, t)
}

function onTapeDragStart(e, t) {
  if (props.tool !== 'tape') return
  const p = localNorm(e)
  selectedId.value = t.id
  dragging.value = {
    kind: 'tape',
    id: e.pointerId,
    box: { ...t },
    ox: p.x - (t.x || 0),
    oy: p.y - (t.y || 0)
  }
  e.currentTarget?.setPointerCapture?.(e.pointerId)
  window.addEventListener('pointermove', onDragMove)
  window.addEventListener('pointerup', onDragEnd)
  window.addEventListener('pointercancel', onDragEnd)
  e.preventDefault()
}

function onTapeLenStart(e, t, end) {
  if (props.tool !== 'tape') return
  selectedId.value = t.id
  const w = t.w || 0.12
  const h = t.h || 0.058
  const cx = (t.x || 0) + w / 2
  const cy = (t.y || 0) + h / 2
  const rot = t.rot || 0
  const hx = Math.cos(rot) * (w / 2)
  const hy = Math.sin(rot) * (w / 2)
  const ax = cx - hx
  const ay = cy - hy
  const bx = cx + hx
  const by = cy + hy
  resizing.value = {
    kind: 'tape-len',
    id: e.pointerId,
    box: { ...t },
    end,
    ax,
    ay,
    bx,
    by
  }
  e.currentTarget?.setPointerCapture?.(e.pointerId)
  window.addEventListener('pointermove', onResizeMove)
  window.addEventListener('pointerup', onResizeEnd)
  window.addEventListener('pointercancel', onResizeEnd)
  e.preventDefault()
}

function onMediaDragStart(e, m) {
  if (props.tool === 'eraser') return
  if (props.tool !== 'media') return
  if (isPinned(m)) return
  // 未固定的图开始拖动时提到上层；已贴胶条的不会走到这里
  upsertMediaLayer({ ...m }, { toFront: true })
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
  if (isPinned(m)) return
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

  if (d.kind === 'tape-len') {
    const thick = d.box.h || defaultTapeThick(isSpread.value)
    let x0 = d.ax
    let y0 = d.ay
    let x1 = d.bx
    let y1 = d.by
    if (d.end === 'a') {
      x0 = p.x
      y0 = p.y
    } else {
      x1 = p.x
      y1 = p.y
    }
    const geo = tapeFromDrag(x0, y0, x1, y1, thick)
    upsertTapeLayer({
      ...d.box,
      ...geo,
      clip: d.box.clip
    })
    return
  }

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
  const box = clampMediaRect(d.box.x || 0, d.box.y || 0, nw, nh, {
    maxW: isSpread.value ? 0.55 : 0.85,
    maxH: isSpread.value ? 0.7 : 0.85
  })
  upsertMediaLayer({
    ...d.box,
    ...box,
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
  if (isPinned(box)) return
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
  const isTape = d.kind === 'tape' || d.box?.type === 'tape'
  const isMedia = d.kind === 'media' || d.box?.type === 'media'
  const w = d.box.w || (isMedia ? 0.32 : isTape ? 0.12 : 0.28)
  const h = d.box.h || (isMedia ? 0.28 : isTape ? 0.058 : 0.1)
  // 贴图/文字必须整框在页内；胶条允许轻微探出（由容器裁切）
  const maxY = isTape ? 1 - h * 0.35 : 1 - h
  const nx = Math.min(1 - w, Math.max(0, p.x - d.ox))
  const ny = Math.min(maxY, Math.max(0, p.y - d.oy))
  d.box = { ...d.box, x: nx, y: ny }

  const id = d.box._pageId || d.box.id
  if (isTape) {
    upsertTapeLayer({
      ...d.box,
      id,
      x: nx,
      y: ny,
      w,
      h,
      rot: d.box.rot || 0,
      clip: d.box.clip
    })
    return
  }
  if (isMedia) {
    const box = clampMediaRect(nx, ny, w, h, {
      maxW: isSpread.value ? 0.55 : 0.92,
      maxH: isSpread.value ? 0.7 : 0.85
    })
    upsertMediaLayer({ ...d.box, id, ...box })
    return
  }
  if (isSpread.value || d.box.space === 'spread' || d.box._side === 'spread') {
    const tw = w
    const th = d.box.h || 0.1
    const tx = Math.min(1 - tw, Math.max(0, nx))
    const ty = Math.min(1 - th, Math.max(0, ny))
    upsertSpreadLayer({
      id,
      type: 'textbox',
      space: 'spread',
      x: tx,
      y: ty,
      w: tw,
      h: th,
      text: d.box.text || '',
      fontSize: d.box.fontSize || 0.035,
      color: d.box.color || props.color
    })
    return
  }
  const layers = clonePageLayers(hostContent.value)
  const idx = layers.findIndex((l) => l.id === id)
  if (idx < 0) return
  const th = layers[idx].h || 0.1
  const tw = layers[idx].w || w
  layers[idx] = {
    ...layers[idx],
    x: Math.min(1 - tw, Math.max(0, nx)),
    y: Math.min(1 - th, Math.max(0, ny))
  }
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

// 切走文字工具前先 blur，把 DOM 里的字写回 layers；
// 切到画笔/橡皮时必须同步 redrawInk：纸面会 skipStroke，HTML 层晚一帧就会闪没。
watch(
  () => props.tool,
  (tool, prev) => {
    if (prev === 'text' && tool !== 'text') {
      const active = document.activeElement
      if (active && active.classList?.contains('text-body')) {
        active.blur()
      }
    }
    redrawInk()
  }
)

onMounted(() => syncCanvasSize())
onUnmounted(() => {
  erasing.value = null
  drafting.value = null
  draftingTape.value = null
  onDragEnd()
  onResizeEnd()
})
</script>

<style scoped lang="scss">
.ink-layer {
  position: absolute;
  z-index: 0;
  /* 裁在日记纸面内，避免贴图拖出本子 */
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

.tool-view .ink-draw {
  cursor: default;
}

.tool-tape .ink-draw {
  cursor: crosshair;
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
  z-index: 10;
  box-sizing: border-box;
  padding: 0;
  background: #fdf8ec;
  border-radius: 4px;
  box-shadow: 1px 2px 6px rgba(62, 50, 32, 0.18);
  overflow: hidden;
  pointer-events: auto;
  user-select: none;
  transition: opacity 0.28s ease, filter 0.28s ease, box-shadow 0.28s ease;
  &.editable {
    cursor: grab;
    &:active { cursor: grabbing; }
  }
  &.inspectable {
    cursor: zoom-in;
    pointer-events: auto !important;
    &:hover {
      box-shadow: 2px 6px 16px rgba(62, 50, 32, 0.32);
      filter: brightness(1.03);
    }
  }
  &.pinned {
    cursor: default;
    &::after {
      content: '';
      position: absolute;
      inset: 0;
      pointer-events: none;
      box-shadow: inset 0 0 0 1px rgba(120, 90, 40, 0.18);
    }
  }
  &.lifted {
    opacity: 0.18;
    filter: blur(0.6px) grayscale(0.15);
    box-shadow: none;
    pointer-events: none !important;
  }
  &.selected {
    outline: 1.5px dashed rgba(62, 52, 38, 0.42);
    outline-offset: 2px;
  }
  &.erasable {
    pointer-events: auto !important;
    cursor: cell;
  }
  /* 非贴图/查看/橡皮：贴图不抢其它工具操作 */
  &:not(.editable):not(.inspectable):not(.erasable) {
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
  overflow: hidden;
}

.media-el--thumb {
  filter: blur(10px) saturate(0.92);
  transform: scale(1.08);
  transform-origin: center;
  z-index: 0;
  pointer-events: none !important;
}

.media-el--full {
  z-index: 1;
  opacity: 0;
  transition: opacity 0.28s ease;
  &.ready {
    opacity: 1;
  }
}

/* 无缩略图时直接显示大图，避免一直透明 */
.media-box:not(:has(.media-el--thumb)) .media-el--full {
  opacity: 1;
}

.media-box:not(.editable) .media-el {
  pointer-events: none;
}

/* 美纹纸图层 */
.tape-layer {
  position: absolute;
  z-index: 10;
  pointer-events: none;
  transform-origin: center center;
  user-select: none;
  &.interactive {
    pointer-events: auto;
  }
  &.editable {
    cursor: grab;
    &:active { cursor: grabbing; }
  }
  &.selected .masking-tape-body {
    outline: 1px dashed rgba(62, 52, 38, 0.4);
    outline-offset: 2px;
  }
  &.drafting {
    pointer-events: none;
    opacity: 0.92;
  }
}

.masking-tape-body {
  position: absolute;
  inset: 0;
  background:
    repeating-linear-gradient(
      90deg,
      #efe0b8 0px,
      #efe0b8 1px,
      #e6d4a6 1px,
      #e6d4a6 2px,
      #deca98 2px,
      #deca98 3.5px
    ),
    linear-gradient(180deg, #f2e4c2 0%, #e2ce9e 55%, #d4bc88 100%);
  box-shadow:
    0 1px 2px rgba(60, 42, 18, 0.28),
    inset 0 1px 0 rgba(255, 248, 220, 0.45),
    inset 0 -2px 3px rgba(120, 90, 40, 0.12);
}

.tape-fiber {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background:
    repeating-linear-gradient(
      0deg,
      transparent 0 3px,
      rgba(140, 110, 60, 0.07) 3px 4px
    );
}

.tape-move {
  position: absolute;
  inset: 18% 12%;
  z-index: 2;
  cursor: grab;
  &:active { cursor: grabbing; }
}

.tape-len {
  position: absolute;
  top: 50%;
  width: 12px;
  height: 12px;
  margin-top: -6px;
  border-radius: 50%;
  background: rgba(62, 52, 38, 0.72);
  border: 1.5px solid rgba(255, 248, 230, 0.9);
  z-index: 3;
  cursor: ew-resize;
  &.a { left: -6px; }
  &.b { right: -6px; }
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
