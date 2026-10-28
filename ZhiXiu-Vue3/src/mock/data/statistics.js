// 统计看板 mock 数据：后端统计模块尚未就绪，这里按开发文档 16.1~16.6 的响应结构造数
// 数值固定（非随机），保证每次刷新看板一致，便于验收对照

/** 16.1 看板总览 */
export const overview = {
  totalOrders: 860,
  pendingOrders: 12,
  goingOrders: 34,
  completedOrders: 780,
  avgResponseMinutes: 22,
  avgHandleMinutes: 95,
  avgScore: 4.7,
  slaBreachedCount: 9,
  aiResolutionRate: 0.34
}

/** 16.2 工单趋势：按天生成稳定的日期与数量序列 */
export function buildOrderTrend(days = 7) {
  const pad = (value) => String(value).padStart(2, '0')
  // 固定的日度基数，避免随机数导致每次进入看板图形跳动
  const base = [12, 18, 15, 21, 26, 19, 23, 17, 14, 20, 24, 16, 22, 25]
  const dates = []
  const counts = []
  const today = new Date()

  for (let i = days - 1; i >= 0; i -= 1) {
    const date = new Date(today.getTime() - i * 86400000)
    dates.push(`${pad(date.getMonth() + 1)}-${pad(date.getDate())}`)
    counts.push(base[(days - 1 - i) % base.length])
  }

  return { dates, counts }
}

/** 16.3 工单类型分布：一级报修类型与工单量 */
export const categoryDistribution = {
  categories: ['水管维修', '电器维修', '门窗维修', '公共设施'],
  counts: [312, 268, 176, 104]
}

/** 16.4 师傅接单排行：覆盖多个师傅，含评分与完工量 */
export const workerRanking = [
  { workerId: '55', workerNo: 'WX-2024-0117', realName: '严志勇', finishOrders: 46, avgScore: 4.8, currentLoad: 2 },
  { workerId: '56', workerNo: 'WX-2024-0121', realName: '罗启明', finishOrders: 41, avgScore: 4.7, currentLoad: 3 },
  { workerId: '57', workerNo: 'WX-2023-0088', realName: '汤雅琳', finishOrders: 38, avgScore: 4.9, currentLoad: 1 },
  { workerId: '58', workerNo: 'WX-2025-0032', realName: '师万青', finishOrders: 33, avgScore: 4.6, currentLoad: 4 },
  { workerId: '59', workerNo: 'WX-2025-0044', realName: '简宁', finishOrders: 29, avgScore: 4.5, currentLoad: 0 }
]

/** 16.5 满意度统计：各评分分布 + 平均分 */
export const satisfaction = {
  scores: [1, 2, 3, 4, 5],
  counts: [4, 9, 36, 118, 213],
  avgScore: 4.7,
  total: 380
}

/** 16.6 AI 自助解决率 */
export const aiResolution = {
  totalSessions: 200,
  resolvedSessions: 68,
  transferSessions: 132,
  resolutionRate: 0.34,
  totalPromptTokens: 120000,
  totalCompletionTokens: 80000
}
