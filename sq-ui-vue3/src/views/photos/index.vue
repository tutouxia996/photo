<template>
  <div class="photos-page" v-loading="loading" @click="closeAlbumMenu">
    <header class="photos-header">
      <h1 class="photos-title">相册</h1>
    </header>

    <div class="photos-toolbar">
      <span class="photos-count">共 {{ albumList.length }} 项</span>
      <div class="photos-tools">
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
              <el-dropdown-item command="createDesc">按创建时间降序</el-dropdown-item>
              <el-dropdown-item command="createAsc">按创建时间升序</el-dropdown-item>
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
          <span class="album-badge">{{ item.photoCount ?? 0 }}</span>
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
              <button type="button" class="album-more-item danger" @click="handleDelete(item)">删除</button>
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

    <el-dialog v-model="createOpen" :title="createDialogTitle" width="520px" append-to-body @closed="resetCreateForm">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="96px">
        <el-form-item label="磁盘目录" prop="localPath">
          <el-input
            v-model="createForm.localPath"
            placeholder="服务器本地路径，如 D:/photos/天坛公园"
            clearable
            @input="onLocalPathInput"
          />
          <div class="form-tip">从磁盘扫描索引，不会上传或复制原图；填写后默认用文件夹名作为相册名</div>
        </el-form-item>
        <el-form-item label="相册名称" prop="albumName">
          <el-input
            v-model="createForm.albumName"
            maxlength="50"
            placeholder="默认同文件夹名，可修改"
            @input="albumNameManual = true"
          />
        </el-form-item>
        <el-form-item label="描述" prop="albumDesc">
          <el-input v-model="createForm.albumDesc" type="textarea" :rows="3" placeholder="可选" />
        </el-form-item>
        <el-form-item label="公开状态" prop="isPublic">
          <el-radio-group v-model="createForm.isPublic">
            <el-radio :value="1">公开</el-radio>
            <el-radio :value="0">私有</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createOpen = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">
          {{ createForm.localPath ? '创建并扫描' : '确定' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editOpen" title="编辑相册" width="460px" append-to-body @closed="resetEditForm">
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

const { proxy } = getCurrentInstance()

const loading = ref(false)
const albumList = ref([])
const filterKey = ref('all')
const sortKey = ref('createDesc')
const fabOpen = ref(false)
const createOpen = ref(false)
const creating = ref(false)
const albumNameManual = ref(false)
const preferImport = ref(false)
const menuAlbumId = ref(null)
const editOpen = ref(false)
const editing = ref(false)
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

const filterLabel = computed(() => {
  const map = { all: '全部', public: '公开', private: '私有' }
  return map[filterKey.value] || '全部'
})

const sortLabel = computed(() => {
  const map = {
    createDesc: '按创建时间降序',
    createAsc: '按创建时间升序',
    nameAsc: '按名称升序',
    countDesc: '按照片数降序'
  }
  return map[sortKey.value] || '按创建时间降序'
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
    const ta = new Date(a.createTime || 0).getTime()
    const tb = new Date(b.createTime || 0).getTime()
    return sortKey.value === 'createAsc' ? ta - tb : tb - ta
  })
  return list
})

function resolveUrl(url) {
  if (!url) return ''
  if (isExternal(url)) return url
  return import.meta.env.VITE_APP_BASE_API + url
}

function coverSrc(item) {
  if (item.coverUrl) {
    return resolveUrl(item.coverUrl)
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
  proxy.$router.push('/photos/detail/' + item.albumId)
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

function handleDelete(item) {
  menuAlbumId.value = null
  proxy.$modal.confirm(`确认删除相册「${item.albumName}」吗？`)
    .then(() => delAlbum(item.albumId))
    .then(() => {
      proxy.$modal.msgSuccess('删除成功')
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

function onUploadPhotos() {
  fabOpen.value = false
  proxy.$modal.msg('上传照片/视频：请在图片管理中使用上传功能')
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
    const req = localPath
      ? importAlbumFromDisk({ ...payload, localPath })
      : addAlbum(payload)
    req
      .then(res => {
        const count = res.data?.album?.photoCount
        proxy.$modal.msgSuccess(
          localPath
            ? `创建成功${count != null ? `，已索引 ${count} 项` : '，磁盘扫描已完成'}`
            : '创建成功'
        )
        createOpen.value = false
        getList()
      })
      .finally(() => {
        creating.value = false
      })
  })
}

function getList() {
  loading.value = true
  listAlbum({ pageNum: 1, pageSize: 200 })
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
