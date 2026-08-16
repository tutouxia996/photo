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
        @change="onAlbumChange"
      >
        <el-option
          v-for="item in albumOptions"
          :key="item.albumId"
          :label="item.albumName"
          :value="item.albumId"
        />
      </el-select>
    </header>
    <div v-if="estimatedCount > 0 || correctMode" class="est-banner">
      <span class="dot" />
      <span class="est-banner-text">
        <template v-if="aiPickMode">
          点选模式：可在右侧列表勾选（不依赖地图叠点），或点地图估计点；已选 {{ aiSelectedIds.length }}/{{ aiMaxBatch }}。识别时按本相册粗定位地点动态拉取周边 POI。
        </template>
        <template v-else-if="correctMode">
          纠正模式：拖动飘到水面/偏航的照片或视频到岸边，右侧点「保存纠正」写成手工坐标并同步轨迹。
        </template>
        <template v-else>
          蓝色「区」区域粗定位，紫色「AI」地标识别，橙色「估」时间推算。均可拖动微调；设备 GPS 可点「纠正定位」。也可「点选照片 → AI 识别地标」。
        </template>
      </span>
      <template v-if="canShowAiLandmark && canEditEstimated && !correctMode">
        <el-button
          v-if="!aiPickMode"
          type="primary"
          size="small"
          plain
          class="ai-btn"
          @click="enterAiPickMode"
        >点选 AI 识别</el-button>
        <template v-else>
          <el-button size="small" @click="selectAiBatch" :disabled="!pendingEstimated.length">选满本批</el-button>
          <el-button size="small" @click="clearAiSelection" :disabled="!aiSelectedIds.length">清空已选</el-button>
          <el-button
            type="primary"
            size="small"
            class="ai-btn"
            :loading="aiLoading"
            :disabled="!aiSelectedIds.length"
            @click="runAiLandmark"
          >识别已选 {{ aiSelectedIds.length }}</el-button>
          <el-button size="small" @click="exitAiPickMode">退出点选</el-button>
        </template>
      </template>
      <el-button
        v-if="canEditEstimated && !aiPickMode"
        size="small"
        :type="correctMode ? 'warning' : 'default'"
        class="ai-btn"
        @click="toggleCorrectMode"
      >{{ correctMode ? '退出纠正' : '纠正定位' }}</el-button>
      <el-button
        v-if="canConfirmAll && !aiPickMode && !correctMode"
        type="warning"
        size="small"
        class="ai-btn"
        :loading="confirmAllLoading"
        :disabled="!canEditEstimated"
        @click="confirmAllToTrack"
      >全部上主轨迹 ({{ estimatedCount }})</el-button>
      <el-button
        v-if="canConfirmAll && !aiPickMode && !correctMode"
        size="small"
        class="ai-btn"
        :loading="respreadLoading"
        :disabled="!canEditEstimated"
        title="按拍摄时间重新推算并分散估计点，避免叠成一团"
        @click="respreadEstimated"
      >重新分散</el-button>
      <span v-if="estimatedCount && !correctMode" class="est-count">待确认 {{ estimatedCount }}</span>
    </div>
    <div v-else-if="canEditEstimated" class="est-banner est-banner--slim">
      <span class="est-banner-text">设备 GPS 若飘到水面，可进入纠正模式拖回岸边。</span>
      <el-button
        size="small"
        :type="correctMode ? 'warning' : 'primary'"
        plain
        class="ai-btn"
        @click="toggleCorrectMode"
      >{{ correctMode ? '退出纠正' : '纠正定位' }}</el-button>
    </div>
    <div class="map-page-body">
      <PhotoClusterMap
        ref="mapRef"
        :points="points"
        :empty-text="emptyText"
        :auto-fit="false"
        :estimated-draggable="canEditEstimated && !aiPickMode && !correctMode"
        :location-correctable="canEditEstimated && correctMode && !aiPickMode"
        :ai-pick-mode="aiPickMode"
        :selected-photo-ids="aiSelectedIds"
        @estimated-drag-end="onEstimatedDragEnd"
        @estimated-select="onEstimatedSelect"
        @location-drag-end="onLocationDragEnd"
        @location-select="onLocationSelect"
      >
        <template #meta>
          <div class="map-meta">
            <span class="name">{{ metaTitle }}</span>
            <span class="stat">定位 {{ points.length }}</span>
            <span v-if="estimatedCount" class="stat est">估计 {{ estimatedCount }}</span>
          </div>
        </template>
      </PhotoClusterMap>

      <aside v-if="aiPickMode" class="est-edit-panel ai-pick-panel">
        <div class="panel-head">
          <span>AI 点选列表（{{ pendingEstimated.length }}）</span>
          <button type="button" class="panel-close" @click="exitAiPickMode">×</button>
        </div>
        <p class="panel-tip">叠在一起时请在此勾选；单次最多 {{ aiMaxBatch }} 张。点缩略图可定位到地图。</p>
        <div class="ai-pick-actions">
          <el-button size="small" type="primary" plain @click="selectAiBatch" :disabled="!pendingEstimated.length">选满本批</el-button>
          <el-button size="small" @click="clearAiSelection" :disabled="!aiSelectedIds.length">清空</el-button>
        </div>
        <div class="ai-pick-list">
          <button
            v-for="p in pendingEstimated"
            :key="p.photoId"
            type="button"
            class="ai-pick-item"
            :class="{ selected: isAiSelected(p.photoId) }"
            @click="onAiListItemClick(p)"
          >
            <span class="ai-pick-check" @click.stop="toggleAiSelection({ photoId: p.photoId })">
              <input type="checkbox" :checked="isAiSelected(p.photoId)" tabindex="-1" readonly />
            </span>
            <img class="ai-pick-thumb" :src="mediaSrc(p, false)" alt="" loading="lazy" />
            <span class="ai-pick-meta">
              <span class="ai-pick-name" :title="p.fileName">{{ p.fileName || ('#' + p.photoId) }}</span>
              <span class="ai-pick-badge">{{ estimatedSourceLabel(p) }}</span>
            </span>
          </button>
        </div>
      </aside>

      <aside v-else-if="editEst" class="est-edit-panel">
        <div class="panel-head">
          <span>{{ editIsEstimated ? '微调估计位置' : '纠正定位' }}</span>
          <button type="button" class="panel-close" @click="cancelEstimatedEdit">×</button>
        </div>
        <div class="panel-media" v-if="editThumb">
          <img :src="editThumb" alt="" />
        </div>
        <div class="panel-name" :title="editEst.fileName">{{ editEst.fileName || '未命名媒体' }}</div>
        <div v-if="editSourceLabel" class="panel-source">来源：{{ editSourceLabel }}</div>
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
        <p v-else-if="!editIsEstimated" class="panel-tip">设备 GPS 若飘到水面/偏航，可拖到岸边后保存为手工坐标</p>
        <div class="panel-actions">
          <el-button
            type="primary"
            :loading="saving"
            :disabled="!canEditEstimated"
            @click="saveLocationAdjust"
          >{{ editIsEstimated ? '保存调整' : '保存纠正' }}</el-button>
          <el-button
            v-if="editIsEstimated"
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
  aiLandmarkAlbum,
  confirmEstimatedBatch,
  confirmEstimatedPhoto,
  correctPhotoPosition,
  fallbackLocateAlbum,
  listPhotoMapPoints,
  updateEstimatedPosition
} from '@/api/photos/photo'
import { searchTrackPlace } from '@/api/album/track'
import PhotoClusterMap from '@/components/PhotoClusterMap/index.vue'
import {
  estimatedSourceLabel,
  isEstimatedLocation,
  isPendingEstimated,
  mediaSrc,
  estimatedSourceTitle
} from '@/utils/photoMapCluster'
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
const aiLoading = ref(false)
const confirmAllLoading = ref(false)
const aiPickMode = ref(false)
const correctMode = ref(false)
const aiSelectedIds = ref([])
/** 单次上限，与后端 album.aiLandmark.maxSample 对齐；可多次分批 */
const aiMaxBatch = 40
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

