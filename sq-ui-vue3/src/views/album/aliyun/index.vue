<template>
  <div class="app-container">
    <el-alert
      class="mb8"
      type="info"
      :closable="false"
      title="在此保存后立即生效，不必再改 application-local.yml。refresh_token 只回显脱敏值，留空保存表示不改动。"
    />

    <el-form
      v-loading="loading"
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="140px"
      style="max-width: 760px"
    >
      <el-divider content-position="left">开关与相册</el-divider>
      <el-form-item label="启用同步" prop="enabled">
        <el-switch v-model="form.enabled" />
        <span class="form-tip">关闭后定时任务会直接跳过</span>
      </el-form-item>
      <el-form-item label="refresh_token" prop="refreshToken">
        <el-input
          v-model="form.refreshToken"
          type="password"
          show-password
          placeholder="粘贴新 token；留空则保持原值"
          autocomplete="new-password"
        />
        <div class="form-tip" v-if="form.hasRefreshToken">当前已保存：{{ form.refreshTokenMasked }}</div>
        <div class="form-tip">浏览器登录 alipan.com → F12 → Application → Local Storage → token → refresh_token</div>
      </el-form-item>
      <el-form-item label="云盘相册" prop="selectedRemoteAlbumIds">
        <div class="album-row">
          <el-select
            v-model="selectedRemoteAlbumIds"
            multiple
            collapse-tags
            collapse-tags-tooltip
            clearable
            filterable
            placeholder="先刷新列表，可多选云盘相册"
            style="flex: 1"
            @change="onCloudAlbumsChange"
          >
            <el-option
              v-for="item in cloudAlbums"
              :key="item.albumId"
              :label="item.name"
              :value="item.albumId"
            />
          </el-select>
          <el-button :loading="loadingAlbums" @click="loadCloudAlbums">刷新列表</el-button>
        </div>
        <div class="form-tip" v-if="selectedRemoteAlbums.length">
          已选 {{ selectedRemoteAlbums.length }} 个：{{ selectedRemoteAlbums.map(a => a.name).join('、') }}
        </div>
      </el-form-item>

      <el-divider content-position="left">本机与入库</el-divider>
      <el-form-item label="本机父目录" prop="localBasePath">
        <el-input v-model="form.localBasePath" placeholder="如 F:/照片视频备份" />
        <div v-if="downloadPathPreview.length" class="path-preview">
          <div class="path-preview-title">各相册下载到：</div>
          <div v-for="p in downloadPathPreview" :key="p.albumId" class="path-preview-item">
            <span class="path-name">{{ p.name }}</span>
            <code>{{ p.path }}</code>
          </div>
        </div>
        <div v-else class="form-tip">选择云盘相册并填写父目录后，将显示各相册的完整下载路径</div>
      </el-form-item>
      <el-form-item v-if="selectedRemoteAlbums.length <= 1" label="入库相册" prop="bindAlbumId">
        <el-select
          v-model="form.bindAlbumId"
          clearable
          filterable
          placeholder="可选；留空则自动用云盘相册名创建/匹配本地相册"
          style="width: 100%"
        >
          <el-option
            v-for="a in localAlbums"
            :key="a.albumId"
            :label="`${a.albumName || '未命名'}（ID ${a.albumId}）`"
            :value="a.albumId"
          />
        </el-select>
        <div class="form-tip">
          单相册时可选手动指定；多相册时每个云盘相册名会自动对应一个本地相册。
        </div>
      </el-form-item>
      <el-form-item v-else label="入库相册">
        <span class="form-tip block-tip">已选 {{ selectedRemoteAlbums.length }} 个云盘相册，将分别按相册名自动创建/匹配本地相册并扫描入库。</span>
      </el-form-item>
      <el-form-item label="下载后扫描">
        <el-switch v-model="form.triggerScan" />
      </el-form-item>
      <el-form-item label="全量扫描">
        <el-switch v-model="form.fullScan" />
        <span class="form-tip">默认增量；全量会更慢</span>
      </el-form-item>
      <el-form-item label="仅图片/视频">
        <el-switch v-model="form.mediaOnly" />
        <span class="form-tip">关闭=相册内全部原文件（推荐）</span>
      </el-form-item>
      <el-form-item label="token 文件" prop="tokenFile">
        <el-input v-model="form.tokenFile" placeholder="D:/uploadPath/album/aliyun-drive-token.json" />
      </el-form-item>

      <el-divider content-position="left">下载速度</el-divider>
      <el-form-item label="同时下载文件数" prop="downloadConcurrency">
        <el-input-number v-model="form.downloadConcurrency" :min="1" :max="8" />
        <span class="form-tip">建议 2～3</span>
      </el-form-item>
      <el-form-item label="单文件分片数" prop="chunkConcurrency">
        <el-input-number v-model="form.chunkConcurrency" :min="1" :max="16" />
        <span class="form-tip">大文件 Range 并行，建议 8</span>
      </el-form-item>
      <el-form-item label="分片阈值(字节)" prop="multipartMinBytes">
        <el-input-number v-model="form.multipartMinBytes" :min="0" :step="1048576" />
      </el-form-item>
      <el-form-item label="下载 Referer" prop="downloadReferer">
        <el-input v-model="form.downloadReferer" placeholder="https://www.aliyundrive.com/" />
      </el-form-item>

      <el-divider content-position="left">下载进度</el-divider>
      <el-form-item label="状态">
        <span>{{ statusText }}</span>
        <span class="form-tip" v-if="isMultiAlbum">
          相册 {{ sync.albumIndex || 0 }}/{{ sync.albumCount || 0 }}
          <template v-if="sync.currentAlbumName"> · {{ sync.currentAlbumName }}</template>
        </span>
      </el-form-item>
      <el-form-item label="进度">
        <div class="sync-progress-panel">
          <div class="sync-progress-row">
            <span>{{ progressLabel }}</span>
            <span>{{ displayPercent }}%</span>
          </div>
          <el-progress
            :percentage="displayPercent"
            :stroke-width="10"
            striped
            :striped-flow="syncActive"
            :indeterminate="listingPhase"
            :status="progressBarStatus"
          />
          <div class="sync-progress-stats">
            <span>已下载 {{ overallDownloaded }}</span>
            <span>跳过 {{ overallSkipped }}</span>
            <span>失败 {{ overallFailed }}</span>
            <span>剩余 {{ overallRemainingText }}</span>
          </div>
          <div v-if="currentFiles.length" class="sync-current-files">
            <div v-for="f in currentFiles" :key="f.name" class="sync-current-file">
              <div class="sync-file-name" :title="f.name">{{ f.name }}</div>
              <el-progress :percentage="Number(f.percent) || 0" :stroke-width="6" />
              <div class="sync-file-bytes">{{ formatBytes(f.written) }} / {{ formatBytes(f.expected) }}</div>
            </div>
          </div>
          <div class="sync-progress-msg" :title="sync.message">{{ sync.message || (syncActive ? '准备中…' : '暂无任务') }}</div>
        </div>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="saving" v-hasPermi="['album:aliyun:edit']" @click="submitForm">保存</el-button>
        <el-button
          v-hasPermi="['album:aliyun:run']"
          type="success"
          :disabled="sync.running && !sync.canResume"
          @click="handleRun"
        >开始同步</el-button>
        <el-button v-if="sync.canPause" v-hasPermi="['album:aliyun:run']" @click="handlePause">暂停</el-button>
        <el-button v-if="sync.canResume" type="warning" v-hasPermi="['album:aliyun:run']" @click="handleResume">继续下载</el-button>
        <el-button @click="load">刷新</el-button>
        <span class="form-tip" v-if="form.source === 'yml'">尚未保存到库，当前显示的是配置文件默认值</span>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup name="AlbumAliyun">
