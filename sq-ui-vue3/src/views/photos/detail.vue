<template>
  <div class="album-detail" v-loading="loading" @contextmenu="onPageContextMenu" @click="closeCtxMenu">
    <header class="detail-header">
      <div class="header-left">
        <button type="button" class="back-btn" title="返回" @click="goBack">
          <el-icon :size="22"><ArrowLeft /></el-icon>
        </button>
        <div class="header-info">
          <h1 class="album-title" :title="album.albumName">{{ album.albumName || '相册' }}</h1>
          <div
            class="album-desc"
            :class="{ placeholder: !album.albumDesc }"
            @click="startEditDesc"
          >
            <template v-if="editingDesc">
              <el-input
                ref="descInputRef"
                v-model="descDraft"
                size="small"
                maxlength="200"
                placeholder="添加相册描述"
                @keyup.enter="saveDesc"
                @blur="saveDesc"
              />
            </template>
            <template v-else>
              {{ album.albumDesc || '点击添加相册描述' }}
            </template>
          </div>
        </div>
      </div>
      <div class="header-right">
        <el-button round type="primary" plain @click="triggerUpload">
          <el-icon class="mr4"><Plus /></el-icon>
          添加照片
        </el-button>
        <input
          ref="fileInputRef"
          type="file"
          accept="image/*,video/*"
          multiple
          class="hidden-input"
          @change="onFilesSelected"
        />
        <input
          ref="folderInputRef"
          type="file"
          accept="image/*,video/*"
          multiple
          webkitdirectory
          directory
          class="hidden-input"
          @change="onFilesSelected"
        />
      </div>
    </header>

    <div class="detail-toolbar">
      <div class="toolbar-left">
        <button
          type="button"
          class="select-btn"
          :class="selectBtnClass"
          :title="selectBtnTitle"
          @click="onSelectHeaderClick"
        >
          <el-icon v-if="isAllSelected" :size="16"><Select /></el-icon>
          <el-icon v-else-if="selectedIds.length" :size="16"><Minus /></el-icon>
          <el-icon v-else :size="18"><CircleCheck /></el-icon>
        </button>
        <span v-if="selectedIds.length" class="selected-count">已选 {{ selectedIds.length }} 项</span>
        <span v-else class="item-count">共 {{ total }} 项</span>
      </div>
      <div class="toolbar-right">
        <el-dropdown trigger="click" @command="handleFilterType">
          <button type="button" class="tool-btn">
            <el-icon><Operation /></el-icon>
            <span>{{ typeFilterLabel }}</span>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="all">全部</el-dropdown-item>
              <el-dropdown-item command="1">仅图片</el-dropdown-item>
              <el-dropdown-item command="2">仅视频</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-dropdown trigger="click" @command="handleSizeMode">
          <button type="button" class="tool-btn">
            <el-icon><Menu /></el-icon>
            <span>{{ sizeModeLabel }}</span>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="small">小图模式</el-dropdown-item>
              <el-dropdown-item command="medium">中图模式</el-dropdown-item>
              <el-dropdown-item command="large">大图模式</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <button type="button" class="tool-btn icon-only" title="刷新" @click="reload">
          <el-icon><Refresh /></el-icon>
        </button>
      </div>
    </div>

    <div v-if="!photoList.length && !loading" class="empty-box">
      <el-empty description="相册暂无内容，点击右上角添加照片" />
    </div>

    <div v-else class="photo-grid" :class="['mode-' + sizeMode, { 'is-selecting': isSelecting }]">
      <div
        v-for="item in photoList"
        :key="item.photoId"
        class="photo-cell"
        :class="{ selected: isSelected(item.photoId) }"
        @click="onItemClick(item, $event)"
        @dblclick.prevent="onItemDblClick(item)"
        @contextmenu.prevent.stop="onItemContextMenu(item, $event)"
      >
        <div class="photo-inner">
          <template v-if="item.fileType === 2">
            <img
              v-if="item.thumbUrl"
              :src="thumbSrc(item)"
              :alt="item.fileName"
              loading="lazy"
              @error="onThumbError(item)"
            />
            <video
              v-else
              class="video-thumb"
              :src="originalSrc(item)"
              muted
              preload="metadata"
              playsinline
              @loadedmetadata="onVideoMeta(item, $event)"
            />
            <div class="video-mark">
              <el-icon :size="14"><VideoPlay /></el-icon>
              <span v-if="formatDuration(item.duration)" class="video-duration">{{ formatDuration(item.duration) }}</span>
            </div>
            <!-- 有缩略图但无入库时长时，静默读取原片 metadata -->
            <video
              v-if="item.thumbUrl && !item.duration"
              class="duration-probe"
              :src="originalSrc(item)"
              muted
              preload="metadata"
              @loadedmetadata="onVideoMeta(item, $event)"
            />
          </template>
          <img
            v-else
            :src="thumbSrc(item)"
            :alt="item.fileName"
            loading="lazy"
            @error="onThumbError(item)"
          />
          <div class="check-mark" aria-hidden="true">
            <el-icon :size="14"><Select /></el-icon>
          </div>
        </div>
      </div>
    </div>

    <div v-if="photoList.length" class="detail-footer">
      <el-button v-if="hasMore" text :loading="loadingMore" @click="loadMore">加载更多</el-button>
      <span v-else>没有更多了</span>
    </div>

    <!-- 选中操作栏 -->
    <teleport to="body">
      <transition name="sel-bar">
        <div v-if="selectedIds.length" class="selection-bar" @click.stop>
          <button type="button" class="sel-btn" title="下载" @click="downloadSelected">
            <el-icon :size="20"><Download /></el-icon>
          </button>
          <button type="button" class="sel-btn" title="添加到..." @click="openAddToAlbum">
            <el-icon :size="20"><FolderAdd /></el-icon>
          </button>
          <button type="button" class="sel-btn" title="删除" @click="removeSelected">
            <el-icon :size="20"><Delete /></el-icon>
          </button>
          <button type="button" class="sel-btn" title="取消多选" @click="cancelMultiSelect">
            <el-icon :size="20"><CircleClose /></el-icon>
          </button>
        </div>
      </transition>
    </teleport>

    <!-- 右键菜单：空白 / 图片视频 -->
    <teleport to="body">
      <div
        v-if="ctxMenu.visible"
        class="photos-ctx-menu"
        :class="'type-' + ctxMenu.type"
        :style="{ left: ctxMenu.x + 'px', top: ctxMenu.y + 'px' }"
        @click.stop
        @contextmenu.prevent
      >
        <template v-if="ctxMenu.type === 'blank'">
          <button type="button" class="ctx-item" @click="onCtxUploadFiles">
            <el-icon :size="18"><Picture /></el-icon>
            <span>上传照片/视频</span>
          </button>
          <button type="button" class="ctx-item" @click="onCtxUploadFolder">
            <el-icon :size="18"><FolderOpened /></el-icon>
            <span>上传文件夹</span>
          </button>
          <div class="ctx-divider"></div>
          <button type="button" class="ctx-item" @click="onCtxCreateAlbum">
            <el-icon :size="18"><Files /></el-icon>
            <span>创建相册</span>
          </button>
          <div class="ctx-divider"></div>
          <button type="button" class="ctx-item" @click="onCtxRefresh">
            <el-icon :size="18"><Refresh /></el-icon>
            <span>刷新页面</span>
          </button>
        </template>
        <template v-else>
          <button type="button" class="ctx-item" @click="onItemCtxDownload">下载</button>
          <div class="ctx-divider"></div>
          <button type="button" class="ctx-item" @click="onItemCtxAddTo">添加到...</button>
          <button type="button" class="ctx-item" @click="onItemCtxDetail">查看详细信息</button>
          <button type="button" class="ctx-item" @click="onItemCtxSetCover">设置为相册封面</button>
          <div class="ctx-divider"></div>
          <button type="button" class="ctx-item danger" @click="onItemCtxRemoveFromAlbum">从当前相册移除</button>
          <button type="button" class="ctx-item danger" @click="onItemCtxTrash">放入回收站</button>
        </template>
      </div>
    </teleport>

    <el-dialog
      v-model="createAlbumOpen"
      title="创建相册"
      width="420px"
      append-to-body
      :z-index="4200"
      @closed="resetCreateAlbumForm"
    >
      <el-form ref="createAlbumFormRef" :model="createAlbumForm" :rules="createAlbumRules" label-width="88px">
        <el-form-item label="相册名称" prop="albumName">
          <el-input v-model="createAlbumForm.albumName" maxlength="50" placeholder="请输入相册名称" />
        </el-form-item>
        <el-form-item label="描述" prop="albumDesc">
          <el-input v-model="createAlbumForm.albumDesc" type="textarea" :rows="3" placeholder="可选" />
        </el-form-item>
        <el-form-item label="公开状态" prop="isPublic">
          <el-radio-group v-model="createAlbumForm.isPublic">
            <el-radio :value="1">公开</el-radio>
            <el-radio :value="0">私有</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createAlbumOpen = false">取消</el-button>
        <el-button type="primary" :loading="creatingAlbumPage" @click="submitCreateAlbum">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="addToOpen"
      title="添加到"
      width="520px"
      append-to-body
      align-center
      class="add-to-dialog"
      :z-index="4100"
      destroy-on-close
      @open="onAddToOpen"
      @closed="resetAddToState"
    >
      <div class="add-to-list" v-loading="albumLoading">
        <div v-if="creatingAlbum" class="add-to-row is-editing">
          <div class="add-to-cover is-placeholder">
            <el-icon :size="22"><Files /></el-icon>
          </div>
          <input
            ref="newAlbumInputRef"
            v-model="newAlbumName"
            class="add-to-name-input"
            maxlength="50"
            @keyup.enter="confirmCreateAlbum"
            @click.stop
          />
          <button type="button" class="add-to-icon-btn ok" title="确认" @click.stop="confirmCreateAlbum">
            <el-icon :size="16"><Select /></el-icon>
          </button>
          <button type="button" class="add-to-icon-btn" title="取消" @click.stop="cancelCreateAlbum">
            <el-icon :size="16"><Close /></el-icon>
          </button>
        </div>

        <div
          v-for="item in albumOptions"
          :key="item.albumId"
          class="add-to-row"
          :class="{ selected: String(targetAlbumId) === String(item.albumId) }"
          @click="selectTargetAlbum(item)"
        >
          <div class="add-to-cover" :class="{ 'is-placeholder': !albumCoverSrc(item) }">
            <img v-if="albumCoverSrc(item)" :src="albumCoverSrc(item)" :alt="item.albumName" />
            <el-icon v-else :size="22"><PictureFilled /></el-icon>
          </div>
          <div class="add-to-name" :title="item.albumName">{{ item.albumName }}</div>
          <div class="add-to-count">{{ item.photoCount ?? 0 }}</div>
        </div>

        <div v-if="!albumLoading && !albumOptions.length && !creatingAlbum" class="add-to-empty">
          暂无其他相册，可先新建
        </div>
      </div>

      <template #footer>
        <div class="add-to-footer">
          <button type="button" class="add-to-create-link" @click="startCreateAlbum">新建相册</button>
          <div class="add-to-footer-actions">
            <el-button @click="addToOpen = false">取消</el-button>
            <el-button
              type="primary"
              :disabled="!canSubmitAddTo"
              :loading="addingTo"
              @click="submitAddToAlbum"
            >
              添加
            </el-button>
          </div>
        </div>
      </template>
    </el-dialog>

    <!-- 图片/视频预览（白底；点击空白不关闭，仅返回按钮 / Esc） -->
    <teleport to="body">
      <div v-if="mediaVisible" class="media-viewer" :class="{ 'detail-open': detailOpen }">
        <!-- 媒体展示区：抽屉打开时收缩，图片与底部工具栏随之适配 -->
        <div class="media-stage">
          <button
            v-if="mediaIndex > 0"
            type="button"
            class="media-nav prev"
            title="上一张"
            @click.stop="shiftMedia(-1)"
          >
            <el-icon :size="22"><ArrowLeft /></el-icon>
          </button>
          <button
            v-if="mediaIndex < photoList.length - 1"
            type="button"
            class="media-nav next"
            title="下一张"
            @click.stop="shiftMedia(1)"
          >
            <el-icon :size="22"><ArrowRight /></el-icon>
          </button>

          <div
            v-if="currentMedia && currentMedia.fileType !== 2"
            class="media-canvas"
            :class="{ dragging: imageDragging }"
            @wheel.prevent="onImageWheel"
            @pointerdown="onImagePointerDown"
            @dblclick.prevent="onImageDblClick"
            @dragstart.prevent
          >
            <img
              ref="mediaImageRef"
              class="media-image"
              :class="{ 'is-original': imageMode === 'original' }"
              :src="originalSrc(currentMedia)"
              :alt="currentMedia.fileName"
              draggable="false"
              decoding="async"
              @load="paintImageTransform(false)"
              @click.stop
              @dragstart.prevent
            />
          </div>
          <div v-else-if="currentMedia" class="media-video-wrap">
            <video
              :key="currentMedia.photoId"
              class="media-video"
              :src="originalSrc(currentMedia)"
              controls
              autoplay
              playsinline
              @click.stop
            />
          </div>

          <div
            v-if="currentMedia && currentMedia.fileType !== 2"
            class="media-toolbar"
            @click.stop
          >
            <button type="button" class="media-tool-btn" title="缩小" @click="zoomImage(-1, true)">
              <el-icon :size="18"><ZoomOut /></el-icon>
            </button>
            <span class="media-zoom-label">{{ imageZoomPercent }}%</span>
            <button type="button" class="media-tool-btn" title="放大" @click="zoomImage(1, true)">
              <el-icon :size="18"><ZoomIn /></el-icon>
            </button>
            <button type="button" class="media-tool-btn" title="向右旋转" @click="rotateImage">
              <el-icon :size="18"><RefreshRight /></el-icon>
            </button>
            <button type="button" class="media-tool-btn" title="切换原始尺寸" @click="toggleImageMode">
              <el-icon :size="18">
                <FullScreen v-if="imageMode === 'contain'" />
                <ScaleToOriginal v-else />
              </el-icon>
            </button>
          </div>
        </div>
      </div>
    </teleport>

    <!-- 预览顶栏按钮独立层级，打开抽屉时位置不变且可点 -->
    <teleport to="body">
      <template v-if="mediaVisible">
        <button type="button" class="media-close" title="返回" @click="closeMedia">
          <el-icon :size="22"><ArrowLeft /></el-icon>
        </button>
        <div class="media-actions">
          <button type="button" class="media-action-btn" title="下载" @click.stop="onMediaDownload">
            <el-icon :size="20"><Download /></el-icon>
          </button>
          <button type="button" class="media-action-btn" title="添加到..." @click.stop="onMediaAddTo">
            <el-icon :size="20"><FolderAdd /></el-icon>
          </button>
          <button type="button" class="media-action-btn" title="删除" @click.stop="onMediaDelete">
            <el-icon :size="20"><Delete /></el-icon>
          </button>
          <button
            type="button"
            class="media-action-btn"
            :class="{ active: detailOpen }"
            title="查看详细信息"
            @click.stop="onMediaDetail"
          >
            <el-icon :size="20"><InfoFilled /></el-icon>
          </button>
        </div>
      </template>
    </teleport>

    <!-- 详细信息右侧面板：避开顶部操作栏，仅在下方区域弹出 -->
    <teleport to="body">
      <transition name="detail-panel">
        <aside
          v-if="detailOpen && detailPhoto"
          class="photo-detail-panel"
          @click.stop
        >
          <div class="photo-detail-album">
            <h3 class="detail-section-title">所属相册</h3>
            <div class="detail-album-row">
              <button type="button" class="album-tag" @click="goCurrentAlbumFromDetail">
                {{ album.albumName || '相册' }}
              </button>
              <button type="button" class="album-tag ghost" @click="onDetailAddTo">添加到...</button>
            </div>
          </div>

          <div class="photo-detail-info">
            <h3 class="detail-section-title">详细信息</h3>
            <div class="photo-detail-list">
              <div class="photo-detail-row">
                <span class="label">名称</span>
                <span class="value">{{ detailPhoto.fileName || '-' }}</span>
              </div>
              <div class="photo-detail-row">
                <span class="label">大小</span>
                <span class="value">{{ formatFileSize(detailPhoto.fileSize) }}</span>
              </div>
              <div class="photo-detail-row">
                <span class="label">类型</span>
                <span class="value">{{ detailPhoto.fileType === 2 ? '视频' : '图片' }}</span>
              </div>
              <div v-if="detailPhoto.fileType === 2" class="photo-detail-row">
                <span class="label">时长</span>
                <span class="value">{{ formatDuration(detailPhoto.duration) || '-' }}</span>
              </div>
              <div class="photo-detail-row">
                <span class="label">文件位置</span>
                <span class="value">相册</span>
              </div>
              <div class="photo-detail-row">
                <span class="label">创建时间</span>
                <span class="value">{{ formatDetailTime(detailPhoto.createTime) }}</span>
              </div>
              <div class="photo-detail-row">
                <span class="label">修改时间</span>
                <span class="value">{{ formatDetailTime(detailPhoto.updateTime || detailPhoto.shootTime || detailPhoto.createTime) }}</span>
              </div>
              <div class="photo-detail-row">
                <span class="label">拍摄时间</span>
                <span class="value">{{ formatDetailTime(detailPhoto.shootTime) }}</span>
              </div>
              <div class="photo-detail-row">
                <span class="label">相机</span>
                <span class="value">{{ detailPhoto.cameraModel || '-' }}</span>
              </div>
              <div class="photo-detail-row">
                <span class="label">镜头</span>
                <span class="value">{{ detailPhoto.lensInfo || '-' }}</span>
              </div>
              <div class="photo-detail-row">
                <span class="label">光圈</span>
                <span class="value">{{ detailPhoto.aperture || '-' }}</span>
              </div>
              <div class="photo-detail-row">
                <span class="label">快门</span>
                <span class="value">{{ detailPhoto.shutterSpeed || '-' }}</span>
              </div>
              <div class="photo-detail-row">
                <span class="label">ISO</span>
                <span class="value">{{ detailPhoto.iso ?? '-' }}</span>
              </div>
              <div class="photo-detail-row">
                <span class="label">焦距</span>
                <span class="value">{{ detailPhoto.focalLength || '-' }}</span>
              </div>
              <div class="photo-detail-row">
                <span class="label">位置</span>
                <span class="value">{{ detailLocationText }}</span>
              </div>
            </div>
          </div>
        </aside>
      </transition>
    </teleport>
  </div>
