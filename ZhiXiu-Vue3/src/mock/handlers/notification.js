import { notifications } from '../data/notifications'
import { datetime, fail, ok, paginate } from '../utils'

function own(auth) {
  return notifications
    .filter((item) => item.userId === auth.userId)
    .sort((a, b) => String(b.createTime).localeCompare(String(a.createTime)))
}

function responseItem(item) {
  const { userId: _userId, ...response } = item
  return response
}

export const notificationHandlers = [
  {
    method: 'GET',
    path: '/notification/list',
    handler: ({ auth, params }) => {
      const page = paginate(own(auth).map(responseItem), params)
      return ok(page)
    }
  },
  {
    method: 'GET',
    path: '/notification/unread-count',
    handler: ({ auth }) => ok({ unreadCount: own(auth).filter((item) => item.isRead !== 1).length })
  },
  {
    method: 'PUT',
    path: '/notification/:id/read',
    handler: ({ auth, pathParams }) => {
      const item = notifications.find(
        (notification) => notification.id === pathParams.id && notification.userId === auth.userId
      )
      if (!item) return fail(404, '消息不存在')
      if (item.isRead !== 1) {
        item.isRead = 1
        item.readTime = datetime()
      }
      return ok(null, '已标记为已读')
    }
  },
  {
    method: 'PUT',
    path: '/notification/read-all',
    handler: ({ auth }) => {
      own(auth).forEach((item) => {
        if (item.isRead !== 1) {
          item.isRead = 1
          item.readTime = datetime()
        }
      })
      return ok(null, '全部通知已读')
    }
  }
]
