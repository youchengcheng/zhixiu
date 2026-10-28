// 业主房产：字段与 /api/property/list 契约一致

export const properties = [
  {
    id: '3001',
    ownerId: '10001',
    community: '阳光嘉园',
    building: '3',
    unit: '2',
    room: '501',
    fullAddress: '阳光嘉园 3 栋 2 单元 501',
    areaSize: 89.5,
    isDefault: 1,
    status: 1,
    createTime: '2026-03-12 09:31:12'
  },
  {
    id: '3002',
    ownerId: '10001',
    community: '静安雅苑',
    building: '7',
    unit: '1',
    room: '1202',
    fullAddress: '静安雅苑 7 栋 1 单元 1202',
    areaSize: 128,
    isDefault: 0,
    status: 1,
    createTime: '2026-05-20 16:02:48'
  }
]

export function listByOwner(ownerId) {
  return properties.filter((item) => item.ownerId === String(ownerId))
}

export function findProperty(id) {
  return properties.find((item) => item.id === String(id)) || null
}

export function defaultProperty(ownerId) {
  const list = listByOwner(ownerId)
  return list.find((item) => item.isDefault === 1) || list[0] || null
}