</template>

<script setup name="PhotosAlbumDetail">
import { ElMessageBox } from 'element-plus'
import { isExternal } from '@/utils/validate'
import { getToken } from '@/utils/auth'
import { saveAs } from 'file-saver'
import axios from 'axios'
import { getAlbum, updateAlbum, listAlbum, addAlbum } from '@/api/photos/album'
import { listPhoto, uploadPhoto, delPhoto, updatePhoto } from '@/api/photos/photo'

const { proxy } = getCurrentInstance()
const route = useRoute()

const loading = ref(false)
const loadingMore = ref(false)
const album = ref({})
const photoList = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(60)
const typeFilter = ref('all')
const sizeMode = ref('small')
/** 顶部按钮开启的持续多选模式（等同常按 Ctrl） */
const multiMode = ref(false)
const selectedIds = ref([])
/** Shift 范围选择的锚点 */
const lastAnchorId = ref(null)
const editingDesc = ref(false)
const descDraft = ref('')
const descInputRef = ref()
const fileInputRef = ref()
const folderInputRef = ref()
const mediaVisible = ref(false)
const ctxMenu = reactive({ visible: false, x: 0, y: 0, type: 'blank', photoId: null })
const detailOpen = ref(false)
const detailPhoto = ref(null)
const createAlbumOpen = ref(false)
const creatingAlbumPage = ref(false)
const createAlbumFormRef = ref()
const createAlbumForm = reactive({
  albumName: '',
  albumDesc: '',
  isPublic: 1
})
const createAlbumRules = {
  albumName: [{ required: true, message: '请输入相册名称', trigger: 'blur' }]
}
const mediaIndex = ref(0)
const uploading = ref(false)
const clickTimer = ref(null)
const brokenThumbs = ref(new Set())
const addToOpen = ref(false)
const albumOptions = ref([])
const targetAlbumId = ref(null)
const addingTo = ref(false)
const downloading = ref(false)
const albumLoading = ref(false)
const creatingAlbum = ref(false)
const creatingAlbumBusy = ref(false)
const newAlbumName = ref('未命名')
const newAlbumInputRef = ref()

