import axios from 'axios'
import { parseToken } from './data/accounts'
import { findRoute } from './registry'
import { fail, mockDelay, parseBody, parseQuery, sleep } from './utils'

/** 去掉 baseURL 与查询串，得到注册表里使用的相对路径 */
function normalizePath(config) {
  const base = config.baseURL || ''
  let path = String(config.url || '')
  if (base && path.startsWith(base)) {
    path = path.slice(base.length)
  }
  path = path.split('?')[0]
  return path.startsWith('/') ? path : `/${path}`
}

function readHeader(headers, name) {
  if (!headers) return ''
  if (typeof headers.get === 'function') {
    return headers.get(name) || ''
  }
  return headers[name] || headers[name.toLowerCase()] || ''
}

/**
 * 透传用的独立 axios 实例：不挂任何拦截器，只借用它的默认 adapter 发真实 HTTP 请求。
 * 之所以新建实例而不是复用 utils/request 的 http，是为了避免「mock adapter → 真实请求 → 又回到 mock adapter」的互相递归。
 */
const passthrough = axios.create()

/** 透传真实后端：沿用原请求的路径、参数、请求头与进度回调，只把 adapter 换成默认实现 */
function forwardToBackend(config) {
  return passthrough.request({
    url: config.url,
    baseURL: config.baseURL,
    method: config.method,
    params: config.params,
    data: config.data,
    headers: config.headers,
    timeout: config.timeout,
    responseType: config.responseType,
    withCredentials: config.withCredentials,
    onUploadProgress: config.onUploadProgress,
    signal: config.signal
  })
}

/**
 * Mock adapter：替换 axios 的 adapter，其余链路（请求拦截器、响应拦截器、
 * 业务码处理、分页结构）与真实后端完全一致。
 *
 * 拦截策略（阶段二起）：
 * - 命中 registry 里注册的 mock 路由 → 由 mock 处理，返回 HTTP 200 + 统一响应体；
 * - 未注册的接口 → 原样透传真实后端（工单、房产等已就绪模块直接打 8080）。
 *
 * 这样只需为「后端尚未就绪」的模块（公告、反馈、AI）补 mock，避免与真实数据脱节。
 */
export function createMockAdapter() {
  return async function mockAdapter(config) {
    const method = String(config.method || 'get').toUpperCase()
    const path = normalizePath(config)
    const matched = findRoute(method, path)

    // 未注册 mock 的接口一律透传真实后端，且不注入模拟延迟（否则真实请求会被白白拖慢）
    if (!matched) {
      console.debug(`[mock] 透传真实后端：${method} ${path}`)
      return forwardToBackend(config)
    }

    // 已注册的接口才加模拟延迟：常规 200-600ms，少量慢请求用于验证骨架屏
    await sleep(mockDelay())

    const token = readHeader(config.headers, 'Authorization').replace(/^Bearer\s+/i, '')
    const auth = parseToken(token)

    let payload

    if (!matched.public && !auth) {
      payload = fail(401, '登录已过期，请重新登录')
    } else {
      try {
        payload = await matched.handler({
          method,
          path,
          auth,
          params: { ...parseQuery(config.url), ...(config.params || {}) },
          pathParams: matched.pathParams,
          body: parseBody(config.data),
          config
        })
      } catch (error) {
        console.error('[mock] 处理异常', method, path, error)
        payload = fail(500, 'Mock 处理异常，请查看控制台')
      }
    }

    return {
      data: payload ?? fail(500, 'Mock 未返回数据'),
      status: 200,
      statusText: 'OK',
      headers: {},
      config,
      request: {}
    }
  }
}
