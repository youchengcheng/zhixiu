<script setup>
import { Warning } from '@element-plus/icons-vue'
import StatusTag from './StatusTag.vue'
import { URGENCY } from '@/utils/dict'

// 紧急程度 4 级；安全隐患（涉电/涉水/涉气/高空）用软底 + 图标，不用状态圆点
const props = defineProps({
  urgency: { type: [Number, String], default: 2 },
  /** 安全隐患标识，取值见 dict.js 的 SAFETY_FLAGS */
  safety: { type: String, default: '' },
  size: { type: String, default: 'default' }
})
</script>

<template>
  <span class="urgency-tag">
    <StatusTag :status="props.urgency" :dict="URGENCY" :size="props.size" />
    <span v-if="props.safety" class="urgency-tag__safety">
      <el-icon><Warning /></el-icon>
      <span>{{ props.safety }}</span>
    </span>
  </span>
</template>

<style scoped>
.urgency-tag {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
}

.urgency-tag__safety {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  padding: 2px 8px;
  border-radius: var(--radius-full);
  background: var(--color-warning-soft);
  color: var(--color-warning-strong);
  font-size: var(--fs-xs);
  font-weight: var(--fw-medium);
  line-height: 18px;
  white-space: nowrap;
}

.urgency-tag__safety .el-icon {
  font-size: 13px;
}
</style>
