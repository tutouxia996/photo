<template>
  <div class="photos-map-page" v-loading="loading">
    <header class="map-page-header">
      <button type="button" class="back-btn" title="返回" @click="goBack">
        <el-icon :size="20"><ArrowLeft /></el-icon>
      </button>
      <div class="header-main">
        <h1 class="page-title">照片地图</h1>
        <span class="page-sub">{{ headerSub }}</span>
      </div>
      <el-select
        v-model="albumId"
        clearable
        filterable
        placeholder="全部相册"
        class="album-select"
        @change="loadPoints"
      >
        <el-option
          v-for="item in albumOptions"
          :key="item.albumId"
          :label="item.albumName"
          :value="item.albumId"
        />
      </el-select>
    </header>
    <div class="map-page-body">
      <PhotoClusterMap
        ref="mapRef"
        :points="points"
        :empty-text="emptyText"
      >
        <template #meta>
          <div class="map-meta">
            <span class="name">{{ metaTitle }}</span>
            <span class="stat">定位照片 {{ points.length }}</span>
          </div>
        </template>
      </PhotoClusterMap>
    </div>
  </div>
</template>

<script setup name="PhotosMap">
import { ArrowLeft } from '@element-plus/icons-vue'
import { listAlbum } from '@/api/photos/album'
import { listPhotoMapPoints } from '@/api/photos/photo'
import PhotoClusterMap from '@/components/PhotoClusterMap/index.vue'

const route = useRoute()
const router = useRouter()
const { proxy } = getCurrentInstance()

const loading = ref(false)
const points = ref([])
const albumOptions = ref([])
const albumId = ref(undefined)
const mapRef = ref(null)

const albumNameMap = computed(() => {
  const map = {}
  albumOptions.value.forEach(a => {
    map[a.albumId] = a.albumName
  })
  return map
})

const metaTitle = computed(() => {
  if (albumId.value == null || albumId.value === '') return '全部相册'
  return albumNameMap.value[albumId.value] || '相册'
})

const headerSub = computed(() => {
  if (!points.value.length) return '暂无带定位的照片'
  return `共 ${points.value.length} 张带定位照片，缩放可分层聚合`
})

const emptyText = computed(() => (
  albumId.value ? '该相册暂无带定位的照片' : '暂无带定位的照片'
))

function goBack() {
  if (window.history.length > 1) {
    router.back()
    return
  }
  proxy.$tab.closeOpenPage({ path: '/photos/index' })
}

function loadAlbums() {
  return listAlbum({ pageNum: 1, pageSize: 500 }).then(res => {
    albumOptions.value = res.rows || []
  })
}

function loadPoints() {
  loading.value = true
  const params = {}
  if (albumId.value != null && albumId.value !== '') {
    params.albumId = albumId.value
  }
  listPhotoMapPoints(params).then(res => {
    points.value = res.data || []
    nextTick(() => mapRef.value?.refresh?.())
  }).catch(() => {
    points.value = []
  }).finally(() => {
    loading.value = false
  })
}

onMounted(() => {
  const q = route.query.albumId
  if (q != null && q !== '') {
    const n = Number(q)
    if (!Number.isNaN(n)) albumId.value = n
  }
  loadAlbums().finally(() => loadPoints())
})

watch(() => route.query.albumId, (val) => {
  if (val == null || val === '') {
    if (albumId.value != null) {
      albumId.value = undefined
      loadPoints()
    }
    return
  }
  const n = Number(val)
  if (!Number.isNaN(n) && albumId.value !== n) {
    albumId.value = n
    loadPoints()
  }
})
</script>

<style scoped>
.photos-map-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 84px);
  min-height: 480px;
  background: #f5f7fa;
}

.map-page-header {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  background: #fff;
  border-bottom: 1px solid var(--el-border-color-lighter, #ebeef5);
}

.back-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: 0;
  border-radius: 50%;
  background: #f2f3f5;
  color: #303133;
  cursor: pointer;
}

.back-btn:hover {
  background: #e9ebef;
}

.header-main {
  flex: 1;
  min-width: 0;
}

.page-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  line-height: 1.3;
}

.page-sub {
  display: block;
  margin-top: 2px;
  font-size: 12px;
  color: #909399;
}

.album-select {
  width: 200px;
}

.map-page-body {
  flex: 1;
  min-height: 0;
  position: relative;
}

.map-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: baseline;
  padding: 8px 12px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.12);
  color: #606266;
  font-size: 13px;
}

.map-meta .name {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
</style>
