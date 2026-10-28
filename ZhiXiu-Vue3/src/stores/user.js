import { defineStore } from 'pinia'
import * as authApi from '@/api/auth'
import {
  clearAuthStorage,
  getRefreshToken,
  getStoredUser,
  getToken,
  setStoredUser,
  setTokens
} from '@/utils/auth'

// 各角色登录后的首页
const HOME_PATH = {
  OWNER: '/owner/home',
  WORKER: '/worker/workbench',
  ADMIN: '/admin/dashboard'
}

export const useUserStore = defineStore('user', {
  state: () => ({
    token: getToken(),
    refreshToken: getRefreshToken(),
    userInfo: getStoredUser()
  }),

  getters: {
    isLoggedIn: (state) => Boolean(state.token),
    roleCode: (state) => state.userInfo?.roleCode || '',
    roleName: (state) => state.userInfo?.roleName || '',
    userId: (state) => state.userInfo?.userId || '',
    displayName: (state) => state.userInfo?.realName || state.userInfo?.username || '未登录',
    avatar: (state) => state.userInfo?.avatar || '',
    /** 师傅账号的扩展信息 */
    workerId: (state) => state.userInfo?.workerId || '',
    auditStatus: (state) => state.userInfo?.auditStatus,
    workStatus: (state) => state.userInfo?.workStatus,
    homePath: (state) => HOME_PATH[state.userInfo?.roleCode] || '/login'
  },

  actions: {
    async login(payload) {
      const data = await authApi.login(payload)
      this.token = data.token || ''
      this.refreshToken = data.refreshToken || ''
      this.userInfo = data.userInfo || null
      setTokens({ token: this.token, refreshToken: this.refreshToken })
      setStoredUser(this.userInfo)
      return data
    },

    /** 刷新用户信息：进入应用时调用，可顺便校验 token 是否仍有效 */
    async fetchMe() {
      const info = await authApi.getCurrentUser()
      this.setUserInfo({ ...(this.userInfo || {}), ...info })
      return this.userInfo
    },

    async logout() {
      try {
        if (this.token) {
          await authApi.logout()
        }
      } catch {
        // 服务端登出失败不应阻塞本地登出
      }
      this.clearAuth()
    },

    setUserInfo(info) {
      this.userInfo = info || null
      setStoredUser(this.userInfo)
    },

    setTokens(token, refreshToken) {
      this.token = token || ''
      this.refreshToken = refreshToken || ''
      setTokens({ token: this.token, refreshToken: this.refreshToken })
    },

    clearAuth() {
      this.token = ''
      this.refreshToken = ''
      this.userInfo = null
      clearAuthStorage()
    }
  }
})
