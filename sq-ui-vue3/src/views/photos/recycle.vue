<template>
  <div class="recycle-page" v-loading="loading" @click="closeCtxMenu" @contextmenu="onPageContextMenu">
    <header class="recycle-header">
      <div class="header-left">
        <button type="button" class="back-btn" @click="goBack">
          <el-icon><ArrowLeft /></el-icon>
          <span>返回相册</span>
        </button>
        <h1 class="recycle-title">回收站</h1>
      </div>
      <div class="header-right">
        <el-button round :disabled="!selectedIds.length" @click="restoreSelected">
          文件恢复
        </el-button>
        <el-button round type="danger" plain :disabled="!hasAnyTrash" @click="emptyRecycle">
          清空回收站
        </el-button>
      </div>
    </header>

    <div class="recycle-toolbar">
      <div class="toolbar-left">
        <button
          type="button"
          class="select-btn"
          :class="selectBtnClass"
          :title="selectBtnTitle"
          @click="onSelectHeaderClick"
        >
          <el-icon v-if="isAllSelected" :size="16"><Select /></el-icon>
          <el-icon v-else-if="selectedIds.length" :size="16"><Minus /></el-icon>
          <el-icon v-else :size="18"><CircleCheck /></el-icon>
        </button>
        <span v-if="selectedIds.length" class="selected-count">已选 {{ selectedIds.length }} 项</span>
        <span v-else class="item-count">共 {{ currentList.length }} 项</span>
      </div>
    </div>

    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <el-tab-pane label="相册" name="album">
        <div v-if="!albumList.length" class="empty">回收站暂无相册</div>
        <div v-else class="item-list">
          <div
            v-for="item in albumList"
            :key="item.albumId"
            class="item-row"
            :class="{ selected: isSelected(item.albumId) }"
            @click="onItemClick(item.albumId, $event)"
            @contextmenu.prevent.stop="onItemContextMenu(item.albumId, $event)"
          >
            <div class="check-mark" aria-hidden="true">
              <el-icon :size="14"><Select /></el-icon>
            </div>
            <div class="item-main">
              <div class="item-name">{{ item.albumName }}</div>
              <div class="item-sub">照片 {{ item.photoCount ?? 0 }} · {{ formatTime(item.updateTime || item.createTime) }}</div>
            </div>
          </div>
        </div>
      </el-tab-pane>
      <el-tab-pane label="照片/视频" name="photo">
        <div v-if="!photoList.length" class="empty">回收站暂无照片/视频</div>
        <div v-else class="photo-grid" :class="{ 'is-selecting': selectedIds.length > 0 }">
          <div
            v-for="item in photoList"
            :key="item.photoId"
            class="photo-card"
            :class="{ selected: isSelected(item.photoId) }"
            @click="onItemClick(item.photoId, $event)"
            @contextmenu.prevent.stop="onItemContextMenu(item.photoId, $event)"
          >
            <div class="thumb">
              <img v-if="thumbSrc(item)" :src="thumbSrc(item)" :alt="item.fileName" loading="lazy" />
              <div v-else class="thumb-empty">
                <el-icon :size="28"><PictureFilled /></el-icon>
              </div>
              <div class="check-mark" aria-hidden="true">
                <el-icon :size="14"><Select /></el-icon>
              </div>
              <span v-if="item.fileType === 2" class="video-badge">视频</span>
            </div>
            <div class="photo-meta">
              <div class="item-name" :title="item.fileName">{{ item.fileName }}</div>
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <teleport to="body">
      <div
        v-if="ctxMenu.visible"
        class="recycle-ctx-menu"
        :style="{ left: ctxMenu.x + 'px', top: ctxMenu.y + 'px' }"
        @click.stop
        @contextmenu.prevent
      >
        <button type="button" class="ctx-item" @click="onCtxRestore">恢复</button>
        <div class="ctx-divider"></div>
        <button type="button" class="ctx-item danger" @click="onCtxPurge">彻底删除</button>
      </div>
    </teleport>
  </div>
</template>

<script setup name="PhotosRecycle">
import { isExternal } from '@/utils/validate'
import { getToken } from '@/utils/auth'
import { listAlbum, restoreAlbum, purgeAlbum } from '@/api/photos/album'
import { listPhoto, restorePhoto, purgePhoto } from '@/api/photos/photo'

