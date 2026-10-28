// 认证模块 /api/auth（baseURL 已含 /api，故此处路径不再重复前缀）
import http from '@/utils/request'

/** 业主注册（公开） */
export function registerOwner(data) {
  return http.post('/auth/register/owner', data)
}

/** 师傅注册（公开）：注册后 audit_status=待审，需管理员审核 */
export function registerWorker(data) {
  return http.post('/auth/register/worker', data)
}

/** 登录（公开）：返回 token / refreshToken / expiresIn / userInfo */
export function login(data) {
  return http.post('/auth/login', data)
}

/** 登出：token 加入 Redis 黑名单 */
export function logout() {
  return http.post('/auth/logout')
}

/** 刷新 Token（公开） */
export function refreshToken(token) {
  return http.post('/auth/refresh', { refreshToken: token })
}

/** 获取当前登录用户；师傅角色额外返回 workerId / auditStatus / workStatus */
export function getCurrentUser() {
  return http.get('/auth/me')
}
