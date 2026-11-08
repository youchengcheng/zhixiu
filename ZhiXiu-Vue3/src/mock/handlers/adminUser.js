import { findAccountById } from '../data/accounts'
import { fail, ok } from '../utils'

function adminOnly(auth) {
  if (!auth || auth.roleCode !== 'ADMIN') return fail(403, '无权限访问该资源')
  return null
}

export const adminUserHandlers = [
  {
    method: 'PUT',
    path: '/admin/user/:id/reset-password',
    handler: ({ auth, pathParams, body }) => {
      const denied = adminOnly(auth)
      if (denied) return denied

      const account = findAccountById(pathParams.id)
      if (!account) return fail(404, '用户不存在')
      if (account.roleCode === 'ADMIN') return fail(403, '不能重置其他管理员密码')
      if (!body.newPassword || body.newPassword.length < 6 || body.newPassword.length > 20) {
        return fail(400, '新密码长度需 6-20 位')
      }
      if (body.newPassword !== body.confirmPassword) return fail(400, '两次输入的密码不一致')

      account.password = body.newPassword
      return ok(null, '密码重置成功')
    }
  }
]
