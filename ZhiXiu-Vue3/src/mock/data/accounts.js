// Mock 账号数据：登录后按账号角色下发 token，方便三端来回演示

export const accounts = [
  {
    id: '10001',
    username: 'owner01',
    password: 'owner123',
    realName: '苏明彦',
    phone: '13847129063',
    email: 'smy@example.com',
    avatar: '',
    gender: 1,
    roleCode: 'OWNER',
    roleName: '业主',
    roleId: '1',
    status: 1,
    address: '阳光嘉园 3 栋 2 单元 501',
    createTime: '2026-03-12 09:24:31',
    lastLoginTime: '2026-10-14 19:42:08'
  },
  {
    id: '10002',
    username: 'worker01',
    password: 'worker123',
    realName: '严志勇',
    phone: '13907645182',
    email: 'yzy@example.com',
    avatar: '',
    gender: 1,
    roleCode: 'WORKER',
    roleName: '维修师傅',
    roleId: '2',
    status: 1,
    workerId: '55',
    workerNo: 'WX-2024-0117',
    skillTags: '水管维修,电器维修',
    idCard: '310104198705124318',
    auditStatus: 1,
    workStatus: 1,
    avgScore: 4.8,
    totalOrders: 236,
    finishOrders: 229,
    currentLoad: 2,
    createTime: '2026-01-08 14:06:22',
    lastLoginTime: '2026-10-15 08:11:47'
  },
  {
    id: '10003',
    username: 'admin01',
    password: 'admin123',
    realName: '贺琳',
    phone: '13721390845',
    email: 'helin@example.com',
    avatar: '',
    gender: 2,
    roleCode: 'ADMIN',
    roleName: '物业管理员',
    roleId: '3',
    status: 1,
    createTime: '2026-01-05 10:12:00',
    lastLoginTime: '2026-10-15 07:58:19'
  }
]

/** 按用户名推断角色：演示账号之外也允许任意账号登录，便于快速验证 */
export function inferRole(username = '') {
  const name = String(username).toLowerCase()
  if (name.startsWith('worker')) return 'WORKER'
  if (name.startsWith('admin')) return 'ADMIN'
  return 'OWNER'
}

export function findAccountByUsername(username) {
  return accounts.find((item) => item.username === String(username || '').trim()) || null
}

export function findAccountById(id) {
  return accounts.find((item) => item.id === String(id)) || null
}

/** 从 mock token 中解析身份 */
export function parseToken(token) {
  if (!token || !String(token).startsWith('mock.')) return null
  const [, userId, roleCode] = String(token).split('.')
  if (!userId || !roleCode) return null
  return { userId, roleCode }
}

export function buildToken(userId, roleCode) {
  return `mock.${userId}.${roleCode}`
}
