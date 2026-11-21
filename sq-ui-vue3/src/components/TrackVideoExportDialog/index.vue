<template>
  <el-dialog
    v-model="visible"
    title="导出轨迹视频"
    width="460px"
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
    @closed="onClosed"
  >
    <el-form label-width="88px" class="export-form">
      <el-form-item label="帧率">
        <el-radio-group v-model="form.fps">
          <el-radio :label="29.97">29.97 fps</el-radio>
          <el-radio :label="59.94">59.94 fps</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="地图视角">
        <el-radio-group v-model="form.viewMode">
          <el-radio label="2d">二维</el-radio>
          <el-radio label="3d">3D</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="底图样式">
        <el-radio-group v-model="form.style">
          <el-radio label="normal">标准</el-radio>
          <el-radio label="satellite">卫星</el-radio>
          <el-radio label="hybrid">混合</el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>
    <p class="export-tip">导出时仅保留跟拍主体与渐显轨迹；二维为俯视，3D 为倾斜跟拍视角。</p>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="confirming" @click="confirm">开始导出</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'confirm'])

const visible = ref(false)
const confirming = ref(false)
const form = reactive({
  fps: 29.97,
  viewMode: '3d',
  style: 'hybrid'
})

watch(
  () => props.modelValue,
  (v) => {
    visible.value = !!v
    if (v) confirming.value = false
  },
  { immediate: true }
)

watch(visible, (v) => {
  if (v !== props.modelValue) emit('update:modelValue', v)
})

function onClosed() {
  confirming.value = false
  emit('update:modelValue', false)
}

function confirm() {
  confirming.value = true
  emit('confirm', {
    fps: form.fps === 59.94 ? 59.94 : 29.97,
    viewMode: form.viewMode === '2d' ? '2d' : '3d',
    style: ['normal', 'satellite', 'hybrid'].includes(form.style) ? form.style : 'hybrid'
  })
}

/** 父级导出结束后关闭并复位 */
function finish() {
  confirming.value = false
  visible.value = false
}

defineExpose({ finish })
</script>

<style scoped>
.export-form :deep(.el-form-item) {
  margin-bottom: 16px;
}

.export-form :deep(.el-radio) {
  margin-right: 14px;
}

.export-tip {
  margin: 0;
  padding: 0 2px;
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
}
</style>
