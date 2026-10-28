<script setup>
const props = defineProps({
  title: { type: String, default: '' },
  description: { type: String, default: '' },
  /** none 时内容贴边，适合直接放表格 */
  padding: { type: String, default: 'default' },
  /** 是否用分隔线把标题区与内容分开 */
  divided: { type: Boolean, default: false }
})
</script>

<template>
  <section class="section-card surface">
    <header v-if="props.title || $slots.extra" class="section-card__head" :class="{ 'is-divided': props.divided }">
      <div class="section-card__heading">
        <h2 class="section-card__title">{{ props.title }}</h2>
        <p v-if="props.description" class="section-card__desc">{{ props.description }}</p>
      </div>
      <div class="section-card__extra">
        <slot name="extra" />
      </div>
    </header>

    <div class="section-card__body" :class="{ 'is-flush': props.padding === 'none' }">
      <slot />
    </div>

    <footer v-if="$slots.footer" class="section-card__foot">
      <slot name="footer" />
    </footer>
  </section>
</template>

<style scoped>
.section-card {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.section-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-5) var(--space-5) 0;
  flex-wrap: wrap;
}

.section-card__head.is-divided {
  padding-bottom: var(--space-4);
  border-bottom: 1px solid var(--color-divider);
}

.section-card__heading {
  min-width: 0;
}

.section-card__title {
  font-size: var(--fs-h2);
  font-weight: var(--fw-semibold);
  color: var(--color-text-1);
}

.section-card__desc {
  margin-top: var(--space-1);
  font-size: var(--fs-sm);
  color: var(--color-text-3);
}

.section-card__extra {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-wrap: wrap;
}

.section-card__body {
  padding: var(--space-5);
  min-width: 0;
}

.section-card__body.is-flush {
  padding: 0;
}

.section-card__body.is-flush :deep(.el-table) {
  border-radius: 0 0 var(--radius-xl) var(--radius-xl);
}

.section-card__foot {
  padding: var(--space-4) var(--space-5);
  border-top: 1px solid var(--color-divider);
  background: var(--color-bg-card);
}

@media (max-width: 768px) {
  .section-card__head {
    padding: var(--space-4) var(--space-4) 0;
  }

  .section-card__body {
    padding: var(--space-4);
  }

  .section-card__foot {
    padding: var(--space-4);
  }
}
</style>
