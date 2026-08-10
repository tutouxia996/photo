<template>
  <div class="track-map-viewer">
    <PhotoClusterMap
      ref="clusterMapRef"
      :points="displayPoints"
      :show-polyline="true"
      :polyline-color="track?.trackColor || '#3B82F6'"
      :segment-by-travel-mode="true"
      :show-direction="!customOpen"
      :editable="editable && editing && !customOpen"
      :active-segment-index="editing && !customOpen ? activeIndex : -1"
      :path-edit-index="editing && !customOpen ? activeIndex : -1"
      :segment-labels="editing ? 'none' : 'hover'"
      :preview-path="customPreviewPath"
      :auto-fit="!editing"
      empty-text="暂无轨迹点位"
      @segment-click="onSegmentClick"
      @segment-path-change="onSegmentPathChange"
      @waypoint-click="onWaypointClick"
    >
      <template #meta>
        <div v-if="track" class="track-meta" :class="{ 'is-open': metaOpen }">
          <div class="meta-bar">
            <button type="button" class="meta-toggle" :title="metaOpen ? '收起详情' : '展开详情'" @click="metaOpen = !metaOpen">
              <span class="name">{{ track.trackName || '未命名轨迹' }}</span>
              <span class="meta-chevron">{{ metaOpen ? '▴' : '▾' }}</span>
            </button>
            <div class="meta-actions">
              <el-button
                size="small"
                :loading="replanning"
                :disabled="editing"
                @click="replanRoutes"
              >贴合路网</el-button>
              <template v-if="editable">
                <el-button
                  v-if="!editing"
                  type="primary"
                  size="small"
                  @click="startEdit"
                >编辑轨迹</el-button>
                <template v-else>
                  <el-button size="small" :type="customOpen ? 'warning' : 'default'" @click="toggleCustomPanel">增补路段</el-button>
                  <el-button type="primary" size="small" :loading="saving" @click="saveEdit">保存</el-button>
                  <el-button size="small" :disabled="saving" @click="cancelEdit">取消</el-button>
                </template>
              </template>
            </div>
          </div>
          <div v-if="metaOpen" class="meta-detail">
            <div class="meta-stats">
              <span>点位 {{ track.pointCount ?? displayPoints.length }}</span>
              <span>里程 {{ formatDistance(track.totalDistance) }}</span>
              <span>时长 {{ formatDuration(track.totalDuration) }}</span>
            </div>
            <div v-if="track.remark" class="remark">{{ track.remark }}</div>
            <div class="dir-legend">
              <span class="dir-start">起</span>
              <span class="dir-flow">→ 行进方向 →</span>
              <span class="dir-end">终</span>
            </div>
            <p v-if="editing" class="edit-tip">点线路改走向；点橙色途经点可改说明或删除自定义路段</p>
          </div>
        </div>
      </template>
    </PhotoClusterMap>

    <div v-if="usedModes.length && !editing" class="mode-legend">
      <span
        v-for="m in usedModes"
        :key="m.key"
        class="legend-item"
      >
        <i class="legend-dot" :style="{ background: m.color }" />
        {{ m.label }}
      </span>
    </div>

    <aside v-if="editing && customOpen" class="seg-panel custom-panel">
      <div class="seg-panel-hd">
        <div class="seg-panel-title">增补路段</div>
        <button type="button" class="seg-close" @click="closeCustomPanel">×</button>
      </div>

      <div class="custom-tabs">
        <button type="button" class="custom-tab" :class="{ active: customTab === 'place' }" @click="customTab = 'place'">地点搜索</button>
        <button type="button" class="custom-tab" :class="{ active: customTab === 'train' }" @click="switchTrainTab">车次经停</button>
      </div>

      <template v-if="customTab === 'place'">
        <p class="path-tip">用高德搜索起终点；步行/骑行/驾车走高德路网，火车/高铁/地铁优先 OSM 铁路贴轨后插入轨迹。</p>

        <div class="seg-label">起点</div>
        <el-select
          v-model="customFromId"
          filterable
          remote
          clearable
          reserve-keyword
          placeholder="搜索起点（如：北京南站）"
          :remote-method="q => searchPlace(q, 'from')"
          :loading="customFromSearching"
          style="width: 100%"
          @change="onPickCustomPlace('from')"
        >
          <el-option
            v-for="item in customFromOptions"
            :key="item.id || item.name + item.lng"
            :label="item.name"
            :value="item.id || item.name + ',' + item.lng"
          >
            <div class="place-opt">
              <div class="place-name">{{ item.name }}</div>
              <div class="place-addr">{{ item.address }}</div>
            </div>
          </el-option>
        </el-select>
        <div v-if="customFrom" class="place-picked">已选：{{ customFrom.name }}</div>

        <div class="seg-label">终点</div>
        <el-select
          v-model="customToId"
          filterable
          remote
          clearable
          reserve-keyword
          placeholder="搜索终点"
          :remote-method="q => searchPlace(q, 'to')"
          :loading="customToSearching"
          style="width: 100%"
          @change="onPickCustomPlace('to')"
        >
          <el-option
            v-for="item in customToOptions"
            :key="item.id || item.name + item.lng"
            :label="item.name"
            :value="item.id || item.name + ',' + item.lng"
          >
            <div class="place-opt">
              <div class="place-name">{{ item.name }}</div>
              <div class="place-addr">{{ item.address }}</div>
            </div>
          </el-option>
        </el-select>
        <div v-if="customTo" class="place-picked">已选：{{ customTo.name }}</div>

        <div class="seg-label">出行方式</div>
        <div class="mode-grid">
          <button
            v-for="m in travelModes"
            :key="m.key"
            type="button"
            class="mode-btn"
            :class="{ active: customMode === m.key }"
            :style="customModeBtnStyle(m)"
            @click="customMode = m.key"
          >{{ m.label }}</button>
        </div>
      </template>

      <template v-else>
        <p class="path-tip">
          手工填经停即可贴轨（用高德定位车站 + OSM 铁路）。
          「查询班次 / 拉取经停」需要配置聚合 Key（JUHE_TRAIN_KEY），未配置时按钮会禁用，不影响手填预览。
        </p>

        <div class="seg-label">车次（可选，写入说明）</div>
        <el-input v-model="trainNo" placeholder="如 G8731" clearable @change="onTrainNoChange" />

        <div class="train-row">
          <div class="train-col">
            <div class="seg-label">起点站</div>
            <el-select
              v-model="trainFromId"
              filterable
              remote
              clearable
              reserve-keyword
              placeholder="高德搜索：清河站"
              :remote-method="q => searchTrainStation(q, 'from')"
              :loading="trainFromSearching"
              style="width: 100%"
              @change="onPickTrainStation('from')"
            >
              <el-option
                v-for="item in trainFromOptions"
                :key="'tf-' + (item.id || item.name + item.lng)"
                :label="item.name"
                :value="item.id || item.name + ',' + item.lng"
              >
                <div class="place-opt">
                  <div class="place-name">{{ item.name }}</div>
                  <div class="place-addr">{{ item.address }}</div>
                </div>
              </el-option>
            </el-select>
          </div>
          <div class="train-col">
            <div class="seg-label">终点站</div>
            <el-select
              v-model="trainToId"
              filterable
              remote
              clearable
              reserve-keyword
              placeholder="高德搜索：八达岭长城站"
              :remote-method="q => searchTrainStation(q, 'to')"
              :loading="trainToSearching"
              style="width: 100%"
              @change="onPickTrainStation('to')"
            >
              <el-option
                v-for="item in trainToOptions"
                :key="'tt-' + (item.id || item.name + item.lng)"
                :label="item.name"
                :value="item.id || item.name + ',' + item.lng"
              >
                <div class="place-opt">
                  <div class="place-name">{{ item.name }}</div>
                  <div class="place-addr">{{ item.address }}</div>
                </div>
              </el-option>
            </el-select>
          </div>
        </div>

        <div class="path-actions" style="margin-top: 8px">
          <el-button size="small" type="primary" plain @click="fillStopsFromStations">填入起终点到经停</el-button>
          <el-button size="small" :loading="trainQuerying" :disabled="!trainApiReady" :title="trainApiReady ? '' : '需配置 JUHE_TRAIN_KEY'" @click="queryTrainList">查询班次</el-button>
          <el-button size="small" :loading="trainStopsLoading" :disabled="!trainApiReady || !trainNo" :title="trainApiReady ? '' : '需配置 JUHE_TRAIN_KEY'" @click="fetchTrainStops">拉取经停</el-button>
        </div>
        <p v-if="!trainApiReady" class="api-disabled-tip">未配置聚合火车 Key，班次查询已禁用；请用上方高德搜站 + 手填经停。</p>

        <div class="train-row">
          <div class="train-col">
            <div class="seg-label">日期（查班次用）</div>
            <el-date-picker
              v-model="trainDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="出发日期"
              style="width: 100%"
              :disabled="!trainApiReady"
            />
          </div>
          <div class="train-col">
            <div class="seg-label">车型筛选</div>
            <el-select v-model="trainFilter" clearable placeholder="全部" style="width: 100%" :disabled="!trainApiReady">
              <el-option label="高铁/城际 G" value="G" />
              <el-option label="动车 D" value="D" />
              <el-option label="其他" value="O" />
            </el-select>
          </div>
        </div>

        <div v-if="trainOptions.length" class="seg-label">选择班次</div>
        <el-select
          v-if="trainOptions.length"
          v-model="selectedTrainKey"
          placeholder="点击填入车次与站"
          style="width: 100%"
          @change="onPickTrainOption"
        >
          <el-option
            v-for="t in trainOptions"
            :key="t.trainNo + t.departTime"
            :label="`${t.trainNo} ${t.departTime || ''}→${t.arriveTime || ''} ${t.fromStation || ''}-${t.toStation || ''}`"
            :value="t.trainNo + '|' + (t.departTime || '')"
          />
        </el-select>

        <div class="seg-label">经停站（每行一个站，可只填起终点）</div>
        <el-input
          v-model="trainManualStops"
          type="textarea"
          :rows="5"
          placeholder="清河站&#10;八达岭长城站"
        />

        <div class="seg-label">出行方式</div>
        <div class="mode-grid">
          <button
            v-for="m in trainModeChoices"
            :key="m.key"
            type="button"
            class="mode-btn"
            :class="{ active: customMode === m.key }"
            :style="customModeBtnStyle(m)"
            @click="customMode = m.key"
          >{{ m.label }}</button>
        </div>
      </template>

      <div class="seg-label">插入位置</div>
      <el-radio-group v-model="customInsertPos" class="insert-pos">
        <el-radio-button label="start">开头</el-radio-button>
        <el-radio-button label="after">某点之后</el-radio-button>
        <el-radio-button label="end">末尾</el-radio-button>
      </el-radio-group>
      <el-select
        v-if="customInsertPos === 'after'"
        v-model="customAfterPointId"
        placeholder="选择锚点"
        style="width: 100%; margin-top: 8px"
      >
        <el-option
          v-for="p in displayPoints"
          :key="p.pointId"
          :label="`#${p.sequence} ${pointLabel(p)}`"
          :value="p.pointId"
        />
      </el-select>

      <p v-if="customHint" class="route-hint">{{ customHint }}</p>
      <div class="seg-panel-ft">
        <el-button :loading="customPlanning" @click="previewCustomSegment">预览线路</el-button>
        <el-button type="primary" :loading="customSaving" :disabled="!customPreviewPath?.length" @click="submitCustomSegment">添加到轨迹</el-button>
      </div>
    </aside>

    <aside v-if="editing && !customOpen && waypointIndex >= 0" class="seg-panel">
      <div class="seg-panel-hd">
        <div class="seg-panel-title">途经点 #{{ waypointPoint?.sequence ?? (waypointIndex + 1) }}</div>
        <button type="button" class="seg-close" @click="closeWaypointPanel">×</button>
      </div>
      <p class="path-tip">自定义增补的途经点（无照片）。可改名称说明，或删除本点 / 整段。</p>
      <div class="seg-label">名称 / 说明</div>
      <el-input
        v-model="waypointDesc"
        maxlength="500"
        show-word-limit
        placeholder="地点名称"
      />
      <p v-if="waypointHint" class="route-hint">{{ waypointHint }}</p>
      <div class="seg-panel-ft" style="flex-wrap: wrap">
        <el-button type="primary" :loading="waypointSaving" @click="saveWaypointDesc">保存说明</el-button>
        <el-button type="danger" plain :loading="waypointDeleting" @click="deleteWaypoint(false)">删除此点</el-button>
        <el-button
          v-if="canDeleteWaypointSegment"
          type="danger"
          :loading="waypointDeleting"
          @click="deleteWaypoint(true)"
        >删除整段</el-button>
      </div>
    </aside>

    <aside v-if="editing && !customOpen && waypointIndex < 0 && activeIndex >= 0" class="seg-panel">
      <div class="seg-panel-hd">
        <div class="seg-panel-title">路段编辑 #{{ activeIndex + 1 }}</div>
        <button type="button" class="seg-close" @click="activeIndex = -1">×</button>
      </div>
      <div class="seg-route">
        <div class="seg-end">
          <span class="seg-no">#{{ fromPoint?.sequence ?? (activeIndex + 1) }}</span>
          <span class="seg-name">{{ pointLabel(fromPoint) }}</span>
        </div>
        <div class="seg-arrow">→</div>
        <div class="seg-end">
          <span class="seg-no">#{{ toPoint?.sequence ?? (activeIndex + 2) }}</span>
          <span class="seg-name">{{ pointLabel(toPoint) }}</span>
        </div>
      </div>

      <div class="seg-label">线路调整</div>
      <div class="path-actions">
        <el-button type="primary" size="small" :loading="planning" @click="snapSegment">贴合路网</el-button>
        <el-button size="small" :disabled="planning" @click="straightenSegment">改为直线</el-button>
      </div>
      <p class="path-tip">
        地图上拖动橙色圆点自定义走向；照片锚点（灰点）固定。双击绿色线路可增加拐点。
      </p>

      <div class="seg-label">出行方式</div>
      <div class="mode-grid">
        <button
          v-for="m in travelModes"
          :key="m.key"
          type="button"
          class="mode-btn"
          :class="{ active: draftMode === m.key }"
          :style="modeBtnStyle(m)"
          @click="onPickMode(m.key)"
        >{{ m.label }}</button>
      </div>

      <div class="seg-label">文字说明</div>
      <el-input
        v-model="draftDesc"
        type="textarea"
        :rows="3"
        maxlength="500"
        show-word-limit
        placeholder="例如：沿长城步道向北；此段为索道"
        @change="applyDescription"
      />

      <p v-if="routeHint" class="route-hint">{{ routeHint }}</p>
      <div class="seg-panel-ft">
        <el-button type="primary" :loading="saving" @click="saveEdit">保存全部</el-button>
        <el-button :disabled="planning" @click="clearSegment">清空本段</el-button>
        <el-button
          v-if="canDeleteActiveCustomSegment"
          type="danger"
          plain
          :loading="waypointDeleting"
          @click="deleteActiveCustomSegment"
        >删除自定义路段</el-button>
      </div>
    </aside>
  </div>
