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
      <el-form-item label="云盘相册名称" prop="remoteAlbumName">
        <el-input v-model="form.remoteAlbumName" placeholder="如：平时拍照" />
      </el-form-item>
      <el-form-item label="云盘相册 ID" prop="remoteAlbumId">
        <el-input v-model="form.remoteAlbumId" placeholder="可选，填写则优先于名称" />
      </el-form-item>

      <el-divider content-position="left">本机与入库</el-divider>
      <el-form-item label="本机下载目录" prop="localPath">
        <el-input v-model="form.localPath" placeholder="如 F:/照片视频备份/平时拍照" />
      </el-form-item>
      <el-form-item label="扫描目录" prop="scanPathId">
        <el-select v-model="form.scanPathId" clearable filterable placeholder="下载完成后触发扫描（可空，按路径匹配）" style="width: 100%">
          <el-option
            v-for="p in scanPaths"
            :key="p.pathId"
            :label="`${p.pathName}（ID ${p.pathId}） ${p.localPath || ''}`"
            :value="p.pathId"
          />
        </el-select>
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
        <span class="form-tip">当前进度 {{ sync.percent || 0 }}%，剩余 {{ sync.remaining || 0 }} 个未下载</span>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="saving" v-hasPermi="['album:aliyun:edit']" @click="submitForm">保存</el-button>
        <el-button v-hasPermi="['album:aliyun:run']" :disabled="sync.running && !sync.canResume" @click="handleRun">开始同步</el-button>
        <el-button v-if="sync.canPause" v-hasPermi="['album:aliyun:run']" @click="handlePause">暂停</el-button>
        <el-button v-if="sync.canResume" type="warning" v-hasPermi="['album:aliyun:run']" @click="handleResume">继续下载</el-button>
        <el-button @click="load">刷新</el-button>
        <span class="form-tip" v-if="form.source === 'yml'">尚未保存到库，当前显示的是配置文件默认值</span>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup name="AlbumAliyun">
import { getAliyunSetting, saveAliyunSetting } from '@/api/album/aliyun'
import { listScanPath } from '@/api/album/scan'
import useAliyunSyncStore from '@/store/modules/aliyunSync'

const { proxy } = getCurrentInstance()
const syncStore = useAliyunSyncStore()
const loading = ref(false)
const saving = ref(false)
const scanPaths = ref([])
const form = ref({
  enabled: false,
  refreshToken: '',
  hasRefreshToken: false,
  refreshTokenMasked: '',
  remoteAlbumName: '',
  remoteAlbumId: '',
  localPath: '',
  scanPathId: undefined,
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
  localPath: [{ required: true, message: '本机目录不能为空', trigger: 'blur' }]
}

const sync = computed(() => syncStore.progress)
const statusText = computed(() => {
  const st = Number(sync.value.status)
  if (sync.value.running || st === 0) return '下载中'
  if (st === 3) return '已暂停'
  if (st === 2) return '失败'
  if ((sync.value.remaining || 0) > 0) return '未完成'
  return '空闲'
})

function loadScanPaths() {
  listScanPath({ pageNum: 1, pageSize: 100 }).then(res => {
    scanPaths.value = res.rows || []
  }).catch(() => {})
}

function load() {
  loading.value = true
  getAliyunSetting().then(res => {
    const data = res.data || {}
    form.value = { ...form.value, ...data, refreshToken: '' }
  }).finally(() => { loading.value = false })
  syncStore.refresh()
}

function submitForm() {
  proxy.$refs.formRef.validate(valid => {
    if (!valid) return
    if (!form.value.remoteAlbumName && !form.value.remoteAlbumId) {
      proxy.$modal.msgError('请填写云盘相册名称或 ID')
      return
    }
    saving.value = true
    const payload = { ...form.value }
    if (!payload.refreshToken) {
      payload.refreshToken = ''
    }
    saveAliyunSetting(payload).then(() => {
      proxy.$modal.msgSuccess('已保存')
      load()
    }).finally(() => { saving.value = false })
  })
}

function handleRun() {
  if (sync.value.running) {
    proxy.$modal.msgWarning('下载进行中，可暂停或到右下角查看进度')
    return
  }
  proxy.$modal.confirm('将按已保存配置后台下载，可切换到其他页面，不影响其它功能。').then(() => {
    return syncStore.start()
  }).then(() => {
    proxy.$modal.msgSuccess('已开始下载，进度在右下角')
  }).catch(() => {})
}

function handlePause() {
  syncStore.pause().then(() => proxy.$modal.msgSuccess('已请求暂停，当前文件完成后停止'))
}

function handleResume() {
  syncStore.resume().then(() => proxy.$modal.msgSuccess('继续下载剩余文件'))
}

loadScanPaths()
load()
syncStore.startPolling()
</script>

<style scoped>
.form-tip {
  margin-left: 8px;
  color: #909399;
  font-size: 12px;
}
.mb8 {
  margin-bottom: 12px;
}
</style>
