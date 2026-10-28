// 报修类型：与建库脚本初始化数据保持一致（一级 100/200/300/400，二级覆盖各类型常见维修物品）

export const categoryTree = [
  {
    id: '100',
    name: '水管维修',
    parentId: '0',
    sort: 1,
    children: [
      { id: '101', name: '水龙头', parentId: '100', sort: 1 },
      { id: '102', name: '马桶', parentId: '100', sort: 2 },
      { id: '103', name: '下水道', parentId: '100', sort: 3 },
      { id: '104', name: '洗手盆', parentId: '100', sort: 4 },
      { id: '105', name: '淋浴花洒', parentId: '100', sort: 5 },
      { id: '106', name: '水管漏水', parentId: '100', sort: 6 },
      { id: '107', name: '其他水管问题', parentId: '100', sort: 7 }
    ]
  },
  {
    id: '200',
    name: '电器维修',
    parentId: '0',
    sort: 2,
    children: [
      { id: '201', name: '空调', parentId: '200', sort: 1 },
      { id: '202', name: '热水器', parentId: '200', sort: 2 },
      { id: '203', name: '照明', parentId: '200', sort: 3 },
      { id: '204', name: '洗衣机', parentId: '200', sort: 4 },
      { id: '205', name: '冰箱', parentId: '200', sort: 5 },
      { id: '206', name: '电视', parentId: '200', sort: 6 },
      { id: '207', name: '其他电器问题', parentId: '200', sort: 7 }
    ]
  },
  {
    id: '300',
    name: '门窗维修',
    parentId: '0',
    sort: 3,
    children: [
      { id: '301', name: '入户门', parentId: '300', sort: 1 },
      { id: '302', name: '窗户', parentId: '300', sort: 2 },
      { id: '303', name: '门锁', parentId: '300', sort: 3 },
      { id: '304', name: '纱窗', parentId: '300', sort: 4 },
      { id: '305', name: '其他门窗问题', parentId: '300', sort: 5 }
    ]
  },
  {
    id: '400',
    name: '公共设施',
    parentId: '0',
    sort: 4,
    children: [
      { id: '401', name: '电梯', parentId: '400', sort: 1 },
      { id: '402', name: '楼道照明', parentId: '400', sort: 2 },
      { id: '403', name: '单元门禁', parentId: '400', sort: 3 },
      { id: '404', name: '其他公共设施', parentId: '400', sort: 4 }
    ]
  }
]

/** 由类型 ID 拼出「一级 > 二级」路径，用于列表与详情展示 */
export function categoryPath(categoryId) {
  const id = String(categoryId || '')
  for (const parent of categoryTree) {
    if (parent.id === id) return parent.name
    for (const child of parent.children) {
      if (child.id === id) return `${parent.name} > ${child.name}`
    }
  }
  return ''
}
