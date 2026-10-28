import { operationLogs } from '../data/logs'
import { fail, like, ok, paginate } from '../utils'

// 操作日志：表 operation_log 已由后端 @ApiLog 切面在写，这里补齐查询接口的 mock
function requireAdmin(auth) {
  if (!auth || auth.roleCode !== 'ADMIN') return fail(403, '无权限访问该资源')
  return null
}

export const logHandlers = [
  {
    method: 'GET',
    path: '/admin/log/list',
    handler: ({ auth, params }) => {
      const denied = requireAdmin(auth)
      if (denied) return denied

      const module = params.module ? String(params.module) : ''
      const username = params.username ? String(params.username) : ''
      const result = params.result === undefined || params.result === '' ? null : Number(params.result)
      const startTime = params.startTime ? String(params.startTime) : ''
      const endTime = params.endTime ? String(params.endTime) : ''

      const list = operationLogs
        .filter((item) => (module ? item.module === module : true))
        .filter((item) => like(item.username, username))
        .filter((item) => (result === null ? true : item.result === result))
        // 时间范围按字符串比较即可，后端返回的时间格式固定为 yyyy-MM-dd HH:mm:ss
        .filter((item) => (startTime ? item.createTime >= startTime : true))
        .filter((item) => (endTime ? item.createTime <= `${endTime} 23:59:59` : true))
        .sort((a, b) => new Date(b.createTime).getTime() - new Date(a.createTime).getTime())

      return ok(paginate(list, params))
    }
  }
]
