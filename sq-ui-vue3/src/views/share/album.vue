<template>
  <div class="share-page" :class="{ 'is-map': viewMode === 'map' }" v-loading="loading">
    <header class="share-header">
      <div class="share-header-main">
        <div class="share-brand">分享相册</div>
        <div class="share-title-wrap">
          <h1 class="share-title">{{ album.albumName || '相册' }}</h1>
          <p v-if="album.albumDesc" class="share-desc">{{ album.albumDesc }}</p>
          <p class="share-meta">
            <span>共 {{ total }} 项</span>
            <span v-if="album.locationSummary">· {{ album.locationSummary }}</span>
            <span v-if="trackName && shareTrack">· 轨迹 {{ trackName }}</span>
          </p>
        </div>
      </div>
      <div class="share-actions">
        <el-button
          round
          plain
          :type="viewMode === 'map' ? 'primary' : 'default'"
          @click="toggleMap"
        >
          {{ viewMode === 'map' ? '返回相册' : '照片地图' }}
        </el-button>
      </div>
    </header>

    <div v-if="errorMsg" class="share-error">
      <el-empty :description="errorMsg" />
    </div>

    <template v-else>
      <div v-show="viewMode === 'grid'" class="share-grid-wrap">
        <div class="share-grid">
          <button
            v-for="(item, index) in photos"
            :key="item.photoId"
            type="button"
            class="share-card"
            @click="openViewer(index)"
          >
            <img :src="thumbSrc(item)" :alt="item.fileName || ''" loading="lazy" />
            <span v-if="item.fileType === 2" class="video-badge">▶</span>
          </button>
        </div>
        <div v-if="!photos.length && !loading" class="share-empty">
          <el-empty description="此分享暂无可查看的文件" />
        </div>
        <div v-if="photos.length" class="share-load-more">
          <el-button v-if="hasMore" :loading="loadingMore" text @click="loadMore">加载更多</el-button>
          <span v-else class="share-end">已全部加载</span>
        </div>
      </div>

      <div v-show="viewMode === 'map'" class="share-map-wrap">
        <PhotoClusterMap
          v-if="mapReady"
          :points="mapDisplayPoints"
          :empty-text="mapEmptyText"
          :auto-fit="true"
          :show-polyline="trackLineActive"
          :polyline-points="trackLineActive ? trackPoints : null"
          :polyline-color="trackColor"
          :segment-by-travel-mode="trackLineActive"
          :show-direction="trackLineActive"
          :show-travelers="trackLineActive"
          :overlay-paths="trackLineActive ? trackGpxOverlays : []"
          :visible-travel-modes="visibleTravelModes"
          :show-media-markers="!tracksOnly"
          :show-mode-legend="false"
          :segment-labels="'hover'"
        >
          <template #toolbar-right>
            <div class="header-toggles">
              <div
                class="track-toggle"
                :title="shareTrack ? '关闭后隐藏轨迹线，仅保留照片/视频点' : '此分享未开放轨迹'"
              >
                <span class="track-toggle-label">显示轨迹</span>
                <el-switch
                  v-model="showTrackLine"
                  inline-prompt
                  active-text="开"
                  inactive-text="关"
                  :disabled="!shareTrack || !hasTrackData"
                />
              </div>
              <div class="track-toggle" title="开启后仅显示轨迹线与途经点，隐藏照片/视频">
                <span class="track-toggle-label">只显示轨迹</span>
                <el-switch
                  v-model="tracksOnly"
                  inline-prompt
                  active-text="开"
                  inactive-text="关"
                  :disabled="!shareTrack || !hasTrackData"
                  @change="onTracksOnlyChange"
                />
              </div>
            </div>
          </template>
          <template #meta>
            <div class="map-meta">
              <span class="name">{{ album.albumName || '分享相册' }}</span>
              <span class="stat">定位 {{ mapPoints.length }}</span>
              <span v-if="trackLineActive && trackName" class="stat track">轨迹 {{ trackName }}</span>
            </div>
          </template>
          <template v-if="legendItems.length && trackLineActive" #modeLegend>
            <div class="pmc-mode-legend-title">出行方式</div>
            <button
              v-for="m in legendItems"
              :key="m.key"
              type="button"
              class="legend-item"
              :class="{ active: isModeFilterActive(m.key), muted: modeFilter.length > 0 && !isModeFilterActive(m.key) }"
              :title="modeFilterHint(m)"
              @click="toggleModeFilter(m.key)"
            >
              <span class="legend-icon" :style="{ background: m.color }" v-html="travelModeIconSvg(m.key, 12)" />
              {{ m.label }}
            </button>
            <button
              v-if="modeFilter.length"
              type="button"
              class="legend-reset"
              title="显示全部线路"
              @click="clearModeFilter"
            >全部</button>
          </template>
        </PhotoClusterMap>
      </div>
    </template>

    <el-dialog
      v-model="viewerOpen"
      fullscreen
      append-to-body
      destroy-on-close
      class="share-viewer-dialog"
      :show-close="true"
    >
      <div class="viewer-body" v-if="currentMedia">
        <video
          v-if="currentMedia.fileType === 2"
          :src="mediaSrc(currentMedia)"
          controls
          autoplay
          class="viewer-media"
        />
        <img v-else :src="mediaSrc(currentMedia)" class="viewer-media" alt="" />
        <div class="viewer-nav">
          <el-button circle :disabled="viewerIndex <= 0" @click="viewerIndex--">
            <el-icon><ArrowLeft /></el-icon>
          </el-button>
          <span>{{ viewerIndex + 1 }} / {{ photos.length }}</span>
          <el-button circle :disabled="viewerIndex >= photos.length - 1" @click="viewerIndex++">
            <el-icon><ArrowRight /></el-icon>
          </el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup name="ShareAlbumView">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { isExternal } from '@/utils/validate'
