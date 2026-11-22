<template>
  <el-dialog
    :model-value="modelValue"
    title="导入图片 / 视频"
    width="760px"
    append-to-body
    destroy-on-close
    class="diary-media-picker"
    @update:model-value="onVisible"
  >
    <el-tabs v-model="tab" class="picker-tabs">
      <el-tab-pane label="从相册选择" name="album">
        <div class="album-pane">
          <div class="album-side">
            <div class="side-title">相册</div>
            <div v-loading="albumLoading" class="album-list">
              <button
                v-for="a in albums"
                :key="a.albumId"
                type="button"
                class="album-item"
                :class="{ active: a.albumId === albumId }"
                @click="selectAlbum(a.albumId)"
              >
                <img v-if="albumCover(a)" :src="albumCover(a)" alt="" class="album-cover" />
                <span v-else class="album-cover placeholder" />
                <span class="album-name" :title="a.albumName">{{ a.albumName || '未命名' }}</span>
                <span class="album-count">{{ a.photoCount || 0 }}</span>
              </button>
              <div v-if="!albumLoading && !albums.length" class="empty-tip">暂无相册</div>
            </div>
          </div>

          <div class="photo-side">
            <div class="side-head">
              <span>已选 {{ selected.length }} 项</span>
              <el-button link type="primary" :disabled="!selected.length" @click="selected = []">清空</el-button>
            </div>
            <div v-loading="photoLoading" class="photo-grid">
              <button
                v-for="p in photos"
                :key="p.photoId"
                type="button"
                class="photo-item"
                :class="{ active: isSelected(p.photoId) }"
                @click="togglePhoto(p)"
              >
                <img :src="thumbOf(p)" :alt="p.fileName || ''" loading="lazy" />
                <span v-if="Number(p.fileType) === 2" class="video-badge">▶</span>
                <span v-if="isSelected(p.photoId)" class="check">✓</span>
              </button>
              <div v-if="!photoLoading && albumId && !photos.length" class="empty-tip wide">该相册暂无媒体</div>
              <div v-else-if="!albumId" class="empty-tip wide">请选择左侧相册</div>
            </div>
            <div v-if="hasMore" class="more-wrap">
              <el-button size="small" :loading="photoLoading" @click="loadMore">加载更多</el-button>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="本地上传" name="local">
        <div class="local-pane">
          <el-upload
            drag
            multiple
            :action="uploadUrl"
            :headers="uploadHeaders"
            :show-file-list="false"
            :before-upload="beforeUpload"
            :on-success="onUploadSuccess"
            :on-error="onUploadError"
            accept="image/*,video/*,.mp4,.mov,.avi,.mkv,.wmv,.jpg,.jpeg,.png,.gif,.webp,.bmp"
            class="local-upload"
          >
            <div class="upload-inner">
              <p class="upload-title">拖拽图片 / 视频到此处，或点击选择</p>
              <p class="upload-sub">支持常见图片与 mp4 等视频；单文件建议不超过 200MB</p>
            </div>
          </el-upload>

          <div v-if="uploads.length" class="upload-list">
            <div v-for="u in uploads" :key="u.uid" class="upload-card" :class="{ fail: u.status === 'fail' }">
              <img v-if="u.kind === 'image' && u.thumbSrc" :src="absUrl(u.thumbSrc)" alt="" />
              <div v-else class="upload-video-ph">▶</div>
              <div class="upload-meta">
                <div class="upload-name" :title="u.fileName">{{ u.fileName }}</div>
                <div class="upload-status">{{ uploadLabel(u) }}</div>
              </div>
              <el-button link type="danger" @click="removeUpload(u.uid)">移除</el-button>
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button type="primary" :disabled="!canConfirm" :loading="confirming" @click="confirm">
        导入{{ pendingCount ? `（${pendingCount}）` : '' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getToken } from '@/utils/auth'
import { listAlbum } from '@/api/photos/album'
import { listPhoto } from '@/api/photos/photo'
import { photoThumbSrc } from '@/utils/photoMedia.js'

const props = defineProps({
  modelValue: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'confirm'])

const tab = ref('album')
const albums = ref([])
const albumLoading = ref(false)
const albumId = ref(null)
const photos = ref([])
const photoLoading = ref(false)
const pageNum = ref(1)
const total = ref(0)
const selected = ref([])
const uploads = ref([])
const confirming = ref(false)

const uploadUrl = import.meta.env.VITE_APP_BASE_API + '/common/upload'
const uploadHeaders = computed(() => ({ Authorization: 'Bearer ' + getToken() }))

const hasMore = computed(() => photos.value.length < total.value)
const pendingCount = computed(() => {
  if (tab.value === 'album') return selected.value.length
  return uploads.value.filter((u) => u.status === 'done').length
})
const canConfirm = computed(() => pendingCount.value > 0 && !confirming.value)

function absUrl(url) {
  if (!url) return ''
  if (/^https?:\/\//i.test(url) || url.startsWith('blob:') || url.startsWith('data:')) return url
  return (import.meta.env.VITE_APP_BASE_API || '') + url
}

function resolveUrl(url) {
  return absUrl(url)
}

function albumCover(a) {
  if (!a) return ''
  if (a.coverUrl) {
    const cover = String(a.coverUrl)
    if (cover.startsWith('/album/files/')) return resolveUrl(cover)
    const m = cover.match(/\/album\/photo\/(?:media|thumb)\/(\d+)/)
    if (m) return resolveUrl('/album/photo/thumb/' + m[1])
    return resolveUrl(cover)
  }
  if (a.coverPhotoId) return resolveUrl('/album/photo/thumb/' + a.coverPhotoId)
  return ''
}

function thumbOf(p) {
  return photoThumbSrc(p, resolveUrl)
}

function isSelected(photoId) {
  return selected.value.some((s) => s.photoId === photoId)
}

function onVisible(v) {
  emit('update:modelValue', v)
}

function close() {
  emit('update:modelValue', false)
}

async function loadAlbums() {
  albumLoading.value = true
  try {
    const res = await listAlbum({ pageNum: 1, pageSize: 200 })
    albums.value = res.rows || res.data || []
    if (!albumId.value && albums.value.length) {
      selectAlbum(albums.value[0].albumId)
    }
  } catch {
    albums.value = []
  } finally {
    albumLoading.value = false
  }
}

async function selectAlbum(id) {
  if (!id) return
  if (id === albumId.value && photos.value.length) return
  albumId.value = id
  photos.value = []
  pageNum.value = 1
  total.value = 0
  await loadPhotos()
}

async function loadPhotos() {
  if (!albumId.value) return
  photoLoading.value = true
  try {
    const res = await listPhoto({
      albumId: albumId.value,
      pageNum: pageNum.value,
      pageSize: 48
    })
    const rows = res.rows || []
    total.value = res.total || 0
    photos.value = pageNum.value === 1 ? rows : [...photos.value, ...rows]
  } catch {
    if (pageNum.value === 1) photos.value = []
  } finally {
    photoLoading.value = false
  }
}

function loadMore() {
  if (!hasMore.value || photoLoading.value) return
  pageNum.value += 1
  loadPhotos()
}

function togglePhoto(p) {
  if (!p?.photoId) return
  const idx = selected.value.findIndex((s) => s.photoId === p.photoId)
  if (idx >= 0) {
    selected.value = selected.value.filter((_, i) => i !== idx)
    return
  }
  const kind = Number(p.fileType) === 2 ? 'video' : 'image'
  const thumb = p.thumbUrl || `/album/photo/thumb/${p.photoId}`
  // 日记贴图用浏览中图/原片，勿用网格小缩略图（否则放大必糊）
  const src =
    kind === 'video'
      ? `/album/photo/media/${p.photoId}?original=true`
      : `/album/photo/media/${p.photoId}?size=medium`
  const pw = Number(p.width || p.imageWidth || 0)
  const ph = Number(p.height || p.imageHeight || 0)
  selected.value = [
    ...selected.value,
    {
      photoId: p.photoId,
      kind,
      src,
      thumbSrc: thumb,
      fileName: p.fileName || '',
      aspect: pw > 0 && ph > 0 ? pw / ph : null
    }
  ]
}

function beforeUpload(file) {
  const name = file.name || ''
  const lower = name.toLowerCase()
  const isVideo = /\.(mp4|mov|avi|mkv|wmv|webm)$/i.test(lower) || (file.type || '').startsWith('video/')
  const isImage = /\.(jpg|jpeg|png|gif|webp|bmp)$/i.test(lower) || (file.type || '').startsWith('image/')
  if (!isVideo && !isImage) {
    ElMessage.warning('请选择图片或视频文件')
    return false
  }
  const max = 200 * 1024 * 1024
  if ((file.size || 0) > max) {
    ElMessage.warning('单文件不能超过 200MB')
    return false
  }
  uploads.value = [
    ...uploads.value,
    {
      uid: file.uid || `${Date.now()}_${Math.random().toString(36).slice(2, 7)}`,
      fileName: name,
      kind: isVideo ? 'video' : 'image',
      status: 'uploading',
      src: '',
      thumbSrc: ''
    }
  ]
  return true
}

function findUpload(file) {
  const name = file?.name
  // el-upload success 回调的 file 带 uid
  return uploads.value.find((u) => u.uid === file?.uid) ||
    [...uploads.value].reverse().find((u) => u.fileName === name && u.status === 'uploading')
}

function onUploadSuccess(res, file) {
  const row = findUpload(file)
  if (!row) return
  if (res?.code !== 200) {
    row.status = 'fail'
    ElMessage.error(res?.msg || '上传失败')
    return
  }
  // 存相对路径，便于日记 JSON 迁移
  const path = res.fileName || ''
  row.src = path
  row.thumbSrc = row.kind === 'image' ? path : ''
  row.status = 'done'
}

function onUploadError(_err, file) {
  const row = findUpload(file)
  if (row) row.status = 'fail'
  ElMessage.error('上传失败，请重试')
}

function uploadLabel(u) {
  if (u.status === 'uploading') return '上传中…'
  if (u.status === 'fail') return '失败'
  return u.kind === 'video' ? '视频就绪' : '图片就绪'
}

function removeUpload(uid) {
  uploads.value = uploads.value.filter((u) => u.uid !== uid)
}

function confirm() {
  confirming.value = true
  try {
    let items = []
    if (tab.value === 'album') {
      items = selected.value.map((s) => ({ ...s }))
    } else {
      items = uploads.value
        .filter((u) => u.status === 'done' && u.src)
        .map((u) => ({
          photoId: null,
          kind: u.kind,
          src: u.src,
          thumbSrc: u.thumbSrc || u.src,
          fileName: u.fileName,
          aspect: null
        }))
    }
    if (!items.length) {
      ElMessage.warning('请先选择或上传媒体')
      return
    }
    emit('confirm', items)
    close()
  } finally {
    confirming.value = false
  }
}

watch(
  () => props.modelValue,
  (v) => {
    if (!v) return
    tab.value = 'album'
    selected.value = []
    uploads.value = []
    loadAlbums()
  }
)
</script>

<style scoped lang="scss">
.picker-tabs {
  :deep(.el-tabs__header) {
    margin-bottom: 12px;
  }
}

.album-pane {
  display: grid;
  grid-template-columns: 220px 1fr;
  gap: 12px;
  min-height: 420px;
}

.album-side,
.photo-side {
  border: 1px solid rgba(62, 52, 38, 0.1);
  border-radius: 10px;
  background: rgba(255, 252, 245, 0.65);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.side-title,
.side-head {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  font-size: 13px;
  color: rgba(62, 52, 38, 0.72);
  border-bottom: 1px solid rgba(62, 52, 38, 0.08);
}

.album-list,
.photo-grid {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 8px;
}

.album-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  border: 0;
  background: transparent;
  border-radius: 8px;
  padding: 6px 8px;
  cursor: pointer;
  text-align: left;
  color: #3e3426;
  &:hover {
    background: rgba(255, 252, 245, 0.95);
  }
  &.active {
    background: rgba(43, 42, 51, 0.9);
    color: #f7f2e8;
  }
}

.album-cover {
  width: 36px;
  height: 36px;
  border-radius: 6px;
  object-fit: cover;
  flex: none;
  background: rgba(210, 198, 178, 0.45);
  &.placeholder {
    display: inline-block;
  }
}

.album-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
}

.album-count {
  flex: none;
  font-size: 11px;
  opacity: 0.65;
}

.photo-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(88px, 1fr));
  gap: 8px;
  align-content: start;
}

