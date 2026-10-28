<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { iconOf } from '@/utils/menu-icons'

const props = defineProps({
  /** [{ name: '分组名', items: [{ title, icon, path }] }]，name 为空表示不分组 */
  menus: { type: Array, default: () => [] },
  collapsed: { type: Boolean, default: false }
})

const emit = defineEmits(['navigate'])

const route = useRoute()

const activePath = computed(() => route.meta?.activeMenu || route.path)

function isActive(path) {
  return activePath.value === path
}
</script>

<template>
  <nav class="side-nav" aria-label="主导航">
    <div v-for="(group, groupIndex) in props.menus" :key="group.name || groupIndex" class="nav-group">
      <p v-if="group.name && !props.collapsed" class="nav-group__title">{{ group.name }}</p>
      <div v-else-if="group.name" class="nav-group__rule" />

      <router-link
        v-for="item in group.items"
        :key="item.path"
        class="nav-item"
        :class="{ 'is-active': isActive(item.path), 'is-collapsed': props.collapsed }"
        :to="item.path"
        :aria-current="isActive(item.path) ? 'page' : undefined"
        :title="props.collapsed ? item.title : ''"
        @click="emit('navigate')"
      >
        <el-icon v-if="iconOf(item.icon)" class="nav-item__icon">
          <component :is="iconOf(item.icon)" />
        </el-icon>
        <span v-if="!props.collapsed" class="nav-item__text">{{ item.title }}</span>
      </router-link>
    </div>
  </nav>
</template>

<style scoped>
.side-nav {
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
  padding: var(--space-5) var(--space-3);
  overflow-y: auto;
}

.nav-group {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.nav-group__title {
  padding: 0 var(--space-3) var(--space-2);
  font-size: var(--fs-xs);
  font-weight: var(--fw-medium);
  letter-spacing: 0.06em;
  color: var(--color-text-3);
}

.nav-group__rule {
  height: 1px;
  margin: var(--space-3) var(--space-2);
  background: var(--color-divider);
}

.nav-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-height: 44px;
  padding: 0 var(--space-3);
  border-radius: var(--radius-md);
  color: var(--color-text-2);
  font-size: var(--fs-body);
  transition: background var(--dur-fast) var(--ease-out), color var(--dur-fast) var(--ease-out);
}

.nav-item.is-collapsed {
  justify-content: center;
  padding: 0;
}

.nav-item:hover {
  background: var(--color-bg-sunken);
  color: var(--color-text-1);
}

.nav-item.is-active {
  background: var(--color-primary-soft);
  color: var(--color-primary);
  font-weight: var(--fw-semibold);
  box-shadow: inset 3px 0 0 var(--color-primary);
}

.nav-item__icon {
  font-size: 17px;
}

.nav-item__text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
