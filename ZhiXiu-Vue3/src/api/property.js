// 房产 /api/property（业主）
import http from '@/utils/request'

export function listMyProperties() {
  return http.get('/property/list')
}

export function createProperty(data) {
  return http.post('/property', data)
}

export function updateProperty(id, data) {
  return http.put(`/property/${id}`, data)
}

export function deleteProperty(id) {
  return http.delete(`/property/${id}`)
}

export function setDefaultProperty(id) {
  return http.put(`/property/${id}/default`)
}
