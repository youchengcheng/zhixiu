// AI 助手 mock 数据：会话 / 消息 / 工单草稿
// 字段与 ai_chat_session、ai_chat_message 两张表对齐；对外输出结构按开发文档 6.1~6.7 的响应示例裁剪

import { datetime } from '../utils'

// 会话（ai_chat_session）
const seedSessions = [
  {
    id: '9001',
    userId: '10001',
    title: '卧室空调不制冷且外机异响',
    messageCount: 7,
    resolved: 2,
    createTime: '2026-10-15 14:00:00',
    updateTime: '2026-10-15 14:04:12'
  },
  {
    id: '9002',
    userId: '10001',
    title: '厨房水槽下水慢',
    messageCount: 4,
    resolved: 1,
    createTime: '2026-10-12 09:20:00',
    updateTime: '2026-10-12 09:26:00'
  }
]

// 消息（ai_chat_message）
const seedMessages = {
  9001: [
    {
      id: '1',
      sender: 'USER',
      content: '卧室空调不制冷，外机声音很大',
      intent: null,
      citations: null,
      formDraft: null,
      filledSlots: null,
      missingSlots: null,
      createTime: '2026-10-15 14:00:10'
    },
    {
      id: '2',
      sender: 'AI',
      content:
        '先帮你做几步自助排查，通常能定位到多数「不制冷」问题：\n\n1. 确认遥控器处于 **制冷** 模式，温度设到 26℃ 以下；\n2. 检查外机周围是否有遮挡，出风口是否被堵；\n3. 断电 5 分钟后重新上电，观察外机是否能正常启动。\n\n外机有明显异响时不要强行长时间运行，避免压缩机损伤。',
      intent: 'TROUBLESHOOT',
      citations: '301,302',
      formDraft: null,
      filledSlots: 'categoryId,urgency',
      missingSlots: 'affectScope,deviceInfo',
      createTime: '2026-10-15 14:00:32'
    },
    {
      id: '3',
      sender: 'USER',
      content: '试过了，还是不行，空调是格力 KFR-35GW',
      intent: null,
      citations: null,
      formDraft: null,
      filledSlots: null,
      missingSlots: null,
      createTime: '2026-10-15 14:02:05'
    },
    {
      id: '4',
      sender: 'AI',
      content: '了解，设备型号已记录。请再确认一下：这种情况影响的是 **本户**、**本层** 还是 **整栋**？',
      intent: 'FILL_FORM',
      citations: null,
      formDraft: null,
      filledSlots: 'categoryId,urgency,deviceInfo',
      missingSlots: 'affectScope',
      createTime: '2026-10-15 14:02:26'
    },
    {
      id: '5',
      sender: 'USER',
      content: '就我家这一户',
      intent: null,
      citations: null,
      formDraft: null,
      filledSlots: null,
      missingSlots: null,
      createTime: '2026-10-15 14:03:40'
    },
    {
      id: '6',
      sender: 'AI',
      content: '信息已收集完整，我整理成了一份工单草稿，可在右侧核对并确认提交。',
      intent: 'FILL_FORM',
      citations: null,
      formDraft: null,
      filledSlots: 'categoryId,urgency,deviceInfo,affectScope',
      missingSlots: null,
      createTime: '2026-10-15 14:03:58'
    },
    {
      id: '7',
      sender: 'AI',
      content: '工单已提交，工单号 **WO20261015140412**。师傅会在 SLA 时限内响应，可在「我的工单」里跟踪进度。',
      intent: 'FILL_FORM',
      citations: null,
      formDraft: null,
      filledSlots: null,
      missingSlots: null,
      createTime: '2026-10-15 14:04:12'
    }
  ],
  9002: [
    {
      id: '11',
      sender: 'USER',
      content: '厨房水槽下水很慢，洗碗后要很久才排完',
      intent: null,
      citations: null,
      formDraft: null,
      filledSlots: null,
      missingSlots: null,
      createTime: '2026-10-12 09:20:12'
    },
    {
      id: '12',
      sender: 'AI',
      content:
        '排水变慢多数是存水弯或滤网积了油污，可以这样处理：\n\n1. 拆下滤网清掉食物残渣；\n2. 用 **60℃ 左右的热水 + 少量小苏打** 冲洗存水弯；\n3. 不要倒入大量油脂。\n\n如果水位还会回涌，可能存在硬堵塞，需要师傅上门疏通。',
      intent: 'QA',
      citations: '305',
      formDraft: null,
      filledSlots: null,
      missingSlots: null,
      createTime: '2026-10-12 09:20:40'
    },
    {
      id: '13',
      sender: 'USER',
      content: '清理滤网后排水恢复正常了，后续应该怎么维护？',
      intent: null,
      citations: null,
      formDraft: null,
      filledSlots: null,
      missingSlots: null,
      createTime: '2026-10-12 09:25:10'
    },
    {
      id: '14',
      sender: 'AI',
      content: '定期清理滤网，避免把油脂和食物残渣直接倒入水槽；如果再次排水变慢，可以先检查滤网。你已确认本次问题解决，感谢反馈。',
      intent: 'QA',
      citations: null,
      formDraft: null,
      filledSlots: null,
      missingSlots: null,
      createTime: '2026-10-12 09:25:40'
    }
  ]
}