</template>

<script setup>
import { computed, getCurrentInstance, ref, watch } from 'vue'
import PhotoClusterMap from '@/components/PhotoClusterMap/index.vue'
import {
  addCustomSegment,
  addTrainSegment,
  delTrackPoint,
  getTrainApiStatus,
  getTrainStops,
  planTrainRoute,
  previewTrackRoute,
  queryTrains,
  searchTrackPlace,
  updateTrackPoints
} from '@/api/album/track'
import { isWaypointPoint, TRAVEL_MODES, toMapLatLng } from '@/utils/photoMapCluster'

const props = defineProps({
  track: { type: Object, default: null },
  points: { type: Array, default: () => [] },
  /** 是否允许在地图上编辑路段 */
  editable: { type: Boolean, default: false }
})

const emit = defineEmits(['saved', 'replan'])

const { proxy } = getCurrentInstance()
const clusterMapRef = ref(null)
const metaOpen = ref(false)
const editing = ref(false)
const saving = ref(false)
const planning = ref(false)
const replanning = ref(false)
const draftPoints = ref([])
const activeIndex = ref(-1)
const draftMode = ref('')
const draftDesc = ref('')
const routeHint = ref('')
const travelModes = TRAVEL_MODES

const customOpen = ref(false)
const customTab = ref('place')
const customFromId = ref('')
const customToId = ref('')
const customFrom = ref(null)
const customTo = ref(null)
const customFromOptions = ref([])
const customToOptions = ref([])
const customFromSearching = ref(false)
const customToSearching = ref(false)
const customMode = ref('walk')
const customInsertPos = ref('end')
const customAfterPointId = ref(null)
const customPreviewPath = ref(null)
const customPlanning = ref(false)
const customSaving = ref(false)
const customHint = ref('')
let placeSearchTimer = null