const estimatedCount = computed(() => points.value.filter(isPendingEstimated).length)

const pendingEstimated = computed(() => points.value.filter(isPendingEstimated))

const respreadLoading = ref(false)

const regionOrAiCount = computed(() => points.value.filter(p => {
  const s = p?.locationSource
  return (s === 'region_center' || s === 'ai_landmark') && isPendingEstimated(p)
}).length)

/** 已选相册且存在区域/AI 估计点时，可进一步 AI 缩小范围 */
const canShowAiLandmark = computed(() => {
  if (albumId.value == null || albumId.value === '') return false
  return regionOrAiCount.value > 0 || estimatedCount.value > 0
})

const canConfirmAll = computed(() => {
  if (albumId.value == null || albumId.value === '') return false
  return estimatedCount.value > 0
})

const editThumb = computed(() => {
  if (!editEst.value) return ''
  return mediaSrc(editEst.value, false)
})

const editIsEstimated = computed(() => {
  if (!editEst.value) return false
  return isEstimatedLocation(editEst.value)
})

const editSourceLabel = computed(() => {
  if (!editEst.value) return ''
  if (editIsEstimated.value) return estimatedSourceTitle(editEst.value)
  const s = editEst.value.locationSource
  if (s === 'video') return '视频元数据 GPS（可纠正）'
  if (s === 'exif') return '照片 EXIF GPS（可纠正）'
  if (s === 'manual') return '手工坐标'
  if (s === 'gpx_match') return 'GPX 匹配'
  return s ? `来源 ${s}` : '设备/历史 GPS（可纠正）'
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
  proxy.$tab.navigatePage({ path: '/photos/index' })
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
    nextTick(() => {
      mapRef.value?.refresh?.({ fit: true })
      // 页签原地跳转 / 过渡动画后容器尺寸可能延后就绪，补两次校准
      setTimeout(() => mapRef.value?.invalidateMapSize?.(), 50)
      setTimeout(() => mapRef.value?.invalidateMapSize?.(), 300)
    })
  }).catch(() => {
    points.value = []
  }).finally(() => {
    loading.value = false
  })
}

