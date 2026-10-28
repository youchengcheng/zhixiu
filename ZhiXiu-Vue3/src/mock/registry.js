// Mock 路由注册表：新增模块时在这里合并对应 handlers 即可
// 注意（阶段二起）：只有这里注册过的接口才由 mock 接管，未注册的会透传真实后端
import { aiHandlers } from './handlers/ai'
import { announcementHandlers } from './handlers/announcement'
import { authHandlers } from './handlers/auth'
import { categoryHandlers } from './handlers/category'
import { feedbackHandlers } from './handlers/feedback'
import { fileHandlers } from './handlers/file'
import { knowledgeHandlers } from './handlers/knowledge'
import { logHandlers } from './handlers/log'
import { statisticsHandlers } from './handlers/statistics'
import { userHandlers } from './handlers/user'

export const routes = [
  ...authHandlers,
  ...userHandlers,
  ...categoryHandlers,
  ...announcementHandlers,
  ...feedbackHandlers,
  ...aiHandlers,
  ...knowledgeHandlers,
  ...statisticsHandlers,
  ...logHandlers,
  ...fileHandlers
]

function compile(path) {
  const keys = []
  const source = path.replace(/:[A-Za-z0-9_]+/g, (token) => {
    keys.push(token.slice(1))
    return '([^/]+)'
  })
  return { regex: new RegExp(`^${source}$`), keys }
}

const compiled = routes.map((route) => ({ ...route, ...compile(route.path) }))

export function findRoute(method, path) {
  for (const route of compiled) {
    if (route.method.toUpperCase() !== method) continue
    const matched = route.regex.exec(path)
    if (!matched) continue

    const pathParams = {}
    route.keys.forEach((key, index) => {
      pathParams[key] = decodeURIComponent(matched[index + 1])
    })
    return { handler: route.handler, pathParams, public: Boolean(route.public) }
  }
  return null
}

export const routeCount = compiled.length
