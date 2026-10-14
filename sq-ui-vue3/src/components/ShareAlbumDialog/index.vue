<template>
  <el-dialog
    :model-value="modelValue"
    title="分享相册"
    width="780px"
    append-to-body
    destroy-on-close
    class="share-album-dialog"
    @update:model-value="onVisible"
  >
    <div v-loading="loading" class="share-body">
      <div v-if="!isMulti" class="share-tabs">
        <button type="button" class="share-tab" :class="{ active: tab === 'create' }" @click="tab = 'create'">新建分享</button>
        <button type="button" class="share-tab" :class="{ active: tab === 'manage' }" @click="openManage">
          管理分享
          <span v-if="shareList.length" class="tab-count">{{ shareList.length }}</span>
        </button>
      </div>

      <!-- 新建 -->
      <template v-if="tab === 'create' && !hasResult">
        <div class="share-album-name">
          <template v-if="isMulti">已选 {{ albumTargets.length }} 个相册</template>
          <template v-else>{{ primaryAlbumName || '未命名相册' }}</template>
        </div>

        <div v-if="isMulti" class="share-album-chips">
          <span v-for="a in albumTargets" :key="a.albumId" class="share-album-chip" :title="a.albumName">
            {{ a.albumName || '未命名相册' }}
          </span>
        </div>

        <div class="share-row">
          <span class="share-label">分享轨迹</span>
          <el-switch v-model="form.shareTrack" inline-prompt active-text="开" inactive-text="关" />
          <span class="share-hint">{{ isMulti ? '开启后，每个相册的访问者可查看对应轨迹' : '开启后，访问者可查看本相册轨迹' }}</span>
        </div>

        <div class="share-row">
          <span class="share-label">分享内容</span>
          <el-select v-model="form.mediaScope" style="width: 200px" @change="onScopeChange">
            <el-option label="全部文件" value="all" />
            <el-option label="只分享图片" value="image" />
            <el-option label="只分享视频" value="video" />
          </el-select>
        </div>

        <div class="share-row">
          <span class="share-label">有效期</span>
          <el-select v-model="form.expirePreset" style="width: 200px" @change="onExpirePresetChange">
            <el-option label="永久有效" value="never" />
            <el-option label="1 天后" value="1d" />
            <el-option label="7 天后" value="7d" />
            <el-option label="30 天后" value="30d" />
            <el-option label="自定义时间" value="custom" />
          </el-select>
          <el-date-picker
            v-if="form.expirePreset === 'custom'"
            v-model="form.expireTime"
            type="datetime"
            placeholder="选择过期时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            :disabled-date="disablePastDate"
            style="width: 220px"
          />
          <span v-else-if="expireHint" class="share-hint">{{ expireHint }}</span>
        </div>

        <template v-if="!isMulti">
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
        <p v-else class="share-multi-hint">多选相册将生成<strong>一条</strong>分享链接与二维码，访客打开后可查看本次勾选的全部相册内容（按上述设置分享符合条件的文件）。</p>
      </template>

      <!-- 新建成功结果 -->
      <template v-else-if="tab === 'create' && hasResult">
        <div class="share-result">
          <p class="result-tip">
            <template v-if="isMulti">已为选中的 {{ albumTargets.length }} 个相册生成一条分享链接。</template>
            <template v-else>分享已生成。可随时在「管理分享」中关闭或删除。</template>
            <template v-if="resultExpireText">有效期至 {{ resultExpireText }}。</template>
            <template v-else>永久有效。</template>
          </p>
          <div class="result-url-row">
            <el-input :model-value="shareUrl" readonly />
            <el-button type="primary" @click="copyText(shareUrl)">复制</el-button>
          </div>
          <div class="result-qr">
            <canvas ref="qrCanvasRef" width="200" height="200" />
            <p>扫码打开分享相册</p>
          </div>
        </div>
      </template>

      <!-- 管理已有分享 -->
      <template v-else>
        <div v-if="!shareList.length" class="share-empty">暂无分享记录，可先新建分享</div>
        <div v-else class="share-manage-list">
          <div v-for="item in shareList" :key="item.shareId" class="share-manage-card">
            <div class="manage-main">
              <div class="manage-title">
                <el-tag size="small" :type="shareStatusType(item)">{{ shareStatusLabel(item) }}</el-tag>
                <span class="manage-scope">{{ scopeLabel(item.mediaScope) }}</span>
                <span v-if="item.shareTrack === 1" class="manage-scope">含轨迹</span>
                <span v-if="(item.albumIdList || []).length > 1" class="manage-scope">
                  {{ item.albumIdList.length }} 个相册
                </span>
              </div>
              <div class="manage-meta">
                <span>创建 {{ formatTime(item.createTime) }}</span>
                <span>· {{ expireLabel(item) }}</span>
              </div>
              <div class="manage-url">{{ buildUrlForShare(item) }}</div>
            </div>
            <div class="manage-actions">
              <el-switch
                :model-value="item.status === 1 && !isExpired(item)"
                inline-prompt
                active-text="开"
                inactive-text="关"
                :disabled="isExpired(item) || togglingId === item.shareId"
                :loading="togglingId === item.shareId"
                @change="(val) => toggleShareStatus(item, val)"
              />
              <el-button size="small" @click="showShareLink(item)">链接/二维码</el-button>
              <el-button size="small" type="danger" plain :loading="deletingId === item.shareId" @click="removeShare(item)">
                删除
              </el-button>
            </div>
          </div>
        </div>

        <div v-if="managePreviewUrl" class="share-result manage-preview">
          <div class="result-url-row">
            <el-input :model-value="managePreviewUrl" readonly />
            <el-button type="primary" @click="copyText(managePreviewUrl)">复制</el-button>
          </div>
          <div class="result-qr">
            <canvas ref="manageQrRef" width="180" height="180" />
          </div>
        </div>
      </template>
    </div>

    <template #footer>
      <template v-if="tab === 'create' && !hasResult">
        <el-button @click="close">取消</el-button>
        <el-button type="primary" :loading="saving" :disabled="!canSubmit" @click="createShare">
          生成分享链接
        </el-button>
      </template>
      <template v-else-if="tab === 'create' && hasResult">
        <el-button v-if="!isMulti" @click="backToSettings">再建一个</el-button>
        <el-button v-if="!isMulti" @click="openManage">管理分享</el-button>
        <el-button type="primary" @click="close">完成</el-button>
      </template>
      <template v-else>
        <el-button type="primary" @click="close">关闭</el-button>
      </template>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, getCurrentInstance, nextTick, ref, watch } from 'vue'
