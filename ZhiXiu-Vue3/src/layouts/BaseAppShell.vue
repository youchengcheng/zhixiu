<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowDown } from '@element-plus/icons-vue'
import { roleRoutes } from '@/router/routes'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import SideNav from './components/SideNav.vue'
import TopBar from './components/TopBar.vue'

// 业主使用顶部服务导航，师傅与管理员使用可折叠侧栏；手机共用导航抽屉。
// 菜单完全由路由 meta 推导（hidden 不出现在菜单，group 决定分组）
const props = defineProps({
  roleCode: { type: String, required: true },
  roleLabel: { type: String, default: '' }
})

const route = useRoute()
const router = useRouter()
const isOwner = computed(() => props.roleCode === 'OWNER')
const activePath = computed(() => route.meta?.activeMenu || route.path)
const appStore = useAppStore()
const userStore = useUserStore()
const isNarrow = ref(false)

function syncViewport() {
  isNarrow.value = window.innerWidth < 1024
  if (!isNarrow.value) {
    appStore.closeMobileNav()
  }
}

onMounted(() => {
  syncViewport()
  window.addEventListener('resize', syncViewport)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', syncViewport)
})

const collapsed = computed(() => appStore.sidebarCollapsed || isNarrow.value)

const mobileNavOpen = computed({
  get: () => appStore.mobileNavOpen,
  set: (value) => (value ? appStore.openMobileNav() : appStore.closeMobileNav())
})

const menus = computed(() => {
  const branch = roleRoutes[props.roleCode]
  if (!branch) return []

  const groups = []
  branch.children.forEach((child) => {
    if (child.meta?.hidden) return

    const name = child.meta?.group || ''
    let group = groups.find((item) => item.name === name)
    if (!group) {
      group = { name, items: [] }
      groups.push(group)
    }
    group.items.push({
      title: child.meta.title,
      icon: child.meta.icon,
      path: `${branch.path}/${child.path}`
    })
  })
  return groups
})

function onToggle() {
  if (isNarrow.value) {
    appStore.openMobileNav()
    return
  }
  appStore.toggleSidebar()
}
</script>

<template>
  <div class="app-shell" :class="{ 'app-shell--owner': isOwner, 'app-shell--operations': !isOwner }">
    <aside v-if="!isNarrow && !isOwner" class="app-shell__side" :class="{ 'is-collapsed': collapsed }">
      <router-link class="brand" :to="userStore.homePath" aria-label="智修云首页">
        <span class="brand__mark" aria-hidden="true">修</span>
        <span v-if="!collapsed" class="brand__text">
          <span class="brand__name">智修云</span>
          <span class="brand__desc">{{ props.roleLabel }}</span>
        </span>
      </router-link>
      <SideNav :menus="menus" :collapsed="collapsed" />
      <router-link v-if="!collapsed" class="app-shell__side-note" :to="`${roleRoutes[props.roleCode].path}/profile`">
        <span>{{ userStore.displayName }}</span>
        <span class="app-shell__account-hint">管理个人资料 →</span>
      </router-link>
    </aside>

    <el-drawer
      v-if="isNarrow"
      v-model="mobileNavOpen"
      direction="ltr"
      size="248px"
      :with-header="false"
      class="app-shell__drawer"
    >
      <router-link class="brand brand--drawer" :to="userStore.homePath" aria-label="智修云首页" @click="appStore.closeMobileNav()">
        <span class="brand__mark" aria-hidden="true">修</span>
        <span class="brand__text">
          <span class="brand__name">智修云</span>
          <span class="brand__desc">{{ props.roleLabel }}</span>
        </span>
      </router-link>
      <SideNav :menus="menus" :collapsed="false" @navigate="appStore.closeMobileNav()" />
    </el-drawer>

    <div class="app-shell__main">
      <TopBar class="app-shell__top" :class="{ 'top-bar--service': isOwner && !isNarrow }" :collapsed="collapsed" :service-navigation="isOwner && !isNarrow" @toggle="onToggle">
        <template v-if="isOwner && !isNarrow" #brand>
          <router-link class="brand brand--service" :to="userStore.homePath" aria-label="智修云首页">
            <span class="brand__mark" aria-hidden="true">修</span><span class="brand__name">智修云</span>
          </router-link>
        </template>
        <template v-if="isOwner && !isNarrow" #navigation>
          <nav class="service-nav" aria-label="主导航">
            <router-link v-for="item in menus[0]?.items || []" :key="item.path" class="service-nav__item" :class="{ 'is-active': activePath === item.path }" :to="item.path" :aria-current="activePath === item.path ? 'page' : undefined">{{ item.title }}</router-link>
            <el-dropdown trigger="click" :hide-on-click="true" @command="router.push">
              <button class="service-nav__more" :class="{ 'is-active': menus[1]?.items.some(item => item.path === activePath) }" type="button">社区与账户 <el-icon><ArrowDown /></el-icon></button>
              <template #dropdown><el-dropdown-menu>
                <el-dropdown-item v-for="item in menus[1]?.items || []" :key="item.path" :command="item.path">{{ item.title }}</el-dropdown-item>
              </el-dropdown-menu></template>
            </el-dropdown>
          </nav>
        </template>
      </TopBar>
      <main class="app-shell__content">
        <router-view v-slot="{ Component }">
          <transition name="route-fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<style scoped>
.app-shell {
  display: flex;
  height: 100dvh;
  overflow: hidden;
  background: var(--color-bg-page);
}

.app-shell__side {
  flex: 0 0 auto;
  width: var(--layout-side-width);
  display: flex;
  flex-direction: column;
  background: var(--color-bg-card);
  border-right: 1px solid var(--color-border);
}

.app-shell__side.is-collapsed {
  width: var(--layout-side-width-collapsed);
}

.app-shell__main {
  position: relative;
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

/* 顶栏与滚动内容分开布局，键盘焦点不会被顶栏遮挡。 */
.app-shell__top {
  flex: 0 0 auto;
  z-index: var(--z-nav);
}

.app-shell__content {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior-y: contain;
}

.brand {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex: 0 0 auto;
  min-height: var(--layout-top-height);
  padding: var(--space-5);
  border-bottom: 1px solid var(--color-divider);
}

.brand--drawer {
  padding-left: 0;
  border-bottom: none;
}

.brand__mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  width: 36px;
  height: 36px;
  border-radius: var(--radius-md);
  background: var(--color-primary);
  color: var(--color-on-primary);
  font-size: 19px;
  font-weight: var(--fw-semibold);
}

.brand__text {
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 6px;
  line-height: 1.3;
}

.brand__name {
  font-size: 20px;
  font-weight: var(--fw-semibold);
  color: var(--color-text-1);
  letter-spacing: 0.02em;
}

.brand__desc {
  font-size: var(--fs-xs);
  color: var(--color-text-3);
}

.app-shell__side-note {
  margin-top: auto;
  padding: var(--space-4) var(--space-5);
  border-top: 1px solid var(--color-border);
  color: var(--color-text-2);
  font-size: var(--fs-xs);
}

.is-collapsed .brand {
  justify-content: center;
  padding-inline: var(--space-3);
}
</style>

<style>
/* 抽屉内部不额外增加内边距，让品牌区与导航自然贴边 */
.app-shell__drawer .el-drawer__body {
  padding: var(--space-4) var(--space-3);
}
</style>
