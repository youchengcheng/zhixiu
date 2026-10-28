// 环境开关与常量：页面与工具层统一从这里读取，不直接碰 import.meta.env

export const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'

export const APP_TITLE = import.meta.env.VITE_APP_TITLE || '智修云 · 物业报修系统'

// 项目统一约定的时间格式，所有展示与请求参数都用它
export const DATETIME_FORMAT = 'yyyy-MM-dd HH:mm:ss'

export const PAGE_SIZE_OPTIONS = [10, 20, 50, 100]
