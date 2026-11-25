<template>
  <el-dialog
    :model-value="modelValue"
    title="编辑火车票"
    width="920px"
    class="train-ticket-editor"
    destroy-on-close
    append-to-body
    top="4vh"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="editor-preview-wrap">
      <div class="editor-preview">
        <TrainTicketCard
          :data="form"
          :style-config="style"
          :face="previewBack ? 'back' : 'front'"
          :width-px="480"
          flat
        />
      </div>
      <div class="preview-actions">
        <el-button size="small" @click="previewBack = !previewBack">
          {{ previewBack ? '看正面' : '看背面' }}
        </el-button>
        <span class="hint">渲染引擎：FoskyM train-ticket-generator（AGPL-3.0）</span>
      </div>
    </div>

    <el-form label-position="top" class="editor-form" @submit.prevent>
      <div class="form-grid">
        <el-form-item label="火车票 ID">
          <el-input v-model="form.id" maxlength="21" />
        </el-form-item>
        <el-form-item label="红色 ID">
          <el-input v-model="form.redId" maxlength="11" />
        </el-form-item>
        <el-form-item label="售票点">
          <el-input v-model="form.ticketOffice" />
        </el-form-item>
        <el-form-item label="检票口">
          <el-input v-model="form.checkGate" maxlength="12" />
        </el-form-item>
        <el-form-item label="出发地">
          <el-input v-model="form.startStation" maxlength="5" />
        </el-form-item>
        <el-form-item label="目的地">
          <el-input v-model="form.endStation" maxlength="5" />
        </el-form-item>
        <el-form-item label="车次">
          <el-input v-model="form.trainNumber" maxlength="6" />
        </el-form-item>
        <el-form-item label="座位类型">
          <el-select v-model="form.seatType" style="width: 100%">
            <el-option v-for="s in seatTypes" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker
            v-model="form.date"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="时间">
          <el-time-picker
            v-model="form.time"
            value-format="HH:mm"
            format="HH:mm"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="价格">
          <el-input-number v-model="form.price" :min="0" :max="50000" :step="0.5" :precision="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="车厢号">
          <el-input v-model="form.seatCarriage" maxlength="2" />
        </el-form-item>
        <el-form-item label="座位号">
          <el-input v-model="form.seatNumber" maxlength="3" />
        </el-form-item>
        <el-form-item label="自定义座位类型">
          <el-input v-model="form.seatTypeCustom" maxlength="12" placeholder="留空则用座位类型" />
        </el-form-item>
        <el-form-item label="乘客姓名">
          <el-input v-model="form.passengerName" maxlength="12" />
        </el-form-item>
        <el-form-item label="身份证号">
          <el-input v-model="form.passengerId" maxlength="18" />
        </el-form-item>
      </div>

      <el-form-item label="身份">
        <el-radio-group v-model="form.identity">
          <el-radio-button v-for="t in identityOptions" :key="t.value" :value="t.value">
            {{ t.label }}
          </el-radio-button>
        </el-radio-group>
      </el-form-item>

      <el-form-item label="其它">
        <el-checkbox
          v-model="form.isDiscount"
          :disabled="form.identity === 'student'"
        >
          优惠
        </el-checkbox>
      </el-form-item>

      <el-form-item label="支付方式">
        <el-radio-group v-model="form.payMethod">
          <el-radio-button value="">无</el-radio-button>
          <el-radio-button v-for="t in payOptions" :key="t.value" :value="t.value">
            {{ t.label }}
          </el-radio-button>
        </el-radio-group>
      </el-form-item>

      <el-divider content-position="left">样式配置</el-divider>

      <div class="style-row">
        <el-checkbox v-model="style.showProtrusions">梯形突出</el-checkbox>
        <el-checkbox v-model="style.wearEffect.enabled">磨损效果</el-checkbox>
        <div v-if="style.wearEffect.enabled" class="style-inline">
          <span>强度</span>
          <el-slider v-model="style.wearEffect.intensity" :min="0" :max="1" :step="0.1" style="width: 140px" />
        </div>
      </div>

      <div class="style-row">
        <div class="style-inline grow">
          <span>背景透明度</span>
          <el-slider v-model="style.backgroundOpacity" :min="0" :max="0.3" :step="0.01" style="width: 160px" />
        </div>
        <div class="style-inline grow">
          <span>二维码内容</span>
          <el-input v-model="style.qrContent" placeholder="默认为项目地址" />
        </div>
      </div>

      <div v-for="(group, gi) in styleFieldGroups" :key="gi" class="font-grid">
        <div v-for="field in group.fields" :key="field.key" class="font-item">
          <label>{{ field.label }}</label>
          <div class="font-controls">
            <el-select
              v-model="style.fonts[field.key].family"
              filterable
              allow-create
              default-first-option
              style="flex: 1"
            >
              <el-option v-for="f in fontFamilies" :key="f" :label="f" :value="f" />
            </el-select>
            <el-input-number
              v-model="style.fonts[field.key].size"
              :min="field.sizeMin"
              :max="field.sizeMax"
              :step="1"
              controls-position="right"
              style="width: 110px"
            />
          </div>
        </div>
      </div>

      <div class="style-footer">
        <el-button size="small" @click="resetStyle">重置默认样式</el-button>
      </div>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" @click="onConfirm">保存到日记</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'
