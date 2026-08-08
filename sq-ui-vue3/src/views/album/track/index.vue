<template>
  <div class="app-container">
    <el-form :model="queryParams" :inline="true">
      <el-form-item label="相册">
        <el-select
          v-model="queryParams.albumId"
          placeholder="全部相册"
          clearable
          filterable
          style="width: 220px"
        >
          <el-option
            v-for="item in albumOptions"
            :key="item.albumId"
            :label="albumLabel(item)"
            :value="item.albumId"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="轨迹名称">
        <el-input
          v-model="queryParams.trackName"
          placeholder="轨迹名称"
          clearable
          style="width: 200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="openGenerate" v-hasPermi="['album:track:generate']">生成轨迹</el-button>
      </el-col>
    </el-row>

    <el-table v-loading="loading" :data="trackList">
      <el-table-column label="ID" prop="trackId" width="80" />
      <el-table-column label="相册" min-width="140" :show-overflow-tooltip="true">
        <template #default="scope">
          {{ albumNameMap[scope.row.albumId] || scope.row.albumId || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="名称" prop="trackName" />
      <el-table-column label="点位数" prop="pointCount" width="90" />
      <el-table-column label="里程(km)" prop="totalDistance" width="100" />
      <el-table-column label="时长" min-width="160">
        <template #default="scope">{{ formatDuration(scope.row.totalDuration) }}</template>
      </el-table-column>
      <el-table-column label="公开" prop="isPublic" width="80">
        <template #default="scope">{{ scope.row.isPublic === 1 ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="180">
        <template #default="scope">
          <el-button link type="primary" @click="handleView(scope.row)">查看轨迹</el-button>
          <el-button link type="danger" @click="handleDelete(scope.row)" v-hasPermi="['album:track:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog title="生成轨迹" v-model="genOpen" width="480px">
      <el-form label-width="90px">
        <el-form-item label="相册" required>
          <el-select v-model="genForm.albumId" placeholder="选择相册" filterable style="width: 100%">
            <el-option
              v-for="item in albumOptions"
              :key="item.albumId"
              :label="albumLabel(item)"
              :value="item.albumId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="轨迹名称">
          <el-input v-model="genForm.trackName" placeholder="可选，默认：相册名称相册轨迹1" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" :loading="genLoading" @click="submitGenerate">生成</el-button>
        <el-button @click="genOpen = false">取消</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="detailOpen"
      title="轨迹查看"
      width="100%"
      top="0"
      class="track-view-dialog"
      modal-class="track-view-modal"
      destroy-on-close
      append-to-body
      @opened="onViewerOpened"
      @closed="stopViewerRectSync"
    >
      <div v-loading="detailLoading" class="track-view-body">
        <TrackMapViewer
          v-if="detailOpen && !detailLoading"
          ref="mapViewerRef"
          :track="detailTrack"
          :points="detailPoints"
        />
      </div>
    </el-dialog>
  </div>
</template>

<script setup name="AlbumTrack">
import { listAlbum } from '@/api/album/album'
import { listTrack, getTrack, generateTrack, delTrack } from '@/api/album/track'
import TrackMapViewer from '@/components/TrackMapViewer/index.vue'
import useAppStore from '@/store/modules/app'

const { proxy } = getCurrentInstance()
const appStore = useAppStore()
const trackList = ref([])
const albumOptions = ref([])
const albumNameMap = ref({})
const loading = ref(true)
const total = ref(0)
const genOpen = ref(false)
const genLoading = ref(false)
const detailOpen = ref(false)
const detailLoading = ref(false)
const detailTrack = ref(null)
const detailPoints = ref([])
const mapViewerRef = ref(null)
let viewerRectRaf = 0
const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  albumId: undefined,
  trackName: undefined
})
const genForm = ref({ albumId: undefined, trackName: '' })

function albumLabel(item) {
  if (!item) return ''
  return `${item.albumName || '未命名'}（ID:${item.albumId}）`
}

function formatDuration(sec) {
  if (sec == null || sec === '') return '-'
  const totalSec = Math.max(0, Math.floor(Number(sec) || 0))
  const h = Math.floor(totalSec / 3600)
  const m = Math.floor((totalSec % 3600) / 60)
  const r = totalSec % 60
  return `${h}小时${m}分钟${r}秒`
}

function loadAlbums() {
  return listAlbum({ pageNum: 1, pageSize: 500 }).then(res => {
    const rows = res.rows || []
    albumOptions.value = rows
    const map = {}
    rows.forEach(item => {
      map[item.albumId] = item.albumName
    })
    albumNameMap.value = map
  })
}

function getList() {
  loading.value = true
  listTrack(queryParams.value).then(res => {
    trackList.value = res.rows || []
    total.value = res.total || 0
  }).finally(() => {
    loading.value = false
  })
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  queryParams.value = {
    pageNum: 1,
    pageSize: 10,
    albumId: undefined,
    trackName: undefined
  }
  getList()
}

