// 工单 — 师傅 /api/worker/order 与个人统计 /api/worker/statistics
import http from '@/utils/request'

/** 待接单工单列表：按紧急程度 + 时间排序 */
export function listPendingOrders(params) {
  return http.get('/worker/order/pending', { params })
}

/** 抢单：PENDING/ASSIGNED → ACCEPTED，已被抢返回 2003 */
export function grabOrder(id) {
  return http.post(`/worker/order/${id}/grab`)
}

/** 接受派单：ASSIGNED → ACCEPTED */
export function acceptOrder(id) {
  return http.post(`/worker/order/${id}/accept`)
}

/** 我的工单列表 */
export function listMyWorkerOrders(params) {
  return http.get('/worker/order/list', { params })
}

export function getWorkerOrderDetail(id) {
  return http.get(`/worker/order/${id}`)
}

/** 到场签到：ACCEPTED → GOING */
export function arriveOrder(id, data = {}) {
  return http.post(`/worker/order/${id}/arrive`, data)
}

/** 暂停工单：GOING → PAUSED */
export function pauseOrder(id, remark) {
  return http.post(`/worker/order/${id}/pause`, { remark })
}

/** 恢复工单：PAUSED → GOING */
export function resumeOrder(id) {
  return http.post(`/worker/order/${id}/resume`)
}

/** 申请转单：GOING → TRANSFER_PENDING */
export function transferOrder(id, transferReason) {
  return http.post(`/worker/order/${id}/transfer`, { transferReason })
}

/** 提交完工：GOING → SUBMITTED */
export function submitOrder(id, data) {
  return http.post(`/worker/order/${id}/submit`, data)
}

/** 个人统计 */
export function getWorkerStatistics() {
  return http.get('/worker/statistics')
}

/** 切换在线状态：1在线 2离线 */
export function updateWorkStatus(workStatus) {
  return http.post('/worker/status', { workStatus })
}
