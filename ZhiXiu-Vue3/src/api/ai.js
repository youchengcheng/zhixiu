// 智能维修助手 /api/ai（业主）
// 注意：发送消息为 SSE 流式接口，不走 axios，统一由 utils/sse.js 封装
import http from '@/utils/request'

export function createSession(data = {}) {
  return http.post('/ai/session', data)
}

export function listSessions() {
  return http.get('/ai/session/list')
}

export function listSessionMessages(sessionId) {
  return http.get(`/ai/session/${sessionId}/messages`)
}

/** 获取当前工单草稿；不存在时业务码 3002 */
export function getSessionDraft(sessionId, config = {}) {
  return http.get(`/ai/session/${sessionId}/draft`, config)
}

/** 确认草稿生成工单：合并 AI 草稿 + 用户确认字段 */
export function confirmDraft(sessionId, data) {
  return http.post(`/ai/session/${sessionId}/confirm`, data)
}

export function deleteSession(sessionId) {
  return http.delete(`/ai/session/${sessionId}`)
}
