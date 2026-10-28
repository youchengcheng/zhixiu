package com.ycc.zhixiucloud.service;

import com.ycc.zhixiucloud.request.StatisticsRankingQueryRequestVO;
import com.ycc.zhixiucloud.request.StatisticsTrendQueryRequestVO;
import com.ycc.zhixiucloud.response.AiResolutionResponseVO;
import com.ycc.zhixiucloud.response.CategoryDistributionResponseVO;
import com.ycc.zhixiucloud.response.OrderTrendResponseVO;
import com.ycc.zhixiucloud.response.SatisfactionResponseVO;
import com.ycc.zhixiucloud.response.StatisticsOverviewResponseVO;
import com.ycc.zhixiucloud.response.WorkerRankingResponseVO;

import java.util.List;

/**
 * 统计看板服务：管理端各类只读聚合统计。
 *
 * <p>职责边界：只做统计查询与结果装配，不修改任何业务数据；
 * 聚合计算在 Mapper 层 SQL 完成。</p>
 *
 * <p>主要使用方：管理端数据看板。</p>
 *
 * <p>关键实现要点：缺失日期/评分档位补 0，空值统一兜底为 0；比率分母为 0 时返回 0；
 * 趋势按“今天往前 N 天”左闭右开区间统计，排行榜按自然月统计。</p>
 */
public interface StatisticsService {

    /**
     * 看板总览。
     *
     * <p>包含工单总量、待处理/进行中/已完成数量、平均响应与处理时长、
     * 全站平均评分、SLA 超时工单数与 AI 自助解决率。</p>
     *
     * @return 总览统计结果；无数据项以 0 兜底
     */
    StatisticsOverviewResponseVO overview();

    /**
     * 工单趋势。
     *
     * <p>统计最近 N 天（含今天）每日新建工单量，按日期升序返回；
     * 没有数据的日期补 0，保证返回正好 N 个点。</p>
     *
     * @param requestVO 查询参数，days 为空时默认 7 天
     * @return 日期标签（MM-dd）与对应数量两个平行列表
     */
    OrderTrendResponseVO orderTrend(StatisticsTrendQueryRequestVO requestVO);

    /**
     * 工单类型分布。
     *
     * <p>按报修类型聚合工单数量，取数量前 10 名；返回类型名称与数量两个平行列表。</p>
     *
     * @return 类型分布结果；无数据时两个列表均为空
     */
    CategoryDistributionResponseVO categoryDistribution();

    /**
     * 师傅接单排行。
     *
     * <p>统计指定自然月内的接单数，按接单数倒序取前 N 名；month 为空时取当前月。</p>
     *
     * @param requestVO 查询参数（month 格式 yyyy-MM、top 条数）
     * @return 师傅排行列表（含姓名、接单数、平均分）
     * @throws com.ycc.zhixiucloud.common.exception.BizException month 格式非法时抛出 400
     */
    List<WorkerRankingResponseVO> workerRanking(StatisticsRankingQueryRequestVO requestVO);

    /**
     * 满意度统计。
     *
     * <p>按固定 1-5 档位统计评价数量，缺失档位补 0，并返回全站平均分
     * （无评价时平均分为 0）。</p>
     *
     * @return 评分档位、各档位数量与平均分
     */
    SatisfactionResponseVO satisfaction();

    /**
     * AI 自助解决率统计。
     *
     * <p>统计 AI 会话总数、自助解决数、转工单数、解决率，
     * 以及累计输入/输出 Token 消耗。</p>
     *
     * @return AI 解决率与 Token 统计结果
     */
    AiResolutionResponseVO aiResolution();
}
