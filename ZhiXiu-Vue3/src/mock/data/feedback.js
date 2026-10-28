// 问题反馈 mock 数据：覆盖 0待处理 / 1处理中 / 2已处理 / 3关闭 四种状态与三种类型
// 字段与 feedback 表 DDL 对齐，仅补 userName / replierName 便于列表展示

export const feedbacks = [
  {
    id: '4101',
    userId: '10001',
    userName: '苏明彦',
    userRole: 'OWNER',
    type: 'BUG',
    title: '报修列表分页在 50 条时显示错位',
    content: '把每页条数切到 50 之后，操作列会挤到第二行，麻烦看一下。',
    contact: '138****9063',
    status: 0,
    reply: null,
    replierId: null,
    replierName: null,
    replyTime: null,
    createTime: '2026-10-15 20:41:07',
    updateTime: '2026-10-15 20:41:07'
  },
  {
    id: '4102',
    userId: '10001',
    userName: '苏明彦',
    userRole: 'OWNER',
    type: 'SUGGESTION',
    title: '希望能看到师傅预计到达时间',
    content: '师傅接单后如果能显示预计上门时间，等待的时候心里更有数。',
    contact: '138****9063',
    status: 1,
    reply: '已记录，将在派单通知里补充预计时段，本期先由师傅电话确认。',
    replierId: '10003',
    replierName: '贺琳',
    replyTime: '2026-10-14 14:22:31',
    createTime: '2026-10-13 09:15:44',
    updateTime: '2026-10-14 14:22:31'
  },
  {
    id: '4103',
    userId: '10002',
    userName: '严志勇',
    userRole: 'WORKER',
    type: 'SUGGESTION',
    title: '待接单池希望能按区域筛选',
    content: '跨区跑单比较费时间，如果能按楼栋或单元筛选待接单会高效很多。',
    contact: '139****5182',
    status: 2,
    reply: '已在待接单列表加入类型与紧急度筛选，区域筛选排在下一版。',
    replierId: '10003',
    replierName: '贺琳',
    replyTime: '2026-10-11 10:08:52',
    createTime: '2026-10-09 17:36:20',
    updateTime: '2026-10-11 10:08:52'
  },
  {
    id: '4104',
    userId: '10001',
    userName: '苏明彦',
    userRole: 'OWNER',
    type: 'COMPLAINT',
    title: '夜间施工噪音',
    content: '10 月 10 日晚 23 点后仍有装修敲击声，影响休息。',
    contact: '138****9063',
    status: 3,
    reply: '已上门核实并警告涉事业主，后续如再发生请直接联系值班电话。',
    replierId: '10003',
    replierName: '贺琳',
    replyTime: '2026-10-11 09:12:40',
    createTime: '2026-10-10 23:41:18',
    updateTime: '2026-10-11 09:12:40'
  },
  {
    id: '4105',
    userId: '10002',
    userName: '严志勇',
    userRole: 'WORKER',
    type: 'BUG',
    title: '上传完工照片偶发失败',
    content: '照片超过 8MB 时上传会失败，但没有明确提示。',
    contact: '139****5182',
    status: 1,
    reply: '已确认是大小限制提示缺失，正在补充前端校验。',
    replierId: '10003',
    replierName: '贺琳',
    replyTime: '2026-10-15 11:03:12',
    createTime: '2026-10-14 19:52:03',
    updateTime: '2026-10-15 11:03:12'
  }
]

export function findFeedback(id) {
  return feedbacks.find((item) => item.id === String(id)) || null
}