const canSubmitAddTo = computed(() => !!targetAlbumId.value && !creatingAlbum.value && !addingTo.value)

const ctxTargetPhoto = computed(() =>
  photoList.value.find(p => p.photoId === ctxMenu.photoId) || null
)

const detailLocationText = computed(() => {
  const p = detailPhoto.value
  if (!p) return '-'
  const parts = [p.province, p.city, p.district, p.address].filter(Boolean)
  if (parts.length) return parts.join(' ')
  if (p.latitude != null && p.longitude != null) return `${p.latitude}, ${p.longitude}`
  return '-'
})
const imageMode = ref('contain') // contain | original
const mediaImageRef = ref()
const imageZoomPercent = ref(100)
const imageDragging = ref(false)
/** 非响应式，交互时直接改 DOM，避免 Vue 每帧重渲染导致高倍缩放卡顿 */
const imageTransform = {
  scale: 1,
  deg: 0,
  offsetX: 0,
  offsetY: 0
}
let imageDragPointerId = null
let imageDragCleanup = null
let imagePaintRaf = 0
let wheelZoomRaf = 0
let pendingWheelDelta = 0

const IMAGE_ZOOM_RATE = 1.2
const IMAGE_MIN_SCALE = 0.2
const IMAGE_MAX_SCALE = 5
const IMAGE_DRAG_THRESHOLD = 3

const albumId = computed(() => route.params.albumId)

const typeFilterLabel = computed(() => {
  const map = { all: '全部', 1: '仅图片', 2: '仅视频' }
  return map[typeFilter.value] || '全部'
})

const sizeModeLabel = computed(() => {
  const map = { small: '小图模式', medium: '中图模式', large: '大图模式' }
  return map[sizeMode.value] || '小图模式'
})

const hasMore = computed(() => photoList.value.length < total.value)

const currentMedia = computed(() => photoList.value[mediaIndex.value] || null)

const isSelecting = computed(() => selectedIds.value.length > 0 || multiMode.value)

const isAllSelected = computed(() =>
  photoList.value.length > 0 && selectedIds.value.length >= photoList.value.length
)

const selectBtnClass = computed(() => ({
  active: selectedIds.value.length > 0,
  partial: selectedIds.value.length > 0 && !isAllSelected.value
}))

const selectBtnTitle = computed(() => {
  if (selectedIds.value.length) return '取消全选'
  return '全选'
})

function resolveUrl(url) {
  if (!url) return ''
  if (isExternal(url)) return url
  return import.meta.env.VITE_APP_BASE_API + url
}

function thumbSrc(item) {
  if (!item) return ''
  if (brokenThumbs.value.has(item.photoId)) {
    return originalSrc(item)
  }
  return resolveUrl('/album/photo/media/' + item.photoId)
}

function originalSrc(item) {
  if (!item) return ''
  return resolveUrl('/album/photo/media/' + item.photoId + '?original=true')
}

/** 秒 -> 0:16 / 1:02:03 */
function formatDuration(seconds) {
  if (seconds == null || seconds === '' || Number.isNaN(Number(seconds))) return ''
  const total = Math.max(0, Math.round(Number(seconds)))
  const h = Math.floor(total / 3600)
  const m = Math.floor((total % 3600) / 60)
  const s = total % 60
  const ss = String(s).padStart(2, '0')
  if (h > 0) return `${h}:${String(m).padStart(2, '0')}:${ss}`
  return `${m}:${ss}`
}

