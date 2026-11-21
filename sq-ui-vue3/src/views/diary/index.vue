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
          :title="t.label"
          @click="setTool(t.id)"
        >
          {{ t.label }}
        </button>

        <span class="tool-sep" aria-hidden="true" />

        <label class="color-wrap" title="颜色">
          <input v-model="inkColor" type="color" class="color-input" />
        </label>

        <label class="width-wrap" title="笔粗">
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
      />
      <div v-else class="diary-loading">正在打开日记本…</div>
    </div>
  </div>
</template>

<script setup>
import { computed, getCurrentInstance, onMounted, onUnmounted, ref, watch } from 'vue'
import { ArrowLeft } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import HobonichiBook from '@/components/HobonichiBook/index.vue'
import { getMineBook } from '@/api/journal/book'
import { useJournalPages } from './composables/useJournalPages.js'

defineOptions({ name: 'AlbumDiary' })

const { proxy } = getCurrentInstance()

const year = new Date().getFullYear()
const book = ref(null)
const ready = ref(false)
const tool = ref('turn')
const inkColor = ref('#2B2A33')
const penWidthUi = ref(3)

const tools = [
  { id: 'turn', label: '翻页' },
  { id: 'pen', label: '画笔' },
  { id: 'text', label: '文字' },
  { id: 'eraser', label: '橡皮' }
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
  tool.value = id
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
}

.color-input {
  width: 40px;
  height: 40px;
  border: 0;
  padding: 0;
  transform: translate(-6px, -6px);
  cursor: pointer;
  background: transparent;
}

.width-wrap {
  display: inline-flex;
  align-items: center;
  padding: 0 6px;
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
