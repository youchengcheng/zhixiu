// Token 存取：唯一读写位置，store 与拦截器都走这里

const TOKEN_KEY = 'zhixiu_token'
const REFRESH_TOKEN_KEY = 'zhixiu_refresh_token'
const USER_KEY = 'zhixiu_user'

function readStorage(key) {
  try {
    return window.localStorage.getItem(key) || ''
  } catch {
    return ''
  }
}

function writeStorage(key, value) {
  try {
    if (value === undefined || value === null || value === '') {
      window.localStorage.removeItem(key)
      return
    }
    window.localStorage.setItem(key, value)
  } catch {
    // 隐私模式等场景下写入失败，忽略即可，不影响会话内的内存状态
  }
}

export function getToken() {
  return readStorage(TOKEN_KEY)
}

export function getRefreshToken() {
  return readStorage(REFRESH_TOKEN_KEY)
}

export function setTokens({ token, refreshToken } = {}) {
  writeStorage(TOKEN_KEY, token)
  writeStorage(REFRESH_TOKEN_KEY, refreshToken)
}

export function clearTokens() {
  writeStorage(TOKEN_KEY, '')
  writeStorage(REFRESH_TOKEN_KEY, '')
}

export function getStoredUser() {
  const raw = readStorage(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

export function setStoredUser(user) {
  writeStorage(USER_KEY, user ? JSON.stringify(user) : '')
}

export function clearStoredUser() {
  writeStorage(USER_KEY, '')
}

export function clearAuthStorage() {
  clearTokens()
  clearStoredUser()
}
