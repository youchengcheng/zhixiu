import http from '@/utils/request'
import { openJsonEventStream } from '@/utils/sse'

const SESSIONS = '/ai/concierge/sessions'

export const listConciergeSessions = (pageNum = 1) =>
  http.get(SESSIONS, { params: { pageNum, pageSize: 15 } })

export const createConciergeSession = () => http.post(SESSIONS, {})

export const listConciergeHistory = (id, pageNum = 1) =>
  http.get(SESSIONS + '/' + id + '/messages', { params: { pageNum, pageSize: 20 } })

export const fetchConciergeTable = (id, messageId, pageNum = 1) =>
  http.get(SESSIONS + '/' + id + '/messages/' + messageId + '/table', { params: { pageNum } })

export const streamConciergeChat = (id, payload, callbacks) =>
  openJsonEventStream(SESSIONS + '/' + id + '/chat', payload, callbacks)
