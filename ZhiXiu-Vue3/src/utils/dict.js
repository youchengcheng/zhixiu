import { untilNow } from './format'

// ============================================================================
// 字典唯一维护处：页面禁止硬编码中文状态与颜色
// tone 决定 StatusTag / UrgencyTag 的视觉轨道，取值见 components/StatusTag.vue
// ============================================================================

export const ORDER_STATUS = {
  PENDING: { text: '待接单', tone: 'warning' },
  ASSIGNED: { text: '已派单', tone: 'info' },
  ACCEPTED: { text: '已接单', tone: 'info-strong' },
  GOING: { text: '处理中', tone: 'primary' },
  PAUSED: { text: '已暂停', tone: 'warning-strong' },
  TRANSFER_PENDING: { text: '转单申请中', tone: 'neutral' },
  SUBMITTED: { text: '已提交完工', tone: 'brand-soft' },
  COMPLETED: { text: '已完工', tone: 'success' },
  CLOSED: { text: '已关闭', tone: 'neutral' },
  CANCELED: { text: '已取消', tone: 'danger' }
}

// 业主可取消的状态
export const OWNER_CANCELABLE_STATUS = ['PENDING', 'ASSIGNED', 'ACCEPTED']

// 进行中（未终结）状态：首页概览、师傅工作台用它统计算在办的工单
export const ORDER_ACTIVE_STATUS = [
  'PENDING',
  'ASSIGNED',
  'ACCEPTED',
  'GOING',
  'PAUSED',
  'TRANSFER_PENDING',
  'SUBMITTED'
]

// 已完结状态：完工、关闭与取消
export const ORDER_FINISHED_STATUS = ['COMPLETED', 'CLOSED', 'CANCELED']

export const URGENCY = {
  1: { text: '低', tone: 'neutral' },
  2: { text: '中', tone: 'info' },
  3: { text: '高', tone: 'warning' },
  4: { text: '紧急', tone: 'danger' }
}

export const AFFECT_SCOPE = {
  1: { text: '本户', tone: 'neutral' },
  2: { text: '本层', tone: 'neutral' },
  3: { text: '整栋', tone: 'neutral' }
}

export const SAFETY_FLAGS = ['涉电', '涉水', '涉气', '高空']

// 师傅技能标签：注册页与师傅管理页共用，允许在选择框中自由输入追加
export const WORKER_SKILL_SUGGESTIONS = [
  '水管维修',
  '电路检修',
  '家电维修',
  '门窗五金',
  '下水疏通',
  '墙面防水',
  '燃气灶具',
  '空调维保'
]

export const ORDER_SOURCE = {
  MANUAL: { text: '手动报修', tone: 'neutral' },
  AI: { text: '智能助手', tone: 'ai' }
}

export const WORK_STATUS = {
  0: { text: '审核中', tone: 'neutral' },
  1: { text: '在线', tone: 'success' },
  2: { text: '离线', tone: 'neutral' },
  3: { text: '忙碌', tone: 'warning' },
  4: { text: '禁用', tone: 'danger' }
}

export const AUDIT_STATUS = {
  0: { text: '待审核', tone: 'warning' },
  1: { text: '已通过', tone: 'success' },
  2: { text: '已驳回', tone: 'danger' }
}

export const FEEDBACK_STATUS = {
  0: { text: '待处理', tone: 'warning' },
  1: { text: '处理中', tone: 'info' },
  2: { text: '已处理', tone: 'success' },
  3: { text: '已关闭', tone: 'neutral' }
}

export const FEEDBACK_TYPE = {
  BUG: { text: '功能异常', tone: 'danger' },
  SUGGESTION: { text: '优化建议', tone: 'info' },
  COMPLAINT: { text: '投诉', tone: 'warning' }
}

export const ANNOUNCEMENT_TYPE = {
  SYSTEM: { text: '系统通知', tone: 'info' },
  MAINTENANCE: { text: '维护公告', tone: 'warning' },
  ACTIVITY: { text: '社区活动', tone: 'success' }
}

