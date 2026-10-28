<script setup>
import { useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'

const props = defineProps({
  title: { type: String, required: true },
  description: { type: String, default: '' },
  /** 详情页返回上一级 */
  back: { type: Boolean, default: false }
})

const router = useRouter()
</script>

<template>
  <header class="page-header">
    <div class="page-header__main">
      <button v-if="props.back" class="page-header__back" type="button" @click="router.back()">
        <el-icon><ArrowLeft /></el-icon>
        <span>返回</span>
      </button>
      <h1 class="page-header__title">{{ props.title }}</h1>
      <p v-if="props.description" class="page-header__desc">{{ props.description }}</p>
    </div>
    <div v-if="$slots.default" class="page-header__actions">
      <slot />
    </div>
  </header>
</template>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-5);
  flex-wrap: wrap;
}

.page-header__main {
  min-width: 0;
}

.page-header__back {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  margin-bottom: var(--space-2);
  padding: 0;
  border: none;
  background: transparent;
  color: var(--color-text-3);
  font-size: var(--fs-sm);
  cursor: pointer;
  transition: color var(--dur-fast) var(--ease-out);
}

.page-header__back:hover {
  color: var(--color-primary);
}

.page-header__title {
  font-size: 30px;
  font-weight: var(--fw-semibold);
  line-height: 1.4;
  letter-spacing: -0.04em;
  color: var(--color-text-1);
}

.page-header__desc {
  margin-top: var(--space-2);
  max-width: 68ch;
  font-size: var(--fs-sm);
  color: var(--color-text-2);
  line-height: var(--lh-base);
}

@media (max-width: 768px) {
  .page-header {
    gap: var(--space-4);
  }

  .page-header__title {
    font-size: var(--fs-h1);
  }

  .page-header__actions {
    gap: var(--space-2);
  }
}

.page-header__actions {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex-wrap: wrap;
}
</style>
