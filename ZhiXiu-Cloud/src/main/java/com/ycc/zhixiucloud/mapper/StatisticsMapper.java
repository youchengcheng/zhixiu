package com.ycc.zhixiucloud.mapper;

import com.ycc.zhixiucloud.dto.CategoryCountDTO;
import com.ycc.zhixiucloud.dto.DailyCountDTO;
import com.ycc.zhixiucloud.dto.ScoreCountDTO;
import com.ycc.zhixiucloud.dto.WorkerRankingDTO;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 统计看板 Mapper：全部为只读聚合查询，SQL 见 StatisticsMapper.xml。
 * <p>
 * 围绕工单、评价、SLA、AI 会话等数据提供数量统计、时长计算、趋势与分布查询，
 * 为管理端数据看板提供指标数据，不涉及任何写操作。
 */
public interface StatisticsMapper {

    /**
     * 工单总数（逻辑删除不参与统计）。
     */
    Long selectTotalOrderCount();

    /**
     * 待处理工单数（PENDING）。
     */
    Long selectPendingOrderCount();

    /**
     * 进行中工单数（已派单到已提交完工之间）。
     */
    Long selectGoingOrderCount();

    /**
     * 已完成工单数（COMPLETED / CLOSED）。
     */
    Long selectCompletedOrderCount();

    /**
     * 平均响应时长（分钟）：下单到接单，无数据返回 null。
     */
    Long selectAvgResponseMinutes();

    /**
     * 平均处理时长（分钟）：接单到提交完工，无数据返回 null。
     */
    Long selectAvgHandleMinutes();

    /**
     * 全站平均评分，无评价返回 null。
     */
    BigDecimal selectAvgEvaluationScore();

    /**
     * SLA 超时工单数：响应或处理任一超时。
     */
    Long selectSlaBreachedCount();

    /**
     * AI 会话总数。
     */
    Long selectTotalSessionCount();

    /**
     * AI 自助解决会话数（resolved = 1）。
     */
    Long selectResolvedSessionCount();

    /**
     * AI 转工单会话数（resolved = 2）。
     */
    Long selectTransferSessionCount();

    /**
     * 累计输入 Token 数。
     */
    Long selectTotalPromptTokens();

    /**
     * 累计输出 Token 数。
     */
    Long selectTotalCompletionTokens();

    /**
     * 近 N 天工单量：按 MM-dd 分组。
     *
     * @param startTime 区间下界（含）
     * @param endTime   区间上界（不含）
     */
    List<DailyCountDTO> selectDailyOrderCount(@Param("startTime") LocalDateTime startTime,
                                               @Param("endTime") LocalDateTime endTime);

    /**
     * 工单类型分布：按报修类型分组取数量前 10。
     */
    List<CategoryCountDTO> selectCategoryDistribution();

    /**
     * 师傅接单排行：某月内按接单数倒序取前 N。
     *
     * @param startTime 月初时间（含）
     * @param endTime   下月初时间（不含）
     * @param top       取前 N 条
     */
    List<WorkerRankingDTO> selectWorkerRanking(@Param("startTime") LocalDateTime startTime,
                                               @Param("endTime") LocalDateTime endTime,
                                               @Param("top") Integer top);

    /**
     * 满意度分布：按评分档位分组。
     */
    List<ScoreCountDTO> selectScoreDistribution();
}