function openGenerate() {
  genForm.value = {
    albumId: queryParams.value.albumId || undefined,
    trackName: ''
  }
  genOpen.value = true
}

function syncViewerRect() {
  const main = document.querySelector('.app-main')
  const modal = document.querySelector('.track-view-modal')
  if (!main || !modal) return
  const rect = main.getBoundingClientRect()
  const top = Math.max(0, rect.top)
  const left = Math.max(0, rect.left)
  const width = Math.max(0, Math.min(window.innerWidth, rect.right) - left)
  const height = Math.max(0, Math.min(window.innerHeight, rect.bottom) - top)
  modal.style.setProperty('--track-view-top', `${top}px`)
  modal.style.setProperty('--track-view-left', `${left}px`)
  modal.style.setProperty('--track-view-width', `${width}px`)
  modal.style.setProperty('--track-view-height', `${height}px`)
}

function scheduleViewerRectSync() {
  cancelAnimationFrame(viewerRectRaf)
  viewerRectRaf = requestAnimationFrame(syncViewerRect)
}

function startViewerRectSync() {
  syncViewerRect()
  window.addEventListener('resize', scheduleViewerRectSync)
}

function stopViewerRectSync() {
  cancelAnimationFrame(viewerRectRaf)
  window.removeEventListener('resize', scheduleViewerRectSync)
}

function openTrackViewer(trackId) {
  if (!trackId) return
  detailLoading.value = true
  detailOpen.value = true
  detailTrack.value = null
  detailPoints.value = []
  nextTick(() => {
    startViewerRectSync()
  })
  getTrack(trackId).then(res => {
    const data = res.data || {}
    detailTrack.value = data.track || null
    detailPoints.value = data.points || []
  }).catch(() => {
    detailOpen.value = false
  }).finally(() => {
    detailLoading.value = false
  })
}

function onViewerOpened() {
  syncViewerRect()
  mapViewerRef.value?.refresh?.()
  // 侧栏动画结束后再对齐一次，确保贴合白色内容区
  setTimeout(() => {
    syncViewerRect()
    mapViewerRef.value?.refresh?.()
  }, 320)
}

watch(() => appStore.sidebar.opened, () => {
  if (!detailOpen.value) return
  scheduleViewerRectSync()
  setTimeout(() => {
    syncViewerRect()
    mapViewerRef.value?.refresh?.()
  }, 320)
})

onBeforeUnmount(() => {
  stopViewerRectSync()
})

function submitGenerate() {
  if (!genForm.value.albumId) {
    proxy.$modal.msgError('请选择相册')
    return
  }
  genLoading.value = true
  generateTrack(genForm.value).then(res => {
    proxy.$modal.msgSuccess('生成成功')
    genOpen.value = false
    getList()
    const track = res.data
    if (track?.trackId) {
      openTrackViewer(track.trackId)
    }
  }).finally(() => {
    genLoading.value = false
  })
}

function handleView(row) {
  openTrackViewer(row.trackId)
}

function handleDelete(row) {
  proxy.$modal.confirm('确认删除该轨迹？').then(() => delTrack(row.trackId)).then(() => {
    getList()
    proxy.$modal.msgSuccess('删除成功')
  }).catch(() => {})
}

loadAlbums().finally(() => getList())
</script>

<style>
/* 遮罩与弹窗都对齐主内容区（侧栏右侧、顶栏下方的白色区域） */
.track-view-modal.el-overlay {
  --track-view-top: 84px;
  --track-view-left: 200px;
  --track-view-width: calc(100vw - 200px);
  --track-view-height: calc(100vh - 84px);
  top: var(--track-view-top) !important;
  left: var(--track-view-left) !important;
  right: auto !important;
  bottom: auto !important;
  width: var(--track-view-width) !important;
  height: var(--track-view-height) !important;
}

.track-view-modal .el-overlay-dialog {
  position: absolute !important;
  inset: 0 !important;
  overflow: hidden;
  display: flex !important;
  align-items: stretch !important;
  justify-content: stretch !important;
  padding: 0 !important;
}

/* 覆盖全局 .el-dialog:not(.is-fullscreen){ margin-top:6vh }，避免底部留白 */
.track-view-dialog.el-dialog,
.track-view-dialog.el-dialog:not(.is-fullscreen) {
  margin: 0 !important;
  margin-top: 0 !important;
  width: 100% !important;
  height: 100% !important;
  max-width: none !important;
  max-height: none !important;
  border-radius: 0;
  display: flex;
  flex-direction: column;
  box-shadow: none;
}

.track-view-dialog .el-dialog__header {
  flex-shrink: 0;
  padding: 10px 16px;
  margin-right: 0;
  border-bottom: 1px solid var(--el-border-color-lighter, #ebeef5);
}

.track-view-dialog .el-dialog__body {
  flex: 1 1 auto;
  height: auto !important;
  max-height: none !important;
  min-height: 0;
  padding: 0 !important;
  box-sizing: border-box;
  overflow: hidden;
}

.track-view-dialog .track-view-body {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 0;
}
</style>
