package com.ycc.zhixiucloud.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 师傅个人统计出参（GET /api/worker/statistics，师傅端）。
 * 统计当前登录师傅的累计接单 / 完工、完工率、平均处理时长、评分与当前负载；无数据时数值为 0。
 */
@Data
@Builder
@Schema(description = "师傅个人统计")
public class WorkerStatisticsResponseVO {

    @Schema(description = "累计接单数")
    private Integer totalOrders;

    @Schema(description = "累计完工数")
    private Integer finishOrders;

    /** 接单数为 0 时返回 0 */
    @Schema(description = "完工率 0-1")
    private BigDecimal finishRate;

    /** 统计口径：接单到提交完工的平均分钟数，无数据为 0 */
    @Schema(description = "平均处理时长(分钟)")
    private long avgHandleMinutes;

    /** 历史评价均分；尚无评价时为 0 */
    @Schema(description = "平均评分")
    private BigDecimal avgScore;

    @Schema(description = "当前进行中工单数")
    private Integer currentLoad;
}