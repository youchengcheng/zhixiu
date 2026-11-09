import { defineStore } from 'pinia'
import { getUnreadCount } from '@/api/notification'

export const useNotificationStore = defineStore('notification', {
  state: () => ({
    unreadCount: 0,
    loading: false
  }),

  actions: {
    async loadUnreadCount() {
      if (this.loading) return this.unreadCount
      this.loading = true
      try {
        const data = await getUnreadCount()
        this.unreadCount = Number(data?.count || 0)
      } finally {
        this.loading = false
      }
      return this.unreadCount
    },

    markOneRead() {
      this.unreadCount = Math.max(0, this.unreadCount - 1)
    },

    clearUnread() {
      this.unreadCount = 0
    },

    reset() {
      this.unreadCount = 0
      this.loading = false
    }
  }
})
