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
        <el-button
          type="primary"
          plain
          icon="Plus"
          @click="openGenerate"
          v-hasPermi="['album:track:generate']"
        >生成轨迹</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="info"
          plain
          icon="MapLocation"
          @click="openRegionLocate(queryParams.albumId)"
          v-hasPermi="['album:photo:edit']"
          title="无 GPS 相册：先定区域中心，再到照片地图确认"
        >区域定位</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="Location"
          :loading="fallbackLoading"
          @click="handleFallbackLocate(queryParams.albumId)"
          v-hasPermi="['album:photo:edit']"
          title="已有 GPS 锚点时，为无坐标照片按时间估计位置"
        >估计补点</el-button>
      </el-col>
    </el-row>

    <el-table v-loading="loading" :data="trackList">
      <el-table-column label="ID" prop="trackId" width="70" align="center" />
      <el-table-column label="相册" min-width="140" :show-overflow-tooltip="true">
        <template #default="scope">
          {{ albumNameMap[scope.row.albumId] || scope.row.albumId || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="名称" prop="trackName" min-width="200" :show-overflow-tooltip="true" />
      <el-table-column label="来源" width="70" align="center">
        <template #default="scope">{{ sourceTypeLabel(scope.row.sourceType) }}</template>
      </el-table-column>
      <el-table-column label="点位数" prop="pointCount" width="80" align="center" />
      <el-table-column prop="totalDistance" width="100" align="center">
        <template #header>
          <span title="不包含增补路段">里程(km)</span>
        </template>
      </el-table-column>
      <el-table-column width="150" align="center">
        <template #header>
          <span title="不包含增补路段">时长</span>
        </template>
        <template #default="scope">{{ formatDuration(scope.row.totalDuration) }}</template>
      </el-table-column>
      <el-table-column label="显示轨迹线" width="110" align="center">
        <template #default="scope">
          <el-switch
            :model-value="isTrackEnabledDisplay(scope.row)"
            :loading="enabledLoadingId === scope.row.trackId"
            :disabled="!canToggleTrackEnabled(scope.row)"
            inline-prompt
            active-text="开"
            inactive-text="关"
            :title="trackEnabledTitle(scope.row)"
            @change="(val) => handleEnabledChange(scope.row, val)"
          />
        </template>
      </el-table-column>
      <el-table-column label="启用GPX" width="100" align="center">
        <template #default="scope">
          <el-switch
            :model-value="isGpxEnabled(scope.row)"
            :loading="gpxEnabledLoadingId === scope.row.trackId"
            :disabled="!canEditTrack || !hasGpxFiles(scope.row)"
            inline-prompt
            active-text="开"
            inactive-text="关"
            :title="hasGpxFiles(scope.row) ? '控制地图是否显示 GPX 线路' : '请先导入 GPX'"
            @change="(val) => handleGpxEnabledChange(scope.row, val)"
          />
        </template>
      </el-table-column>
      <el-table-column label="公开" prop="isPublic" width="70" align="center">
        <template #default="scope">{{ scope.row.isPublic === 1 ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column label="行程说明" prop="remark" min-width="240" :show-overflow-tooltip="true" />
      <el-table-column label="操作" width="400" fixed="right">
        <template #default="scope">
          <el-button
            v-if="canShowPhotoMap(scope.row)"
            link
            type="primary"
            @click="handleView(scope.row)"
          >照片地图</el-button>
          <el-button
            v-if="canShowTrack(scope.row)"
            link
            type="primary"
            @click="openTrackViewer(scope.row.trackId)"
            v-hasPermi="['album:track:query']"
          >轨迹</el-button>
          <el-button
            link
            type="primary"
            @click="handleEdit(scope.row)"
            v-hasPermi="['album:track:edit']"
          >编辑</el-button>
          <el-button
            v-if="canShowRegionLocate(scope.row)"
            link
            type="info"
            @click="openRegionLocate(scope.row.albumId)"
            v-hasPermi="['album:photo:edit']"
          >区域定位</el-button>
          <el-button
            v-if="canShowFallbackLocate(scope.row)"
            link
            type="warning"
            @click="handleFallbackLocate(scope.row.albumId, scope.row)"
            v-hasPermi="['album:photo:edit']"
          >估计补点</el-button>
          <el-button
            v-if="canShowGpxImport(scope.row)"
            link
            type="success"
            @click="openGpxImport(scope.row)"
            v-hasPermi="['album:track:generate']"
          >导入GPX</el-button>
          <el-button
            link
            type="danger"
            @click="handleDelete(scope.row)"
            v-hasPermi="['album:track:remove']"
          >删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog title="生成轨迹" v-model="genOpen" width="520px" append-to-body>
      <p class="region-hint">
        <strong>本对话框主操作是「生成」</strong>：用相册里已确认的坐标创建正式轨迹。<br />
        无 GPS 相册请先走：区域定位 → 照片地图确认 → 再回来生成。下方两个按钮是辅助跳转，不是互相替代。
      </p>
      <el-form label-width="110px">
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
        <div class="gen-dialog-footer">
          <div class="gen-dialog-aux">
            <el-button
              link
              type="info"
              :disabled="!genForm.albumId"
              v-hasPermi="['album:photo:edit']"
              @click="openRegionLocateFromGenerate"
            >去区域定位</el-button>
            <el-button
              link
              type="success"
              :disabled="!genForm.albumId"
              @click="openPhotoMapForAlbum(genForm.albumId)"
            >去照片地图</el-button>
          </div>
          <div>
            <el-button @click="genOpen = false">取消</el-button>
            <el-button type="primary" :loading="genLoading" @click="submitGenerate">生成</el-button>
          </div>
        </div>
      </template>
    </el-dialog>

    <el-dialog title="导入 GPX" v-model="gpxImportOpen" width="520px" append-to-body destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="相册">
          <span>{{ albumNameMap[gpxImportAlbumId] || gpxImportAlbumId || '-' }}</span>
        </el-form-item>
        <el-form-item label="轨迹">
          <span>{{ gpxImportTrackName || '-' }}</span>
        </el-form-item>
        <el-form-item label="GPX 文件" required>
          <el-upload
            ref="gpxUploadRef"
            drag
            multiple
            :auto-upload="false"
            accept=".gpx"
            :limit="20"
            v-model:file-list="gpxFileList"
          >
            <div class="el-upload__text">将 .gpx 拖到此处，或<em>点击选择</em>（可多选）</div>
          </el-upload>
        </el-form-item>
        <p class="gpx-tip">
          适配 GPSLogger（GPX 1.1）：导入后叠加到当前轨迹地图的绿色 GPX 折线（只读，不可贴合路网或编辑）。
          同相册下同名 GPX 会覆盖旧文件，不会重复累加点位/里程。
          拍摄时间与 GPX 点相差在 10 秒内的照片/视频会挂到 GPX 对应位置。
          导入后将自动开启「启用GPX」，列表中的来源/点位/里程/时长会合并 GPX 数据。
        </p>
      </el-form>
      <template #footer>
        <el-button type="primary" :loading="gpxImportLoading" @click="submitGpxImport">导入并叠加</el-button>
        <el-button @click="gpxImportOpen = false">取消</el-button>
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

    <el-dialog title="相册区域粗定位" v-model="regionOpen" width="520px" append-to-body>
      <p class="region-hint">
        用于没有 GPS / GPX 的相册：填写国家、省、市、区或搜索地点后，系统会把媒体放到该区域中心附近（蓝色「区」）。可覆盖已有的区域/AI/时间估计点；不会覆盖设备 GPS 与手工确认点。
      </p>
      <el-form label-width="88px">
        <el-form-item label="相册" required>
          <el-select
            v-model="regionAlbumId"
            placeholder="选择相册"
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="item in albumOptions"
              :key="item.albumId"
              :label="albumLabel(item)"
              :value="item.albumId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="国家">
          <el-input v-model="regionForm.country" placeholder="可选，如：中国" clearable maxlength="50" />
        </el-form-item>
        <el-form-item label="省份">
          <el-input v-model="regionForm.province" placeholder="如：北京市 / 浙江省" clearable maxlength="50" />
        </el-form-item>
        <el-form-item label="城市">
          <el-input v-model="regionForm.city" placeholder="如：北京市 / 杭州市" clearable maxlength="50" />
        </el-form-item>
        <el-form-item label="地区">
          <el-input v-model="regionForm.district" placeholder="如：东城区 / 西湖区（越细越好）" clearable maxlength="50" />
        </el-form-item>
        <el-form-item label="地点搜索">
          <el-select
            v-model="regionPlaceId"
            filterable
            remote
            clearable
            reserve-keyword
            :remote-method="searchRegionPlace"
            :loading="regionPlaceSearching"
            placeholder="也可搜索地点作为区域中心，如：地坛公园"
            style="width: 100%"
            @change="onRegionPlacePicked"
          >
            <el-option
              v-for="item in regionPlaceOptions"
              :key="regionPlaceKey(item)"
              :label="item.name"
              :value="regionPlaceKey(item)"
            >
              <div class="place-opt">
                <div class="place-name">{{ item.name }}</div>
                <div class="place-addr">{{ item.address }}</div>
              </div>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item v-if="regionPreviewText" label="预览">
          <span class="region-preview">{{ regionPreviewText }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button
          :loading="regionPreviewLoading"
          :disabled="!canRegionPreview"
          @click="previewRegionGeocode"
        >预览坐标</el-button>
        <el-button type="primary" :loading="regionLoading" :disabled="!canRegionSubmit" @click="submitRegionLocate">应用到相册</el-button>
        <el-button @click="regionOpen = false">取消</el-button>
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
          :gpx-overlays="detailGpxOverlays"
          :editable="canEditTrack && isTrackEnabled(detailTrack)"
          @saved="onTrackMapSaved"
          @replan="onTrackReplan"
          @location-corrected="onLocationCorrected"
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
  resolveTrackRoutes,
  importTrackGpx
} from '@/api/album/track'
import { fallbackLocateAlbum, regionLocateAlbum, geocodeAddress } from '@/api/album/photo'
import { searchTrackPlace } from '@/api/album/track'
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
const fallbackLoading = ref(false)
const regionOpen = ref(false)
const regionLoading = ref(false)
const regionPreviewLoading = ref(false)
const regionAlbumId = ref(undefined)
const regionForm = ref({
  country: '中国',
  province: '',
  city: '',
  district: ''
})
const regionPlaceOptions = ref([])
const regionPlaceId = ref('')
const regionPlaceSearching = ref(false)
const regionSelectedPlace = ref(null)
const regionPreview = ref(null)
let regionPlaceTimer = 0
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
const detailGpxOverlays = ref([])
const mapViewerRef = ref(null)
let viewerRectRaf = 0
const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  albumId: undefined,
  trackName: undefined
})
const genForm = ref({ albumId: undefined, trackName: '' })
const gpxImportOpen = ref(false)
const gpxImportLoading = ref(false)
const gpxImportAlbumId = ref(undefined)
const gpxImportTrackId = ref(undefined)
const gpxImportTrackName = ref('')
const gpxFileList = ref([])
const gpxUploadRef = ref(null)
const gpxEnabledLoadingId = ref(null)