// 仅持久化 AI 演示数据，页面刷新后仍可恢复有效草稿。
const STORAGE_KEY = 'zhixiu.mock.ai.v2'
function loadAiState() {
  try {
    return typeof sessionStorage === 'undefined' ? null : JSON.parse(sessionStorage.getItem(STORAGE_KEY) || 'null')
  } catch {
    return null
  }
}
const savedState = loadAiState()
export const aiSessions = Array.isArray(savedState?.sessions) ? savedState.sessions : seedSessions
export const aiMessages = savedState?.messages || seedMessages
export const aiDrafts = savedState?.drafts || {}
let idSeed = Math.max(Date.now() * 1000, Number(savedState?.idSeed || 0))
const sessionLocks = new Set()

export function persistAiState() {
  try {
    if (typeof sessionStorage !== 'undefined') {
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify({
        sessions: aiSessions,
        messages: aiMessages,
        drafts: aiDrafts,
        idSeed
      }))
    }
  } catch {
    // 浏览器禁用存储时仍可在当前页面使用演示模式。
  }
}

export function nextAiId() {
  idSeed += 1
  return String(idSeed)
}

export function lockSession(sessionId) {
  const key = String(sessionId)
  if (sessionLocks.has(key)) return false
  sessionLocks.add(key)
  return true
}

export function unlockSession(sessionId) {
  sessionLocks.delete(String(sessionId))
}

export function addSession(session) {
  aiSessions.unshift(session)
  persistAiState()
}

/** 会话列表：不返回空会话，正式提交后附带关联工单信息。 */
export function sessionsOf(userId) {
  return aiSessions
    .filter((item) => item.userId === String(userId) && item.messageCount > 0)
    .sort((a, b) => b.updateTime.localeCompare(a.updateTime))
    .map((item) => ({
      sessionId: item.id,
      title: item.title,
      messageCount: item.messageCount,
      resolved: item.resolved,
      orderId: item.orderId || null,
      orderNo: item.orderNo || null,
      createTime: item.createTime,
      updateTime: item.updateTime
    }))
}

export function findSession(id) {
  return aiSessions.find((item) => item.id === String(id)) || null
}

/** 历史消息：输出结构与文档 6.4 一致。 */
export function messagesOf(sessionId) {
  return (aiMessages[String(sessionId)] || []).map((item) => ({
    id: item.id,
    sender: item.sender,
    content: item.content,
    intent: item.intent,
    citations: item.citations,
    createTime: item.createTime
  }))
}

export function appendMessage(sessionId, message) {
  const key = String(sessionId)
  if (!aiMessages[key]) aiMessages[key] = []
  const record = {
    id: nextAiId(),
    intent: null,
    citations: null,
    formDraft: null,
    filledSlots: null,
    missingSlots: null,
    createTime: datetime(),
    ...message
  }
  aiMessages[key].push(record)
  const session = findSession(key)
  if (session) {
    session.messageCount = aiMessages[key].length
    session.updateTime = record.createTime
  }
  persistAiState()
  return record
}

export function countUserMessages(sessionId) {
  return (aiMessages[String(sessionId)] || []).filter((item) => item.sender === 'USER').length
}

/** expiresAtMs 为演示内部时间锚点，不出现在接口响应中。 */
export function getDraft(sessionId) {
  const session = findSession(sessionId)
  const draft = aiDrafts[String(sessionId)]
  const now = Date.now()
  if (!session || session.resolved !== 0 || !draft || now >= draft.expiresAtMs) return null
  return {
    draftId: draft.draftId,
    form: draft.form,
    expiresAt: datetime(new Date(draft.expiresAtMs)),
    expiresIn: Math.ceil((draft.expiresAtMs - now) / 1000)
  }
}

export function setDraft(sessionId, draft) {
  aiDrafts[String(sessionId)] = draft
  persistAiState()
}

export function clearDraft(sessionId) {
  delete aiDrafts[String(sessionId)]
  persistAiState()
}

export function setSessionResolved(sessionId, resolved) {
  const session = findSession(sessionId)
  if (session) {
    session.resolved = resolved
    session.updateTime = datetime()
    persistAiState()
  }
}

export function removeSession(id) {
  const key = String(id)
  const index = aiSessions.findIndex((item) => item.id === key)
  if (index < 0) return false
  aiSessions.splice(index, 1)
  delete aiMessages[key]
  clearDraft(key)
  return true
}
