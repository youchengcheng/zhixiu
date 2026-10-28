import { fetchEventSource } from '@microsoft/fetch-event-source'
import { API_BASE_URL, USE_MOCK } from './env'
import { getToken } from './auth'
import { BizError } from './request'

// AI 采用 POST + JSON，并沿用同一流式接口处理最新回答的按钮动作。
const CHAT_URL = API_BASE_URL + '/ai/chat'

function realChatStream({ sessionId, content, action = 'CHAT', messageId, onEvent, onDone, onError }) {
  const ctrl = new AbortController()

  fetchEventSource(CHAT_URL, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      Authorization: 'Bearer ' + getToken()
    },
    body: JSON.stringify({ sessionId, content, action, messageId }),
    signal: ctrl.signal,
    openWhenHidden: true,
    async onopen(response) {
      const contentType = response.headers.get('content-type') || ''
      if (response.ok && contentType.includes('text/event-stream')) return

      let body = null
      if (contentType.includes('json')) {
        try {
          body = await response.json()
        } catch {
          // 无法解析时使用 HTTP 状态的提示。
        }
      }
      if (response.status === 401) throw new BizError(401, body?.message || '登录已过期，请重新登录')
      if (response.status === 403) throw new BizError(403, body?.message || '无权限访问该资源')
      throw new BizError(body?.code || response.status, body?.message || '智能助手服务不可用，请稍后重试')
    },
    onmessage(event) {
      if (!event.data) return
      let payload = event.data
      try {
        payload = JSON.parse(event.data)
      } catch {
        // 非 JSON 消息保留原文。
      }
      if (event.event === 'error') {
        throw new BizError(payload?.code || 3001, payload?.message || '智能助手暂时不可用，请稍后重试')
      }
      onEvent?.(event.event || 'message', payload)
    },
    onclose() {
      onDone?.()
    },
    onerror(error) {
      // 按钮动作不可自动重放，抛出后由 Promise 的 catch 统一通知页面一次。
      throw error
    }
  }).catch((error) => {
    if (!ctrl.signal.aborted) onError?.(error)
  })

  return () => ctrl.abort()
}

/**
 * 事件：message / intent / citations / slot / followup / draft / done / error。
 * 返回中断函数；普通消息默认 CHAT，按钮动作需附最新已完成回答的 messageId。
 */
export function chatStream(options) {
  if (USE_MOCK) {
    let inner = null
    let aborted = false
    import('@/mock/sse')
      .then((mod) => {
        if (!aborted) inner = mod.mockChatStream(options)
      })
      .catch((error) => {
        if (!aborted) options.onError?.(error)
      })
    return () => {
      aborted = true
      inner?.()
    }
  }
  return realChatStream(options)
}
