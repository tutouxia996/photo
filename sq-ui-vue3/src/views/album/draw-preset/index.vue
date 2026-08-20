<template>
  <div class="app-container">
    <el-form :model="queryParams" :inline="true" v-show="showSearch">
      <el-form-item label="风格名称" prop="label">
        <el-input v-model="queryParams.label" placeholder="风格名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="标识" prop="presetKey">
        <el-input v-model="queryParams.presetKey" placeholder="preset key" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="状态" prop="enabled">
        <el-select v-model="queryParams.enabled" placeholder="全部" clearable style="width: 120px">
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['album:drawPreset:add']">手动新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Upload" @click="openImport" v-hasPermi="['album:drawPreset:add']">导入 Skill</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['album:drawPreset:remove']">删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="presetList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" :selectable="row => row.source !== 'builtin'" />
      <el-table-column label="排序" prop="sortOrder" width="70" align="center" />
      <el-table-column label="名称" prop="label" min-width="140" :show-overflow-tooltip="true" />
      <el-table-column label="标识" prop="presetKey" width="160" :show-overflow-tooltip="true" />
      <el-table-column label="布局" prop="layout" width="170" :show-overflow-tooltip="true" />
      <el-table-column label="尺寸" prop="panelSize" width="110" align="center" />
      <el-table-column label="来源" prop="source" width="110" align="center">
        <template #default="scope">
          <el-tag v-if="scope.row.source === 'builtin'" type="info">内置</el-tag>
          <el-tag v-else-if="scope.row.source === 'skill_import'" type="success">Skill</el-tag>
          <el-tag v-else>手动</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="启用" prop="enabled" width="80" align="center">
        <template #default="scope">
          <el-tag :type="scope.row.enabled === 1 ? 'success' : 'info'">{{ scope.row.enabled === 1 ? '是' : '否' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="风格描述" prop="panelPrompt" min-width="220" :show-overflow-tooltip="true" />
      <el-table-column label="操作" width="160" align="center" fixed="right">
        <template #default="scope">
          <el-button link type="primary" @click="handleUpdate(scope.row)" v-hasPermi="['album:drawPreset:edit']">修改</el-button>
          <el-button
            v-if="scope.row.source !== 'builtin'"
            link
            type="danger"
            @click="handleDelete(scope.row)"
            v-hasPermi="['album:drawPreset:remove']"
          >删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog :title="title" v-model="open" width="720px" append-to-body destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="风格名称" prop="label">
          <el-input v-model="form.label" placeholder="如：水彩旅行插画" maxlength="100" />
        </el-form-item>
        <el-form-item label="标识" prop="presetKey">
          <el-input v-model="form.presetKey" placeholder="小写字母/数字/连字符，如 watercolor-travel" maxlength="63" :disabled="!!form.presetId && form.source === 'builtin'" />
        </el-form-item>
        <el-form-item label="风格描述" prop="panelPrompt">
          <el-input v-model="form.panelPrompt" type="textarea" :rows="8" placeholder="英文或中文均可；将作为万相图生图 prompt" />
        </el-form-item>
        <el-row>
          <el-col :span="12">
            <el-form-item label="出图尺寸" prop="panelSize">
              <el-select v-model="form.panelSize" style="width: 100%">
                <el-option label="960×1280（竖版）" value="960*1280" />
                <el-option label="960×640（横版面板）" value="960*640" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="拼版布局" prop="layout">
              <el-select v-model="form.layout" style="width: 100%" :disabled="true">
                <el-option label="整页艺术 FULL_CANVAS" value="FULL_CANVAS" />
                <el-option label="整页+标题 FULL_WITH_TITLES" value="FULL_WITH_TITLES" />
                <el-option label="上图下照 TOP_PANEL_BOTTOM_PHOTO" value="TOP_PANEL_BOTTOM_PHOTO" />
                <el-option label="上照下图 TOP_PHOTO_BOTTOM_PANEL" value="TOP_PHOTO_BOTTOM_PANEL" />
                <el-option label="左照右图 LEFT_PHOTO_RIGHT_PANEL" value="LEFT_PHOTO_RIGHT_PANEL" />
                <el-option label="极简 Zine MINIMAL_ZINE" value="MINIMAL_ZINE" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="排序" prop="sortOrder">
              <el-input-number v-model="form.sortOrder" :min="0" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="启用" prop="enabled">
              <el-radio-group v-model="form.enabled">
                <el-radio :value="1">启用</el-radio>
                <el-radio :value="0">禁用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item v-if="form.skillRaw" label="Skill 原文">
          <el-input v-model="form.skillRaw" type="textarea" :rows="4" readonly />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="open = false">取 消</el-button>
      </template>
    </el-dialog>

    <el-dialog title="导入 Skill" v-model="importOpen" width="640px" append-to-body destroy-on-close>
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px"
        title="支持 Cursor/Claude 的 SKILL.md（YAML frontmatter），或纯文本 / Markdown 风格描述。" />
      <el-form label-width="90px">
        <el-form-item label="上传文件">
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :limit="1"
            accept=".md,.txt,.markdown"
            :on-change="onSkillFileChange"
            :on-remove="() => { skillFile = null }"
          >
            <el-button type="primary" plain>选择 .md / .txt</el-button>
          </el-upload>
        </el-form-item>
        <el-form-item label="或粘贴">
          <el-input v-model="skillText" type="textarea" :rows="10" placeholder="粘贴 SKILL.md 或风格描述全文" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" :loading="importing" @click="parseAndFill">解析并填入表单</el-button>
        <el-button @click="importOpen = false">取 消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="AlbumDrawPreset">
import { listDrawPreset, addDrawPreset, updateDrawPreset, delDrawPreset, parseDrawSkill } from '@/api/album/drawPreset'

const { proxy } = getCurrentInstance()
const presetList = ref([])
const open = ref(false)
const importOpen = ref(false)
const importing = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const multiple = ref(true)
const total = ref(0)
const title = ref('')
const skillText = ref('')
const skillFile = ref(null)

const data = reactive({
  form: {},
  queryParams: { pageNum: 1, pageSize: 10, label: undefined, presetKey: undefined, enabled: undefined },
  rules: {
    label: [{ required: true, message: '风格名称不能为空', trigger: 'blur' }],
    panelPrompt: [{ required: true, message: '风格描述不能为空', trigger: 'blur' }],
    presetKey: [{ required: true, message: '标识不能为空', trigger: 'blur' }]
  }
})
const { queryParams, form, rules } = toRefs(data)

function getList() {
  loading.value = true
  listDrawPreset(queryParams.value).then(res => {
    presetList.value = res.rows
    total.value = res.total
    loading.value = false
  }).catch(() => { loading.value = false })
}
function handleQuery() { queryParams.value.pageNum = 1; getList() }
function resetQuery() {
  queryParams.value = { pageNum: 1, pageSize: 10, label: undefined, presetKey: undefined, enabled: undefined }
  handleQuery()
}
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.presetId)
  multiple.value = !selection.length
}
function reset() {
  form.value = {
    presetId: undefined,
    presetKey: undefined,
    label: undefined,
    panelPrompt: undefined,
    panelSize: '960*1280',
    layout: 'FULL_CANVAS',
    gradePhoto: 1,
    sortOrder: 100,
    enabled: 1,
    source: 'manual',
    skillRaw: undefined,
    remark: undefined
  }
  proxy.resetForm('formRef')
}
function handleAdd() {
  reset()
  open.value = true
  title.value = '新增预设风格'
}
function handleUpdate(row) {
  reset()
  form.value = { ...row }
  open.value = true
  title.value = '修改预设风格'
}
function submitForm() {
  proxy.$refs['formRef'].validate(valid => {
    if (!valid) return
    const req = form.value.presetId ? updateDrawPreset(form.value) : addDrawPreset(form.value)
    req.then(() => {
      proxy.$modal.msgSuccess('操作成功')
      open.value = false
      getList()
    })
  })
}
function handleDelete(row) {
  if (row?.source === 'builtin') {
    proxy.$modal.msgWarning('内置预设不可删除')
    return
  }
  const presetIds = row?.presetId || ids.value
  proxy.$modal.confirm('确认删除选中预设？删除后出图将无法再选择该风格。').then(() => delDrawPreset(presetIds)).then(() => {
    getList()
    proxy.$modal.msgSuccess('删除成功')
  }).catch(() => {})
}
function openImport() {
  skillText.value = ''
  skillFile.value = null
  importOpen.value = true
}
function onSkillFileChange(file) {
  skillFile.value = file?.raw || null
}
async function parseAndFill() {
  if (!skillFile.value && !skillText.value?.trim()) {
    proxy.$modal.msgWarning('请上传文件或粘贴 Skill 文本')
    return
  }
  importing.value = true
  try {
    const fd = new FormData()
    if (skillFile.value) {
      fd.append('file', skillFile.value)
    }
    if (skillText.value?.trim()) {
      fd.append('text', skillText.value)
    }
    const res = await parseDrawSkill(fd)
    const data = res.data || {}
    reset()
    form.value.label = data.label
    form.value.presetKey = data.presetKey
    form.value.panelPrompt = data.panelPrompt
    form.value.panelSize = data.panelSize || '960*1280'
    form.value.layout = 'FULL_CANVAS'
    form.value.source = 'skill_import'
    form.value.skillRaw = data.skillRaw
    importOpen.value = false
    open.value = true
    title.value = '导入 Skill 并保存'
  } catch (e) {
    /* request 已提示 */
  } finally {
    importing.value = false
  }
}
getList()
</script>