export const ANNOUNCEMENT_TARGET = {
  ALL: { text: '全部用户', tone: 'neutral' },
  OWNER: { text: '业主', tone: 'neutral' },
  WORKER: { text: '维修师傅', tone: 'neutral' }
}

export const ANNOUNCEMENT_STATUS = {
  0: { text: '草稿', tone: 'neutral' },
  1: { text: '已发布', tone: 'success' }
}

export const AI_INTENT = {
  QA: { text: '问答', tone: 'info' },
  TROUBLESHOOT: { text: '自助排查', tone: 'ai' },
  FILL_FORM: { text: '信息收集', tone: 'primary' }
}

export const AI_SESSION_RESOLVED = {
  0: { text: '进行中', tone: 'neutral' },
  1: { text: '自助解决', tone: 'success' },
  2: { text: '已转工单', tone: 'primary' }
}

export const ROLE = {
  OWNER: { text: '业主', tone: 'info' },
  WORKER: { text: '维修师傅', tone: 'primary' },
  ADMIN: { text: '物业管理员', tone: 'warning' }
}

export const LOG_RESULT = {
  0: { text: '失败', tone: 'danger' },
  1: { text: '成功', tone: 'success' }
}

export const GENDER = {
  0: { text: '未填写', tone: 'neutral' },
  1: { text: '男', tone: 'neutral' },
  2: { text: '女', tone: 'neutral' }
}

export const KNOWLEDGE_VECTORIZED = {
  0: { text: '未向量化', tone: 'warning' },
  1: { text: '已向量化', tone: 'success' }
}

export const ENABLED_STATUS = {
  0: { text: '已禁用', tone: 'neutral' },
  1: { text: '已启用', tone: 'success' }
}

export const USER_STATUS = {
  0: { text: '已禁用', tone: 'danger' },
  1: { text: '正常', tone: 'success' }
}

// 工单操作日志动作
export const ORDER_LOG_ACTION = {
  CREATE: '创建工单',
  ASSIGN: '派单',
  ACCEPT: '接单',
  GRAB: '抢单',
  ARRIVE: '到场签到',
  PAUSE: '暂停',
  RESUME: '恢复',
  TRANSFER: '申请转单',
  SYSTEM_SUBMIT: '提交完工',
  COMPLETE: '确认完工',
  EVALUATE: '提交评价',
  CANCEL: '取消工单',
  ESCALATE: 'SLA 升级'
}

export const OPERATOR_TYPE = {
  OWNER: '业主',
  WORKER: '维修师傅',
  ADMIN: '物业管理员',
  SYSTEM: '系统'
}

// ---------- 通用取值助手 ----------

function pick(dict, value) {
  if (value === null || value === undefined) return null
  return dict[value] ?? dict[String(value)] ?? null
}

export function dictText(dict, value, fallback = '-') {
  return pick(dict, value)?.text ?? fallback
}

export function dictTone(dict, value, fallback = 'neutral') {
  return pick(dict, value)?.tone ?? fallback
}

// 用于 el-select / el-radio-group：数字键的字典会转成 number
export function dictOptions(dict) {
  return Object.entries(dict).map(([value, item]) => ({
    value: /^\d+$/.test(value) ? Number(value) : value,
    label: item.text
  }))
}

export function dictEntries(dict) {
  return Object.entries(dict).map(([value, item]) => ({
    value: /^\d+$/.test(value) ? Number(value) : value,
    ...item
  }))
}

// ---------- SLA 状态：由截止时间与超时标记推导 ----------

export function slaState(order) {
  if (!order) return { text: '未设定', tone: 'neutral' }

  if (order.slaResponseBreached || order.slaHandleBreached) {
    return { text: '已超时', tone: 'danger' }
  }

  const left = untilNow(order.slaHandleDeadline)
  if (!left) return { text: '未设定', tone: 'neutral' }
  if (left.expired) return { text: '已超时', tone: 'danger' }
  if (left.minutes <= 60) return { text: '即将超时', tone: 'warning' }
  return { text: '正常', tone: 'neutral' }
}
