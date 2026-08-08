<template>
  <div class="recycle-page" v-loading="loading">
    <header class="recycle-header">
      <button type="button" class="back-btn" @click="goBack">
        <el-icon><ArrowLeft /></el-icon>
        <span>返回相册</span>
      </button>
      <h1 class="recycle-title">回收站</h1>
    </header>

    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <el-tab-pane label="相册" name="album">
        <div v-if="!albumList.length" class="empty">回收站暂无相册</div>
        <div v-else class="item-list">
          <div v-for="item in albumList" :key="item.albumId" class="item-row">
            <div class="item-main">
              <div class="item-name">{{ item.albumName }}</div>
              <div class="item-sub">照片 {{ item.photoCount ?? 0 }} · {{ formatTime(item.updateTime || item.createTime) }}</div>
            </div>
            <div class="item-actions">
              <el-button size="small" @click="restoreOneAlbum(item)">恢复</el-button>
              <el-button size="small" type="danger" plain @click="purgeOneAlbum(item)">彻底删除</el-button>
            </div>
          </div>
        </div>
      </el-tab-pane>
      <el-tab-pane label="照片/视频" name="photo">
        <div v-if="!photoList.length" class="empty">回收站暂无照片/视频</div>
        <div v-else class="photo-grid">
          <div v-for="item in photoList" :key="item.photoId" class="photo-card">
            <div class="thumb">
              <img v-if="thumbSrc(item)" :src="thumbSrc(item)" :alt="item.fileName" loading="lazy" />
              <div v-else class="thumb-empty">
                <el-icon :size="28"><PictureFilled /></el-icon>
              </div>
            </div>
            <div class="photo-meta">
              <div class="item-name" :title="item.fileName">{{ item.fileName }}</div>
              <div class="item-actions">
                <el-button size="small" @click="restoreOnePhoto(item)">恢复</el-button>
                <el-button size="small" type="danger" plain @click="purgeOnePhoto(item)">彻底删除</el-button>
              </div>
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
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

function resolveUrl(url) {
  if (!url) return ''
  if (isExternal(url)) return url
  if (url.startsWith('/album/photo/media/')) {
    const token = getToken()
    return import.meta.env.VITE_APP_BASE_API + url + (token ? `?Authorization=Bearer ${token}` : '')
  }
  return import.meta.env.VITE_APP_BASE_API + url
}

function thumbSrc(item) {
  if (!item) return ''
  if (item.thumbUrl) return resolveUrl(item.thumbUrl)
  if (item.photoId) return resolveUrl(`/album/photo/media/${item.photoId}`)
  return ''
}

function formatTime(time) {
  if (!time) return ''
  return String(time).replace('T', ' ').slice(0, 19)
}

function goBack() {
  proxy.$tab.closeOpenPage({ path: '/photos/index' })
}

function loadAlbums() {
  return listAlbum({ pageNum: 1, pageSize: 200, deleted: 2 }).then(res => {
    albumList.value = res.rows || []
  })
}

function loadPhotos() {
  return listPhoto({ pageNum: 1, pageSize: 200, deleted: 2 }).then(res => {
    photoList.value = res.rows || []
  })
}

function refresh() {
  loading.value = true
  const req = activeTab.value === 'album' ? loadAlbums() : loadPhotos()
  req.catch(() => {}).finally(() => {
    loading.value = false
  })
}

function onTabChange() {
  refresh()
}

function restoreOneAlbum(item) {
  proxy.$modal.confirm(`确认恢复相册「${item.albumName}」吗？`)
    .then(() => restoreAlbum(item.albumId))
    .then(() => {
      proxy.$modal.msgSuccess('已恢复')
      refresh()
    })
    .catch(() => {})
}

function purgeOneAlbum(item) {
  proxy.$modal.confirm(`确认彻底删除相册「${item.albumName}」吗？此操作不可恢复。`)
    .then(() => purgeAlbum(item.albumId))
    .then(() => {
      proxy.$modal.msgSuccess('已彻底删除')
      refresh()
    })
    .catch(() => {})
}

function restoreOnePhoto(item) {
  proxy.$modal.confirm(`确认恢复「${item.fileName}」吗？`)
    .then(() => restorePhoto(item.photoId))
    .then(() => {
      proxy.$modal.msgSuccess('已恢复')
      refresh()
    })
    .catch(() => {})
}

function purgeOnePhoto(item) {
  proxy.$modal.confirm(`确认彻底删除「${item.fileName}」吗？此操作不可恢复。`)
    .then(() => purgePhoto(item.photoId))
    .then(() => {
      proxy.$modal.msgSuccess('已彻底删除')
      refresh()
    })
    .catch(() => {})
}

refresh()
</script>

<style scoped lang="scss">
.recycle-page {
  min-height: calc(100vh - 84px);
  margin: -20px;
  padding: 28px 32px 48px;
  background: #fff;
  color: #1a1a1a;
}

.recycle-header {
  margin-bottom: 18px;
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
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border: 1px solid #eee;
  border-radius: 10px;
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

.item-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
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
}

.thumb {
  aspect-ratio: 1;
  background: #f5f5f5;
  display: flex;
  align-items: center;
  justify-content: center;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.thumb-empty {
  color: #bbb;
}

.photo-meta {
  padding: 10px 12px 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
</style>