function onVideoMeta(item, event) {
  const el = event?.target
  if (!el || !item) return
  const d = el.duration
  if (!d || !Number.isFinite(d)) return
  item.duration = Math.round(d)
}

function onThumbError(item) {
  if (!item || item.fileType === 2) {
    // 视频缩略图失败时改用 video 标签
    if (item) item.thumbUrl = ''
    return
  }
  const next = new Set(brokenThumbs.value)
  next.add(item.photoId)
  brokenThumbs.value = next
}

function goBack() {
  // 返回列表时关闭当前详情页签，原地切回相册列表
  proxy.$tab.closeOpenPage({ path: '/photos/index' })
}

function handleFilterType(cmd) {
  typeFilter.value = cmd
  reload()
}

function handleSizeMode(cmd) {
  sizeMode.value = cmd
}

function isSelected(id) {
  return selectedIds.value.includes(id)
}

function clearSelection() {
  selectedIds.value = []
  lastAnchorId.value = null
}

function selectAllLoaded() {
  const ids = photoList.value.map(p => p.photoId)
  selectedIds.value = ids.slice()
  lastAnchorId.value = ids.length ? ids[0] : null
  multiMode.value = true
}

async function ensureAllLoaded() {
  while (photoList.value.length < total.value) {
    pageNum.value += 1
    await loadPhotos(false)
  }
}

async function onSelectHeaderClick() {
  if (selectedIds.value.length) {
    clearSelection()
    multiMode.value = false
    return
  }
  if (!total.value) {
    proxy.$modal.msg('暂无可选项')
    return
  }
  if (photoList.value.length < total.value) {
    loading.value = true
    try {
      await ensureAllLoaded()
    } finally {
      loading.value = false
    }
  }
  selectAllLoaded()
}

function startEditDesc() {
  if (editingDesc.value) return
  descDraft.value = album.value.albumDesc || ''
  editingDesc.value = true
  nextTick(() => {
    descInputRef.value?.focus?.()
  })
}

function saveDesc() {
  if (!editingDesc.value) return
  editingDesc.value = false
  const next = (descDraft.value || '').trim()
  if (next === (album.value.albumDesc || '')) return
  updateAlbum({
    albumId: album.value.albumId,
    albumName: album.value.albumName,
    albumDesc: next,
    isPublic: album.value.isPublic,
    coverUrl: album.value.coverUrl
  }).then(() => {
    album.value.albumDesc = next
    proxy.$modal.msgSuccess('描述已更新')
  })
}

function isMediaFile(file) {
  const type = file?.type || ''
  if (type.startsWith('image/') || type.startsWith('video/')) return true
  const name = String(file?.name || '').toLowerCase()
  return /\.(jpe?g|png|gif|webp|bmp|heic|heif|mp4|mov|avi|mkv|webm|m4v)$/i.test(name)
}

function triggerUpload() {
  closeCtxMenu()
  fileInputRef.value?.click?.()
}

function triggerFolderUpload() {
  closeCtxMenu()
  folderInputRef.value?.click?.()
}

function onFilesSelected(e) {
  const raw = Array.from(e.target.files || [])
  e.target.value = ''
  const files = raw.filter(isMediaFile)
  if (!files.length) {
    if (raw.length) proxy.$modal.msgWarning('所选内容中没有可上传的照片或视频')
    return
  }
  uploading.value = true
  const tasks = files.map(file => {
    const form = new FormData()
    form.append('file', file)
    form.append('albumId', albumId.value)
    return uploadPhoto(form)
  })
  Promise.allSettled(tasks)
    .then(results => {
      const ok = results.filter(r => r.status === 'fulfilled').length
      const fail = results.length - ok
      if (ok) proxy.$modal.msgSuccess(`成功添加 ${ok} 个文件${fail ? `，失败 ${fail}` : ''}`)
      else proxy.$modal.msgError('添加失败')
      if (ok) reload()
    })
    .finally(() => {
      uploading.value = false
    })
}

function closeCtxMenu() {
  ctxMenu.visible = false
  ctxMenu.type = 'blank'
  ctxMenu.photoId = null
}

function positionCtxMenu(e, menuW = 220, menuH = 220) {
  const pad = 8
  let x = e.clientX
  let y = e.clientY
  if (x + menuW + pad > window.innerWidth) x = window.innerWidth - menuW - pad
  if (y + menuH + pad > window.innerHeight) y = window.innerHeight - menuH - pad
  ctxMenu.x = Math.max(pad, x)
  ctxMenu.y = Math.max(pad, y)
  ctxMenu.visible = true
}

function onPageContextMenu(e) {
  e.preventDefault()
  const t = e.target
  // 仅空白区域弹出；点在缩略图/控件/弹层上不弹出
  if (
    t.closest?.(
      '.photo-cell, .detail-header, .detail-toolbar, .selection-bar, .photos-ctx-menu, .el-dialog, .el-overlay, .media-viewer, button, a, input, textarea, .el-button, .el-dropdown'
    )
  ) {
    closeCtxMenu()
    return
  }
  ctxMenu.type = 'blank'
  ctxMenu.photoId = null
  positionCtxMenu(e, 220, 200)
}

function onItemContextMenu(item, e) {
  // 未选中则单选当前项；已在多选中则保持选中集合
  if (!isSelected(item.photoId)) {
    selectedIds.value = [item.photoId]
    lastAnchorId.value = item.photoId
  }
  ctxMenu.type = 'item'
  ctxMenu.photoId = item.photoId
  positionCtxMenu(e, 200, 280)
}

function formatFileSize(size) {
  const n = Number(size)
  if (!n || Number.isNaN(n)) return '-'
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  if (n < 1024 * 1024 * 1024) return `${(n / 1024 / 1024).toFixed(1)} MB`
  return `${(n / 1024 / 1024 / 1024).toFixed(2)} GB`
}

function onItemCtxDownload() {
  closeCtxMenu()
  downloadSelected()
}

function onItemCtxAddTo() {
  closeCtxMenu()
  openAddToAlbum()
}

function onItemCtxDetail() {
  const item = ctxTargetPhoto.value
  closeCtxMenu()
  if (!item) return
  openPhotoDetail(item)
}

function formatDetailTime(time) {
  if (!time) return '-'
  const d = new Date(typeof time === 'string' ? time.replace(/-/g, '/') : time)
  if (Number.isNaN(d.getTime())) return String(time)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}/${pad(d.getMonth() + 1)}/${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function openPhotoDetail(item) {
  if (!item) return
  detailPhoto.value = item
  detailOpen.value = true
}

function ensureMediaSelected() {
  const item = currentMedia.value
  if (!item) return null
  selectedIds.value = [item.photoId]
  lastAnchorId.value = item.photoId
  return item
}

function onMediaDownload() {
  if (!ensureMediaSelected()) return
  downloadSelected()
}

function confirmAboveMedia(content) {
  // 预览层 z-index=3000，确认框需更高，否则会点了没反应
  return ElMessageBox.confirm(content, '系统提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
    customClass: 'above-media-msgbox',
    appendTo: document.body
  })
}

function removePhotosFromViewer(ids) {
  const idSet = new Set(ids.map(String))
  const oldIdx = mediaIndex.value
  photoList.value = photoList.value.filter(p => !idSet.has(String(p.photoId)))
  total.value = Math.max(0, (total.value || 0) - ids.length)
  selectedIds.value = selectedIds.value.filter(x => !idSet.has(String(x)))
  detailOpen.value = false
  if (!photoList.value.length) {
    closeMedia()
    return
  }
  mediaIndex.value = Math.min(oldIdx, photoList.value.length - 1)
  resetImageTransform()
}

function onMediaAddTo() {
  const item = ensureMediaSelected()
  if (!item) {
    proxy.$modal.msgWarning('当前没有可添加的内容')
    return
  }
  openAddToAlbum()
}

function onMediaDetail() {
  if (detailOpen.value && detailPhoto.value?.photoId === currentMedia.value?.photoId) {
    detailOpen.value = false
    return
  }
  openPhotoDetail(currentMedia.value)
}

