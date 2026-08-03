<template>
  <component :is="type" v-bind="linkProps()">
    <slot />
  </component>
</template>

<script setup>
import { isExternal } from '@/utils/validate'

const props = defineProps({
  to: {
    type: [String, Object],
    required: true
  }
})

// 站内绝对路径直接打开新页签的菜单（无需进入路由）
const NEW_TAB_PATHS = {
  '/tool/swagger': import.meta.env.VITE_APP_BASE_API + '/doc.html',
  '/monitor/druid': import.meta.env.VITE_APP_BASE_API + '/druid/login.html'
}

const toPath = computed(() => {
  return typeof props.to === 'string' ? props.to : props.to?.path
})

const newTabHref = computed(() => {
  return NEW_TAB_PATHS[toPath.value]
})

const isExt = computed(() => {
  return isExternal(props.to)
})

const type = computed(() => {
  if (isExt.value || newTabHref.value) {
    return 'a'
  }
  return 'router-link'
})

function linkProps() {
  if (newTabHref.value) {
    return {
      href: newTabHref.value,
      target: '_blank',
      rel: 'noopener'
    }
  }
  if (isExt.value) {
    return {
      href: props.to,
      target: '_blank',
      rel: 'noopener'
    }
  }
  return {
    to: props.to
  }
}
</script>
