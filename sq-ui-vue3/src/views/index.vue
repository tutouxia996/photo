<template>
  <div class="home-footprint-page" v-loading="loading">
    <header class="map-page-header">
      <div class="header-main">
        <h1 class="page-title">足迹地图</h1>
        <span class="page-sub">{{ headerSub }}</span>
      </div>
    </header>

    <div class="map-page-body">
      <PhotoClusterMap
        ref="mapRef"
        :points="points"
        :empty-text="emptyText"
        :auto-fit="false"
        :show-polyline="false"
        :region-geo-json="regionGeoJson"
        :show-region-highlight="true"
      >
        <template #meta>
          <div class="map-meta">
            <span class="name">全部相册</span>
            <span class="stat">定位 {{ points.length }}</span>
            <span v-if="videoCount" class="stat">视频 {{ videoCount }}</span>
            <span v-if="regionStatText" class="stat region">{{ regionStatText }}</span>
          </div>
        </template>
      </PhotoClusterMap>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, shallowRef, ref } from 'vue'
import { listPhotoMapPoints, getVisitedRegionGeo } from '@/api/photos/photo'
import PhotoClusterMap from '@/components/PhotoClusterMap/index.vue'

defineOptions({ name: 'Index' })

const loading = ref(false)
const points = ref([])
const mapRef = ref(null)
/** 边界坐标很多，用 shallowRef 避免深层响应式拖垮页面 */
const regionGeoJson = shallowRef(null)
const regionMeta = ref({ provinces: [], cities: [], districts: [] })

const videoCount = computed(() => points.value.filter(p => Number(p.fileType) === 2).length)

const regionStatText = computed(() => {
  const p = regionMeta.value.provinces?.length || 0
  const c = regionMeta.value.cities?.length || 0
  const d = regionMeta.value.districts?.length || 0
  if (!p && !c && !d) return ''
  const parts = []
  if (p) parts.push(`${p} 省`)
  if (c) parts.push(`${c} 市`)
  if (d) parts.push(`${d} 区县`)
  return parts.join(' · ')
})

const headerSub = computed(() => {
  if (!points.value.length) return '暂无带定位的照片/视频'
  const parts = [`共 ${points.value.length} 个定位点`]
  if (videoCount.value) parts.push(`含 ${videoCount.value} 个视频`)
  if (regionStatText.value) parts.push(`途经 ${regionStatText.value}`)
  return parts.join('，')
})

const emptyText = computed(() => '暂无带定位的照片或视频')

function loadPoints() {
  loading.value = true
  return listPhotoMapPoints({ includeEstimated: true }).then(res => {
    points.value = res.data || []
    nextTick(() => {
      mapRef.value?.refresh?.({ fit: true })
      setTimeout(() => mapRef.value?.invalidateMapSize?.(), 50)
      setTimeout(() => mapRef.value?.invalidateMapSize?.(), 300)
    })
  }).catch(() => {
    points.value = []
  }).finally(() => {
    loading.value = false
  })
}

function loadRegions() {
  return getVisitedRegionGeo().then(res => {
    const data = res.data || {}
    const geo = data.geojson || null
    const count = Array.isArray(geo?.features) ? geo.features.length : 0
    regionGeoJson.value = count > 0 ? geo : null
    regionMeta.value = {
      provinces: data.provinces || [],
      cities: data.cities || [],
      districts: data.districts || []
    }
    nextTick(() => {
      mapRef.value?.refresh?.({ fit: false })
    })
  }).catch(() => {
    regionGeoJson.value = null
    regionMeta.value = { provinces: [], cities: [], districts: [] }
  })
}

onMounted(() => {
  loadPoints()
  loadRegions()
})
</script>

<style scoped lang="scss">
.home-footprint-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 84px);
  min-height: 480px;
  background: #f0f3f8;
}

.map-page-header {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 16px;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
}

.header-main {
  flex: 1;
  min-width: 0;
}

.page-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #1a3a6c;
  line-height: 1.3;
}

.page-sub {
  display: block;
  margin-top: 2px;
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.map-page-body {
  flex: 1;
  min-height: 0;
  position: relative;
}

.map-page-body > :deep(.photo-cluster-map) {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  min-height: 0;
}

.map-meta {
  display: inline-flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
  font-size: 12px;
  color: #606266;
}

.map-meta .name {
  font-weight: 600;
  color: #303133;
}

.map-meta .stat.region {
  color: #2563eb;
}

@media (max-width: 768px) {
  .home-footprint-page {
    height: calc(100vh - 100px);
  }

  .page-sub {
    white-space: normal;
  }
}
</style>
