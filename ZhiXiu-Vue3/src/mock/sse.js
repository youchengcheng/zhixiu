import {
  aiMessages,
  appendMessage,
  clearDraft,
  countUserMessages,
  findSession,
  getDraft,
  lockSession,
  messagesOf,
  setDraft,
  setSessionResolved,
  unlockSession
} from './data/ai'
import { parseToken } from './data/accounts'
import { getToken } from '../utils/auth'

// 演示模式沿用真实 SSE 的动作、消息 ID、草稿 TTL 和会话终态。
const UNRESOLVED_TEXT = '问题还没有解决，请提供其他排查方案，并询问还需要哪些详细信息。'

function businessError(code, message) {
  return Object.assign(new Error(message), { code })
}

function userContext(sessionId) {
  return (aiMessages[String(sessionId)] || [])
    .filter((item) => item.sender === 'USER' && item.content !== UNRESOLVED_TEXT)
    .map((item) => item.content)
    .join('\n')
}

function buildPlan(sessionId) {
  const context = userContext(sessionId)
  const turn = countUserMessages(sessionId)
  if (/水槽|排水|下水|堵塞/.test(context)) {
    const plans = [
      '先检查水槽滤网是否积有残渣，再用温水少量冲洗，观察排水是否改善。不要混用疏通剂。\n\n水位会回涌吗？问题只出现在厨房水槽，还是其他排水口也变慢？',
      '可以换个方向，观察水槽下方是否渗水，以及排水时是否有明显的咕噜声。先做外观检查，不要强行拆开管道。\n\n排水变慢持续多久了？最近是否往水槽倒过油脂或食物残渣？',
      '还可以对比其他排水口：分别少量放水，观察是否同时回涌；如果多处异常，可能需要检查共用管道。若已出现溢水，先停止用水。\n\n其他排水口也有异常吗？水槽是一直排水慢，还是放水较多时才出现？'
    ]
    return { text: plans[(turn - 1) % plans.length], citations: ['305'] }
  }
  if (/空调|制冷|外机/.test(context)) {
    const plans = [
      '先帮你做几步自助排查：\n\n1. 确认遥控器处于 **制冷** 模式，设定温度低于室温；\n2. 观察外机出风口是否被遮挡，滤网是否积灰；\n3. 在设备说明允许的情况下，停机后再重新开启，观察是否恢复。\n\n外机有明显异响时先停机，避免持续运行。\n\n内机是否有正常出风？异响是在启动时出现，还是持续存在？',
      '可以继续检查另外两个方向：观察遥控器是否开启了定时或睡眠模式，以及显示屏是否有故障代码。只做外观检查，不要拆机或接触线路。\n\n显示屏有没有故障代码？不制冷是突然发生，还是逐渐变差的？',
      '还可以对比风量和声音变化：保持出风口无遮挡，在安全范围内观察内机是否有风、外机是否启动。明显异响或焦味出现时，先停机。\n\n设备是什么品牌和型号？异常是否只影响这台空调？'
    ]
    return { text: plans[(turn - 1) % plans.length], citations: ['301', '302'] }
  }
  return {
    text: turn === 1
      ? '先从安全的外观检查开始：确认设备是否有松动、渗水、异响或指示灯异常，再对照设备说明检查设置。发现冒烟、焦味或漏电迹象时，先停止使用。\n\n具体是哪台设备或哪个位置出现问题？异常持续多久了？'
      : '继续换一个排查方向：可以比较异常发生前后的使用条件，观察是否只在特定时段或操作后出现，不要拆开设备或接触带电部件。\n\n目前已经检查了哪些项目，结果分别是什么？有没有故障代码、照片或其他明显变化？',
    citations: []
  }
}

function buildDraftForm(session) {
  const context = userContext(session.id)
  const airConditioner = /空调|制冷|外机/.test(context)
  const drain = /水槽|排水|下水|堵塞/.test(context)
  return {
    categoryId: airConditioner ? '201' : drain ? '103' : null,
    categoryPath: airConditioner ? '电器维修 > 空调' : drain ? '水管维修 > 下水道' : '',
    title: session.title || '设备故障咨询',
    description: context.slice(0, 1000),
    urgency: 2,
    faultDuration: '',
    affectScope: /整栋/.test(context) ? 3 : /本层/.test(context) ? 2 : 1,
    safetyFlag: airConditioner ? '涉电' : '',
    deviceInfo: context.match(/\bKFR-[A-Z0-9/-]+/i)?.[0] || ''
  }
}

