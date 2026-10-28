// 知识库管理 /api/admin/knowledge
import http from '@/utils/request'

export function listKnowledge(params) {
  return http.get('/admin/knowledge/list', { params })
}

/** 文件表单复用原接口；保留 JSON 人工录入兼容。 */
function requestBody(data) {
  if (!Object.prototype.hasOwnProperty.call(data, 'file')) return data
  const form = new FormData()
  form.append('title', data.title)
  if (data.file) form.append('file', data.file)
  if (data.categoryId) form.append('categoryId', data.categoryId)
  if (data.tags !== undefined) form.append('tags', data.tags)
  return form
}

/** 新增知识：上传并解析文件，保存后异步分片向量化。 */
export function createKnowledge(data, onUploadProgress) {
  return http.post('/admin/knowledge', requestBody(data), { timeout: 60000, onUploadProgress })
}

/** 修改知识：未选择文件时保留原正文，保存后重新分片向量化。 */
export function updateKnowledge(id, data, onUploadProgress) {
  return http.put(`/admin/knowledge/${id}`, requestBody(data), { timeout: 60000, onUploadProgress })
}

export function deleteKnowledge(id) {
  return http.delete(`/admin/knowledge/${id}`)
}

/** 重新向量化 */
export function revectorizeKnowledge(id) {
  return http.post(`/admin/knowledge/${id}/revectorize`)
}

/** 检索测试：返回相似知识条目与相似度 */
export function testKnowledgeSearch(params) {
  return http.get('/admin/knowledge/test-search', { params })
}