import TrainTicketCard from '@/components/HobonichiBook/TrainTicketCard.vue'
import {
  defaultStyleConfig,
  FONT_FAMILY_OPTIONS,
  IDENTITY_OPTIONS,
  normalizeStyleConfig,
  normalizeTicketData,
  PAY_METHOD_OPTIONS,
  SEAT_TYPE_LIST,
  styleFieldGroups
} from '@/components/HobonichiBook/trainTicket.js'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  data: { type: Object, default: null },
  styleConfig: { type: Object, default: null }
})

const emit = defineEmits(['update:modelValue', 'confirm'])

const seatTypes = SEAT_TYPE_LIST
const identityOptions = IDENTITY_OPTIONS
const payOptions = PAY_METHOD_OPTIONS
const fontFamilies = FONT_FAMILY_OPTIONS

const form = reactive(normalizeTicketData())
const style = reactive(defaultStyleConfig())
const previewBack = ref(false)

watch(
  () => [props.modelValue, props.data, props.styleConfig],
  () => {
    if (!props.modelValue) return
    previewBack.value = false
    Object.assign(form, normalizeTicketData(props.data))
    const next = normalizeStyleConfig(props.styleConfig)
    Object.assign(style, next)
    style.fonts = { ...next.fonts }
    style.wearEffect = { ...next.wearEffect }
  },
  { immediate: true }
)

watch(
  () => form.identity,
  (val) => {
    if (val === 'student') form.isDiscount = true
  }
)

function resetStyle() {
  const next = defaultStyleConfig()
  Object.assign(style, next)
  style.fonts = { ...next.fonts }
  style.wearEffect = { ...next.wearEffect }
}

function onConfirm() {
  emit('confirm', {
    data: normalizeTicketData({ ...form }),
    style: normalizeStyleConfig({
      ...style,
      fonts: { ...style.fonts },
      wearEffect: { ...style.wearEffect }
    })
  })
  emit('update:modelValue', false)
}
</script>

<style scoped lang="scss">
.editor-preview-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  margin: 0 auto 16px;
}

.editor-preview {
  width: min(100%, 480px);
  aspect-ratio: 876 / 539;
  background: #f3f0ea;
  border-radius: 8px;
}

.preview-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.hint {
  font-size: 11px;
  color: #8a8074;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 0 14px;
}

.editor-form :deep(.el-form-item) {
  margin-bottom: 12px;
}

.editor-form :deep(.el-radio-group) {
  flex-wrap: wrap;
  row-gap: 6px;
}

.style-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 16px 20px;
  margin-bottom: 12px;
}

.style-inline {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  span {
    font-size: 12px;
    color: #6b6358;
    white-space: nowrap;
  }
  &.grow {
    flex: 1 1 220px;
  }
}

.font-grid {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 10px 14px;
  margin-bottom: 14px;
  padding-bottom: 12px;
  border-bottom: 1px solid rgba(62, 52, 38, 0.08);
}

.font-item {
  label {
    display: block;
    font-size: 12px;
    color: #6b6358;
    margin-bottom: 4px;
  }
}

.font-controls {
  display: flex;
  gap: 8px;
}

.style-footer {
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 900px) {
  .form-grid,
  .font-grid {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 640px) {
  .form-grid,
  .font-grid {
    grid-template-columns: 1fr;
  }
}
</style>