const trainApiReady = ref(false)
const trainNo = ref('')
const trainFromStation = ref('')
const trainToStation = ref('')
const trainFromId = ref('')
const trainToId = ref('')
const trainFromPlace = ref(null)
const trainToPlace = ref(null)
const trainFromOptions = ref([])
const trainToOptions = ref([])
const trainFromSearching = ref(false)
const trainToSearching = ref(false)
const trainDate = ref('')
const trainFilter = ref('G')
const trainManualStops = ref('')
const trainOptions = ref([])
const selectedTrainKey = ref('')
const trainQuerying = ref(false)
const trainStopsLoading = ref(false)
const trainPlannedStops = ref(null)
const trainDescription = ref('')

const trainModeChoices = computed(() =>
  TRAVEL_MODES.filter(m => m.key === 'hsr' || m.key === 'train' || m.key === 'metro')
)

const waypointIndex = ref(-1)
const waypointDesc = ref('')
const waypointHint = ref('')
const waypointSaving = ref(false)
const waypointDeleting = ref(false)

const displayPoints = computed(() => (editing.value ? draftPoints.value : props.points))

const waypointPoint = computed(() => {
  if (waypointIndex.value < 0) return null
  return displayPoints.value[waypointIndex.value] || null
})

const canDeleteWaypointSegment = computed(() => {
  const i = waypointIndex.value
  if (i < 0) return false
  const list = displayPoints.value
  const cur = list[i]
  const next = list[i + 1]
  return isWaypointPoint(cur) && isWaypointPoint(next)
})

