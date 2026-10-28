// 报修类型 /api/category
import http from '@/utils/request'

/** 报修类型树：一级类型带 children 二级类型 */
export function getCategoryTree() {
  return http.get('/category/tree')
}
