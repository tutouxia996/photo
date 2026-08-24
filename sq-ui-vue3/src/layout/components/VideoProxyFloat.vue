<template>
  <div
    v-if="visible"
    class="video-proxy-float"
    :class="{ mini: minimized }"
    :style="{ bottom: floatBottom, right: floatRight }"
  >
    <div class="video-proxy-head">
      <span class="video-proxy-title">{{ titleText }}</span>
      <div class="video-proxy-head-actions">
        <button type="button" class="video-proxy-icon" @click="minimized = !minimized">
          {{ minimized ? '展开' : '收起' }}
        </button>
        <button
          v-if="canDismiss"
          type="button"
          class="video-proxy-icon"
          @click="onDismiss"
        >关闭</button>
      </div>
    </div>
    <template v-if="!minimized">
      <div class="video-proxy-row">
        <span>进度 {{ progress.percent || 0 }}%</span>
        <span>剩余 {{ progress.remaining || 0 }} 个</span>
      </div>
      <el-progress
        v-if="Number(progress.total) > 0 || progress.running"
        :percentage="Number(progress.percent) || 0"
        :stroke-width="8"
        :status="progress.status === 2 ? 'exception' : (progress.status === 1 && !progress.running ? 'success' : undefined)"
      />
      <div class="video-proxy-stats">
        <span v-if="Number(progress.total) > 0">完成 {{ progress.done || 0 }}/{{ progress.total || 0 }}</span>
        <span v-if="progress.skippedVideos">跳过 {{ progress.skippedVideos }}</span>
        <span>转码中 {{ progress.generating || 0 }}</span>
        <span v-if="progress.failed">失败 {{ progress.failed }}</span>
      </div>
      <div v-if="currentJobs.length" class="video-proxy-current">
        <div v-for="(j, i) in currentJobs" :key="i" class="video-proxy-name" :title="j.message">
          {{ j.fileName }} · {{ j.variant }}
        </div>
      </div>
      <div v-if="failedJobs.length" class="video-proxy-failed">
        <div v-for="(j, i) in failedJobs" :key="i" class="video-proxy-fail-item">
          <div class="video-proxy-name">失败：{{ j.fileName }} · {{ j.variant }}</div>
          <div v-if="j.message" class="video-proxy-fail-msg" :title="j.message">{{ j.message }}</div>
        </div>
      </div>
      <div class="video-proxy-msg" :title="progress.message">{{ progress.message }}</div>
    </template>
    <div v-else class="video-proxy-mini">
      {{ progress.percent || 0 }}% · 剩 {{ progress.remaining || 0 }}
    </div>
  </div>
</template>

<script setup>
import useVideoProxyStore from '@/store/modules/videoProxy'
import useAliyunSyncStore from '@/store/modules/aliyunSync'
import useDiskScanStore from '@/store/modules/diskScan'
import usePhotoScoreStore from '@/store/modules/photoScore'

const store = useVideoProxyStore()
const aliyunStore = useAliyunSyncStore()
const diskScanStore = useDiskScanStore()
const photoScoreStore = usePhotoScoreStore()
const progress = computed(() => store.progress)
const visible = computed(() => store.visible)
const minimized = computed({
  get: () => store.minimized,
  set: (v) => { store.minimized = v }
})
const currentJobs = computed(() => progress.value.currentJobs || [])
const failedJobs = computed(() => (progress.value.failedJobs || []).slice(0, 5))
const canDismiss = computed(() => !progress.value.running && Number(progress.value.status) !== 0)

const floatBottom = computed(() => {
  let bottom = 20
  if (aliyunStore.visible) bottom += 170
  if (diskScanStore.visible) bottom += 170
  return `${bottom}px`
})
const floatRight = computed(() => {
  if (photoScoreStore.visible && aliyunStore.visible) return '350px'
  if (photoScoreStore.visible) return '350px'
  return '20px'
})

const titleText = computed(() => {
  const st = Number(progress.value.status)
  const total = Number(progress.value.total) || 0
  if (progress.value.running || st === 0) return '视频转码中'
  if (total === 0) return '视频转码'
  if (st === 2) return '视频转码（有失败）'
  return '视频转码完成'
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
.video-proxy-float {
  position: fixed;
  z-index: 3000;
  width: 320px;
  padding: 12px 14px;
  background: #fff;
  border: 1px solid #dcdfe6;
  color: #303133;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
}
.video-proxy-float.mini {
  width: auto;
  min-width: 160px;
}
.video-proxy-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  gap: 8px;
}
.video-proxy-title {
  font-size: 13px;
  font-weight: 600;
}
.video-proxy-head-actions {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}
.video-proxy-icon {
  border: 0;
  background: none;
  color: #909399;
  cursor: pointer;
  font-size: 12px;
}
.video-proxy-row,
.video-proxy-stats {
  display: flex;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 4px 8px;
  font-size: 12px;
  color: #606266;
  margin: 6px 0;
}
.video-proxy-current {
  margin-top: 6px;
  font-size: 12px;
  color: #606266;
}
.video-proxy-failed {
  margin-top: 6px;
  font-size: 12px;
  color: #f56c6c;
}
.video-proxy-fail-item + .video-proxy-fail-item {
  margin-top: 6px;
}
.video-proxy-fail-msg {
  margin-top: 2px;
  font-size: 11px;
  color: #909399;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.video-proxy-name,
.video-proxy-msg,
.video-proxy-mini {
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.video-proxy-msg {
  margin-top: 8px;
  color: #606266;
}
.video-proxy-mini {
  color: #909399;
}
</style>
