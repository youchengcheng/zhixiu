import { findAccountById } from '../data/accounts'
import { feedbacks, findFeedback } from '../data/feedback'
import { datetime, fail, nextId, ok, paginate } from '../utils'

function requireAdmin(auth) {
  if (!auth || auth.roleCode !== 'ADMIN') return fail(403, '无权限访问该资源')
  return null
}

function displayName(auth) {
  const account = auth ? findAccountById(auth.userId) : null
  return account?.realName || account?.username || '当前用户'
}

export const feedbackHandlers = [
  {
    method: 'POST',
    path: '/feedback',
    handler: ({ auth, body }) => {
      const type = String(body.type || '').trim()
      const title = String(body.title || '').trim()
      const content = String(body.content || '').trim()

      if (!type) return fail(400, '请选择反馈类型')
      if (!title) return fail(400, '请填写标题')
      if (!content) return fail(400, '请填写反馈内容')

      const now = datetime()
      const item = {
        id: nextId(),
        userId: auth.userId,
        userName: displayName(auth),
        userRole: auth.roleCode,
        type,
        title,
        content,
        contact: String(body.contact || '').trim(),
        status: 0,
        reply: null,
        replierId: null,
        replierName: null,
        replyTime: null,
        createTime: now,
        updateTime: now
      }
      feedbacks.unshift(item)
      return ok({ id: item.id }, '反馈已提交，我们会尽快处理')
    }
  },
  {
    method: 'GET',
    path: '/feedback/my',
    handler: ({ auth, params }) => {
      // 只返回当前用户自己提交的反馈，按时间倒序
      const list = feedbacks
        .filter((item) => item.userId === auth.userId)
        .sort((a, b) => new Date(b.createTime).getTime() - new Date(a.createTime).getTime())
      return ok(paginate(list, params))
    }
  },
  {
    method: 'GET',
    path: '/admin/feedback/list',
    handler: ({ auth, params }) => {
      const denied = requireAdmin(auth)
      if (denied) return denied

      const status = params.status === undefined || params.status === '' ? null : Number(params.status)
      const type = params.type ? String(params.type) : ''

      const list = feedbacks
        .filter((item) => (status === null ? true : item.status === status))
        .filter((item) => (type ? item.type === type : true))
        .sort((a, b) => new Date(b.createTime).getTime() - new Date(a.createTime).getTime())
      return ok(paginate(list, params))
    }
  },
  {
    method: 'POST',
    path: '/admin/feedback/:id/reply',
    handler: ({ auth, pathParams, body }) => {
      const denied = requireAdmin(auth)
      if (denied) return denied

      const item = findFeedback(pathParams.id)
      if (!item) return fail(404, '反馈不存在')

      const reply = String(body.reply || '').trim()
      if (!reply) return fail(400, '请填写回复内容')

      const status = body.status === undefined || body.status === null ? null : Number(body.status)
      if (status !== null && ![1, 2, 3].includes(status)) return fail(400, '反馈状态不合法')

      item.reply = reply
      item.replierId = auth.userId
      item.replierName = displayName(auth)
      item.replyTime = datetime()
      // 未显式指定状态时，回复即视为「处理中」
      item.status = status ?? 1
      item.updateTime = item.replyTime
      return ok(null, '回复已发送')
    }
  }
]
