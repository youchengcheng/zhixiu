import { useUserStore } from '@/stores/user'
import { APP_TITLE } from '@/utils/env'

export function setupRouterGuard(router) {
  router.beforeEach(async (to) => {
    const userStore = useUserStore()

    const isPublic = to.matched.some((record) => record.meta?.public)

    // 未登录：白名单放行，其余跳登录并带回跳地址
    if (!userStore.isLoggedIn) {
      return isPublic ? true : { name: 'Login', query: { redirect: to.fullPath } }
    }

    // 有 token 但没有用户信息（例如只残留了本地 token）：尝试恢复，失败则退出登录
    if (!userStore.roleCode) {
      try {
        await userStore.fetchMe()
      } catch {
        userStore.clearAuth()
        return { name: 'Login', query: { redirect: to.fullPath } }
      }
    }

    // 已登录访问登录/注册页：直接回到本角色首页
    if (['/login', '/register', '/register/worker'].includes(to.path)) {
      return userStore.homePath
    }

    // 角色校验：目标分支声明了角色且与当前角色不符时，回到本角色首页
    const requiredRole = to.matched.find((record) => record.meta?.role)?.meta.role
    if (requiredRole && requiredRole !== userStore.roleCode) {
      return userStore.homePath
    }

    return true
  })

  router.afterEach((to) => {
    const title = to.meta?.title
    document.title = title ? `${title} · ${APP_TITLE}` : APP_TITLE
  })
}
