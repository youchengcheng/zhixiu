package com.ycc.zhixiucloud.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 师傅接单排行聚合行：某月内师傅的接单数与平均评分。
 *
 * <p>统计投影：由 StatisticsMapper 关联工单、师傅、评价表聚合后映射，
 * 供 StatisticsServiceImpl 生成排行榜 VO；不落库、不参与 MQ 传输。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkerRankingDTO implements Serializable {

    /** 师傅 ID（maintenance_worker.id） */
    private Long workerId;

    /** 师傅姓名，real_name 为空时回退 username */
    private String workerName;

    /** 该月接单数 */
    private Long orderCount;

    /** 平均评分 */
    private BigDecimal avgScore;
}
