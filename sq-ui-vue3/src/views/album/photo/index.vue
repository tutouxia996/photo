<template>
  <div class="app-container">
    <el-form :model="queryParams" :inline="true">
      <el-form-item label="相册">
        <el-select
          v-model="queryParams.albumId"
          placeholder="全部相册"
          clearable
          filterable
          style="width: 220px"
        >
          <el-option
            v-for="item in albumOptions"
            :key="item.albumId"
            :label="albumLabel(item)"
            :value="item.albumId"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="文件名">
        <el-input
          v-model="queryParams.fileName"
          placeholder="文件名"
          clearable
          style="width: 200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Upload" @click="uploadOpen = true" v-hasPermi="['album:photo:upload']">上传图片</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="!ids.length" @click="handleDelete()" v-hasPermi="['album:photo:remove']">删除</el-button>
      </el-col>
    </el-row>

    <el-table v-loading="loading" :data="photoList" @selection-change="sel => ids = sel.map(i => i.photoId)">
      <el-table-column type="selection" width="55" />
      <el-table-column label="ID" prop="photoId" width="80" />
      <el-table-column label="相册" min-width="140" :show-overflow-tooltip="true">
        <template #default="scope">
          {{ albumNameMap[scope.row.albumId] || scope.row.albumId || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="预览" width="90" align="center">
        <template #default="scope">
          <div class="thumb-wrap" @click="openPreview(scope.row)">
            <template v-if="scope.row.fileType === 2">
              <img
                v-if="hasVideoThumb(scope.row)"
                class="thumb-img"
                :src="thumbSrc(scope.row)"
                alt=""
                @error="markThumbBroken(scope.row)"
              />
              <div v-else class="video-thumb-box" title="无封面">
                <span class="thumb-fallback">视频</span>
              </div>
              <span class="thumb-badge">视频</span>
            </template>
            <img
              v-else
              class="thumb-img"
              :src="thumbSrc(scope.row)"
              alt=""
            />
          </div>
        </template>
      </el-table-column>
      <el-table-column label="文件名" prop="fileName" :show-overflow-tooltip="true" />
      <el-table-column label="拍摄时间" prop="shootTime" width="170" />
      <el-table-column label="相机" prop="cameraModel" :show-overflow-tooltip="true" />
      <el-table-column label="坐标" width="180">
        <template #default="scope">
          <span v-if="scope.row.latitude">{{ scope.row.latitude }}, {{ scope.row.longitude }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template #default="scope">
          <el-button link type="danger" @click="handleDelete(scope.row)" v-hasPermi="['album:photo:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog title="上传图片" v-model="uploadOpen" width="480px">
      <el-form label-width="80px">
        <el-form-item label="相册" required>
          <el-select v-model="uploadAlbumId" placeholder="选择目标相册" filterable style="width: 100%">
            <el-option
              v-for="item in albumOptions"
              :key="item.albumId"
              :label="albumLabel(item)"
              :value="item.albumId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="文件">
          <el-upload :auto-upload="false" :limit="1" :on-change="f => uploadFile = f.raw" :on-remove="() => uploadFile = null">
            <el-button type="primary">选择文件</el-button>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitUpload">上传</el-button>
        <el-button @click="uploadOpen = false">取消</el-button>
      </template>
    </el-dialog>

    <!-- 居中预览：保留底部工具栏，禁止拖动，缩放走轻量 DOM transform -->
    <teleport to="body">
      <div v-if="previewVisible" class="album-photo-viewer">
        <button type="button" class="album-photo-viewer__close" title="关闭" @click="closePreview">
          <el-icon :size="20"><Close /></el-icon>
        </button>
        <div
          class="album-photo-viewer__stage"
          @wheel.prevent="onPreviewWheel"
        >
          <div v-if="previewIsVideo" class="album-photo-viewer__video-wrap">
            <video
              :key="videoPlayerKey"
              class="album-photo-viewer__video"
              :src="previewUrl"
              controls
              autoplay
              playsinline
            />
            <div class="album-photo-viewer__video-toolbar" @click.stop>
              <label>
                清晰度
                <select v-model="videoQuality" @change="onAdminVideoQualityChange">
                  <option value="480p">480p</option>
                  <option value="720p">720p</option>
                  <option value="1080p">1080p</option>
                  <option value="original">原片</option>
                </select>
              </label>
            </div>
          </div>
          <div v-else ref="previewLayerRef" class="album-photo-viewer__layer">
            <img
              class="album-photo-viewer__img"
              :src="previewUrl"
              :alt="previewName"
              draggable="false"
              decoding="async"
              @load="paintPreviewTransform(false)"
              @dragstart.prevent
            />
          </div>
        </div>
        <div v-if="!previewIsVideo" class="album-photo-viewer__toolbar" @click.stop>
          <button type="button" class="album-photo-viewer__tool" title="缩小" @click="zoomPreview(-1, true)">
            <el-icon :size="18"><ZoomOut /></el-icon>
          </button>
          <span ref="previewZoomLabelRef" class="album-photo-viewer__zoom">100%</span>
          <button type="button" class="album-photo-viewer__tool" title="放大" @click="zoomPreview(1, true)">
            <el-icon :size="18"><ZoomIn /></el-icon>
          </button>
          <button type="button" class="album-photo-viewer__tool" title="向右旋转" @click="rotatePreview">
            <el-icon :size="18"><RefreshRight /></el-icon>
          </button>
          <button type="button" class="album-photo-viewer__tool" title="重置" @click="resetPreviewTransform">
            <el-icon :size="18"><FullScreen /></el-icon>
          </button>
        </div>
      </div>
    </teleport>
  </div>
</template>

<script setup name="AlbumPhoto">
import { Close, ZoomIn, ZoomOut, RefreshRight, FullScreen } from '@element-plus/icons-vue'
import { isExternal } from '@/utils/validate'
import { listAlbum } from '@/api/album/album'
import { listPhoto, delPhoto, uploadPhoto, getVideoProxyStatus } from '@/api/album/photo'
import { videoPlaySrc } from '@/utils/videoProxy'

const { proxy } = getCurrentInstance()
const photoList = ref([])
const albumOptions = ref([])
const albumNameMap = ref({})
const loading = ref(true)
const total = ref(0)
const ids = ref([])
const uploadOpen = ref(false)
const uploadAlbumId = ref(undefined)
const uploadFile = ref(null)
const brokenThumbIds = ref(new Set())
const previewVisible = ref(false)
const previewUrl = ref('')
const previewName = ref('')
const previewIsVideo = ref(false)
const previewPhotoId = ref(null)
const videoQuality = ref('480p')
const VIDEO_PLAY_FPS = 30
const videoPlayerKey = ref('')
let videoProxyReqSeq = 0
const previewLayerRef = ref()
const previewZoomLabelRef = ref()
const previewTransform = { scale: 1, deg: 0 }
let previewPaintRaf = 0
let previewWheelRaf = 0
let previewLabelRaf = 0
let pendingWheelDelta = 0

const ZOOM_RATE = 1.2
const MIN_SCALE = 0.2
const MAX_SCALE = 4

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  albumId: undefined,
  fileName: undefined
})

function albumLabel(item) {
  if (!item) return ''
  return `${item.albumName || '未命名'}（ID:${item.albumId}）`
}

function resolveUrl(url) {
  if (!url) return ''
  if (isExternal(url)) return url
  return import.meta.env.VITE_APP_BASE_API + url
}

/** 优先静态 thumbUrl；视频无封面时用按需截帧接口 */
function thumbSrc(item) {
  if (!item) return ''
  if (item.thumbUrl) return resolveUrl(item.thumbUrl)
  if (Number(item.fileType) === 2 && item.photoId) {
    return resolveUrl('/album/photo/thumb/' + item.photoId)
  }
  if (!item.photoId) return ''
  return resolveUrl('/album/photo/media/' + item.photoId)
}

function originalSrc(item) {
  if (!item?.photoId) return ''
  return resolveUrl('/album/photo/media/' + item.photoId + '?original=true')
}

async function reloadAdminVideoProxy() {
  const photoId = previewPhotoId.value
  if (!previewIsVideo.value || !photoId) return
  const seq = ++videoProxyReqSeq
  const quality = videoQuality.value
  if (quality === 'original') {
    previewUrl.value = videoPlaySrc(photoId, 'original')
    videoPlayerKey.value = `${photoId}-original`
    return
  }
  try {
    const res = await getVideoProxyStatus(photoId, quality, VIDEO_PLAY_FPS)
    if (seq !== videoProxyReqSeq) return
    const data = res?.data || res || {}
    if (data.status === 'ready') {
      previewUrl.value = videoPlaySrc(photoId, quality, VIDEO_PLAY_FPS)
      videoPlayerKey.value = `${photoId}-${quality}-30`
      return
    }
  } catch (_) { /* fallback original */ }
  if (seq !== videoProxyReqSeq) return
  previewUrl.value = videoPlaySrc(photoId, 'original')
  videoPlayerKey.value = `${photoId}-original-fallback`
}

function onAdminVideoQualityChange() {
  reloadAdminVideoProxy()
}

function hasVideoThumb(item) {
  if (!item?.photoId || !item.thumbUrl) return false
  return !brokenThumbIds.value.has(item.photoId)
}

function markThumbBroken(item) {
  if (!item?.photoId) return
  const next = new Set(brokenThumbIds.value)
  next.add(item.photoId)
  brokenThumbIds.value = next
}

function buildPreviewTransformCss() {
  const { scale, deg } = previewTransform
  return `translate3d(0,0,0) scale(${scale}) rotate(${deg}deg)`
}

function syncPreviewZoomLabel() {
  const el = previewZoomLabelRef.value
  if (!el) return
  el.textContent = `${Math.round(previewTransform.scale * 100)}%`
}

function schedulePreviewZoomLabel() {
  if (previewLabelRaf) return
  previewLabelRaf = requestAnimationFrame(() => {
    previewLabelRaf = 0
    syncPreviewZoomLabel()
  })
}

function paintPreviewTransform(animate = false) {
  const el = previewLayerRef.value
  if (!el) return
  el.style.transition = animate ? 'transform .2s ease-out' : 'none'
  el.style.transform = buildPreviewTransformCss()
  schedulePreviewZoomLabel()
}

function resetPreviewTransform() {
  previewTransform.scale = 1
  previewTransform.deg = 0
  pendingWheelDelta = 0
  nextTick(() => {
    paintPreviewTransform(false)
    syncPreviewZoomLabel()
  })
}

function zoomPreview(delta, animate = false) {
  const next = delta > 0
    ? previewTransform.scale * ZOOM_RATE
    : previewTransform.scale / ZOOM_RATE
  previewTransform.scale = Math.min(MAX_SCALE, Math.max(MIN_SCALE, next))
  if (animate) paintPreviewTransform(true)
  else {
    if (previewPaintRaf) cancelAnimationFrame(previewPaintRaf)
    previewPaintRaf = requestAnimationFrame(() => {
      previewPaintRaf = 0
      paintPreviewTransform(false)
    })
  }
}

function rotatePreview() {
  previewTransform.deg += 90
  paintPreviewTransform(true)
}

function onPreviewWheel(e) {
  if (previewIsVideo.value) return
  pendingWheelDelta += e.deltaY < 0 ? 1 : -1
  if (previewWheelRaf) return
  previewWheelRaf = requestAnimationFrame(() => {
    previewWheelRaf = 0
    const delta = pendingWheelDelta
    pendingWheelDelta = 0
    if (!delta) return
    zoomPreview(delta > 0 ? 1 : -1, false)
  })
}

function openPreview(item) {
  if (!item?.photoId) return
  const isVideo = item.fileType === 2
  previewIsVideo.value = isVideo
  previewPhotoId.value = item.photoId
  previewName.value = item.fileName || ''
  previewVisible.value = true
  setPreviewPageLock(true)
  resetPreviewTransform()
  if (isVideo) {
    videoQuality.value = '480p'
    reloadAdminVideoProxy()
  } else {
    previewUrl.value = originalSrc(item)
  }
}

function closePreview() {
  previewVisible.value = false
  previewUrl.value = ''
  previewName.value = ''
  previewIsVideo.value = false
  previewPhotoId.value = null
  previewTransform.scale = 1
  previewTransform.deg = 0
  setPreviewPageLock(false)
}

/** 预览时隔离底层后台页，避免半透明合成导致放大卡顿 */
function setPreviewPageLock(locked) {
  const app = document.getElementById('app')
  if (app) app.style.visibility = locked ? 'hidden' : ''
  document.body.style.overflow = locked ? 'hidden' : ''
}

function onPreviewKeydown(e) {
  if (e.key === 'Escape' && previewVisible.value) closePreview()
}

function loadAlbums() {
  return listAlbum({ pageNum: 1, pageSize: 500 }).then(res => {
    const rows = res.rows || []
    albumOptions.value = rows
    const map = {}
    rows.forEach(item => {
      map[item.albumId] = item.albumName
    })
    albumNameMap.value = map
  })
}

function getList() {
  loading.value = true
  listPhoto(queryParams.value).then(res => {
    photoList.value = res.rows || []
    total.value = res.total || 0
  }).finally(() => {
    loading.value = false
  })
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  queryParams.value = {
    pageNum: 1,
    pageSize: 10,
    albumId: undefined,
    fileName: undefined
  }
  getList()
}

function handleDelete(row) {
  const photoIds = row?.photoId || ids.value
  proxy.$modal.confirm('确认将选中图片放入回收站？').then(() => delPhoto(photoIds)).then(() => {
    getList()
    proxy.$modal.msgSuccess('已放入回收站')
  }).catch(() => {})
}

const MAX_UPLOAD_BYTES = 30 * 1024 * 1024 * 1024

function submitUpload() {
  if (!uploadAlbumId.value || !uploadFile.value) {
    proxy.$modal.msgError('请选择相册并选择文件')
    return
  }
  if ((uploadFile.value.size || 0) > MAX_UPLOAD_BYTES) {
    proxy.$modal.msgError('文件超过约 30GB 上传上限；本地超大视频请用「扫描入库」')
    return
  }
  const fd = new FormData()
  fd.append('file', uploadFile.value)
  fd.append('albumId', uploadAlbumId.value)
  uploadPhoto(fd).then(() => {
    proxy.$modal.msgSuccess('上传成功')
    uploadOpen.value = false
    uploadFile.value = null
    getList()
  })
}

onMounted(() => {
  window.addEventListener('keydown', onPreviewKeydown)
})
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onPreviewKeydown)
  setPreviewPageLock(false)
  if (previewPaintRaf) cancelAnimationFrame(previewPaintRaf)
  if (previewWheelRaf) cancelAnimationFrame(previewWheelRaf)
  if (previewLabelRaf) cancelAnimationFrame(previewLabelRaf)
})

