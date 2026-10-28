import { accounts, buildToken, findAccountById, findAccountByUsername, inferRole } from '../data/accounts'
import { fail, nextId, ok } from '../utils'

const ROLE_NAME = { OWNER: '业主', WORKER: '维修师傅', ADMIN: '物业管理员' }

/** 对外暴露的用户信息结构（与 /api/auth/login 响应一致） */
function userInfoOf(account) {
  const info = {
    userId: String(account.id),
    username: account.username,
    realName: account.realName || account.username,
    phone: account.phone || '',
    email: account.email || '',
    avatar: account.avatar || '',
    gender: account.gender ?? 0,
    roleCode: account.roleCode,
    roleName: account.roleName || ROLE_NAME[account.roleCode]
  }

  if (account.roleCode === 'WORKER') {
    info.workerId = String(account.workerId || '')
    info.workerNo = account.workerNo || ''
    info.skillTags = account.skillTags || ''
    info.auditStatus = account.auditStatus ?? 0
    info.workStatus = account.workStatus ?? 0
  }

  return info
}

function buildSession(account) {
  return {
    token: buildToken(account.id, account.roleCode),
    refreshToken: `mock.refresh.${account.id}`,
    expiresIn: 7200,
    userInfo: userInfoOf(account)
  }
}

/** 演示账号之外的用户：按用户名前缀推断角色并落库，便于任意账号登录 */
function ensureAccount(username) {
  const existing = findAccountByUsername(username)
  if (existing) return existing

  const roleCode = inferRole(username)
  const id = nextId()
  const account = {
    id,
    username,
    password: 'mock',
    realName: username,
    phone: '',
    email: '',
    avatar: '',
    gender: 0,
    roleCode,
    roleName: ROLE_NAME[roleCode],
    roleId: { OWNER: '1', WORKER: '2', ADMIN: '3' }[roleCode],
    status: 1,
    createTime: '',
    lastLoginTime: ''
  }

  if (roleCode === 'WORKER') {
    account.workerId = String(Number(id) + 900)
    account.workerNo = `WX-2026-${String(id).slice(-4)}`
    account.skillTags = '水管维修,电器维修'
    account.auditStatus = 1
    account.workStatus = 1
    account.avgScore = 4.6
    account.totalOrders = 0
    account.finishOrders = 0
    account.currentLoad = 0
  }

  accounts.push(account)
  return account
}

function accountOf(auth) {
  if (!auth) return null
  return findAccountById(auth.userId) || null
}

export const authHandlers = [
  {
    method: 'POST',
    path: '/auth/login',
    public: true,
    handler: ({ body }) => {
      const username = String(body.username || '').trim()
      const password = String(body.password || '')

      if (!username) return fail(400, '请输入登录名')
      if (!password) return fail(400, '请输入密码')
      // mock 下不校验真实密码，但保留长度校验以便演示错误分支
      if (password.length < 6) return fail(1003, '用户名或密码错误')

      const account = ensureAccount(username)
      if (account.status === 0) return fail(1004, '账号已被禁用，请联系物业管理员')

      account.lastLoginTime = ''
      return ok(buildSession(account), '登录成功')
    }
  },
  {
    method: 'POST',
    path: '/auth/logout',
    handler: () => ok(null, '登出成功')
  },
  {
    method: 'POST',
    path: '/auth/refresh',
    public: true,
    handler: ({ body }) => {
      const refreshToken = String(body.refreshToken || '')
      const userId = refreshToken.split('.')[2]
      const account = findAccountById(userId)
      if (!account) return fail(401, '刷新令牌无效，请重新登录')
      return ok(buildSession(account), '刷新成功')
    }
  },
  {
    method: 'GET',
    path: '/auth/me',
    handler: ({ auth }) => {
      const account = accountOf(auth)
      if (!account) return fail(401, '登录状态已失效')
      return ok(userInfoOf(account))
    }
  },
  {
    method: 'POST',
    path: '/auth/register/owner',
    public: true,
    handler: ({ body }) => {
      const username = String(body.username || '').trim()
      const phone = String(body.phone || '').trim()

      if (username.length < 4 || username.length > 20) return fail(400, '登录名需为 4-20 位')
      if (String(body.password || '').length < 6 || String(body.password || '').length > 20)
        return fail(400, '密码需为 6-20 位')
      if (body.password !== body.confirmPassword) return fail(400, '两次输入的密码不一致')
      if (!/^1[3-9]\d{9}$/.test(phone)) return fail(400, '请输入正确的手机号')
      if (accounts.some((item) => item.username === username)) return fail(1001, '用户名已存在')
      if (phone && accounts.some((item) => item.phone === phone)) return fail(1002, '手机号已注册')

      const id = nextId()
      accounts.push({
        id,
        username,
        password: 'mock',
        realName: String(body.realName || '').trim() || username,
        phone,
        email: '',
        avatar: '',
        gender: 0,
        roleCode: 'OWNER',
        roleName: ROLE_NAME.OWNER,
        roleId: '1',
        status: 1,
        createTime: '',
        lastLoginTime: ''
      })

      return ok({ userId: String(id) }, '注册成功')
    }
  },
  {
    method: 'POST',
    path: '/auth/register/worker',
    public: true,
    handler: ({ body }) => {
      const username = String(body.username || '').trim()
      const phone = String(body.phone || '').trim()
      const workerNo = String(body.workerNo || '').trim()

      if (username.length < 4 || username.length > 20) return fail(400, '登录名需为 4-20 位')
      if (String(body.password || '').length < 6) return fail(400, '密码需为 6-20 位')
      if (body.password !== body.confirmPassword) return fail(400, '两次输入的密码不一致')
      if (!String(body.realName || '').trim()) return fail(400, '请填写真实姓名')
      if (!/^1[3-9]\d{9}$/.test(phone)) return fail(400, '请输入正确的手机号')
      if (!workerNo) return fail(400, '请填写工号')
      if (!String(body.skillTags || '').trim()) return fail(400, '请选择技能标签')
      if (accounts.some((item) => item.username === username)) return fail(1001, '用户名已存在')
      if (accounts.some((item) => item.phone === phone)) return fail(1002, '手机号已注册')
      if (accounts.some((item) => item.workerNo === workerNo)) return fail(1001, '工号已存在')

      const id = nextId()
      accounts.push({
        id,
        username,
        password: 'mock',
        realName: String(body.realName).trim(),
        phone,
        email: '',
        avatar: '',
        gender: 0,
        roleCode: 'WORKER',
        roleName: ROLE_NAME.WORKER,
        roleId: '2',
        status: 1,
        workerId: String(Number(id) + 900),
        workerNo,
        skillTags: String(body.skillTags),
        idCard: String(body.idCard || ''),
        auditStatus: 0,
        workStatus: 0,
        avgScore: 0,
        totalOrders: 0,
        finishOrders: 0,
        currentLoad: 0,
        createTime: '',
        lastLoginTime: ''
      })

      return ok({ userId: String(id) }, '注册成功，等待管理员审核')
    }
  }
]
