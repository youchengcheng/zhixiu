// SLA 配置 /api/admin/sla
import http from '@/utils/request'

export function listSlaConfigs() {
  return http.get('/admin/sla/list')
}

export function createSlaConfig(data) {
  return http.post('/admin/sla', data)
}

export function updateSlaConfig(id, data) {
  return http.put(`/admin/sla/${id}`, data)
}

export function updateSlaEnabled(id, enabled) {
  return http.put(`/admin/sla/${id}/enabled`, { enabled })
}

/** 删除 SLA：全局兜底配置不可删除 */
export function deleteSlaConfig(id) {
  return http.delete(`/admin/sla/${id}`)
}
