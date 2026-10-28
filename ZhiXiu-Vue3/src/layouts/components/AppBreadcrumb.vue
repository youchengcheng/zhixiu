<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

// 面包屑直接由 route.matched 推导，避免维护第二份配置
const items = computed(() => {
  const trail = route.matched
    .filter((record) => record.meta?.title)
    .map((record) => ({
      title: record.meta.title,
      path: record.path
    }))

  // 详情页通过已有 activeMenu 找到所属列表，层级名称仍来自路由配置。
  if (route.meta.activeMenu && route.meta.activeMenu !== route.path) {
    const parent = router.resolve(route.meta.activeMenu)
    if (parent.meta.title && !trail.some((item) => item.path === parent.path)) {
      trail.unshift({ title: parent.meta.title, path: parent.path })
    }
  }

  return trail.map((item, index) => ({ ...item, isLast: index === trail.length - 1 }))
})
</script>

<template>
  <nav v-if="items.length > 1" class="crumb" aria-label="面包屑">
    <template v-for="(item, index) in items" :key="item.path + index">
      <span v-if="index > 0" class="crumb__sep">/</span>
      <span v-if="item.isLast" class="crumb__current">{{ item.title }}</span>
      <router-link v-else class="crumb__link" :to="item.path">{{ item.title }}</router-link>
    </template>
  </nav>
</template>

<style scoped>
.crumb {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-width: 0;
  font-size: var(--fs-sm);
  color: var(--color-text-3);
}

.crumb__sep {
  color: var(--color-text-disabled);
}

.crumb__link {
  color: var(--color-text-3);
}

.crumb__link:hover {
  color: var(--color-text-1);
}

.crumb__current {
  color: var(--color-text-1);
  font-weight: var(--fw-medium);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