import { getAliyunSetting, saveAliyunSetting, listAliyunAlbums } from '@/api/album/aliyun'
import { listAlbum } from '@/api/album/album'
import useAliyunSyncStore from '@/store/modules/aliyunSync'

const { proxy } = getCurrentInstance()
const syncStore = useAliyunSyncStore()
const loading = ref(false)
const saving = ref(false)
const loadingAlbums = ref(false)
const cloudAlbums = ref([])
const localAlbums = ref([])
const selectedRemoteAlbumIds = ref([])
const form = ref({
  enabled: false,
  refreshToken: '',
  hasRefreshToken: false,
  refreshTokenMasked: '',
  remoteAlbumName: '',
  remoteAlbumId: '',
  remoteAlbums: [],
  localPath: '',
  localBasePath: '',
  bindAlbumId: undefined,
  triggerScan: true,
  fullScan: false,
  tokenFile: '',
  mediaOnly: false,
  downloadConcurrency: 2,
  chunkConcurrency: 8,
  multipartMinBytes: 2097152,
  downloadReferer: 'https://www.aliyundrive.com/',
  source: 'yml',
  running: false
})
const rules = {
  selectedRemoteAlbumIds: [{
    validator: (_rule, _value, callback) => {
      if (!selectedRemoteAlbumIds.value.length) {
        callback(new Error('请至少选择一个云盘相册'))
      } else {
        callback()
      }
    },
    trigger: 'change'
  }],
  localBasePath: [{ required: true, message: '本机父目录不能为空', trigger: 'blur' }]
}

