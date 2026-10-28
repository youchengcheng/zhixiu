// 静态资源统一出口：页面从这里取插画与占位图，避免各处散落相对路径
// 用法：import { illustrations, placeholders } from '@/assets'
// Vite 会把 .svg 当作资源，import 得到的是可直接用于 <img :src> 的 URL 字符串

// 空状态插画（4 张，配合 components/EmptyState.vue 的 illustration 属性）
import emptyOrder from './illustrations/empty-order.svg'
import emptyProperty from './illustrations/empty-property.svg'
import emptyAnnouncement from './illustrations/empty-announcement.svg'
import emptyFeedback from './illustrations/empty-feedback.svg'

// 图片占位（3 张，配合 el-image 的 error 占位、头像兜底、知识库封面）
import imageError from './placeholders/image-error.svg'
import avatar from './placeholders/avatar.svg'
import knowledgeCover from './placeholders/knowledge-cover.svg'

export const illustrations = {
  emptyOrder,
  emptyProperty,
  emptyAnnouncement,
  emptyFeedback
}

export const placeholders = {
  imageError,
  avatar,
  knowledgeCover
}