function onDetailAddTo() {
  const item = detailPhoto.value || currentMedia.value
  if (!item) return
  selectedIds.value = [item.photoId]
  lastAnchorId.value = item.photoId
  openAddToAlbum()
}

function goCurrentAlbumFromDetail() {
  detailOpen.value = false
  if (mediaVisible.value) closeMedia()
}

function onMediaDelete() {
  const item = currentMedia.value
  if (!item) {
    proxy.$modal.msgWarning('当前没有可删除的内容')
    return
  }
  const id = item.photoId
  confirmAboveMedia('确认删除当前项吗？')
    .then(() => delPhoto(String(id)))
    .then(() => {
      proxy.$modal.msgSuccess('删除成功')
      removePhotosFromViewer([id])
    })
    .catch(() => {})
}

function onItemCtxSetCover() {
  const item = ctxTargetPhoto.value
  closeCtxMenu()
  if (!item) return
  // 封面统一走媒体接口，避免 /album/files/** 无静态映射导致列表不显示
  const coverUrl = `/album/photo/media/${item.photoId}`
  updateAlbum({
    albumId: album.value.albumId,
    albumName: album.value.albumName,
    albumDesc: album.value.albumDesc,
    isPublic: album.value.isPublic,
    coverUrl
  }).then(() => {
    album.value.coverUrl = coverUrl
    proxy.$modal.msgSuccess('已设置为相册封面')
  })
}

function deletePhotosByIds(ids, confirmText, successText) {
  if (!ids.length) return
  proxy.$modal.confirm(confirmText)
    .then(() => delPhoto(ids.join(',')))
    .then(() => {
      proxy.$modal.msgSuccess(successText)
      cancelMultiSelect()
      reload()
    })
    .catch(() => {})
}

function onItemCtxRemoveFromAlbum() {
  const ids = selectedIds.value.slice()
  closeCtxMenu()
  deletePhotosByIds(
    ids,
    `确认从当前相册移除选中的 ${ids.length} 项吗？`,
    '已从当前相册移除'
  )
}

function onItemCtxTrash() {
  const ids = selectedIds.value.slice()
  closeCtxMenu()
  deletePhotosByIds(
    ids,
    `确认将选中的 ${ids.length} 项放入回收站吗？`,
    '已放入回收站'
  )
}

function onCtxUploadFiles() {
  triggerUpload()
}

function onCtxUploadFolder() {
  triggerFolderUpload()
}

function onCtxCreateAlbum() {
  closeCtxMenu()
  createAlbumForm.albumName = ''
  createAlbumForm.albumDesc = ''
  createAlbumForm.isPublic = 1
  createAlbumOpen.value = true
}

function onCtxRefresh() {
  closeCtxMenu()
  init()
}

function resetCreateAlbumForm() {
  createAlbumForm.albumName = ''
  createAlbumForm.albumDesc = ''
  createAlbumForm.isPublic = 1
  createAlbumFormRef.value?.resetFields?.()
}

function submitCreateAlbum() {
  createAlbumFormRef.value?.validate?.(valid => {
    if (!valid) return
    creatingAlbumPage.value = true
    addAlbum({
      albumName: createAlbumForm.albumName.trim(),
      albumDesc: createAlbumForm.albumDesc,
      isPublic: createAlbumForm.isPublic,
      photoCount: 0,
      sortOrder: 0
    })
      .then(res => {
        const created = res.data || {}
        proxy.$modal.msgSuccess('创建成功')
        createAlbumOpen.value = false
        if (created.albumId != null) {
          proxy.$tab.closeOpenPage({ path: '/photos/detail/' + created.albumId })
        } else {
          proxy.$tab.closeOpenPage({ path: '/photos/index' })
        }
      })
      .finally(() => {
        creatingAlbumPage.value = false
      })
  })
}

function applySelection(item, event = {}) {
  const id = item.photoId
  const ids = photoList.value.map(p => p.photoId)
  const idx = ids.indexOf(id)
  if (idx < 0) return

  const ctrl = !!(event.ctrlKey || event.metaKey || multiMode.value)
  const shift = !!event.shiftKey

  if (shift && lastAnchorId.value != null) {
    const from = ids.indexOf(lastAnchorId.value)
    if (from >= 0) {
      const start = Math.min(from, idx)
      const end = Math.max(from, idx)
      const range = ids.slice(start, end + 1)
      if (ctrl) {
        selectedIds.value = [...new Set(selectedIds.value.concat(range))]
      } else {
        selectedIds.value = range.slice()
      }
      return
    }
  }

  if (ctrl) {
    const i = selectedIds.value.indexOf(id)
    if (i >= 0) selectedIds.value.splice(i, 1)
    else selectedIds.value.push(id)
    lastAnchorId.value = id
    return
  }

  // 默认单选：再次点击已选项则取消
  if (selectedIds.value.length === 1 && selectedIds.value[0] === id) {
    clearSelection()
  } else {
    selectedIds.value = [id]
    lastAnchorId.value = id
  }
}

function onItemClick(item, event) {
  const immediate = event.ctrlKey || event.metaKey || event.shiftKey || multiMode.value
  if (clickTimer.value) clearTimeout(clickTimer.value)
  if (immediate) {
    applySelection(item, event)
    return
  }
  clickTimer.value = setTimeout(() => {
    applySelection(item, event)
    clickTimer.value = null
  }, 200)
}

function onItemDblClick(item) {
  if (clickTimer.value) {
    clearTimeout(clickTimer.value)
    clickTimer.value = null
  }
  // 双击查看时取消多选/全选状态
  clearSelection()
  multiMode.value = false
  openViewer(item)
}

function buildImageTransformCss() {
  const { scale, deg, offsetX, offsetY } = imageTransform
  let translateX = offsetX / scale
  let translateY = offsetY / scale
  switch (((deg % 360) + 360) % 360) {
    case 90:
      [translateX, translateY] = [translateY, -translateX]
      break
    case 180:
      [translateX, translateY] = [-translateX, -translateY]
      break
    case 270:
      [translateX, translateY] = [-translateY, translateX]
      break
  }
  return `translate3d(0,0,0) scale(${scale}) rotate(${deg}deg) translate(${translateX}px, ${translateY}px)`
}

function paintImageTransform(animate = false) {
  const el = mediaImageRef.value
  if (!el) return
  el.style.transition = animate ? 'transform .2s ease-out' : 'none'
  el.style.transform = buildImageTransformCss()
  const nextPercent = Math.round(imageTransform.scale * 100)
  if (imageZoomPercent.value !== nextPercent) {
    imageZoomPercent.value = nextPercent
  }
}

function schedulePaintImage(animate = false) {
  if (imagePaintRaf) cancelAnimationFrame(imagePaintRaf)
  imagePaintRaf = requestAnimationFrame(() => {
    imagePaintRaf = 0
    paintImageTransform(animate)
  })
}

function resetImageTransform() {
  imageMode.value = 'contain'
  imageTransform.scale = 1
  imageTransform.deg = 0
  imageTransform.offsetX = 0
  imageTransform.offsetY = 0
  imageZoomPercent.value = 100
  pendingWheelDelta = 0
  if (wheelZoomRaf) {
    cancelAnimationFrame(wheelZoomRaf)
    wheelZoomRaf = 0
  }
  nextTick(() => paintImageTransform(false))
}

function openViewer(item) {
  const idx = photoList.value.findIndex(p => p.photoId === item.photoId)
  mediaIndex.value = idx >= 0 ? idx : 0
  resetImageTransform()
  mediaVisible.value = true
}

function closeMedia() {
  stopImageDrag()
  detailOpen.value = false
  mediaVisible.value = false
  resetImageTransform()
}

function shiftMedia(step) {
  const next = mediaIndex.value + step
  if (next < 0 || next >= photoList.value.length) return
  stopImageDrag()
  mediaIndex.value = next
  resetImageTransform()
}

