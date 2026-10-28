package com.ycc.zhixiucloud.controller.admin;

import com.ycc.zhixiucloud.common.result.Result;
import com.ycc.zhixiucloud.request.StatisticsRankingQueryRequestVO;
import com.ycc.zhixiucloud.request.StatisticsTrendQueryRequestVO;
import com.ycc.zhixiucloud.response.AiResolutionResponseVO;
import com.ycc.zhixiucloud.response.CategoryDistributionResponseVO;
import com.ycc.zhixiucloud.response.OrderTrendResponseVO;
import com.ycc.zhixiucloud.response.SatisfactionResponseVO;
import com.ycc.zhixiucloud.response.StatisticsOverviewResponseVO;
import com.ycc.zhixiucloud.response.WorkerRankingResponseVO;
import com.ycc.zhixiucloud.service.StatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 统计看板接口（管理端）：总览、趋势、类型分布、师傅排行、满意度与 AI 解决率，全部只读。
 * 类级 @PreAuthorize 限定 ADMIN，统计结果只用于管理端看板展示，不修改业务数据。
 */
@Tag(name = "统计看板")
@RestController
@RequestMapping("/api/admin/statistics")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
public class StatisticsController {

    private final StatisticsService statisticsService;

    @Operation(summary = "看板总览")
    @GetMapping("/overview")
    public Result<StatisticsOverviewResponseVO> overview() {
        // 汇总工单量、平均处理时长、评分、SLA 超时与 AI 解决率，供看板顶部卡片展示
        return Result.ok(statisticsService.overview());
    }

    @Operation(summary = "工单趋势")
    @GetMapping("/order-trend")
    public Result<OrderTrendResponseVO> orderTrend(@Valid StatisticsTrendQueryRequestVO requestVO) {
        // 近 N 天逐日统计，缺失日期由服务层补 0，前端可直接绘制折线图
        return Result.ok(statisticsService.orderTrend(requestVO));
    }

    @Operation(summary = "工单类型分布")
    @GetMapping("/category-distribution")
    public Result<CategoryDistributionResponseVO> categoryDistribution() {
        // 按报修类型统计数量并取前 10，用于看板饼图
        return Result.ok(statisticsService.categoryDistribution());
    }

    @Operation(summary = "师傅接单排行")
    @GetMapping("/worker-ranking")
    public Result<List<WorkerRankingResponseVO>> workerRanking(@Valid StatisticsRankingQueryRequestVO requestVO) {
        // 指定月份内按接单数倒序取前 N，用于师傅绩效排行
        return Result.ok(statisticsService.workerRanking(requestVO));
    }

    @Operation(summary = "满意度统计")
    @GetMapping("/satisfaction")
    public Result<SatisfactionResponseVO> satisfaction() {
        // 返回 1-5 星评价分布与平均分，反映业主满意度
        return Result.ok(statisticsService.satisfaction());
    }

    @Operation(summary = "AI 自助解决率")
    @GetMapping("/ai-resolution")
    public Result<AiResolutionResponseVO> aiResolution() {
        // 对比 AI 自助解决数与转工单数，并统计 Token 消耗，衡量助手实际效果
        return Result.ok(statisticsService.aiResolution());
    }
}