/** 模拟 POST /api/ai/chat，只有 GENERATE_DRAFT 才预填写。 */
export function mockChatStream({ sessionId, content, action = 'CHAT', messageId, onEvent, onDone, onError }) {
  let cancelled = false
  const timers = new Map()
  const wait = (ms) => new Promise((resolve) => {
    const timer = setTimeout(() => {
      timers.delete(timer)
      resolve()
    }, ms)
    timers.set(timer, resolve)
  })
  const emit = (event, data) => {
    if (!cancelled) onEvent?.(event, data)
  }
  const done = (id, session) => emit('done', {
    messageId: id,
    resolved: session.resolved === 1,
    sessionResolved: session.resolved,
    hasDraft: Boolean(getDraft(sessionId))
  })

  async function typeOut(text) {
    for (let i = 0; i < text.length; i += 6) {
      if (cancelled) return
      emit('message', { delta: text.slice(i, i + 6) })
      await wait(22)
    }
  }

  async function run() {
    // 反馈动作也保持异步，调用方可以先获得中断函数。
    await wait(20)
    if (cancelled) return
    const auth = parseToken(getToken())
    if (!auth) throw businessError(401, '登录已过期，请重新登录')
    if (auth.roleCode !== 'OWNER') throw businessError(403, '无权限访问该资源')
    const session = findSession(sessionId)
    if (!session) throw businessError(404, '会话不存在')
    if (session.userId !== auth.userId) throw businessError(403, '无权限访问该会话')
    if (!['CHAT', 'UNRESOLVED', 'GENERATE_DRAFT', 'RESOLVED'].includes(action)) {
      throw businessError(400, '不支持的会话动作')
    }
    if (action === 'CHAT' && (!String(content || '').trim() || String(content).length > 2000)) {
      throw businessError(400, '请填写 2000 字以内的问题')
    }
    if (action !== 'CHAT' && !messageId) throw businessError(400, '请选择最新一轮回答')
    if (!lockSession(sessionId)) throw businessError(409, '当前会话正在处理，请稍后重试')
    try {
      const latest = messagesOf(sessionId).at(-1)
      if (action !== 'CHAT' && (latest?.sender !== 'AI' || latest.id !== String(messageId))) {
        throw businessError(409, '请操作最新一轮回答')
      }
      if (action === 'RESOLVED' && session.resolved === 1) {
        done(latest.id, session)
        return
      }
      if (session.resolved !== 0) throw businessError(3002, '会话已结束，请新建会话继续咨询')

      if (action === 'RESOLVED') {
        setSessionResolved(sessionId, 1)
        clearDraft(sessionId)
        done(latest.id, session)
        return
      }
      if (action === 'GENERATE_DRAFT') {
        const existing = getDraft(sessionId)
        if (existing) {
          emit('draft', existing)
          done(latest.id, session)
          return
        }
        const form = buildDraftForm(session)
        const missing = ['categoryId', 'faultDuration', 'deviceInfo'].filter((key) => !form[key])
        const previousQuestions = (aiMessages[String(sessionId)] || [])
          .filter((item) => item.sender === 'AI' && item.intent === 'FILL_FORM' && item.missingSlots).length
        const question = missing.length && previousQuestions < 2
          ? '请再补充故障持续时间和设备型号；不确定的信息可先在草稿中核对。'
          : ''
        const summary = '已按你的选择整理工单草稿，保留 15 分钟。请核对并补充信息，确认报修后才会提交工单。'
        emit('intent', { intent: 'FILL_FORM' })
        await typeOut(summary)
        if (cancelled) return
        emit('slot', {
          filled: Object.keys(form).filter((key) => key !== 'categoryPath' && form[key]),
          missing
        })
        if (question) emit('followup', { question })
        const record = appendMessage(sessionId, {
          sender: 'AI',
          content: summary + (question ? '\n\n' + question : ''),
          intent: 'FILL_FORM',
          formDraft: form,
          missingSlots: missing.join(',') || null
        })
        setDraft(sessionId, {
          draftId: record.id,
          form,
          expiresAtMs: Date.now() + 15 * 60 * 1000
        })
        emit('draft', getDraft(sessionId))
        done(record.id, session)
        return
      }

      const userContent = action === 'UNRESOLVED' ? UNRESOLVED_TEXT : String(content).trim()
      if (!session.messageCount && session.title === '新的报修咨询') {
        session.title = userContent.slice(0, 16)
      }
      appendMessage(sessionId, { sender: 'USER', content: userContent })
      emit('intent', { intent: 'TROUBLESHOOT' })
      const plan = buildPlan(sessionId)
      await typeOut(plan.text)
      if (cancelled) return
      if (plan.citations.length) emit('citations', { ids: plan.citations })
      const record = appendMessage(sessionId, {
        sender: 'AI',
        content: plan.text,
        intent: 'TROUBLESHOOT',
        citations: plan.citations.join(',') || null
      })
      done(record.id, session)
    } finally {
      unlockSession(sessionId)
    }
  }

  run().then(() => {
    if (!cancelled) onDone?.()
  }).catch((error) => {
    if (cancelled) return
    if (error.code) emit('error', { code: error.code, message: error.message })
    else onError?.(error)
  })

  return () => {
    cancelled = true
    timers.forEach((resolve, timer) => {
      clearTimeout(timer)
      resolve()
    })
    timers.clear()
  }
}
