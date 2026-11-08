// 站内消息中心 /api/notification
import http from '@/utils/request'

export function listNotifications(params) {
  return http.get('/notification/list', { params })
}

export function getUnreadCount() {
  return http.get('/notification/unread-count')
}

export function readNotification(id) {
  return http.put(`/notification/${id}/read`)
}

export function readAllNotifications() {
  return http.put('/notification/read-all')
}
