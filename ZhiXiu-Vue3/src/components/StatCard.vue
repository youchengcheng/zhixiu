<script setup>
import { computed } from 'vue'
import { ArrowUp, ArrowDown, Minus } from '@element-plus/icons-vue'
import { iconOf } from '@/utils/menu-icons'
import { formatNumber } from '@/utils/format'

// 指标卡：管理端看板与师傅统计复用；dark 变体供看板顶部深色 KPI 区使用
const props = defineProps({
  label: { type: String, default: '' },
  value: { type: [Number, String], default: '' },
  unit: { type: String, default: '' },
  /** 变化率（数字，按百分比处理）；null 表示不展示变化 */
  delta: { type: [Number, String], default: null },
  deltaLabel: { type: String, default: '较上周' },
  /** 该指标变大是否是好消息；「超时率」这类应传 false，决定箭头与颜色 */
  positiveIsGood: { type: Boolean, default: true },
  /** 图标名，取自 utils/menu-icons.js，留空则不显示图标 */
  icon: { type: String, default: '' },
  /** 图标轨道：default / primary / ai / success / warning / danger */
  tone: { type: String, default: 'default' },
  hint: { type: String, default: '' },
  /** 深色 KPI 面板：文字走 --kpi-* 令牌，保证白字对比度 */
  dark: { type: Boolean, default: false },
  loading: { type: Boolean, default: false }
})

const iconComponent = computed(() => (props.icon ? iconOf(props.icon) : null))

const valueText = computed(() =>
  typeof props.value === 'number' ? formatNumber(props.value) : String(props.value ?? '-')
)

const hasDelta = computed(() => props.delta !== null && props.delta !== '' && props.delta !== undefined)
const deltaNumber = computed(() => Number(props.delta))
const deltaText = computed(() => {
  const num = deltaNumber.value
  const sign = num > 0 ? '+' : ''
  return `${sign}${Number.isNaN(num) ? props.delta : num}%`
})

/** 趋势方向：决定箭头图标与配色 */
const trend = computed(() => {
  if (!hasDelta.value || Number.isNaN(deltaNumber.value)) return 'flat'
  if (deltaNumber.value > 0) return 'up'
  if (deltaNumber.value < 0) return 'down'
  return 'flat'
})

/** 变化是否为利好：利好走 success，利空走 danger，持平走中性 */
const trendTone = computed(() => {
  if (trend.value === 'flat') return 'neutral'
  const isUp = trend.value === 'up'
  const good = isUp === props.positiveIsGood
  return good ? 'success' : 'danger'
})

const trendIcon = computed(() => {
  if (trend.value === 'up') return ArrowUp
  if (trend.value === 'down') return ArrowDown
  return Minus
})
</script>

<template>
  <div class="stat-card" :class="[`tone-${props.tone}`, { 'is-dark': props.dark }]">
    <div class="stat-card__head">
      <span class="stat-card__label">{{ props.label }}</span>
      <span v-if="iconComponent" class="stat-card__icon" aria-hidden="true">
        <el-icon><component :is="iconComponent" /></el-icon>
      </span>
    </div>

    <div class="stat-card__value-row">
      <!-- loading 用等高骨架占位，避免数据到达时高度跳动 -->
      <span v-if="props.loading" class="skeleton stat-card__value-skeleton" />
      <template v-else>
        <span class="stat-card__value text-mono">{{ valueText }}</span>
        <span v-if="props.unit" class="stat-card__unit">{{ props.unit }}</span>
      </template>
    </div>

    <div class="stat-card__foot">
      <span v-if="hasDelta" class="stat-card__trend" :class="`trend-${trendTone}`">
        <el-icon><component :is="trendIcon" /></el-icon>
        <span class="text-mono">{{ deltaText }}</span>
        <span class="stat-card__trend-label">{{ props.deltaLabel }}</span>
      </span>
      <span v-if="props.hint" class="stat-card__hint">{{ props.hint }}</span>
    </div>
  </div>
</template>

<style scoped>
.stat-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-5);
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-sm);
}

/* 深色 KPI 面板：看板顶部区域，白字对比度达标 */
.stat-card.is-dark {
  background: var(--kpi-panel-bg);
  border-color: var(--kpi-panel-border);
  box-shadow: none;
}

.stat-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.stat-card__label {
  font-size: var(--fs-sm);
  color: var(--color-text-2);
}

.stat-card.is-dark .stat-card__label {
  color: var(--kpi-text-2);
}

.stat-card__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 19px;
}

.tone-default .stat-card__icon {
  color: var(--color-text-2);
}

.tone-primary .stat-card__icon {
  color: var(--color-primary);
}

.tone-ai .stat-card__icon {
  color: var(--color-ai-strong);
}

.tone-success .stat-card__icon {
  color: var(--color-success-strong);
}

.tone-warning .stat-card__icon {
  color: var(--color-warning-strong);
}

.tone-danger .stat-card__icon {
  color: var(--color-danger-strong);
}

.stat-card__value-row {
  display: flex;
  align-items: baseline;
  gap: var(--space-1);
  min-height: 34px;
}

.stat-card__value {
  font-size: var(--fs-display);
  font-weight: var(--fw-semibold);
  line-height: var(--lh-tight);
  color: var(--color-text-1);
}

.stat-card.is-dark .stat-card__value {
  color: var(--kpi-text-1);
}

.stat-card__value-skeleton {
  display: block;
  width: 96px;
  height: 28px;
}

.stat-card__unit {
  font-size: var(--fs-sm);
  color: var(--color-text-3);
}

.stat-card.is-dark .stat-card__unit {
  color: var(--kpi-text-2);
}

.stat-card__foot {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  font-size: var(--fs-xs);
  min-height: 18px;
}

.stat-card__trend {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  font-weight: var(--fw-medium);
}

.trend-success {
  color: var(--color-success-strong);
}

.trend-danger {
  color: var(--color-danger-strong);
}

.trend-neutral {
  color: var(--color-text-3);
}

.stat-card.is-dark .trend-neutral,
.stat-card.is-dark .stat-card__trend-label,
.stat-card.is-dark .stat-card__hint {
  color: var(--kpi-text-2);
}

.stat-card__trend-label,
.stat-card__hint {
  color: var(--color-text-3);
}
</style>