loadAlbums().finally(() => getList())
</script>

<style scoped>
.thumb-wrap {
  position: relative;
  width: 56px;
  height: 56px;
  margin: 0 auto;
  cursor: pointer;
  border-radius: 4px;
  overflow: hidden;
  background: #f5f5f5;
}

.thumb-img {
  width: 56px;
  height: 56px;
  object-fit: cover;
  display: block;
}

.video-thumb-box {
  width: 56px;
  height: 56px;
  background: #111;
  display: flex;
  align-items: center;
  justify-content: center;
}

.thumb-fallback {
  color: #bbb;
  font-size: 11px;
}

.thumb-badge {
  position: absolute;
  right: 2px;
  bottom: 2px;
  padding: 0 4px;
  border-radius: 3px;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 10px;
  line-height: 16px;
  pointer-events: none;
}
</style>

<style lang="scss">
/* 与相册详情一致：不透明白底，避免半透明叠加后台表格导致放大卡顿 */
.album-photo-viewer {
  position: fixed;
  inset: 0;
  z-index: 4000;
  background: #fff;
  user-select: none;
  -webkit-user-select: none;
}

.album-photo-viewer__close {
  position: absolute;
  top: 20px;
  left: 20px;
  z-index: 2;
  width: 40px;
  height: 40px;
  border: none;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.06);
  color: #333;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;

  &:hover {
    background: rgba(0, 0, 0, 0.12);
  }
}

