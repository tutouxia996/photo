import { onUnmounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listJournalPages, saveJournalPage } from '@/api/journal/page'
import { emptyContent, parseContent, serializeContent } from '@/components/HobonichiBook/pageContent.js'

/**
 * 日记页内容缓存 + 范围拉取 + 防抖保存
 */
export function useJournalPages() {
  const bookId = ref(null)
  const pages = ref(new Map()) // pageNo -> content object
  const dirty = ref(new Set())
  const loading = ref(false)
  const timers = new Map()

  function getContent(pageNo) {
    if (!pageNo) return emptyContent()
    return pages.value.get(pageNo) || emptyContent()
  }

  function setContent(pageNo, content, { markDirty = true } = {}) {
    if (!pageNo) return
    const next = new Map(pages.value)
    next.set(pageNo, content && content.layers ? content : emptyContent())
    pages.value = next
    if (markDirty) {
      const d = new Set(dirty.value)
      d.add(pageNo)
      dirty.value = d
      scheduleSave(pageNo)
    }
  }

  function scheduleSave(pageNo) {
    if (!bookId.value || !pageNo) return
    if (timers.has(pageNo)) clearTimeout(timers.get(pageNo))
    timers.set(
      pageNo,
      setTimeout(() => {
        timers.delete(pageNo)
        flushPage(pageNo)
      }, 800)
    )
  }

  async function flushPage(pageNo) {
    if (!bookId.value || !pageNo) return
    if (!dirty.value.has(pageNo)) return
    const content = getContent(pageNo)
    try {
      await saveJournalPage({
        bookId: bookId.value,
        pageNo,
        contentJson: serializeContent(content)
      })
      const d = new Set(dirty.value)
      d.delete(pageNo)
      dirty.value = d
    } catch (e) {
      ElMessage.warning('日记保存失败，请稍后再试')
    }
  }

  async function flushAll() {
    const nos = [...dirty.value]
    for (const n of nos) {
      if (timers.has(n)) {
        clearTimeout(timers.get(n))
        timers.delete(n)
      }
      await flushPage(n)
    }
  }

  async function ensureRange(from, to) {
    if (!bookId.value || from == null || to == null || to < from) return
    loading.value = true
    try {
      const res = await listJournalPages(bookId.value, from, to)
      const list = (res && res.data && res.data.pages) || []
      const next = new Map(pages.value)
      for (const row of list) {
        if (!row || row.pageNo == null) continue
        // 不覆盖本地未保存脏页
        if (dirty.value.has(row.pageNo)) continue
        next.set(row.pageNo, parseContent(row.contentJson))
      }
      pages.value = next
    } catch {
      /* silent */
    } finally {
      loading.value = false
    }
  }

  function bindBook(id) {
    bookId.value = id
  }

  onUnmounted(() => {
    for (const t of timers.values()) clearTimeout(t)
    timers.clear()
    flushAll()
  })

  return {
    bookId,
    pages,
    dirty,
    loading,
    bindBook,
    getContent,
    setContent,
    ensureRange,
    flushPage,
    flushAll
  }
}
