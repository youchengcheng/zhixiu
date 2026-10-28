package com.ycc.zhixiucloud.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 满意度聚合行：按评分档位分组的评价数量。
 *
 * <p>统计投影：由 StatisticsMapper 对评价表按 score 分组统计后映射，
 * 供 StatisticsServiceImpl 生成满意度分布图；没有评价的档位不会出现在结果中，
 * 由服务层按 1-5 档补零。不落库、不参与 MQ 传输。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScoreCountDTO implements Serializable {

    /** 评分档位 1-5 */
    private Integer score;

    /** 该档位评价数 */
    private Long count;
}