.album-photo-viewer__stage {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  cursor: default;
  contain: layout style;
}

.album-photo-viewer__layer {
  display: flex;
  align-items: center;
  justify-content: center;
  transform: translate3d(0, 0, 0);
  transform-origin: center center;
  will-change: transform;
  backface-visibility: hidden;
}

.album-photo-viewer__img {
  max-width: min(92%, 1400px);
  max-height: min(86%, 86vh);
  width: auto;
  height: auto;
  object-fit: contain;
  pointer-events: none;
  -webkit-user-drag: none;
}

.album-photo-viewer__video-wrap {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
}

.album-photo-viewer__video {
  max-width: min(92%, 1400px);
  max-height: min(78%, 78vh);
  width: auto;
  height: auto;
  outline: none;
  background: #000;
}

.album-photo-viewer__video-toolbar {
  position: absolute;
  left: 50%;
  bottom: 28px;
  transform: translateX(-50%);
  z-index: 3;
  display: flex;
  gap: 12px;
  padding: 8px 14px;
  border-radius: 22px;
  background: rgba(0, 0, 0, 0.78);
  color: #fff;
  font-size: 13px;

  label {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    margin: 0;

    &.is-disabled {
      opacity: 0.45;
    }
  }

  select {
    border: none;
    border-radius: 6px;
    padding: 4px 8px;
    background: rgba(255, 255, 255, 0.14);
    color: #fff;

    option {
      color: #111;
    }
  }
}

.album-photo-viewer__toolbar {
  position: absolute;
  left: 50%;
  bottom: 28px;
  transform: translateX(-50%);
  z-index: 3;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 10px;
  border-radius: 22px;
  background: rgba(0, 0, 0, 0.78);
  color: #fff;
}

.album-photo-viewer__tool {
  width: 34px;
  height: 34px;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;

  &:hover {
    background: rgba(255, 255, 255, 0.14);
  }
}

.album-photo-viewer__zoom {
  min-width: 52px;
  text-align: center;
  font-size: 13px;
  color: #fff;
}
</style>
