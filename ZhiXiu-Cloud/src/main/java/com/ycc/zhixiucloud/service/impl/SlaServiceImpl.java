package com.ycc.zhixiucloud.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ycc.zhixiucloud.common.constant.MqConstants;
import com.ycc.zhixiucloud.common.enums.OrderStatusEnum;
import com.ycc.zhixiucloud.common.enums.SafetyFlagEnum;
import com.ycc.zhixiucloud.dto.WorkOrderStatusDTO;
import com.ycc.zhixiucloud.entity.SlaConfig;
import com.ycc.zhixiucloud.entity.WorkOrder;
import com.ycc.zhixiucloud.mapper.SlaConfigMapper;
import com.ycc.zhixiucloud.mapper.WorkOrderMapper;
import com.ycc.zhixiucloud.mq.producer.SlaProducer;
import com.ycc.zhixiucloud.service.OrderCacheService;
import com.ycc.zhixiucloud.service.SlaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * SLA 服务实现：规则匹配、超时点计算、RocketMQ 延迟消息投递与超时升级。
 *
 * <p>匹配优先级：命中维度数量多的规则胜出，同分时按 priority_weight 降序、id 升序。</p>
 *
 * <p>关键设计：</p>
 * <ul>
 *   <li>规则按 categoryId + urgency + safetyFlag + affectScope 四个维度匹配，配置中为 null
 *       的维度表示“不限”，四维全空即全局兜底规则；“具体维度数”越多说明规则越精确。</li>
 *   <li>超时点 = 工单创建时间 + 响应/处理时限；未命中规则时使用配置文件中的默认时限。</li>
 *   <li>超时判定由 RocketMQ 任意秒延迟消息触发，不做定时轮询；消息到期后回调本类的
 *       {@link #markResponseBreached(Long)} / {@link #markHandleBreached(Long)}。</li>
 *   <li>标记超时前先看 Redis 状态缓存做快速判定，缓存缺失或落后时回库以数据库为准；
 *       已推进、已终结或已标记过的工单直接返回 false，保证消息重复投递时幂等。</li>
 *   <li>首次判定超时会在同一事务内同时置“超时”和“已升级”标记，返回 true 后由消费端
 *       通知管理员。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SlaServiceImpl implements SlaService {

    /** 启用状态 */
    private static final int ENABLED = 1;

    /** 超时标记：已超时 / 已升级（数据库 tinyint 布尔语义） */
    private static final int FLAG_YES = 1;

    // SLA 规则数据访问
    private final SlaConfigMapper slaConfigMapper;

    // 工单数据访问（超时判定的权威数据来源）
    private final WorkOrderMapper workOrderMapper;

    // SLA 超时延迟消息生产者（响应、处理各投递一条）
    private final SlaProducer slaProducer;

    // 工单状态缓存（超时判定的快路径）
    private final OrderCacheService orderCacheService;

    /** 无规则命中时的兜底响应时限（分钟） */
    @Value("${repair.sla.default-response-timeout:120}")
    private int defaultResponseTimeout;

    /** 无规则命中时的兜底处理时限（分钟） */
    @Value("${repair.sla.default-handle-timeout:1440}")
    private int defaultHandleTimeout;

    /**
     * 匹配最精确的启用 SLA 规则。
     *
     * <p>先取出全部启用规则，过滤掉与工单四个维度不匹配的规则，再按“具体维度数 → 派单权重
     * 降序 → id 升序”取最优。规则字段为 null 表示该维度不限；四维全空的全局兜底规则同样会
     * 参与匹配并可能被返回。所有规则都被禁用时返回 null，由调用方使用默认时限。</p>
     *
     * @param categoryId  报修类型 ID
     * @param urgency     紧急程度
     * @param safetyFlag  安全标记（可能包含多个子标记）
     * @param affectScope 影响范围
     * @return 命中的规则；无任何启用规则时返回 null
     */
    @Override
    public SlaConfig matchSla(Long categoryId, Integer urgency, String safetyFlag, Integer affectScope) {
        // 取出全部启用规则，后续在内存中过滤匹配
        List<SlaConfig> configs = slaConfigMapper.selectList(Wrappers.<SlaConfig>lambdaQuery()
                .eq(SlaConfig::getEnabled, ENABLED));
        // 无启用规则时降级为默认时限
        if (configs.isEmpty()) {
            // 无任何启用规则时告警，由调用方使用默认时限
            log.warn("SLA 规则全部禁用，使用默认时限：response={}min, handle={}min",
                    defaultResponseTimeout, defaultHandleTimeout);
            // 无可用规则时返回空，交由调用方用默认时限
            return null;
        }
        // 过滤出匹配规则，再按精确度比较器取最优
        return configs.stream()
                .filter(config -> matches(config, categoryId, urgency, safetyFlag, affectScope))
                // 比较器按“命中维度数降序 → 权重降序 → id 升序”取最大；id 取负值使较小 id 胜出
                .max(Comparator
                        .comparingInt((SlaConfig config) -> matchedDimensions(config, categoryId, urgency, safetyFlag, affectScope))
                        .thenComparingInt(config -> config.getPriorityWeight() == null ? 0 : config.getPriorityWeight())
                        .thenComparing(config -> -config.getId()))
                .orElse(null);
    }

    /**
     * 按 SLA 规则计算并写入工单的响应/处理超时点。
     *
     * <p>规则或规则中的时限为空时回退到配置默认值；基准时间取工单创建时间，缺失时取当前时间。
     * 同时把升级、响应超时、处理超时三个标记重置为 0。本方法只修改传入实体，不落库，
     * 由调用方在同一个事务中随工单一起保存。</p>
     *
     * @param order     待计算超时点的工单实体（会被原地修改）
     * @param slaConfig 命中的 SLA 规则，可为 null（使用默认时限）
     */
    @Override
    public void applyDeadlines(WorkOrder order, SlaConfig slaConfig) {
        // 规则缺失或时限为空时回退配置默认响应时限
        int responseTimeout = slaConfig == null || slaConfig.getResponseTimeout() == null
                ? defaultResponseTimeout : slaConfig.getResponseTimeout();
        // 规则缺失或时限为空时回退配置默认处理时限
        int handleTimeout = slaConfig == null || slaConfig.getHandleTimeout() == null
                ? defaultHandleTimeout : slaConfig.getHandleTimeout();
        // 基准时间取工单创建时间，缺失时用当前时间兜底
        LocalDateTime base = order.getCreateTime() == null ? LocalDateTime.now() : order.getCreateTime();
        // 响应超时点 = 基准时间 + 响应时限
        order.setSlaResponseDeadline(base.plusMinutes(responseTimeout));
        // 处理超时点 = 基准时间 + 处理时限
        order.setSlaHandleDeadline(base.plusMinutes(handleTimeout));
        // 重置升级标记：新计算时视为未升级
        order.setSlaEscalated(0);
        // 重置响应超时标记
        order.setSlaResponseBreached(0);
        // 重置处理超时标记
        order.setSlaHandleBreached(0);
        // 记录计算后的超时点
        log.info("工单 SLA 计算：responseTimeout={}min, handleTimeout={}min, 响应超时点={}, 处理超时点={}",
                responseTimeout, handleTimeout, order.getSlaResponseDeadline(), order.getSlaHandleDeadline());
    }

    /**
     * 投递响应、处理两条 SLA 超时延迟消息。
     *
     * <p>延迟时长由 {@link SlaProducer} 按“超时点 - 当前时间”计算，到期后由
     * {@code SlaTimeoutConsumer} 回调本类做超时判定。需在 {@link #applyDeadlines} 之后调用，
     * 否则超时点为空。投递失败时由生产者抛出系统异常。</p>
     *
     * @param order 已写入超时点的工单
     */
    @Override
    public void scheduleTimeoutTasks(WorkOrder order) {
        // 投递响应超时延迟消息
        slaProducer.sendTimeoutTask(order.getId(), MqConstants.SlaTimeout.RESPONSE, order.getSlaResponseDeadline());
        // 投递处理超时延迟消息
        slaProducer.sendTimeoutTask(order.getId(), MqConstants.SlaTimeout.HANDLE, order.getSlaHandleDeadline());
    }

    /**
     * 处理响应超时消息：确认工单未在时限内接单后标记响应超时并升级。
     *
     * <p>幂等与防误判：先用 Redis 状态缓存快速判断是否已推进到接单之后，未命中再回库；
     * 工单不存在、状态未知/已终结、已有接单时间或已被标记过响应超时，均返回 false。
     * 首次标记时在同一事务内同时写入“响应超时”和“已升级”标记。</p>
     *
     * @param orderId 工单主键
     * @return true 表示本次新标记了响应超时（消费端据此发送管理员通知）；false 表示忽略
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean markResponseBreached(Long orderId) {
        // 缓存快路径：状态已推进时直接忽略超时消息，无需回库
        if (isAdvanced(orderId, OrderStatusEnum.ACCEPTED)) {
            // 已接单，超时消息视为过期
            log.info("响应已在时限内达成，忽略响应超时消息：orderId={}", orderId);
            // 已达成时限，无需标记超时
            return false;
        }
        // 缓存未命中，回库取权威数据
        WorkOrder order = workOrderMapper.selectById(orderId);
        // 工单不存在时无法判定，忽略该消息
        if (order == null) {
            // 工单不存在，无需标记
            log.warn("响应超时消息对应的工单不存在：orderId={}", orderId);
            // 无工单可标记，直接返回忽略
            return false;
        }
        // 数据库权威判断：状态未知/终态、已接单或已标记过超时，均视为无需处理
        OrderStatusEnum status = OrderStatusEnum.of(order.getStatus());
        // 任一条件命中都视为无需标记
        if (status == null || status.isFinal() || order.getAcceptedTime() != null
                || Integer.valueOf(FLAG_YES).equals(order.getSlaResponseBreached())) {
            // 已推进或已标记，返回忽略保持幂等
            return false;
        }
        // 同一事务内写入响应超时与升级标记，避免出现“已超时但未升级”的中间态
        workOrderMapper.update(null, Wrappers.<WorkOrder>lambdaUpdate()
                .eq(WorkOrder::getId, orderId)
                .set(WorkOrder::getSlaResponseBreached, FLAG_YES)
                .set(WorkOrder::getSlaEscalated, FLAG_YES));
        // 记录超时升级告警
        log.warn("工单响应超时，已升级：orderId={}, orderNo={}, 超时点={}",
                orderId, order.getOrderNo(), order.getSlaResponseDeadline());
        // 返回 true 通知消费端发送管理员通知
        return true;
    }

    /**
     * 处理处理超时消息：确认工单未提交完工后标记处理超时并升级。
     *
     * <p>判定逻辑与响应超时一致：缓存快路径 + 数据库权威判断；工单不存在、状态未知/已终结、
     * 已有提交完工时间或已标记过处理超时时返回 false。首次标记时同时写入处理超时与升级标记。</p>
     *
     * @param orderId 工单主键
     * @return true 表示本次新标记了处理超时；false 表示忽略
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean markHandleBreached(Long orderId) {
        // 缓存快路径：状态已推进到提交完工或终态时直接忽略，无需回库
        if (isAdvanced(orderId, OrderStatusEnum.SUBMITTED)) {
            // 已提交完工，超时消息视为过期
            log.info("处理已在时限内达成，忽略处理超时消息：orderId={}", orderId);
            // 已达成时限，无需标记超时
            return false;
        }
        // 缓存未命中，回库取权威数据
        WorkOrder order = workOrderMapper.selectById(orderId);
        // 工单不存在时无法判定，忽略该消息
        if (order == null) {
            // 工单不存在，无需标记
            log.warn("处理超时消息对应的工单不存在：orderId={}", orderId);
            // 无工单可标记，直接返回忽略
            return false;
        }
        // 数据库权威判断：状态未知/终态、已提交完工或已标记过超时，均视为无需处理
        OrderStatusEnum status = OrderStatusEnum.of(order.getStatus());
        // 任一条件命中都视为无需标记
        if (status == null || status.isFinal() || order.getSubmittedTime() != null
                || Integer.valueOf(FLAG_YES).equals(order.getSlaHandleBreached())) {
            // 已推进或已标记，返回忽略保持幂等
            return false;
        }
        // 同一事务内写入处理超时与升级标记
        workOrderMapper.update(null, Wrappers.<WorkOrder>lambdaUpdate()
                .eq(WorkOrder::getId, orderId)
                .set(WorkOrder::getSlaHandleBreached, FLAG_YES)
                .set(WorkOrder::getSlaEscalated, FLAG_YES));
        // 记录超时升级告警
        log.warn("工单处理超时，已升级：orderId={}, orderNo={}, 超时点={}",
                orderId, order.getOrderNo(), order.getSlaHandleDeadline());
        // 返回 true 通知消费端发送管理员通知
        return true;
    }

    /**
     * 缓存快路径：缓存中的状态已达到（或越过）阈值状态时，无需回库即可判定已达标。
     * 缓存缺失或状态落后时返回 false，继续走数据库权威判断，避免用过期缓存做出误判。
     *
     * <p>响应阈值 ACCEPTED：除 PENDING、ASSIGNED、CANCELED 外均视为已推进；
     * 处理阈值 SUBMITTED：仅 SUBMITTED、COMPLETED、CLOSED、CANCELED 视为已推进。
     * 注意缓存只能用于确认“已达标”，不能用于确认“未达标”。</p>
     *
     * @param orderId   工单主键
     * @param threshold 目标里程碑状态
     * @return true 表示缓存状态已越过阈值，可快速忽略超时消息
     */
    private boolean isAdvanced(Long orderId, OrderStatusEnum threshold) {
        // 取 Redis 中的状态缓存
        WorkOrderStatusDTO cached = orderCacheService.getStatus(orderId);
        // 缓存缺失则无法快速判定
        if (cached == null) {
            // 缓存缺失，交由数据库权威判断
            return false;
        }
        // 缓存中的状态码转枚举
        OrderStatusEnum status = OrderStatusEnum.of(cached.getStatus());
        // 缓存状态码需能转成合法枚举
        if (status == null) {
            // 状态码非法，缓存不可信
            return false;
        }
        // 响应阈值与处理阈值判定口径不同
        if (threshold == OrderStatusEnum.ACCEPTED) {
            // 响应阈值：除待派/已派/取消外均视为已接单
            return status != OrderStatusEnum.PENDING && status != OrderStatusEnum.ASSIGNED
                    && status != OrderStatusEnum.CANCELED;
        }
        // 处理阈值：仅完工及之后的终态视为已推进
        return status == OrderStatusEnum.SUBMITTED || status == OrderStatusEnum.COMPLETED
                || status == OrderStatusEnum.CLOSED || status == OrderStatusEnum.CANCELED;
    }

    /**
     * 判断规则是否匹配工单维度。
     *
     * <p>规则字段为 null 表示该维度不限制；safetyFlag 采用单向包含匹配：规则配置的安全标记
     * 必须出现在工单的安全标记集合中。</p>
     *
     * @return true 表示所有已配置维度均匹配
     */
    private boolean matches(SlaConfig config, Long categoryId, Integer urgency, String safetyFlag,
                            Integer affectScope) {
        // 类型维度不一致则整体不匹配
        if (config.getCategoryId() != null && !config.getCategoryId().equals(categoryId)) {
            // 配置了类型维度但不一致，直接判定不匹配
            return false;
        }
        // 紧急程度维度不一致则整体不匹配
        if (config.getUrgency() != null && !config.getUrgency().equals(urgency)) {
            // 配置了紧急程度维度但不一致
            return false;
        }
        // 影响范围维度不一致则整体不匹配
        if (config.getAffectScope() != null && !config.getAffectScope().equals(affectScope)) {
            // 配置了影响范围维度但不一致
            return false;
        }
        // 安全标记维度需单向包含匹配
        if (config.getSafetyFlag() != null) {
            // 安全标记做单向包含匹配
            SafetyFlagEnum flag = safetyFlagOf(config.getSafetyFlag());
            // 规则标记不在工单标记集合中则不匹配
            if (flag == null || !SafetyFlagEnum.contains(safetyFlag, flag)) {
                // 工单安全标记中不含规则要求项
                return false;
            }
        }
        // 所有已配置维度均匹配
        return true;
    }

    /**
     * 命中的非空维度数，用于衡量规则精确度（数值越大优先级越高）。
     *
     * <p>调用前已通过 {@link #matches} 过滤，因此这里只需统计规则中配置了具体值的维度数量。</p>
     *
     * @return 0~4 的具体维度数
     */
    private int matchedDimensions(SlaConfig config, Long categoryId, Integer urgency, String safetyFlag,
                                  Integer affectScope) {
        // 初始化命中维度计数
        int matched = 0;
        // 逐维度累加：规则配置了具体值即计 1 分
        matched += config.getCategoryId() == null ? 0 : 1;
        // 紧急程度维度
        matched += config.getUrgency() == null ? 0 : 1;
        // 影响范围维度
        matched += config.getAffectScope() == null ? 0 : 1;
        // 安全标记维度
        matched += config.getSafetyFlag() == null ? 0 : 1;
        // 返回 0~4 的具体维度数
        return matched;
    }

    /**
     * 按枚举值查找安全标记，未知值返回 null。
     *
     * @param value 规则中配置的安全标记字符串
     * @return 对应枚举；无匹配时返回 null
     */
    private SafetyFlagEnum safetyFlagOf(String value) {
        // 遍历枚举按值匹配，兼容配置中的空白字符
        for (SafetyFlagEnum flag : SafetyFlagEnum.values()) {
            // 按枚举值逐项比对
            if (flag.getValue().equals(value.trim())) {
                // 命中对应枚举
                return flag;
            }
        }
        // 无匹配时返回 null
        return null;
    }
}