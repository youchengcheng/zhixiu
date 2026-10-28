package com.ycc.zhixiucloud.service.impl;

import com.ycc.zhixiucloud.common.constant.RedisKeys;
import com.ycc.zhixiucloud.common.enums.OrderStatusEnum;
import com.ycc.zhixiucloud.dto.WorkOrderStatusDTO;
import com.ycc.zhixiucloud.entity.WorkOrder;
import com.ycc.zhixiucloud.service.OrderCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 工单缓存实现。
 *
 * <p>维护两类 Redis 数据：</p>
 * <ul>
 *   <li>待接单池 ZSet（workorder:pending:pool）：member 为工单 ID，score 为派单优先级，供派单/抢单按优先级有序读取；</li>
 *   <li>工单状态缓存（workorder:status:{orderId}）：value 为状态 DTO，TTL 1 小时，降低状态查询对数据库的压力。</li>
 * </ul>
 *
 * <p>一致性策略：数据库仍是唯一事实来源，缓存只做加速；工单状态变化时覆盖写缓存，
 * 工单离开待接单流程时从 ZSet 删除，状态缓存未命中返回 null 由调用方决定回源。</p>
 *
 * <p>待接单池 score 计算：score = (10 - 紧急程度) * 1e12 + 创建时间秒 - SLA 派单权重 * 1e6。
 * 分层跨度远大于秒级时间戳与权重偏移，区间互不重叠，因此升序取前 N 个即
 * 「紧急度高优先、SLA 权重高优先、创建早优先」。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCacheServiceImpl implements OrderCacheService {

    /** 紧急程度分层跨度，保证不同紧急度不会串层 */
    private static final long URGENCY_BAND = 1_000_000_000_000L;

    /** SLA 权重偏移步长 */
    private static final long PRIORITY_STEP = 1_000_000L;

    /** 紧急程度基准值，高于最大值 4 留出余量 */
    private static final int URGENCY_BASE = 10;

    /** 工单状态缓存 TTL（秒），与开发文档 Redis 清单一致 */
    private static final long STATUS_CACHE_SECONDS = 3600L;

    private final StringRedisTemplate stringRedisTemplate;

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 将工单加入待接单池。
     *
     * <p>以工单 ID 为 member、优先级得分为 score 写入 ZSet；重复加入会更新 score，天然幂等。</p>
     *
     * @param order          工单，需包含 ID、紧急程度与创建时间
     * @param priorityWeight SLA 派单权重，可为 null，按 0 处理
     */
    @Override
    public void addToPendingPool(WorkOrder order, Integer priorityWeight) {
        // 计算派单优先级得分
        double score = score(order, priorityWeight);
        // 写入待接单池 ZSet，重复加入会覆盖旧分
        stringRedisTemplate.opsForZSet().add(RedisKeys.PENDING_POOL, String.valueOf(order.getId()), score);
        // 记录加入待接单池日志
        log.debug("工单加入待接单池：orderId={}, score={}", order.getId(), score);
    }

    /**
     * 将工单移出待接单池。
     *
     * <p>工单被接单、取消或关闭后调用；member 不存在时静默返回，保证幂等。</p>
     *
     * @param orderId 工单 ID
     */
    @Override
    public void removeFromPendingPool(Long orderId) {
        // 从 ZSet 删除成员，返回实际移除数量
        Long removed = stringRedisTemplate.opsForZSet().remove(RedisKeys.PENDING_POOL, String.valueOf(orderId));
        // 判断成员是否真的被移除
        if (removed != null && removed > 0) {
            // 确实移除成功才记录日志
            log.debug("工单移出待接单池：orderId={}", orderId);
        }
    }

    /**
     * 按优先级升序返回待接单工单 ID。
     *
     * <p>直接读取 ZSet 全量成员（0 到 -1），返回顺序即派单推荐顺序；池为空时返回空列表。</p>
     *
     * @return 按优先级排序的工单 ID 列表
     */
    @Override
    public List<Long> listPendingOrderIds() {
        // 读取 ZSet 全量成员，返回顺序即派单推荐顺序
        Set<String> members = stringRedisTemplate.opsForZSet().range(RedisKeys.PENDING_POOL, 0, -1);
        // 池为空时提前返回
        if (members == null || members.isEmpty()) {
            // 池为空返回空列表
            return Collections.emptyList();
        }
        // 成员是工单 ID 字符串，转回 Long 列表
        return members.stream().map(Long::valueOf).toList();
    }

    /**
     * 缓存工单状态。
     *
     * <p>写入工单 ID、工单号、状态码、状态名称与缓存时间，TTL 为 1 小时；
     * 状态变化时再次调用即可覆盖旧值。</p>
     *
     * @param order 工单，需包含 ID、工单号与状态
     */
    @Override
    public void cacheStatus(WorkOrder order) {
        // 组装状态 DTO，含工单号、状态码与文案
        WorkOrderStatusDTO statusDTO = new WorkOrderStatusDTO(order.getId(), order.getOrderNo(),
                order.getStatus(), OrderStatusEnum.nameOf(order.getStatus()), LocalDateTime.now());
        // 覆盖写缓存并设置 1 小时 TTL
        redisTemplate.opsForValue().set(RedisKeys.of(RedisKeys.ORDER_STATUS, order.getId()),
                statusDTO, STATUS_CACHE_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 读取工单状态缓存。
     *
     * <p>未命中或缓存类型不匹配时返回 null，由调用方决定是否回源数据库。</p>
     *
     * @param orderId 工单 ID
     * @return 缓存的状态 DTO，未命中返回 null
     */
    @Override
    public WorkOrderStatusDTO getStatus(Long orderId) {
        // 读取状态缓存原始值
        Object cached = redisTemplate.opsForValue().get(RedisKeys.of(RedisKeys.ORDER_STATUS, orderId));
        // 类型匹配才返回，否则视为未命中
        return cached instanceof WorkOrderStatusDTO statusDTO ? statusDTO : null;
    }

    /**
     * 计算待接单池排序分数。
     *
     * <p>紧急程度按 1e12 分层，层内以创建时间秒升序，再减去 SLA 权重偏移
     * （权重越高分数越小、越靠前）。紧急程度缺省为 2，权重缺省为 0，
     * 创建时间为空时取当前时间，避免空指针。</p>
     *
     * @param order          工单
     * @param priorityWeight SLA 派单权重
     * @return ZSet score，越小优先级越高
     */
    private double score(WorkOrder order, Integer priorityWeight) {
        // 紧急程度缺省为 2
        int urgency = order.getUrgency() == null ? 2 : order.getUrgency();
        // SLA 权重缺省为 0
        int weight = priorityWeight == null ? 0 : priorityWeight;
        // 创建时间缺失时取当前时间，避免空指针
        LocalDateTime createTime = order.getCreateTime() == null ? LocalDateTime.now() : order.getCreateTime();
        // 创建时间转秒级时间戳
        long createEpochSecond = createTime.atZone(ZoneId.systemDefault()).toEpochSecond();
        // 紧急度分层 + 时间戳 - 权重偏移，越小越靠前
        return (URGENCY_BASE - urgency) * (double) URGENCY_BAND + createEpochSecond - weight * (double) PRIORITY_STEP;
    }
}