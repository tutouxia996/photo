<template>
  <div class="share-page" :class="{ 'is-map': viewMode === 'map' }" v-loading="loading">
    <header class="share-header">
      <div class="share-header-main">
        <div class="share-brand">分享相册</div>
        <div class="share-title-wrap">
          <h1 class="share-title">{{ pageTitle }}</h1>
          <p v-if="!isMultiAlbum && album.albumDesc" class="share-desc">{{ album.albumDesc }}</p>
          <div v-if="isMultiAlbum" class="share-album-chips">
            <span v-for="a in albums" :key="a.albumId" class="share-album-chip" :title="a.albumName">
              {{ a.albumName || '未命名相册' }}
            </span>
          </div>
          <p class="share-meta">
            <span>共 {{ total || photos.length }} 项</span>
            <span v-if="isMultiAlbum">· {{ albums.length }} 个相册</span>
            <span v-if="photos.length && total && photos.length < total">· 已加载 {{ photos.length }}</span>
            <span v-if="!isMultiAlbum && album.locationSummary">· {{ album.locationSummary }}</span>
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
            <span v-else-if="isPanoPhoto(item)" class="pano-badge" title="360 全景">360</span>
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
              <span class="name">{{ pageTitle }}</span>
              <span class="stat">定位 {{ mapPoints.length }}</span>
              <span v-if="isMultiAlbum" class="stat">{{ albums.length }} 个相册</span>
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

    <!-- 与相册详情一致的白底全屏预览 -->
    <teleport to="body">
      <div v-if="viewerOpen && currentMedia" class="media-viewer" @keydown.stop>
        <div class="media-stage">
          <button
            v-if="viewerIndex > 0"
            type="button"
            class="media-nav prev"
            title="上一张"
            @click.stop="shiftMedia(-1)"
          >
            <el-icon :size="22"><ArrowLeft /></el-icon>
          </button>
          <button
            v-if="viewerIndex < photos.length - 1 || hasMore"
            type="button"
            class="media-nav next"
            title="下一张"
            @click.stop="shiftMedia(1)"
          >
            <el-icon :size="22"><ArrowRight /></el-icon>
          </button>

          <div
            v-if="isPanoViewing"
            class="media-pano-wrap"
            @click.stop
          >
            <PhotoSphereViewer :src="originalSrc(currentMedia)" :key="currentMedia.photoId" />
          </div>
          <div
            v-else-if="currentMedia.fileType !== 2"
            class="media-canvas"
          >
            <div class="media-image-layer">
              <img
                class="media-image"
                :src="originalSrc(currentMedia)"
                :alt="currentMedia.fileName || ''"
                draggable="false"
                decoding="async"
              />
            </div>
          </div>
          <div v-else class="media-video-wrap">
            <video
              :key="'v-' + currentMedia.photoId"
              class="media-video"
              :src="originalSrc(currentMedia)"
              controls
              autoplay
              playsinline
            />
          </div>

          <div class="media-index">
            {{ viewerIndex + 1 }} / {{ total || photos.length }}
          </div>
        </div>
      </div>
    </teleport>

    <teleport to="body">
      <template v-if="viewerOpen && currentMedia">
        <button
          type="button"
          class="media-close"
          :class="{ 'on-dark': isPanoViewing }"
          title="返回"
          @click="closeViewer"
        >
          <el-icon :size="22"><ArrowLeft /></el-icon>
        </button>
        <div class="media-actions" :class="{ 'on-dark': isPanoViewing }">
          <span v-if="isPanoViewing" class="media-pano-label">360 全景</span>
          <button
            v-if="isPanoViewing"
            type="button"
            class="media-action-btn media-close-x"
            title="关闭"
            @click="closeViewer"
          >
            <el-icon :size="20"><Close /></el-icon>
          </button>
        </div>
      </template>
    </teleport>
  </div>
</template>

<script setup name="ShareAlbumView">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { isExternal } from '@/utils/validate'
import PhotoClusterMap from '@/components/PhotoClusterMap/index.vue'
import PhotoSphereViewer from '@/components/PhotoSphereViewer/index.vue'
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
const albums = ref([])
const shareTrack = ref(false)
const photos = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = 200
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
const isPanoViewing = computed(() => isPanoPhoto(currentMedia.value))
const isMultiAlbum = computed(() => albums.value.length > 1)
const pageTitle = computed(() => {
  if (isMultiAlbum.value) {
    const first = albums.value[0]?.albumName || '相册'
    return `${first} 等${albums.value.length}个相册`
  }
  return album.value.albumName || '相册'
})