function albumLabel(item) {
  if (!item) return ''
  return `${item.albumName || '未命名'}（ID:${item.albumId}）`
}

function sourceTypeLabel(type) {
  if (type === 'gpx') return 'GPX'
  if (type === 'mixed') return '混合'
  return '照片'
}

function isTrackEnabled(track) {
  return track == null || track.enabled == null || track.enabled === 1
}

function hasGpxFiles(row) {
  return row?.hasGpx === 1
}

/** 区域粗定位草稿（尚未在照片地图确认正式定位） */
function isRegionDraft(row) {
  return String(row?.remark || '').includes('区域粗定位草稿')
}

/** 区域粗定位草稿：展示为关且置灰；普通照片轨（含 0 点）仍可开关，便于启用后自动同步 */
function isTrackEnabledDisplay(row) {
  if (isRegionDraft(row)) return false
  return row?.enabled !== 0
}

function canToggleTrackEnabled(row) {
  return canEditTrack.value && !isRegionDraft(row)
}

function trackEnabledTitle(row) {
  if (isRegionDraft(row)) {
    return '区域粗定位草稿，请先在照片地图确认定位并生成正式轨迹后再显示轨迹线'
  }
  return '控制前台/轨迹查看器是否绘制照片轨折线；关闭后仍可打开照片地图看点位'
}

