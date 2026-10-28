// 业主管理 /api/admin/owner 与房产管理 /api/admin/property
import http from '@/utils/request'

export function listOwners(params) {
  return http.get('/admin/owner/list', { params })
}

/** 启用/禁用业主：status 0禁用 1正常 */
export function updateOwnerStatus(id, status) {
  return http.put(`/admin/owner/${id}/status`, { status })
}

/** 查看某业主名下房产 */
export function listOwnerProperties(id) {
  return http.get(`/admin/owner/${id}/properties`)
}

/** 管理员新增房产：参数同业主端，额外 ownerId */
export function createOwnerProperty(data) {
  return http.post('/admin/property', data)
}

export function updateOwnerProperty(id, data) {
  return http.put(`/admin/property/${id}`, data)
}

export function deleteOwnerProperty(id) {
  return http.delete(`/admin/property/${id}`)
}
