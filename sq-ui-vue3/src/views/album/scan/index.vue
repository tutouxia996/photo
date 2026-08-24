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
      <el-table-column label="操作" width="460" fixed="right">
        <template #default="scope">
          <el-button link type="primary" @click="handleUpdate(scope.row)" v-hasPermi="['album:scan:edit']">修改</el-button>
          <el-button
            link
            type="primary"
            :disabled="scanning || repairing || proxying"
            @click="handleRun(scope.row, false)"
            v-hasPermi="['album:scan:run']"
          >增量扫描</el-button>
          <el-button
            link
            type="warning"
            :disabled="scanning || repairing || proxying"
            @click="handleRun(scope.row, true)"
            v-hasPermi="['album:scan:run']"
          >全量扫描</el-button>
          <el-button
            link
            type="success"
            :disabled="scanning || repairing || proxying"
            @click="handleRepairThumbs(scope.row)"
            v-hasPermi="['album:scan:run']"
          >补视频缩略图</el-button>
          <el-button
            link
            type="success"
            :disabled="scanning || repairing || proxying"
            @click="handleGenerateProxies(scope.row)"
            v-hasPermi="['album:scan:run']"
          >一键转码</el-button>
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
  </div>
</template>

<script setup name="AlbumScan">
import { listScanPath, addScanPath, updateScanPath, delScanPath, runScan, listScanLog, repairVideoThumbs, getVideoProxyStats } from '@/api/album/scan'
import useVideoProxyStore from '@/store/modules/videoProxy'
import useDiskScanStore from '@/store/modules/diskScan'

const { proxy } = getCurrentInstance()
const videoProxyStore = useVideoProxyStore()
const diskScanStore = useDiskScanStore()
const pathList = ref([])
const logList = ref([])
const loading = ref(true)
const open = ref(false)
const title = ref('')
const form = ref({})
const scanning = ref(false)
const repairing = ref(false)
const proxying = ref(false)
const rules = {
  pathName: [{ required: true, message: '名称不能为空', trigger: 'blur' }],
  localPath: [{ required: true, message: '路径不能为空', trigger: 'blur' }],
  defaultAlbumId: [{ required: true, message: '相册不能为空', trigger: 'blur' }]
}

function statusText(s) {
  return s === 1 ? '成功' : s === 2 ? '失败' : '进行中'
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
  if (scanning.value || diskScanStore.progress.running) {
    proxy.$modal.msgWarning('已有扫描任务进行中，请查看右下角进度')
    return
  }
  scanning.value = true
  try {
    const res = await runScan(row.pathId, fullScan)
    const log = res.data || {}
    const logId = log.logId
    if (!logId) {
      proxy.$modal.msgError('未能获取扫描任务')
      return
    }
    diskScanStore.track(logId, { pathId: row.pathId, pathName: row.pathName || `ID ${row.pathId}` })
    proxy.$modal.msgSuccess('已开始扫描，进度在右下角，可继续浏览其他页面')
    getList()
  } catch (e) {
    // 全局拦截器已提示
  } finally {
    scanning.value = false
  }
}

async function handleRepairThumbs(row) {
  if (repairing.value || scanning.value || proxying.value) {
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

async function handleGenerateProxies(row) {
  if (repairing.value || scanning.value || proxying.value) {
    proxy.$modal.msgWarning('请等待当前任务完成')
    return
  }
  let stats = null
  proxying.value = true
  proxy.$modal.loading('正在分析视频转码条件，请稍候…')
  try {
    const res = await getVideoProxyStats(row.pathId)
    stats = res.data || {}
  } catch (e) {
    return
  } finally {
    proxy.$modal.closeLoading()
    proxying.value = false
  }
  const total = Number(stats.totalVideos) || 0
  const eligible = Number(stats.eligible) || 0
  const skipped = Number(stats.skipped) || 0
  const reasons = stats.skipReasons || {}
  const reasonText = Object.keys(reasons).length
    ? '\n跳过原因：' + Object.entries(reasons).map(([k, v]) => `${k}=${v}`).join('，')
    : ''
  const sampleText = (stats.ineligibleSamples || []).slice(0, 2)
    .map(s => `· ${s.fileName}（${s.reason}）`)
    .join('\n')
  let force = false
  try {
    await proxy.$modal.confirm(
      `为「${row.pathName || row.pathId}」排队生成视频浏览档？\n\n` +
      `已入库视频 ${total} 个，符合转码条件 ${eligible} 个，不符合 ${skipped} 个。\n` +
      `条件：1080p 及以上 且 ≥30fps` +
      reasonText +
      (sampleText ? `\n\n示例：\n${sampleText}` : '') +
      `\n\n生成 720p30 / 1080p30，输出到 cache/proxy。`
    )
  } catch (e) {
    return
  }
  if (eligible === 0) {
    proxy.$modal.msgWarning(stats.message || '没有符合转码条件的视频，proxy 目录会保持为空')
    return
  }
  try {
    await proxy.$modal.confirm('是否强制重转已有浏览档？（一般选「取消」只补缺失）')
    force = true
  } catch (e) {
    force = false
  }
  proxying.value = true
  proxy.$modal.loading('正在启动后台转码，请稍候…')
  try {
    const p = await videoProxyStore.enqueue(row.pathId, force)
    const totalJobs = Number(p.total) || 0
    const skippedVideos = Number(p.skippedVideos) || 0
    if (totalJobs === 0 && !p.running) {
      proxy.$modal.msgWarning(
        skippedVideos > 0
          ? `没有需要转码的视频（已跳过 ${skippedVideos} 个：需1080p+、≥30fps，或浏览档已存在）`
          : '没有需要转码的视频'
      )
    } else {
      proxy.$modal.msgSuccess('已开始后台转码，可在右下角查看进度')
    }
  } catch (e) {
    // 全局拦截器已提示
  } finally {
    proxy.$modal.closeLoading()
    proxying.value = false
  }
}

getList()

watch(
  () => diskScanStore.progress.status,
  (status, prev) => {
    if (prev === 0 && status !== 0) {
      getList()
      const msg = diskScanStore.progress.message || ''
      if (msg.includes('视频转码')) {
        videoProxyStore.startPolling()
      }
    }
  }
)
</script>

