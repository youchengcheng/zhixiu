<script setup>
import { computed } from 'vue'
import { iconOf } from '@/utils/menu-icons'

// 空状态：图形 + 业务化引导语 + 一个主操作，替代「暂无数据」四个字
const props = defineProps({
  /** 插画路径（src/assets 下的正式插画），不传则退回图标方案 */
  illustration: { type: String, default: '' },
  icon: { type: String, default: 'Document' },
  title: { type: String, default: '这里还没有内容' },
  description: { type: String, default: '' },
  actionText: { type: String, default: '' },
  size: { type: String, default: 'default' }
})

const emit = defineEmits(['action'])

const iconComponent = computed(() => iconOf(props.icon))
</script>

<template>
  <div class="empty-state" :class="`is-${props.size}`">
    <img v-if="props.illustration" class="empty-state__image" :src="props.illustration" alt="" />
    <span v-else class="empty-state__icon" aria-hidden="true">
      <el-icon><component :is="iconComponent" /></el-icon>
    </span>

    <p class="empty-state__title">{{ props.title }}</p>
    <p v-if="props.description" class="empty-state__desc">{{ props.description }}</p>

    <div class="empty-state__actions">
      <el-button v-if="props.actionText" type="primary" @click="emit('action')">
        {{ props.actionText }}
      </el-button>
      <slot />
    </div>
  </div>
</template>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-3);
  padding: var(--space-9) var(--space-5);
  text-align: center;
}

.empty-state.is-compact {
  padding: var(--space-6) var(--space-4);
}

.empty-state__image {
  width: 168px;
  max-width: 60%;
  margin-bottom: var(--space-2);
}

.empty-state__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xl);
  background: var(--color-bg-sunken);
  color: var(--color-text-3);
  font-size: 24px;
  margin-bottom: var(--space-1);
}

.empty-state__title {
  font-size: var(--fs-h3);
  font-weight: var(--fw-medium);
  color: var(--color-text-1);
}

.empty-state__desc {
  max-width: 46ch;
  font-size: var(--fs-sm);
  color: var(--color-text-3);
  line-height: var(--lh-base);
}

.empty-state__actions {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-top: var(--space-2);
}
</style>
