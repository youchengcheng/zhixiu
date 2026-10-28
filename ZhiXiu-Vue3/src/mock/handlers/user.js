import { accounts, findAccountById } from '../data/accounts'
import { fail, ok } from '../utils'

function currentAccount(ctx) {
  return ctx.auth ? findAccountById(ctx.auth.userId) : null
}

export const userHandlers = [
  {
    method: 'GET',
    path: '/user/profile',
    handler: (ctx) => {
      const account = currentAccount(ctx)
      if (!account) return fail(401, '登录状态已失效')
      return ok({
        id: String(account.id),
        username: account.username,
        realName: account.realName || '',
        phone: account.phone || '',
        email: account.email || '',
        avatar: account.avatar || '',
        gender: account.gender ?? 0,
        roleCode: account.roleCode,
        roleName: account.roleName,
        createTime: account.createTime || '',
        lastLoginTime: account.lastLoginTime || ''
      })
    }
  },
  {
    method: 'PUT',
    path: '/user/profile',
    handler: (ctx) => {
      const account = currentAccount(ctx)
      if (!account) return fail(401, '登录状态已失效')

      const { realName, email, gender, avatar } = ctx.body
      if (realName !== undefined) account.realName = String(realName)
      if (email !== undefined) account.email = String(email)
      if (gender !== undefined) account.gender = Number(gender)
      if (avatar !== undefined) account.avatar = String(avatar)

      return ok(null, '保存成功')
    }
  },
  {
    method: 'PUT',
    path: '/user/password',
    handler: (ctx) => {
      const account = currentAccount(ctx)
      if (!account) return fail(401, '登录状态已失效')

      const { oldPassword, newPassword, confirmPassword } = ctx.body
      if (String(oldPassword || '').length < 6) return fail(1003, '原密码不正确')
      if (String(newPassword || '').length < 6 || String(newPassword || '').length > 20)
        return fail(400, '新密码需为 6-20 位')
      if (newPassword !== confirmPassword) return fail(400, '两次输入的新密码不一致')
      if (newPassword === oldPassword) return fail(400, '新密码不能与原密码相同')

      account.password = String(newPassword)
      return ok(null, '密码修改成功')
    }
  }
]
