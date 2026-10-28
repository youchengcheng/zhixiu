<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import LetterAvatar from '@/components/LetterAvatar.vue'
import AppBreadcrumb from './AppBreadcrumb.vue'
import { ArrowDown, Expand, Fold } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import { ROLE } from '@/utils/dict'

const props = defineProps({
  collapsed: { type: Boolean, default: false },
  serviceNavigation: { type: Boolean, default: false }
})

const emit = defineEmits(['toggle'])

const router = useRouter()
const userStore = useUserStore()

const roleLabel = computed(() => ROLE[userStore.roleCode]?.text || userStore.roleName || '')

const profilePath = computed(() => {
  const prefix = { OWNER: '/owner', WORKER: '/worker', ADMIN: '/admin' }[userStore.roleCode]
  return prefix ? `${prefix}/profile` : '/login'
})

async function onCommand(command) {
  if (command === 'profile') {
    router.push(profilePath.value)
    return
  }

  if (command === 'logout') {
    try {
      await ElMessageBox.confirm('退出后需要重新登录，确定退出吗？', '退出登录', {
        confirmButtonText: '退出登录',
        cancelButtonText: '取消',
        type: 'warning'
      })
    } catch {
      return
    }
    await userStore.logout()
    router.replace({ name: 'Login' })
  }
}
</script>

<template>
  <header class="top-bar">
    <slot name="brand" />
    <button
      v-if="!props.serviceNavigation"
      class="top-bar__icon-button"
      type="button"
      :aria-label="props.collapsed ? '展开侧边导航' : '收起侧边导航'"
      @click="emit('toggle')"
    >
      <el-icon><Expand v-if="props.collapsed" /><Fold v-else /></el-icon>
    </button>

    <AppBreadcrumb v-if="!props.serviceNavigation" class="top-bar__crumb" />
    <slot name="navigation" />

    <span class="spacer" />

    <span v-if="roleLabel" class="top-bar__role">{{ roleLabel }}</span>

    <el-dropdown trigger="click" @command="onCommand">
      <button class="top-bar__user" type="button" :aria-label="'账户菜单：' + userStore.displayName">
        <!-- 有上传头像时显示图片，没有时回退姓名首字 -->
        <LetterAvatar :name="userStore.displayName" :src="userStore.avatar" :size="32" />
        <span class="top-bar__user-name">{{ userStore.displayName }}</span>
        <el-icon class="top-bar__user-arrow"><ArrowDown /></el-icon>
      </button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="profile">个人中心</el-dropdown-item>
          <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </header>
</template>

<style scoped>
.top-bar {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  height: var(--layout-top-height);
  padding: 0 var(--space-5);
  background: var(--color-bg-card);
  border-bottom: 1px solid var(--color-border);
}

.top-bar__icon-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border: none;
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--color-text-2);
  cursor: pointer;
  transition: background var(--dur-fast) var(--ease-out), color var(--dur-fast) var(--ease-out);
}

.top-bar__icon-button:hover {
  background: var(--color-bg-sunken);
  color: var(--color-text-1);
}

.top-bar__crumb {
  min-width: 0;
}

.top-bar__role {
  padding: 2px var(--space-2);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-bg-sunken);
  color: var(--color-text-2);
  font-size: var(--fs-xs);
  white-space: nowrap;
}

.top-bar__user {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  height: 44px;
  padding: 0 var(--space-2);
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--color-text-1);
  cursor: pointer;
  transition: background var(--dur-fast) var(--ease-out), border-color var(--dur-fast) var(--ease-out);
}

.top-bar__user:hover {
  background: var(--color-bg-sunken);
  border-color: var(--color-border);
}

.top-bar__user-name {
  font-size: var(--fs-sm);
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.top-bar__user-arrow {
  font-size: 12px;
  color: var(--color-text-3);
}

@media (max-width: 768px) {
  .top-bar {
    padding: 0 var(--space-4);
  }

  .top-bar__role,
  .top-bar__user-name {
    display: none;
  }

  .top-bar__crumb {
    flex: 1;
    overflow: hidden;
  }
}
</style>