function onAlbumChange() {
  exitAiPickMode()
  correctMode.value = false
  editEst.value = null
  loadPoints()
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
  if (!point) return
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
    locationSource: point.locationSource,
    locationConfidence: point.locationConfidence,
    address: same ? editEst.value.address : (point.address || ''),
    latitude: lat,
    longitude: lng,
    dirty: dirty || (same && editEst.value.dirty),
    backup: same && editEst.value.backup
      ? editEst.value.backup
      : {
          latitude: point.latitude,
          longitude: point.longitude,
          address: point.address || '',
          locationSource: point.locationSource
        }
  }
  // 拖动时标记已在地图上，勿回写 points 触发整图重绘/缩放
  if (dirty && syncMap) {
    patchLocalPoint(point.photoId, { latitude: lat, longitude: lng })
    nextTick(() => mapRef.value?.refresh?.({ fit: false }))
  }
}

function onEstimatedSelect(payload) {
  if (aiPickMode.value) {
    toggleAiSelection(payload)
    return
  }
  // 估计点仍走原逻辑；设备点由 location-select 处理，避免重复打开
  if (payload?.point && isEstimatedLocation(payload.point)) {
    openEstimatedEditor(payload, { dirty: false })
  }
}

function onLocationSelect(payload) {
  if (aiPickMode.value) return
  openEstimatedEditor(payload, { dirty: false })
}

function onEstimatedDragEnd(payload) {
  if (!canEditEstimated.value || !payload?.photoId) return
  if (payload?.point && isEstimatedLocation(payload.point)) {
    openEstimatedEditor(payload, { dirty: true, syncMap: false })
  }
}

function onLocationDragEnd(payload) {
  if (!canEditEstimated.value || !payload?.photoId) return
  openEstimatedEditor(payload, { dirty: true, syncMap: false })
}

