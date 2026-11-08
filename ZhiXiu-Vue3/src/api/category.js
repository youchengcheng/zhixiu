// 报修类型 /api/category
import http from '@/utils/request'

/** 报修类型树：一级类型带 children 二级类型 */
export function getCategoryTree() {
  return http.get('/category/tree')
}

/** 管理端完整类型树：包含停用节点 */
export function getAdminCategoryTree() {
  return http.get('/admin/category/tree')
}

export function createCategory(data) {
  return http.post('/admin/category', data)
}

export function updateCategory(id, data) {
  return http.put(`/admin/category/${id}`, data)
}

export function updateCategoryStatus(id, status) {
  return http.put(`/admin/category/${id}/status`, { status })
}

export function deleteCategory(id) {
  return http.delete(`/admin/category/${id}`)
}