function zoomImage(delta, animate = false) {
  const next = delta > 0
    ? imageTransform.scale * IMAGE_ZOOM_RATE
    : imageTransform.scale / IMAGE_ZOOM_RATE
  imageTransform.scale = Math.min(IMAGE_MAX_SCALE, Math.max(IMAGE_MIN_SCALE, next))
  if (animate) paintImageTransform(true)
  else schedulePaintImage(false)
}

function rotateImage() {
  imageTransform.deg += 90
  paintImageTransform(true)
}

function toggleImageMode() {
  if (imageMode.value === 'contain') {
    imageMode.value = 'original'
    imageTransform.scale = 1
  } else {
    imageMode.value = 'contain'
    imageTransform.scale = 1
    imageTransform.offsetX = 0
    imageTransform.offsetY = 0
  }
  nextTick(() => paintImageTransform(false))
}

function onImageWheel(e) {
  pendingWheelDelta += e.deltaY < 0 ? 1 : -1
  if (wheelZoomRaf) return
  wheelZoomRaf = requestAnimationFrame(() => {
    wheelZoomRaf = 0
    const delta = pendingWheelDelta
    pendingWheelDelta = 0
    if (!delta) return
    zoomImage(delta > 0 ? 1 : -1, false)
  })
}

function stopImageDrag() {
  if (imageDragCleanup) {
    imageDragCleanup()
    imageDragCleanup = null
  }
  imageDragPointerId = null
  imageDragging.value = false
}

function onImagePointerDown(e) {
  if (e.button !== 0 && e.pointerType === 'mouse') return
  // 双击的第二次按下不启动拖拽，避免粘连
  if (e.detail > 1) return
  e.preventDefault()
  stopImageDrag()

  const startX = e.clientX
  const startY = e.clientY
  const originOffsetX = imageTransform.offsetX
  const originOffsetY = imageTransform.offsetY
  const pointerId = e.pointerId
  imageDragPointerId = pointerId
  let active = false
  let moveRaf = 0
  let latestX = startX
  let latestY = startY

  const target = e.currentTarget
  try {
    target?.setPointerCapture?.(pointerId)
  } catch (_) {}

  const onMove = (ev) => {
    if (imageDragPointerId !== pointerId) return
    latestX = ev.clientX
    latestY = ev.clientY
    if (moveRaf) return
    moveRaf = requestAnimationFrame(() => {
      moveRaf = 0
      const dx = latestX - startX
      const dy = latestY - startY
      if (!active) {
        if (dx * dx + dy * dy < IMAGE_DRAG_THRESHOLD * IMAGE_DRAG_THRESHOLD) return
        active = true
        imageDragging.value = true
      }
      imageTransform.offsetX = originOffsetX + dx
      imageTransform.offsetY = originOffsetY + dy
      paintImageTransform(false)
    })
  }

  const onEnd = (ev) => {
    if (ev && ev.pointerId != null && ev.pointerId !== pointerId) return
    if (moveRaf) {
      cancelAnimationFrame(moveRaf)
      moveRaf = 0
    }
    try {
      target?.releasePointerCapture?.(pointerId)
    } catch (_) {}
    stopImageDrag()
  }

  window.addEventListener('pointermove', onMove)
  window.addEventListener('pointerup', onEnd)
  window.addEventListener('pointercancel', onEnd)
  window.addEventListener('blur', onEnd)

  imageDragCleanup = () => {
    if (moveRaf) {
      cancelAnimationFrame(moveRaf)
      moveRaf = 0
    }
    window.removeEventListener('pointermove', onMove)
    window.removeEventListener('pointerup', onEnd)
    window.removeEventListener('pointercancel', onEnd)
    window.removeEventListener('blur', onEnd)
  }
}

function onImageDblClick(e) {
  e.preventDefault()
  stopImageDrag()
  // 清除双击产生的文本选区，避免白底竖线残影
  window.getSelection?.()?.removeAllRanges?.()
  toggleImageMode()
}

function cancelMultiSelect() {
  clearSelection()
  multiMode.value = false
}

function selectedPhotos() {
  const idSet = new Set(selectedIds.value)
  return photoList.value.filter(p => idSet.has(p.photoId))
}

async function downloadSelected() {
  const items = selectedPhotos()
  if (!items.length || downloading.value) return
  downloading.value = true
  try {
    for (const item of items) {
      const url = originalSrc(item)
      const res = await axios({
        method: 'get',
        url,
        responseType: 'blob',
        headers: { Authorization: 'Bearer ' + getToken() }
      })
      const name = item.fileName || `photo_${item.photoId}`
      saveAs(res.data, name)
    }
    if (items.length > 1) {
      proxy.$modal.msgSuccess(`已开始下载 ${items.length} 项`)
    }
  } catch (e) {
    console.error(e)
    proxy.$modal.msgError('下载失败')
  } finally {
    downloading.value = false
  }
}

function openAddToAlbum() {
  if (!selectedIds.value.length) return
  targetAlbumId.value = null
  addToOpen.value = true
}

function resetAddToState() {
  targetAlbumId.value = null
  creatingAlbum.value = false
  creatingAlbumBusy.value = false
  newAlbumName.value = '未命名'
  albumOptions.value = []
}

function albumCoverSrc(item) {
  if (!item) return ''
  if (item.coverUrl) return resolveUrl(item.coverUrl)
  if (item.photoCount > 0 && item.coverPhotoId) {
    return resolveUrl('/album/photo/media/' + item.coverPhotoId)
  }
  return ''
}

function onAddToOpen() {
  loadAlbumOptions()
}

function loadAlbumOptions() {
  albumLoading.value = true
  return listAlbum({ pageNum: 1, pageSize: 500 })
    .then(res => {
      const rows = res.rows || res.data || []
      albumOptions.value = rows.filter(a => String(a.albumId) !== String(albumId.value))
    })
    .finally(() => {
      albumLoading.value = false
    })
}

function selectTargetAlbum(item) {
  if (creatingAlbum.value) cancelCreateAlbum()
  targetAlbumId.value = item.albumId
}

function startCreateAlbum() {
  if (creatingAlbum.value) {
    nextTick(() => newAlbumInputRef.value?.focus?.())
    return
  }
  creatingAlbum.value = true
  newAlbumName.value = '未命名'
  targetAlbumId.value = null
  nextTick(() => {
    const input = newAlbumInputRef.value
    if (!input) return
    input.focus?.()
    input.select?.()
  })
}

function cancelCreateAlbum() {
  creatingAlbum.value = false
  creatingAlbumBusy.value = false
  newAlbumName.value = '未命名'
}

async function confirmCreateAlbum() {
  if (creatingAlbumBusy.value) return
  const name = (newAlbumName.value || '').trim() || '未命名'
  creatingAlbumBusy.value = true
  try {
    const res = await addAlbum({
      albumName: name,
      albumDesc: '',
      isPublic: 1,
      photoCount: 0,
      sortOrder: 0
    })
    const created = res.data || {}
    await loadAlbumOptions()
    if (created.albumId != null) {
      if (!albumOptions.value.some(a => String(a.albumId) === String(created.albumId))) {
        albumOptions.value.unshift({
          albumId: created.albumId,
          albumName: created.albumName || name,
          photoCount: created.photoCount ?? 0,
          coverUrl: created.coverUrl
        })
      }
      targetAlbumId.value = created.albumId
    }
    creatingAlbum.value = false
    proxy.$modal.msgSuccess('相册已创建')
  } catch (e) {
    console.error(e)
    proxy.$modal.msgError('创建相册失败')
  } finally {
    creatingAlbumBusy.value = false
  }
}

async function submitAddToAlbum() {
  if (!canSubmitAddTo.value) return
  const ids = selectedIds.value.slice()
  if (!ids.length) return
  const stayInViewer = mediaVisible.value
  addingTo.value = true
  try {
    for (const photoId of ids) {
      const item = photoList.value.find(p => String(p.photoId) === String(photoId))
      await updatePhoto({
        photoId,
        albumId: targetAlbumId.value,
        fileName: item?.fileName
      })
    }
    proxy.$modal.msgSuccess(`已添加 ${ids.length} 项`)
    addToOpen.value = false
    cancelMultiSelect()
    if (stayInViewer) {
      // 已移到其他相册，从当前预览列表移除
      removePhotosFromViewer(ids)
    } else {
      reload()
    }
  } catch (e) {
    console.error(e)
    proxy.$modal.msgError('添加失败')
  } finally {
    addingTo.value = false
  }
}