function enterAiPickMode() {
  if (!canEditEstimated.value || albumId.value == null || albumId.value === '') {
    proxy?.$modal?.msgWarning?.('请先选择一个相册')
    return
  }
  correctMode.value = false
  editEst.value = null
  resetPlaceSearch()
  aiPickMode.value = true
  aiSelectedIds.value = []
  proxy?.$modal?.msgSuccess?.('已进入点选：可在右侧列表勾选，或点地图估计点')
}

function isAiSelected(photoId) {
  return aiSelectedIds.value.some(x => String(x) === String(photoId))
}

function selectAiBatch() {
  const list = pendingEstimated.value
  if (!list.length) return
  const next = []
  for (const p of list) {
    if (next.length >= aiMaxBatch) break
    next.push(p.photoId)
  }
  aiSelectedIds.value = next
  proxy?.$modal?.msgSuccess?.(`已选本批 ${next.length} 张，可点「识别已选」`)
}

function onAiListItemClick(point) {
  if (!point?.photoId) return
  toggleAiSelection({ photoId: point.photoId })
  if (point.latitude != null && point.longitude != null) {
    mapRef.value?.focusWgs?.(Number(point.latitude), Number(point.longitude), 16)
  }
}

async function respreadEstimated() {
  if (!canEditEstimated.value || albumId.value == null || albumId.value === '') {
    proxy?.$modal?.msgWarning?.('请先选择一个相册')
    return
  }
  try {
    await proxy?.$modal?.confirm?.(
      '将按拍摄时间重新推算橙色「估」点并分散。蓝色「区」仅当附近（约 3km）有 GPS 锚点时才会被精修，不会被远处景点吸走。是否继续？'
    )
  } catch (e) {
    return
  }
  respreadLoading.value = true
  try {
    const res = await fallbackLocateAlbum(albumId.value)
    const data = res?.data
    const n = typeof data === 'number'
      ? data
      : (Number(data?.updated) || 0)
    proxy?.$modal?.msgSuccess?.(n > 0 ? `已重新分散 ${n} 个估计点` : '没有需要更新的估计点')
    editEst.value = null
    loadPoints()
  } catch (e) {
    /* 全局已提示 */
  } finally {
    respreadLoading.value = false
  }
}

function toggleCorrectMode() {
  if (!canEditEstimated.value) return
  if (correctMode.value) {
    correctMode.value = false
    if (editEst.value && !isEstimatedLocation(editEst.value)) {
      cancelEstimatedEdit()
    }
    nextTick(() => mapRef.value?.refresh?.({ fit: false }))
    return
  }
  exitAiPickMode()
  correctMode.value = true
  proxy?.$modal?.msgSuccess?.('已进入纠正：拖动缩略图到正确位置后点「保存纠正」')
  nextTick(() => mapRef.value?.refresh?.({ fit: false }))
}

function exitAiPickMode() {
  aiPickMode.value = false
  aiSelectedIds.value = []
}

function clearAiSelection() {
  aiSelectedIds.value = []
}

function toggleAiSelection(payload) {
  const id = payload?.photoId
  if (id == null) return
  const point = points.value.find(p => String(p.photoId) === String(id))
  if (!point || !isPendingEstimated(point)) {
    proxy?.$modal?.msgWarning?.('只能点选待确认的估计位置照片（区 / AI / 估）')
    return
  }
  const key = String(id)
  const exists = aiSelectedIds.value.some(x => String(x) === key)
  if (exists) {
    aiSelectedIds.value = aiSelectedIds.value.filter(x => String(x) !== key)
    return
  }
  if (aiSelectedIds.value.length >= aiMaxBatch) {
    proxy?.$modal?.msgWarning?.(`单次最多选 ${aiMaxBatch} 张，请先识别这批，或取消部分后再选`)
    return
  }
  aiSelectedIds.value = [...aiSelectedIds.value, id]
}

