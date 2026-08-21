<template>
  <div class="app-container">
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['album:scan:add']">新增目录</el-button>
      </el-col>
    </el-row>

    <el-table v-loading="loading" :data="pathList">
      <el-table-column label="ID" prop="pathId" width="70" />
      <el-table-column label="名称" prop="pathName" width="140" />
      <el-table-column label="本地路径" prop="localPath" :show-overflow-tooltip="true" />
      <el-table-column label="相册ID" prop="defaultAlbumId" width="90" />
      <el-table-column label="Cron" prop="scanCron" width="120" />
      <el-table-column label="上次扫描" prop="lastScanTime" width="170" />
      <el-table-column label="状态" prop="status" width="80">
        <template #default="scope">{{ scope.row.status === 1 ? '启用' : '禁用' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="360" fixed="right">
        <template #default="scope">
          <el-button link type="primary" @click="handleUpdate(scope.row)" v-hasPermi="['album:scan:edit']">修改</el-button>
          <el-button
            link
            type="primary"
            :disabled="scanning || repairing"
            @click="handleRun(scope.row, false)"
            v-hasPermi="['album:scan:run']"
          >增量扫描</el-button>
          <el-button
            link
            type="warning"
            :disabled="scanning || repairing"
            @click="handleRun(scope.row, true)"
            v-hasPermi="['album:scan:run']"
          >全量扫描</el-button>
          <el-button
            link
            type="success"
            :disabled="scanning || repairing"
            @click="handleRepairThumbs(scope.row)"
            v-hasPermi="['album:scan:run']"
          >补视频缩略图</el-button>
          <el-button link type="danger" @click="handleDelete(scope.row)" v-hasPermi="['album:scan:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-divider content-position="left">扫描记录</el-divider>
    <el-table :data="logList" size="small">
      <el-table-column label="ID" prop="logId" width="70" />
      <el-table-column label="目录" prop="pathId" width="70" />
      <el-table-column label="类型" width="80">
        <template #default="scope">{{ scope.row.scanType === 2 ? '全量' : '增量' }}</template>
      </el-table-column>
      <el-table-column label="总数" prop="totalCount" width="70" />
      <el-table-column label="新增" prop="newCount" width="70" />
      <el-table-column label="跳过" prop="skipCount" width="70" />
      <el-table-column label="失败" prop="failCount" width="70" />
      <el-table-column label="状态" width="80">
        <template #default="scope">{{ statusText(scope.row.status) }}</template>
      </el-table-column>
      <el-table-column label="信息" prop="message" :show-overflow-tooltip="true" />
      <el-table-column label="开始时间" prop="startTime" width="170" />
    </el-table>

    <el-dialog :title="title" v-model="open" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="目录名称" prop="pathName"><el-input v-model="form.pathName" /></el-form-item>
        <el-form-item label="本地路径" prop="localPath"><el-input v-model="form.localPath" placeholder="如 D:/photos/2024" /></el-form-item>
        <el-form-item label="绑定相册" prop="defaultAlbumId"><el-input v-model="form.defaultAlbumId" /></el-form-item>
        <el-form-item label="Cron" prop="scanCron"><el-input v-model="form.scanCron" placeholder="可选" /></el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitForm">确定</el-button>
        <el-button @click="open = false">取消</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="scanDialogVisible"
      title="扫描进度"
      width="480px"
      append-to-body
      :close-on-click-modal="false"
      :close-on-press-escape="!scanning"
      :show-close="!scanning"
      :before-close="beforeScanClose"
    >
      <div class="scan-dialog-body">
        <div class="scan-dialog-summary">
          <span>{{ scanProcessedText }}</span>
          <span>{{ scanPercent }}%</span>
        </div>
        <el-progress
          :percentage="scanPercent"
          :stroke-width="12"
          striped
          striped-flow
          :status="scanProgress.status === 2 ? 'exception' : (scanProgress.status === 1 ? 'success' : undefined)"
        />
        <div class="scan-dialog-stats">
          <span>新增 {{ scanProgress.newCount }}</span>
          <span>跳过 {{ scanProgress.skipCount }}</span>
          <span>失败 {{ scanProgress.failCount }}</span>
        </div>
        <div class="scan-dialog-msg" :title="scanProgress.message">{{ scanProgress.message || '准备中…' }}</div>
      </div>
      <template #footer>
        <el-button :disabled="scanning" type="primary" @click="scanDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="AlbumScan">
import { listScanPath, addScanPath, updateScanPath, delScanPath, runScan, listScanLog, pollScanProgress, repairVideoThumbs } from '@/api/album/scan'

const { proxy } = getCurrentInstance()
const pathList = ref([])
const logList = ref([])
const loading = ref(true)
const open = ref(false)
const title = ref('')
const form = ref({})
const scanning = ref(false)
const repairing = ref(false)
const scanDialogVisible = ref(false)
const scanProgress = reactive({
  status: 0,
  totalCount: 0,
  newCount: 0,
  skipCount: 0,
  failCount: 0,
  message: ''
})
const rules = {
  pathName: [{ required: true, message: '名称不能为空', trigger: 'blur' }],
  localPath: [{ required: true, message: '路径不能为空', trigger: 'blur' }],
  defaultAlbumId: [{ required: true, message: '相册不能为空', trigger: 'blur' }]
}

const scanPercent = computed(() => {
  const total = Number(scanProgress.totalCount) || 0
  if (total <= 0) return scanProgress.status === 0 ? 0 : 100
  const processed = (Number(scanProgress.newCount) || 0) + (Number(scanProgress.skipCount) || 0) + (Number(scanProgress.failCount) || 0)
  return Math.min(100, Math.max(0, Math.round((processed / total) * 100)))
})

