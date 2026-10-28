import axios from 'axios'
import { ElMessage } from 'element-plus'
import { API_BASE_URL } from './env'
import { getToken } from './auth'

// 业务异常：页面需要分支处理时按 code 判断，其余一律由拦截器统一提示
export class BizError extends Error {
  constructor(code, message) {
    super(message || '请求失败')
    this.name = 'BizError'
    this.code = code
  }
}

let unauthorizedHandler = null

// 由 main.js 注册：清空登录态并跳转登录页（避免请求层反向依赖路由与 store）
export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = handler
}

const http = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15000
})

// Mock 层通过它替换 axios adapter，页面与拦截器链路完全不变
export function setHttpAdapter(adapter) {
  http.defaults.adapter = adapter
}

http.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    // FormData 交给浏览器生成带 boundary 的 Content-Type
    if (typeof FormData !== 'undefined' && config.data instanceof FormData) {
      delete config.headers['Content-Type']
    }
    return config
  },
  (error) => Promise.reject(error)
)

function notifyUnauthorized(message) {
  if (unauthorizedHandler) {
    unauthorizedHandler()
  }
  ElMessage.warning(message || '登录已过期，请重新登录')
}

http.interceptors.response.use(
  (response) => {
    const body = response.data

    // 非统一响应体（二进制流等）直接透出
    if (!body || typeof body !== 'object' || body.code === undefined) {
      return body
    }

    if (body.code === 200) {
      return body.data
    }

    if (body.code === 401) {
      notifyUnauthorized(body.message)
      return Promise.reject(new BizError(401, body.message || '登录已过期'))
    }

    if (body.code === 403) {
      ElMessage.error(body.message || '无权限访问该资源')
      return Promise.reject(new BizError(403, body.message || '无权限访问该资源'))
    }

    // 恢复草稿时允许静默处理预期的 3002，其他请求仍统一提示。
    if (!response.config?.silentCodes?.includes(body.code)) {
      ElMessage.error(body.message || '操作失败')
    }
    return Promise.reject(new BizError(body.code, body.message))
  },
  (error) => {
    const status = error?.response?.status

    if (status === 401) {
      notifyUnauthorized(error.response?.data?.message)
      return Promise.reject(new BizError(401, '登录已过期'))
    }

    if (status === 403) {
      ElMessage.error('无权限访问该资源')
      return Promise.reject(new BizError(403, '无权限访问该资源'))
    }

    if (status === 413) {
      ElMessage.error('文件过大，请压缩后重试')
      return Promise.reject(new BizError(413, '文件过大'))
    }

    const isTimeout =
      error?.code === 'ECONNABORTED' || /timeout/i.test(String(error?.message || ''))
    if (isTimeout) {
      ElMessage.error('请求超时，请稍后重试')
      return Promise.reject(new BizError('TIMEOUT', '请求超时'))
    }

    if (status) {
      const message = error?.response?.data?.message
      ElMessage.error(message || `请求失败（${status}）`)
      return Promise.reject(new BizError(status, message))
    }

    ElMessage.error('网络异常，请检查网络连接')
    return Promise.reject(new BizError('NETWORK', '网络异常'))
  }
)

export default http