const canDeleteActiveCustomSegment = computed(() => {
  const i = activeIndex.value
  if (i < 0) return false
  const list = displayPoints.value
  return isWaypointPoint(list[i]) && isWaypointPoint(list[i + 1])
})

const usedModes = computed(() => {
  const keys = new Set()
  displayPoints.value.forEach((p, idx) => {
    if (idx >= displayPoints.value.length - 1) return
    if (p?.travelMode) keys.add(p.travelMode)
  })
  return TRAVEL_MODES.filter(m => keys.has(m.key))
})

const fromPoint = computed(() => {
  if (activeIndex.value < 0) return null
  return displayPoints.value[activeIndex.value] || null
})

const toPoint = computed(() => {
  if (activeIndex.value < 0) return null
  return displayPoints.value[activeIndex.value + 1] || null
})

watch(() => props.points, () => {
  if (!editing.value) {
    activeIndex.value = -1
  }
})

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

function pointLabel(p) {
  if (!p) return '-'
  return p.description || p.address || p.fileName || `${p.latitude}, ${p.longitude}`
}

function placeKey(item) {
  return item?.id || `${item?.name},${item?.lng},${item?.lat}`
}

function searchPlace(query, which) {
  const q = (query || '').trim()
  if (!q) return
  clearTimeout(placeSearchTimer)
  placeSearchTimer = setTimeout(async () => {
    if (which === 'from') customFromSearching.value = true
    else customToSearching.value = true
    try {
      const res = await searchTrackPlace({ keywords: q, offset: 10 })
      const list = res.data || []
      if (which === 'from') customFromOptions.value = list
      else customToOptions.value = list
    } catch (e) {
      if (which === 'from') customFromOptions.value = []
      else customToOptions.value = []
    } finally {
      if (which === 'from') customFromSearching.value = false
      else customToSearching.value = false
    }
  }, 320)
}

function onPickCustomPlace(which) {
  const id = which === 'from' ? customFromId.value : customToId.value
  const opts = which === 'from' ? customFromOptions.value : customToOptions.value
  const found = opts.find(o => placeKey(o) === id) || null
  if (which === 'from') customFrom.value = found
  else customTo.value = found
  customPreviewPath.value = null
}

function customModeBtnStyle(m) {
  if (customMode.value === m.key) {
    return { background: m.color, borderColor: m.color, color: '#fff' }
  }
  return { borderColor: m.color, color: m.color }
}

function toggleCustomPanel() {
  if (customOpen.value) {
    closeCustomPanel()
  } else {
    customOpen.value = true
    activeIndex.value = -1
    customTab.value = 'place'
    customHint.value = '搜索并选择起点、终点后预览，再添加到轨迹'
    loadTrainApiStatus()
  }
}

function closeCustomPanel() {
  customOpen.value = false
  customPreviewPath.value = null
  customHint.value = ''
  trainOptions.value = []
  selectedTrainKey.value = ''
  trainPlannedStops.value = null
  trainDescription.value = ''
}

async function loadTrainApiStatus() {
  try {
    const res = await getTrainApiStatus()
    trainApiReady.value = !!(res.data && res.data.apiReady)
  } catch (e) {
    trainApiReady.value = false
  }
}

function switchTrainTab() {
  customTab.value = 'train'
  if (!['hsr', 'train', 'metro'].includes(customMode.value)) {
    customMode.value = 'hsr'
  }
  loadTrainApiStatus().then(() => {
    customHint.value = trainApiReady.value
      ? '可用聚合查班次；也可高德搜站后手填经停预览贴轨'
      : '请用高德搜索起终点站 →「填入起终点到经停」→ 预览（无需聚合 Key）'
  })
}

function searchTrainStation(query, which) {
  const q = (query || '').trim()
  if (!q) return
  clearTimeout(placeSearchTimer)
  placeSearchTimer = setTimeout(async () => {
    if (which === 'from') trainFromSearching.value = true
    else trainToSearching.value = true
    try {
      const kw = q.includes('站') ? q : `${q}站`
      const res = await searchTrackPlace({ keywords: kw, offset: 10 })
      const list = res.data || []
      if (which === 'from') trainFromOptions.value = list
      else trainToOptions.value = list
    } catch (e) {
      if (which === 'from') trainFromOptions.value = []
      else trainToOptions.value = []
    } finally {
      if (which === 'from') trainFromSearching.value = false
      else trainToSearching.value = false
    }
  }, 320)
}

function onPickTrainStation(which) {
  const id = which === 'from' ? trainFromId.value : trainToId.value
  const opts = which === 'from' ? trainFromOptions.value : trainToOptions.value
  const found = opts.find(o => placeKey(o) === id) || null
  if (which === 'from') {
    trainFromPlace.value = found
    trainFromStation.value = found?.name || ''
  } else {
    trainToPlace.value = found
    trainToStation.value = found?.name || ''
  }
  customPreviewPath.value = null
}

function fillStopsFromStations() {
  const a = trainFromStation.value || trainFromPlace.value?.name
  const b = trainToStation.value || trainToPlace.value?.name
  if (!a || !b) {
    customHint.value = '请先用高德搜索并选择起点站、终点站'
    return
  }
  trainManualStops.value = `${a}\n${b}`
  customHint.value = '已填入起终点，可点「预览线路」贴轨'
}

function onTrainNoChange() {
  const no = (trainNo.value || '').trim().toUpperCase()
  if (!no) return
  trainNo.value = no
  const c = no.charAt(0)
  if (c === 'G' || c === 'C' || c === 'D') customMode.value = 'hsr'
  else if (c === 'S' || c === 'Z' || c === 'T' || c === 'K') customMode.value = 'train'
}