/** 库内真实照片轨点数（关闭「显示轨迹线」时列表 pointCount 可能为 0） */
function realPhotoPointCount(row) {
  const raw = Number(row?.photoPointCount)
  if (!Number.isNaN(raw) && raw > 0) return raw
  return Number(row?.pointCount) || 0
}

function canShowPhotoMap(row) {
  // 与「显示轨迹线」开关无关：有照片点 / GPX / 草稿即可进照片地图
  return realPhotoPointCount(row) > 0 || hasGpxFiles(row) || isRegionDraft(row)
}

function canShowTrack(row) {
  // 关闭显示轨迹线时仍可进入查看器（查看器内可只看点）；草稿未转正前不开放
  return realPhotoPointCount(row) > 0 && !isRegionDraft(row)
}

function canShowRegionLocate(row) {
  // 已有 GPX 或已有权威 GPS 照片时，不再需要区域粗定位
  if (!row?.albumId) return false
  if (hasGpxFiles(row)) return false
  if (Number(row.hasAuthoritativeGps) === 1) return false
  return true
}

function canShowFallbackLocate(row) {
  // 估计补点：需要已有权威 GPS 作锚点，且不是纯 GPX
  if (!row?.albumId) return false
  if (hasGpxFiles(row)) return false
  if (row.sourceType === 'gpx') return false
  if (isRegionDraft(row)) return false
  // 没有权威 GPS 时估计补点无效
  if (Number(row.hasAuthoritativeGps) !== 1) return false
  return true
}