const sync = computed(() => syncStore.progress)
const syncActive = computed(() => {
  const st = Number(sync.value.status)
  return !!(sync.value.running || st === 0)
})
const listingPhase = computed(() => {
  const phase = sync.value.phase || ''
  return syncActive.value && (phase === 'listing' || phase === 'scanning')
    && !(sync.value.currentFiles || []).length
})
const isMultiAlbum = computed(() => Number(sync.value.albumCount) > 1)
const currentFiles = computed(() => sync.value.currentFiles || [])
const displayPercent = computed(() => Math.min(100, Math.max(0, Number(sync.value.percent) || 0)))
const progressBarStatus = computed(() => {
  const st = Number(sync.value.status)
  if (st === 2) return 'exception'
  if (st === 1 && !syncActive.value) return 'success'
  return undefined
})
const overallDownloaded = computed(() => {
  if (isMultiAlbum.value) return Number(sync.value.overallDownloaded) || 0
  return Number(sync.value.downloaded) || 0
})
const overallSkipped = computed(() => {
  if (isMultiAlbum.value) return Number(sync.value.overallSkipped) || 0
  return Number(sync.value.skipped) || 0
})
const overallFailed = computed(() => {
  if (isMultiAlbum.value) return Number(sync.value.overallFailed) || 0
  return Number(sync.value.failed) || 0
})
const overallRemainingText = computed(() => {
  if (isMultiAlbum.value) {
    const n = Number(sync.value.overallRemaining) || 0
    const pending = Number(sync.value.albumsPending) || 0
    return pending > 0 ? `${n}+` : String(n)
  }
  return String(Number(sync.value.remaining) || 0)
})
const progressLabel = computed(() => {
  if (listingPhase.value) {
    return sync.value.phase === 'scanning' ? '扫描入库中' : '列举云盘文件中'
  }
  if (isMultiAlbum.value) {
    return `总进度 · 本相册剩余 ${sync.value.remaining || 0}`
  }
  const need = Number(sync.value.needDownload) || 0
  const done = overallDownloaded.value + overallFailed.value
  return need > 0 ? `下载进度 ${done}/${need}` : `已下载 ${overallDownloaded.value}`
})
const statusText = computed(() => {
  const st = Number(sync.value.status)
  const phase = sync.value.phase || ''
  if (sync.value.running || st === 0) {
    if (phase === 'listing') return '列举文件中'
    if (phase === 'scanning') return '扫描入库中'
    if (phase === 'downloading') return '下载中'
    return '同步中'
  }
  if (st === 3) return '已暂停'
  if (st === 2) return '失败'
  if ((sync.value.remaining || 0) > 0 || (sync.value.overallRemaining || 0) > 0) return '未完成'
  if (st === 1) return '已完成'
  return '空闲'
})