function removeSelected() {
  if (!selectedIds.value.length) return
  proxy.$modal.confirm(`确认删除选中的 ${selectedIds.value.length} 项吗？`)
    .then(() => delPhoto(selectedIds.value.join(',')))
    .then(() => {
      proxy.$modal.msgSuccess('删除成功')
      cancelMultiSelect()
      reload()
    })
    .catch(() => {})
}

function loadAlbum() {
  return getAlbum(albumId.value).then(res => {
    album.value = res.data || {}
  })
}

function loadPhotos(reset = false) {
  if (reset) {
    pageNum.value = 1
    loading.value = true
  } else {
    loadingMore.value = true
  }
  const query = {
    pageNum: pageNum.value,
    pageSize: pageSize.value,
    albumId: albumId.value
  }
  if (typeFilter.value !== 'all') {
    query.fileType = Number(typeFilter.value)
  }
  return listPhoto(query)
    .then(res => {
      const rows = res.rows || []
      total.value = res.total || 0
      photoList.value = reset ? rows : photoList.value.concat(rows)
    })
    .finally(() => {
      loading.value = false
      loadingMore.value = false
    })
}

function loadMore() {
  if (!hasMore.value || loadingMore.value) return
  pageNum.value += 1
  loadPhotos(false)
}

function reload() {
  clearSelection()
  loadPhotos(true)
}

function init() {
  loading.value = true
  Promise.all([loadAlbum(), loadPhotos(true)]).finally(() => {
    loading.value = false
  })
}

function onKeydown(e) {
  if (e.key === 'Escape') {
    if (ctxMenu.visible) {
      closeCtxMenu()
      return
    }
    if (mediaVisible.value) closeMedia()
  }
  if (!mediaVisible.value) return
  if (e.key === 'ArrowLeft') shiftMedia(-1)
  if (e.key === 'ArrowRight') shiftMedia(1)
}

function onWindowBlurOrScroll() {
  closeCtxMenu()
}

watch(() => route.params.albumId, (id) => {
  if (id) init()
})

watch(currentMedia, (item) => {
  if (detailOpen.value && item) {
    detailPhoto.value = item
  }
})

watch(detailOpen, () => {
  // 抽屉开合后重绘，让图片适配收缩后的展示区
  nextTick(() => {
    if (imageMode.value === 'contain') {
      imageTransform.offsetX = 0
      imageTransform.offsetY = 0
    }
    paintImageTransform(true)
  })
})

onMounted(() => {
  window.addEventListener('keydown', onKeydown)
  window.addEventListener('scroll', onWindowBlurOrScroll, true)
  window.addEventListener('resize', onWindowBlurOrScroll)
  window.addEventListener('blur', onWindowBlurOrScroll)
})
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  window.removeEventListener('scroll', onWindowBlurOrScroll, true)
  window.removeEventListener('resize', onWindowBlurOrScroll)
  window.removeEventListener('blur', onWindowBlurOrScroll)
  if (clickTimer.value) clearTimeout(clickTimer.value)
  if (imagePaintRaf) cancelAnimationFrame(imagePaintRaf)
  if (wheelZoomRaf) cancelAnimationFrame(wheelZoomRaf)
  stopImageDrag()
  closeCtxMenu()
})

init()
</script>

<style scoped lang="scss">
.album-detail {
  --muted: #999;
  --select-blue: #4c8dff;
  min-height: calc(100vh - 84px);
  margin: -20px;
  padding: 20px 28px 88px;
  background: #fff;
}

.detail-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.header-left {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  min-width: 0;
}

.back-btn {
  border: none;
  background: transparent;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: #333;
  flex-shrink: 0;
  margin-top: 2px;

  &:hover {
    background: #f3f3f3;
  }
}

.header-info {
  min-width: 0;
}

.album-title {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  line-height: 1.3;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.album-desc {
  margin-top: 6px;
  font-size: 13px;
  color: #666;
  cursor: pointer;
  min-height: 24px;

  &.placeholder {
    color: var(--muted);
  }
}

.header-right {
  flex-shrink: 0;
}

.hidden-input {
  display: none;
}

.mr4 {
  margin-right: 4px;
}

.detail-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  gap: 12px;
}

.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.select-btn {
  width: 28px;
  height: 28px;
  border: 1.5px solid #d0d0d0;
  border-radius: 50%;
  background: #fff;
  color: #8a8a8a;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 0.2s ease, border-color 0.2s ease, color 0.2s ease, transform 0.15s ease;

  &:hover {
    border-color: var(--select-blue);
    color: var(--select-blue);
  }

  &.active {
    background: var(--select-blue);
    border-color: var(--select-blue);
    color: #fff;
  }

  &:active {
    transform: scale(0.92);
  }
}

.item-count,
.selected-count,
.mode-tip {
  font-size: 13px;
  color: var(--muted);
}

.selected-count {
  color: #333;
  font-weight: 500;
}

.mode-tip {
  color: #637dff;
}

.tool-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: none;
  background: transparent;
  color: #555;
  font-size: 13px;
  cursor: pointer;
  padding: 4px 0;

  &:hover {
    color: #111;
  }

  &.icon-only {
    padding: 4px;
  }
}

.photo-grid {
  display: grid;
  gap: 4px;

  &.mode-small {
    grid-template-columns: repeat(auto-fill, minmax(96px, 1fr));
  }

  &.mode-medium {
    grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
    gap: 8px;
  }

  &.mode-large {
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
    gap: 12px;
  }
}

.photo-cell {
  position: relative;
  aspect-ratio: 1;
  border-radius: 2px;
  background: transparent;
  cursor: pointer;
  user-select: none;
  -webkit-user-drag: none;
  box-sizing: border-box;
  transition: background 0.18s ease, box-shadow 0.18s ease;

  .photo-inner {
    position: absolute;
    inset: 0;
    border-radius: 2px;
    overflow: hidden;
    background: #f2f2f2;
    transition: inset 0.18s cubic-bezier(0.22, 1, 0.36, 1),
      border-radius 0.18s ease;
  }

  img,
  .video-thumb {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
    background: #1a1a1a;
    pointer-events: none;
  }

  &:hover:not(.selected) .photo-inner {
    box-shadow: 0 2px 10px rgba(0, 0, 0, 0.08);
  }

  &:hover .check-mark {
    opacity: 1;
    transform: scale(1);
  }

  /* 选中：蓝色外框 + 白色内边距（参考阿里云盘） */
  &.selected {
    background: #fff;
    box-shadow: inset 0 0 0 2px var(--select-blue);
  }

  &.selected .photo-inner {
    inset: 4px;
    border-radius: 1px;
    box-shadow: none;
  }

  &.selected .check-mark {
    opacity: 1;
    transform: scale(1);
    background: var(--select-blue);
    color: #fff;
    border-color: var(--select-blue);
  }
}

.photo-grid.is-selecting .photo-cell .check-mark {
  opacity: 0.55;
}

.video-mark {
  position: absolute;
  left: 6px;
  bottom: 6px;
  min-height: 20px;
  padding: 0 6px;
  border-radius: 10px;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 3px;
  z-index: 1;
  font-size: 11px;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

.video-duration {
  transform: translateY(0.5px);
}

.duration-probe {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
  pointer-events: none;
  left: 0;
  top: 0;
}

.check-mark {
  position: absolute;
  top: 6px;
  left: 6px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: 1.5px solid rgba(255, 255, 255, 0.95);
  background: rgba(0, 0, 0, 0.18);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transform: scale(0.72);
  transition: opacity 0.18s ease, transform 0.22s cubic-bezier(0.22, 1, 0.36, 1),
    background 0.18s ease, border-color 0.18s ease;
  z-index: 2;
  backdrop-filter: blur(2px);
}

.photo-cell.selected .check-mark {
  top: 8px;
  left: 8px;
}

.empty-box {
  padding: 80px 0;
}

.detail-footer {
  margin-top: 28px;
  text-align: center;
  font-size: 13px;
  color: var(--muted);
}

.selection-bar {
  position: fixed;
  left: 50%;
  bottom: 28px;
  transform: translateX(-50%);
  z-index: 2500;
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 8px 12px;
  border-radius: 28px;
  background: rgba(45, 45, 45, 0.92);
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.22);
  backdrop-filter: blur(8px);
}

