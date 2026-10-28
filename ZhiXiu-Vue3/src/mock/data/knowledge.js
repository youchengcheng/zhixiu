// 知识库 mock 数据：字段与 repair_knowledge 表 DDL 对齐
// 后端知识库模块尚未就绪，检索测试在此做本地相似度打分

export const knowledges = [
  {
    id: '6001',
    title: '空调不制冷的常见原因与排查',
    categoryId: '201',
    content:
      '空调不制冷通常有五种原因：一是遥控器未处于制冷模式或温度设置过高；二是滤网积尘导致风量不足；三是外机出风口被遮挡散热不良；四是缺氟（制冷剂不足）；五是压缩机或电容故障。\n\n自助排查顺序：先确认模式与温度，再清洗滤网，检查外机周围是否堆物；若外机不启动且有异响，多为电容或压缩机问题，需师傅上门。',
    tags: '空调,不制冷,滤网',
    sourceType: 'MANUAL',
    status: 1,
    vectorized: 1,
    version: 0,
    createTime: '2026-09-18 10:12:40',
    updateTime: '2026-10-08 09:31:22'
  },
  {
    id: '6002',
    title: '涉电故障的安全处置规范',
    categoryId: '203',
    content:
      '处理任何涉电故障前，必须先断开对应回路的分闸，并用电笔确认无电。\n\n严禁用湿手操作开关；严禁在未断电情况下拆卸插座、灯具；发现线路发热、焦糊味或有火花时，立即断开总闸并报修，不得自行处理。',
    tags: '涉电,安全,断电',
    sourceType: 'MANUAL',
    status: 1,
    vectorized: 1,
    version: 0,
    createTime: '2026-09-20 14:05:10',
    updateTime: '2026-09-20 14:05:10'
  },
  {
    id: '6003',
    title: '厨房下水缓慢的自助处理',
    categoryId: '103',
    content:
      '下水变慢多由存水弯或滤网积油污引起。\n\n处理步骤：拆下滤网清理残渣；用 60℃ 左右热水配合少量小苏打冲刷存水弯，静置 10 分钟后再冲一次；日常避免倒入大量油脂与厨余。\n\n若水位出现回涌，说明存在硬堵塞，需要师傅使用疏通设备上门。',
    tags: '下水道,堵塞,疏通',
    sourceType: 'MANUAL',
    status: 1,
    vectorized: 1,
    version: 0,
    createTime: '2026-09-25 16:48:03',
    updateTime: '2026-10-02 11:20:47'
  },
  {
    id: '6004',
    title: '热水器不出热水的判断方法',
    categoryId: '202',
    content:
      '燃气热水器不出热水：先确认燃气阀门开启、水压是否过低（低于 0.02MPa 通常不点火）、烟道是否堵塞；若点火声正常但水温不升，多为水流量传感器或热交换器结垢。\n\n电热水器不出热水：检查漏电保护是否跳闸、加热管是否结垢失效，以及是否处于定时模式未到加热时段。',
    tags: '热水器,燃气,水压',
    sourceType: 'MANUAL',
    status: 1,
    vectorized: 1,
    version: 0,
    createTime: '2026-09-28 09:15:36',
    updateTime: '2026-09-28 09:15:36'
  },
  {
    id: '6005',
    title: '入户门开合异响与门锁卡顿',
    categoryId: '301',
    content:
      '门轴异响多因合页缺油或门体轻微下沉。\n\n可用石墨粉或专用润滑剂处理合页；若门扇蹭地，需调整合页垫片。门锁卡顿常见于锁舌积尘或锁体偏移，可用石墨粉润滑，偏移明显时需师傅校正锁体位置。',
    tags: '入户门,五金,门锁',
    sourceType: 'IMPORT',
    status: 1,
    vectorized: 1,
    version: 1,
    createTime: '2026-10-05 15:02:18',
    updateTime: '2026-10-09 10:44:05'
  },
  {
    id: '6006',
    title: '窗户渗水的原因排查（待补充向量）',
    categoryId: '302',
    content:
      '窗户渗水常见于密封胶条老化、排水孔堵塞、窗框与墙体接缝开裂三种情况。\n\n可先清理窗框下沿排水孔，检查胶条是否硬化开裂；接缝开裂需铲除旧胶重新打耐候胶。若为外墙渗漏，需物业协调外部施工。',
    tags: '窗户,渗水,密封',
    sourceType: 'MANUAL',
    status: 1,
    vectorized: 0,
    version: 2,
    createTime: '2026-10-12 13:26:51',
    updateTime: '2026-10-14 17:03:29'
  },
  {
    id: '6007',
    title: '水龙头出水变小与滴漏',
    categoryId: '101',
    content:
      '出水变小通常是起泡器滤网被水垢堵塞，可拧下起泡器用白醋浸泡 30 分钟后冲洗。\n\n龙头根部滴漏多为阀芯陶瓷片磨损或密封圈老化，需要更换阀芯；更换前务必关闭角阀并放空管内余水。',
    tags: '水龙头,水垢,阀芯',
    sourceType: 'MANUAL',
    status: 1,
    vectorized: 1,
    version: 0,
    createTime: '2026-09-30 08:40:12',
    updateTime: '2026-09-30 08:40:12'
  },
  {
    id: '6008',
    title: '马桶水箱不断进水（旧版说明，已停用）',
    categoryId: '102',
    content: '早期版本的排查说明，已被新版替代。',
    tags: '马桶,水箱',
    sourceType: 'MANUAL',
    status: 0,
    vectorized: 1,
    version: 3,
    createTime: '2026-08-11 10:00:00',
    updateTime: '2026-10-01 09:00:00'
  }
]

export function findKnowledge(id) {
  return knowledges.find((item) => item.id === String(id)) || null
}

/**
 * 检索测试用的本地相似度：按字符二元组计算 Jaccard 相似度，
 * 让「检索测试」在没有向量库时也能给出稳定、可解释的排序结果
 */
export function scoreByText(source, query) {
  const text = String(source || '').toLowerCase()
  const q = String(query || '').trim().toLowerCase()
  if (!q) return 0

  const grams = (value) => {
    const set = new Set()
    for (let i = 0; i < value.length - 1; i += 1) set.add(value.slice(i, i + 2))
    return set
  }

  const sourceGrams = grams(text)
  const queryGrams = grams(q)
  if (!queryGrams.size) return 0

  let hit = 0
  queryGrams.forEach((gram) => {
    if (sourceGrams.has(gram)) hit += 1
  })
  return Number((hit / queryGrams.size).toFixed(4))
}
