import { fail, ok } from '../utils'

// 文件上传：真实后端走 MinIO，mock 环境用本地对象地址（objectURL）做预览。
// 目的：在后端文件模块就绪前，工单报修照片、完工照片、评价图片、头像的
// 「选择文件 → 进度 → 预览 → 删除」整条链路依然可以完整演示。
//
// 说明：不注册 GET /api/file/public/:path —— 前端 publicFileUrl 对 blob:/data:
// 与 http(s) 地址都直接透传，Mock 下拿到的就是 objectURL，不需要文件代理；
// 未注册的请求会照常透传给真实后端。
export const fileHandlers = [
  {
    method: 'POST',
    path: '/file/upload',
    handler: ({ body }) => {
      const file = body?.file
      if (!file || typeof file === 'string') return fail(400, '请选择要上传的文件')

      // 与后端一致：单文件 ≤ 10MB
      if (Number(file.size || 0) > 10 * 1024 * 1024) {
        return fail(413, '文件过大，请压缩后重试')
      }

      const url = URL.createObjectURL(file)
      return ok({ url, name: file.name || 'image' }, '上传成功')
    }
  }
]
