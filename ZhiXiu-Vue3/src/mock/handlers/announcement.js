import { announcements, findAnnouncement } from '../data/announcements'
import { datetime, fail, nextId, ok, paginate } from '../utils'

// 管理端专属操作：与后端一致返回 403 业务码（HTTP 仍是 200）
function requireAdmin(auth) {
  if (!auth || auth.roleCode !== 'ADMIN') return fail(403, '无权限访问该资源')
  return null
}

export const announcementHandlers = [
  {
    method: 'GET',
    path: '/announcement/list',
    handler: ({ auth, params }) => {
      const role = auth?.roleCode
      // 管理员看全部（含草稿，便于管理端上下架）；业主/师傅只看已发布且投放范围命中自己的
      const list = announcements
        .filter((item) => role === 'ADMIN' || item.status === 1)
        .filter((item) => role === 'ADMIN' || item.targetRole === 'ALL' || item.targetRole === role)
        .sort(
          (a, b) =>
            b.isTop - a.isTop ||
            new Date(b.publishTime || 0).getTime() - new Date(a.publishTime || 0).getTime()
        )
      return ok(paginate(list, params))
    }
  },
  {
    method: 'GET',
    path: '/announcement/:id',
    handler: ({ pathParams }) => {
      const item = findAnnouncement(pathParams.id)
      if (!item) return fail(404, '公告不存在')
      // 详情页打开即计一次阅读量
      item.viewCount += 1
      return ok({ ...item })
    }
  },
  {
    method: 'POST',
    path: '/admin/announcement',
    handler: ({ auth, body }) => {
      const denied = requireAdmin(auth)
      if (denied) return denied

      const title = String(body.title || '').trim()
      const content = String(body.content || '').trim()
      if (!title) return fail(400, '请填写公告标题')
      if (!content) return fail(400, '请填写公告内容')

      const now = datetime()
      const item = {
        id: nextId(),
        title,
        content,
        type: body.type || 'SYSTEM',
        targetRole: body.targetRole || 'ALL',
        publisherId: auth.userId,
        publisherName: '贺琳',
        publishTime: null,
        isTop: Number(body.isTop || 0),
        status: 0, // 新建即草稿，需再调发布接口
        viewCount: 0,
        createTime: now,
        updateTime: now
      }
      announcements.unshift(item)
      return ok({ id: item.id }, '草稿已保存')
    }
  },
  {
    method: 'PUT',
    path: '/admin/announcement/:id',
    handler: ({ auth, pathParams, body }) => {
      const denied = requireAdmin(auth)
      if (denied) return denied

      const item = findAnnouncement(pathParams.id)
      if (!item) return fail(404, '公告不存在')

      if (body.title !== undefined) item.title = String(body.title).trim()
      if (body.content !== undefined) item.content = String(body.content).trim()
      if (body.type !== undefined) item.type = body.type
      if (body.targetRole !== undefined) item.targetRole = body.targetRole
      if (body.isTop !== undefined) item.isTop = Number(body.isTop)
      item.updateTime = datetime()
      return ok(null, '公告已更新')
    }
  },
  {
    method: 'DELETE',
    path: '/admin/announcement/:id',
    handler: ({ auth, pathParams }) => {
      const denied = requireAdmin(auth)
      if (denied) return denied

      const index = announcements.findIndex((item) => item.id === String(pathParams.id))
      if (index < 0) return fail(404, '公告不存在')
      announcements.splice(index, 1)
      return ok(null, '公告已删除')
    }
  },
  {
    method: 'PUT',
    path: '/admin/announcement/:id/publish',
    handler: ({ auth, pathParams, body }) => {
      const denied = requireAdmin(auth)
      if (denied) return denied

      const item = findAnnouncement(pathParams.id)
      if (!item) return fail(404, '公告不存在')

      const status = Number(body.status)
      if (status !== 0 && status !== 1) return fail(400, '发布状态不合法')

      item.status = status
      // 首次发布记录发布时间；下架保留原发布时间
      item.publishTime = status === 1 ? item.publishTime || datetime() : item.publishTime
      item.updateTime = datetime()
      return ok(null, status === 1 ? '公告已发布' : '公告已下架')
    }
  }
]
