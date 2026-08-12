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
    <div v-if="estimatedCount > 0" class="est-banner">
      <span class="dot" />
      橙色「估」为估计位置。可拖动，或用高德搜索位置后点「预览」定位；再「保存调整 / 确认上主轨迹」。
      <span class="est-count">待确认 {{ estimatedCount }}</span>
    </div>
    <div class="map-page-body">
      <PhotoClusterMap
        ref="mapRef"
        :points="points"
        :empty-text="emptyText"
        :auto-fit="false"
        :estimated-draggable="canEditEstimated"
        @estimated-drag-end="onEstimatedDragEnd"
        @estimated-select="onEstimatedSelect"
      >
        <template #meta>
          <div class="map-meta">
            <span class="name">{{ metaTitle }}</span>
            <span class="stat">定位 {{ points.length }}</span>
            <span v-if="estimatedCount" class="stat est">估计 {{ estimatedCount }}</span>
          </div>
        </template>
      </PhotoClusterMap>

      <aside v-if="editEst" class="est-edit-panel">
        <div class="panel-head">
          <span>微调估计位置</span>
          <button type="button" class="panel-close" @click="cancelEstimatedEdit">×</button>
        </div>
        <div class="panel-media" v-if="editThumb">
          <img :src="editThumb" alt="" />
        </div>
        <div class="panel-name" :title="editEst.fileName">{{ editEst.fileName || '未命名媒体' }}</div>
        <div class="panel-coords">{{ editEst.latitude }}, {{ editEst.longitude }}</div>
        <el-form label-position="top" size="small">
          <el-form-item label="高德搜索位置">
            <el-select
              v-model="placePickId"
              filterable
              remote
              clearable
              reserve-keyword
              :remote-method="searchPlace"
              :loading="placeSearching"
              placeholder="输入地点名，如：天安门广场"
              style="width: 100%"
              @change="onPlacePicked"
            >
              <el-option
                v-for="item in placeOptions"
                :key="placeKey(item)"
                :label="item.name"
                :value="placeKey(item)"
              >
                <div class="place-opt">
                  <div class="place-name">{{ item.name }}</div>
                  <div class="place-addr">{{ item.address }}</div>
                </div>
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="位置名称（将写入 address）">
            <el-input
              v-model="editEst.address"
              maxlength="200"
              show-word-limit
              clearable
              placeholder="可改：默认用高德选中名称"
            />
          </el-form-item>
        </el-form>
        <el-button
          class="preview-btn"
          type="success"
          plain
          :disabled="!canPreviewPlace || !canEditEstimated"
          @click="previewPlaceOnMap"
        >预览定位</el-button>
        <p v-if="editEst.dirty" class="panel-tip">位置已变更（拖动或预览），点击下方按钮才会保存</p>
        <div class="panel-actions">
          <el-button
            type="primary"
            :loading="saving"
            :disabled="!canEditEstimated"
            @click="saveEstimatedAdjust"
          >保存调整</el-button>
          <el-button
            type="warning"
            :loading="saving"
            :disabled="!canEditEstimated"
            @click="confirmToTrack"
          >确认上主轨迹</el-button>
          <el-button :disabled="saving" @click="cancelEstimatedEdit">取消</el-button>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup name="PhotosMap">
import { ArrowLeft } from '@element-plus/icons-vue'
import { listAlbum } from '@/api/photos/album'
import {
  confirmEstimatedPhoto,
  listPhotoMapPoints,
  updateEstimatedPosition
} from '@/api/photos/photo'
import { searchTrackPlace } from '@/api/album/track'
import PhotoClusterMap from '@/components/PhotoClusterMap/index.vue'
import { isEstimatedLocation, mediaSrc } from '@/utils/photoMapCluster'
import { checkPermi } from '@/utils/permission'

const route = useRoute()
const router = useRouter()
const { proxy } = getCurrentInstance()

const loading = ref(false)
const points = ref([])
const albumOptions = ref([])
const albumId = ref(undefined)
const mapRef = ref(null)
const saving = ref(false)
const editEst = ref(null)
const placeOptions = ref([])
const placePickId = ref('')
const placeSearching = ref(false)
const selectedPlace = ref(null)
let placeSearchTimer = 0
const canEditEstimated = computed(() => checkPermi(['album:photo:edit']))

const canPreviewPlace = computed(() => {
  const p = selectedPlace.value
  return !!(p && p.wgsLat != null && p.wgsLng != null)
})

const albumNameMap = computed(() => {
  const map = {}
  albumOptions.value.forEach(a => {
    map[a.albumId] = a.albumName
  })
  return map
})

const estimatedCount = computed(() => points.value.filter(isEstimatedLocation).length)

const editThumb = computed(() => {
  if (!editEst.value) return ''
  return mediaSrc(editEst.value, false)
})