import QRCode from 'qrcode'
import { listPhoto } from '@/api/photos/photo'
import {
  createAlbumShare,
  updateAlbumShare,
  listAlbumShare,
  delAlbumShare
} from '@/api/photos/share'
import { isExternal } from '@/utils/validate'
import { buildShareUrl, normalizeSharePath } from '@/utils/shareLink'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  albumId: { type: [Number, String], default: undefined },
  albumName: { type: String, default: '' },
  /** 多选相册：[{ albumId, albumName }]，优先于 albumId */
  albums: { type: Array, default: () => [] }
})

const emit = defineEmits(['update:modelValue'])
const { proxy } = getCurrentInstance()

const loading = ref(false)
const photosLoading = ref(false)
const saving = ref(false)
const photos = ref([])
const selectedIds = ref([])
const sharePath = ref('')
const shareUrl = ref('')
const resultExpireText = ref('')
const qrCanvasRef = ref(null)
const manageQrRef = ref(null)
const tab = ref('create')
const shareList = ref([])
const togglingId = ref(null)
const deletingId = ref(null)
const managePreviewUrl = ref('')

const form = ref({
  shareTrack: false,
  mediaScope: 'all',
  expirePreset: 'never',
  expireTime: ''
})

const albumTargets = computed(() => {
  const list = Array.isArray(props.albums) ? props.albums.filter(a => a && a.albumId != null) : []
  if (list.length) {
    return list.map(a => ({
      albumId: a.albumId,
      albumName: a.albumName || ''
    }))
  }
  if (props.albumId != null && props.albumId !== '') {
    return [{ albumId: props.albumId, albumName: props.albumName || '' }]
  }
  return []
})