const { proxy } = getCurrentInstance()

const loading = ref(false)
const activeTab = ref('album')
const albumList = ref([])
const photoList = ref([])
const selectedIds = ref([])
const lastAnchorId = ref(null)
const albumTotal = ref(0)
const photoTotal = ref(0)

const ctxMenu = reactive({
  visible: false,
  x: 0,
  y: 0
})

const currentList = computed(() => (activeTab.value === 'album' ? albumList.value : photoList.value))
const currentIds = computed(() =>
  currentList.value.map(item => (activeTab.value === 'album' ? item.albumId : item.photoId))
)
const hasAnyTrash = computed(() => albumTotal.value > 0 || photoTotal.value > 0 || albumList.value.length > 0 || photoList.value.length > 0)
const isAllSelected = computed(
  () => currentIds.value.length > 0 && selectedIds.value.length >= currentIds.value.length
)
const selectBtnClass = computed(() => ({
  active: selectedIds.value.length > 0,
  partial: selectedIds.value.length > 0 && !isAllSelected.value
}))
const selectBtnTitle = computed(() => {
  if (selectedIds.value.length) return '取消全选'
  return '全选'
})

function resolveUrl(url) {
  if (!url) return ''
  if (isExternal(url)) return url
  if (url.startsWith('/album/photo/media/')) {
    const token = getToken()
    return import.meta.env.VITE_APP_BASE_API + url + (token ? `?Authorization=Bearer ${token}` : '')
  }
  return import.meta.env.VITE_APP_BASE_API + url
}

/** 统一走媒体接口，避免 /album/files/** 无静态映射导致预览失败 */
function thumbSrc(item) {
  if (!item?.photoId) return ''
  return resolveUrl('/album/photo/media/' + item.photoId)
}

function formatTime(time) {
  if (!time) return ''
  return String(time).replace('T', ' ').slice(0, 19)
}

function goBack() {
  proxy.$tab.navigatePage({ path: '/photos/index' })
}

function isSelected(id) {
  return selectedIds.value.includes(id)
}

function clearSelection() {
  selectedIds.value = []
  lastAnchorId.value = null
}

function closeCtxMenu() {
  ctxMenu.visible = false
}

function selectAllCurrent() {
  selectedIds.value = currentIds.value.slice()
  lastAnchorId.value = selectedIds.value[0] ?? null
}

function onSelectHeaderClick() {
  if (selectedIds.value.length) {
    clearSelection()
    return
  }
  if (!currentIds.value.length) {
    proxy.$modal.msg('暂无可选项')
    return
  }
  selectAllCurrent()
}

function applySelection(id, event = {}) {
  const ids = currentIds.value
  const idx = ids.indexOf(id)
  if (idx < 0) return

  const ctrl = !!(event.ctrlKey || event.metaKey)
  const shift = !!event.shiftKey

  if (shift && lastAnchorId.value != null) {
    const from = ids.indexOf(lastAnchorId.value)
    if (from >= 0) {
      const start = Math.min(from, idx)
      const end = Math.max(from, idx)
      const range = ids.slice(start, end + 1)
      if (ctrl) {
        selectedIds.value = [...new Set(selectedIds.value.concat(range))]
      } else {
        selectedIds.value = range.slice()
      }
      return
    }
  }

  if (ctrl) {
    const i = selectedIds.value.indexOf(id)
    if (i >= 0) selectedIds.value.splice(i, 1)
    else selectedIds.value.push(id)
    lastAnchorId.value = id
    return
  }

  if (selectedIds.value.length === 1 && selectedIds.value[0] === id) {
    clearSelection()
  } else {
    selectedIds.value = [id]
    lastAnchorId.value = id
  }
}

function onItemClick(id, event) {
  closeCtxMenu()
  applySelection(id, event)
}

function positionCtxMenu(e, menuW = 160, menuH = 96) {
  const pad = 8
  let x = e.clientX
  let y = e.clientY
  if (x + menuW + pad > window.innerWidth) x = window.innerWidth - menuW - pad
  if (y + menuH + pad > window.innerHeight) y = window.innerHeight - menuH - pad
  ctxMenu.x = Math.max(pad, x)
  ctxMenu.y = Math.max(pad, y)
  ctxMenu.visible = true
}

function onPageContextMenu(e) {
  e.preventDefault()
  closeCtxMenu()
}

