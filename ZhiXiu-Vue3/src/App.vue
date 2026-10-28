<script setup>
// 全局挂载 Element Plus 中文语言包，统一分页、日期、表格等文案
import zhCn from 'element-plus/es/locale/lang/zh-cn'
</script>

<template>
  <el-config-provider :locale="zhCn">
    <router-view />
  </el-config-provider>
</template>
