package com.ycc.zhixiucloud.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 工单类型分布聚合行：按报修类型分组的工单数量。
 *
 * <p>统计投影：由 StatisticsMapper 的 GROUP BY + COUNT 查询直接映射，
 * 供 StatisticsServiceImpl 转换为管理端图表 VO；不落库、不参与 MQ 传输，
 * 实现 Serializable 只是为了兼容缓存/会话等通用序列化场景。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoryCountDTO implements Serializable {

    /** 报修类型名称 */
    private String name;

    /** 该类型工单数 */
    private Long count;
}
