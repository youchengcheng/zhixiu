import { aiSessions } from '../data/ai'
import {
  aiResolution,
  buildOrderTrend,
  categoryDistribution,
  overview,
  satisfaction,
  workerRanking
} from '../data/statistics'
import { fail, ok } from '../utils'

// 统计看板：管理员专属，纯聚合查询，无写操作
function requireAdmin(auth) {
  if (!auth || auth.roleCode !== 'ADMIN') return fail(403, '无权限访问该资源')
  return null
}

// 与真实统计相同：只有明确的已解决反馈计入自助解决成功。
function currentAiResolution() {
  const totalSessions = aiSessions.length
  const resolvedSessions = aiSessions.filter((item) => item.resolved === 1).length
  const transferSessions = aiSessions.filter((item) => item.resolved === 2).length
  return {
    ...aiResolution,
    totalSessions,
    resolvedSessions,
    transferSessions,
    resolutionRate: totalSessions ? Math.round((resolvedSessions / totalSessions) * 100) / 100 : 0
  }
}

export const statisticsHandlers = [
  {
    method: 'GET',
    path: '/admin/statistics/overview',
    handler: ({ auth }) => {
      const denied = requireAdmin(auth)
      if (denied) return denied
      return ok({ ...overview, aiResolutionRate: currentAiResolution().resolutionRate })
    }
  },
  {
    method: 'GET',
    path: '/admin/statistics/order-trend',
    handler: ({ auth, params }) => {
      const denied = requireAdmin(auth)
      if (denied) return denied

      const days = Number(params.days || 7)
      // 限制在 1~30 天，与看板的时间切换保持一致
      const range = Number.isNaN(days) ? 7 : Math.min(30, Math.max(1, days))
      return ok(buildOrderTrend(range))
    }
  },
  {
    method: 'GET',
    path: '/admin/statistics/category-distribution',
    handler: ({ auth }) => {
      const denied = requireAdmin(auth)
      if (denied) return denied
      return ok(categoryDistribution)
    }
  },
  {
    method: 'GET',
    path: '/admin/statistics/worker-ranking',
    handler: ({ auth, params }) => {
      const denied = requireAdmin(auth)
      if (denied) return denied

      const top = Number(params.top || 10)
      const size = Number.isNaN(top) ? 10 : Math.min(20, Math.max(1, top))
      return ok(workerRanking.slice(0, size))
    }
  },
  {
    method: 'GET',
    path: '/admin/statistics/satisfaction',
    handler: ({ auth }) => {
      const denied = requireAdmin(auth)
      if (denied) return denied
      return ok(satisfaction)
    }
  },
  {
    method: 'GET',
    path: '/admin/statistics/ai-resolution',
    handler: ({ auth }) => {
      const denied = requireAdmin(auth)
      if (denied) return denied
      return ok(currentAiResolution())
    }
  }
]
