<template>
  <div v-if="visible" class="aliyun-sync-float" :class="{ mini: minimized }">
    <div class="aliyun-sync-head">
      <span class="aliyun-sync-title">{{ titleText }}</span>
      <button type="button" class="aliyun-sync-icon" @click="minimized = !minimized">{{ minimized ? '展开' : '收起' }}</button>
    </div>
    <template v-if="!minimized">
      <div v-if="isMultiAlbum" class="aliyun-sync-album">
        <div class="album-line">相册 {{ progress.albumIndex || 0 }}/{{ progress.albumCount }}</div>
        <div v-if="progress.currentAlbumName" class="album-name" :title="progress.currentAlbumName">
          {{ progress.currentAlbumName }}
        </div>
      </div>
      <div class="aliyun-sync-row">
        <span>总进度 {{ progress.percent || 0 }}%</span>
        <span>累计剩余 {{ overallRemainingText }}</span>
      </div>
      <el-progress
        :percentage="Number(progress.percent) || 0"
        :stroke-width="8"
        striped
        :striped-flow="Number(progress.status) === 0 || progress.running"
        :status="progress.status === 2 ? 'exception' : (progress.status === 1 ? 'success' : undefined)"
      />
      <div class="aliyun-sync-stats">
        <span>累计下载 {{ overallDownloaded }}</span>
        <span>跳过 {{ overallSkipped }}</span>
        <span>失败 {{ overallFailed }}</span>
      </div>
      <div v-if="isMultiAlbum" class="aliyun-sync-substats">
        <span>本相册剩余 {{ progress.remaining || 0 }}</span>
        <span>待列举 {{ albumsPendingText }}</span>
      </div>
      <div v-if="failedFiles.length" class="aliyun-sync-failed">
        <div v-for="(f, i) in failedFiles" :key="i" class="aliyun-sync-name" :title="f.reason">失败：{{ f.name }}</div>
      </div>
      <div v-if="currentFiles.length" class="aliyun-sync-current">
        <div v-for="f in currentFiles" :key="f.name" class="aliyun-sync-file">
          <div class="aliyun-sync-name" :title="f.name">{{ f.name }}</div>
          <el-progress :percentage="Number(f.percent) || 0" :stroke-width="6" />
          <div class="aliyun-sync-bytes">{{ formatBytes(f.written) }} / {{ formatBytes(f.expected) }}</div>
        </div>
      </div>
      <div class="aliyun-sync-msg" :title="progress.message">{{ progress.message }}</div>
      <div class="aliyun-sync-actions">
        <el-button v-if="progress.canPause" size="small" @click="onPause">暂停</el-button>
        <el-button v-if="progress.canResume" size="small" type="primary" @click="onResume">继续</el-button>
      </div>
    </template>
    <div v-else class="aliyun-sync-mini">
      <template v-if="isMultiAlbum">{{ progress.albumIndex || 0 }}/{{ progress.albumCount }} · </template>
      剩余 {{ overallRemainingText }} · {{ progress.percent || 0 }}%
    </div>
  </div>
</template>

<script setup>
import useAliyunSyncStore from '@/store/modules/aliyunSync'

const store = useAliyunSyncStore()
const progress = computed(() => store.progress)
const visible = computed(() => store.visible)
const minimized = computed({
  get: () => store.minimized,
  set: (v) => { store.minimized = v }
})
const currentFiles = computed(() => progress.value.currentFiles || [])
const failedFiles = computed(() => (progress.value.failedFiles || []).slice(0, 5))
const isMultiAlbum = computed(() => Number(progress.value.albumCount) > 1)
const overallDownloaded = computed(() => {
  if (isMultiAlbum.value) return Number(progress.value.overallDownloaded) || 0
  return Number(progress.value.downloaded) || 0
})
const overallSkipped = computed(() => {
  if (isMultiAlbum.value) return Number(progress.value.overallSkipped) || 0
  return Number(progress.value.skipped) || 0
})
const overallFailed = computed(() => {
  if (isMultiAlbum.value) return Number(progress.value.overallFailed) || 0
  return Number(progress.value.failed) || 0
})
const overallRemainingText = computed(() => {
  if (isMultiAlbum.value) {
    const n = Number(progress.value.overallRemaining) || 0
    const pending = Number(progress.value.albumsPending) || 0
    return pending > 0 ? `${n}+` : String(n)
  }
  return String(Number(progress.value.remaining) || 0)
})
const albumsPendingText = computed(() => {
  const pending = Number(progress.value.albumsPending) || 0
  return pending > 0 ? `${pending} 个` : '无'
})
const titleText = computed(() => {
  const st = Number(progress.value.status)
  const prefix = isMultiAlbum.value
    ? `云盘下载 ${progress.value.albumIndex || 0}/${progress.value.albumCount}`
    : '云盘下载中'
  if (st === 0) return prefix
  if (st === 3) return isMultiAlbum.value ? `${prefix}（已暂停）` : '云盘下载已暂停'
  if (st === 2) return '云盘下载失败'
  return '云盘下载'
})

function formatBytes(n) {
  const v = Number(n) || 0
  if (v < 1024) return v + ' B'
  if (v < 1048576) return (v / 1024).toFixed(1) + ' KB'
  if (v < 1073741824) return (v / 1048576).toFixed(1) + ' MB'
  return (v / 1073741824).toFixed(2) + ' GB'
}

function onPause() {
  store.pause()
}
function onResume() {
  store.resume()
}

onMounted(() => {
  store.startPolling()
})
onUnmounted(() => {
  store.stopPolling()
})
</script>

<style scoped>
.aliyun-sync-float {
  position: fixed;
  right: 20px;
  bottom: 20px;
  z-index: 3000;
  width: 340px;
  padding: 12px 14px;
  background: #fff;
  border: 1px solid #dcdfe6;
  color: #303133;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  border-radius: 8px;
}
.aliyun-sync-float.mini {
  width: auto;
  min-width: 180px;
}
.aliyun-sync-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.aliyun-sync-title {
  font-size: 13px;
  font-weight: 600;
}
.aliyun-sync-icon {
  border: 0;
  background: none;
  color: #909399;
  cursor: pointer;
  font-size: 12px;
}
.aliyun-sync-album {
  margin-bottom: 6px;
}
.album-line {
  font-size: 12px;
  color: #606266;
}
.album-name {
  font-size: 12px;
  color: #303133;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-top: 2px;
}
.aliyun-sync-row,
.aliyun-sync-stats,
.aliyun-sync-substats {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #606266;
  margin: 6px 0;
}
.aliyun-sync-substats {
  color: #909399;
  font-size: 11px;
}
.aliyun-sync-failed {
  margin-top: 8px;
  color: #f56c6c;
  font-size: 12px;
}
.aliyun-sync-file + .aliyun-sync-file {
  margin-top: 8px;
}
.aliyun-sync-name,
.aliyun-sync-msg {
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.aliyun-sync-bytes,
.aliyun-sync-mini {
  font-size: 12px;
  color: #909399;
}
.aliyun-sync-msg {
  margin-top: 8px;
  color: #606266;
}
.aliyun-sync-actions {
  margin-top: 10px;
}
</style>
