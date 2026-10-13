<template>
  <el-dialog
    :model-value="modelValue"
    title="分享相册"
    width="720px"
    append-to-body
    destroy-on-close
    class="share-album-dialog"
    @update:model-value="onVisible"
  >
    <div v-loading="loading" class="share-body">
      <template v-if="!sharePath">
        <div class="share-album-name">{{ albumName || '未命名相册' }}</div>

        <div class="share-row">
          <span class="share-label">分享轨迹</span>
          <el-switch v-model="form.shareTrack" inline-prompt active-text="开" inactive-text="关" />
          <span class="share-hint">开启后，访问者可查看本相册轨迹</span>
        </div>

        <div class="share-row">
          <span class="share-label">分享内容</span>
          <el-select v-model="form.mediaScope" style="width: 200px" @change="onScopeChange">
            <el-option label="全部文件" value="all" />
            <el-option label="只分享图片" value="image" />
            <el-option label="只分享视频" value="video" />
          </el-select>
        </div>

        <div class="share-select-bar">
          <span class="share-label">选择文件</span>
          <div class="share-select-actions">
            <el-checkbox
              :model-value="isAllChecked"
              :indeterminate="isIndeterminate"
              @change="toggleAll"
            >全选当前列表</el-checkbox>
            <span class="share-count">已选 {{ selectedIds.length }} / {{ filteredPhotos.length }}</span>
          </div>
        </div>

        <div v-if="!filteredPhotos.length" class="share-empty">
          {{ photosLoading ? '加载中…' : '当前筛选下没有可分享的文件' }}
        </div>
        <div v-else class="share-grid" v-loading="photosLoading">
          <button
            v-for="item in filteredPhotos"
            :key="item.photoId"
            type="button"
            class="share-item"
            :class="{ selected: isSelected(item.photoId) }"
            @click="toggleOne(item.photoId)"
          >
            <img :src="thumbSrc(item)" :alt="item.fileName || ''" loading="lazy" />
            <span v-if="item.fileType === 2" class="share-video-badge">▶</span>
            <span class="share-check">
              <el-icon v-if="isSelected(item.photoId)"><Select /></el-icon>
            </span>
          </button>
        </div>
      </template>

      <template v-else>
        <div class="share-result">
          <p class="result-tip">
            分享已生成。链接与二维码均使用外网域名（不含内网地址与端口），复制或扫码均可打开。
          </p>
          <div class="result-url-row">
            <el-input :model-value="shareUrl" readonly />
            <el-button type="primary" @click="copyUrl">复制</el-button>
          </div>
          <div class="result-qr">
            <canvas ref="qrCanvasRef" width="200" height="200" />
            <p>扫码打开分享相册</p>
          </div>
        </div>
      </template>
    </div>

    <template #footer>
      <template v-if="!sharePath">
        <el-button @click="close">取消</el-button>
        <el-button type="primary" :loading="saving" :disabled="!canSubmit" @click="createShare">
          生成分享链接
        </el-button>
      </template>
      <template v-else>
        <el-button @click="backToSettings">继续设置</el-button>
        <el-button type="primary" @click="close">完成</el-button>
      </template>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, getCurrentInstance, nextTick, ref, watch } from 'vue'
import QRCode from 'qrcode'
import { listPhoto } from '@/api/photos/photo'
import { createAlbumShare } from '@/api/photos/share'
import { isExternal } from '@/utils/validate'
import { buildShareUrl, normalizeSharePath } from '@/utils/shareLink'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  albumId: { type: [Number, String], default: undefined },
  albumName: { type: String, default: '' }
})

const emit = defineEmits(['update:modelValue'])

const { proxy } = getCurrentInstance()

const loading = ref(false)
const photosLoading = ref(false)
const saving = ref(false)
const photos = ref([])
const selectedIds = ref([])
/** 路由路径，如 /share/xxx */
const sharePath = ref('')
/** 可访问的完整分享地址（复制与二维码相同，无内网 IP/端口） */
const shareUrl = ref('')
const qrCanvasRef = ref(null)

const form = ref({
  shareTrack: false,
  mediaScope: 'all'
})

const filteredPhotos = computed(() => {
  const list = photos.value || []
  if (form.value.mediaScope === 'image') {
    return list.filter(p => p.fileType !== 2)
  }
  if (form.value.mediaScope === 'video') {
    return list.filter(p => p.fileType === 2)
  }
  return list
})

const isAllChecked = computed(() => {
  const list = filteredPhotos.value
  return list.length > 0 && list.every(p => selectedIds.value.includes(p.photoId))
})

const isIndeterminate = computed(() => {
  const list = filteredPhotos.value
  if (!list.length) return false
  const n = list.filter(p => selectedIds.value.includes(p.photoId)).length
  return n > 0 && n < list.length
})

const canSubmit = computed(() => {
  return !!props.albumId && selectedIds.value.length > 0
})

function resolveUrl(url) {
  if (!url) return ''
  if (isExternal(url)) return url
  return import.meta.env.VITE_APP_BASE_API + url
}

function thumbSrc(item) {
  if (!item) return ''
  if (item.thumbUrl) return resolveUrl(item.thumbUrl)
  if (item.photoId) return resolveUrl('/album/photo/thumb/' + item.photoId)
  return ''
}

function isSelected(id) {
  return selectedIds.value.includes(id)
}

function toggleOne(id) {
  const i = selectedIds.value.indexOf(id)
  if (i >= 0) {
    selectedIds.value = selectedIds.value.filter(x => x !== id)
  } else {
    selectedIds.value = [...selectedIds.value, id]
  }
}

