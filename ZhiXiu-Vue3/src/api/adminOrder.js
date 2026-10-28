// 工单 — 管理员 /api/admin/order
import http from '@/utils/request'

/** 全部工单列表：status / urgency / categoryId / escalated / keyword */
export function listAllOrders(params) {
  return http.get('/admin/order/list', { params })
}

export function getAdminOrderDetail(id) {
  return http.get(`/admin/order/${id}`)
}

/** 手动派单：PENDING → ASSIGNED */
export function assignOrder(id, data) {
  return http.post(`/admin/order/${id}/assign`, data)
}

/** 改派：ASSIGNED/ACCEPTED → ASSIGNED */
export function reassignOrder(id, data) {
  return http.post(`/admin/order/${id}/reassign`, data)
}

export function cancelOrderByAdmin(id, cancelReason) {
  return http.post(`/admin/order/${id}/cancel`, { cancelReason })
}

export function listAdminOrderLogs(id) {
  return http.get(`/admin/order/${id}/logs`)
}

/** 智能派单推荐：返回 Top3 推荐师傅与理由 */
export function recommendWorkers(id) {
  return http.get(`/admin/order/${id}/recommend-workers`)
}
