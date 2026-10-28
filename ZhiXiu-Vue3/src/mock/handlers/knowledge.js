import { categoryPath } from '../data/categories'
import { findKnowledge, knowledges, scoreByText } from '../data/knowledge'
import { datetime, fail, like, nextId, ok, paginate } from '../utils'

// 知识库管理：管理员专属；文本文件导入与分片检索使用本地模拟。
const knowledgeChunks = new Map()

async function readContent(body, fallback = '') {
  let content = body.content === undefined ? fallback : String(body.content)
  if (body.file) {
    const file = body.file
    if (!file.size) return fail(400, '请上传非空的知识文件')
    if (file.size > 10 * 1024 * 1024) return fail(413, '文件大小不能超过 10MB')
    const extension = file.name.split('.').pop().toLowerCase()
    if (!['txt', 'md', 'markdown'].includes(extension)) {
      return fail(400, '模拟模式支持 TXT、Markdown；PDF、DOCX 请使用真实后端导入')
    }
    try {
      content = new TextDecoder('utf-8', { fatal: true }).decode(await file.arrayBuffer())
    } catch {
      return fail(400, '文件解析失败，请使用 UTF-8 编码的文本文件')
    }
  }
  content = content.replace(/\r\n?/g, '\n').trim()
  if (!content) return fail(400, '请上传包含正文的知识文件')
  if (new TextEncoder().encode(content).length > 65535) {
    return fail(413, '提取后的正文超过 64KB，请拆分文件后上传')
  }
  return content
}

function splitContent(content) {
  const characters = Array.from(content)
  const chunks = []
  let start = 0
  while (start < characters.length) {
    let end = Math.min(start + 800, characters.length)
    if (end < characters.length) {
      for (let index = end - 1; index >= start + 400; index -= 1) {
        if ('\n。！？；.!?;'.includes(characters[index])) {
          end = index + 1
          break
        }
      }
    }
    const chunk = characters.slice(start, end).join('').trim()
    if (chunk) chunks.push(chunk)
    if (end === characters.length) break
    start = end - 100
  }
  return chunks
}
function requireAdmin(auth) {
  if (!auth || auth.roleCode !== 'ADMIN') return fail(403, '无权限访问该资源')
  return null
}

/** 列表/详情统一补一个 categoryName，便于前端直接展示 */
function withCategory(item) {
  return { ...item, categoryName: categoryPath(item.categoryId) }
}

export const knowledgeHandlers = [
  {
    method: 'GET',
    path: '/admin/knowledge/list',
    handler: ({ auth, params }) => {
      const denied = requireAdmin(auth)
      if (denied) return denied

      const categoryId = params.categoryId ? String(params.categoryId) : ''
      const keyword = params.keyword ? String(params.keyword) : ''

      const list = knowledges
        .filter((item) => (categoryId ? String(item.categoryId) === categoryId : true))
        .filter((item) => like(item.title, keyword) || like(item.content, keyword) || like(item.tags, keyword))
        .sort((a, b) => new Date(b.updateTime).getTime() - new Date(a.updateTime).getTime())
        .map(withCategory)

      return ok(paginate(list, params))
    }
  },
  {
    method: 'POST',
    path: '/admin/knowledge',
    handler: async ({ auth, body }) => {
      const denied = requireAdmin(auth)
      if (denied) return denied

      const title = String(body.title || '').trim()
      if (!title) return fail(400, '请填写知识标题')
      if (title.length > 200) return fail(400, '标题不超过 200 个字')
      if (String(body.tags || '').length > 255) return fail(400, '标签不超过 255 个字')
      const content = await readContent(body)
      if (typeof content !== 'string') return content

      const now = datetime()
      const item = {
        id: nextId(),
        title,
        categoryId: body.categoryId || null,
        content,
        tags: String(body.tags || '').trim(),
        sourceType: body.file ? 'IMPORT' : 'MANUAL',
        status: 1,
        // 真实后端保存后异步向量化，mock 先标记为未向量化
        vectorized: 0,
        version: 0,
        createTime: now,
        updateTime: now
      }
      knowledges.unshift(item)
      return ok(item.id, '已保存，分片向量化任务已提交')
    }
  },
  {
    method: 'PUT',
    path: '/admin/knowledge/:id',
    handler: async ({ auth, pathParams, body }) => {
      const denied = requireAdmin(auth)
      if (denied) return denied

      const item = findKnowledge(pathParams.id)
      if (!item) return fail(404, '知识条目不存在')

      const title = String(body.title || '').trim()
      if (!title) return fail(400, '请填写知识标题')
      if (title.length > 200) return fail(400, '标题不超过 200 个字')
      if (String(body.tags || '').length > 255) return fail(400, '标签不超过 255 个字')
      const content = await readContent(body, item.content)
      if (typeof content !== 'string') return content

      item.title = title
      item.content = content
      if (body.file) item.sourceType = 'IMPORT'
      if (body.categoryId !== undefined) item.categoryId = body.categoryId || null
      if (body.tags !== undefined) item.tags = String(body.tags || '').trim()

      // 修改后需重新向量化：版本号 +1，与文档 13.3 一致
      item.vectorized = 0
      item.version += 1
      item.updateTime = datetime()
      knowledgeChunks.delete(item.id)
      return ok(null, '已更新，重新分片向量化任务已提交')
    }
  },
  {
    method: 'DELETE',
    path: '/admin/knowledge/:id',
    handler: ({ auth, pathParams }) => {
      const denied = requireAdmin(auth)
      if (denied) return denied

      const index = knowledges.findIndex((item) => item.id === String(pathParams.id))
      if (index < 0) return fail(404, '知识条目不存在')
      knowledgeChunks.delete(String(pathParams.id))
      knowledges.splice(index, 1)
      return ok(null, '已删除，向量库同步清理')
    }
  },
  {
    method: 'POST',
    path: '/admin/knowledge/:id/revectorize',
    handler: ({ auth, pathParams }) => {
      const denied = requireAdmin(auth)
      if (denied) return denied

      const item = findKnowledge(pathParams.id)
      if (!item) return fail(404, '知识条目不存在')

      item.vectorized = 1
      knowledgeChunks.set(item.id, splitContent(item.content))
      item.updateTime = datetime()
      return ok(null, '重新向量化完成')
    }
  },
  {
    method: 'GET',
    path: '/admin/knowledge/test-search',
    handler: ({ auth, params }) => {
      const denied = requireAdmin(auth)
      if (denied) return denied

      const query = String(params.query || '').trim()
      if (!query) return fail(400, '请输入检索语句')

      const topK = Number(params.topK || 3)
      const size = Number.isNaN(topK) ? 3 : Math.min(10, Math.max(1, topK))

      const list = knowledges
        .filter((item) => item.status === 1 && item.vectorized === 1)
        .flatMap((item) => (knowledgeChunks.get(item.id) || splitContent(item.content)).map((content) => ({
          ...withCategory(item),
          content,
          score: scoreByText(`${item.title}${item.tags}${content}`, query)
        })))
        .filter((item) => item.score > 0)
        .sort((a, b) => b.score - a.score)
        .slice(0, size)

      return ok(list)
    }
  }
]
