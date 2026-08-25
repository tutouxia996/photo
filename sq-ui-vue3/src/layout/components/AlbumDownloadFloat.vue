<template>
  <div
    v-if="visible"
    class="album-download-float"
    :class="{ mini: minimized }"
    :style="{ bottom: floatBottom, right: floatRight }"
  >
    <div class="album-download-head">
      <span class="album-download-title">{{ titleText }}</span>
      <div class="album-download-head-actions">
        <button type="button" class="album-download-icon" @click="minimized = !minimized">
          {{ minimized ? '展开' : '收起' }}
        </button>
        <button
          v-if="canDismiss"
          type="button"
          class="album-download-icon"
          @click="onDismiss"
        >关闭</button>
      </div>
    </div>
    <template v-if="!minimized">
      <div class="album-download-row">
        <span>{{ progressText }}</span>
        <span>{{ percent }}%</span>
      </div>
      <el-progress
        :percentage="percent"
        :stroke-width="8"
        striped
        striped-flow
        :status="progress.status === 2 ? 'exception' : (progress.phase === 'done' ? 'success' : undefined)"
      />
      <div v-if="progress.mode === 'album'" class="album-download-stats">
        <span>已打包 {{ progress.packed || 0 }}/{{ progress.total || 0 }}</span>
        <span v-if="progress.skipped">跳过 {{ progress.skipped }}</span>
      </div>
      <div v-else class="album-download-stats">
        <span>成功 {{ progress.done || 0 }}/{{ progress.total || 0 }}</span>
        <span v-if="progress.failed">失败 {{ progress.failed }}</span>
      </div>
      <div
        v-if="progress.currentFile"
        class="album-download-current"
        :title="progress.currentFile"
      >当前：{{ progress.currentFile }}</div>
      <div class="album-download-msg" :title="progress.message">{{ progress.message || '准备中…' }}</div>
    </template>
    <div v-else class="album-download-mini">{{ percent }}% · {{ miniText }}</div>
  </div>
</template>

<script setup>
import useAlbumDownloadStore from '@/store/modules/albumDownload'
import useAliyunSyncStore from '@/store/modules/aliyunSync'
import useDiskScanStore from '@/store/modules/diskScan'
import useVideoProxyStore from '@/store/modules/videoProxy'
import usePhotoScoreStore from '@/store/modules/photoScore'

const store = useAlbumDownloadStore()
const aliyunStore = useAliyunSyncStore()
const diskScanStore = useDiskScanStore()
const videoProxyStore = useVideoProxyStore()
const photoScoreStore = usePhotoScoreStore()

const progress = computed(() => store.progress)
const visible = computed(() => store.visible)
const minimized = computed({
  get: () => store.minimized,
  set: (v) => { store.minimized = v }
})
const percent = computed(() => Math.min(100, Math.max(0, Number(progress.value.percent) || 0)))
const canDismiss = computed(() => !progress.value.running && !store.transferring && Number(progress.value.status) !== 0)

const floatBottom = computed(() => {
  let bottom = 20
  if (aliyunStore.visible) bottom += 170
  if (diskScanStore.visible) bottom += 170
  if (videoProxyStore.visible) bottom += 170
  return `${bottom}px`
})
const floatRight = computed(() => (photoScoreStore.visible ? '350px' : '20px'))

const titleText = computed(() => {
  const p = progress.value
  const name = p.albumName ? ` · ${p.albumName}` : ''
  if (p.mode === 'items') {
    if (p.running || Number(p.status) === 0) return '下载原图/原视频'
    if (Number(p.status) === 2) return '下载失败'
    return '下载完成'
  }
  if (p.phase === 'packing' || (p.running && p.phase !== 'transferring' && p.phase !== 'ready')) {
    return `打包相册${name}`
  }
  if (p.phase === 'ready' || p.phase === 'transferring') return `下载相册${name}`
  if (Number(p.status) === 2 || p.phase === 'failed') return `下载失败${name}`
  return `下载完成${name}`
})

const progressText = computed(() => {
  const p = progress.value
  if (p.mode === 'items') return `进度 ${p.done || 0}/${p.total || 0}`
  if (p.phase === 'transferring') return '传输到本地'
  if (p.phase === 'ready') return '打包完成'
  return `打包 ${p.packed || 0}/${p.total || 0}`
})

const miniText = computed(() => {
  const p = progress.value
  if (p.mode === 'items') return `${p.done || 0}/${p.total || 0}`
  return p.phase === 'transferring' ? '下载中' : `${p.packed || 0}/${p.total || 0}`
})

function onDismiss() {
  store.dismiss()
}

onMounted(() => {
  store.startPolling()
})
onUnmounted(() => {
  store.stopPolling()
})
</script>

<style scoped>
.album-download-float {
  position: fixed;
  z-index: 3000;
  width: 320px;
  padding: 12px 14px;
  background: #fff;
  border: 1px solid #dcdfe6;
  color: #303133;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
}
.album-download-float.mini {
  width: auto;
  min-width: 180px;
}
.album-download-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  gap: 8px;
}
.album-download-title {
  font-size: 13px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.album-download-head-actions {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}
.album-download-icon {
  border: 0;
  background: none;
  color: #909399;
  cursor: pointer;
  font-size: 12px;
}
.album-download-row,
.album-download-stats {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #606266;
  margin: 6px 0;
  gap: 8px;
}
.album-download-current,
.album-download-msg,
.album-download-mini {
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.album-download-current {
  color: #606266;
  margin-top: 4px;
}
.album-download-msg {
  margin-top: 8px;
  color: #606266;
}
.album-download-mini {
  color: #909399;
}
</style>
