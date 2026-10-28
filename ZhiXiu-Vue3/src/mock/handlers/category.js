import { categoryTree } from '../data/categories'
import { ok } from '../utils'

export const categoryHandlers = [
  {
    method: 'GET',
    path: '/category/tree',
    handler: () => ok(categoryTree)
  }
]