function toggleAll(val) {
  const ids = filteredPhotos.value.map(p => p.photoId)
  if (val) {
    const set = new Set([...selectedIds.value, ...ids])
    selectedIds.value = [...set]
  } else {
    const drop = new Set(ids)
    selectedIds.value = selectedIds.value.filter(id => !drop.has(id))
  }
}

function onScopeChange() {
  // 切换范围后，默认勾选当前可见项
  selectedIds.value = filteredPhotos.value.map(p => p.photoId)
}

async function loadPhotos() {
  if (!props.albumId) return
  photosLoading.value = true
  try {
    const res = await listPhoto({
      albumId: props.albumId,
      pageNum: 1,
      pageSize: 2000,
      shootTimeOrder: 'desc'
    })
    photos.value = res.rows || []
    selectedIds.value = filteredPhotos.value.map(p => p.photoId)
  } catch (e) {
    photos.value = []
    selectedIds.value = []
  } finally {
    photosLoading.value = false
  }
}

async function renderQr(url) {
  await nextTick()
  const canvas = qrCanvasRef.value
  if (!canvas || !url) return
  try {
    await QRCode.toCanvas(canvas, url, {
      width: 200,
      margin: 2,
      color: { dark: '#111827', light: '#ffffff' }
    })
  } catch (e) {
    /* ignore */
  }
}

async function createShare() {
  if (!canSubmit.value) {
    proxy?.$modal?.msgWarning?.('请至少选择一个文件')
    return
  }
  saving.value = true
  try {
    const allIds = filteredPhotos.value.map(p => p.photoId)
    const selected = selectedIds.value.filter(id => allIds.includes(id))
    const selectAll = selected.length === allIds.length && allIds.length > 0
    const res = await createAlbumShare({
      albumId: props.albumId,
      shareTrack: form.value.shareTrack ? 1 : 0,
      mediaScope: form.value.mediaScope,
      selectMode: selectAll ? 'all' : 'include',
      photoIdList: selectAll ? [] : selected,
      status: 1
    })
    const data = res.data || {}
    const path = data.sharePath || (data.share?.shareToken ? '/share/' + data.share.shareToken : '')
    if (!path) {
      proxy?.$modal?.msgError?.('生成失败')
      return
    }
    sharePath.value = normalizeSharePath(path)
    shareUrl.value = buildShareUrl(sharePath.value)
    await renderQr(shareUrl.value)
    proxy?.$modal?.msgSuccess?.('分享链接已生成')
  } catch (e) {
    /* 全局已提示 */
  } finally {
    saving.value = false
  }
}

async function copyUrl() {
  const text = shareUrl.value
  if (!text) return
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text)
    } else {
      const ta = document.createElement('textarea')
      ta.value = text
      document.body.appendChild(ta)
      ta.select()
      document.execCommand('copy')
      document.body.removeChild(ta)
    }
    proxy?.$modal?.msgSuccess?.('已复制分享链接')
  } catch (e) {
    proxy?.$modal?.msgError?.('复制失败，请手动复制')
  }
}

function resetForm() {
  form.value = { shareTrack: false, mediaScope: 'all' }
  photos.value = []
  selectedIds.value = []
  sharePath.value = ''
  shareUrl.value = ''
}

function backToSettings() {
  sharePath.value = ''
  shareUrl.value = ''
}

function close() {
  emit('update:modelValue', false)
}

function onVisible(val) {
  emit('update:modelValue', val)
}

watch(
  () => props.modelValue,
  async (open) => {
    if (open) {
      loading.value = true
      resetForm()
      try {
        await loadPhotos()
      } finally {
        loading.value = false
      }
    } else {
      resetForm()
    }
  }
)
</script>

<style scoped>
.share-body {
  min-height: 240px;
}

.share-album-name {
  margin-bottom: 16px;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.share-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}

.share-label {
  flex-shrink: 0;
  width: 72px;
  color: #606266;
  font-size: 14px;
}

.share-hint {
  color: #909399;
  font-size: 12px;
}

.share-select-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 8px 0 10px;
}

.share-select-actions {
  display: inline-flex;
  align-items: center;
  gap: 12px;
}

.share-count {
  color: #909399;
  font-size: 12px;
}

.share-empty {
  padding: 40px 0;
  text-align: center;
  color: #909399;
  font-size: 13px;
}

.share-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(96px, 1fr));
  gap: 8px;
  max-height: 340px;
  overflow: auto;
  padding: 4px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
}

.share-item {
  position: relative;
  aspect-ratio: 1;
  margin: 0;
  padding: 0;
  border: 2px solid transparent;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  background: #f5f7fa;
}

.share-item.selected {
  border-color: #409eff;
}

.share-item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.share-video-badge {
  position: absolute;
  left: 6px;
  bottom: 6px;
  padding: 1px 5px;
  border-radius: 4px;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 10px;
}

.share-check {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.9);
  color: #409eff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.15);
}

.share-item:not(.selected) .share-check {
  background: rgba(255, 255, 255, 0.55);
  color: transparent;
}

.share-result {
  text-align: center;
}

.result-tip {
  margin: 0 0 16px;
  color: #606266;
  font-size: 13px;
  line-height: 1.5;
}

.result-url-row {
  display: flex;
  gap: 8px;
  margin-bottom: 20px;
}

.result-qr {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.result-qr p {
  margin: 0;
  color: #909399;
  font-size: 12px;
}

.result-qr canvas {
  border-radius: 8px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.08);
}
</style>