function onItemContextMenu(id, event) {
  if (!isSelected(id)) {
    selectedIds.value = [id]
    lastAnchorId.value = id
  }
  positionCtxMenu(event)
}

function loadAlbums() {
  return listAlbum({ pageNum: 1, pageSize: 200, deleted: 2 }).then(res => {
    albumList.value = res.rows || []
    albumTotal.value = res.total ?? albumList.value.length
  })
}

function loadPhotos() {
  return listPhoto({ pageNum: 1, pageSize: 200, deleted: 2 }).then(res => {
    photoList.value = res.rows || []
    photoTotal.value = res.total ?? photoList.value.length
  })
}

function refresh(keepSelection = false) {
  loading.value = true
  if (!keepSelection) clearSelection()
  closeCtxMenu()
  // 两侧都拉，便于「清空回收站」判断与跨 Tab 状态
  Promise.all([loadAlbums(), loadPhotos()])
    .catch(() => {})
    .finally(() => {
      loading.value = false
    })
}

function onTabChange() {
  clearSelection()
  closeCtxMenu()
}

function restoreByIds(ids) {
  if (!ids.length) return Promise.resolve()
  const joined = ids.join(',')
  return activeTab.value === 'album' ? restoreAlbum(joined) : restorePhoto(joined)
}

function purgeByIds(ids) {
  if (!ids.length) return Promise.resolve()
  const joined = ids.join(',')
  return activeTab.value === 'album' ? purgeAlbum(joined) : purgePhoto(joined)
}

function restoreSelected() {
  const ids = selectedIds.value.slice()
  if (!ids.length) {
    proxy.$modal.msgWarning('请先选择要恢复的项')
    return
  }
  const label = activeTab.value === 'album' ? '相册' : '文件'
  proxy.$modal.confirm(`确认恢复选中的 ${ids.length} 个${label}吗？`)
    .then(() => restoreByIds(ids))
    .then(() => {
      proxy.$modal.msgSuccess('已恢复')
      refresh()
    })
    .catch(() => {})
}

function purgeSelected() {
  const ids = selectedIds.value.slice()
  if (!ids.length) {
    proxy.$modal.msgWarning('请先选择要删除的项')
    return
  }
  const label = activeTab.value === 'album' ? '相册' : '文件'
  proxy.$modal.confirm(`确认彻底删除选中的 ${ids.length} 个${label}吗？此操作不可恢复。`)
    .then(() => purgeByIds(ids))
    .then(() => {
      proxy.$modal.msgSuccess('已彻底删除')
      refresh()
    })
    .catch(() => {})
}

function onCtxRestore() {
  closeCtxMenu()
  restoreSelected()
}

function onCtxPurge() {
  closeCtxMenu()
  purgeSelected()
}

function emptyRecycle() {
  const albumIds = albumList.value.map(a => a.albumId)
  const photoIds = photoList.value.map(p => p.photoId)
  if (!albumIds.length && !photoIds.length) {
    proxy.$modal.msg('回收站已为空')
    return
  }
  proxy.$modal.confirm('确认清空回收站吗？其中的相册与文件将被彻底删除，此操作不可恢复。')
    .then(async () => {
      loading.value = true
      try {
        if (albumIds.length) await purgeAlbum(albumIds.join(','))
        if (photoIds.length) await purgePhoto(photoIds.join(','))
        proxy.$modal.msgSuccess('回收站已清空')
        refresh()
      } catch (e) {
        loading.value = false
      }
    })
    .catch(() => {})
}

refresh()
</script>

<style scoped lang="scss">
.recycle-page {
  --select-blue: #4c8dff;
  min-height: calc(100vh - 84px);
  margin: -20px;
  padding: 28px 32px 48px;
  background: #fff;
  color: #1a1a1a;
}

.recycle-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}

.header-left {
  min-width: 0;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
  margin-top: 28px;
}

.back-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: none;
  background: transparent;
  color: #666;
  font-size: 13px;
  cursor: pointer;
  padding: 0;
  margin-bottom: 10px;

  &:hover {
    color: #111;
  }
}

.recycle-title {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
  line-height: 1.2;
}

