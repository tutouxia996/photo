<template>
  <div class="track-map-viewer">
    <PhotoClusterMap
      ref="clusterMapRef"
      :points="displayPoints"
      :show-polyline="trackLineVisible"
      :polyline-color="track?.trackColor || '#3B82F6'"
      :segment-by-travel-mode="true"
      :show-direction="trackLineVisible && !editing && !customOpen && !boxSelectMode"
      :editable="editable && editing && !customOpen && !boxSelectMode"
      :active-segment-index="editing && !customOpen && !boxSelectMode ? activeIndex : -1"
      :path-edit-index="editing && !customOpen && !boxSelectMode ? activeIndex : -1"
      :segment-labels="editing ? 'none' : 'hover'"
      :preview-path="customPreviewPath"
      :overlay-paths="localGpxOverlays"
      :visible-travel-modes="visibleTravelModes"
      :gpx-endpoint-pickable="editing && customOpen && customTab === 'place'"
      :point-pickable="editing && customOpen && customTab === 'place'"
      :box-select-active="editing && boxSelectMode"
      :selected-segment-indexes="selectedSegmentIndexes"
      :auto-fit="!editing"
      empty-text="暂无轨迹点位"
      @segment-click="onSegmentClick"
      @segment-path-change="onSegmentPathChange"
      @waypoint-click="onWaypointClick"
      @point-click="onTrackPointPick"
      @gpx-click="onGpxClick"
      @gpx-endpoint-click="onGpxEndpointClick"
      @box-select="onBoxSelect"
    >
      <template #meta>
        <div v-if="track && trackLineVisible" class="track-meta" :class="{ 'is-open': metaOpen }">
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
                title="仅对照片轨点之间缺折线的路段贴合；已匹配 GPX 的媒体不会进照片轨；已有手动连接会保留；不影响 GPX"
                @click="replanRoutes"
              >贴合路网</el-button>
              <template v-if="editable">
                <el-button
                  v-if="!editing"
                  type="primary"
                  size="small"
                  title="编辑照片/自定义轨迹；GPX 出行方式可点击绿色/彩色线路修改"
                  @click="startEdit"
                >编辑轨迹</el-button>
                <template v-else>
                  <el-button
                    size="small"
                    :type="boxSelectMode ? 'warning' : 'default'"
                    title="拖拽框选多段路，批量改出行方式并贴合路网"
                    @click="toggleBoxSelect"
                  >{{ boxSelectMode ? '退出框选' : '框选' }}</el-button>
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
            <p v-if="editing && boxSelectMode" class="edit-tip">在地图上按住拖拽拉框，选中橙色高亮路段后可批量改出行方式并贴合路网。蓝虚线表示尚未贴合。</p>
            <p v-else-if="editing" class="edit-tip">点线路改走向；可用「框选」批量修改。点橙色途经点可改说明或删除自定义路段。增补时可选用 GPX 起/终或照片点做连接（不改 GPX 折线）。</p>
            <p v-else-if="hasGpxPathOverlay" class="edit-tip">彩色 GPX 线路按出行方式着色（与照片轨一致）；点击线路可查看详情并修改出行方式。</p>
          </div>
        </div>
      </template>
    </PhotoClusterMap>

    <div v-if="legendItems.length && !editing" class="mode-legend" :class="{ 'is-filtering': modeFilter.length > 0 }">
      <button
        v-for="m in legendItems"
        :key="m.key"
        type="button"
        class="legend-item"
        :class="{ active: isModeFilterActive(m.key), muted: modeFilter.length > 0 && !isModeFilterActive(m.key) }"
        :title="modeFilterHint(m)"
        @click="toggleModeFilter(m.key)"
      >
        <i class="legend-dot" :style="{ background: m.color }" />
        {{ m.label }}
      </button>
      <button
        v-if="modeFilter.length"
        type="button"
        class="legend-reset"
        title="显示全部线路"
        @click="clearModeFilter"
      >全部</button>
    </div>

    <aside v-if="gpxPanelOpen && activeGpx" class="seg-panel gpx-panel">
      <div class="seg-panel-hd">
        <div class="seg-panel-title">GPX 出行方式</div>
        <button type="button" class="seg-close" @click="closeGpxPanel">×</button>
      </div>
      <p class="path-tip">{{ activeGpx.fileName || ('GPX #' + activeGpx.gpxId) }}</p>
      <div class="seg-label">出行方式</div>
      <el-select
        v-model="gpxDraftMode"
        placeholder="选择出行方式"
        style="width: 100%"
        :disabled="!editable || gpxModeSaving"
      >
        <el-option
          v-for="m in travelModes"
          :key="m.key"
          :label="m.label"
          :value="m.key"
        >
          <span class="mode-opt">
            <i class="legend-dot" :style="{ background: m.color }" />
            {{ m.label }}
          </span>
        </el-option>
      </el-select>
      <p class="path-tip">根据平均速度自动推断；可手动改为与照片/自定义路段一致的颜色。</p>
      <div class="seg-actions" v-if="editable">
        <el-button type="primary" :loading="gpxModeSaving" @click="saveGpxTravelMode">保存</el-button>
        <el-button :disabled="gpxModeSaving" @click="closeGpxPanel">取消</el-button>
      </div>
    </aside>

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
        <p class="path-tip">
          起终点可选：高德搜索、GPX 起/终、或现有照片/途经点。选好后预览即形成连接（不改 GPX 折线）。
          默认不自动接到前后邻点；需要时勾选下方选项。
        </p>

        <div class="seg-label">下次地图点击填入</div>
        <el-radio-group v-model="gpxPickSide" class="insert-pos" size="small">
          <el-radio-button label="auto">自动</el-radio-button>
          <el-radio-button label="from">起点</el-radio-button>
          <el-radio-button label="to">终点</el-radio-button>
        </el-radio-group>

        <div v-if="gpxAnchorOptions.length" class="seg-label" style="margin-top: 10px">选用 GPX 端点</div>
        <div v-if="gpxAnchorOptions.length" class="gpx-anchor-row">
          <el-select
            v-model="customFromGpxKey"
            clearable
            filterable
            placeholder="起点 ← GPX 起/终"
            style="width: 100%"
            @change="onPickGpxAnchor('from')"
          >
            <el-option
              v-for="item in gpxAnchorOptions"
              :key="item.key"
              :label="item.name"
              :value="item.key"
            />
          </el-select>
          <el-select
            v-model="customToGpxKey"
            clearable
            filterable
            placeholder="终点 ← GPX 起/终"
            style="width: 100%; margin-top: 8px"
            @change="onPickGpxAnchor('to')"
          >
            <el-option
              v-for="item in gpxAnchorOptions"
              :key="'to-' + item.key"
              :label="item.name"
              :value="item.key"
            />
          </el-select>
        </div>

        <div v-if="trackPointAnchorOptions.length" class="seg-label" style="margin-top: 10px">选用现有轨迹点</div>
        <div v-if="trackPointAnchorOptions.length" class="gpx-anchor-row">
          <el-select
            v-model="customFromTrackKey"
            clearable
            filterable
            placeholder="起点 ← 照片/途经点"
            style="width: 100%"
            @change="onPickTrackAnchor('from')"
          >
            <el-option
              v-for="item in trackPointAnchorOptions"
              :key="item.key"
              :label="item.name"
              :value="item.key"
            />
          </el-select>
          <el-select
            v-model="customToTrackKey"
            clearable
            filterable
            placeholder="终点 ← 照片/途经点"
            style="width: 100%; margin-top: 8px"
            @change="onPickTrackAnchor('to')"
          >
            <el-option
              v-for="item in trackPointAnchorOptions"
              :key="'to-' + item.key"
              :label="item.name"
              :value="item.key"
            />
          </el-select>
        </div>

        <div class="seg-label">起点（或高德搜索）</div>
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

        <div class="seg-label">终点（或高德搜索）</div>
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

        <div class="seg-label" style="margin-top: 10px">可选邻接连接</div>
        <div class="link-opts">
          <el-checkbox v-model="customLinkPrev">同时连接上一轨迹点 → 本段起点</el-checkbox>
          <el-checkbox v-model="customLinkNext">同时连接本段终点 → 下一轨迹点</el-checkbox>
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

    <aside v-if="editing && !customOpen && !boxSelectMode && waypointIndex >= 0" class="seg-panel">
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

    <aside v-if="editing && boxSelectMode" class="seg-panel" @mousedown.stop @click.stop>
      <div class="seg-panel-hd">
        <div class="seg-panel-title">框选批量修改</div>
        <button type="button" class="seg-close" @click="exitBoxSelect">×</button>
      </div>
      <p class="path-tip">已选 <b>{{ selectedSegmentIndexes.length }}</b> 段（橙色虚线=选中且未贴合）。选出行方式 → 贴合路网 → 保存。</p>
      <div class="seg-label">出行方式</div>
      <div class="mode-grid">
        <button
          v-for="m in travelModes"
          :key="m.key"
          type="button"
          class="mode-btn"
          :class="{ active: batchMode === m.key }"
          :style="modeBtnStyle(m, batchMode)"
          @click.stop="onPickBatchMode(m.key)"
        >{{ m.label }}</button>
      </div>
      <div class="path-actions" style="margin-top: 10px">
        <el-button
          type="primary"
          size="small"
          :loading="batchPlanning"
          :disabled="!selectedSegmentIndexes.length"
          @click="applyBatchSnap"
        >批量贴合路网</el-button>
        <el-button size="small" :disabled="!selectedSegmentIndexes.length" @click="clearBoxSelection">清空选中</el-button>
      </div>
      <p v-if="batchHint" class="route-hint">{{ batchHint }}</p>
      <div class="seg-panel-ft">
        <el-button type="primary" :loading="saving || batchPlanning" @click="saveEdit">保存全部</el-button>
        <el-button @click="exitBoxSelect">退出框选</el-button>
      </div>
    </aside>

    <aside v-if="editing && !customOpen && !boxSelectMode && waypointIndex < 0 && activeIndex >= 0" class="seg-panel">
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
          :style="modeBtnStyle(m, draftMode)"
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
import { computed, getCurrentInstance, nextTick, ref, watch } from 'vue'
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
  setTrackGpxTravelMode,
  updateTrackPoints
} from '@/api/album/track'
  import { isWaypointPoint, TRAVEL_MODES, toMapLatLng, travelModeColor, wgs84ToGcj02 } from '@/utils/photoMapCluster'

