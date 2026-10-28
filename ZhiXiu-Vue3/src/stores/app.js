import { defineStore } from 'pinia'

const COLLAPSE_KEY = 'zhixiu_sidebar_collapsed'

function readCollapsed() {
  try {
    return window.localStorage.getItem(COLLAPSE_KEY) === '1'
  } catch {
    return false
  }
}

export const useAppStore = defineStore('app', {
  state: () => ({
    sidebarCollapsed: readCollapsed(),
    mobileNavOpen: false,
    loadingCount: 0
  }),

  getters: {
    loading: (state) => state.loadingCount > 0
  },

  actions: {
    setSidebarCollapsed(collapsed) {
      this.sidebarCollapsed = collapsed
      try {
        window.localStorage.setItem(COLLAPSE_KEY, collapsed ? '1' : '0')
      } catch {
        // 忽略存储失败
      }
    },

    toggleSidebar() {
      this.setSidebarCollapsed(!this.sidebarCollapsed)
    },

    openMobileNav() {
      this.mobileNavOpen = true
    },

    closeMobileNav() {
      this.mobileNavOpen = false
    },

    startLoading() {
      this.loadingCount += 1
    },

    stopLoading() {
      this.loadingCount = Math.max(0, this.loadingCount - 1)
    }
  }
})
