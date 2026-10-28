package com.ycc.zhixiucloud.service;

import com.ycc.zhixiucloud.dto.WorkOrderStatusDTO;
import com.ycc.zhixiucloud.entity.WorkOrder;

import java.util.List;

/**
 * 工单缓存服务：维护 Redis 待接单池（ZSet）与工单状态缓存（String）。
 *
 * <p>职责边界：缓存只用于读加速与待接单列表排序，真值始终以 MySQL 为准；
 * 调用方读取待接单池后需要回表校验，并清理已不在待接单状态的脏数据。</p>
 *
 * <p>主要使用方：工单创建/状态变更（写入与移除）、师傅待接单列表（读取）、
 * SLA 超时判断（状态快路径）。</p>
 *
 * <p>关键实现要点：待接单池 score 同时编码紧急程度、SLA 派单权重与创建时间，
 * 升序读取即得到“紧急度高、SLA 权重高、创建早”的顺序；状态缓存 TTL 为 1 小时，
 * 未命中时由调用方回落数据库。</p>
 */
public interface OrderCacheService {

    /**
     * 将工单加入待接单池。
     *
     * <p>score 由紧急程度、SLA 派单权重与创建时间共同决定，具体公式见实现类；
     * 权重越高越靠前，不同紧急程度分层之间不会相互串层。</p>
     *
     * @param order          工单实体（需包含 id、urgency、createTime）
     * @param priorityWeight SLA 规则中的派单权重，可为 null（按 0 处理）
     */
    void addToPendingPool(WorkOrder order, Integer priorityWeight);

    /**
     * 将工单移出待接单池。
     *
     * <p>幂等操作：工单不在池中时静默返回。</p>
     *
     * @param orderId 工单 ID
     */
    void removeFromPendingPool(Long orderId);

    /**
     * 按优先级读取待接单池中的工单 ID。
     *
     * <p>ZSet 升序即为优先级顺序：紧急度高的在前，同紧急度下 SLA 权重高的在前，
     * 再按创建时间早的在前。返回的 ID 可能包含缓存脏数据，调用方需回表校验。</p>
     *
     * @return 工单 ID 列表；池为空时返回空列表
     */
    List<Long> listPendingOrderIds();

    /**
     * 写入/刷新工单状态缓存。
     *
     * <p>缓存内容包含工单号、状态码、状态中文名与写入时间，TTL 1 小时；
     * 状态变更后由调用方主动刷新。</p>
     *
     * @param order 工单实体（需包含 id、orderNo、status）
     */
    void cacheStatus(WorkOrder order);

    /**
     * 读取工单状态缓存。
     *
     * <p>未命中或缓存类型异常时返回 null，调用方应回落数据库查询。
     * SLA 超时判断会先用该缓存做快速路径，但只在缓存状态已达标时才采信。</p>
     *
     * @param orderId 工单 ID
     * @return 状态缓存 DTO；未命中返回 null
     */
    WorkOrderStatusDTO getStatus(Long orderId);
}