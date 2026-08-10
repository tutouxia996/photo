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
      <el-table-column label="启用轨迹" width="110" align="center">
        <template #default="scope">
          <el-switch
            :model-value="scope.row.enabled !== 0"
            :loading="enabledLoadingId === scope.row.trackId"
            :disabled="!canEditTrack"
            inline-prompt
            active-text="开"
            inactive-text="关"
            @change="(val) => handleEnabledChange(scope.row, val)"
          />
        </template>
      </el-table-column>
      <el-table-column label="公开" prop="isPublic" width="80">
        <template #default="scope">{{ scope.row.isPublic === 1 ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column label="行程说明" prop="remark" min-width="160" :show-overflow-tooltip="true" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="scope">
          <el-button link type="primary" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="primary" @click="handleEdit(scope.row)" v-hasPermi="['album:track:edit']">编辑</el-button>
          <el-button link type="danger" @click="handleDelete(scope.row)" v-hasPermi="['album:track:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog title="生成轨迹" v-model="genOpen" width="480px" append-to-body>
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

    <el-dialog title="编辑轨迹" v-model="editOpen" width="560px" append-to-body destroy-on-close>
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="90px">
        <el-form-item label="轨迹名称" prop="trackName">
          <el-input v-model="editForm.trackName" maxlength="100" show-word-limit placeholder="轨迹名称" />
        </el-form-item>
        <el-form-item label="轨迹颜色" prop="trackColor">
          <el-color-picker v-model="editForm.trackColor" />
          <span class="color-tip">{{ editForm.trackColor || '#3B82F6' }}</span>
        </el-form-item>
        <el-form-item label="公开" prop="isPublic">
          <el-radio-group v-model="editForm.isPublic">
            <el-radio :value="1">公开</el-radio>
            <el-radio :value="0">私有</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="行程说明" prop="remark">
          <el-input
            v-model="editForm.remark"
            type="textarea"
            :rows="5"
            maxlength="500"
            show-word-limit
            placeholder="可用文字描述行程，例如：北京南站乘坐G123次高铁至八达岭，步行至钟楼登山"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" :loading="editLoading" @click="submitEdit">保存</el-button>
        <el-button @click="editOpen = false">取消</el-button>
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
      :z-index="1990"
      @opened="onViewerOpened"
      @closed="stopViewerRectSync"
    >
      <div v-loading="detailLoading" class="track-view-body">
        <TrackMapViewer
          v-if="detailOpen && !detailLoading"
          ref="mapViewerRef"
          :track="detailTrack"
          :points="detailPoints"
          :editable="canEditTrack && isTrackEnabled(detailTrack)"
          @saved="onTrackMapSaved"
          @replan="onTrackReplan"
        />
      </div>
    </el-dialog>
  </div>
</template>

<script setup name="AlbumTrack">
import { listAlbum } from '@/api/album/album'
import {
  listTrack,
  getTrack,
  generateTrack,
  updateTrack,
  delTrack,
  resolveTrackRoutes
} from '@/api/album/track'
import TrackMapViewer from '@/components/TrackMapViewer/index.vue'
import { hasMissingRoutePaths, hasUnstableAutoRoutes } from '@/utils/photoMapCluster'
import { checkPermi } from '@/utils/permission'
import useAppStore from '@/store/modules/app'

const { proxy } = getCurrentInstance()
const appStore = useAppStore()
const canEditTrack = computed(() => checkPermi(['album:track:edit']))
const trackList = ref([])
const albumOptions = ref([])
const albumNameMap = ref({})
const loading = ref(true)
const total = ref(0)
const genOpen = ref(false)
const genLoading = ref(false)
const editOpen = ref(false)
const editLoading = ref(false)
const editFormRef = ref(null)
const enabledLoadingId = ref(null)
const editForm = ref({
  trackId: undefined,
  trackName: '',
  trackColor: '#3B82F6',
  isPublic: 1,
  remark: ''
})
const editRules = {
  trackName: [{ required: true, message: '轨迹名称不能为空', trigger: 'blur' }]
}
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