const props = defineProps({
  track: { type: Object, default: null },
  points: { type: Array, default: () => [] },
  /** 相册 GPX 叠层（与照片/自定义轨迹同图显示） */
  gpxOverlays: { type: Array, default: () => [] },
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

/** 框选批量 */
const boxSelectMode = ref(false)
const selectedSegmentIndexes = ref([])
const batchMode = ref('walk')
const batchPlanning = ref(false)
const batchHint = ref('')

const localGpxOverlays = ref([])
watch(() => props.gpxOverlays, (list) => {
  localGpxOverlays.value = Array.isArray(list)
    ? list.map(o => (o ? { ...o, matchedPhotos: Array.isArray(o.matchedPhotos) ? o.matchedPhotos.slice() : [] } : o))
    : []
}, { immediate: true, deep: true })

const gpxPanelOpen = ref(false)
const activeGpx = ref(null)
const gpxDraftMode = ref('walk')
const gpxModeSaving = ref(false)

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
const customFromGpxKey = ref('')
const customToGpxKey = ref('')
const customFromTrackKey = ref('')
const customToTrackKey = ref('')
/** 地图点选时填入哪一侧：auto | from | to */
const gpxPickSide = ref('auto')
const customLinkPrev = ref(false)
const customLinkNext = ref(false)
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

/** 关闭启用时不画线路；自定义途经点一并隐藏，照片/视频点仍展示 */
const trackLineVisible = computed(() => {
  const t = props.track
  return t == null || t.enabled == null || t.enabled === 1
})

const displayPoints = computed(() => {
  const list = editing.value ? draftPoints.value : props.points
  if (!Array.isArray(list)) return []
  if (trackLineVisible.value) return list
  return list.filter(p => !isWaypointPoint(p))
})

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

const hasGpxPathOverlay = computed(() => localGpxOverlays.value.some(o => {
  if (!o || o.showPath === false) return false
  if (Array.isArray(o.path) && o.path.length >= 2) return true
  return Number(o.pathPointCount) >= 2
}))

/** 增补路段可选的 GPX 起/终点锚点（只读坐标，不改 GPX） */
const gpxAnchorOptions = computed(() => {
  const list = []
  localGpxOverlays.value.forEach((o) => {
    if (!o || o.showPath === false) return
    const path = Array.isArray(o.path) ? o.path : []
    if (path.length < 2) return
    const fileName = o.fileName || (`GPX #${o.gpxId || ''}`)
    const start = pathPointToAnchor(path[0], o.gpxId, 'start', `GPX《${fileName}》起点`)
    const end = pathPointToAnchor(path[path.length - 1], o.gpxId, 'end', `GPX《${fileName}》终点`)
    if (start) list.push(start)
    if (end) list.push(end)
  })
  return list
})

/** 现有照片/途经点，可选手动连接 */
const trackPointAnchorOptions = computed(() => {
  return displayPoints.value
    .filter(p => p && p.pointId != null && p.latitude != null && p.longitude != null)
    .map((p) => {
      const wgsLat = Number(p.latitude)
      const wgsLng = Number(p.longitude)
      const [gcjLng, gcjLat] = wgs84ToGcj02(wgsLng, wgsLat)
      const kind = isWaypointPoint(p) ? '途经' : '照片'
      const label = pointLabel(p)
      return {
        key: `pt-${p.pointId}`,
        pointId: p.pointId,
        name: `#${p.sequence ?? ''} ${kind} ${label}`.trim(),
        id: `pt-${p.pointId}`,
        address: kind,
        lat: gcjLat,
        lng: gcjLng,
        wgsLat,
        wgsLng,
        source: 'track',
        photoId: p.photoId
      }
    })
})

function pathPointToAnchor(p, gpxId, kind, name) {
  if (!p) return null
  const lat = Number(p.lat != null ? p.lat : p[0])
  const lng = Number(p.lng != null ? p.lng : p[1])
  if (Number.isNaN(lat) || Number.isNaN(lng)) return null
  const wgsLat = p.latWgs != null ? Number(p.latWgs) : null
  const wgsLng = p.lngWgs != null ? Number(p.lngWgs) : null
  return {
    key: `gpx-${gpxId}-${kind}`,
    gpxId,
    kind,
    name,
    id: `gpx-${gpxId}-${kind}`,
    address: 'GPX端点（只读）',
    lat,
    lng,
    wgsLat: Number.isNaN(wgsLat) ? null : wgsLat,
    wgsLng: Number.isNaN(wgsLng) ? null : wgsLng,
    source: 'gpx'
  }
}

/** 照片轨 + GPX 的出行方式，统一圆点图例（颜色一致） */
const legendItems = computed(() => {
  const keys = new Set()
  if (trackLineVisible.value) {
    usedModes.value.forEach(m => keys.add(m.key))
  }
  localGpxOverlays.value.forEach(o => {
    if (!o || o.showPath === false) return
    const hasPath = (Array.isArray(o.path) && o.path.length >= 2) || Number(o.pathPointCount) >= 2
    if (!hasPath) return
    if (o.travelMode) keys.add(String(o.travelMode).toLowerCase())
  })
  return TRAVEL_MODES.filter(m => keys.has(m.key))
})

/** 选中的出行方式；空数组表示显示全部 */
const modeFilter = ref([])

const visibleTravelModes = computed(() => (
  modeFilter.value.length ? modeFilter.value.slice() : null
))

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

watch(legendItems, (items) => {
  const keys = new Set(items.map(m => m.key))
  modeFilter.value = modeFilter.value.filter(k => keys.has(k))
})

watch(editing, (val) => {
  if (val) {
    modeFilter.value = []
    closeGpxPanel()
  }
})

function onGpxClick({ overlay }) {
  // 增补选点时不打开出行方式面板，避免打断锚点选择
  if (customOpen.value) return
  if (!overlay?.gpxId) return
  activeGpx.value = overlay
  gpxDraftMode.value = overlay.travelMode || 'walk'
  gpxPanelOpen.value = true
}

function applyGpxAnchor(which, anchor) {
  if (!anchor) return
  if (which === 'from') {
    customFrom.value = { ...anchor }
    customFromId.value = ''
    customFromGpxKey.value = anchor.key
    customFromTrackKey.value = ''
  } else {
    customTo.value = { ...anchor }
    customToId.value = ''
    customToGpxKey.value = anchor.key
    customToTrackKey.value = ''
  }
  customPreviewPath.value = null
  customHint.value = `已选用 ${anchor.name} 作为${which === 'from' ? '起点' : '终点'}（不改 GPX）`
}

function applyTrackAnchor(which, anchor) {
  if (!anchor) return
  if (which === 'from') {
    customFrom.value = { ...anchor }
    customFromId.value = ''
    customFromTrackKey.value = anchor.key
    customFromGpxKey.value = ''
  } else {
    customTo.value = { ...anchor }
    customToId.value = ''
    customToTrackKey.value = anchor.key
    customToGpxKey.value = ''
  }
  customPreviewPath.value = null
  customHint.value = `已选用 ${anchor.name} 作为${which === 'from' ? '起点' : '终点'}`
}

function onPickGpxAnchor(which) {
  const key = which === 'from' ? customFromGpxKey.value : customToGpxKey.value
  if (!key) {
    if (which === 'from' && customFrom.value?.source === 'gpx') customFrom.value = null
    if (which === 'to' && customTo.value?.source === 'gpx') customTo.value = null
    customPreviewPath.value = null
    return
  }
  const found = gpxAnchorOptions.value.find(o => o.key === key)
  if (found) applyGpxAnchor(which, found)
}

function onPickTrackAnchor(which) {
  const key = which === 'from' ? customFromTrackKey.value : customToTrackKey.value
  if (!key) {
    if (which === 'from' && customFrom.value?.source === 'track') customFrom.value = null
    if (which === 'to' && customTo.value?.source === 'track') customTo.value = null
    customPreviewPath.value = null
    return
  }
  const found = trackPointAnchorOptions.value.find(o => o.key === key)
  if (found) applyTrackAnchor(which, found)
}

function resolvePickSide() {
  let which = gpxPickSide.value
  if (which === 'auto') {
    which = !customFrom.value ? 'from' : 'to'
  }
  return which
}

function onGpxEndpointClick({ overlay, kind }) {
  if (!customOpen.value || customTab.value !== 'place') return
  const path = Array.isArray(overlay?.path) ? overlay.path : []
  if (path.length < 2) return
  const fileName = overlay.fileName || (`GPX #${overlay.gpxId || ''}`)
  const pt = kind === 'end' ? path[path.length - 1] : path[0]
  const label = kind === 'end' ? `GPX《${fileName}》终点` : `GPX《${fileName}》起点`
  const anchor = pathPointToAnchor(pt, overlay.gpxId, kind, label)
  if (!anchor) return
  const which = resolvePickSide()
  applyGpxAnchor(which, anchor)
  if (gpxPickSide.value === 'auto' && which === 'from' && !customTo.value) {
    customHint.value = `已选起点：${anchor.name}。再点 GPX 端点 / 轨迹点或搜索终点`
  }
}

function onTrackPointPick({ point }) {
  if (!customOpen.value || customTab.value !== 'place' || !point?.pointId) return
  const found = trackPointAnchorOptions.value.find(o => o.pointId === point.pointId)
  if (!found) return
  const which = resolvePickSide()
  applyTrackAnchor(which, found)
  if (gpxPickSide.value === 'auto' && which === 'from' && !customTo.value) {
    customHint.value = `已选起点：${found.name}。再选终点`
  }
}

function closeGpxPanel() {
  gpxPanelOpen.value = false
  activeGpx.value = null
}

async function saveGpxTravelMode() {
  const gpx = activeGpx.value
  if (!gpx?.gpxId || !gpxDraftMode.value) return
  gpxModeSaving.value = true
  try {
    await setTrackGpxTravelMode(gpx.gpxId, gpxDraftMode.value)
    const mode = gpxDraftMode.value
    const color = travelModeColor(mode, gpx.color || '#10B981')
    localGpxOverlays.value = localGpxOverlays.value.map(o => {
      if (!o || o.gpxId !== gpx.gpxId) return o
      return { ...o, travelMode: mode, color }
    })
    activeGpx.value = { ...gpx, travelMode: mode, color }
    proxy.$modal.msgSuccess('已更新 GPX 出行方式')
    nextTick(() => clusterMapRef.value?.refresh?.({ fit: false }))
  } catch (e) {
    proxy.$modal.msgError('更新失败')
  } finally {
    gpxModeSaving.value = false
  }
}

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
  if (which === 'from') {
    customFrom.value = found
    customFromGpxKey.value = ''
    customFromTrackKey.value = ''
  } else {
    customTo.value = found
    customToGpxKey.value = ''
    customToTrackKey.value = ''
  }
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
    exitBoxSelect(false)
    customOpen.value = true
    activeIndex.value = -1
    customTab.value = 'place'
    gpxPickSide.value = 'auto'
    closeGpxPanel()
    customHint.value = gpxAnchorOptions.value.length || trackPointAnchorOptions.value.length
      ? '可选：搜索地点 / GPX 起终 / 现有轨迹点；默认不自动接邻点'
      : '搜索并选择起点、终点后预览，再添加到轨迹'
    loadTrainApiStatus()
  }
}