async function queryTrainList() {
  const from = trainFromStation.value || trainFromPlace.value?.name
  const to = trainToStation.value || trainToPlace.value?.name
  if (!from || !to || !trainDate.value) {
    customHint.value = '查班次需：出发站、到达站、日期（并配置聚合 Key）'
    return
  }
  trainQuerying.value = true
  customHint.value = '正在查询班次…'
  try {
    const res = await queryTrains({
      fromStation: String(from).replace(/站$/, ''),
      toStation: String(to).replace(/站$/, ''),
      date: trainDate.value,
      filter: trainFilter.value || undefined
    })
    trainOptions.value = res.data || []
    customHint.value = trainOptions.value.length
      ? `查到 ${trainOptions.value.length} 趟车，请选择班次`
      : '未查到班次，可改日期或手工填经停'
  } catch (e) {
    trainOptions.value = []
    customHint.value = e?.message || '班次查询失败'
  } finally {
    trainQuerying.value = false
  }
}

function onPickTrainOption(key) {
  const found = trainOptions.value.find(t => (t.trainNo + '|' + (t.departTime || '')) === key)
  if (!found) return
  trainNo.value = found.trainNo || ''
  if (found.fromStation) trainFromStation.value = found.fromStation
  if (found.toStation) trainToStation.value = found.toStation
  onTrainNoChange()
  // 无经停详情时先写入起终点，便于手工补充中间站
  if (!trainManualStops.value.trim() && found.fromStation && found.toStation) {
    const dep = found.departTime ? ` ${found.departTime}` : ''
    const arr = found.arriveTime ? ` ${found.arriveTime}` : ''
    trainManualStops.value = `${found.fromStation}${dep}\n${found.toStation}${arr}`
  }
  customHint.value = `已选 ${found.trainNo}，可点「拉取经停」或直接预览`
}

async function fetchTrainStops() {
  if (!trainNo.value) {
    customHint.value = '请先填写车次'
    return
  }
  trainStopsLoading.value = true
  customHint.value = '正在拉取经停…'
  try {
    const res = await getTrainStops({
      trainNo: trainNo.value.trim(),
      fromStation: trainFromStation.value || undefined,
      toStation: trainToStation.value || undefined
    })
    const stops = res.data || []
    if (!stops.length) {
      customHint.value = '未查到经停，请手工填写'
      return
    }
    trainManualStops.value = stops.map(s => {
      const parts = [s.name]
      if (s.arriveTime) parts.push(s.arriveTime)
      if (s.departTime && s.departTime !== s.arriveTime) parts.push(s.departTime)
      return parts.join(' ')
    }).join('\n')
    if (stops[0]?.name) trainFromStation.value = stops[0].name
    if (stops[stops.length - 1]?.name) trainToStation.value = stops[stops.length - 1].name
    customHint.value = `已填入 ${stops.length} 个经停站，可预览线路`
  } catch (e) {
    customHint.value = e?.message || '拉取经停失败，请手工填写'
  } finally {
    trainStopsLoading.value = false
  }
}

async function previewCustomSegment() {
  if (customTab.value === 'train') {
    await previewTrainSegment()
    return
  }
  if (!customFrom.value || !customTo.value) {
    customHint.value = '请先搜索并选择起点和终点'
    return
  }
  if (!customMode.value) {
    customHint.value = '请选择出行方式'
    return
  }
  customPlanning.value = true
  customHint.value = '正在规划真实线路…'
  try {
    const res = await previewTrackRoute({
      fromLat: customFrom.value.wgsLat,
      fromLng: customFrom.value.wgsLng,
      toLat: customTo.value.wgsLat,
      toLng: customTo.value.wgsLng,
      travelMode: customMode.value
    })
    const data = res.data || {}
    const path = data.path && data.path.length >= 2 ? data.path : null
    customPreviewPath.value = path
    trainPlannedStops.value = null
    trainDescription.value = ''
    if (!path) {
      customHint.value = data.message || '未拿到折线，请换出行方式重试'
      return
    }
    const dist = data.distanceMeters != null
      ? (Number(data.distanceMeters) >= 1000
        ? `${(Number(data.distanceMeters) / 1000).toFixed(2)} km`
        : `${Math.round(Number(data.distanceMeters))} m`)
      : ''
    customHint.value = ['预览成功', dist ? `约 ${dist}` : '', data.message || ''].filter(Boolean).join(' · ')
    clusterMapRef.value?.refresh?.({ fit: true })
  } catch (e) {
    customPreviewPath.value = null
    customHint.value = '规划失败，请检查高德 Key 或稍后重试'
  } finally {
    customPlanning.value = false
  }
}

async function previewTrainSegment() {
  // 未填经停时，自动用起终点两站
  if (!trainManualStops.value.trim()) {
    const a = trainFromStation.value || trainFromPlace.value?.name
    const b = trainToStation.value || trainToPlace.value?.name
    if (a && b) {
      trainManualStops.value = `${a}\n${b}`
    }
  }
  if (!trainManualStops.value.trim() && !trainNo.value.trim()) {
    customHint.value = '请用高德选择起终点站，或手工填写经停'
    return
  }
  customPlanning.value = true
  customHint.value = '正在按经停站 OSM 贴轨（首次可能较慢）…'
  try {
    const body = {
      trainNo: trainNo.value || undefined,
      fromStation: trainFromStation.value || trainFromPlace.value?.name || undefined,
      toStation: trainToStation.value || trainToPlace.value?.name || undefined,
      travelMode: customMode.value || 'hsr',
      manualStops: trainManualStops.value.trim() || undefined
    }
    // 起终点都用高德选过：两站（或未填中间站）时直接带坐标，减少二次搜索偏差
    const stopLines = (trainManualStops.value || '').trim().split(/\n/).map(s => s.trim()).filter(Boolean)
    if (trainFromPlace.value && trainToPlace.value && stopLines.length <= 2) {
      body.stops = [
        {
          name: trainFromPlace.value.name,
          lat: trainFromPlace.value.lat,
          lng: trainFromPlace.value.lng,
          wgsLat: trainFromPlace.value.wgsLat,
          wgsLng: trainFromPlace.value.wgsLng
        },
        {
          name: trainToPlace.value.name,
          lat: trainToPlace.value.lat,
          lng: trainToPlace.value.lng,
          wgsLat: trainToPlace.value.wgsLat,
          wgsLng: trainToPlace.value.wgsLng
        }
      ]
      delete body.manualStops
    }
    const res = await planTrainRoute(body)
    const data = res.data || {}
    const path = data.path && data.path.length >= 2 ? data.path : null
    customPreviewPath.value = path
    trainPlannedStops.value = data.stops || null
    trainDescription.value = data.description || ''
    if (data.travelMode) customMode.value = data.travelMode
    if (!path) {
      customHint.value = data.message || '未拿到折线'
      return
    }
    const dist = data.distanceMeters != null
      ? (Number(data.distanceMeters) >= 1000
        ? `${(Number(data.distanceMeters) / 1000).toFixed(2)} km`
        : `${Math.round(Number(data.distanceMeters))} m`)
      : ''
    const stopN = data.stops?.length ? `${data.stops.length} 站` : ''
    const pts = path.length
    customHint.value = [
      pts <= 2 ? '仍是直线（OSM 可能失败）' : '车次预览成功',
      stopN,
      dist ? `约 ${dist}` : '',
      data.message || ''
    ].filter(Boolean).join(' · ')
    clusterMapRef.value?.refresh?.({ fit: true })
  } catch (e) {
    customPreviewPath.value = null
    trainPlannedStops.value = null
    customHint.value = e?.message || '车次贴轨失败'
  } finally {
    customPlanning.value = false
  }
}