function isTrackEnabled(track) {
  return track == null || track.enabled == null || track.enabled === 1
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
  // 顶边贴标签栏底边，避免盖住「首页 / 轨迹」页签与右键菜单区域
  const tags = document.querySelector('#tags-view-container')
  const header = document.querySelector('.fixed-header')
  const mainRect = main.getBoundingClientRect()
  const top = tags
    ? Math.max(0, Math.ceil(tags.getBoundingClientRect().bottom))
    : header
      ? Math.max(0, Math.ceil(header.getBoundingClientRect().bottom))
      : 84
  const left = Math.max(0, mainRect.left)
  const width = Math.max(0, Math.min(window.innerWidth, mainRect.right) - left)
  const height = Math.max(0, window.innerHeight - top)
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

async function loadTrackWithRoutes(trackId, force = false) {
  const res = await getTrack(trackId)
  const data = res.data || {}
  let track = data.track || null
  let points = data.points || []
  // 打开查看：缺折线才修补；不要因个别直线段 force 全量重算（会把上百段又打一遍高德/OSM）
  const needRepair = force || hasMissingRoutePaths(points) || hasUnstableAutoRoutes(points)
  if (!needRepair) {
    return { track, points }
  }
  try {
    // 仅用户点「贴合路网」时 force；自动打开只用增量修补
    const useForce = !!force
    for (let i = 0; i < 3; i++) {
      const statsRes = await resolveTrackRoutes(trackId, useForce && i === 0, 'photo')
      const stats = statsRes.data || {}
      if (!stats.remaining || stats.remaining <= 0) break
    }
    const fresh = await getTrack(trackId)
    track = fresh.data?.track || track
    points = fresh.data?.points || points
  } catch (e) {
    console.warn('resolve track routes failed', e)
  }
  return { track, points }
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
  // 直接读库展示已保存折线，不自动重算（重算仅手动「贴合路网」）
  getTrack(trackId).then(res => {
    const data = res.data || {}
    detailTrack.value = data.track || null
    detailPoints.value = data.points || []
    nextTick(() => mapViewerRef.value?.refresh?.({ fit: true }))
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

function handleEdit(row) {
  editForm.value = {
    trackId: row.trackId,
    trackName: row.trackName || '',
    trackColor: row.trackColor || '#3B82F6',
    isPublic: row.isPublic == null ? 1 : row.isPublic,
    remark: row.remark || ''
  }
  editOpen.value = true
  nextTick(() => editFormRef.value?.clearValidate?.())
}

function submitEdit() {
  editFormRef.value?.validate?.(valid => {
    if (!valid) return
    editLoading.value = true
    updateTrack({
      trackId: editForm.value.trackId,
      trackName: editForm.value.trackName,
      trackColor: editForm.value.trackColor || '#3B82F6',
      isPublic: editForm.value.isPublic,
      remark: editForm.value.remark ?? ''
    }).then(() => {
      proxy.$modal.msgSuccess('保存成功')
      editOpen.value = false
      getList()
    }).finally(() => {
      editLoading.value = false
    })
  })
}

async function handleEnabledChange(row, val) {
  if (!row?.trackId) return
  const enabled = val ? 1 : 0
  const prev = row.enabled == null ? 1 : row.enabled
  if (prev === enabled) return
  enabledLoadingId.value = row.trackId
  row.enabled = enabled
  try {
    await updateTrack({ trackId: row.trackId, enabled })
    if (enabled === 1) {
      proxy.$modal.msgSuccess('已开启：将自动同步生成，并在地图展示')
    } else {
      proxy.$modal.msgSuccess('已关闭：保留轨迹数据，地图不再展示')
    }
    getList()
  } catch (e) {
    row.enabled = prev
    proxy.$modal.msgError('更新失败')
  } finally {
    enabledLoadingId.value = null
  }
}

function onTrackMapSaved() {
  const trackId = detailTrack.value?.trackId
  if (!trackId) {
    getList()
    return
  }
  // 保存后只重新拉取，不再自动 resolve（避免覆盖用户刚改的出行方式）
  getTrack(trackId).then((res) => {
    const data = res.data || {}
    detailTrack.value = data.track || detailTrack.value
    detailPoints.value = data.points || []
    getList()
    nextTick(() => mapViewerRef.value?.refresh?.({ fit: false }))
  }).catch(() => {
    getList()
  })
}

function onTrackReplan({ trackId, done }) {
  const id = trackId || detailTrack.value?.trackId
  if (!id) {
    done?.()
    return
  }
  loadTrackWithRoutes(id, true).then(({ track, points }) => {
    detailTrack.value = track || detailTrack.value
    detailPoints.value = points || []
    proxy.$modal.msgSuccess('已按照片坐标重新贴合路网')
    nextTick(() => mapViewerRef.value?.refresh?.({ fit: true }))
  }).catch(() => {
    proxy.$modal.msgError('重新贴合失败，请检查高德 Key 或稍后重试')
  }).finally(() => {
    done?.()
  })
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
.color-tip {
  margin-left: 10px;
  color: #909399;
  font-size: 13px;
  vertical-align: middle;
}

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
  justify-content: flex-start !important;
  padding: 0 !important;
}

/* 覆盖全局 margin-top:6vh / dialog 默认 padding与底边距，让地图铺满白色内容区 */
.track-view-modal .track-view-dialog.el-dialog,
.track-view-dialog.el-dialog,
.track-view-dialog.el-dialog:not(.is-fullscreen) {
  --el-dialog-margin-top: 0;
  --el-dialog-padding-primary: 0;
  margin: 0 !important;
  margin-top: 0 !important;
  margin-bottom: 0 !important;
  width: 100% !important;
  height: 100% !important;
  max-width: none !important;
  max-height: 100% !important;
  border-radius: 0;
  padding: 0 !important;
  display: flex !important;
  flex-direction: column;
  box-shadow: none;
  overflow: hidden;
  box-sizing: border-box;
}

.track-view-dialog .el-dialog__header {
  flex: 0 0 auto;
  padding: 10px 16px !important;
  margin: 0 !important;
  border-bottom: 1px solid var(--el-border-color-lighter, #ebeef5);
}

/* flex:1 + height:0 让 body 拿到确定高度；子元素才能 100% 撑满 */
.track-view-dialog .el-dialog__body {
  flex: 1 1 0 !important;
  height: 0 !important;
  max-height: none !important;
  min-height: 0 !important;
  padding: 0 !important;
  margin: 0 !important;
  box-sizing: border-box;
  overflow: hidden;
  position: relative;
}

.track-view-dialog .track-view-body,
.track-view-dialog .track-map-viewer,
.track-view-dialog .photo-cluster-map {
  position: absolute !important;
  inset: 0 !important;
  width: 100% !important;
  height: 100% !important;
  min-height: 0 !important;
  overflow: hidden;
}
</style>
