package com.ycc.zhixiucloud.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 师傅接单排行查询入参（GET /api/admin/statistics/worker-ranking），提交方为管理员。
 * 取指定月份接单量前 N 的师傅，默认前 10、本月；month 为空时由 Service 兜底为当前月。
 */
@Data
@Schema(description = "师傅接单排行查询入参")
public class StatisticsRankingQueryRequestVO {

    @Min(value = 1, message = "排行榜条数至少 1 条")
    @Max(value = 100, message = "排行榜条数最多 100 条")
    @Schema(description = "取前 N，默认 10", example = "10")
    private Integer top = 10;

    /** 为空时由 Service 兜底为当前月 */
    @Schema(description = "统计月份 yyyy-MM，默认本月", example = "2026-10")
    private String month;
}
