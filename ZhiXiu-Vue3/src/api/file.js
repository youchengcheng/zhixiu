// 文件 /api/file
import http from '@/utils/request'

/**
 * 上传文件：bizType 取 order / evaluate / avatar / knowledge
 * @param {File} file 待上传文件（图片，≤10MB）
 * @param {string} bizType 业务类型，后端按其分目录存储
 * @param {(event: ProgressEvent) => void} [onUploadProgress] 上传进度回调，供 ImageUpload 展示进度条
 * @returns {Promise<{ url: string, name: string }>}
 */
export function uploadFile(file, bizType = 'order', onUploadProgress) {
  const form = new FormData()
  form.append('file', file)
  if (bizType) {
    form.append('bizType', bizType)
  }
  return http.post('/file/upload', form, { timeout: 60000, onUploadProgress })
}

/** 公开文件访问地址（图片代理） */
export function publicFileUrl(path) {
  if (!path) return ''
  const text = String(path)
  // 完整地址（MinIO 直链）与本地预览地址（mock 下的 objectURL、data URI）都直接返回
  if (/^(https?:|blob:|data:)/i.test(text)) return text
  return `/api/file/public/${text.replace(/^\/+/, '')}`
}