const scanProcessedText = computed(() => {
  const total = Number(scanProgress.totalCount) || 0
  const processed = (Number(scanProgress.newCount) || 0) + (Number(scanProgress.skipCount) || 0) + (Number(scanProgress.failCount) || 0)
  if (!total && scanProgress.status === 0) return '统计文件中…'
  return `已处理 ${processed}/${total || processed}`
})

function statusText(s) {
  return s === 1 ? '成功' : s === 2 ? '失败' : '进行中'
}

function applyProgress(log) {
  scanProgress.status = Number(log.status ?? 0)
  scanProgress.totalCount = Number(log.totalCount) || 0
  scanProgress.newCount = Number(log.newCount) || 0
  scanProgress.skipCount = Number(log.skipCount) || 0
  scanProgress.failCount = Number(log.failCount) || 0
  scanProgress.message = log.message || ''
}

function beforeScanClose(done) {
  if (scanning.value) {
    proxy.$modal.msgWarning('扫描进行中，请稍候')
    return
  }
  done()
}

function getList() {
  loading.value = true
  listScanPath({ pageNum: 1, pageSize: 50 }).then(res => {
    pathList.value = res.rows
    loading.value = false
  })
  listScanLog({ pageNum: 1, pageSize: 20 }).then(res => { logList.value = res.rows })
}

function reset() {
  form.value = { pathId: undefined, pathName: '', localPath: '', defaultAlbumId: '', scanCron: '', status: 1 }
}

function handleAdd() { reset(); title.value = '新增扫描目录'; open.value = true }
function handleUpdate(row) { form.value = { ...row }; title.value = '修改扫描目录'; open.value = true }

function submitForm() {
  proxy.$refs.formRef.validate(valid => {
    if (!valid) return
    const req = form.value.pathId ? updateScanPath(form.value) : addScanPath(form.value)
    req.then(() => { proxy.$modal.msgSuccess('成功'); open.value = false; getList() })
  })
}

function handleDelete(row) {
  proxy.$modal.confirm('确认删除？').then(() => delScanPath(row.pathId)).then(() => { getList(); proxy.$modal.msgSuccess('删除成功') }).catch(() => {})
}

async function handleRun(row, fullScan) {
  if (scanning.value) {
    proxy.$modal.msgWarning('已有扫描任务进行中')
    return
  }
  scanning.value = true
  scanDialogVisible.value = true
  applyProgress({ status: 0, totalCount: 0, newCount: 0, skipCount: 0, failCount: 0, message: '正在启动扫描…' })
  try {
    const res = await runScan(row.pathId, fullScan)
    const log = res.data || {}
    const logId = log.logId
    if (!logId) {
      proxy.$modal.msgError('未能获取扫描任务')
      return
    }
    applyProgress(log)
    if (Number(log.status) === 0) {
      const finalLog = await pollScanProgress(logId, {
        interval: 800,
        onProgress: applyProgress
      })
      applyProgress(finalLog)
    }
    if (Number(scanProgress.status) === 2) {
      proxy.$modal.msgError(scanProgress.message || '扫描失败')
    } else {
      proxy.$modal.msgSuccess(`扫描完成，新增 ${scanProgress.newCount}，跳过 ${scanProgress.skipCount}`)
    }
    getList()
  } catch (e) {
    // 全局拦截器已提示
  } finally {
    scanning.value = false
  }
}

async function handleRepairThumbs(row) {
  if (repairing.value || scanning.value) {
    proxy.$modal.msgWarning('请等待当前任务完成')
    return
  }
  let force = false
  try {
    await proxy.$modal.confirm(
      `为「${row.pathName || row.pathId}」补齐缺少封面的视频缩略图？\n` +
      `大疆 Action 等大视频可能较慢。\n\n` +
      `若提示「全部跳过」但地图仍是灰底「视频」，请再点一次并在下一框选择强制重截。`
    )
  } catch (e) {
    return
  }
  try {
    await proxy.$modal.confirm('是否强制重截已有封面？（Action 5 Pro 等 HEVC 建议选「确定」重截；已有封面正常则选「取消」只补缺失）')
    force = true
  } catch (e) {
    force = false
  }
  repairing.value = true
  try {
    const res = await repairVideoThumbs(row.pathId, force)
    const data = res.data || {}
    const repaired = Number(data.repaired) || 0
    const failed = Number(data.failed) || 0
    const skipped = Number(data.skipped) || 0
    const samples = Array.isArray(data.samples) ? data.samples : []
    let msg = `补齐完成：成功 ${repaired}，跳过 ${skipped}，失败 ${failed}`
    if (data.hint) {
      msg += `。${data.hint}`
    } else if (samples.length) {
      msg += `。示例：${samples.slice(0, 3).join('；')}`
    } else if (repaired > 0) {
      msg += '。请强制刷新首页/地图查看封面。'
    }
    if (failed > 0 && repaired === 0) {
      proxy.$modal.msgError(msg)
    } else {
      proxy.$modal.msgSuccess(msg)
    }
  } catch (e) {
    // 全局拦截器已提示
  } finally {
    repairing.value = false
  }
}

getList()
</script>

<style scoped>
.scan-dialog-body {
  padding: 4px 0 8px;
}

.scan-dialog-summary {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 13px;
  color: #666;
}

.scan-dialog-stats {
  display: flex;
  gap: 16px;
  margin-top: 14px;
  font-size: 12px;
  color: #888;
}

.scan-dialog-msg {
  margin-top: 12px;
  font-size: 13px;
  color: #333;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