/** 与相册详情一致：库字段优先，文件名 PHOTOSPHERE / .PANO. 兜底 */
function isPanoPhoto(item) {
  if (!item || item.fileType === 2) return false
  if (item.isPano === 1 || item.isPano === true) return true
  const name = item.fileName || ''
  return /PHOTOSPHERE|\.PANO\./i.test(name)
}

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

/** 列表缩略图用；预览必须走原图/原视频 */
function originalSrc(item) {
  if (!item?.photoId) return ''
  return resolveUrl('/album/photo/media/' + item.photoId + '?original=true')
}

function openViewer(index) {
  viewerIndex.value = index
  viewerOpen.value = true
  ensureAllPhotosLoaded()
}

function closeViewer() {
  viewerOpen.value = false
}

async function shiftMedia(delta) {
  const next = viewerIndex.value + delta
  if (next < 0) return
  if (next < photos.value.length) {
    viewerIndex.value = next
    return
  }
  if (delta > 0 && hasMore.value) {
    await loadPhotos(false)
    if (viewerIndex.value < photos.value.length - 1) {
      viewerIndex.value += 1
    }
  }
}

function onViewerKeydown(e) {
  if (!viewerOpen.value) return
  if (e.key === 'Escape') {
    e.preventDefault()
    closeViewer()
  } else if (e.key === 'ArrowLeft') {
    e.preventDefault()
    shiftMedia(-1)
  } else if (e.key === 'ArrowRight') {
    e.preventDefault()
    shiftMedia(1)
  }
}

async function loadMeta() {
  const res = await resolveShare(token.value)
  const data = res.data || {}
  album.value = data.album || {}
  const list = Array.isArray(data.albums) ? data.albums : []
  albums.value = list.length ? list : (data.album ? [data.album] : [])
  shareTrack.value = !!(data.share && data.share.shareTrack)
  document.title = pageTitle.value + ' - 分享'
}

async function loadPhotos(reset = false) {
  if (reset) {
    pageNum.value = 1
    photos.value = []
    hasMore.value = true
  }
  if (!hasMore.value || loadingMore.value) return
  const isFirst = pageNum.value === 1 && photos.value.length === 0
  loadingMore.value = true
  try {
    const res = await listSharePhotos(token.value, {
      pageNum: pageNum.value,
      pageSize
    })
    const rows = res.rows || []
    total.value = Number(res.total) || 0
    photos.value = isFirst ? rows : photos.value.concat(rows)
    hasMore.value = photos.value.length < total.value && rows.length > 0
    if (rows.length) pageNum.value += 1
    else hasMore.value = false
  } finally {
    loadingMore.value = false
  }
}

/** 自动把剩余页全部拉完，避免只看到第一页 */
let allLoadPromise = null
async function ensureAllPhotosLoaded() {
  if (!hasMore.value) return
  if (allLoadPromise) return allLoadPromise
  allLoadPromise = (async () => {
    let guard = 0
    while (hasMore.value && guard < 50) {
      guard += 1
      await loadPhotos(false)
    }
  })().finally(() => {
    allLoadPromise = null
  })
  return allLoadPromise
}

/** 首次进入：先拉第一页，再把剩余页全部加载完 */
async function loadAllPhotos() {
  await loadPhotos(true)
  await ensureAllPhotosLoaded()
}

function loadMore() {
  if (loading.value || loadingMore.value || !hasMore.value || viewMode.value !== 'grid') return
  loadPhotos(false)
}

