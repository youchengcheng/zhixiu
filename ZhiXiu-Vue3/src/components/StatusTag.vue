<script setup>
import { computed } from 'vue'
import { ORDER_STATUS, dictText, dictTone } from '@/utils/dict'

// 状态标签：文案与颜色轨道全部取自 utils/dict.js，页面禁止硬编码中文与色值
const props = defineProps({
  /** 状态值，键名对应 dict 中的键 */
  status: { type: [String, Number], default: '' },
  /** 状态字典，默认工单状态 10 态 */
  dict: { type: Object, default: () => ORDER_STATUS },
  /** 字典外的自定义文案（如「已超时」），传入后走 text / tone */
  text: { type: String, default: '' },
  tone: { type: String, default: 'neutral' },
  /** 状态圆点：需要点状指示时保留，纯文字场景可关掉 */
  dot: { type: Boolean, default: true },
  size: { type: String, default: 'default' }
})

const label = computed(() => props.text || dictText(props.dict, props.status))
const visualTone = computed(() => (props.text ? props.tone : dictTone(props.dict, props.status)))
</script>

<template>
  <span class="status-tag" :class="[`tone-${visualTone}`, `is-${size}`]">
    <i v-if="dot" class="status-tag__dot" aria-hidden="true" />
    <span class="status-tag__text">{{ label }}</span>
  </span>
</template>

<style scoped>
.status-tag {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  padding: 2px 8px;
  border-radius: var(--radius-full);
  font-size: var(--fs-xs);
  font-weight: var(--fw-medium);
  line-height: 18px;
  white-space: nowrap;
}

.status-tag__dot {
  width: 6px;
  height: 6px;
  border-radius: var(--radius-full);
  background: currentColor;
}

.status-tag.is-small {
  padding: 0 6px;
  font-size: 11px;
  line-height: 16px;
}

.status-tag.is-large {
  padding: 4px 10px;
  font-size: var(--fs-sm);
  line-height: 20px;
}

/* 轨道：一律「软底 + 深色文字」，保证小字号对比度 ≥4.5:1 */
.tone-neutral {
  background: var(--color-bg-sunken);
  color: var(--color-text-2);
}

.tone-info {
  background: var(--color-info-soft);
  color: var(--color-info);
}

.tone-info-strong {
  background: var(--color-info-soft);
  color: var(--color-info-strong);
}

.tone-primary,
.tone-brand-soft {
  background: var(--color-primary-soft);
  color: var(--color-primary);
}

.tone-warning,
.tone-warning-strong {
  background: var(--color-warning-soft);
  color: var(--color-warning-strong);
}

.tone-success {
  background: var(--color-success-soft);
  color: var(--color-success-strong);
}

.tone-danger {
  background: var(--color-danger-soft);
  color: var(--color-danger-strong);
}

.tone-ai {
  background: var(--color-ai-soft);
  color: var(--color-ai-strong);
}
</style>