async function submitCustomSegment() {
  if (!props.track?.trackId) return
  if (customInsertPos.value === 'after' && !customAfterPointId.value) {
    customHint.value = '请选择插入锚点'
    return
  }
  if (customTab.value === 'train') {
    await submitTrainSegment()
    return
  }
  if (!customFrom.value || !customTo.value || !customPreviewPath.value?.length) {
    customHint.value = '请先预览线路再添加'
    return
  }
  customSaving.value = true
  try {
    const body = {
      fromName: customFrom.value.name,
      fromLat: customFrom.value.lat,
      fromLng: customFrom.value.lng,
      toName: customTo.value.name,
      toLat: customTo.value.lat,
      toLng: customTo.value.lng,
      coords: 'gcj02',
      travelMode: customMode.value,
      routePath: JSON.stringify(customPreviewPath.value),
      append: customInsertPos.value === 'end',
      afterPointId: customInsertPos.value === 'after' ? customAfterPointId.value : null
    }
    if (customInsertPos.value === 'start') {
      body.append = false
      body.afterPointId = null
    }
    await addCustomSegment(props.track.trackId, body)
    proxy?.$modal?.msgSuccess?.('已增补路段')
    closeCustomPanel()
    editing.value = false
    emit('saved')
  } catch (e) {
    customHint.value = e?.message || '添加失败'
  } finally {
    customSaving.value = false
  }
}

async function submitTrainSegment() {
  if (!customPreviewPath.value?.length) {
    customHint.value = '请先预览线路再添加'
    return
  }
  customSaving.value = true
  try {
    const body = {
      trainNo: trainNo.value || undefined,
      fromStation: trainFromStation.value || undefined,
      toStation: trainToStation.value || undefined,
      travelMode: customMode.value || 'hsr',
      manualStops: trainManualStops.value.trim() || undefined,
      stops: trainPlannedStops.value || undefined,
      append: customInsertPos.value === 'end',
      afterPointId: customInsertPos.value === 'after' ? customAfterPointId.value : null
    }
    if (customInsertPos.value === 'start') {
      body.append = false
      body.afterPointId = null
    }
    const res = await addTrainSegment(props.track.trackId, body)
    const n = res.data?.insertedStops
    proxy?.$modal?.msgSuccess?.(n ? `已插入 ${n} 个车站途经点` : '已增补车次路段')
    closeCustomPanel()
    editing.value = false
    emit('saved')
  } catch (e) {
    customHint.value = e?.message || '添加失败'
  } finally {
    customSaving.value = false
  }
}

function modeBtnStyle(m) {
  if (draftMode.value === m.key) {
    return { background: m.color, borderColor: m.color, color: '#fff' }
  }
  return { borderColor: m.color, color: m.color }
}

function clonePoints(list) {
  return (list || []).map(p => ({
    ...p,
    travelMode: p.travelMode || '',
    description: p.description || '',
    routePath: p.routePath || ''
  }))
}

function startEdit() {
  draftPoints.value = clonePoints(props.points)
  editing.value = true
  activeIndex.value = -1
  draftMode.value = ''
  draftDesc.value = ''
  routeHint.value = '点击地图上的一段线路开始编辑'
  customOpen.value = false
  customPreviewPath.value = null
}

function cancelEdit() {
  editing.value = false
  activeIndex.value = -1
  draftPoints.value = []
  draftMode.value = ''
  draftDesc.value = ''
  routeHint.value = ''
  closeCustomPanel()
  closeWaypointPanel()
}

function ensureDragHandle(index) {
  const p = draftPoints.value[index]
  const next = draftPoints.value[index + 1]
  if (!p || !next) return
  let path = null
  if (p.routePath) {
    try {
      path = JSON.parse(p.routePath)
    } catch (e) {
      path = null
    }
  }
  if (!Array.isArray(path) || path.length < 2) {
    path = [toMapLatLng(p), toMapLatLng(next)]
  }
  // 仅有两端时插入中点，方便直接拖动改线
  if (path.length === 2) {
    const a = path[0]
    const b = path[1]
    path = [a, [(Number(a[0]) + Number(b[0])) / 2, (Number(a[1]) + Number(b[1])) / 2], b]
    p.routePath = JSON.stringify(path)
    draftPoints.value = [...draftPoints.value]
  }
}

function onSegmentClick({ index }) {
  // 仅「编辑轨迹」开启后才允许点选/改线
  if (!props.editable || !editing.value) return
  waypointIndex.value = -1
  activeIndex.value = index
  ensureDragHandle(index)
  const p = draftPoints.value[index]
  draftMode.value = p?.travelMode || ''
  draftDesc.value = p?.description || ''
  routeHint.value = '可拖动橙色拐点改线，或点「贴合路网」按出行方式重算'
}

