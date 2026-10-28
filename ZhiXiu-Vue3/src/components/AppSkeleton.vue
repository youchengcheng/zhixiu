<script setup>
// 骨架屏家族：表格 / 卡片流 / 详情 / 图表四种形态
// 尺寸严格对齐真实布局（表格行 46px、卡片 16:9 封面等），切换时零位移
const props = defineProps({
  /** table 表格 / card 卡片流 / detail 详情 / chart 图表 */
  variant: { type: String, default: 'table' },
  /** 重复行数：表格行 / 卡片张数 / 详情字段行 */
  rows: { type: Number, default: 6 },
  /** 卡片流列数（仅 card 生效） */
  columns: { type: Number, default: 3 },
  /** 图表区高度（仅 chart 生效） */
  height: { type: Number, default: 260 },
  /** 屏幕阅读器提示语 */
  label: { type: String, default: '加载中' }
})

// 表格列宽：模拟「编号 / 标题 / 状态 / 时间 / 操作」的常见比例
const tableColumns = [16, 34, 14, 20, 16]
</script>

<template>
  <div class="app-skeleton" :class="`is-${props.variant}`" role="status" aria-busy="true">
    <span class="sr-only">{{ props.label }}</span>

    <!-- 表格：表头 + N 行，行高与真实表格一致（44~48px） -->
    <template v-if="props.variant === 'table'">
      <div class="app-skeleton__row is-head">
        <span v-for="(w, i) in tableColumns" :key="i" class="skeleton" :style="{ width: `${w}%` }" />
      </div>
      <div v-for="i in props.rows" :key="i" class="app-skeleton__row">
        <span v-for="(w, j) in tableColumns" :key="j" class="skeleton" :style="{ width: `${w}%` }" />
      </div>
    </template>

    <!-- 卡片流：封面 + 两行标题 + 底部一行 -->
    <div
      v-else-if="props.variant === 'card'"
      class="app-skeleton__grid"
      :style="{ gridTemplateColumns: `repeat(${props.columns}, minmax(0, 1fr))` }"
    >
      <div v-for="i in props.rows" :key="i" class="app-skeleton__card surface">
        <div class="skeleton app-skeleton__cover" />
        <span class="skeleton skeleton-text" style="width: 72%" />
        <span class="skeleton skeleton-text" style="width: 46%" />
        <span class="skeleton skeleton-text" style="width: 28%" />
      </div>
    </div>

    <!-- 详情：标题 + 元信息 + 字段行 + 内容块 -->
    <div v-else-if="props.variant === 'detail'" class="app-skeleton__detail">
      <span class="skeleton" style="width: 180px; height: 22px" />
      <span class="skeleton skeleton-text" style="width: 42%" />
      <div class="app-skeleton__fields">
        <div v-for="i in props.rows" :key="i" class="app-skeleton__field">
          <span class="skeleton skeleton-text" style="width: 72px" />
          <span class="skeleton skeleton-text" style="width: 160px" />
        </div>
      </div>
      <div class="skeleton app-skeleton__block" />
    </div>

    <!-- 图表：图例 + 绘图区 -->
    <div v-else class="app-skeleton__chart">
      <div class="app-skeleton__legend">
        <span v-for="i in 3" :key="i" class="skeleton" style="width: 64px; height: 12px" />
      </div>
      <div class="skeleton app-skeleton__plot" :style="{ height: `${props.height}px` }" />
    </div>
  </div>
</template>

<style scoped>
.app-skeleton {
  width: 100%;
}

/* ---------- 表格 ---------- */
.app-skeleton__row {
  display: flex;
  align-items: center;
  gap: var(--space-5);
  height: 46px;
  border-bottom: 1px solid var(--color-divider);
}

.app-skeleton__row.is-head {
  height: 40px;
}

.app-skeleton__row .skeleton {
  height: 12px;
}

.app-skeleton__row.is-head .skeleton {
  height: 10px;
}

/* ---------- 卡片流 ---------- */
.app-skeleton__grid {
  display: grid;
  gap: var(--space-5);
}

.app-skeleton__card {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-4);
}

.app-skeleton__cover {
  aspect-ratio: 16 / 9;
  border-radius: var(--radius-md);
}

/* ---------- 详情 ---------- */
.app-skeleton__detail {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.app-skeleton__fields {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: var(--space-3) var(--space-5);
  margin: var(--space-2) 0;
}

.app-skeleton__field {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  height: 22px;
}

.app-skeleton__block {
  height: 120px;
  border-radius: var(--radius-md);
}

/* ---------- 图表 ---------- */
.app-skeleton__chart {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.app-skeleton__legend {
  display: flex;
  gap: var(--space-4);
}

.app-skeleton__plot {
  border-radius: var(--radius-md);
}

@media (max-width: 768px) {
  .app-skeleton__grid {
    grid-template-columns: minmax(0, 1fr) !important;
  }
}
</style>
