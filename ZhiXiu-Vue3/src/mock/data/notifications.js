// 三角色站内消息样例。归属字段仅供 Mock 鉴权，接口响应时会移除。
export const notifications = [
  {
    id: '91001',
    userId: '10001',
    title: '工单已被接单',
    content: '师傅严志勇已接下您的水管漏水工单，请留意后续上门联系。',
    orderId: '70001',
    isRead: 0,
    readTime: null,
    createTime: '2026-11-07 09:32:18'
  },
  {
    id: '91002',
    userId: '10001',
    title: '维修已完成',
    content: '工单已由师傅提交完工，请确认维修结果并完成评价。',
    orderId: '70002',
    isRead: 1,
    readTime: '2026-11-06 18:21:12',
    createTime: '2026-11-06 18:15:09'
  },
  {
    id: '92001',
    userId: '10002',
    title: '收到新的派单',
    content: '管理员向您派发了一条空调维修工单，请及时查看并处理。',
    orderId: '70003',
    isRead: 0,
    readTime: null,
    createTime: '2026-11-07 08:48:36'
  },
  {
    id: '92002',
    userId: '10002',
    title: '工单即将超时',
    content: '您处理中的工单距离 SLA 响应时限不足 30 分钟。',
    orderId: '70004',
    isRead: 0,
    readTime: null,
    createTime: '2026-11-07 08:15:42'
  },
  {
    id: '93001',
    userId: '10003',
    title: 'SLA 超时提醒',
    content: '工单已超过首次响应时限，请尽快协调师傅处理。',
    orderId: '70005',
    isRead: 0,
    readTime: null,
    createTime: '2026-11-07 09:10:06'
  },
  {
    id: '93002',
    userId: '10003',
    title: '工单已转单',
    content: '师傅因现场条件不符申请转单，请重新安排处理人员。',
    orderId: '70006',
    isRead: 1,
    readTime: '2026-11-06 16:30:25',
    createTime: '2026-11-06 16:28:01'
  }
]
