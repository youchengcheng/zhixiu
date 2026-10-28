// 师傅管理 /api/admin/worker
import http from '@/utils/request'

export function listWorkers(params) {
  return http.get('/admin/worker/list', { params })
}

export function getWorkerDetail(id) {
  return http.get(`/admin/worker/${id}`)
}

/** 师傅审核：auditStatus 1通过 2驳回 */
export function auditWorker(id, data) {
  return http.post(`/admin/worker/${id}/audit`, data)
}

/** 启用/禁用师傅：workStatus 1在线 4禁用 */
export function updateWorkerStatus(id, workStatus) {
  return http.put(`/admin/worker/${id}/status`, { workStatus })
}

/** 师傅负载列表：各师傅当前进行中工单数 */
export function listWorkerLoad() {
  return http.get('/admin/worker/load')
}
