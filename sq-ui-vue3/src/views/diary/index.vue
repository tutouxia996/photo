<template>
  <div class="diary-page">
    <header class="diary-header">
      <button type="button" class="back-btn" title="返回" @click="goBack">
        <el-icon :size="20"><ArrowLeft /></el-icon>
      </button>

      <div class="tool-bar" role="toolbar" aria-label="日记工具">
        <button
          v-for="t in tools"
          :key="t.id"
          type="button"
          class="tool-btn"
          :class="{ active: tool === t.id }"
          :title="t.tip"
          @click="setTool(t.id)"
        >
          {{ t.label }}
        </button>

        <span class="tool-sep" aria-hidden="true" />

        <label class="color-wrap" title="颜色" :class="{ disabled: tool !== 'pen' && tool !== 'text' }">
          <input
            v-model="inkColor"
            type="color"
            class="color-input"
            :disabled="tool !== 'pen' && tool !== 'text'"
          />
        </label>

        <label class="width-wrap" title="笔粗" :class="{ disabled: tool !== 'pen' }">
          <input
            v-model.number="penWidthUi"
            type="range"
            min="1"
            max="8"
            step="1"
            class="width-input"
            :disabled="tool !== 'pen'"
          />
        </label>
      </div>
    </header>

    <div class="diary-body">
      <HobonichiBook
        v-if="ready"
        ref="bookRef"
        :pages="sheetPages"
        :cover-title="book?.title || '相册日记'"
        :cover-sub="book?.coverSub || 'HOBONICHI STYLE'"
        :year="book?.year || year"
        :tool="tool"
        :ink-color="inkColor"
        :pen-width="penWidth"
        :page-contents="pageContents"
        @change="onBookChange"
        @need-pages="onNeedPages"
        @update-content="onUpdateContent"
        @request-import="openMediaPicker"
      />
      <div v-else class="diary-loading">正在打开日记本…</div>
    </div>

    <DiaryMediaPicker v-model="mediaPickerOpen" @confirm="onMediaConfirm" />
  </div>
</template>

<script setup>
import { computed, getCurrentInstance, onMounted, onUnmounted, ref, watch } from 'vue'
import { ArrowLeft } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import HobonichiBook from '@/components/HobonichiBook/index.vue'
import {
  mediaHeightForWidth,
  mediaWidthForPage,
  newId
} from '@/components/HobonichiBook/pageContent.js'
import { getMineBook } from '@/api/journal/book'
import { useJournalPages } from './composables/useJournalPages.js'
import DiaryMediaPicker from './components/DiaryMediaPicker.vue'

defineOptions({ name: 'AlbumDiary' })

const { proxy } = getCurrentInstance()

const year = new Date().getFullYear()
const book = ref(null)
const bookRef = ref(null)
const ready = ref(false)
const tool = ref('turn')
const inkColor = ref('#2B2A33')
const penWidthUi = ref(3)
const mediaPickerOpen = ref(false)

const tools = [
  { id: 'view', label: '查看', tip: '拿起查看：点击相片/纸条/物品 · 双击翻转背面' },
  { id: 'turn', label: '翻页', tip: '仅翻页：拖页角或按 ← →' },
  { id: 'pen', label: '画笔', tip: '仅书写：在纸面上画' },
  { id: 'text', label: '文字', tip: '仅文字：点击放置文字框' },
  { id: 'media', label: '图片', tip: '仅贴图：拖动缩放；再点本按钮或点空白可导入' },
  { id: 'tape', label: '美纹纸', tip: '拖出胶条：压在胶条下的图/卡片会固定；盖在上面且无胶条的仍可拿起' },
  { id: 'eraser', label: '橡皮', tip: '仅擦除：点击删除笔画/文字/贴图/美纹纸' }
]

const {
  pages: pageContents,
  bindBook,
  setContent,
  ensureRange,
  flushAll
} = useJournalPages()

const sheetPages = computed(() => {
  const n = book.value?.pageCount || 1000
  return Array.from({ length: n }, (_, i) => ({ id: i + 1 }))
})

const penWidth = computed(() => 0.0015 + penWidthUi.value * 0.0012)

function setTool(id) {
  if (tool.value !== id) {
    // 先让文字框失焦写回内容，再切工具
    const active = document.activeElement
    if (active && active.classList?.contains('text-body')) {
      active.blur()
    }
    flushAll()
  }
  // 图片工具：已在该模式再点一次 → 打开导入；首次切入只进入贴图调整
  if (id === 'media' && tool.value === 'media') {
    openMediaPicker()
    return
  }
  tool.value = id
}

function openMediaPicker() {
  mediaPickerOpen.value = true
}

function currentSpread() {
  const exposed = bookRef.value?.spreadPages
  if (exposed && typeof exposed === 'object' && 'value' in exposed) return exposed.value
  return exposed || { left: null, right: null }
}

function mediaSize(item) {
  const aspect = item.aspect && item.aspect > 0 ? item.aspect : 4 / 3
  // 一律按单页坐标存，对开显示时再映射，避免 spread 宽高比拉歪
  const w = mediaWidthForPage(false)
  const h = mediaHeightForWidth(w, aspect, false)
  return { w, h, aspect }
}