import PhotoClusterMap from '@/components/PhotoClusterMap/index.vue'
import {
  TRAVEL_MODES,
  travelModeIconSvg
} from '@/utils/photoMapCluster'
import {
  resolveShare,
  listSharePhotos,
  listShareMapPoints,
  listShareTracks,
  getShareTrackPoints
} from '@/api/photos/share'

const route = useRoute()

const loading = ref(true)
const loadingMore = ref(false)
const errorMsg = ref('')
const album = ref({})
const shareTrack = ref(false)
const photos = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = 60
const hasMore = ref(true)
const viewMode = ref('grid')
const mapReady = ref(false)
const mapPoints = ref([])
const trackPoints = ref([])
const trackGpxOverlays = ref([])
const trackColor = ref('#3B82F6')
const trackName = ref('')
const showTrackLine = ref(true)
const tracksOnly = ref(false)
const modeFilter = ref([])

const viewerOpen = ref(false)
const viewerIndex = ref(0)

const token = computed(() => String(route.params.token || '').trim())
const currentMedia = computed(() => photos.value[viewerIndex.value] || null)

const hasTrackData = computed(() =>
  trackPoints.value.length > 1 || trackGpxOverlays.value.some(o => {
    if (!o || o.showPath === false) return false
    return (Array.isArray(o.path) && o.path.length >= 2) || Number(o.pathPointCount) >= 2
  })
)

const trackLineActive = computed(() =>
  !!shareTrack.value && !!showTrackLine.value && hasTrackData.value
)

const mapDisplayPoints = computed(() => {
  // 地图点：有定位的分享媒体；轨迹点用于折线（polylinePoints）
  return mapPoints.value
})

const mapEmptyText = computed(() => {
  if (tracksOnly.value && trackLineActive.value) return '暂无轨迹'
  return '暂无定位或轨迹'
})

const visibleTravelModes = computed(() => (
  modeFilter.value.length ? modeFilter.value.slice() : null
))

const legendItems = computed(() => {
  if (!trackLineActive.value) return []
  const keys = new Set()
  trackPoints.value.forEach((p, idx) => {
    if (idx >= trackPoints.value.length - 1) return
    if (p?.travelMode) keys.add(String(p.travelMode).toLowerCase())
  })
  trackGpxOverlays.value.forEach(o => {
    if (!o || o.showPath === false) return
    const hasPath = (Array.isArray(o.path) && o.path.length >= 2) || Number(o.pathPointCount) >= 2
    if (!hasPath) return
    const segs = Array.isArray(o.segments) ? o.segments : []
    if (segs.length) {
      segs.forEach(s => {
        if (s?.travelMode) keys.add(String(s.travelMode).toLowerCase())
      })
    } else if (o.travelMode) {
      keys.add(String(o.travelMode).toLowerCase())
    }
  })
  return TRAVEL_MODES.filter(m => keys.has(m.key))
})

function isModeFilterActive(key) {
  return modeFilter.value.includes(key)
}

function modeFilterHint(m) {
  if (!modeFilter.value.length) return `点击仅显示「${m.label}」线路`
  if (isModeFilterActive(m.key)) return `再次点击取消「${m.label}」筛选`
  return `叠加显示「${m.label}」线路`
}

function toggleModeFilter(key) {
  const cur = modeFilter.value
  if (cur.includes(key)) {
    modeFilter.value = cur.filter(k => k !== key)
  } else {
    modeFilter.value = [...cur, key]
  }
}

function clearModeFilter() {
  modeFilter.value = []
}

function onTracksOnlyChange(val) {
  if (val && !showTrackLine.value && shareTrack.value) {
    showTrackLine.value = true
  }
}

watch(showTrackLine, (on) => {
  if (!on) {
    tracksOnly.value = false
    modeFilter.value = []
  }
})