async function runAiLandmark() {
  if (!canEditEstimated.value || albumId.value == null || albumId.value === '') {
    proxy?.$modal?.msgWarning?.('请先选择一个相册')
    return
  }
  if (!aiSelectedIds.value.length) {
    proxy?.$modal?.msgWarning?.('请先在右侧列表或地图上点选要识别的照片')
    return
  }
  try {
    await proxy?.$modal?.confirm?.(
      `将对已选的 ${aiSelectedIds.value.length} 张照片做 AI 地标识别：按本相册粗定位地点拉取周边 POI 白名单，只在该范围内匹配；同批多图会投票吸附。是否继续？`
    )
  } catch (e) {
    return
  }
  aiLoading.value = true
  try {
    const res = await aiLandmarkAlbum(albumId.value, {
      photoIds: aiSelectedIds.value,
      interpolateOthers: true
    })
    const data = res.data || {}
    const updated = Number(data.updated) || 0
    const recognized = Number(data.recognized) || 0
    const interp = Number(data.interpUpdated) || 0
    const poiN = Number(data.poiWhitelistSize) || 0
    const voted = Number(data.votedApplied) || 0
    const names = (data.landmarks || [])
      .map(x => x.landmark || x.place)
      .filter(Boolean)
      .slice(0, 5)
    const nameHint = names.length ? `：${names.join('、')}` : ''
    const extra = [
      poiN > 0 ? `周边POI ${poiN}` : null,
      voted > 0 ? `投票吸附 ${voted}` : null,
      interp > 0 ? `时间推算补 ${interp}` : null
    ].filter(Boolean).join('，')
    proxy?.$modal?.msgSuccess?.(
      `AI 已更新 ${updated} 个点（识别 ${recognized}${extra ? '，' + extra : ''}）${nameHint}`
    )
    aiSelectedIds.value = []
    editEst.value = null
    resetPlaceSearch()
    loadPoints()
  } catch (e) {
    /* 全局已提示 */
  } finally {
    aiLoading.value = false
  }
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

function saveLocationAdjust() {
  const cur = editEst.value
  if (!cur || !canEditEstimated.value) return
  const estimated = isEstimatedLocation(cur)
  saving.value = true
  const req = estimated
    ? updateEstimatedPosition({
        photoId: cur.photoId,
        latitude: cur.latitude,
        longitude: cur.longitude,
        address: cur.address || ''
      })
    : correctPhotoPosition({
        photoId: cur.photoId,
        latitude: cur.latitude,
        longitude: cur.longitude,
        address: cur.address || ''
      })
  req.then((res) => {
    const data = res?.data || {}
    patchLocalPoint(cur.photoId, {
      latitude: data.latitude ?? cur.latitude,
      longitude: data.longitude ?? cur.longitude,
      address: data.address ?? cur.address ?? '',
      locationSource: estimated
        ? (data.locationSource || cur.locationSource)
        : (data.locationSource || 'manual'),
      locationConfidence: data.locationConfidence ?? (estimated ? cur.locationConfidence : 1)
    })
    editEst.value = {
      ...cur,
      latitude: data.latitude ?? cur.latitude,
      longitude: data.longitude ?? cur.longitude,
      address: data.address ?? cur.address ?? '',
      locationSource: estimated
        ? (data.locationSource || cur.locationSource)
        : (data.locationSource || 'manual'),
      dirty: false,
      backup: {
        latitude: data.latitude ?? cur.latitude,
        longitude: data.longitude ?? cur.longitude,
        address: data.address ?? cur.address ?? '',
        locationSource: estimated
          ? (data.locationSource || cur.locationSource)
          : (data.locationSource || 'manual')
      }
    }
    proxy?.$modal?.msgSuccess?.(
      estimated ? '估计位置已保存（仍未上主轨迹）' : '定位已纠正为手工坐标，并已同步轨迹'
    )
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
      locationSource: data.locationSource || cur.locationSource || 'manual',
      locationConfidence: data.locationConfidence ?? 1
    })
    editEst.value = null
    proxy?.$modal?.msgSuccess?.('已确认上主轨迹（仍保留来源标记）')
    resetPlaceSearch()
    nextTick(() => mapRef.value?.refresh?.({ fit: false }))
  }).catch(() => {
    /* 取消或失败 */
  }).finally(() => {
    saving.value = false
  })
}

