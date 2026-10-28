package com.ycc.zhixiucloud.service;

import com.ycc.zhixiucloud.entity.SlaConfig;
import com.ycc.zhixiucloud.entity.WorkOrder;

/**
 * SLA 服务：SLA 规则匹配、超时点计算、延迟超时任务投递与超时升级。
 *
 * <p>职责边界：不修改工单状态机，只在超时消息到达后标记工单的超时/升级标志；
 * 工单创建与状态流转由 {@link WorkOrderService} / 工单状态机驱动。</p>
 *
 * <p>主要使用方：工单创建（匹配规则、计算超时点、投递延迟消息）、
 * SLA 超时消息消费者（标记超时升级）。</p>
 *
 * <p>关键实现要点：规则按 category_id + urgency + safety_flag + affect_scope 四维匹配，
 * 命中维度越多优先级越高；超时任务通过 RocketMQ 延迟消息实现，响应与处理各一条；
 * 标记超时前会先用状态缓存做快速判断，命中后再回数据库做幂等校验。</p>
 */
public interface SlaService {

    /**
     * 为工单匹配最精确的启用规则。
     *
     * <p>匹配规则：命中维度数多者优先；同分时 priority_weight 大者优先，再按 ID 小者优先。
     * 全局兜底规则（各维度为空）同样参与匹配并可能被返回；仅当所有规则被禁用、
     * 或没有任何规则命中时才返回 null，由调用方使用默认时限。</p>
     *
     * @param categoryId  报修类型 ID
     * @param urgency     紧急程度
     * @param safetyFlag  安全隐患标志（逗号分隔，可为空）
     * @param affectScope 影响范围
     * @return 命中的规则；无有效规则返回 null
     */
    SlaConfig matchSla(Long categoryId, Integer urgency, String safetyFlag, Integer affectScope);

    /**
     * 按规则计算并写入工单的响应/处理超时点。
     *
     * <p>以工单创建时间为基准（为空则取当前时间），规则缺失时使用配置的默认时限
     * （默认响应 120 分钟、处理 1440 分钟）；同时把超时/升级标志重置为 0。</p>
     *
     * <p>该方法只修改内存中的工单实体，不直接落库，由调用方随工单一起保存。</p>
     *
     * @param order     待创建或待更新的工单
     * @param slaConfig 匹配到的规则，可为 null（使用默认时限）
     */
    void applyDeadlines(WorkOrder order, SlaConfig slaConfig);

    /**
     * 投递响应超时与处理超时两条 RocketMQ 延迟消息。
     *
     * <p>消息到达时间分别为工单的 slaResponseDeadline 与 slaHandleDeadline；
     * 消费方会再次校验工单是否已达标，避免误判。</p>
     *
     * @param order 已完成超时点计算的工单
     */
    void scheduleTimeoutTasks(WorkOrder order);

    /**
     * 标记响应超时并升级工单。
     *
     * <p>由响应超时延迟消息消费者调用。若缓存显示工单已越过待接单阶段、已取消、
     * 已终态、已记录接单时间或已标记过超时，则返回 false（幂等）。
     * 标记成功后同时置 sla_response_breached=1、sla_escalated=1。</p>
     *
     * @param orderId 工单 ID
     * @return true 表示本次首次标记超时；false 表示无需处理或已处理过
     */
    boolean markResponseBreached(Long orderId);

    /**
     * 标记处理超时并升级工单。
     *
     * <p>由处理超时延迟消息消费者调用。若工单已提交完工、已终态、已记录提交时间
     * 或已标记过超时，则返回 false（幂等）。标记成功后同时置
     * sla_handle_breached=1、sla_escalated=1。</p>
     *
     * @param orderId 工单 ID
     * @return true 表示本次首次标记超时；false 表示无需处理或已处理过
     */
    boolean markHandleBreached(Long orderId);
}