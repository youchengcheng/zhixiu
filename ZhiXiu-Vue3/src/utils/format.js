import { DATETIME_FORMAT } from './env'

// 后端统一返回 "yyyy-MM-dd HH:mm:ss"，Safari 需要把短横线替换掉才能解析
export function toDate(value) {
  if (value === null || value === undefined || value === '') return null
  if (value instanceof Date) return value
  if (typeof value === 'number') return new Date(value)
  const text = String(value).trim()
  const normalized = text.includes('T') ? text : text.replace(/-/g, '/')
  const date = new Date(normalized)
  return Number.isNaN(date.getTime()) ? null : date
}

function pad(value) {
  return String(value).padStart(2, '0')
}

export function formatDateTime(value, fallback = '-') {
  const date = toDate(value)
  if (!date) return fallback
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(
    date.getHours()
  )}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

export function formatDate(value, fallback = '-') {
  const date = toDate(value)
  if (!date) return fallback
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

export function formatTime(value, fallback = '-') {
  const date = toDate(value)
  if (!date) return fallback
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`
}

// 相对时间：列表里更易读，绝对时间由组件放到 tooltip 里
export function fromNow(value, fallback = '-') {
  const date = toDate(value)
  if (!date) return fallback

  const diff = Date.now() - date.getTime()
  const minute = 60000
  const hour = 60 * minute
  const day = 24 * hour

  if (diff < 0) return formatDateTime(date)
  if (diff < minute) return '刚刚'
  if (diff < hour) return `${Math.floor(diff / minute)} 分钟前`
  if (diff < day) return `${Math.floor(diff / hour)} 小时前`
  if (diff < 2 * day) return `昨天 ${formatTime(date)}`
  if (diff < 7 * day) return `${Math.floor(diff / day)} 天前`
  return formatDate(date)
}

// 剩余时长：用于 SLA 倒计时
export function untilNow(value) {
  const date = toDate(value)
  if (!date) return null
  const diff = date.getTime() - Date.now()
  if (diff <= 0) return { expired: true, text: '已超时', minutes: 0 }
  const minutes = Math.floor(diff / 60000)
  if (minutes < 60) return { expired: false, text: `剩余 ${minutes} 分钟`, minutes }
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return { expired: false, text: `剩余 ${hours} 小时`, minutes }
  return { expired: false, text: `剩余 ${Math.floor(hours / 24)} 天`, minutes }
}

export function formatNumber(value, digits = 0, fallback = '-') {
  const num = Number(value)
  if (value === null || value === undefined || value === '' || Number.isNaN(num)) return fallback
  return num.toLocaleString('zh-CN', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits
  })
}

// 比率（0.342）→ 百分比文本（34.2%）
export function formatPercent(ratio, digits = 1, fallback = '-') {
  const num = Number(ratio)
  if (ratio === null || ratio === undefined || ratio === '' || Number.isNaN(num)) return fallback
  return `${(num * 100).toFixed(digits)}%`
}

export function formatMinutes(minutes, fallback = '-') {
  const num = Number(minutes)
  if (minutes === null || minutes === undefined || minutes === '' || Number.isNaN(num))
    return fallback
  if (num < 60) return `${num} 分钟`
  const hours = Math.floor(num / 60)
  const rest = num % 60
  return rest === 0 ? `${hours} 小时` : `${hours} 小时 ${rest} 分`
}

export function maskPhone(phone) {
  const text = String(phone || '')
  if (text.length < 7) return text || '-'
  return `${text.slice(0, 3)}****${text.slice(-4)}`
}

export function maskIdCard(idCard) {
  const text = String(idCard || '')
  if (text.length < 8) return text || '-'
  return `${text.slice(0, 4)}**********${text.slice(-4)}`
}

// 姓名取末位一到两个字做字母头像，比默认人像占位更真实
export function nameInitials(name) {
  const text = String(name || '').trim()
  if (!text) return '·'
  return text.length <= 2 ? text : text.slice(-2)
}

export function formatFileSize(bytes) {
  const num = Number(bytes || 0)
  if (num < 1024) return `${num} B`
  if (num < 1024 * 1024) return `${(num / 1024).toFixed(1)} KB`
  return `${(num / 1024 / 1024).toFixed(1)} MB`
}