.photo-item {
  position: relative;
  aspect-ratio: 1;
  border: 0;
  padding: 0;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  background: rgba(210, 198, 178, 0.35);
  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
  }
  &.active {
    outline: 2px solid #2b2a33;
    outline-offset: 1px;
  }
}

.video-badge,
.check {
  position: absolute;
  color: #fff;
  font-size: 11px;
}

.video-badge {
  left: 6px;
  bottom: 6px;
  background: rgba(0, 0, 0, 0.45);
  border-radius: 999px;
  width: 18px;
  height: 18px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.check {
  right: 6px;
  top: 6px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #2b2a33;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.more-wrap {
  flex: none;
  padding: 8px;
  text-align: center;
  border-top: 1px solid rgba(62, 52, 38, 0.08);
}

.empty-tip {
  padding: 24px 12px;
  text-align: center;
  color: rgba(62, 52, 38, 0.45);
  font-size: 13px;
  &.wide {
    grid-column: 1 / -1;
  }
}

.local-pane {
  min-height: 360px;
}

.local-upload {
  width: 100%;
  :deep(.el-upload-dragger) {
    width: 100%;
    padding: 36px 16px;
    background: rgba(255, 252, 245, 0.7);
    border-color: rgba(62, 52, 38, 0.18);
  }
}

.upload-inner {
  pointer-events: none;
}

.upload-title {
  margin: 0 0 6px;
  font-size: 15px;
  color: #3e3426;
}

.upload-sub {
  margin: 0;
  font-size: 12px;
  color: rgba(62, 52, 38, 0.5);
}

.upload-list {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 220px;
  overflow: auto;
}

.upload-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 8px;
  background: rgba(255, 252, 245, 0.8);
  border: 1px solid rgba(62, 52, 38, 0.08);
  img,
  .upload-video-ph {
    width: 44px;
    height: 44px;
    border-radius: 6px;
    object-fit: cover;
    flex: none;
    background: rgba(210, 198, 178, 0.4);
  }
  .upload-video-ph {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    color: #3e3426;
  }
  &.fail {
    border-color: rgba(180, 60, 50, 0.35);
  }
}

.upload-meta {
  flex: 1;
  min-width: 0;
}

.upload-name {
  font-size: 13px;
  color: #3e3426;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.upload-status {
  font-size: 12px;
  color: rgba(62, 52, 38, 0.5);
  margin-top: 2px;
}

@media (max-width: 720px) {
  .album-pane {
    grid-template-columns: 1fr;
    min-height: 480px;
  }
  .album-side {
    max-height: 160px;
  }
}
</style>
