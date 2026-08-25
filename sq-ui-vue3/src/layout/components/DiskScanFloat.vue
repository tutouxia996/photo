<template>
  <div
    v-if="visible"
    class="disk-scan-float"
    :class="{ mini: minimized }"
    :style="{ bottom: floatBottom, right: floatRight }"
  >
    <div class="disk-scan-head">
      <span class="disk-scan-title">{{ titleText }}</span>
      <div class="disk-scan-head-actions">
        <button type="button" class="disk-scan-icon" @click="minimized = !minimized">
          {{ minimized ? '展开' : '收起' }}
        </button>
        <button
          v-if="canDismiss"
          type="button"
          class="disk-scan-icon"
          @click="onDismiss"
        >关闭</button>
      </div>
    </div>
    <template v-if="!minimized">
      <div class="disk-scan-row">
        <span>{{ processedText }}</span>
        <span>{{ percent }}%</span>
      </div>
      <el-progress
        :percentage="percent"
        :stroke-width="8"
        striped
        striped-flow
        :status="progress.status === 2 ? 'exception' : (progress.status === 1 && !progress.running ? 'success' : undefined)"
      />
      <div class="disk-scan-stats">
        <span>新增 {{ progress.newCount || 0 }}</span>
        <span>跳过 {{ progress.skipCount || 0 }}</span>
        <span>失败 {{ progress.failCount || 0 }}</span>
      </div>
      <div class="disk-scan-msg" :title="progress.message">{{ progress.message || '准备中…' }}</div>
      <div v-if="proxyHint" class="disk-scan-proxy-hint">{{ proxyHint }}</div>
    </template>
    <div v-else class="disk-scan-mini">{{ percent }}% · {{ processedText }}</div>
  </div>
</template>

<script setup>
import useDiskScanStore from '@/store/modules/diskScan'
import useAliyunSyncStore from '@/store/modules/aliyunSync'
import useVideoProxyStore from '@/store/modules/videoProxy'
import usePhotoScoreStore from '@/store/modules/photoScore'

const store = useDiskScanStore()
const aliyunStore = useAliyunSyncStore()
const videoProxyStore = useVideoProxyStore()
const photoScoreStore = usePhotoScoreStore()

const progress = computed(() => store.progress)
const visible = computed(() => store.visible)
const percent = computed(() => store.percent)
const minimized = computed({
  get: () => store.minimized,
  set: (v) => { store.minimized = v }
})

const processedText = computed(() => {
  const total = Number(progress.value.totalCount) || 0
  const processed = (Number(progress.value.newCount) || 0)
    + (Number(progress.value.skipCount) || 0)
    + (Number(progress.value.failCount) || 0)
  if (!total && progress.value.running) return '统计文件中…'
  return `已处理 ${processed}/${total || processed}`
})

const canDismiss = computed(() => !progress.value.running && Number(progress.value.status) !== 0)

const proxyHint = computed(() => {
  if (videoProxyStore.visible && (videoProxyStore.progress.running || Number(videoProxyStore.progress.status) === 0)) {
    const p = videoProxyStore.progress
    const pct = Number(p.percent) || 0
    const rem = Number(p.remaining) || 0
    return `视频转码进行中 ${pct}%${rem ? `，剩余 ${rem}` : ''}（详见下方浮层）`
  }
  const msg = String(progress.value.message || '')
  if (!progress.value.running && /转码|浏览档/i.test(msg)) {
    return '已触发视频转码，进度见右下角转码浮层'
  }
  return ''
})

const floatBottom = computed(() => {
  let bottom = 20
  if (aliyunStore.visible) bottom += 170
  if (videoProxyStore.visible) bottom += 170
  return `${bottom}px`
})

const floatRight = computed(() => {
  if (photoScoreStore.visible) return '350px'
  return '20px'
})

const titleText = computed(() => {
  if (progress.value.running || Number(progress.value.status) === 0) {
    return progress.value.pathName ? `扫描：${progress.value.pathName}` : '磁盘扫描中'
  }
  if (Number(progress.value.status) === 2) return '磁盘扫描失败'
  return '磁盘扫描完成'
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
.disk-scan-float {
  position: fixed;
  z-index: 3000;
  width: 320px;
  padding: 12px 14px;
  background: #fff;
  border: 1px solid #dcdfe6;
  color: #303133;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
}
.disk-scan-float.mini {
  width: auto;
  min-width: 180px;
}
.disk-scan-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  gap: 8px;
}
.disk-scan-title {
  font-size: 13px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.disk-scan-head-actions {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}
.disk-scan-icon {
  border: 0;
  background: none;
  color: #909399;
  cursor: pointer;
  font-size: 12px;
}
.disk-scan-row,
.disk-scan-stats {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #606266;
  margin: 6px 0;
}
.disk-scan-msg,
.disk-scan-mini {
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.disk-scan-msg {
  margin-top: 8px;
  color: #606266;
}
.disk-scan-proxy-hint {
  margin-top: 6px;
  font-size: 12px;
  color: #409eff;
  line-height: 1.4;
}
.disk-scan-mini {
  color: #909399;
}
</style>
