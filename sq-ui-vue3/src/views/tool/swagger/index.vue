<template>
  <el-card class="swagger-launcher">
    <el-result icon="info" title="接口文档" sub-title="该页面已改为新页签打开。如未自动弹出，请点击下方按钮。">
      <template #extra>
        <el-button type="primary" @click="openDoc">打开接口文档</el-button>
      </template>
    </el-result>
  </el-card>
</template>

<script setup>
const url = import.meta.env.VITE_APP_BASE_API + '/doc.html'

function openDoc() {
  window.open(url, '_blank', 'noopener,noreferrer')
}
</script>

<style scoped>
.swagger-launcher {
  margin: 20px;
}
</style>
