import { categoryTree } from '../data/categories'
import { fail, nextId, ok } from '../utils'

const referencedIds = new Set(['101', '201'])
const categories = categoryTree.flatMap((parent) => [
  { ...parent, children: undefined, status: parent.status ?? 1, icon: parent.icon || null },
  ...parent.children.map((child) => ({
    ...child,
    status: child.status ?? 1,
    icon: child.icon || null
  }))
])

function adminOnly(auth) {
  if (!auth || auth.roleCode !== 'ADMIN') return fail(403, '无权限访问该资源')
  return null
}

function buildTree(includeDisabled) {
  const visible = categories.filter((item) => includeDisabled || item.status === 1)
  return visible
    .filter((item) => String(item.parentId) === '0')
    .sort((a, b) => a.sort - b.sort)
    .map((parent) => ({
      ...parent,
      children: visible
        .filter((item) => String(item.parentId) === String(parent.id))
        .sort((a, b) => a.sort - b.sort)
    }))
}

function validParent(parentId) {
  if (String(parentId) === '0') return true
  return categories.some(
    (item) => String(item.id) === String(parentId) && String(item.parentId) === '0'
  )
}

export const categoryHandlers = [
  {
    method: 'GET',
    path: '/category/tree',
    handler: () => ok(buildTree(false))
  },
  {
    method: 'GET',
    path: '/admin/category/tree',
    handler: ({ auth }) => adminOnly(auth) || ok(buildTree(true))
  },
  {
    method: 'POST',
    path: '/admin/category',
    handler: ({ auth, body }) => {
      const denied = adminOnly(auth)
      if (denied) return denied
      if (!body.name?.trim()) return fail(400, '分类名称不能为空')
      if (!validParent(body.parentId)) return fail(400, '上级分类不存在')

      const id = nextId()
      categories.push({
        id,
        name: body.name.trim(),
        parentId: String(body.parentId),
        sort: Number(body.sort || 0),
        icon: body.icon || null,
        status: 1
      })
      return ok(id, '新增成功')
    }
  },
  {
    method: 'PUT',
    path: '/admin/category/:id',
    handler: ({ auth, pathParams, body }) => {
      const denied = adminOnly(auth)
      if (denied) return denied
      const item = categories.find((category) => String(category.id) === pathParams.id)
      if (!item) return fail(404, '分类不存在')
      if (!body.name?.trim()) return fail(400, '分类名称不能为空')
      if (!validParent(body.parentId)) return fail(400, '上级分类不存在')

      Object.assign(item, {
        name: body.name.trim(),
        parentId: String(body.parentId),
        sort: Number(body.sort || 0),
        icon: body.icon || null
      })
      return ok(null, '保存成功')
    }
  },
  {
    method: 'PUT',
    path: '/admin/category/:id/status',
    handler: ({ auth, pathParams, body }) => {
      const denied = adminOnly(auth)
      if (denied) return denied
      const item = categories.find((category) => String(category.id) === pathParams.id)
      if (!item) return fail(404, '分类不存在')
      item.status = Number(body.status)
      return ok(null, '状态已更新')
    }
  },
  {
    method: 'DELETE',
    path: '/admin/category/:id',
    handler: ({ auth, pathParams }) => {
      const denied = adminOnly(auth)
      if (denied) return denied
      const index = categories.findIndex((category) => String(category.id) === pathParams.id)
      if (index < 0) return fail(404, '分类不存在')
      if (categories.some((item) => String(item.parentId) === pathParams.id)) {
        return fail(409, '该分类下仍有子分类，不能删除')
      }
      if (referencedIds.has(pathParams.id)) return fail(409, '该分类已被引用，只能停用')
      categories.splice(index, 1)
      return ok(null, '删除成功')
    }
  }
]
