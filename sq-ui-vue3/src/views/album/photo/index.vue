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
      <el-form-item label="文件名">
        <el-input
          v-model="queryParams.fileName"
          placeholder="文件名"
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
        <el-button type="primary" plain icon="Upload" @click="uploadOpen = true" v-hasPermi="['album:photo:upload']">上传图片</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="!ids.length" @click="handleDelete()" v-hasPermi="['album:photo:remove']">删除</el-button>
      </el-col>
    </el-row>

    <el-table v-loading="loading" :data="photoList" @selection-change="sel => ids = sel.map(i => i.photoId)">
      <el-table-column type="selection" width="55" />
      <el-table-column label="ID" prop="photoId" width="80" />
      <el-table-column label="相册" min-width="140" :show-overflow-tooltip="true">
        <template #default="scope">
          {{ albumNameMap[scope.row.albumId] || scope.row.albumId || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="预览" width="90" align="center">
        <template #default="scope">
          <div class="thumb-wrap">
            <el-image
              style="width:56px;height:56px"
              :src="thumbSrc(scope.row)"
              :preview-src-list="previewList(scope.row)"
              :initial-index="0"
              preview-teleported
              fit="cover"
            >
              <template #error>
                <div class="thumb-fallback">{{ scope.row.fileType === 2 ? '视频' : '无图' }}</div>
              </template>
            </el-image>
            <span v-if="scope.row.fileType === 2" class="thumb-badge">视频</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="文件名" prop="fileName" :show-overflow-tooltip="true" />
      <el-table-column label="拍摄时间" prop="shootTime" width="170" />
      <el-table-column label="相机" prop="cameraModel" :show-overflow-tooltip="true" />
      <el-table-column label="坐标" width="180">
        <template #default="scope">
          <span v-if="scope.row.latitude">{{ scope.row.latitude }}, {{ scope.row.longitude }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template #default="scope">
          <el-button link type="danger" @click="handleDelete(scope.row)" v-hasPermi="['album:photo:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog title="上传图片" v-model="uploadOpen" width="480px">
      <el-form label-width="80px">
        <el-form-item label="相册" required>
          <el-select v-model="uploadAlbumId" placeholder="选择目标相册" filterable style="width: 100%">
            <el-option
              v-for="item in albumOptions"
              :key="item.albumId"
              :label="albumLabel(item)"
              :value="item.albumId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="文件">
          <el-upload :auto-upload="false" :limit="1" :on-change="f => uploadFile = f.raw" :on-remove="() => uploadFile = null">
            <el-button type="primary">选择文件</el-button>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitUpload">上传</el-button>
        <el-button @click="uploadOpen = false">取消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="AlbumPhoto">
import { isExternal } from '@/utils/validate'
import { listAlbum } from '@/api/album/album'
import { listPhoto, delPhoto, uploadPhoto } from '@/api/album/photo'

const { proxy } = getCurrentInstance()
const photoList = ref([])
const albumOptions = ref([])
const albumNameMap = ref({})
const loading = ref(true)
const total = ref(0)
const ids = ref([])
const uploadOpen = ref(false)
const uploadAlbumId = ref(undefined)
const uploadFile = ref(null)
const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  albumId: undefined,
  fileName: undefined
})

function albumLabel(item) {
  if (!item) return ''
  return `${item.albumName || '未命名'}（ID:${item.albumId}）`
}

function resolveUrl(url) {
  if (!url) return ''
  if (isExternal(url)) return url
  return import.meta.env.VITE_APP_BASE_API + url
}

/** 统一走媒体接口，避免 /album/files/** 无静态映射导致预览失败 */
function thumbSrc(item) {
  if (!item?.photoId) return ''
  return resolveUrl('/album/photo/media/' + item.photoId)
}

function originalSrc(item) {
  if (!item?.photoId) return ''
  return resolveUrl('/album/photo/media/' + item.photoId + '?original=true')
}

/** 视频大图预览用截帧缩略图，不能把 mp4 塞进 el-image */
function previewList(item) {
  if (!item?.photoId) return []
  if (item.fileType === 2) return [thumbSrc(item)]
  return [originalSrc(item)]
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
  listPhoto(queryParams.value).then(res => {
    photoList.value = res.rows || []
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
    fileName: undefined
  }
  getList()
}

function handleDelete(row) {
  const photoIds = row?.photoId || ids.value
  proxy.$modal.confirm('确认将选中图片放入回收站？').then(() => delPhoto(photoIds)).then(() => {
    getList()
    proxy.$modal.msgSuccess('已放入回收站')
  }).catch(() => {})
}

function submitUpload() {
  if (!uploadAlbumId.value || !uploadFile.value) {
    proxy.$modal.msgError('请选择相册并选择文件')
    return
  }
  const fd = new FormData()
  fd.append('file', uploadFile.value)
  fd.append('albumId', uploadAlbumId.value)
  uploadPhoto(fd).then(() => {
    proxy.$modal.msgSuccess('上传成功')
    uploadOpen.value = false
    uploadFile.value = null
    getList()
  })
}

loadAlbums().finally(() => getList())
</script>

<style scoped>
.thumb-wrap {
  position: relative;
  width: 56px;
  height: 56px;
  margin: 0 auto;
}

.thumb-fallback {
  width: 56px;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f5f5;
  color: #999;
  font-size: 12px;
  border-radius: 4px;
}

.thumb-badge {
  position: absolute;
  right: 2px;
  bottom: 2px;
  padding: 0 4px;
  border-radius: 3px;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 10px;
  line-height: 16px;
  pointer-events: none;
}
</style>
