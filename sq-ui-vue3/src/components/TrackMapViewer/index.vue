<template>
  <div class="track-map-viewer">
    <PhotoClusterMap
      ref="clusterMapRef"
      :points="points"
      :show-polyline="true"
      :polyline-color="track?.trackColor || '#3B82F6'"
      empty-text="暂无轨迹点位"
    >
      <template #meta>
        <div v-if="track" class="track-meta">
          <span class="name">{{ track.trackName || '未命名轨迹' }}</span>
          <span class="stat">点位 {{ track.pointCount ?? points.length }}</span>
          <span class="stat">里程 {{ formatDistance(track.totalDistance) }}</span>
          <span class="stat">时长 {{ formatDuration(track.totalDuration) }}</span>
        </div>
      </template>
    </PhotoClusterMap>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import PhotoClusterMap from '@/components/PhotoClusterMap/index.vue'

defineProps({
  track: { type: Object, default: null },
  points: { type: Array, default: () => [] }
})

const clusterMapRef = ref(null)

function formatDistance(km) {
  if (km == null || km === '') return '-'
  const n = Number(km)
  if (Number.isNaN(n)) return '-'
  return n < 1 ? `${(n * 1000).toFixed(0)} m` : `${n.toFixed(2)} km`
}

function formatDuration(sec) {
  if (sec == null || sec === '') return '-'
  const total = Math.max(0, Math.floor(Number(sec) || 0))
  const h = Math.floor(total / 3600)
  const m = Math.floor((total % 3600) / 60)
  const r = total % 60
  return `${h}小时${m}分钟${r}秒`
}

function refresh() {
  clusterMapRef.value?.refresh?.()
}

defineExpose({ refresh })
</script>

<style scoped>
.track-map-viewer {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 0;
}

.track-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: baseline;
  max-width: min(100%, 720px);
  padding: 8px 12px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.12);
  color: #606266;
  font-size: 13px;
}

.track-meta .name {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
</style>
