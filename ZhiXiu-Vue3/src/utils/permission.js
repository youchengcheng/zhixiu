import { useUserStore } from '@/stores/user'

// v-permission="'ADMIN'" 或 v-permission="['ADMIN','WORKER']"
// 权限不匹配时直接移除元素，避免只隐藏布局占位
export const permission = {
  mounted(el, binding) {
    const value = binding.value
    if (!value) return

    const roles = Array.isArray(value) ? value : [value]
    const userStore = useUserStore()
    if (roles.includes(userStore.roleCode)) return

    el.parentNode && el.parentNode.removeChild(el)
  }
}

export function hasRole(...roles) {
  const userStore = useUserStore()
  return roles.flat().includes(userStore.roleCode)
}
