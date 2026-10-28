// 公告 mock 数据：覆盖已发布/草稿、置顶、三种类型（SYSTEM/MAINTENANCE/ACTIVITY）与三种投放范围（ALL/OWNER/WORKER）
// 字段与 announcement 表 DDL 对齐，仅补一个便于列表展示的 publisherName

export const announcements = [
  {
    id: '4001',
    title: '10 月 20 日 3 栋供水管道计划检修',
    content:
      '为排查近期 3 栋高区水压波动问题，物业将于 **10 月 20 日 09:00 - 15:00** 对 3 栋立管进行分段检修。\n\n检修期间 3 栋 2 单元将短时停水，每段预计停水 40 分钟。请提前做好储水准备，检修完成后若水质浑浊，放水 2-3 分钟即可恢复。\n\n如遇紧急用水问题，可拨打物业值班电话 021-6400-8811。',
    type: 'MAINTENANCE',
    targetRole: 'ALL',
    publisherId: '10003',
    publisherName: '贺琳',
    publishTime: '2026-10-14 09:00:00',
    isTop: 1,
    status: 1,
    viewCount: 236,
    createTime: '2026-10-13 17:20:11',
    updateTime: '2026-10-14 09:00:00'
  },
  {
    id: '4002',
    title: '智能维修助手上线，报修可以说句话就完成',
    content:
      '智能维修助手已上线。遇到故障时可以直接描述现象，助手会先给出自助排查步骤；若无法自行解决，会自动整理成工单草稿，确认后即可提交。\n\n目前已覆盖空调、水管、电路、门窗等常见类型。',
    type: 'SYSTEM',
    targetRole: 'OWNER',
    publisherId: '10003',
    publisherName: '贺琳',
    publishTime: '2026-10-12 10:30:00',
    isTop: 0,
    status: 1,
    viewCount: 512,
    createTime: '2026-10-12 10:05:42',
    updateTime: '2026-10-12 10:30:00'
  },
  {
    id: '4003',
    title: '10 月社区便民服务日安排',
    content:
      '本月便民服务日定于 **10 月 25 日（周六）09:00 - 16:00**，地点在小区中心广场。\n\n服务内容：小家电免费检测、水龙头与角阀免费更换（材料自理）、免费磨刀、老年人家电安全上门检查。\n\n现场报名取号，名额有限。',
    type: 'ACTIVITY',
    targetRole: 'ALL',
    publisherId: '10003',
    publisherName: '贺琳',
    publishTime: '2026-10-10 15:00:00',
    isTop: 0,
    status: 1,
    viewCount: 318,
    createTime: '2026-10-10 14:12:09',
    updateTime: '2026-10-10 15:00:00'
  },
  {
    id: '4004',
    title: '维修师傅接单规范更新（10 月版）',
    content:
      '接单后请在 **30 分钟内**联系业主确认上门时间；到场需点击「到场签到」；如需延期必须提交暂停申请。\n\n连续两次无故超时未响应将影响本月派单优先级。',
    type: 'SYSTEM',
    targetRole: 'WORKER',
    publisherId: '10003',
    publisherName: '贺琳',
    publishTime: '2026-10-08 08:40:00',
    isTop: 0,
    status: 1,
    viewCount: 97,
    createTime: '2026-10-08 08:31:55',
    updateTime: '2026-10-08 08:40:00'
  },
  {
    id: '4005',
    title: '电梯年检安排（草稿，待发布）',
    content: '1 号楼、2 号楼电梯将于 11 月上旬进行年度检验，具体停梯时段待与检验机构确认后补充。',
    type: 'MAINTENANCE',
    targetRole: 'ALL',
    publisherId: '10003',
    publisherName: '贺琳',
    publishTime: null,
    isTop: 0,
    status: 0,
    viewCount: 0,
    createTime: '2026-10-15 16:02:33',
    updateTime: '2026-10-15 16:02:33'
  },
  {
    id: '4006',
    title: '小区消防通道清理通知',
    content:
      '近期巡查发现部分楼层消防通道有杂物堆放。请于 **10 月 22 日前** 自行清理，逾期物业将统一清运。\n\n消防通道涉及全楼安全，请务必配合。',
    type: 'SYSTEM',
    targetRole: 'ALL',
    publisherId: '10003',
    publisherName: '贺琳',
    publishTime: '2026-10-05 11:20:00',
    isTop: 0,
    status: 1,
    viewCount: 421,
    createTime: '2026-10-05 11:00:12',
    updateTime: '2026-10-05 11:20:00'
  }
]

export function findAnnouncement(id) {
  return announcements.find((item) => item.id === String(id)) || null
}