function formatBytes(n) {
  const v = Number(n) || 0
  if (v < 1024) return v + ' B'
  if (v < 1048576) return (v / 1024).toFixed(1) + ' KB'
  if (v < 1073741824) return (v / 1048576).toFixed(1) + ' MB'
  return (v / 1073741824).toFixed(2) + ' GB'
}

const selectedRemoteAlbums = computed(() => {
  const ids = selectedRemoteAlbumIds.value || []
  return ids.map(id => {
    const hit = cloudAlbums.value.find(a => a.albumId === id)
    return hit || { albumId: id, name: id }
  })
})

const downloadPathPreview = computed(() => {
  const base = String(form.value.localBasePath || '').trim().replace(/[\\/]+$/, '')
  if (!base) return []
  return selectedRemoteAlbums.value.map(a => ({
    albumId: a.albumId,
    name: a.name,
    path: composeLocalPath(base, a.name)
  }))
})

function sanitizeFolderName(name) {
  return String(name || '').trim().replace(/[\\/:*?"<>|]/g, '_')
}

function composeLocalPath(base, albumName) {
  const b = String(base || '').trim().replace(/[\\/]+$/, '')
  const name = sanitizeFolderName(albumName)
  if (!b || !name) return b || name
  return `${b}/${name}`
}

function syncFormRemoteAlbums() {
  const albums = selectedRemoteAlbums.value.map(a => ({
    albumId: a.albumId,
    name: a.name
  }))
  form.value.remoteAlbums = albums
  const first = albums[0]
  form.value.remoteAlbumId = first?.albumId || ''
  form.value.remoteAlbumName = first?.name || ''
}

function onCloudAlbumsChange() {
  syncFormRemoteAlbums()
  if (selectedRemoteAlbums.value.length === 1) {
    const name = selectedRemoteAlbums.value[0]?.name
    if (name && !form.value.bindAlbumId) {
      const matched = localAlbums.value.find(a => (a.albumName || '').trim() === name.trim())
      if (matched) form.value.bindAlbumId = matched.albumId
    }
  } else {
    form.value.bindAlbumId = undefined
  }
}

function loadLocalAlbums() {
  listAlbum({ pageNum: 1, pageSize: 500 }).then(res => {
    localAlbums.value = res.rows || []
  }).catch(() => {})
}

function loadCloudAlbums() {
  loadingAlbums.value = true
  const token = (form.value.refreshToken || '').trim()
  listAliyunAlbums(token || undefined).then(res => {
    cloudAlbums.value = res.data || []
    if (!cloudAlbums.value.length) {
      proxy.$modal.msgWarning('未获取到云盘相册，请确认 refresh_token 有效')
    }
    mergeSavedAlbumsIntoOptions()
    onCloudAlbumsChange()
  }).finally(() => { loadingAlbums.value = false })
}

function mergeSavedAlbumsIntoOptions() {
  const saved = form.value.remoteAlbums || []
  const map = new Map(cloudAlbums.value.map(a => [a.albumId, a]))
  saved.forEach(a => {
    if (a?.albumId && !map.has(a.albumId)) {
      cloudAlbums.value.push({ albumId: a.albumId, name: a.name || a.albumId })
    }
  })
}

function applyLoadedAlbums(data) {
  const albums = Array.isArray(data.remoteAlbums) && data.remoteAlbums.length
    ? data.remoteAlbums
    : (data.remoteAlbumId
      ? [{ albumId: data.remoteAlbumId, name: data.remoteAlbumName || data.remoteAlbumId }]
      : [])
  form.value.remoteAlbums = albums
  selectedRemoteAlbumIds.value = albums.map(a => a.albumId).filter(Boolean)
  mergeSavedAlbumsIntoOptions()
  onCloudAlbumsChange()
}

function load() {
  loading.value = true
  getAliyunSetting().then(res => {
    const data = res.data || {}
    form.value = { ...form.value, ...data, refreshToken: '' }
    applyLoadedAlbums(data)
  }).finally(() => { loading.value = false })
  syncStore.refresh()
}

function submitForm() {
  syncFormRemoteAlbums()
  proxy.$refs.formRef.validate(valid => {
    if (!valid) return
    if (!selectedRemoteAlbumIds.value.length) {
      proxy.$modal.msgError('请至少选择一个云盘相册')
      return
    }
    const multi = selectedRemoteAlbums.value.length > 1
    const willAutoCreate = form.value.triggerScan && (multi || !form.value.bindAlbumId)
    saving.value = true
    const payload = {
      ...form.value,
      remoteAlbums: selectedRemoteAlbums.value.map(a => ({ albumId: a.albumId, name: a.name })),
      localPath: String(form.value.localBasePath || '').trim().replace(/[\\/]+$/, '')
    }
    delete payload.scanPathId
    delete payload.linkedScanPathName
    delete payload.linkedScanPathLocal
    if (!payload.refreshToken) payload.refreshToken = ''
    if (multi) payload.bindAlbumId = undefined

    saveAliyunSetting(payload).then(() => {
      const names = selectedRemoteAlbums.value.map(a => a.name).join('、')
      proxy.$modal.msgSuccess(willAutoCreate
        ? `已保存，将同步 ${selectedRemoteAlbums.value.length} 个相册：${names}`
        : '已保存')
      loadLocalAlbums()
      load()
    }).finally(() => { saving.value = false })
  })
}

function handleRun() {
  if (sync.value.running) {
    proxy.$modal.msgWarning('下载进行中，可暂停或到右下角查看进度')
    return
  }
  proxy.$modal.confirm(
    `将按已保存配置顺序下载 ${selectedRemoteAlbums.value.length || form.value.remoteAlbums?.length || 1} 个云盘相册，可切换到其他页面。`
  ).then(() => syncStore.start()).then(() => {
    proxy.$modal.msgSuccess('已开始下载，进度在右下角')
  }).catch(() => {})
}

function handlePause() {
  syncStore.pause().then(() => proxy.$modal.msgSuccess('已请求暂停，当前文件完成后停止'))
}

function handleResume() {
  syncStore.resume().then(() => proxy.$modal.msgSuccess('继续下载剩余文件'))
}

loadLocalAlbums()
load()
syncStore.startPolling()
</script>

<style scoped>
.form-tip {
  margin-left: 8px;
  color: #909399;
  font-size: 12px;
}
.form-tip code {
  color: #409eff;
  font-size: 12px;
}
.block-tip {
  display: block;
  margin-left: 0;
  line-height: 1.5;
}
.mb8 {
  margin-bottom: 12px;
}
.album-row {
  display: flex;
  gap: 8px;
  width: 100%;
}
.path-preview {
  margin-top: 8px;
  padding: 8px 10px;
  background: #f5f7fa;
  border-radius: 4px;
  font-size: 12px;
}
.path-preview-title {
  color: #606266;
  margin-bottom: 6px;
}
.path-preview-item {
  display: flex;
  gap: 8px;
  align-items: baseline;
  line-height: 1.6;
}
.path-preview-item + .path-preview-item {
  margin-top: 4px;
}
.path-name {
  flex: 0 0 auto;
  color: #303133;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.path-preview-item code {
  color: #409eff;
  word-break: break-all;
}
.sync-progress-panel {
  width: 100%;
  max-width: 520px;
  padding: 12px 14px;
  background: #f5f7fa;
  border-radius: 6px;
}
.sync-progress-row,
.sync-progress-stats {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-size: 12px;
  color: #606266;
  margin-bottom: 8px;
}
.sync-progress-stats {
  flex-wrap: wrap;
  margin-top: 8px;
  margin-bottom: 0;
}
.sync-current-files {
  margin-top: 10px;
}
.sync-current-file + .sync-current-file {
  margin-top: 8px;
}
.sync-file-name,
.sync-progress-msg {
  font-size: 12px;
  color: #303133;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.sync-file-bytes {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}
.sync-progress-msg {
  margin-top: 10px;
  color: #606266;
}
</style>
