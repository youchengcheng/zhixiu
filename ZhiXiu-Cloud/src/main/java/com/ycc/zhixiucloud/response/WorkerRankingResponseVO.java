package com.ycc.zhixiucloud.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 师傅接单排行项（GET /api/admin/statistics/worker-ranking，管理端）。
 * 统计口径：指定月份（默认当月）内按接单数降序取前 top 名，orderCount 即该月接单量。
 */
@Data
@Builder
@Schema(description = "师傅接单排行项")
public class WorkerRankingResponseVO {

    @Schema(description = "师傅 ID")
    private Long workerId;

    @Schema(description = "师傅姓名")
    private String workerName;

    /** 指定统计月份内的接单数，作为排行依据 */
    @Schema(description = "接单数")
    private Long orderCount;

    /** 师傅历史评价均分；尚无评价时可能为 null */
    @Schema(description = "平均评分")
    private BigDecimal avgScore;
}