function closeCustomPanel() {
  customOpen.value = false
  customPreviewPath.value = null
  customHint.value = ''
  customFromGpxKey.value = ''
  customToGpxKey.value = ''
  customFromTrackKey.value = ''
  customToTrackKey.value = ''
  customLinkPrev.value = false
  customLinkNext.value = false
  gpxPickSide.value = 'auto'
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
    customHint.value = '请先选择起点和终点（可搜索或点选 GPX 起/终点）'
    return
  }
  if (customFrom.value.wgsLat == null || customFrom.value.wgsLng == null
    || customTo.value.wgsLat == null || customTo.value.wgsLng == null) {
    customHint.value = '起终点缺少有效坐标，请重新选择'
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
  const reusePoint = !!(customFrom.value?.pointId || customTo.value?.pointId)
  if (!reusePoint && customInsertPos.value === 'after' && !customAfterPointId.value) {
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
      afterPointId: customInsertPos.value === 'after' ? customAfterPointId.value : null,
      linkPrev: !!customLinkPrev.value,
      linkNext: !!customLinkNext.value
    }
    if (customFrom.value.source === 'track' && customFrom.value.pointId) {
      body.fromPointId = customFrom.value.pointId
    }
    if (customTo.value.source === 'track' && customTo.value.pointId) {
      body.toPointId = customTo.value.pointId
    }
    if (customInsertPos.value === 'start') {
      body.append = false
      body.afterPointId = null
    }
    // 起终点已引用现有点时，插入位置由后端按点位衔接，忽略面板插入位
    if (body.fromPointId || body.toPointId) {
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

function modeBtnStyle(m, current) {
  const cur = current == null ? draftMode.value : current
  if (cur === m.key) {
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
  routeHint.value = '点击地图上的一段线路开始编辑；或点「框选」批量修改'
  customOpen.value = false
  customPreviewPath.value = null
  exitBoxSelect(false)
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
  exitBoxSelect(false)
}

function toggleBoxSelect() {
  if (!editing.value) return
  if (boxSelectMode.value) {
    exitBoxSelect()
    return
  }
  customOpen.value = false
  activeIndex.value = -1
  waypointIndex.value = -1
  boxSelectMode.value = true
  selectedSegmentIndexes.value = []
  batchHint.value = '在地图上拖拽拉框选中路段'
}

function exitBoxSelect(keepHint = true) {
  boxSelectMode.value = false
  selectedSegmentIndexes.value = []
  batchPlanning.value = false
  batchMode.value = ''
  if (!keepHint) batchHint.value = ''
}

function clearBoxSelection() {
  selectedSegmentIndexes.value = []
  batchHint.value = '已清空选中'
}

function onPickBatchMode(key) {
  const next = batchMode.value === key ? '' : key
  batchMode.value = next
  const indexes = selectedSegmentIndexes.value
  if (!indexes.length) {
    batchHint.value = next
      ? '已选出行方式，请先在地图上框选路段，再点「批量贴合路网」'
      : '已取消出行方式'
    return
  }
  const label = travelModes.find(m => m.key === next)?.label || next
  const nextPoints = draftPoints.value.map(p => ({ ...p }))
  for (const i of indexes) {
    const p = nextPoints[i]
    if (!p) continue
    p.travelMode = next
    // 改方式后旧折线作废；未贴合前保持虚线，避免以为已生成路网
    if (next) p.routePath = ''
  }
  draftPoints.value = nextPoints
  batchHint.value = next
    ? `已将 ${indexes.length} 段设为「${label}」。请点「批量贴合路网」生成实线路线，再保存`
    : `已清空 ${indexes.length} 段出行方式`
}

function pointInBox(latlng, box) {
  if (!latlng || !box) return false
  const lat = Number(latlng[0])
  const lng = Number(latlng[1])
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) return false
  return lat >= box.south && lat <= box.north && lng >= box.west && lng <= box.east
}

function onBoxSelect(box) {
  if (!boxSelectMode.value || !box) return
  const list = draftPoints.value
  if (!list.length) return
  const hit = []
  for (let i = 0; i < list.length - 1; i++) {
    const from = list[i]
    const to = list[i + 1]
    const a = toMapLatLng(from)
    const b = toMapLatLng(to)
    if (pointInBox(a, box) || pointInBox(b, box)) {
      hit.push(i)
      continue
    }
    if (a && b) {
      const mid = [(Number(a[0]) + Number(b[0])) / 2, (Number(a[1]) + Number(b[1])) / 2]
      if (pointInBox(mid, box)) hit.push(i)
    }
  }
  selectedSegmentIndexes.value = hit
  batchHint.value = hit.length
    ? `已选中 ${hit.length} 段，可设置出行方式后点「批量贴合路网」`
    : '框内没有路段，请放大后重试'
}

async function applyBatchSnap() {
  const indexes = selectedSegmentIndexes.value.slice()
  if (!indexes.length) {
    batchHint.value = '请先框选路段'
    return
  }
  if (batchPlanning.value) return
  const mode = batchMode.value || 'walk'
  batchPlanning.value = true
  batchHint.value = `正在批量贴合（0/${indexes.length}）…`
  let ok = 0
  let fail = 0
  // 在副本上改，避免每段都触发地图 deep watch 重绘闪烁
  const nextPoints = draftPoints.value.map(p => ({ ...p }))
  try {
    for (let n = 0; n < indexes.length; n++) {
      const i = indexes[n]
      const p = nextPoints[i]
      const next = nextPoints[i + 1]
      if (!p || !next) {
        fail++
        continue
      }
      p.travelMode = mode
      try {
        const res = await previewTrackRoute({
          fromLat: p.latitude,
          fromLng: p.longitude,
          toLat: next.latitude,
          toLng: next.longitude,
          travelMode: mode
        })
        const data = res.data || {}
        const path = data.path
        const realSnap = Array.isArray(path) && path.length > 2
          && !/未配置|webKey|回退直线|规划失败/i.test(String(data.message || ''))
        p.routePath = Array.isArray(path) && path.length >= 2 ? JSON.stringify(path) : ''
        if (realSnap) ok++
        else fail++
        if (/未配置|webKey/i.test(String(data.message || ''))) {
          batchHint.value = String(data.message)
        }
      } catch (e) {
        p.routePath = ''
        fail++
      }
      if (n % 5 === 4 || n === indexes.length - 1) {
        batchHint.value = `正在批量贴合（${n + 1}/${indexes.length}）…`
      }
    }
    draftPoints.value = nextPoints
    const keyFail = fail > 0 && fail === indexes.length
    batchHint.value = `批量完成：成功 ${ok} 段` + (fail ? `，失败/直线 ${fail} 段` : '')
      + (keyFail ? '。若全是直线，请检查后端 album.map.webKey 是否已加载并重启' : '')
      + '。记得点「保存全部」'
  } finally {
    batchPlanning.value = false
  }
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
    const pathPts = Array.isArray(data.path) ? data.path.length : 0
    const keyHint = /未配置|webKey|Key/i.test(String(data.message || ''))
      ? '（请确认后端已加载 album.map.webKey，并重启服务）'
      : ''
    const straightHint = p.routePath && pathPts <= 2
      ? '当前仅为直线，高德未返回沿路折线'
      : ''
    routeHint.value = [
      p.routePath ? '已贴合路网，可再拖动微调' : '未拿到折线，暂用直线',
      dist ? `约 ${dist}` : '',
      straightHint,
      data.message || '',
      keyHint
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
  const next = draftPoints.value[activeIndex.value + 1]
  if (!p) return
  p.travelMode = ''
  p.description = ''
  p.routePath = ''
  // 触及自定义途经点：无折线即断开；纯照片段：保存后显示虚直线
  const disconnect = isWaypointPoint(p) || isWaypointPoint(next)
  routeHint.value = disconnect
    ? '已清空本段连接，保存后不再绘制线路'
    : '已清空本段，保存后显示为默认虚直线'
  draftPoints.value = [...draftPoints.value]
}

async function saveEdit() {
  if (!draftPoints.value.length) {
    cancelEdit()
    return
  }
  applyDescription()

  // 框选后只点了出行方式、未贴合就保存：先自动贴合，避免落库仍是直线虚线
  if (boxSelectMode.value && selectedSegmentIndexes.value.length) {
    const needSnap = selectedSegmentIndexes.value.some((i) => {
      const p = draftPoints.value[i]
      return p && p.travelMode && !String(p.routePath || '').trim()
    })
    if (needSnap) {
      if (!batchMode.value) {
        const first = draftPoints.value[selectedSegmentIndexes.value[0]]
        batchMode.value = first?.travelMode || 'walk'
      }
      await applyBatchSnap()
    }
  }

  saving.value = true
  const payload = draftPoints.value.map(p => ({
    pointId: p.pointId,
    travelMode: p.travelMode ?? '',
    description: p.description ?? '',
    routePath: p.routePath ?? '',
    sequence: p.sequence
  }))
  try {
    await updateTrackPoints(payload)
    proxy?.$modal?.msgSuccess?.('轨迹路段已保存')
    editing.value = false
    activeIndex.value = -1
    // 必须清掉框选高亮，否则保存后仍显示橙色虚线
    exitBoxSelect(false)
    emit('saved')
  } finally {
    saving.value = false
  }
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
  right: 12px;
  bottom: 28px;
  left: auto;
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  align-items: center;
  gap: 4px;
  max-width: min(420px, calc(100% - 70px));
  padding: 6px 8px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.12);
  pointer-events: auto;
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

.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
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

.legend-reset:hover {
  background: rgba(0, 0, 0, 0.1);
  color: #303133;
}

.seg-panel {
  position: absolute;
  z-index: 1200;
  pointer-events: auto;
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

.gpx-panel {
  top: 72px;
}

.mode-opt {
  display: inline-flex;
  align-items: center;
  gap: 6px;
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

.gpx-anchor-row {
  margin-bottom: 4px;
}

.link-opts {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 4px;
}
</style>
