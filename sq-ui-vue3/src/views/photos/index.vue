<template>
  <div class="photos-page" v-loading="loading" @click="closeAlbumMenu" @contextmenu.prevent>
    <header class="photos-header">
      <h1 class="photos-title">相册</h1>
    </header>

    <div class="photos-toolbar">
      <span class="photos-count">共 {{ albumList.length }} 项</span>
      <div class="photos-tools">
        <button type="button" class="tool-btn" @click="openPhotoMap">
          <el-icon><Location /></el-icon>
          <span>照片地图</span>
        </button>
        <button type="button" class="tool-btn" @click="openRecycle">
          <el-icon><Delete /></el-icon>
          <span>回收站</span>
        </button>
        <el-dropdown trigger="click" @command="handleFilter">
          <button type="button" class="tool-btn">
            <el-icon><Operation /></el-icon>
            <span>{{ filterLabel }}</span>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="all">全部</el-dropdown-item>
              <el-dropdown-item command="public">公开</el-dropdown-item>
              <el-dropdown-item command="private">私有</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-dropdown trigger="click" @command="handleSort">
          <button type="button" class="tool-btn">
            <el-icon><Sort /></el-icon>
            <span>{{ sortLabel }}</span>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="dateDesc">按拍摄时间降序</el-dropdown-item>
              <el-dropdown-item command="dateAsc">按拍摄时间升序</el-dropdown-item>
              <el-dropdown-item command="nameAsc">按名称升序</el-dropdown-item>
              <el-dropdown-item command="countDesc">按照片数降序</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>

    <div class="album-grid">
      <div
        v-for="item in displayList"
        :key="item.albumId"
        class="album-card"
        :class="{ 'is-active': menuAlbumId === item.albumId }"
        @click.stop="openAlbum(item)"
      >
        <div class="album-cover">
          <img v-if="coverSrc(item)" :src="coverSrc(item)" :alt="item.albumName" loading="lazy" />
          <div v-else class="album-cover-placeholder">
            <el-icon :size="36"><PictureFilled /></el-icon>
          </div>
          <span class="album-badge" :class="{ scanning: isAlbumScanning(item.albumId) }">
            {{ isAlbumScanning(item.albumId) ? '扫描中' : (item.photoCount ?? 0) }}
          </span>
          <div class="album-more" @click.stop>
            <button
              type="button"
              class="album-more-btn"
              title="更多"
              @click="toggleAlbumMenu(item, $event)"
            >
              <el-icon :size="16"><MoreFilled /></el-icon>
            </button>
            <div v-if="menuAlbumId === item.albumId" class="album-more-menu">
              <button type="button" class="album-more-item" @click="openEdit(item)">编辑</button>
              <div class="album-more-divider"></div>
              <button type="button" class="album-more-item danger" @click="handleDelete(item)">放入回收站</button>
            </div>
          </div>
        </div>
        <div class="album-meta">
          <div class="album-name" :title="item.albumName">{{ item.albumName }}</div>
          <div v-if="albumSubtitle(item)" class="album-sub">{{ albumSubtitle(item) }}</div>
        </div>
      </div>

      <div class="album-card create-card" @click="openCreate">
        <div class="create-box">
          <el-icon :size="28"><Plus /></el-icon>
        </div>
        <div class="album-meta">
          <div class="album-name">创建相册</div>
        </div>
      </div>
    </div>

    <div class="fab-wrap" v-click-outside="closeFab">
      <transition name="fab-menu">
        <div v-if="fabOpen" class="fab-menu">
          <button type="button" class="fab-menu-item" @click="onUploadPhotos">
            <el-icon><Picture /></el-icon>
            <span>上传照片/视频</span>
          </button>
          <button type="button" class="fab-menu-item" @click="openImportFolder">
            <el-icon><FolderOpened /></el-icon>
            <span>导入文件夹</span>
          </button>
          <button type="button" class="fab-menu-item" @click="openCreate">
            <el-icon><Files /></el-icon>
            <span>创建相册</span>
          </button>
        </div>
      </transition>
      <button type="button" class="fab-btn" :class="{ open: fabOpen }" @click="fabOpen = !fabOpen">
        <el-icon :size="28"><Plus /></el-icon>
      </button>
    </div>

    <el-dialog
      v-model="createOpen"
      :title="createDialogTitle"
      width="520px"
      append-to-body
      :close-on-click-modal="!creating"
      :close-on-press-escape="!creating"
      :show-close="!creating"
      :before-close="beforeCreateClose"
      @closed="resetCreateForm"
    >
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="96px">
        <el-form-item label="磁盘目录" prop="localPath">
          <el-input
            v-model="createForm.localPath"
            placeholder="服务器本地路径，如 D:/photos/天坛公园"
            clearable
            :disabled="creating"
            @input="onLocalPathInput"
          />
          <div class="form-tip">从磁盘扫描索引，不会上传或复制原图；填写后默认用文件夹名作为相册名。照片较多时扫描可能需数分钟，请耐心等待</div>
        </el-form-item>
        <el-form-item label="相册名称" prop="albumName">
          <el-input
            v-model="createForm.albumName"
            maxlength="50"
            placeholder="默认同文件夹名，可修改"
            :disabled="creating"
            @input="albumNameManual = true"
          />
        </el-form-item>
        <el-form-item label="描述" prop="albumDesc">
          <el-input v-model="createForm.albumDesc" type="textarea" :rows="3" placeholder="可选" :disabled="creating" />
        </el-form-item>
        <el-form-item label="公开状态" prop="isPublic">
          <el-radio-group v-model="createForm.isPublic" :disabled="creating">
            <el-radio :value="1">公开</el-radio>
            <el-radio :value="0">私有</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="creating" @click="createOpen = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">
          {{ createForm.localPath ? '创建并扫描' : '确定' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 磁盘扫描进度 -->
    <teleport to="body">
      <transition name="scan-fade">
        <div v-if="scanUi.visible" class="scan-progress-mask" @click.stop @contextmenu.prevent>
          <div class="scan-progress-panel" role="dialog" aria-label="扫描进度">
            <div class="scan-progress-title">正在扫描磁盘</div>
            <div class="scan-progress-summary">
              <span>{{ scanProcessedText }}</span>
              <span>{{ scanPercent }}%</span>
            </div>
            <el-progress
              :percentage="scanPercent"
              :stroke-width="10"
              :show-text="false"
              striped
              striped-flow
              :status="scanUi.status === 2 ? 'exception' : undefined"
            />
            <div class="scan-progress-stats">
              <span>新增 {{ scanUi.newCount }}</span>
              <span>跳过 {{ scanUi.skipCount }}</span>
              <span>失败 {{ scanUi.failCount }}</span>
            </div>
            <div class="scan-progress-current" :title="scanUi.message">{{ scanUi.message || '准备中…' }}</div>
            <div class="scan-progress-tip">扫描在后台进行，完成后会自动刷新相册；请勿关闭或刷新页面</div>
          </div>
        </div>
      </transition>
    </teleport>

    <el-dialog v-model="editOpen" title="编辑相册" width="460px" append-to-body :close-on-click-modal="!editing" @closed="resetEditForm">
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="88px">
        <el-form-item label="相册名称" prop="albumName">
          <el-input v-model="editForm.albumName" maxlength="50" placeholder="请输入相册名称" />
        </el-form-item>
        <el-form-item label="描述" prop="albumDesc">
          <el-input v-model="editForm.albumDesc" type="textarea" :rows="3" placeholder="可选" />
        </el-form-item>
        <el-form-item label="封面URL" prop="coverUrl">
          <el-input v-model="editForm.coverUrl" placeholder="可选" />
        </el-form-item>
        <el-form-item label="公开状态" prop="isPublic">
          <el-radio-group v-model="editForm.isPublic">
            <el-radio :value="1">公开</el-radio>
            <el-radio :value="0">私有</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editOpen = false">取消</el-button>
        <el-button type="primary" :loading="editing" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="PhotosAlbum">
import { ClickOutside as vClickOutside } from 'element-plus'
import { isExternal } from '@/utils/validate'
import { listAlbum, addAlbum, importAlbumFromDisk, updateAlbum, delAlbum } from '@/api/photos/album'
import { pollScanProgress } from '@/api/album/scan'

const { proxy } = getCurrentInstance()

const loading = ref(false)
const albumList = ref([])
const filterKey = ref('all')
const sortKey = ref('dateDesc')
const fabOpen = ref(false)
const createOpen = ref(false)
const creating = ref(false)
const albumNameManual = ref(false)
const preferImport = ref(false)
const menuAlbumId = ref(null)
const editOpen = ref(false)
const editing = ref(false)
const scanningAlbumIds = ref([])
const scanAbortController = ref(null)
const scanUi = reactive({
  visible: false,
  albumId: null,
  status: 0,
  totalCount: 0,
  newCount: 0,
  skipCount: 0,
  failCount: 0,
  message: ''
})
const createFormRef = ref()
const editFormRef = ref()
const createForm = reactive({
  localPath: '',
  albumName: '',
  albumDesc: '',
  isPublic: 1
})
const editForm = reactive({
  albumId: undefined,
  albumName: '',
  albumDesc: '',
  coverUrl: '',
  isPublic: 1
})
const createRules = {
  albumName: [{
    validator: (_rule, value, callback) => {
      if (createForm.localPath || (value && String(value).trim())) {
        callback()
      } else {
        callback(new Error('请填写相册名称，或填写磁盘目录以自动命名'))
      }
    },
    trigger: 'blur'
  }]
}
const editRules = {
  albumName: [{ required: true, message: '请输入相册名称', trigger: 'blur' }]
}

const createDialogTitle = computed(() => (preferImport.value || createForm.localPath ? '导入文件夹创建相册' : '创建相册'))

const scanPercent = computed(() => {
  const total = Number(scanUi.totalCount) || 0
  if (total <= 0) {
    return scanUi.status === 0 ? 0 : 100
  }
  const processed = (Number(scanUi.newCount) || 0) + (Number(scanUi.skipCount) || 0) + (Number(scanUi.failCount) || 0)
  return Math.min(100, Math.max(0, Math.round((processed / total) * 100)))
})

const scanProcessedText = computed(() => {
  const total = Number(scanUi.totalCount) || 0
  const processed = (Number(scanUi.newCount) || 0) + (Number(scanUi.skipCount) || 0) + (Number(scanUi.failCount) || 0)
  if (!total && scanUi.status === 0) return '统计文件中…'
  return `${processed}/${total || processed}`
})

function isAlbumScanning(albumId) {
  return scanningAlbumIds.value.some(id => String(id) === String(albumId))
}

function applyScanProgress(log) {
  scanUi.status = Number(log.status ?? 0)
  scanUi.totalCount = Number(log.totalCount) || 0
  scanUi.newCount = Number(log.newCount) || 0
  scanUi.skipCount = Number(log.skipCount) || 0
  scanUi.failCount = Number(log.failCount) || 0
  scanUi.message = log.message || ''
}

function resetScanUi() {
  scanUi.visible = false
  scanUi.albumId = null
  scanUi.status = 0
  scanUi.totalCount = 0
  scanUi.newCount = 0
  scanUi.skipCount = 0
  scanUi.failCount = 0
  scanUi.message = ''
}

const filterLabel = computed(() => {
  const map = { all: '全部', public: '公开', private: '私有' }
  return map[filterKey.value] || '全部'
})

const sortLabel = computed(() => {
  const map = {
    dateDesc: '按拍摄时间降序',
    dateAsc: '按拍摄时间升序',
    nameAsc: '按名称升序',
    countDesc: '按照片数降序'
  }
  return map[sortKey.value] || '按拍摄时间降序'
})

const displayList = computed(() => {
  let list = [...albumList.value]
  if (filterKey.value === 'public') {
    list = list.filter(i => i.isPublic === 1 || i.isPublic === undefined)
  } else if (filterKey.value === 'private') {
    list = list.filter(i => i.isPublic === 0)
  }
  list.sort((a, b) => {
    if (sortKey.value === 'nameAsc') {
      return String(a.albumName || '').localeCompare(String(b.albumName || ''), 'zh')
    }
    if (sortKey.value === 'countDesc') {
      return (b.photoCount || 0) - (a.photoCount || 0)
    }
    // 与卡片日期一致：相册 startTime = 相册内照片/视频拍摄时间的最早值
    const ta = shootTimeTs(a)
    const tb = shootTimeTs(b)
    const aMissing = ta == null
    const bMissing = tb == null
    if (aMissing && bMissing) return 0
    if (aMissing) return 1
    if (bMissing) return -1
    return sortKey.value === 'dateAsc' ? ta - tb : tb - ta
  })
  return list
})

/** 相册拍摄时间：后端 refreshAlbumStats 写入的 startTime（min 拍摄时间） */
function shootTimeTs(item) {
  const raw = item?.startTime
  if (!raw) return null
  const t = new Date(typeof raw === 'string' ? raw.replace(/-/g, '/') : raw).getTime()
  return Number.isNaN(t) ? null : t
}

function resolveUrl(url) {
  if (!url) return ''
  if (isExternal(url)) return url
  return import.meta.env.VITE_APP_BASE_API + url
}

function coverSrc(item) {
  if (item.coverUrl) {
    const cover = String(item.coverUrl)
    // 静态缩略图/上传文件直出
    if (cover.startsWith('/album/files/')) {
      return resolveUrl(cover)
    }
    const mediaMatch = cover.match(/\/album\/photo\/media\/(\d+)/)
    if (mediaMatch) {
      // 历史封面仍是 media 链接时保留兼容
      return resolveUrl('/album/photo/media/' + mediaMatch[1])
    }
    return resolveUrl(cover)
  }
  // 兼容旧数据：有照片但未写封面时，用媒体预览接口
  if (item.photoCount > 0 && item.coverPhotoId) {
    return resolveUrl('/album/photo/media/' + item.coverPhotoId)
  }
  return ''
}

function formatCnDate(time) {
  if (!time) return ''
  const d = new Date(typeof time === 'string' ? time.replace(/-/g, '/') : time)
  if (Number.isNaN(d.getTime())) return ''
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日`
}

function albumSubtitle(item) {
  if (item.locationSummary) return item.locationSummary
  return formatCnDate(item.startTime || item.createTime)
}

function handleFilter(cmd) {
  filterKey.value = cmd
}

function handleSort(cmd) {
  sortKey.value = cmd
}

function closeFab() {
  fabOpen.value = false
}

function openAlbum(item) {
  if (menuAlbumId.value != null) {
    menuAlbumId.value = null
    return
  }
  // 原地打开详情：不新增「相册详情」页签，仍留在「相册」页签内
  proxy.$tab.navigatePage({ path: '/photos/detail/' + item.albumId })
}

function closeAlbumMenu() {
  menuAlbumId.value = null
}

function toggleAlbumMenu(item, event) {
  event?.stopPropagation?.()
  menuAlbumId.value = menuAlbumId.value === item.albumId ? null : item.albumId
}

function openEdit(item) {
  menuAlbumId.value = null
  editForm.albumId = item.albumId
  editForm.albumName = item.albumName || ''
  editForm.albumDesc = item.albumDesc || ''
  editForm.coverUrl = item.coverUrl || ''
  editForm.isPublic = item.isPublic ?? 1
  editOpen.value = true
}

function resetEditForm() {
  editForm.albumId = undefined
  editForm.albumName = ''
  editForm.albumDesc = ''
  editForm.coverUrl = ''
  editForm.isPublic = 1
  editFormRef.value?.resetFields?.()
}

function submitEdit() {
  editFormRef.value.validate(valid => {
    if (!valid) return
    editing.value = true
    updateAlbum({
      albumId: editForm.albumId,
      albumName: editForm.albumName.trim(),
      albumDesc: editForm.albumDesc,
      coverUrl: editForm.coverUrl,
      isPublic: editForm.isPublic
    })
      .then(() => {
        proxy.$modal.msgSuccess('保存成功')
        editOpen.value = false
        getList()
      })
      .finally(() => {
        editing.value = false
      })
  })
}

function openRecycle() {
  proxy.$tab.navigatePage({ path: '/photos/recycle' })
}

function openPhotoMap() {
  proxy.$modal.msgWarning('请进入相册后再打开照片地图')
}

function handleDelete(item) {
  menuAlbumId.value = null
  proxy.$modal.confirm(`确认将相册「${item.albumName}」放入回收站吗？`)
    .then(() => delAlbum(item.albumId))
    .then(() => {
      proxy.$modal.msgSuccess('已放入回收站')
      getList()
    })
    .catch(() => {})
}

function folderNameFromPath(path) {
  if (!path) return ''
  const normalized = String(path).trim().replace(/[\\/]+$/, '')
  const parts = normalized.split(/[\\/]/).filter(Boolean)
  return parts.length ? parts[parts.length - 1] : ''
}

function onLocalPathInput() {
  if (albumNameManual.value) return
  createForm.albumName = folderNameFromPath(createForm.localPath)
}

function openCreate() {
  fabOpen.value = false
  preferImport.value = false
  createOpen.value = true
}

function openImportFolder() {
  fabOpen.value = false
  preferImport.value = true
  createOpen.value = true
}

function resetCreateForm() {
  createForm.localPath = ''
  createForm.albumName = ''
  createForm.albumDesc = ''
  createForm.isPublic = 1
  albumNameManual.value = false
  preferImport.value = false
  createFormRef.value?.resetFields?.()
}

function beforeCreateClose(done) {
  if (creating.value) {
    proxy.$modal.msgWarning('正在创建中，请稍候')
    return
  }
  done()
}

function onUploadPhotos() {
  fabOpen.value = false
  proxy.$modal.msg('上传照片/视频：请在图片管理中使用上传功能')
}

async function trackDiskScan(scanLogId, albumId) {
  if (!scanLogId) return
  scanAbortController.value?.abort?.()
  const ac = typeof AbortController !== 'undefined' ? new AbortController() : null
  scanAbortController.value = ac
  scanUi.visible = true
  scanUi.albumId = albumId
  applyScanProgress({ status: 0, totalCount: 0, newCount: 0, skipCount: 0, failCount: 0, message: '正在启动扫描…' })
  if (albumId != null) {
    scanningAlbumIds.value = [...new Set([...scanningAlbumIds.value, albumId])]
  }
  try {
    const finalLog = await pollScanProgress(scanLogId, {
      interval: 800,
      onProgress: applyScanProgress,
      signal: ac?.signal
    })
    applyScanProgress(finalLog)
    const added = Number(finalLog.newCount) || 0
    if (Number(finalLog.status) === 2) {
      proxy.$modal.msgError(finalLog.message || '扫描失败')
    } else {
      proxy.$modal.msgSuccess(`扫描完成，新增 ${added} 项`)
    }
  } catch (e) {
    if (e?.message !== 'aborted') {
      proxy.$modal.msgError('扫描进度获取失败，请稍后刷新相册列表')
    }
  } finally {
    if (scanAbortController.value === ac) {
      scanAbortController.value = null
    }
    if (albumId != null) {
      scanningAlbumIds.value = scanningAlbumIds.value.filter(id => String(id) !== String(albumId))
    }
    await getList()
    // 稍留完成态再关闭，避免进度条瞬间消失
    setTimeout(() => {
      if (!scanningAlbumIds.value.length) resetScanUi()
    }, 600)
  }
}

function submitCreate() {
  createFormRef.value.validate(valid => {
    if (!valid) return
    const localPath = (createForm.localPath || '').trim()
    if (preferImport.value && !localPath) {
      proxy.$modal.msgWarning('请填写服务器磁盘目录路径')
      return
    }
    creating.value = true
    const payload = {
      albumName: (createForm.albumName || '').trim() || folderNameFromPath(localPath),
      albumDesc: createForm.albumDesc,
      isPublic: createForm.isPublic
    }
    if (!localPath) {
      addAlbum(payload)
        .then(() => {
          proxy.$modal.msgSuccess('创建成功')
          createOpen.value = false
          getList()
        })
        .finally(() => {
          creating.value = false
        })
      return
    }
    importAlbumFromDisk({ ...payload, localPath })
      .then(async res => {
        const album = res.data?.album
        const scanLogId = res.data?.scanLogId
        createOpen.value = false
        creating.value = false
        await getList()
        if (scanLogId) {
          await trackDiskScan(scanLogId, album?.albumId)
        } else {
          proxy.$modal.msgSuccess('创建成功')
        }
      })
      .catch(() => {
        creating.value = false
      })
  })
}

function getList() {
  loading.value = true
  return listAlbum({ pageNum: 1, pageSize: 200 })
    .then(res => {
      albumList.value = res.rows || []
    })
    .catch(() => {
      albumList.value = []
    })
    .finally(() => {
      loading.value = false
    })
}

onActivated(() => {
  getList()
})

onBeforeUnmount(() => {
  scanAbortController.value?.abort?.()
})

getList()
</script>

<style scoped lang="scss">
.photos-page {
  --photos-text: #1a1a1a;
  --photos-muted: #999999;
  --photos-fab: #5b7fff;
  --photos-create-bg: #f0f0f0;
  min-height: calc(100vh - 84px);
  margin: -20px;
  padding: 28px 32px 96px;
  background: #fff;
  color: var(--photos-text);
  position: relative;
}

.form-tip {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.4;
  color: var(--photos-muted);
}

.album-badge.scanning {
  min-width: 44px;
  background: rgba(76, 141, 255, 0.9);
}

.photos-header {
  margin-bottom: 18px;
}

.photos-title {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
  letter-spacing: 0.02em;
  line-height: 1.2;
}

.photos-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 22px;
  min-height: 28px;
}

.photos-count {
  font-size: 13px;
  color: var(--photos-muted);
}

.photos-tools {
  display: flex;
  align-items: center;
  gap: 18px;
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
}

.album-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(168px, 1fr));
  gap: 22px 18px;
}

.album-card {
  cursor: pointer;
  min-width: 0;
  padding: 8px;
  margin: -8px;
  border-radius: 10px;
  transition: background 0.15s ease;

  &:hover,
  &.is-active {
    background: #f3f3f3;
  }

  &:hover .album-more-btn,
  &.is-active .album-more-btn {
    opacity: 1;
    visibility: visible;
  }

  &:hover .album-name,
  &.is-active .album-name {
    color: #000;
  }
}

.album-cover {
  position: relative;
  aspect-ratio: 4 / 3;
  border-radius: 6px;
  background: #ececec;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
    border-radius: 6px;
  }
}

.album-cover-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c0c0c0;
  border-radius: 6px;
  background: linear-gradient(145deg, #f2f2f2, #e4e4e4);
}

.album-more {
  position: absolute;
  top: 8px;
  right: 8px;
  z-index: 3;
}

.album-more-btn {
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.92);
  color: #333;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  opacity: 0;
  visibility: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
  transition: opacity 0.15s ease, background 0.15s ease;

  &:hover {
    background: #fff;
  }
}

.album-more-menu {
  position: absolute;
  top: 34px;
  right: 0;
  min-width: 112px;
  padding: 6px 0;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.14);
  overflow: hidden;
}

.album-more-item {
  width: 100%;
  border: none;
  background: transparent;
  text-align: left;
  padding: 10px 16px;
  font-size: 14px;
  color: #222;
  cursor: pointer;

  &:hover {
    background: #f5f5f5;
  }

  &.danger {
    color: #f56c6c;
  }
}

.album-more-divider {
  height: 1px;
  margin: 2px 0;
  background: #eee;
}

.album-badge {
  position: absolute;
  right: 8px;
  bottom: 8px;
  min-width: 28px;
  padding: 2px 7px;
  border-radius: 3px;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 12px;
  line-height: 18px;
  text-align: center;
}

.album-meta {
  margin-top: 10px;
  padding: 0 2px;
}

.album-name {
  font-size: 14px;
  font-weight: 600;
  color: #222;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  transition: color 0.15s ease;
}

.album-sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--photos-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.create-card {
  .create-box {
    aspect-ratio: 4 / 3;
    border-radius: 6px;
    background: var(--photos-create-bg);
    display: flex;
    align-items: center;
    justify-content: center;
    color: #8a8a8a;
    transition: background 0.2s ease, color 0.2s ease;
  }

  &:hover .create-box {
    background: #e6e6e6;
    color: #555;
    box-shadow: none;
    transform: none;
  }

  &:hover .album-cover {
    transform: none;
    box-shadow: none;
  }
}

.fab-wrap {
  position: fixed;
  right: 36px;
  bottom: 36px;
  z-index: 20;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 12px;
}

.fab-btn {
  width: 56px;
  height: 56px;
  border: none;
  border-radius: 50%;
  background: var(--photos-fab);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 6px 18px rgba(91, 127, 255, 0.45);
  transition: transform 0.2s ease, background 0.2s ease;

  &:hover {
    background: #4a6ef5;
  }

  &.open {
    transform: rotate(45deg);
  }
}

.fab-menu {
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.12);
  padding: 8px 0;
  min-width: 180px;
  overflow: hidden;
}

.fab-menu-item {
  width: 100%;
  border: none;
  background: transparent;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 18px;
  font-size: 14px;
  color: #333;
  cursor: pointer;
  text-align: left;

  &:hover {
    background: #f5f7ff;
    color: var(--photos-fab);
  }
}

.fab-menu-enter-active,
.fab-menu-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.fab-menu-enter-from,
.fab-menu-leave-to {
  opacity: 0;
  transform: translateY(8px);
}

@media (max-width: 768px) {
  .photos-page {
    margin: -15px;
    padding: 20px 16px 100px;
  }

  .photos-title {
    font-size: 24px;
  }

  .album-grid {
    grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
    gap: 16px 12px;
  }

  .fab-wrap {
    right: 20px;
    bottom: 24px;
  }
}
</style>

<style lang="scss">
.scan-progress-mask {
  position: fixed;
  inset: 0;
  z-index: 5000;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.scan-progress-panel {
  width: min(440px, 100%);
  background: #fff;
  border-radius: 12px;
  padding: 22px 24px 18px;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.18);
}

.scan-progress-title {
  font-size: 17px;
  font-weight: 650;
  color: #1a1a1a;
  margin-bottom: 14px;
}

.scan-progress-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 13px;
  color: #666;
}

.scan-progress-stats {
  display: flex;
  gap: 16px;
  margin-top: 12px;
  font-size: 12px;
  color: #888;
}

.scan-progress-current {
  margin-top: 12px;
  font-size: 13px;
  color: #333;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.scan-progress-tip {
  margin-top: 14px;
  font-size: 12px;
  color: #999;
  line-height: 1.4;
}

.scan-fade-enter-active,
.scan-fade-leave-active {
  transition: opacity 0.18s ease;
}

.scan-fade-enter-from,
.scan-fade-leave-to {
  opacity: 0;
}
</style>
