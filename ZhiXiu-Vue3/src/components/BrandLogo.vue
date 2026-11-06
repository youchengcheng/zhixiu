<script setup>
const props = defineProps({
  subtitle: { type: String, default: '' },
  symbolOnly: { type: Boolean, default: false },
  compact: { type: Boolean, default: false }
})
</script>

<template>
  <span class="brand-logo" :class="{ 'is-compact': props.compact, 'is-symbol-only': props.symbolOnly }">
    <span class="brand-logo__mark" aria-hidden="true">ZX</span>
    <span v-if="!props.symbolOnly" class="brand-logo__copy">
      <span class="brand-logo__name">智修云</span>
      <span v-if="props.subtitle" class="brand-logo__subtitle">{{ props.subtitle }}</span>
    </span>
  </span>
</template>

<style scoped>
.brand-logo {
  --brand-logo-size: 38px;
  display: inline-flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  color: var(--color-text-1);
}

.brand-logo.is-compact {
  --brand-logo-size: 34px;
  gap: 10px;
}

.brand-logo__mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  width: var(--brand-logo-size);
  height: var(--brand-logo-size);
  border: 1px solid color-mix(in srgb, var(--color-text-1) 82%, transparent);
  border-radius: var(--radius-md);
  background: var(--color-text-1);
  color: var(--color-bg-card);
  font-family: var(--font-mono);
  font-size: calc(var(--brand-logo-size) * 0.34);
  font-weight: 650;
  line-height: 1;
  letter-spacing: -0.1em;
  text-indent: -0.1em;
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--color-bg-card) 12%, transparent);
}

.brand-logo__copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 4px;
  line-height: 1.2;
}

.brand-logo__name {
  color: var(--color-text-1);
  font-size: 19px;
  font-weight: var(--fw-semibold);
  letter-spacing: 0.04em;
  white-space: nowrap;
}

.brand-logo__subtitle {
  overflow: hidden;
  color: var(--color-text-3);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.brand-logo.is-compact .brand-logo__name {
  font-size: 18px;
}

.brand-logo.is-symbol-only {
  gap: 0;
}
</style>
