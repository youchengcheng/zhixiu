// 工单 — 业主 /api/owner/order
import http from '@/utils/request'

/** 手动提交报修 */
export function createOrder(data) {
  return http.post('/owner/order', data)
}

/** 我的工单列表：pageNum / pageSize / status / keyword */
export function listMyOrders(params) {
  return http.get('/owner/order/list', { params })
}

export function getOrderDetail(id) {
  return http.get(`/owner/order/${id}`)
}

/** 工单流转记录 */
export function listOrderLogs(id) {
  return http.get(`/owner/order/${id}/logs`)
}

/** 取消工单：仅 PENDING / ASSIGNED / ACCEPTED 可取消 */
export function cancelOrder(id, cancelReason) {
  return http.post(`/owner/order/${id}/cancel`, { cancelReason })
}

/** 确认完工：SUBMITTED → COMPLETED */
export function confirmOrder(id) {
  return http.post(`/owner/order/${id}/confirm`)
}

/** 提交评价：仅 COMPLETED 可评价，评价后 COMPLETED → CLOSED */
export function evaluateOrder(id, data) {
  return http.post(`/owner/order/${id}/evaluate`, data)
}
