// 问题反馈 /api/feedback 与 /api/admin/feedback
import http from '@/utils/request'

export function createFeedback(data) {
  return http.post('/feedback', data)
}

export function listMyFeedback(params) {
  return http.get('/feedback/my', { params })
}

export function listFeedback(params) {
  return http.get('/admin/feedback/list', { params })
}

/** 回复反馈：status 1处理中 2已处理 3关闭 */
export function replyFeedback(id, data) {
  return http.post(`/admin/feedback/${id}/reply`, data)
}
