const DEFAULT_MESSAGES = {
  400: '请检查填写的信息后重试。',
  401: '登录已过期，请重新登录。',
  403: '当前账号没有此操作权限，请联系管理员。',
  404: '未找到此内容，请返回列表查看。',
  409: '当前状态已变化，请刷新后重试。',
  413: '文件过大，请压缩或更换较小的文件后重试。',
  500: '服务暂时不可用，请稍后重试。',
  1001: '该登录名已被使用，请换一个。',
  1003: '登录名或密码不匹配，请检查后重试。',
  2002: '工单状态已变化，请刷新后查看可用操作。',
  2003: '该工单已被其他师傅接单，请选择其他工单。',
  3003: '报修信息还不完整，请补充缺少的信息后再生成工单。'
}

// 兼容旧后端和演示模式的默认文案，保留具体的业务说明。
const LEGACY_MESSAGES = {
  '未登录或 Token 已失效': DEFAULT_MESSAGES[401],
  '无权限访问': DEFAULT_MESSAGES[403],
  '无权限访问该资源': DEFAULT_MESSAGES[403],
  '服务器内部错误': DEFAULT_MESSAGES[500],
  '用户名已存在': DEFAULT_MESSAGES[1001],
  '用户名或密码错误': DEFAULT_MESSAGES[1003],
  '工单状态不允许该操作': DEFAULT_MESSAGES[2002],
  '工单已被他人抢走': DEFAULT_MESSAGES[2003],
  '槽位信息不足，无法生成工单': DEFAULT_MESSAGES[3003],
  '请输入正确的手机号': '请输入有效的 11 位手机号',
  '手机号格式不正确': '请输入有效的 11 位手机号',
  '联系电话格式不正确': '请输入有效的 11 位手机号',
  'Network Error': '网络连接失败，请检查网络后重试。',
  'Failed to fetch': '网络连接失败，请检查网络后重试。'
}

export function getErrorMessage(code, message) {
  if (Number(code) >= 500 && Number(code) < 600) return DEFAULT_MESSAGES[500]
  if (Object.hasOwn(LEGACY_MESSAGES, message)) return LEGACY_MESSAGES[message]
  return message || DEFAULT_MESSAGES[code] || '暂时无法完成操作，请稍后重试。'
}

export function getOrderLoadError(code) {
  if ([404, 2001].includes(Number(code))) {
    return { title: '未找到此工单', description: '请返回工单列表，查看其他工单。' }
  }
  if (Number(code) === 403) {
    return { title: '无法查看此工单', description: '当前账号没有查看权限，请返回列表或联系管理员。' }
  }
  return { title: '工单暂时无法查看', description: '请返回工单列表后重试。' }
}