.recycle-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  gap: 12px;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.select-btn {
  width: 28px;
  height: 28px;
  border: 1.5px solid #d0d0d0;
  border-radius: 50%;
  background: #fff;
  color: #8a8a8a;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 0.2s ease, border-color 0.2s ease, color 0.2s ease, transform 0.15s ease;

  &:hover {
    border-color: var(--select-blue);
    color: var(--select-blue);
  }

  &.active {
    background: var(--select-blue);
    border-color: var(--select-blue);
    color: #fff;
  }

  &:active {
    transform: scale(0.92);
  }
}

.item-count,
.selected-count {
  font-size: 13px;
  color: #999;
}

.selected-count {
  color: #333;
  font-weight: 500;
}

.empty {
  padding: 48px 0;
  text-align: center;
  color: #999;
  font-size: 14px;
}

.item-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.item-row {
  position: relative;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 14px 16px 14px 44px;
  border: 1px solid #eee;
  border-radius: 10px;
  cursor: pointer;
  user-select: none;
  transition: border-color 0.15s ease, background 0.15s ease, box-shadow 0.15s ease;

  &:hover {
    border-color: #d8d8d8;
    background: #fafafa;
  }

  &.selected {
    border-color: var(--select-blue);
    background: rgba(76, 141, 255, 0.06);
    box-shadow: inset 0 0 0 1px rgba(76, 141, 255, 0.25);
  }

  .check-mark {
    position: absolute;
    left: 14px;
    top: 50%;
    transform: translateY(-50%);
    width: 22px;
    height: 22px;
    border-radius: 50%;
    border: 1.5px solid #d0d0d0;
    background: #fff;
    color: transparent;
    display: inline-flex;
    align-items: center;
    justify-content: center;
  }

  &.selected .check-mark {
    border-color: var(--select-blue);
    background: var(--select-blue);
    color: #fff;
  }
}

.item-main {
  min-width: 0;
}

.item-name {
  font-size: 14px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-sub {
  margin-top: 4px;
  font-size: 12px;
  color: #999;
}

.photo-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 16px;
}

.photo-card {
  border: 1px solid #eee;
  border-radius: 10px;
  overflow: hidden;
  cursor: pointer;
  user-select: none;
  transition: border-color 0.15s ease, box-shadow 0.15s ease, transform 0.15s ease;

  &:hover {
    border-color: #d8d8d8;
  }

  &.selected {
    border-color: var(--select-blue);
    box-shadow: 0 0 0 2px rgba(76, 141, 255, 0.35);
  }
}

.thumb {
  position: relative;
  aspect-ratio: 1;
  background: #f5f5f5;
  display: flex;
  align-items: center;
  justify-content: center;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    pointer-events: none;
  }

  .check-mark {
    position: absolute;
    top: 10px;
    left: 10px;
    width: 22px;
    height: 22px;
    border-radius: 50%;
    border: 1.5px solid rgba(255, 255, 255, 0.9);
    background: rgba(0, 0, 0, 0.25);
    color: transparent;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    opacity: 0;
    transition: opacity 0.15s ease, background 0.15s ease, border-color 0.15s ease;
  }

  .video-badge {
    position: absolute;
    right: 8px;
    bottom: 8px;
    padding: 2px 6px;
    border-radius: 4px;
    background: rgba(0, 0, 0, 0.55);
    color: #fff;
    font-size: 11px;
  }
}

.photo-card:hover .check-mark,
.photo-card.selected .check-mark,
.photo-grid.is-selecting .check-mark {
  opacity: 1;
}

.photo-card.selected .check-mark {
  border-color: var(--select-blue);
  background: var(--select-blue);
  color: #fff;
}

.thumb-empty {
  color: #bbb;
}

.photo-meta {
  padding: 10px 12px 12px;
}
</style>

<style lang="scss">
.recycle-ctx-menu {
  position: fixed;
  z-index: 3200;
  min-width: 140px;
  padding: 6px 0;
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.16), 0 0 0 1px rgba(0, 0, 0, 0.04);
  user-select: none;
}

.recycle-ctx-menu .ctx-item {
  width: 100%;
  border: none;
  background: transparent;
  display: flex;
  align-items: center;
  padding: 10px 16px;
  font-size: 14px;
  color: #222;
  cursor: pointer;
  text-align: left;

  &:hover {
    background: #f3f3f3;
  }

  &.danger {
    color: #e85d5d;
  }
}

.recycle-ctx-menu .ctx-divider {
  height: 1px;
  margin: 4px 10px;
  background: #ececec;
}
</style>
