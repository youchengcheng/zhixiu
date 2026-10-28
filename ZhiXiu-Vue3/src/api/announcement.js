// 公告 /api/announcement（读）与 /api/admin/announcement（写）
import http from '@/utils/request'

/** 公告列表：按角色过滤 target_role */
export function listAnnouncements(params) {
  return http.get('/announcement/list', { params })
}

export function getAnnouncementDetail(id) {
  return http.get(`/announcement/${id}`)
}

export function createAnnouncement(data) {
  return http.post('/admin/announcement', data)
}

export function updateAnnouncement(id, data) {
  return http.put(`/admin/announcement/${id}`, data)
}

export function deleteAnnouncement(id) {
  return http.delete(`/admin/announcement/${id}`)
}

/** 发布/下架：status 0草稿 1发布 */
export function publishAnnouncement(id, status) {
  return http.put(`/admin/announcement/${id}/publish`, { status })
}
