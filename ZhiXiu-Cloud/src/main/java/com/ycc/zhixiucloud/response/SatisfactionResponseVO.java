package com.ycc.zhixiucloud.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 满意度统计出参（GET /api/admin/statistics/satisfaction，管理端）。
 * 统计口径：全量已评价工单按 1-5 分档位分组，缺失档位补 0；平均分无评价时为 0。
 */
@Data
@Builder
@Schema(description = "满意度统计")
public class SatisfactionResponseVO {

    @Schema(description = "评分档位列表，固定 1-5")
    private List<Integer> scores;

    @Schema(description = "各档位评价数，与评分一一对应")
    private List<Long> counts;

    /** 全站平均评分，保留 1 位小数；无评价数据时为 0 */
    @Schema(description = "平均分")
    private BigDecimal avgScore;
}
