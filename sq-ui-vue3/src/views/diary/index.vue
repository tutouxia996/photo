<template>
  <div class="diary-page">
    <header class="diary-header">
      <button type="button" class="back-btn" title="返回" @click="goBack">
        <el-icon :size="20"><ArrowLeft /></el-icon>
      </button>
    </header>

    <div class="diary-body">
      <HobonichiBook
        :pages="pages"
        cover-title="相册日记"
        cover-sub="HOBONICHI STYLE"
        :year="year"
      />
    </div>
  </div>
</template>

<script setup>
import { ArrowLeft } from '@element-plus/icons-vue'
import HobonichiBook from '@/components/HobonichiBook/index.vue'

defineOptions({ name: 'AlbumDiary' })

const { proxy } = getCurrentInstance()

const year = new Date().getFullYear()

/** 默认一千页（每两项合成一张可翻叶） */
const pages = Array.from({ length: 1000 }, (_, i) => ({ id: i + 1 }))

function goBack() {
  proxy.$tab.navigatePage({ path: '/photos/index' })
}

function paintMain(bg) {
  const el = document.querySelector('.app-main')
  if (el) el.style.background = bg
}

onMounted(() => paintMain('#ebe6dc'))
onUnmounted(() => paintMain(''))
</script>

<style scoped lang="scss">
.diary-page {
  display: flex;
  flex-direction: column;
  margin: -20px;
  width: calc(100% + 40px);
  height: calc(100vh - 84px + 40px);
  min-height: 560px;
  background: #ebe6dc;
  overflow: hidden;
}

.diary-header {
  flex: none;
  display: flex;
  align-items: center;
  /* clear fixed navbar / tags-view overlap */
  padding: 22px 16px 4px;
  z-index: 2;
}

.back-btn {
  width: 34px;
  height: 34px;
  border: 0;
  border-radius: 10px;
  background: rgba(255, 252, 245, 0.65);
  color: #3e3426;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  &:hover {
    background: rgba(255, 252, 245, 0.95);
  }
}

.diary-body {
  flex: 1;
  min-height: 0;
  position: relative;
}

@media (max-width: 768px) {
  .diary-page {
    margin: -15px;
    width: calc(100% + 30px);
    height: calc(100vh - 50px + 30px);
  }

  .diary-header {
    padding: 18px 14px 4px;
  }
}
</style>
