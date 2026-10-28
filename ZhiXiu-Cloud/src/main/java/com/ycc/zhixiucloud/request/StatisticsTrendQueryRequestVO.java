package com.ycc.zhixiucloud.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 工单趋势查询入参（GET /api/admin/statistics/order-trend），提交方为管理员。
 * 统计范围含今天在内的近 N 天，默认 7 天，取值 1-365。
 */
@Data
@Schema(description = "工单趋势查询入参")
public class StatisticsTrendQueryRequestVO {

    /** 默认 7，范围 1-365；含今天在内的连续自然日。 */
    @Min(value = 1, message = "统计天数至少 1 天")
    @Max(value = 365, message = "统计天数最多 365 天")
    @Schema(description = "近 N 天，默认 7", example = "7")
    private Integer days = 7;
}
