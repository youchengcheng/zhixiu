package com.ycc.zhixiucloud.service.impl;

import com.ycc.zhixiucloud.common.exception.BizException;
import com.ycc.zhixiucloud.common.result.ResultCode;
import com.ycc.zhixiucloud.dto.CategoryCountDTO;
import com.ycc.zhixiucloud.dto.DailyCountDTO;
import com.ycc.zhixiucloud.dto.ScoreCountDTO;
import com.ycc.zhixiucloud.dto.WorkerRankingDTO;
import com.ycc.zhixiucloud.mapper.StatisticsMapper;
import com.ycc.zhixiucloud.request.StatisticsRankingQueryRequestVO;
import com.ycc.zhixiucloud.request.StatisticsTrendQueryRequestVO;
import com.ycc.zhixiucloud.response.AiResolutionResponseVO;
import com.ycc.zhixiucloud.response.CategoryDistributionResponseVO;
import com.ycc.zhixiucloud.response.OrderTrendResponseVO;
import com.ycc.zhixiucloud.response.SatisfactionResponseVO;
import com.ycc.zhixiucloud.response.StatisticsOverviewResponseVO;
import com.ycc.zhixiucloud.response.WorkerRankingResponseVO;
import com.ycc.zhixiucloud.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 统计看板服务实现：管理端各类只读聚合统计。
 *
 * <p>统计口径与聚合全部下沉到 {@code StatisticsMapper.xml} 的 SQL 中，本类只做默认值兜底、
 * 缺失日期/档位补齐、比率计算与结果装配。</p>
 *
 * <p>关键约定：</p>
 * <ul>
 *   <li>所有统计均排除逻辑删除数据（{@code deleted = 0}）；时间区间统一采用左闭右开
 *       {@code [start, end)}，避免边界记录被重复或遗漏统计。</li>
 *   <li>SQL 负责 COUNT / AVG / SUM / GROUP BY / LIMIT，Service 只处理 Java 侧的空值、
 *       补零与解析；计数结果为 null 时统一兜底为 0，比率分母为 0 时返回 0。</li>
 *   <li>各接口均为实时查询，当前未引入缓存；如需优化可在服务层按分钟级缓存只读结果。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    /** 趋势日期展示格式：MM-dd */
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("MM-dd");

    /** 趋势天数默认值 */
    private static final int DEFAULT_TREND_DAYS = 7;

    /** 排行榜条数默认值 */
    private static final int DEFAULT_RANKING_TOP = 10;

    /** 满意度固定档位 1-5 */
    private static final int[] SCORE_LEVELS = {1, 2, 3, 4, 5};

    private final StatisticsMapper statisticsMapper;

    /**
     * 看板总览：一次汇总工单量、平均时长、评分、SLA 超时与 AI 自助解决率。
     *
     * <p>统计口径：待处理只统计 PENDING；进行中覆盖已派单到已提交完工之间的状态；已完成包含
     * COMPLETED/CLOSED。平均响应时长取“下单→接单”，平均处理时长取“接单→提交完工”，
     * 均由 SQL 按分钟求平均并四舍五入；无数据时计数兜底为 0、评分兜底为 0。</p>
     *
     * @return 总览指标 VO；AI 解决率 = 自助解决会话数 / 会话总数（保留 2 位小数）
     */
    @Override
    public StatisticsOverviewResponseVO overview() {
        // 工单总数
        long totalOrders = nullToZero(statisticsMapper.selectTotalOrderCount());
        // 待处理工单数
        long pendingOrders = nullToZero(statisticsMapper.selectPendingOrderCount());
        // 进行中工单数
        long goingOrders = nullToZero(statisticsMapper.selectGoingOrderCount());
        // 已完成工单数
        long completedOrders = nullToZero(statisticsMapper.selectCompletedOrderCount());
        // 平均响应时长（分钟），无数据为 0
        long avgResponseMinutes = nullToZero(statisticsMapper.selectAvgResponseMinutes());
        // 平均处理时长（分钟），无数据为 0
        long avgHandleMinutes = nullToZero(statisticsMapper.selectAvgHandleMinutes());
        // 全站平均评分，无评价返回空
        BigDecimal avgScore = statisticsMapper.selectAvgEvaluationScore();
        // SLA 超时工单数
        long slaBreachedCount = nullToZero(statisticsMapper.selectSlaBreachedCount());
        // AI 会话总数
        long totalSessions = nullToZero(statisticsMapper.selectTotalSessionCount());
        // AI 自助解决会话数
        long resolvedSessions = nullToZero(statisticsMapper.selectResolvedSessionCount());
        // 自助解决率 = 解决数 / 总数
        BigDecimal aiResolutionRate = rate(resolvedSessions, totalSessions);

        // 组装总览出参
        return StatisticsOverviewResponseVO.builder()
                // 工单总数
                .totalOrders(totalOrders)
                // 待处理工单数
                .pendingOrders(pendingOrders)
                // 进行中工单数
                .goingOrders(goingOrders)
                // 已完成工单数
                .completedOrders(completedOrders)
                // 平均响应时长（分钟）
                .avgResponseMinutes(avgResponseMinutes)
                // 平均处理时长（分钟）
                .avgHandleMinutes(avgHandleMinutes)
                // 无评价时平均分兜底为 0
                .avgScore(avgScore == null ? BigDecimal.ZERO : avgScore)
                // SLA 超时工单数
                .slaBreachedCount(slaBreachedCount)
                // AI 自助解决率
                .aiResolutionRate(aiResolutionRate)
                // 构建总览出参
                .build();
    }

    /**
     * 工单趋势：返回近 N 天每日新增工单量。
     *
     * <p>时间范围为 {@code [今天-(N-1) 00:00, 明天 00:00)}，按工单创建时间统计；SQL 只返回
     * 有数据的日期，本方法按日历逐日遍历并补 0，保证前端拿到连续的 N 个点。日期标签为 MM-dd。</p>
     *
     * @param requestVO 统计天数 days，空值默认 7（请求层限制 1~365）
     * @return 日期与数量两个平行列表
     */
    @Override
    public OrderTrendResponseVO orderTrend(StatisticsTrendQueryRequestVO requestVO) {
        // 近 N 天，空值兜底为默认 7 天
        int days = requestVO.getDays() == null ? DEFAULT_TREND_DAYS : requestVO.getDays();
        // 结束日期取今天
        LocalDate today = LocalDate.now();
        // 起始日期 = 今天 - (N - 1)，保证正好覆盖 N 天
        LocalDate startDate = today.minusDays(days - 1L);
        // 查询下界：起始日 00:00:00
        LocalDateTime startTime = startDate.atStartOfDay();
        // 查询上界：明天 00:00:00，区间左闭右开
        LocalDateTime endTime = today.plusDays(1).atStartOfDay();

        // 按天聚合的近 N 天工单量
        List<DailyCountDTO> rows = statisticsMapper.selectDailyOrderCount(startTime, endTime);
        // 转成 MM-dd -> 数量 映射，供补齐缺失日期使用
        // SQL 已按日期分组，正常情况下键不重复；合并函数仅作防御，重复时保留先到的值
        Map<String, Long> countMap = rows.stream()
                .collect(Collectors.toMap(DailyCountDTO::getDay, DailyCountDTO::getCount, (a, b) -> a));

        // 日期序列
        List<String> dates = new ArrayList<>(days);
        // 数量序列
        List<Long> counts = new ArrayList<>(days);
        // 从起始日逐日推进到今日，保证返回正好 N 个点
        for (LocalDate date = startDate; !date.isAfter(today); date = date.plusDays(1)) {
            // 当前日期标签 MM-dd
            String label = date.format(DAY_FORMATTER);
            // 追加日期
            dates.add(label);
            // 无数据日期补 0
            counts.add(countMap.getOrDefault(label, 0L));
        }

        // 组装趋势出参
        return OrderTrendResponseVO.builder()
                // 日期列表
                .dates(dates)
                // 数量列表
                .counts(counts)
                // 构建趋势出参
                .build();
    }

    /**
     * 工单类型分布：统计各报修类型的工单量并取前 10。
     *
     * <p>统计范围为全量未删除工单，SQL 关联报修类型表按数量倒序 LIMIT 10；这里只把行 DTO
     * 拆成前端图表需要的两个平行列表。</p>
     *
     * @return 类型名称列表与对应数量列表
     */
    @Override
    public CategoryDistributionResponseVO categoryDistribution() {
        // 按报修类型聚合的工单量（SQL 已 LIMIT 10）
        List<CategoryCountDTO> rows = statisticsMapper.selectCategoryDistribution();
        // 类型名称序列
        List<String> categories = new ArrayList<>(rows.size());
        // 数量序列
        List<Long> counts = new ArrayList<>(rows.size());
        // 逐行拆成两个平行列表
        for (CategoryCountDTO row : rows) {
            // 追加类型名称
            categories.add(row.getName());
            // 追加工单数
            counts.add(row.getCount());
        }

        // 组装分布出参
        return CategoryDistributionResponseVO.builder()
                // 类型名称列表
                .categories(categories)
                // 数量列表
                .counts(counts)
                // 构建分布出参
                .build();
    }

    /**
     * 师傅接单排行：统计指定月份内按接单数倒序的前 N 名师傅。
     *
     * <p>统计口径为工单创建时间落在该月（左闭右开）且已有接单师傅的记录；平均评分直接取
     * 师傅档案上的累计平均分，并非仅该月评价。月份为空默认当前月，格式非法抛参数异常。</p>
     *
     * @param requestVO 取前 N（默认 10，请求层限制 1~100）与统计月份 yyyy-MM
     * @return 师傅排行列表，按接单数倒序
     * @throws BizException 月份格式不是 yyyy-MM 时抛出参数错误
     */
    @Override
    public List<WorkerRankingResponseVO> workerRanking(StatisticsRankingQueryRequestVO requestVO) {
        // 取前 N，空值兜底为默认 10
        int top = requestVO.getTop() == null ? DEFAULT_RANKING_TOP : requestVO.getTop();
        // 解析统计月份，为空时取当前月
        YearMonth yearMonth = parseMonth(requestVO.getMonth());
        // 查询下界：月初 00:00:00
        LocalDateTime startTime = yearMonth.atDay(1).atStartOfDay();
        // 查询上界：下月初 00:00:00，区间左闭右开
        LocalDateTime endTime = yearMonth.plusMonths(1).atDay(1).atStartOfDay();

        // 按接单数倒序的师傅排行，逐条转为出参
        return statisticsMapper.selectWorkerRanking(startTime, endTime, top).stream()
                // 行 DTO 转出参 VO
                .map(this::toWorkerRankingVO)
                .toList();
    }

    /**
     * 满意度统计：返回 1~5 分各档位的评价数量与全站平均分。
     *
     * <p>SQL 按评分分组，本方法按固定档位 1-5 遍历，缺失档位补 0，保证图表 X 轴完整；
     * 平均分由 SQL 保留 1 位小数，无评价时兜底为 0。</p>
     *
     * @return 评分档位列表、对应数量列表与平均分
     */
    @Override
    public SatisfactionResponseVO satisfaction() {
        // 按评分档位分组的评价数
        List<ScoreCountDTO> rows = statisticsMapper.selectScoreDistribution();
        // 评分 -> 数量 映射
        Map<Integer, Long> scoreMap = rows.stream()
                .collect(Collectors.toMap(ScoreCountDTO::getScore, ScoreCountDTO::getCount, (a, b) -> a));

        // 评分档位序列，固定 1-5
        List<Integer> scores = new ArrayList<>(SCORE_LEVELS.length);
        // 各档位数量序列
        List<Long> counts = new ArrayList<>(SCORE_LEVELS.length);
        // 按固定档位遍历，缺失档位补 0
        for (int level : SCORE_LEVELS) {
            // 追加评分档位
            scores.add(level);
            // 追加对应数量
            counts.add(scoreMap.getOrDefault(level, 0L));
        }
        // 平均分（保留 1 位小数），无评价返回空
        BigDecimal avgScore = statisticsMapper.selectAvgEvaluationScore();

        // 组装满意度出参
        return SatisfactionResponseVO.builder()
                // 评分档位列表
                .scores(scores)
                // 各档位数量列表
                .counts(counts)
                // 无评价时平均分兜底为 0
                .avgScore(avgScore == null ? BigDecimal.ZERO : avgScore)
                // 构建满意度出参
                .build();
    }

    /**
     * AI 自助解决情况：会话总数、自助解决数、转工单数、解决率与累计 Token 消耗。
     *
     * <p>口径：{@code resolved=1} 记为 AI 自助解决，{@code resolved=2} 记为转人工工单；
     * 解决率 = 自助解决数 / 会话总数，分母为 0 时返回 0。Token 为所有未删除消息的累计值。</p>
     *
     * @return AI 解决率统计 VO
     */
    @Override
    public AiResolutionResponseVO aiResolution() {
        // 会话总数
        long totalSessions = nullToZero(statisticsMapper.selectTotalSessionCount());
        // 自助解决会话数
        long resolvedSessions = nullToZero(statisticsMapper.selectResolvedSessionCount());
        // 转工单会话数
        long transferSessions = nullToZero(statisticsMapper.selectTransferSessionCount());
        // 累计输入 Token
        long totalPromptTokens = nullToZero(statisticsMapper.selectTotalPromptTokens());
        // 累计输出 Token
        long totalCompletionTokens = nullToZero(statisticsMapper.selectTotalCompletionTokens());
        // 解决率 = 解决数 / 总数
        BigDecimal resolutionRate = rate(resolvedSessions, totalSessions);

        // 组装解决率出参
        return AiResolutionResponseVO.builder()
                // 会话总数
                .totalSessions(totalSessions)
                // 自助解决会话数
                .resolvedSessions(resolvedSessions)
                // 转工单会话数
                .transferSessions(transferSessions)
                // 自助解决率
                .resolutionRate(resolutionRate)
                // 累计输入 Token
                .totalPromptTokens(totalPromptTokens)
                // 累计输出 Token
                .totalCompletionTokens(totalCompletionTokens)
                // 构建解决率出参
                .build();
    }

    /**
     * 解析 yyyy-MM，空白时返回当前月，格式非法抛参数异常。
     *
     * @param month 月份字符串，可为空
     * @return 解析后的年月，空值时为当前月
     * @throws BizException 格式不是 yyyy-MM 时抛出参数错误
     */
    private YearMonth parseMonth(String month) {
        // 未传月份时默认当前月
        if (!StringUtils.hasText(month)) {
            // 直接返回当前年月
            return YearMonth.now();
        }
        // 解析月份字符串，格式非法由下方 catch 转业务异常
        try {
            // 按 ISO yyyy-MM 解析
            return YearMonth.parse(month.trim());
        // 捕获解析异常并转为业务参数错误
        } catch (DateTimeParseException e) {
            // 格式非法转为参数异常
            throw BizException.of(ResultCode.PARAM_ERROR, "month 参数格式应为 yyyy-MM");
        }
    }

    /**
     * 计算比率并保留 2 位小数，分母为 0 时返回 0。
     *
     * @param numerator   分子
     * @param denominator 分母
     * @return 四舍五入到 2 位小数的比率；分母为 0 时返回 0
     */
    private BigDecimal rate(long numerator, long denominator) {
        // 分母为 0 直接返回 0，避免除零
        if (denominator == 0) {
            // 返回 0 而非异常，保证统计口径稳定
            return BigDecimal.ZERO;
        }
        // 四舍五入保留 2 位小数
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    /**
     * Long 计数空值兜底为 0。
     *
     * @param value SQL 聚合结果，可能为 null
     * @return 非空原值或 0
     */
    private long nullToZero(Long value) {
        // 空值返回 0，非空返回原值
        return value == null ? 0L : value;
    }

    /**
     * 排行行 DTO 转出参 VO。
     *
     * @param dto 师傅排行聚合行
     * @return 排行展示 VO
     */
    private WorkerRankingResponseVO toWorkerRankingVO(WorkerRankingDTO dto) {
        // 逐字段装配排行项
        return WorkerRankingResponseVO.builder()
                // 师傅 ID
                .workerId(dto.getWorkerId())
                // 师傅姓名
                .workerName(dto.getWorkerName())
                // 接单数
                .orderCount(dto.getOrderCount())
                // 平均评分
                .avgScore(dto.getAvgScore())
                // 构建排行项出参
                .build();
    }
}