function onWaypointClick({ index }) {
  if (!props.editable || !editing.value || customOpen.value) return
  activeIndex.value = -1
  waypointIndex.value = index
  const p = draftPoints.value[index] || props.points[index]
  waypointDesc.value = p?.description || ''
  waypointHint.value = canDeleteWaypointSegment.value
    ? '此点与下一点构成自定义路段，可删整段或只删此点'
    : '删除后将自动与前后点重新连接'
}

function closeWaypointPanel() {
  waypointIndex.value = -1
  waypointDesc.value = ''
  waypointHint.value = ''
}

async function saveWaypointDesc() {
  const p = waypointPoint.value
  if (!p?.pointId) return
  waypointSaving.value = true
  try {
    await updateTrackPoints([{
      pointId: p.pointId,
      description: waypointDesc.value || '',
      travelMode: p.travelMode ?? '',
      routePath: p.routePath ?? '',
      sequence: p.sequence
    }])
    if (editing.value && draftPoints.value[waypointIndex.value]) {
      draftPoints.value[waypointIndex.value].description = waypointDesc.value || ''
      draftPoints.value = [...draftPoints.value]
    }
    proxy?.$modal?.msgSuccess?.('途经点说明已保存')
    emit('saved')
  } finally {
    waypointSaving.value = false
  }
}

async function deleteWaypoint(wholeSegment) {
  const p = waypointPoint.value
  if (!p?.pointId) return
  const next = displayPoints.value[waypointIndex.value + 1]
  const msg = wholeSegment && next?.pointId
    ? `确认删除自定义路段「${pointLabel(p)} → ${pointLabel(next)}」？`
    : `确认删除途经点「${pointLabel(p)}」？`
  try {
    await proxy?.$modal?.confirm?.(msg)
  } catch (e) {
    return
  }
  waypointDeleting.value = true
  try {
    if (wholeSegment && next?.pointId && isWaypointPoint(next)) {
      await delTrackPoint(p.pointId)
      await delTrackPoint(next.pointId)
    } else {
      await delTrackPoint(p.pointId)
    }
    proxy?.$modal?.msgSuccess?.('已删除')
    closeWaypointPanel()
    editing.value = false
    emit('saved')
  } finally {
    waypointDeleting.value = false
  }
}

async function deleteActiveCustomSegment() {
  const i = activeIndex.value
  const from = displayPoints.value[i]
  const to = displayPoints.value[i + 1]
  if (!from?.pointId || !to?.pointId) return
  try {
    await proxy?.$modal?.confirm?.(`确认删除自定义路段「${pointLabel(from)} → ${pointLabel(to)}」？`)
  } catch (e) {
    return
  }
  waypointDeleting.value = true
  try {
    await delTrackPoint(from.pointId)
    await delTrackPoint(to.pointId)
    proxy?.$modal?.msgSuccess?.('已删除自定义路段')
    activeIndex.value = -1
    editing.value = false
    emit('saved')
  } finally {
    waypointDeleting.value = false
  }
}

function onSegmentPathChange({ index, path }) {
  if (!editing.value || index < 0) return
  const p = draftPoints.value[index]
  if (!p || !path || path.length < 2) return
  p.routePath = JSON.stringify(path)
  routeHint.value = `已手动调整（${path.length} 个点），记得保存`
  draftPoints.value = [...draftPoints.value]
}

function applyDescription() {
  if (activeIndex.value < 0) return
  const p = draftPoints.value[activeIndex.value]
  if (!p) return
  p.description = draftDesc.value || ''
  draftPoints.value = [...draftPoints.value]
}

async function onPickMode(key) {
  draftMode.value = draftMode.value === key ? '' : key
  if (activeIndex.value < 0) return
  const p = draftPoints.value[activeIndex.value]
  if (!p) return
  p.travelMode = draftMode.value
  // 改方式后清掉旧折线，并按新方式重新贴合，避免保存后仍显示旧路线/被自动修路改回
  p.routePath = ''
  draftPoints.value = [...draftPoints.value]
  if (draftMode.value) {
    await snapSegment()
  } else {
    routeHint.value = '已清空出行方式'
  }
}

async function snapSegment() {
  if (activeIndex.value < 0) return
  const p = draftPoints.value[activeIndex.value]
  const next = draftPoints.value[activeIndex.value + 1]
  if (!p || !next) return

  p.description = draftDesc.value || ''
  const mode = draftMode.value || p.travelMode || 'walk'
  draftMode.value = mode
  p.travelMode = mode

  planning.value = true
  routeHint.value = '正在按高德路网贴合…'
  try {
    const res = await previewTrackRoute({
      fromLat: p.latitude,
      fromLng: p.longitude,
      toLat: next.latitude,
      toLng: next.longitude,
      travelMode: mode
    })
    const data = res.data || {}
    p.routePath = data.path && data.path.length >= 2 ? JSON.stringify(data.path) : ''
    const dist = data.distanceMeters != null
      ? (Number(data.distanceMeters) >= 1000
        ? `${(Number(data.distanceMeters) / 1000).toFixed(2)} km`
        : `${Math.round(Number(data.distanceMeters))} m`)
      : ''
    routeHint.value = [
      p.routePath ? '已贴合路网，可再拖动微调' : '未拿到折线，暂用直线',
      dist ? `约 ${dist}` : '',
      data.message || ''
    ].filter(Boolean).join(' · ')
    draftPoints.value = [...draftPoints.value]
  } catch (e) {
    routeHint.value = '贴合失败，请检查高德 Web Key 或改为手动拖线'
  } finally {
    planning.value = false
  }
}

function straightenSegment() {
  if (activeIndex.value < 0) return
  const p = draftPoints.value[activeIndex.value]
  const next = draftPoints.value[activeIndex.value + 1]
  if (!p || !next) return
  p.description = draftDesc.value || ''
  if (draftMode.value) p.travelMode = draftMode.value
  p.routePath = JSON.stringify([toMapLatLng(p), toMapLatLng(next)])
  routeHint.value = '已改为直线，可拖动加点或重新贴合路网'
  draftPoints.value = [...draftPoints.value]
}

