// 操作日志 /api/admin/log
import http from '@/utils/request'

/** module / username / result / startTime / endTime */
export function listOperationLogs(params) {
  return http.get('/admin/log/list', { params })
}
