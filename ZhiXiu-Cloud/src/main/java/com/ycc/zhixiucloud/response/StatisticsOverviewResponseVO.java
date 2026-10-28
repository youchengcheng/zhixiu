package com.ycc.zhixiucloud.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 看板总览出参（GET /api/admin/statistics/overview，管理端）。
 * 统计口径：全量未删除工单实时计数；时长与评分无数据时为 0；AI 解决率保留 2 位小数。
 */
@Data
@Builder
@Schema(description = "看板总览")
public class StatisticsOverviewResponseVO {

    @Schema(description = "工单总数")
    private Long totalOrders;

    /** 仅统计 status = PENDING 的工单 */
    @Schema(description = "待处理工单数")
    private Long pendingOrders;

    /** 已派单、已接单、处理中、已暂停、转单申请中、已提交完工六类状态之和 */
    @Schema(description = "进行中工单数")
    private Long goingOrders;

    /** 已完工与已关闭两类状态之和 */
    @Schema(description = "已完成工单数")
    private Long completedOrders;

    /** 统计口径：下单到接单的平均分钟数，仅统计已接单工单，无数据为 0 */
    @Schema(description = "平均响应时长(分钟)")
    private Long avgResponseMinutes;

    /** 统计口径：接单到提交完工的平均分钟数，仅统计两端时间均存在的工单，无数据为 0 */
    @Schema(description = "平均处理时长(分钟)")
    private Long avgHandleMinutes;

    /** 全站评价平均分，保留 1 位小数；无评价数据时为 0 */
    @Schema(description = "平均评分")
    private BigDecimal avgScore;

    /** 响应或处理任一超时的工单数 */
    @Schema(description = "SLA 超时工单数")
    private Long slaBreachedCount;

    /** 自助解决会话数 / 会话总数，取值 0-1，保留 2 位小数；无会话时为 0 */
    @Schema(description = "AI 自助解决率")
    private BigDecimal aiResolutionRate;
}
