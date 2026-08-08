<template>
  <div class="app-container">
    <el-form :model="queryParams" :inline="true" v-show="showSearch">
      <el-form-item label="相册名称" prop="albumName">
        <el-input v-model="queryParams.albumName" placeholder="相册名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="公开状态" prop="isPublic">
        <el-select v-model="queryParams.isPublic" placeholder="全部" clearable style="width: 140px">
          <el-option label="公开" :value="1" />
          <el-option label="私有" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['album:album:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['album:album:remove']">删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="albumList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="ID" prop="albumId" width="80" align="center" />
      <el-table-column label="相册名称" prop="albumName" :show-overflow-tooltip="true" />
      <el-table-column label="照片数" prop="photoCount" width="90" align="center" />
      <el-table-column label="公开" prop="isPublic" width="90" align="center">
        <template #default="scope">
          <el-tag :type="scope.row.isPublic === 1 ? 'success' : 'info'">{{ scope.row.isPublic === 1 ? '公开' : '私有' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="地点概要" prop="locationSummary" :show-overflow-tooltip="true" />
      <el-table-column label="排序" prop="sortOrder" width="80" align="center" />
      <el-table-column label="操作" width="220" align="center">
        <template #default="scope">
          <el-button link type="primary" @click="handleUpdate(scope.row)" v-hasPermi="['album:album:edit']">修改</el-button>
          <el-button link type="primary" @click="handleRefresh(scope.row)" v-hasPermi="['album:album:edit']">刷新统计</el-button>
          <el-button link type="danger" @click="handleDelete(scope.row)" v-hasPermi="['album:album:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog :title="title" v-model="open" width="560px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="相册名称" prop="albumName">
          <el-input v-model="form.albumName" placeholder="请输入相册名称" />
        </el-form-item>
        <el-form-item label="描述" prop="albumDesc">
          <el-input v-model="form.albumDesc" type="textarea" />
        </el-form-item>
        <el-form-item label="封面URL" prop="coverUrl">
          <el-input v-model="form.coverUrl" placeholder="可选" />
        </el-form-item>
        <el-form-item label="公开状态" prop="isPublic">
          <el-radio-group v-model="form.isPublic">
            <el-radio :value="1">公开</el-radio>
            <el-radio :value="0">私有</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="form.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="open = false">取 消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="AlbumAlbum">
import { listAlbum, addAlbum, updateAlbum, delAlbum, refreshAlbumStats } from '@/api/album/album'

const { proxy } = getCurrentInstance()
const albumList = ref([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const multiple = ref(true)
const total = ref(0)
const title = ref('')

const data = reactive({
  form: {},
  queryParams: { pageNum: 1, pageSize: 10, albumName: undefined, isPublic: undefined },
  rules: { albumName: [{ required: true, message: '相册名称不能为空', trigger: 'blur' }] }
})
const { queryParams, form, rules } = toRefs(data)

function getList() {
  loading.value = true
  listAlbum(queryParams.value).then(res => {
    albumList.value = res.rows
    total.value = res.total
    loading.value = false
  })
}
function handleQuery() { queryParams.value.pageNum = 1; getList() }
function resetQuery() { proxy.resetForm('queryRef'); handleQuery() }
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.albumId)
  multiple.value = !selection.length
}
function reset() {
  form.value = { albumId: undefined, albumName: undefined, albumDesc: undefined, coverUrl: undefined, isPublic: 1, sortOrder: 0, remark: undefined }
  proxy.resetForm('formRef')
}
function handleAdd() { reset(); open.value = true; title.value = '新增相册' }
function handleUpdate(row) {
  reset()
  form.value = { ...row }
  open.value = true
  title.value = '修改相册'
}
function submitForm() {
  proxy.$refs['formRef'].validate(valid => {
    if (!valid) return
    const req = form.value.albumId ? updateAlbum(form.value) : addAlbum(form.value)
    req.then(() => {
      proxy.$modal.msgSuccess('操作成功')
      open.value = false
      getList()
    })
  })
}
function handleDelete(row) {
  const albumIds = row.albumId || ids.value
  proxy.$modal.confirm('确认删除选中相册？').then(() => delAlbum(albumIds)).then(() => {
    getList()
    proxy.$modal.msgSuccess('删除成功')
  }).catch(() => {})
}
function handleRefresh(row) {
  refreshAlbumStats(row.albumId).then(() => {
    proxy.$modal.msgSuccess('统计已刷新')
    getList()
  })
}
getList()
</script>
