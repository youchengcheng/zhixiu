// Mock 通用工具：统一响应体、分页、延迟、参数解析

/** 统一响应体：成功 */
export function ok(data = null, message = 'success') {
  return { code: 200, message, data }
}

/** 统一响应体：业务失败（业务码见开发文档 1.3） */
export function fail(code, message, data = null) {
  return { code, message, data }
}

/** 分页响应：与后端 PageResult 字段保持一致 */
export function paginate(list, params = {}) {
  const pageNum = Number(params.pageNum || 1)
  const pageSize = Number(params.pageSize || 10)
  const total = list.length
  const pages = Math.max(1, Math.ceil(total / pageSize))
  const start = (pageNum - 1) * pageSize
  return {
    total,
    pages,
    current: pageNum,
    size: pageSize,
    records: list.slice(start, start + pageSize)
  }
}

export function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

// 常规 200-600ms；保留少量慢请求，用于验证骨架屏
export function mockDelay() {
  if (Math.random() < 0.08) return 1200
  return 200 + Math.floor(Math.random() * 400)
}

export function parseBody(data) {
  if (!data) return {}
  if (typeof data === 'string') {
    try {
      return JSON.parse(data)
    } catch {
      return {}
    }
  }
  if (typeof FormData !== 'undefined' && data instanceof FormData) {
    const result = {}
    data.forEach((value, key) => {
      result[key] = value
    })
    return result
  }
  return data
}

export function parseQuery(url = '') {
  const index = url.indexOf('?')
  if (index < 0) return {}
  const result = {}
  new URLSearchParams(url.slice(index + 1)).forEach((value, key) => {
    result[key] = value
  })
  return result
}

/** 关键字模糊匹配 */
export function like(source, keyword) {
  if (!keyword) return true
  return String(source || '').toLowerCase().includes(String(keyword).trim().toLowerCase())
}

/** 生成后端风格的时间字符串 yyyy-MM-dd HH:mm:ss */
export function datetime(date = new Date()) {
  const pad = (value) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(
    date.getHours()
  )}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

export function addMinutes(base, minutes) {
  return new Date(new Date(base).getTime() + minutes * 60000)
}

/** 单调递增的雪花风格 ID，保证同一次会话内不重复 */
let idSeed = 8800000000000000
export function nextId() {
  idSeed += 7
  return String(idSeed)
}