async function confirmAllToTrack() {
  if (!canEditEstimated.value || albumId.value == null || albumId.value === '') {
    proxy?.$modal?.msgWarning?.('请先选择一个相册')
    return
  }
  if (!estimatedCount.value) {
    proxy?.$modal?.msgWarning?.('当前没有待确认的估计点')
    return
  }
  // 当前面板若有未保存拖动，先写入
  const cur = editEst.value
  if (cur?.dirty && cur.photoId) {
    try {
      await updateEstimatedPosition({
        photoId: cur.photoId,
        latitude: cur.latitude,
        longitude: cur.longitude,
        address: cur.address || ''
      })
      cur.dirty = false
    } catch (e) {
      proxy?.$modal?.msgWarning?.('请先保存当前点的位置调整')
      return
    }
  }
  try {
    await proxy?.$modal?.confirm?.(
      `将把本相册全部 ${estimatedCount.value} 个待确认估计点（区/AI/估）一次性确认上主轨迹，仍保留来源角标。\n若刚拖过其它点，请确认已点「保存调整」。是否继续？`
    )
  } catch (e) {
    return
  }
  confirmAllLoading.value = true
  try {
    const res = await confirmEstimatedBatch(albumId.value)
    const data = res.data || {}
    const n = Number(data.confirmed) || 0
    const promoted = data.promoted !== false && data.trackId != null
    proxy?.$modal?.msgSuccess?.(
      promoted
        ? `已全部确认上主轨迹 ${n} 个点，列表中草稿已转正并可查看「轨迹」`
        : `已全部确认上主轨迹 ${n} 个点`
    )
    editEst.value = null
    resetPlaceSearch()
    exitAiPickMode()
    loadPoints()
  } catch (e) {
    /* 全局已提示 */
  } finally {
    confirmAllLoading.value = false
  }
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
      correctMode.value = false
      exitAiPickMode()
      loadPoints()
    }
    return
  }
  const n = Number(val)
  if (!Number.isNaN(n) && albumId.value !== n) {
    albumId.value = n
    editEst.value = null
    correctMode.value = false
    exitAiPickMode()
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

.est-banner--slim {
  background: #f4f8ff;
  color: #3b6ea5;
  border-bottom-color: #d6e4f5;
}

.est-banner-text {
  flex: 1;
  min-width: 0;
  line-height: 1.4;
}

.est-banner .ai-btn {
  flex-shrink: 0;
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
  margin-left: 0;
  font-weight: 600;
  color: #e6a23c;
  flex-shrink: 0;
}

.map-page-body {
  flex: 1;
  min-height: 0;
  position: relative;
  overflow: hidden;
}

/* Leaflet 需父级有明确宽高；绝对铺满避免 flex/% 高度塌成 0 导致灰屏无瓦片 */
.map-page-body > :deep(.photo-cluster-map) {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  min-height: 0;
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

.panel-source {
  margin-top: 4px;
  font-size: 12px;
  color: #b88230;
  line-height: 1.4;
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

.ai-pick-panel {
  width: 320px;
  max-height: calc(100% - 32px);
  display: flex;
  flex-direction: column;
  padding-bottom: 10px;
}

.ai-pick-actions {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.ai-pick-list {
  flex: 1;
  min-height: 0;
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: min(70vh, 560px);
  padding-right: 2px;
}

.ai-pick-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 6px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fff;
  text-align: left;
  cursor: pointer;
}

.ai-pick-item:hover {
  border-color: #c6e2ff;
  background: #f5f9ff;
}

.ai-pick-item.selected {
  border-color: #409eff;
  background: #ecf5ff;
}

.ai-pick-check {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
}

.ai-pick-thumb {
  width: 48px;
  height: 48px;
  border-radius: 6px;
  object-fit: cover;
  background: #f2f3f5;
  flex-shrink: 0;
}

.ai-pick-meta {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.ai-pick-name {
  font-size: 12px;
  color: #303133;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ai-pick-badge {
  align-self: flex-start;
  font-size: 11px;
  color: #e6a23c;
  background: #fdf6ec;
  border-radius: 3px;
  padding: 0 4px;
  line-height: 1.5;
}
</style>
