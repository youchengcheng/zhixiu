import {
  addSession,
  appendMessage,
  clearDraft,
  findSession,
  getDraft,
  lockSession,
  messagesOf,
  nextAiId,
  removeSession,
  sessionsOf,
  setSessionResolved,
  unlockSession
} from '../data/ai'
import { datetime, fail, ok } from '../utils'

function requireOwner(auth) {
  if (!auth || auth.roleCode !== 'OWNER') return fail(403, '无权限访问该资源')
  return null
}

function resolveSession(auth, id) {
  const session = findSession(id)
  if (!session) return { error: fail(404, '会话不存在') }
  if (session.userId !== auth.userId) return { error: fail(403, '无权限访问该会话') }
  return { session }
}

function buildOrderNo() {
  const stamp = datetime().replace(/[- :]/g, '')
  return 'WO' + stamp + String(Math.floor(Math.random() * 10000)).padStart(4, '0')
}

export const aiHandlers = [
  {
    method: 'POST',
    path: '/ai/session',
    handler: ({ auth, body }) => {
      const denied = requireOwner(auth)
      if (denied) return denied
      const now = datetime()
      const session = {
        id: nextAiId(),
        userId: auth.userId,
        title: String(body.title || '').trim() || '新的报修咨询',
        messageCount: 0,
        resolved: 0,
        createTime: now,
        updateTime: now
      }
      addSession(session)
      return ok({ sessionId: session.id })
    }
  },
  {
    method: 'GET',
    path: '/ai/session/list',
    handler: ({ auth }) => {
      const denied = requireOwner(auth)
      return denied || ok(sessionsOf(auth.userId))
    }
  },
  {
    method: 'GET',
    path: '/ai/session/:id/messages',
    handler: ({ auth, pathParams }) => {
      const denied = requireOwner(auth)
      if (denied) return denied
      const { error } = resolveSession(auth, pathParams.id)
      return error || ok(messagesOf(pathParams.id))
    }
  },
  {
    method: 'GET',
    path: '/ai/session/:id/draft',
    handler: ({ auth, pathParams }) => {
      const denied = requireOwner(auth)
      if (denied) return denied
      const { error } = resolveSession(auth, pathParams.id)
      if (error) return error
      const draft = getDraft(pathParams.id)
      return draft ? ok(draft) : fail(3002, '无有效草稿或草稿已过期')
    }
  },
  {
    method: 'POST',
    path: '/ai/session/:id/confirm',
    handler: ({ auth, pathParams, body }) => {
      const denied = requireOwner(auth)
      if (denied) return denied
      const { session, error } = resolveSession(auth, pathParams.id)
      if (error) return error
      if (!body.draftId) return fail(400, '草稿 ID 不能为空')
      if (!lockSession(session.id)) return fail(409, '当前会话正在处理，请稍后重试')
      try {
        // 正式提交成功后，同一草稿的重试返回原工单，不再创建或计数。
        if (session.resolved === 2 && String(session.confirmedDraftId) === String(body.draftId)) {
          return ok({ orderId: session.orderId, orderNo: session.orderNo }, '工单已提交')
        }
        const draft = getDraft(session.id)
        if (!draft || String(draft.draftId) !== String(body.draftId)) {
          return fail(3002, '无有效草稿或草稿已过期，请重新生成')
        }
        if (!body.propertyId) return fail(400, '请选择报修房产')
        if (!/^1[3-9]\d{9}$/.test(String(body.contactPhone || '').trim())) {
          return fail(400, '联系电话格式不正确')
        }
        const form = { ...draft.form }
        for (const key of [
          'categoryId', 'title', 'description', 'urgency',
          'faultDuration', 'affectScope', 'safetyFlag', 'deviceInfo'
        ]) {
          const value = body[key]
          if (value != null && (typeof value !== 'string' || value.trim())) form[key] = value
        }
        if (!form.categoryId || !form.title || !form.description) {
          return fail(400, '请补充报修类型、标题和故障描述')
        }
        const orderId = nextAiId()
        const orderNo = buildOrderNo()
        // 关联信息仅模拟现有列表响应中的工单关联，不扩展数据库结构。
        Object.assign(session, { orderId, orderNo, confirmedDraftId: draft.draftId })
        setSessionResolved(session.id, 2)
        appendMessage(session.id, {
          sender: 'AI',
          content: '工单已提交，工单号 **' + orderNo + '**。可在「我的工单」里跟踪处理进度。',
          intent: 'FILL_FORM',
          formDraft: form
        })
        clearDraft(session.id)
        return ok({ orderId, orderNo }, '工单已提交')
      } finally {
        unlockSession(session.id)
      }
    }
  },
  {
    method: 'DELETE',
    path: '/ai/session/:id',
    handler: ({ auth, pathParams }) => {
      const denied = requireOwner(auth)
      if (denied) return denied
      const { error } = resolveSession(auth, pathParams.id)
      if (error) return error
      if (!lockSession(pathParams.id)) return fail(409, '当前会话正在处理，请稍后重试')
      try {
        removeSession(pathParams.id)
        return ok(null, '会话已删除')
      } finally {
        unlockSession(pathParams.id)
      }
    }
  }
]
