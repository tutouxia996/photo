<template>
  <div ref="containerRef" class="photo-sphere-host" />
</template>

<script setup>
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { Viewer } from '@photo-sphere-viewer/core'
import '@photo-sphere-viewer/core/index.css'

const props = defineProps({
  src: {
    type: String,
    default: ''
  }
})

const containerRef = ref(null)
let viewer = null
let mountToken = 0

function destroyViewer() {
  if (viewer) {
    try {
      viewer.destroy()
    } catch (_) {
      /* ignore */
    }
    viewer = null
  }
}

async function mountViewer(url) {
  const token = ++mountToken
  destroyViewer()
  if (!url) return
  await nextTick()
  if (token !== mountToken || !containerRef.value) return
  viewer = new Viewer({
    container: containerRef.value,
    panorama: url,
    navbar: ['zoom', 'move', 'fullscreen'],
    defaultZoomLvl: 50,
    mousewheel: true,
    mousemove: true,
    touchmoveTwoFingers: false,
    loadingTxt: '加载全景…',
    lang: {
      zoom: '缩放',
      move: '拖动',
      fullscreen: '全屏',
      loadError: '全景加载失败'
    }
  })
}

watch(
  () => props.src,
  (url) => {
    mountViewer(url)
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  mountToken += 1
  destroyViewer()
})
</script>

<style scoped>
.photo-sphere-host {
  width: 100%;
  height: 100%;
  min-height: 240px;
  background: #111;
}
</style>
