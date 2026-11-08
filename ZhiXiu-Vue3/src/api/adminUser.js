// 管理员账号维护 /api/admin/user
import http from '@/utils/request'

export function resetUserPassword(id, data) {
  return http.put(`/admin/user/${id}/reset-password`, data)
}