function clearSegment() {
  if (activeIndex.value < 0) return
  draftMode.value = ''
  draftDesc.value = ''
  const p = draftPoints.value[activeIndex.value]
  if (!p) return
  p.travelMode = ''
  p.description = ''
  p.routePath = ''
  routeHint.value = '已清空本段，将显示为默认直线'
  draftPoints.value = [...draftPoints.value]
}

function saveEdit() {
  if (!draftPoints.value.length) {
    cancelEdit()
    return
  }
  applyDescription()
  saving.value = true
  const payload = draftPoints.value.map(p => ({
    pointId: p.pointId,
    travelMode: p.travelMode ?? '',
    description: p.description ?? '',
    routePath: p.routePath ?? '',
    sequence: p.sequence
  }))
  updateTrackPoints(payload).then(() => {
    proxy?.$modal?.msgSuccess?.('轨迹路段已保存')
    editing.value = false
    activeIndex.value = -1
    emit('saved')
  }).finally(() => {
    saving.value = false
  })
}

function refresh() {
  clusterMapRef.value?.refresh?.({ fit: !editing.value })
}

function replanRoutes() {
  if (!props.track?.trackId || replanning.value || editing.value) return
  replanning.value = true
  emit('replan', {
    trackId: props.track.trackId,
    done: () => {
      replanning.value = false
    }
  })
}

defineExpose({ refresh, startEdit })
</script>

<style scoped>
.track-map-viewer {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 0;
}

.track-meta {
  max-width: min(100%, 560px);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.12);
  color: #606266;
  font-size: 13px;
}

.meta-bar {
  display: flex;
  flex-wrap: nowrap;
  align-items: center;
  gap: 8px;
  padding: 6px 8px 6px 10px;
}

.meta-toggle {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  max-width: 220px;
  padding: 0;
  border: 0;
  background: transparent;
  cursor: pointer;
  text-align: left;
}

.track-meta .name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.meta-chevron {
  flex-shrink: 0;
  color: #909399;
  font-size: 12px;
}

.meta-actions {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 6px;
  margin-left: auto;
}

.meta-detail {
  padding: 0 12px 10px;
  border-top: 1px solid rgba(0, 0, 0, 0.06);
}

.meta-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 8px;
  color: #606266;
  font-size: 12px;
}

.track-meta .remark {
  margin-top: 6px;
  color: #606266;
  font-size: 12px;
  line-height: 1.45;
  white-space: pre-wrap;
  word-break: break-word;
}

.dir-legend {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  color: #606266;
  font-size: 12px;
}

.dir-start,
.dir-end {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 20px;
  height: 20px;
  padding: 0 5px;
  border-radius: 10px;
  color: #fff;
  font-weight: 700;
}

.dir-start { background: #16a34a; }
.dir-end { background: #dc2626; }

.dir-flow {
  color: #2563eb;
  font-weight: 600;
  letter-spacing: 1px;
}

.edit-tip {
  margin: 8px 0 0;
  color: #909399;
  font-size: 12px;
  line-height: 1.4;
}

.mode-legend {
  position: absolute;
  z-index: 500;
  /* 放右下角，避开左下角缩放按钮与右下角 attribution 文字区略上移 */
  right: 12px;
  bottom: 28px;
  left: auto;
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
  max-width: min(420px, calc(100% - 70px));
  padding: 6px 10px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.12);
  pointer-events: none;
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #606266;
  font-size: 12px;
}

.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

.seg-panel {
  position: absolute;
  z-index: 600;
  top: 72px;
  right: 16px;
  width: min(360px, calc(100% - 32px));
  max-height: calc(100% - 96px);
  overflow: auto;
  padding: 14px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.16);
}

.seg-panel-hd {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.seg-panel-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.seg-close {
  border: 0;
  background: transparent;
  color: #909399;
  font-size: 22px;
  line-height: 1;
  cursor: pointer;
}

.seg-route {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  gap: 8px;
  align-items: center;
  margin-bottom: 12px;
  padding: 10px;
  border-radius: 8px;
  background: #f5f7fa;
}

.seg-end {
  min-width: 0;
}

.seg-no {
  display: inline-block;
  margin-right: 4px;
  color: #409eff;
  font-weight: 600;
}

.seg-name {
  display: block;
  margin-top: 2px;
  color: #606266;
  font-size: 12px;
  line-height: 1.4;
  word-break: break-all;
}

.seg-arrow {
  color: #909399;
  font-weight: 600;
}

.seg-label {
  margin: 10px 0 8px;
  color: #303133;
  font-size: 13px;
  font-weight: 600;
}

.path-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.path-tip {
  margin: 8px 0 0;
  color: #909399;
  font-size: 12px;
  line-height: 1.45;
}

.mode-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}

.mode-btn {
  border: 1px solid;
  background: #fff;
  border-radius: 8px;
  padding: 8px 0;
  font-size: 13px;
  cursor: pointer;
}

.mode-btn:hover {
  filter: brightness(0.97);
}

.seg-panel-ft {
  display: flex;
  gap: 8px;
  margin-top: 14px;
}

.route-hint {
  margin: 10px 0 0;
  color: #909399;
  font-size: 12px;
  line-height: 1.45;
}

.custom-panel .insert-pos {
  display: flex;
  flex-wrap: wrap;
}

.custom-tabs {
  display: flex;
  gap: 6px;
  margin-bottom: 8px;
}

.custom-tab {
  flex: 1;
  padding: 7px 0;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  background: #fff;
  color: #606266;
  font-size: 13px;
  cursor: pointer;
}

.custom-tab.active {
  border-color: #409eff;
  background: #ecf5ff;
  color: #409eff;
  font-weight: 600;
}

.train-row {
  display: flex;
  gap: 8px;
}

.train-col {
  flex: 1;
  min-width: 0;
}

.api-disabled-tip {
  margin: 6px 0 0;
  color: #e6a23c;
  font-size: 12px;
  line-height: 1.4;
}

.place-opt {
  line-height: 1.3;
  padding: 2px 0;
}

.place-name {
  color: #303133;
  font-size: 13px;
}

.place-addr {
  color: #909399;
  font-size: 12px;
}

.place-picked {
  margin-top: 6px;
  color: #67c23a;
  font-size: 12px;
}
</style>