function onMediaConfirm(items) {
  if (!items?.length) return
  const { left, right } = currentSpread()
  if (!left && !right) {
    ElMessage.warning('请先翻开日记页再导入')
    tool.value = 'turn'
    return
  }

  // 贴图挂在单页：交替落到左右页，不跨订口
  const targets = []
  items.forEach((item, i) => {
    const preferLeft = left && (!right || i % 2 === 0)
    const pageNo = preferLeft ? left : right || left
    targets.push({ item, pageNo, i })
  })

  const byPage = new Map()
  for (const t of targets) {
    if (!byPage.has(t.pageNo)) {
      const cur = pageContents.value.get?.(t.pageNo) || { v: 1, layers: [] }
      byPage.set(t.pageNo, [...(cur.layers || [])])
    }
  }

  targets.forEach(({ item, pageNo, i }, idx) => {
    const size = mediaSize(item)
    const col = Math.floor(idx / 2) % 2
    const row = Math.floor(idx / 4)
    const layers = byPage.get(pageNo)
    layers.push({
      id: newId('m'),
      type: 'media',
      space: 'page',
      x: Math.min(0.55, 0.1 + col * 0.45),
      y: Math.min(0.58, 0.14 + row * 0.32),
      w: size.w,
      h: size.h,
      aspect: size.aspect,
      rot: ((i % 5) - 2) * 0.012,
      kind: item.kind || 'image',
      src: item.src || '',
      thumbSrc: item.thumbSrc || item.src || '',
      photoId: item.photoId ?? null,
      fileName: item.fileName || ''
    })
  })

  for (const [pageNo, layers] of byPage) {
    setContent(pageNo, { v: 1, layers }, { markDirty: true })
  }
  tool.value = 'media'
  ElMessage.success(`已导入 ${items.length} 项，可拖动调整位置`)
}

function goBack() {
  flushAll().finally(() => {
    proxy.$tab.navigatePage({ path: '/photos/index' })
  })
}

function onBookChange() {
  flushAll()
}

function onNeedPages({ from, to }) {
  ensureRange(from, to)
}

function onUpdateContent({ pageNo, content }) {
  setContent(pageNo, content, { markDirty: true })
}

function paintMain(bg) {
  const el = document.querySelector('.app-main')
  if (el) el.style.background = bg
}

async function bootstrap() {
  try {
    const res = await getMineBook()
    book.value = res.data
    bindBook(book.value.bookId)
    ready.value = true
  } catch (e) {
    ElMessage.error('无法打开日记本，请确认已登录且后端已执行 journal_init.sql')
    ready.value = false
  }
}

watch(tool, (v, old) => {
  if (old && old !== v) flushAll()
})

onMounted(() => {
  paintMain('#ebe6dc')
  bootstrap()
})

onUnmounted(() => {
  flushAll()
  paintMain('')
})
</script>

<style scoped lang="scss">
.diary-page {
  display: flex;
  flex-direction: column;
  margin: -20px;
  width: calc(100% + 40px);
  height: calc(100vh - 84px + 40px);
  min-height: 560px;
  background: #ebe6dc;
  overflow: hidden;
}

.diary-header {
  flex: none;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 22px 16px 4px;
  z-index: 2;
}

.back-btn {
  width: 34px;
  height: 34px;
  border: 0;
  border-radius: 10px;
  background: rgba(255, 252, 245, 0.65);
  color: #3e3426;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  flex: none;
  &:hover {
    background: rgba(255, 252, 245, 0.95);
  }
}

.tool-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  padding: 4px;
  border-radius: 12px;
  background: rgba(255, 252, 245, 0.55);
  border: 1px solid rgba(62, 52, 38, 0.08);
}

.tool-btn {
  border: 0;
  border-radius: 8px;
  padding: 6px 12px;
  background: transparent;
  color: rgba(62, 52, 38, 0.72);
  font-size: 13px;
  letter-spacing: 0.04em;
  font-family: 'Zen Kaku Gothic New', 'Noto Sans SC', sans-serif;
  cursor: pointer;
  &:hover {
    background: rgba(255, 252, 245, 0.9);
  }
  &.active {
    background: rgba(43, 42, 51, 0.9);
    color: #f7f2e8;
  }
}

.tool-sep {
  width: 1px;
  height: 18px;
  margin: 0 4px;
  background: rgba(62, 52, 38, 0.14);
}

.color-wrap {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid rgba(62, 52, 38, 0.16);
  display: inline-flex;
  cursor: pointer;
  &.disabled {
    opacity: 0.35;
    cursor: default;
    pointer-events: none;
  }
}

.color-input {
  width: 40px;
  height: 40px;
  border: 0;
  padding: 0;
  transform: translate(-6px, -6px);
  cursor: pointer;
  background: transparent;
  &:disabled {
    cursor: default;
  }
}

.width-wrap {
  display: inline-flex;
  align-items: center;
  padding: 0 6px;
  &.disabled {
    opacity: 0.35;
    pointer-events: none;
  }
}

.width-input {
  width: 72px;
  accent-color: #2b2a33;
}

.diary-body {
  flex: 1;
  min-height: 0;
  position: relative;
}

.diary-loading {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: rgba(62, 52, 38, 0.5);
  font-family: 'Jost', 'Zen Kaku Gothic New', sans-serif;
  letter-spacing: 0.08em;
}

@media (max-width: 768px) {
  .diary-page {
    margin: -15px;
    width: calc(100% + 30px);
    height: calc(100vh - 50px + 30px);
  }

  .diary-header {
    padding: 18px 14px 4px;
  }
}
</style>
