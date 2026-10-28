<script setup>
import { computed } from 'vue'
import { formatDateTime, fromNow } from '@/utils/format'

// 相对时间：列表里更易读，绝对时间放进 tooltip，空值退回占位符
const props = defineProps({
  value: { type: [String, Number, Date], default: '' },
  fallback: { type: String, default: '-' },
  /** 前缀，如「更新于 」 */
  prefix: { type: String, default: '' }
})

const valid = computed(() => props.value !== null && props.value !== undefined && props.value !== '')
const text = computed(() => fromNow(props.value, props.fallback))
const absolute = computed(() => formatDateTime(props.value, props.fallback))
</script>

<template>
  <el-tooltip v-if="valid" :content="absolute" placement="top" :show-after="120">
    <span class="relative-time">{{ props.prefix }}{{ text }}</span>
  </el-tooltip>
  <span v-else class="relative-time">{{ props.fallback }}</span>
</template>

<style scoped>
.relative-time {
  color: inherit;
  font-variant-numeric: tabular-nums;
  cursor: default;
}
</style>
