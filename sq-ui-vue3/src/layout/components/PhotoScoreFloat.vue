<template>
  <div
    v-if="visible"
    class="photo-score-float"
    :class="{ mini: minimized }"
    :style="{ right: floatRight }"
  >
    <div class="photo-score-head">
      <span class="photo-score-title">{{ titleText }}</span>
      <div class="photo-score-head-actions">
        <button type="button" class="photo-score-icon" @click="minimized = !minimized">
          {{ minimized ? '展开' : '收起' }}
        </button>
        <button
          v-if="canDismiss"
          type="button"
          class="photo-score-icon"
          @click="onDismiss"
        >关闭</button>
      </div>
    </div>
    <template v-if="!minimized">
      <div class="photo-score-row">
        <span>进度 {{ progress.percent || 0 }}%</span>
        <span>剩余 {{ progress.remaining || 0 }} 张</span>
      </div>
      <el-progress
        :percentage="Number(progress.percent) || 0"
        :stroke-width="8"
        :status="progress.status === 2 ? 'exception' : (progress.status === 1 && !progress.running ? 'success' : undefined)"
      />
      <div class="photo-score-stats">
        <span>已打 {{ progress.done || 0 }}</span>
        <span>合格 {{ progress.passed || 0 }}</span>
        <span>不合格 {{ progress.failed || 0 }}</span>
        <span v-if="progress.skipped">跳过 {{ progress.skipped }}</span>
      </div>
      <div v-if="progress.currentFile" class="photo-score-current" :title="progress.currentFile">
        当前：{{ progress.currentFile }}
      </div>
      <div class="photo-score-msg" :title="progress.message">{{ progress.message }}</div>
    </template>
    <div v-else class="photo-score-mini">
      {{ progress.percent || 0 }}% · 剩 {{ progress.remaining || 0 }}
    </div>
  </div>
</template>

<script setup>
import usePhotoScoreStore from '@/store/modules/photoScore'
import useAliyunSyncStore from '@/store/modules/aliyunSync'

const store = usePhotoScoreStore()
const aliyunStore = useAliyunSyncStore()
const progress = computed(() => store.progress)
const visible = computed(() => store.visible)
const minimized = computed({
  get: () => store.minimized,
  set: (v) => { store.minimized = v }
})
const floatRight = computed(() => (aliyunStore.visible ? '350px' : '20px'))
const canDismiss = computed(() => !progress.value.running && Number(progress.value.status) !== 0)

const titleText = computed(() => {
  const st = Number(progress.value.status)
  const name = progress.value.albumName ? ` · ${progress.value.albumName}` : ''
  if (st === 0 || progress.value.running) return `质量打分中${name}`
  if (st === 2) return `打分失败${name}`
  return `打分完成${name}`
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
.photo-score-float {
  position: fixed;
  bottom: 20px;
  z-index: 3000;
  width: 320px;
  padding: 12px 14px;
  background: #fff;
  border: 1px solid #dcdfe6;
  color: #303133;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
}
.photo-score-float.mini {
  width: auto;
  min-width: 160px;
}
.photo-score-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  gap: 8px;
}
.photo-score-title {
  font-size: 13px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.photo-score-head-actions {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}
.photo-score-icon {
  border: 0;
  background: none;
  color: #909399;
  cursor: pointer;
  font-size: 12px;
}
.photo-score-row,
.photo-score-stats {
  display: flex;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 4px 8px;
  font-size: 12px;
  color: #606266;
  margin: 6px 0;
}
.photo-score-current,
.photo-score-msg,
.photo-score-mini {
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.photo-score-msg {
  margin-top: 8px;
  color: #606266;
}
.photo-score-mini {
  color: #909399;
}
</style>