watch(legendItems, (items) => {
  const keys = new Set(items.map(m => m.key))
  modeFilter.value = modeFilter.value.filter(k => keys.has(k))
})

function resolveUrl(url) {
  if (!url) return ''
  if (isExternal(url)) return url
  return import.meta.env.VITE_APP_BASE_API + url
}

function thumbSrc(item) {
  if (!item) return ''
  if (item.thumbUrl) return resolveUrl(item.thumbUrl)
  if (item.photoId) return resolveUrl('/album/photo/thumb/' + item.photoId)
  return ''
}

function mediaSrc(item) {
  if (!item?.photoId) return ''
  return resolveUrl('/album/photo/media/' + item.photoId)
}

function openViewer(index) {
  viewerIndex.value = index
  viewerOpen.value = true
}

async function loadMeta() {
  const res = await resolveShare(token.value)
  const data = res.data || {}
  album.value = data.album || {}
  shareTrack.value = !!(data.share && data.share.shareTrack)
  document.title = (album.value.albumName || '分享相册') + ' - 分享'
}

async function loadPhotos(reset = false) {
  if (reset) {
    pageNum.value = 1
    photos.value = []
    hasMore.value = true
  }
  if (!hasMore.value) return
  const isFirst = pageNum.value === 1
  if (isFirst) loadingMore.value = false
  else loadingMore.value = true
  try {
    const res = await listSharePhotos(token.value, {
      pageNum: pageNum.value,
      pageSize
    })
    const rows = res.rows || []
    total.value = Number(res.total) || 0
    photos.value = isFirst ? rows : photos.value.concat(rows)
    hasMore.value = photos.value.length < total.value
    if (rows.length) pageNum.value += 1
    else hasMore.value = false
  } finally {
    loadingMore.value = false
  }
}

function loadMore() {
  if (loading.value || loadingMore.value || !hasMore.value || viewMode.value !== 'grid') return
  loadPhotos(false)
}

function onWindowScroll() {
  if (viewMode.value !== 'grid' || !hasMore.value || loading.value || loadingMore.value) return
  const remain = document.documentElement.scrollHeight - window.scrollY - window.innerHeight
  if (remain < 240) loadMore()
}

async function loadMapData() {
  const [pointsRes, tracksRes] = await Promise.all([
    listShareMapPoints(token.value),
    shareTrack.value ? listShareTracks(token.value) : Promise.resolve({ data: [] })
  ])
  mapPoints.value = Array.isArray(pointsRes.data) ? pointsRes.data : []
  const tracks = Array.isArray(tracksRes.data) ? tracksRes.data : []
  trackPoints.value = []
  trackGpxOverlays.value = []
  trackName.value = ''
  if (tracks.length) {
    const first = tracks[0]
    trackColor.value = first.trackColor || '#3B82F6'
    trackName.value = first.trackName || ''
    const detail = await getShareTrackPoints(token.value, first.trackId)
    const data = detail.data || {}
    const track = data.track || first
    trackName.value = track.trackName || trackName.value
    trackColor.value = track.trackColor || trackColor.value
    const pts = Array.isArray(data.points) ? data.points : []
    trackPoints.value = pts
      .filter(p => p.latitude != null && p.longitude != null)
      .map(p => ({
        ...p,
        latitude: p.latitude,
        longitude: p.longitude,
        travelMode: p.travelMode
      }))
    trackGpxOverlays.value = Array.isArray(data.gpxOverlays) ? data.gpxOverlays : []
  }
  showTrackLine.value = shareTrack.value && (
    trackPoints.value.length > 1 || trackGpxOverlays.value.length > 0
  )
  mapReady.value = true
}

async function toggleMap() {
  if (viewMode.value === 'map') {
    viewMode.value = 'grid'
    return
  }
  viewMode.value = 'map'
  if (!mapReady.value) {
    loading.value = true
    try {
      await loadMapData()
    } finally {
      loading.value = false
    }
  }
}

onMounted(async () => {
  window.addEventListener('scroll', onWindowScroll, { passive: true })
  if (!token.value) {
    errorMsg.value = '无效的分享链接'
    loading.value = false
    return
  }
  loading.value = true
  try {
    await loadMeta()
    await loadPhotos(true)
  } catch (e) {
    errorMsg.value = e?.message || '分享不存在或已失效'
  } finally {
    loading.value = false
  }
})

onUnmounted(() => {
  window.removeEventListener('scroll', onWindowScroll)
})

watch(
  () => route.params.token,
  async () => {
    if (!token.value) return
    errorMsg.value = ''
    viewMode.value = 'grid'
    mapReady.value = false
    trackPoints.value = []
    trackGpxOverlays.value = []
    modeFilter.value = []
    tracksOnly.value = false
    loading.value = true
    try {
      await loadMeta()
      await loadPhotos(true)
    } catch (e) {
      errorMsg.value = e?.message || '分享不存在或已失效'
    } finally {
      loading.value = false
    }
  }
)
</script>

