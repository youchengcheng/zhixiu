// 统计看板 /api/admin/statistics
import http from '@/utils/request'

export function getOverview() {
  return http.get('/admin/statistics/overview')
}

/** 工单趋势：days 默认 7 */
export function getOrderTrend(days = 7) {
  return http.get('/admin/statistics/order-trend', { params: { days } })
}

export function getCategoryDistribution() {
  return http.get('/admin/statistics/category-distribution')
}

/** 师傅接单排行：top 默认 10，month 默认本月（yyyy-MM） */
export function getWorkerRanking(params) {
  return http.get('/admin/statistics/worker-ranking', { params })
}

export function getSatisfaction() {
  return http.get('/admin/statistics/satisfaction')
}

export function getAiResolution() {
  return http.get('/admin/statistics/ai-resolution')
}