const metaTitle = computed(() => {
  if (albumId.value == null || albumId.value === '') return '全部相册'
  return albumNameMap.value[albumId.value] || '相册'
})

const headerSub = computed(() => {
  if (!points.value.length) return '暂无带定位的照片'
  if (estimatedCount.value) {
    return `共 ${points.value.length} 个定位点，其中 ${estimatedCount.value} 个为估计位置`
  }
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
  const params = { includeEstimated: true }
  if (albumId.value != null && albumId.value !== '') {
    params.albumId = albumId.value
  }
  listPhotoMapPoints(params).then(res => {
    points.value = res.data || []
    // 仅首次/切换相册时自动适配视野，拖动微调不再 fitBounds
    nextTick(() => mapRef.value?.refresh?.({ fit: true }))
  }).catch(() => {
    points.value = []
  }).finally(() => {
    loading.value = false
  })
}

function patchLocalPoint(photoId, patch) {
  const id = String(photoId)
  points.value = points.value.map(p => {
    if (String(p.photoId) !== id) return p
    return { ...p, ...patch }
  })
}

function placeKey(item) {
  return item?.id || `${item?.name},${item?.lng},${item?.lat}`
}

function searchPlace(query) {
  const q = (query || '').trim()
  if (!q) {
    placeOptions.value = []
    return
  }
  clearTimeout(placeSearchTimer)
  placeSearchTimer = setTimeout(async () => {
    placeSearching.value = true
    try {
      const res = await searchTrackPlace({ keywords: q, offset: 10 })
      placeOptions.value = res.data || []
    } catch (e) {
      placeOptions.value = []
    } finally {
      placeSearching.value = false
    }
  }, 320)
}

function onPlacePicked(id) {
  if (!id) {
    selectedPlace.value = null
    return
  }
  const found = placeOptions.value.find(o => placeKey(o) === id) || null
  selectedPlace.value = found
  if (found?.name && editEst.value) {
    // 选中后先填名称，坐标等点「预览」再落到地图
    editEst.value.address = found.name
  }
}

function previewPlaceOnMap() {
  const cur = editEst.value
  const place = selectedPlace.value
  if (!cur || !place || place.wgsLat == null || place.wgsLng == null) {
    proxy?.$modal?.msgWarning?.('请先搜索并选择一个地点')
    return
  }
  const lat = Number(place.wgsLat)
  const lng = Number(place.wgsLng)
  const address = (cur.address || place.name || '').trim()
  editEst.value = {
    ...cur,
    latitude: lat,
    longitude: lng,
    address,
    dirty: true
  }
  patchLocalPoint(cur.photoId, { latitude: lat, longitude: lng, address })
  nextTick(() => {
    mapRef.value?.refresh?.({ fit: false })
    mapRef.value?.focusWgs?.(lat, lng, 16)
  })
  proxy?.$modal?.msgSuccess?.('已预览到该位置，确认无误后再保存')
}

function resetPlaceSearch() {
  placeOptions.value = []
  placePickId.value = ''
  selectedPlace.value = null
}

function openEstimatedEditor(payload, { dirty = false, syncMap = false } = {}) {
  if (!payload?.photoId) return
  const point = points.value.find(p => String(p.photoId) === String(payload.photoId))
  if (!point || !isEstimatedLocation(point)) return
  const lat = payload.latitude != null ? payload.latitude : point.latitude
  const lng = payload.longitude != null ? payload.longitude : point.longitude
  const same =
    editEst.value
    && String(editEst.value.photoId) === String(point.photoId)
  if (!same) {
    resetPlaceSearch()
  }
  editEst.value = {
    photoId: point.photoId,
    fileName: point.fileName,
    fileType: point.fileType,
    thumbUrl: point.thumbUrl,
    fileUrl: point.fileUrl,
    address: same ? editEst.value.address : (point.address || ''),
    latitude: lat,
    longitude: lng,
    dirty: dirty || (same && editEst.value.dirty),
    backup: same && editEst.value.backup
      ? editEst.value.backup
      : {
          latitude: point.latitude,
          longitude: point.longitude,
          address: point.address || ''
        }
  }
  // 拖动时标记已在地图上，勿回写 points 触发整图重绘/缩放
  if (dirty && syncMap) {
    patchLocalPoint(point.photoId, { latitude: lat, longitude: lng })
    nextTick(() => mapRef.value?.refresh?.({ fit: false }))
  }
}

function onEstimatedSelect(payload) {
  openEstimatedEditor(payload, { dirty: false })
}

function onEstimatedDragEnd(payload) {
  if (!canEditEstimated.value || !payload?.photoId) return
  // 只更新右侧面板坐标，保持当前缩放与中心
  openEstimatedEditor(payload, { dirty: true, syncMap: false })
}

function cancelEstimatedEdit() {
  const cur = editEst.value
  if (!cur) return
  if (cur.dirty) {
    // 还原到备份：回写 points 并重绘（不改缩放）
    if (cur.backup) {
      patchLocalPoint(cur.photoId, {
        latitude: cur.backup.latitude,
        longitude: cur.backup.longitude,
        address: cur.backup.address
      })
    }
    nextTick(() => mapRef.value?.refresh?.({ fit: false }))
  }
  editEst.value = null
  resetPlaceSearch()
}

function saveEstimatedAdjust() {
  const cur = editEst.value
  if (!cur || !canEditEstimated.value) return
  saving.value = true
  updateEstimatedPosition({
    photoId: cur.photoId,
    latitude: cur.latitude,
    longitude: cur.longitude,
    address: cur.address || ''
  }).then(() => {
    patchLocalPoint(cur.photoId, {
      latitude: cur.latitude,
      longitude: cur.longitude,
      address: cur.address || ''
    })
    editEst.value = {
      ...cur,
      dirty: false,
      backup: {
        latitude: cur.latitude,
        longitude: cur.longitude,
        address: cur.address || ''
      }
    }
    proxy?.$modal?.msgSuccess?.('估计位置已保存（仍未上主轨迹）')
    nextTick(() => mapRef.value?.refresh?.({ fit: false }))
  }).finally(() => {
    saving.value = false
  })
}

function confirmToTrack() {
  const cur = editEst.value
  if (!cur || !canEditEstimated.value) return
  proxy?.$modal?.confirm?.('确认将该位置采纳为正式坐标并加入主轨迹？').then(() => {
    saving.value = true
    return confirmEstimatedPhoto({
      photoId: cur.photoId,
      latitude: cur.latitude,
      longitude: cur.longitude,
      address: cur.address || ''
    })
  }).then((res) => {
    if (!res) return
    const data = res.data || {}
    patchLocalPoint(cur.photoId, {
      latitude: data.latitude ?? cur.latitude,
      longitude: data.longitude ?? cur.longitude,
      address: data.address ?? cur.address,
      locationSource: data.locationSource || 'manual',
      locationConfidence: data.locationConfidence ?? 1
    })
    editEst.value = null
    proxy?.$modal?.msgSuccess?.('已确认并同步主轨迹')
    resetPlaceSearch()
    nextTick(() => mapRef.value?.refresh?.({ fit: false }))
  }).catch(() => {
    /* 取消或失败 */
  }).finally(() => {
    saving.value = false
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
      editEst.value = null
      loadPoints()
    }
    return
  }
  const n = Number(val)
  if (!Number.isNaN(n) && albumId.value !== n) {
    albumId.value = n
    editEst.value = null
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

.est-banner {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: #fdf6ec;
  color: #8a6116;
  font-size: 13px;
  border-bottom: 1px solid #f5dab1;
}

.est-banner .dot {
  width: 10px;
  height: 10px;
  border-radius: 2px;
  border: 2px dashed #e6a23c;
  background: #fff;
  flex-shrink: 0;
}

.est-banner .est-count {
  margin-left: auto;
  font-weight: 600;
  color: #e6a23c;
}

.map-page-body {
  flex: 1;
  min-height: 0;
  position: relative;
}

.map-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 10px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.12);
  font-size: 13px;
}

.map-meta .name {
  font-weight: 600;
  color: #303133;
}

.map-meta .stat {
  color: #606266;
}

.map-meta .stat.est {
  color: #e6a23c;
  font-weight: 600;
}

.est-edit-panel {
  position: absolute;
  top: 16px;
  right: 16px;
  z-index: 1000;
  width: 300px;
  padding: 14px;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.18);
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.panel-close {
  width: 28px;
  height: 28px;
  border: 0;
  border-radius: 50%;
  background: #f2f3f5;
  color: #606266;
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
}

.panel-media {
  width: 100%;
  height: 140px;
  margin-bottom: 8px;
  border-radius: 8px;
  overflow: hidden;
  background: #ebeef5;
}

.panel-media img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.panel-name {
  font-size: 13px;
  color: #303133;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.panel-coords {
  margin: 4px 0 10px;
  font-size: 12px;
  color: #909399;
  word-break: break-all;
}

.panel-tip {
  margin: 0 0 10px;
  font-size: 12px;
  color: #e6a23c;
}

.preview-btn {
  width: 100%;
  margin: 0 0 10px;
}

.place-opt {
  line-height: 1.35;
  padding: 2px 0;
}

.place-name {
  font-size: 13px;
  color: #303133;
}

.place-addr {
  font-size: 12px;
  color: #909399;
  white-space: normal;
}

.panel-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.panel-actions .el-button {
  margin: 0;
  width: 100%;
}
</style>