const isMulti = computed(() => albumTargets.value.length > 1)
const primaryAlbumId = computed(() => albumTargets.value[0]?.albumId)
const primaryAlbumName = computed(() => albumTargets.value[0]?.albumName || props.albumName || '')
const hasResult = computed(() => !!sharePath.value && !!shareUrl.value)

const filteredPhotos = computed(() => {
  const list = photos.value || []
  if (form.value.mediaScope === 'image') return list.filter(p => p.fileType !== 2)
  if (form.value.mediaScope === 'video') return list.filter(p => p.fileType === 2)
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
  if (!albumTargets.value.length) return false
  if (isMulti.value) return true
  return selectedIds.value.length > 0
})

const expireHint = computed(() => {
  const t = resolveExpireTime()
  return t ? `将于 ${t} 失效` : ''
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
  if (i >= 0) selectedIds.value = selectedIds.value.filter(x => x !== id)
  else selectedIds.value = [...selectedIds.value, id]
}

function toggleAll(val) {
  const ids = filteredPhotos.value.map(p => p.photoId)
  if (val) selectedIds.value = [...new Set([...selectedIds.value, ...ids])]
  else {
    const drop = new Set(ids)
    selectedIds.value = selectedIds.value.filter(id => !drop.has(id))
  }
}

function onScopeChange() {
  selectedIds.value = filteredPhotos.value.map(p => p.photoId)
}

function disablePastDate(date) {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return date.getTime() < today.getTime()
}

function pad(n) {
  return String(n).padStart(2, '0')
}

function formatDateTime(d) {
  if (!(d instanceof Date) || Number.isNaN(d.getTime())) return ''
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function formatTime(raw) {
  if (!raw) return '-'
  const d = new Date(typeof raw === 'string' ? raw.replace(/-/g, '/') : raw)
  if (Number.isNaN(d.getTime())) return String(raw)
  return formatDateTime(d)
}

function onExpirePresetChange() {
  if (form.value.expirePreset !== 'custom') form.value.expireTime = ''
}

function resolveExpireTime() {
  const preset = form.value.expirePreset
  if (preset === 'never') return null
  if (preset === 'custom') return form.value.expireTime || null
  const days = preset === '1d' ? 1 : preset === '7d' ? 7 : preset === '30d' ? 30 : 0
  if (!days) return null
  const d = new Date()
  d.setDate(d.getDate() + days)
  return formatDateTime(d)
}

function scopeLabel(scope) {
  if (scope === 'image') return '仅图片'
  if (scope === 'video') return '仅视频'
  return '全部文件'
}

function isExpired(item) {
  if (!item?.expireTime) return false
  const t = new Date(typeof item.expireTime === 'string' ? item.expireTime.replace(/-/g, '/') : item.expireTime).getTime()
  return Number.isFinite(t) && t < Date.now()
}

function shareStatusLabel(item) {
  if (isExpired(item)) return '已过期'
  if (item.status === 1) return '生效中'
  return '已关闭'
}

function shareStatusType(item) {
  if (isExpired(item)) return 'info'
  if (item.status === 1) return 'success'
  return 'danger'
}

function expireLabel(item) {
  if (!item?.expireTime) return '永久有效'
  if (isExpired(item)) return `已于 ${formatTime(item.expireTime)} 过期`
  return `有效至 ${formatTime(item.expireTime)}`
}

function buildUrlForShare(item) {
  if (!item?.shareToken) return ''
  return buildShareUrl('/share/' + item.shareToken)
}

async function loadPhotos() {
  if (isMulti.value || primaryAlbumId.value == null) return
  photosLoading.value = true
  try {
    const pageSize = 500
    let pageNum = 1
    let all = []
    let total = 0
    do {
      const res = await listPhoto({
        albumId: primaryAlbumId.value,
        pageNum,
        pageSize,
        shootTimeOrder: 'desc'
      })
      const rows = res.rows || []
      total = Number(res.total) || 0
      all = all.concat(rows)
      if (!rows.length || all.length >= total) break
      pageNum += 1
    } while (pageNum <= 50)
    photos.value = all
    selectedIds.value = filteredPhotos.value.map(p => p.photoId)
  } catch (e) {
    photos.value = []
    selectedIds.value = []
  } finally {
    photosLoading.value = false
  }
}

async function loadShareList() {
  if (isMulti.value || primaryAlbumId.value == null) {
    shareList.value = []
    return
  }
  try {
    const res = await listAlbumShare(primaryAlbumId.value)
    shareList.value = res.rows || []
  } catch (e) {
    shareList.value = []
  }
}

async function renderQr(canvasRef, url) {
  await nextTick()
  const canvas = canvasRef?.value
  if (!canvas || !url) return
  try {
    await QRCode.toCanvas(canvas, url, {
      width: canvas.width || 200,
      margin: 2,
      color: { dark: '#111827', light: '#ffffff' }
    })
  } catch (e) {
    /* ignore */
  }
}

async function createShare() {
  if (!canSubmit.value) {
    proxy?.$modal?.msgWarning?.(isMulti.value ? '请选择要分享的相册' : '请至少选择一个文件')
    return
  }
  if (form.value.expirePreset === 'custom' && !form.value.expireTime) {
    proxy?.$modal?.msgWarning?.('请选择过期时间')
    return
  }
  saving.value = true
  try {
    const expireTime = resolveExpireTime()
    resultExpireText.value = expireTime || ''
    const albumIdList = albumTargets.value.map(a => a.albumId)
    let selectMode = 'all'
    let photoIdList = []
    if (!isMulti.value) {
      const allIds = filteredPhotos.value.map(p => p.photoId)
      const selected = selectedIds.value.filter(id => allIds.includes(id))
      const selectAll = selected.length === allIds.length && allIds.length > 0
      selectMode = selectAll ? 'all' : 'include'
      photoIdList = selectAll ? [] : selected
    }
    const res = await createAlbumShare({
      albumId: primaryAlbumId.value,
      albumIdList,
      shareTrack: form.value.shareTrack ? 1 : 0,
      mediaScope: form.value.mediaScope,
      selectMode,
      photoIdList,
      expireTime,
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
    await renderQr(qrCanvasRef, shareUrl.value)
    if (!isMulti.value) await loadShareList()
    proxy?.$modal?.msgSuccess?.(isMulti.value ? '已生成一条分享链接' : '分享链接已生成')
  } catch (e) {
    /* 全局已提示 */
  } finally {
    saving.value = false
  }
}

async function copyText(text) {
  if (!text) return
  try {
    if (navigator.clipboard?.writeText) await navigator.clipboard.writeText(text)
    else {
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

async function toggleShareStatus(item, enabled) {
  if (!item?.shareId || isExpired(item)) return
  togglingId.value = item.shareId
  try {
    await updateAlbumShare({
      shareId: item.shareId,
      shareTrack: item.shareTrack,
      mediaScope: item.mediaScope,
      selectMode: item.selectMode,
      photoIdList: item.photoIdList || [],
      expireTime: item.expireTime || null,
      status: enabled ? 1 : 0
    })
    item.status = enabled ? 1 : 0
    proxy?.$modal?.msgSuccess?.(enabled ? '已重新开启分享' : '已关闭分享，访客将无法打开')
  } catch (e) {
    /* 全局已提示 */
  } finally {
    togglingId.value = null
  }
}

async function removeShare(item) {
  if (!item?.shareId) return
  try {
    await proxy?.$modal?.confirm?.('删除后该分享链接将永久失效，确定删除？')
  } catch (e) {
    return
  }
  deletingId.value = item.shareId
  try {
    await delAlbumShare(item.shareId)
    shareList.value = shareList.value.filter(s => s.shareId !== item.shareId)
    if (managePreviewUrl.value === buildUrlForShare(item)) managePreviewUrl.value = ''
    proxy?.$modal?.msgSuccess?.('已删除分享')
  } catch (e) {
    /* 全局已提示 */
  } finally {
    deletingId.value = null
  }
}

async function showShareLink(item) {
  managePreviewUrl.value = buildUrlForShare(item)
  await renderQr(manageQrRef, managePreviewUrl.value)
}

async function openManage() {
  tab.value = 'manage'
  sharePath.value = ''
  shareUrl.value = ''
  managePreviewUrl.value = ''
  await loadShareList()
}

function resetForm() {
  form.value = { shareTrack: false, mediaScope: 'all', expirePreset: 'never', expireTime: '' }
  photos.value = []
  selectedIds.value = []
  sharePath.value = ''
  shareUrl.value = ''
  resultExpireText.value = ''
  managePreviewUrl.value = ''
  tab.value = 'create'
}

function backToSettings() {
  sharePath.value = ''
  shareUrl.value = ''
  resultExpireText.value = ''
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
        if (isMulti.value) {
          // 多选：不加载单相册文件与管理列表
        } else {
          await Promise.all([loadPhotos(), loadShareList()])
        }
      } finally {
        loading.value = false
      }
    } else {
      resetForm()
      shareList.value = []
    }
  }
)
</script>

<style scoped>
.share-body {
  min-height: 280px;
}

.share-tabs {
  display: inline-flex;
  gap: 4px;
  margin-bottom: 16px;
  padding: 3px;
  border-radius: 10px;
  background: #f3f4f6;
}

.share-tab {
  position: relative;
  border: 0;
  background: transparent;
  border-radius: 8px;
  padding: 7px 14px;
  font-size: 13px;
  color: #606266;
  cursor: pointer;
}

.share-tab.active {
  background: #fff;
  color: #303133;
  font-weight: 600;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}

.tab-count {
  margin-left: 4px;
  padding: 0 6px;
  border-radius: 999px;
  background: #e5e7eb;
  font-size: 11px;
}

.share-album-name {
  margin-bottom: 16px;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.share-album-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: -6px 0 16px;
  max-height: 96px;
  overflow: auto;
}

.share-album-chip {
  max-width: 160px;
  padding: 4px 10px;
  border-radius: 999px;
  background: #f3f4f6;
  color: #4b5563;
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.share-multi-hint {
  margin: 8px 0 0;
  padding: 12px 14px;
  border-radius: 8px;
  background: #f8fafc;
  color: #64748b;
  font-size: 13px;
  line-height: 1.5;
}

.share-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
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
  max-height: 300px;
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

.share-manage-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 360px;
  overflow: auto;
}

.share-manage-card {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  background: #fafbfc;
}

.manage-main {
  min-width: 0;
  flex: 1;
}

.manage-title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 6px;
}

.manage-scope {
  font-size: 12px;
  color: #606266;
}

.manage-meta {
  font-size: 12px;
  color: #909399;
  margin-bottom: 6px;
}

.manage-url {
  font-size: 12px;
  color: #409eff;
  word-break: break-all;
}

.manage-actions {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}

.manage-preview {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px dashed #e5e7eb;
}

@media (max-width: 720px) {
  .share-manage-card {
    flex-direction: column;
  }

  .manage-actions {
    width: 100%;
    flex-direction: row;
    flex-wrap: wrap;
    justify-content: flex-end;
  }
}
</style>
