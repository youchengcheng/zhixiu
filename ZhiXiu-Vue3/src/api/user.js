// 用户中心 /api/user
import http from '@/utils/request'

export function getProfile() {
  return http.get('/user/profile')
}

export function updateProfile(data) {
  return http.put('/user/profile', data)
}

export function changePassword(data) {
  return http.put('/user/password', data)
}