function canShowGpxImport(row) {
  return row?.albumId != null
}

function isGpxEnabled(row) {
  if (!hasGpxFiles(row)) return false
  return row.gpxEnabled == null || row.gpxEnabled === 1
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

function handleFallbackLocate(albumId, row) {
  let id = albumId != null && albumId !== '' ? albumId : row?.albumId
  if (id == null || id === '') {
    // 未预选相册时，引导用户先选相册再估计
    if (!albumOptions.value.length) {
      proxy.$modal.msgWarning('暂无相册')
      return
    }
    // 复用区域定位的相册选择体验：提示到顶部筛选或直接打开区域定位
    proxy.$modal.msgWarning('请先在上方筛选栏选择相册，或在行内点击「估计补点」')
    return
  }
  const name = albumNameMap.value[id] || id
  proxy.$modal.confirm(
    `将根据相册「${name}」内已有 GPS 照片/视频，按拍摄时间为无坐标媒体估计位置。估计点会显示在照片地图上（橙色「估」），确认后才加入主轨迹。是否继续？\n\n若相册完全没有 GPS，请改用「区域定位」。`
  ).then(() => {
    fallbackLoading.value = true
    return fallbackLocateAlbum(id)
  }).then(res => {
    const n = res?.data ?? 0
    if (n === 0) {
      return proxy.$modal.confirm(
        `相册「${name}」没有可用于推算的 GPS 锚点（更新 0 条）。是否改为「区域定位」？`
      ).then(() => {
        openRegionLocate(id)
      }).catch(() => {})
    }
    if (n > 0) {
      proxy.$modal.msgSuccess(`已更新 ${n} 条估计坐标`)
      return proxy.$modal.confirm('是否打开该相册的照片地图，查看并微调估计点？').then(() => {
        proxy.$tab.navigatePage({ path: '/photos/map', query: { albumId: id } })
      }).catch(() => {})
    }
  }).catch(() => {}).finally(() => {
    fallbackLoading.value = false
  })
}

const canRegionPreview = computed(() => {
  const f = regionForm.value
  return !!(f.country || f.province || f.city || f.district || regionSelectedPlace.value)
})

const canRegionSubmit = computed(() => {
  if (regionAlbumId.value == null || regionAlbumId.value === '') {
    return false
  }
  if (regionSelectedPlace.value?.wgsLat != null && regionSelectedPlace.value?.wgsLng != null) {
    return true
  }
  const f = regionForm.value
  return !!(f.province || f.city || f.district || f.country)
})

const regionPreviewText = computed(() => {
  const p = regionPreview.value
  if (!p) return ''
  const parts = [p.formattedAddress || p.address, p.wgsLat, p.wgsLng].filter(v => v != null && v !== '')
  return parts.join(' · ')
})

function openRegionLocate(albumId) {
  regionAlbumId.value = albumId != null && albumId !== '' ? albumId : (queryParams.value.albumId || undefined)
  regionForm.value = {
    country: '中国',
    province: '',
    city: '',
    district: ''
  }
  regionPlaceOptions.value = []
  regionPlaceId.value = ''
  regionSelectedPlace.value = null
  regionPreview.value = null
  regionOpen.value = true
}

function openRegionLocateFromGenerate() {
  const id = genForm.value.albumId
  if (id == null || id === '') {
    proxy.$modal.msgWarning('请先选择相册')
    return
  }
  genOpen.value = false
  openRegionLocate(id)
}

function regionPlaceKey(item) {
  return `${item?.id || item?.name || ''}|${item?.wgsLng}|${item?.wgsLat}`
}

function searchRegionPlace(query) {
  const q = (query || '').trim()
  clearTimeout(regionPlaceTimer)
  if (!q) {
    regionPlaceOptions.value = []
    return
  }
  regionPlaceTimer = setTimeout(() => {
    regionPlaceSearching.value = true
    const city = (regionForm.value.city || regionForm.value.province || '').trim() || undefined
    searchTrackPlace({ keywords: q, city, offset: 12 }).then(res => {
      regionPlaceOptions.value = res?.data || []
    }).catch(() => {
      regionPlaceOptions.value = []
    }).finally(() => {
      regionPlaceSearching.value = false
    })
  }, 300)
}

function onRegionPlacePicked(val) {
  if (!val) {
    regionSelectedPlace.value = null
    regionPreview.value = null
    return
  }
  const item = regionPlaceOptions.value.find(p => regionPlaceKey(p) === val)
  regionSelectedPlace.value = item || null
  if (item) {
    regionPreview.value = {
      formattedAddress: item.name || item.address,
      address: item.name || item.address,
      name: item.name,
      wgsLat: item.wgsLat,
      wgsLng: item.wgsLng
    }
    if (item.cityname && !regionForm.value.city) {
      regionForm.value.city = item.cityname
    }
    if (item.adname && !regionForm.value.district) {
      regionForm.value.district = item.adname
    }
  }
}

function buildRegionKeyword() {
  const f = regionForm.value
  return [f.country, f.province, f.city, f.district].map(s => (s || '').trim()).filter(Boolean).join(' ')
}

function previewRegionGeocode() {
  if (regionSelectedPlace.value?.wgsLat != null) {
    regionPreview.value = {
      formattedAddress: regionSelectedPlace.value.name || regionSelectedPlace.value.address,
      address: regionSelectedPlace.value.name || regionSelectedPlace.value.address,
      name: regionSelectedPlace.value.name,
      wgsLat: regionSelectedPlace.value.wgsLat,
      wgsLng: regionSelectedPlace.value.wgsLng
    }
    return
  }
  const address = buildRegionKeyword()
  if (!address) {
    proxy.$modal.msgWarning('请先填写行政区，或搜索地点')
    return
  }
  regionPreviewLoading.value = true
  geocodeAddress(address).then(res => {
    regionPreview.value = res?.data || null
    if (!regionPreview.value) {
      proxy.$modal.msgWarning('未解析到坐标')
    }
  }).catch(() => {}).finally(() => {
    regionPreviewLoading.value = false
  })
}

function submitRegionLocate() {
  const id = regionAlbumId.value
  if (id == null || id === '') {
    proxy.$modal.msgWarning('请选择相册')
    return
  }
  const f = regionForm.value
  const place = regionSelectedPlace.value
  const payload = {
    country: (f.country || '').trim() || undefined,
    province: (f.province || '').trim() || undefined,
    city: (f.city || '').trim() || undefined,
    district: (f.district || '').trim() || undefined,
    overwriteRegionCenter: true
  }
  if (place?.wgsLat != null && place?.wgsLng != null) {
    payload.latitude = place.wgsLat
    payload.longitude = place.wgsLng
    // 优先用地名（地坛公园），不要只用街道地址，否则后续 AI 锚定会丢景点名
    payload.address = place.name || place.address
  } else if (regionPreview.value?.wgsLat != null && regionPreview.value?.wgsLng != null) {
    payload.latitude = regionPreview.value.wgsLat
    payload.longitude = regionPreview.value.wgsLng
    payload.address = regionPreview.value.name
      || regionPreview.value.formattedAddress
      || regionPreview.value.address
  }

  const runApply = () => {
    if (!payload.province && !payload.city && !payload.district && !payload.country
        && payload.latitude == null) {
      proxy.$modal.msgWarning('请填写行政区，或在地点搜索中选中一项（仅输入不选中无效）')
      return
    }
    const name = albumNameMap.value[id] || id
    proxy.$modal.confirm(
      `将为相册「${name}」写入区域中心粗定位（蓝色「区」）。会覆盖已有的区域/AI/时间估计点，不会覆盖设备 GPS 与手工确认点。\n\n同相册若还有其它景点的 GPS，只会用「附近约 3km 内」的锚点精修，不会把点吸到远处。是否继续？`
    ).then(() => {
      regionLoading.value = true
      return regionLocateAlbum(id, payload)
    }).then(res => {
      const data = res?.data || {}
      const n = typeof data === 'number' ? data : (data.updated ?? 0)
      const addr = data.address || payload.address || buildRegionKeyword() || '所选区域'
      regionOpen.value = false
      proxy.$modal.msgSuccess(`已写入 ${n} 条区域粗定位（${addr}）`)
      if (n <= 0) return
      getList()
      // 直接打开照片地图，便于确认蓝色「区」点；列表已有轨迹草稿可再次进入
      proxy.$tab.navigatePage({ path: '/photos/map', query: { albumId: id } })
    }).catch(() => {}).finally(() => {
      regionLoading.value = false
    })
  }

  // 只填了文字、还没有坐标时：先地理编码再提交，避免“填了地坛却没点”
  if (payload.latitude == null || payload.longitude == null) {
    const address = buildRegionKeyword() || payload.address
    if (!address) {
      proxy.$modal.msgWarning('请填写行政区，或搜索并选中地点')
      return
    }
    regionLoading.value = true
    geocodeAddress(address).then(res => {
      const geo = res?.data
      if (!geo?.wgsLat || !geo?.wgsLng) {
        proxy.$modal.msgError('未解析到坐标，请换更具体的地点（如搜索并选中「地坛公园」）')
        return
      }
      payload.latitude = geo.wgsLat
      payload.longitude = geo.wgsLng
      payload.address = geo.formattedAddress || address
      if (!payload.province && geo.province) payload.province = geo.province
      if (!payload.city && geo.city) payload.city = geo.city
      if (!payload.district && geo.district) payload.district = geo.district
      regionPreview.value = geo
      regionLoading.value = false
      runApply()
    }).catch(() => {
      regionLoading.value = false
    })
    return
  }
  runApply()
}

function openGenerate() {
  genForm.value = {
    albumId: queryParams.value.albumId || undefined,
    trackName: ''
  }
  genOpen.value = true
}

function openPhotoMapForAlbum(albumId) {
  if (albumId == null || albumId === '') {
    proxy.$modal.msgWarning('请先选择相册')
    return
  }
  genOpen.value = false
  proxy.$tab.navigatePage({ path: '/photos/map', query: { albumId } })
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
  let gpxOverlays = data.gpxOverlays || []
  // 打开查看：缺折线才修补；不要因个别直线段 force 全量重算（会把上百段又打一遍高德/OSM）
  const needRepair = force || hasMissingRoutePaths(points) || hasUnstableAutoRoutes(points)
  if (!needRepair) {
    return { track, points, gpxOverlays }
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
    gpxOverlays = fresh.data?.gpxOverlays || gpxOverlays
  } catch (e) {
    console.warn('resolve track routes failed', e)
  }
  return { track, points, gpxOverlays }
}

function openTrackViewer(trackId, options = {}) {
  if (!trackId) return
  detailLoading.value = true
  detailOpen.value = true
  detailTrack.value = null
  detailPoints.value = []
  detailGpxOverlays.value = []
  nextTick(() => {
    startViewerRectSync()
  })
  // 直接读库展示已保存折线，不自动重算（重算仅手动「贴合路网」）
  // skipAutoSync：刚用估计坐标生成时，避免同步逻辑误清空点位
  const params = options.skipAutoSync ? { skipAutoSync: true } : {}
  getTrack(trackId, params).then(res => {
    const data = res.data || {}
    detailTrack.value = data.track || null
    detailPoints.value = data.points || []
    detailGpxOverlays.value = data.gpxOverlays || []
    if (!detailPoints.value.length) {
      proxy.$modal.msgWarning('轨迹暂无点位。若刚做区域定位，请确认生成时勾选了「包含估计坐标」')
    }
    nextTick(() => mapViewerRef.value?.refresh?.({ fit: true }))
  }).catch(() => {
    detailOpen.value = false
  }).finally(() => {
    detailLoading.value = false
  })
}

function onViewerOpened() {
  syncViewerRect()
  nextTick(() => {
    mapViewerRef.value?.refresh?.({ fit: true })
    mapViewerRef.value?.invalidateMapSize?.()
  })
  // 侧栏动画结束后再对齐一次，确保贴合白色内容区
  setTimeout(() => {
    syncViewerRect()
    mapViewerRef.value?.refresh?.({ fit: true })
    mapViewerRef.value?.invalidateMapSize?.()
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
  const params = {
    albumId: genForm.value.albumId,
    trackName: genForm.value.trackName || undefined,
    includeEstimated: false
  }
  generateTrack(params).then(res => {
    proxy.$modal.msgSuccess('生成成功')
    genOpen.value = false
    getList()
    const track = res.data
    if (track?.trackId) {
      openTrackViewer(track.trackId)
    }
  }).catch(err => {
    const msg = String(err?.message || err?.msg || '')
    const albumId = genForm.value.albumId
    if (msg.includes('确认') || msg.includes('区域') || msg.includes('没有可生成') || msg.includes('没有带坐标')) {
      proxy.$modal.confirm(
        `${msg}\n\n是否打开该相册的照片地图进行确认定位？`
      ).then(() => {
        genOpen.value = false
        openPhotoMapForAlbum(albumId)
      }).catch(() => {})
    }
  }).finally(() => {
    genLoading.value = false
  })
}

function handleView(row) {
  // 再次打开：进入照片地图（与区域定位后同一界面，蓝色「区」点仍在）
  if (row?.albumId != null) {
    openPhotoMapForAlbum(row.albumId)
    return
  }
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
  if (!canToggleTrackEnabled(row)) return
  const enabled = val ? 1 : 0
  const prev = row.enabled == null ? 1 : row.enabled
  if (prev === enabled) return
  enabledLoadingId.value = row.trackId
  row.enabled = enabled
  try {
    await updateTrack({ trackId: row.trackId, enabled })
    if (enabled === 1) {
      proxy.$modal.msgSuccess('已开启显示轨迹线：地图会画照片轨，列表计入里程/时长')
    } else {
      proxy.$modal.msgSuccess('已关闭显示轨迹线：地图只保留照片点、不画折线；「照片地图 / 轨迹」入口仍可用')
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
    detailGpxOverlays.value = data.gpxOverlays || []
    getList()
    nextTick(() => mapViewerRef.value?.refresh?.({ fit: false }))
  }).catch(() => {
    getList()
  })
}

function onLocationCorrected() {
  onTrackMapSaved()
}

function onTrackReplan({ trackId, done }) {
  const id = trackId || detailTrack.value?.trackId
  if (!id) {
    done?.()
    return
  }
  loadTrackWithRoutes(id, true).then(({ track, points, gpxOverlays }) => {
    detailTrack.value = track || detailTrack.value
    detailPoints.value = points || []
    detailGpxOverlays.value = gpxOverlays || []
    proxy.$modal.msgSuccess('已按照片坐标重新贴合路网')
    nextTick(() => mapViewerRef.value?.refresh?.({ fit: true }))
  }).catch(() => {
    proxy.$modal.msgError('重新贴合失败，请检查高德 Key 或稍后重试')
  }).finally(() => {
    done?.()
  })
}

function handleDelete(row) {
  proxy.$modal.confirm('确认删除该轨迹？若该相册下已无其他轨迹，将同时清除已导入的 GPX 叠层。')
    .then(() => delTrack(row.trackId))
    .then(() => {
      getList()
      proxy.$modal.msgSuccess('删除成功')
    }).catch(() => {})
}

function openGpxImport(row) {
  if (!row?.albumId) {
    proxy.$modal.msgError('轨迹未关联相册')
    return
  }
  gpxImportAlbumId.value = row.albumId
  gpxImportTrackId.value = row.trackId
  gpxImportTrackName.value = row.trackName || ''
  gpxFileList.value = []
  gpxImportOpen.value = true
}

function submitGpxImport() {
  if (!gpxImportAlbumId.value) {
    proxy.$modal.msgError('相册无效')
    return
  }
  const rawFiles = (gpxFileList.value || [])
    .map(f => f.raw)
    .filter(Boolean)
  if (!rawFiles.length) {
    proxy.$modal.msgError('请选择至少一个 .gpx 文件')
    return
  }
  const formData = new FormData()
  rawFiles.forEach(file => formData.append('files', file))
  gpxImportLoading.value = true
  importTrackGpx(gpxImportAlbumId.value, formData).then(res => {
    const data = res.data || {}
    const track = data.track
    const replaced = Number(data.replaced) || 0
    const imported = data.imported || rawFiles.length
    proxy.$modal.msgSuccess(replaced > 0
      ? `已导入 ${imported} 个 GPX（覆盖同名 ${replaced} 个），已叠加到轨迹地图`
      : `已导入 ${imported} 个 GPX，已叠加到轨迹地图`)
    gpxImportOpen.value = false
    gpxFileList.value = []
    getList()
    const viewId = track?.trackId || gpxImportTrackId.value
    if (viewId) {
      openTrackViewer(viewId)
    } else {
      proxy.$modal.msgWarning('相册尚无照片轨迹，请先「生成轨迹」后再查看叠加效果')
    }
  }).finally(() => {
    gpxImportLoading.value = false
  })
}

async function handleGpxEnabledChange(row, val) {
  if (!row?.trackId) return
  if (!hasGpxFiles(row)) {
    proxy.$modal.msgWarning('请先在操作中导入 GPX')
    return
  }
  const enabled = val ? 1 : 0
  const prev = row.gpxEnabled == null ? 1 : row.gpxEnabled
  if (prev === enabled) return
  gpxEnabledLoadingId.value = row.trackId
  row.gpxEnabled = enabled
  try {
    await updateTrack({ trackId: row.trackId, gpxEnabled: enabled })
    proxy.$modal.msgSuccess(enabled === 1
      ? '已开启：媒体挂到 GPX；地图展示 GPX 线路'
      : '已关闭：匹配媒体回落到照片轨（自身 GPS），可编辑并贴合路网')
    getList()
    // 正在查看该轨迹时立刻刷新；关闭 GPX 后强制重新贴合照片轨
    if (detailOpen.value && detailTrack.value?.trackId === row.trackId) {
      const { track, points, gpxOverlays } = await loadTrackWithRoutes(row.trackId, enabled === 0)
      detailTrack.value = track || detailTrack.value
      detailPoints.value = points || []
      detailGpxOverlays.value = gpxOverlays || []
      nextTick(() => mapViewerRef.value?.refresh?.({ fit: true }))
    }
  } catch (e) {
    row.gpxEnabled = prev
    proxy.$modal.msgError('更新失败')
  } finally {
    gpxEnabledLoadingId.value = null
  }
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

.gpx-tip {
  margin: 0 0 0 90px;
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
}

.region-hint {
  margin: 0 0 16px;
  padding: 10px 12px;
  border-radius: 6px;
  background: #f4f4f5;
  color: #606266;
  font-size: 13px;
  line-height: 1.55;
}

.gen-dialog-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  gap: 12px;
}

.gen-dialog-aux {
  display: flex;
  align-items: center;
  gap: 4px;
}

.region-preview {
  color: #409eff;
  font-size: 13px;
  word-break: break-all;
}

.place-opt .place-name {
  font-size: 13px;
  color: #303133;
}

.place-opt .place-addr {
  font-size: 12px;
  color: #909399;
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