function onWindowScroll() {
  if (viewMode.value !== 'grid' || !hasMore.value || loading.value || loadingMore.value) return
  const remain = document.documentElement.scrollHeight - window.scrollY - window.innerHeight
  if (remain < 480) loadMore()
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
    const details = await Promise.all(
      tracks.map(t => getShareTrackPoints(token.value, t.trackId).catch(() => null))
    )
    const overlays = []
    details.forEach((detail, idx) => {
      if (!detail) return
      const data = detail.data || {}
      const track = data.track || tracks[idx] || {}
      const pts = (Array.isArray(data.points) ? data.points : [])
        .filter(p => p.latitude != null && p.longitude != null)
        .map(p => ({
          ...p,
          latitude: p.latitude,
          longitude: p.longitude,
          travelMode: p.travelMode
        }))
      const gpx = Array.isArray(data.gpxOverlays) ? data.gpxOverlays : []
      overlays.push(...gpx)
      if (idx === 0) {
        trackPoints.value = pts
        trackName.value = track.trackName || tracks[idx]?.trackName || ''
        trackColor.value = track.trackColor || tracks[idx]?.trackColor || '#3B82F6'
      } else if (pts.length >= 2) {
        // 其余相册轨迹作为叠加线路
        overlays.push({
          fileName: track.trackName || `轨迹${idx + 1}`,
          color: track.trackColor || '#3B82F6',
          showPath: true,
          path: pts.map(p => [Number(p.latitude), Number(p.longitude)]),
          pathPointCount: pts.length,
          travelMode: pts[0]?.travelMode || null,
          segments: []
        })
      }
    })
    if (tracks.length > 1) {
      trackName.value = trackName.value
        ? `${trackName.value} 等${tracks.length}条`
        : `${tracks.length} 条轨迹`
    }
    trackGpxOverlays.value = overlays
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
  window.addEventListener('keydown', onViewerKeydown)
  if (!token.value) {
    errorMsg.value = '无效的分享链接'
    loading.value = false
    return
  }
  loading.value = true
  try {
    await loadMeta()
    await loadAllPhotos()
  } catch (e) {
    errorMsg.value = e?.message || '分享不存在或已失效'
  } finally {
    loading.value = false
  }
})

onUnmounted(() => {
  window.removeEventListener('scroll', onWindowScroll)
  window.removeEventListener('keydown', onViewerKeydown)
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
      await loadAllPhotos()
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

.share-album-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
  max-height: 72px;
  overflow: auto;
}

.share-album-chip {
  max-width: 180px;
  padding: 3px 10px;
  border-radius: 999px;
  background: #f1f5f9;
  color: #475569;
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
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

.video-badge,
.pano-badge {
  position: absolute;
  left: 8px;
  bottom: 8px;
  padding: 2px 6px;
  border-radius: 4px;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 11px;
  line-height: 1.2;
}

.pano-badge {
  background: rgba(37, 99, 235, 0.85);
  letter-spacing: 0.02em;
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

/* —— 预览层：对齐相册详情 media-viewer —— */
.media-viewer {
  position: fixed;
  inset: 0;
  z-index: 3000;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  user-select: none;
  -webkit-user-select: none;
}

.media-stage {
  position: absolute;
  inset: 0;
  z-index: 1;
}

.media-close,
.media-nav {
  position: absolute;
  width: 40px;
  height: 40px;
  border: none;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.06);
  color: #333;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  z-index: 2;
  transition: background 0.15s ease;
}

.media-close:hover,
.media-nav:hover {
  background: rgba(0, 0, 0, 0.12);
}

.media-close {
  position: fixed;
  top: 20px;
  left: 20px;
  z-index: 3200;
}

.media-close.on-dark {
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.35);
}

.media-close.on-dark:hover {
  background: rgba(0, 0, 0, 0.72);
}

.media-nav.prev {
  left: 24px;
  top: 50%;
  transform: translateY(-50%);
}

.media-nav.next {
  right: 24px;
  top: 50%;
  transform: translateY(-50%);
}

.media-actions {
  position: fixed;
  top: 18px;
  right: 24px;
  z-index: 3200;
  display: flex;
  align-items: center;
  gap: 6px;
}

.media-pano-label {
  margin-right: 6px;
  padding: 6px 12px;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 12px;
}

.media-action-btn {
  width: 40px;
  height: 40px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #3a3a3a;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.media-actions.on-dark .media-action-btn {
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.35);
}

.media-actions.on-dark .media-action-btn:hover {
  background: rgba(0, 0, 0, 0.72);
}

.media-canvas {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  z-index: 1;
}

.media-pano-wrap {
  position: absolute;
  inset: 0;
  z-index: 1;
  overflow: hidden;
  background: #111;
}

.media-image-layer {
  display: flex;
  align-items: center;
  justify-content: center;
}

.media-image {
  width: auto;
  height: auto;
  object-fit: contain;
  border-radius: 2px;
  max-width: min(92vw, 1400px);
  max-height: min(86vh, 900px);
  pointer-events: none;
  user-select: none;
  -webkit-user-drag: none;
}

.media-video-wrap {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1;
}

.media-video {
  max-width: min(92%, 1400px);
  max-height: min(78%, 78vh);
  width: auto;
  height: auto;
  object-fit: contain;
  border-radius: 2px;
  background: #000;
}

.media-index {
  position: absolute;
  left: 50%;
  bottom: 24px;
  transform: translateX(-50%);
  z-index: 3;
  padding: 6px 14px;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
  font-size: 13px;
  letter-spacing: 0.02em;
  pointer-events: none;
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