<style scoped>
.share-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background:
    radial-gradient(1200px 600px at 10% -10%, rgba(59, 130, 246, 0.10), transparent 55%),
    radial-gradient(900px 500px at 90% 0%, rgba(16, 185, 129, 0.08), transparent 50%),
    linear-gradient(180deg, #f8fafc 0%, #eef2f7 100%);
  color: #1f2937;
}

.share-page.is-map {
  height: 100vh;
  overflow: hidden;
}

.share-header {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 20px;
  background: rgba(255, 255, 255, 0.92);
  border-bottom: 1px solid #e5e7eb;
  backdrop-filter: blur(8px);
}

.share-header-main {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  min-width: 0;
  flex: 1;
}

.share-brand {
  flex-shrink: 0;
  margin-top: 6px;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.06);
  color: #475569;
  font-size: 12px;
  letter-spacing: 0.04em;
}

.share-title-wrap {
  min-width: 0;
}

.share-title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.02em;
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.share-desc {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.45;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.share-meta {
  margin: 6px 0 0;
  color: #94a3b8;
  font-size: 12px;
}

.share-actions {
  flex-shrink: 0;
}

.share-grid-wrap {
  flex: 1;
  width: 100%;
  padding: 12px 12px 28px;
  box-sizing: border-box;
}

.share-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 8px;
  width: 100%;
}

.share-card {
  position: relative;
  aspect-ratio: 1;
  margin: 0;
  padding: 0;
  border: 0;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  background: #e2e8f0;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.06);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.share-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.12);
}

.share-card img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.video-badge {
  position: absolute;
  left: 8px;
  bottom: 8px;
  padding: 2px 6px;
  border-radius: 4px;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 11px;
}

.share-empty,
.share-error {
  padding: 80px 24px;
}

.share-load-more {
  display: flex;
  justify-content: center;
  padding: 16px 0 8px;
}

.share-end {
  color: #94a3b8;
  font-size: 12px;
}

.share-map-wrap {
  flex: 1;
  min-height: 0;
  width: 100%;
  position: relative;
}

.share-map-wrap :deep(.photo-cluster-map) {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  border-radius: 0;
}

.header-toggles {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.track-toggle {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  padding: 4px 10px;
  border-radius: 8px;
  background: rgba(245, 247, 250, 0.96);
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.08);
}

.track-toggle-label {
  font-size: 13px;
  color: #606266;
  white-space: nowrap;
}

.map-meta {
  display: inline-flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 12px;
  padding: 6px 10px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
  font-size: 13px;
  color: #606266;
}

.map-meta .name {
  font-weight: 600;
  color: #303133;
}

.map-meta .stat.track {
  color: #2563eb;
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin: 0;
  padding: 4px 8px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: #606266;
  font-size: 12px;
  line-height: 1;
  cursor: pointer;
  transition: background 0.15s ease, opacity 0.15s ease, color 0.15s ease;
}

.legend-item:hover {
  background: rgba(0, 0, 0, 0.06);
  color: #303133;
}

.legend-item.active {
  background: rgba(37, 99, 235, 0.12);
  color: #1d4ed8;
  font-weight: 600;
}

.legend-item.muted {
  opacity: 0.38;
}

.legend-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  color: #fff;
  flex-shrink: 0;
}

.legend-icon :deep(svg) {
  display: block;
  fill: #fff;
}

.legend-reset {
  margin: 0 0 0 2px;
  padding: 4px 8px;
  border: 0;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.06);
  color: #606266;
  font-size: 12px;
  line-height: 1;
  cursor: pointer;
}

.viewer-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: calc(100vh - 80px);
  gap: 16px;
  background: #0f172a;
}

.viewer-media {
  max-width: min(96vw, 1400px);
  max-height: calc(100vh - 140px);
  object-fit: contain;
}

.viewer-nav {
  display: flex;
  align-items: center;
  gap: 16px;
  color: #e2e8f0;
}

@media (max-width: 768px) {
  .share-header {
    padding: 12px;
    align-items: flex-start;
  }

  .share-title {
    font-size: 18px;
    white-space: normal;
  }

  .share-grid {
    grid-template-columns: repeat(auto-fill, minmax(110px, 1fr));
    gap: 6px;
  }

  .share-grid-wrap {
    padding: 8px 8px 24px;
  }

  .header-toggles {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>

<style>
.share-viewer-dialog .el-dialog__header {
  margin: 0;
  padding: 8px 16px;
  background: #0f172a;
}

.share-viewer-dialog .el-dialog__body {
  padding: 0;
  background: #0f172a;
}

.share-viewer-dialog .el-dialog__headerbtn .el-dialog__close {
  color: #e2e8f0;
}
</style>