.sel-btn {
  width: 42px;
  height: 42px;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 0.15s ease;

  &:hover {
    background: rgba(255, 255, 255, 0.14);
  }
}

.sel-bar-enter-active,
.sel-bar-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.sel-bar-enter-from,
.sel-bar-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(12px);
}

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
  transition: right 0.22s ease;
}

.media-viewer.detail-open .media-stage {
  right: 360px;
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

  &:hover {
    background: rgba(0, 0, 0, 0.12);
  }
}

.media-close {
  position: fixed;
  top: 20px;
  left: 20px;
  z-index: 3020;
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
  z-index: 3020;
  display: flex;
  align-items: center;
  gap: 6px;
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
  transition: background 0.15s ease, color 0.15s ease;

  &:hover,
  &.active {
    background: rgba(0, 0, 0, 0.06);
    color: #111;
  }
}

.media-canvas {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  cursor: grab;
  z-index: 1;
  touch-action: none;
  user-select: none;
  -webkit-user-select: none;
  contain: strict;

  &.dragging {
    cursor: grabbing;
  }
}

.media-image {
  max-width: min(92%, 1400px);
  max-height: min(86%, 86vh);
  width: auto;
  height: auto;
  object-fit: contain;
  border-radius: 2px;
  outline: none;
  user-select: none;
  -webkit-user-select: none;
  -webkit-user-drag: none;
  pointer-events: none;
  will-change: transform;
  transform: translate3d(0, 0, 0);
  backface-visibility: hidden;
  transition: max-width 0.22s ease, max-height 0.22s ease;

  &.is-original {
    max-width: none;
    max-height: none;
  }
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
  max-height: min(86%, 86vh);
  width: auto;
  height: auto;
  object-fit: contain;
  border-radius: 2px;
  outline: none;
  user-select: none;
  background: #000;
  transition: max-width 0.22s ease, max-height 0.22s ease;
}

.media-toolbar {
  position: absolute;
  left: 50%;
  bottom: 28px;
  transform: translateX(-50%);
  z-index: 3;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 10px;
  border-radius: 22px;
  background: rgba(0, 0, 0, 0.72);
  color: #fff;
  backdrop-filter: blur(4px);
}

.media-tool-btn {
  width: 34px;
  height: 34px;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 0.15s ease;

  &:hover {
    background: rgba(255, 255, 255, 0.16);
  }
}

.media-zoom-label {
  min-width: 48px;
  text-align: center;
  font-size: 13px;
  line-height: 1;
  user-select: none;
}

@media (max-width: 768px) {
  .album-detail {
    margin: -15px;
    padding: 16px 14px 40px;
  }

  .album-title {
    font-size: 20px;
  }

  .detail-header {
    flex-direction: column;
  }
}
</style>

<style lang="scss">
/* 预览层之上的确认框 */
body > .el-overlay:has(.above-media-msgbox) {
  z-index: 4300 !important;
}

.add-to-dialog {
  border-radius: 12px;
  overflow: hidden;

  .el-dialog__header {
    margin: 0;
    padding: 18px 20px 12px;
  }

  .el-dialog__title {
    font-size: 18px;
    font-weight: 600;
    color: #1f1f1f;
  }

  .el-dialog__headerbtn {
    top: 18px;
    right: 18px;
    width: 28px;
    height: 28px;
  }

  .el-dialog__body {
    padding: 4px 12px 8px;
  }

  .el-dialog__footer {
    padding: 10px 16px 16px;
  }
}

.add-to-list {
  max-height: 420px;
  overflow: auto;
  padding: 4px 0;
}

.add-to-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 10px;
  cursor: pointer;
  border: 1.5px solid transparent;
  transition: background 0.15s ease, border-color 0.15s ease;

  &:hover {
    background: #f5f5f5;
  }

  &.selected {
    background: #f5f5f5;
  }

  &.is-editing {
    border-color: #4c8dff;
    background: #fff;
    cursor: default;
  }
}

.add-to-cover {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  overflow: hidden;
  flex-shrink: 0;
  background: #ececec;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #9a9a9a;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  &.is-placeholder {
    background: #efefef;
  }
}

.add-to-name {
  flex: 1;
  min-width: 0;
  font-size: 15px;
  color: #222;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.add-to-name-input {
  flex: 1;
  min-width: 0;
  height: 34px;
  border: none;
  outline: none;
  background: transparent;
  font-size: 15px;
  color: #1a73e8;
  padding: 0 4px;
}

.add-to-count {
  flex-shrink: 0;
  font-size: 13px;
  color: #8a8a8a;
  min-width: 28px;
  text-align: right;
}

.add-to-icon-btn {
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 50%;
  background: #e8e8e8;
  color: #555;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  flex-shrink: 0;

  &.ok {
    background: #4c8dff;
    color: #fff;
  }

  &:hover {
    filter: brightness(0.96);
  }
}

.add-to-empty {
  padding: 36px 12px;
  text-align: center;
  color: #999;
  font-size: 13px;
}

.add-to-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.add-to-create-link {
  border: none;
  background: transparent;
  color: #1a73e8;
  font-size: 14px;
  cursor: pointer;
  padding: 0;

  &:hover {
    color: #1558b0;
  }
}

.add-to-footer-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.photos-ctx-menu {
  position: fixed;
  z-index: 3200;
  min-width: 200px;
  padding: 6px 0;
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.16), 0 0 0 1px rgba(0, 0, 0, 0.04);
  user-select: none;
}

.photos-ctx-menu .ctx-item {
  width: 100%;
  border: none;
  background: transparent;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  font-size: 14px;
  color: #222;
  cursor: pointer;
  text-align: left;

  &:hover {
    background: #f3f3f3;
  }

  &.danger {
    color: #e85d5d;
  }

  .el-icon {
    color: #555;
  }
}

.photos-ctx-menu.type-item .ctx-item {
  gap: 0;
}

.photos-ctx-menu .ctx-divider {
  height: 1px;
  margin: 4px 10px;
  background: #ececec;
}

.photo-detail-panel {
  position: fixed;
  /* 避开顶部返回/操作按钮区域，只在下方红框范围弹出 */
  top: 64px;
  right: 0;
  bottom: 0;
  width: 360px;
  z-index: 3010;
  background: #fff;
  box-shadow: -4px 0 24px rgba(0, 0, 0, 0.08);
  border-radius: 12px 0 0 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.detail-panel-enter-active,
.detail-panel-leave-active {
  transition: transform 0.22s ease, opacity 0.22s ease;
}

.detail-panel-enter-from,
.detail-panel-leave-to {
  transform: translateX(100%);
  opacity: 0.6;
}

.photo-detail-album {
  flex-shrink: 0;
  padding: 16px 18px 14px;
  border-bottom: 1px solid #f0f0f0;
  background: #fff;
}

.photo-detail-info {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 16px 18px 28px;
}

.detail-section-title {
  margin: 0 0 14px;
  font-size: 15px;
  font-weight: 600;
  color: #222;
}

.detail-album-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.album-tag {
  border: none;
  border-radius: 6px;
  padding: 6px 12px;
  font-size: 13px;
  cursor: pointer;
  background: #f0f0f0;
  color: #333;

  &.ghost {
    background: #f5f5f5;
    color: #666;
  }

  &:hover {
    background: #e8e8e8;
  }
}

.photo-detail-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.photo-detail-row {
  display: flex;
  gap: 16px;
  font-size: 13px;
  line-height: 1.5;

  .label {
    width: 72px;
    flex-shrink: 0;
    color: #999;
  }

  .value {
    flex: 1;
    min-width: 0;
    color: #333;
    word-break: break-all;
  }
}
</style>
