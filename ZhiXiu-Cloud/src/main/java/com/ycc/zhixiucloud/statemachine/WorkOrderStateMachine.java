package com.ycc.zhixiucloud.statemachine;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ycc.zhixiucloud.common.enums.OperatorTypeEnum;
import com.ycc.zhixiucloud.common.enums.OrderActionEnum;
import com.ycc.zhixiucloud.common.enums.OrderStatusEnum;
import com.ycc.zhixiucloud.common.exception.BizException;
import com.ycc.zhixiucloud.common.result.ResultCode;
import com.ycc.zhixiucloud.dto.WorkOrderEvent;
import com.ycc.zhixiucloud.entity.MaintenanceWorker;
import com.ycc.zhixiucloud.entity.WorkOrder;
import com.ycc.zhixiucloud.mapper.MaintenanceWorkerMapper;
import com.ycc.zhixiucloud.mapper.WorkOrderMapper;
import com.ycc.zhixiucloud.mq.producer.OrderEventProducer;
import com.ycc.zhixiucloud.service.WorkOrderLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 工单状态机：全系统唯一允许修改 work_order.status 的地方。
 *
 * <p>在整体架构中的位置：所有工单状态变化（派单、抢单、到场、暂停、转单、完工、取消等）
 * 都必须经过本类，Service 层只负责权限校验、参数组装与调用，不直接 update 状态字段。</p>
 *
 * <p>事件驱动模型：调用方不直接指定目标状态，而是提交一个 {@link OrderActionEnum} 事件
 * （如 ASSIGN、ARRIVE、CANCEL），由状态机根据当前状态在 {@link #TRANSITIONS} 中解析出目标状态。
 * 这样业务语义与状态编码解耦，新增流程只需维护流转表，避免散落的 if-else 与非法跳转。</p>
 *
 * <p>一次流转的固定步骤：</p>
 * <ol>
 *   <li>校验当前状态合法并解析目标状态，非法流转抛 2002；</li>
 *   <li>写入本次动作对应的时间戳等附加字段；</li>
 *   <li>通过带 version 的 updateById 落库，影响行数为 0 视为并发冲突并抛 409；</li>
 *   <li>按需显式清空受理人字段；</li>
 *   <li>写 work_order_log 流转日志；</li>
 *   <li>结算师傅负载与接单/完工计数；</li>
 *   <li>投递 RocketMQ 领域事件。</li>
 * </ol>
 *
 * <p>并发控制：状态更新走 work_order.version 乐观锁，更新影响行数为 0 说明工单已被其它请求改动，
 * 直接抛出 409 由前端刷新重试，绝不「后写覆盖」。</p>
 *
 * <p>一致性：状态落库、work_order_log 记录、RocketMQ 事件投递在同一事务内完成，
 * 投递失败会连带回滚业务，避免出现「状态已变但没有事件」的中间态。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkOrderStateMachine {

    /** 合法流转表：当前状态 -&gt; （事件 -&gt; 目标状态），静态构建后不可修改 */
    private static final Map<OrderStatusEnum, Map<OrderActionEnum, OrderStatusEnum>> TRANSITIONS =
            buildTransitions();

    /** 计入师傅负载的状态：已分配且未完结 */
    private static final List<String> LOAD_STATUSES = List.of(
            OrderStatusEnum.ACCEPTED.getCode(),
            OrderStatusEnum.GOING.getCode(),
            OrderStatusEnum.PAUSED.getCode(),
            OrderStatusEnum.TRANSFER_PENDING.getCode());

    /** 工单表访问：状态更新走 MyBatis-Plus 乐观锁，负载统计走条件查询 */
    private final WorkOrderMapper workOrderMapper;

    /** 流转日志服务：每次状态变化写一条 work_order_log */
    private final WorkOrderLogService workOrderLogService;

    /** 维修师傅表访问：维护 current_load 与接单/完工计数 */
    private final MaintenanceWorkerMapper maintenanceWorkerMapper;

    /** 领域事件生产者：向 RocketMQ 投递工单状态变化事件，驱动通知与缓存更新 */
    private final OrderEventProducer orderEventProducer;

    /**
     * 工单创建：写入初始流转日志并投递 CREATED 事件，由创建工单的事务内调用。
     *
     * <p>创建走独立入口，是因为新工单还没有「变更前状态」，且必须校验初始状态为 PENDING。
     * 本方法不修改工单实体，只负责补日志与发事件，工单本身由创建方落库。</p>
     *
     * @param order        已落库的工单实体
     * @param operatorType 操作人类型（业主自助创建或系统创建）
     * @param remark       创建备注，写入流转日志
     */
    public void onCreated(WorkOrder order, OperatorTypeEnum operatorType, String remark) {
        // 本方法只处理新建工单，先校验初始状态必须为待接单
        if (!OrderStatusEnum.PENDING.getCode().equals(order.getStatus())) {
            // 初始状态异常说明创建链路有误，直接抛系统错误
            throw BizException.of(ResultCode.SYSTEM_ERROR, "工单初始状态必须为待接单");
        }
        // 补写一条 CREATE 流转日志，记录操作人与备注
        recordLog(order.getId(), null, operatorType, OrderActionEnum.CREATE, null, OrderStatusEnum.PENDING, remark);
        // 投递 CREATED 领域事件，驱动下游通知与缓存更新
        publishEvent(order, OrderActionEnum.CREATE, null, OrderStatusEnum.PENDING, null, operatorType, remark);
    }

    /**
     * 状态流转：校验合法性后更新状态、写流转日志、结算师傅负载、投递事件。
     *
     * <p>调用方只提交事件，目标状态由流转表推导；非法事件抛 2002，并发冲突抛 409。
     * 整个方法处于同一事务内，任一步失败（包括 MQ 投递失败）都会回滚状态与日志。</p>
     *
     * @param order        已从库中读出的工单实体，调用方可先设置本次动作的附加字段（如受理师傅、完工说明）
     * @param action       本次事件
     * @param operatorId   操作人 ID，系统触发传 null
     * @param operatorType 操作人类型
     * @param remark       备注，写入流转日志
     */
    @Transactional(rollbackFor = Exception.class)
    public void transition(WorkOrder order, OrderActionEnum action, Long operatorId,
                           OperatorTypeEnum operatorType, String remark) {
        // 1. 解析当前状态；库中若存在未知状态值，直接拒绝流转
        OrderStatusEnum from = OrderStatusEnum.of(order.getStatus());
        // 库中状态无法映射枚举时视为非法状态
        if (from == null) {
            // 未知状态不允许继续流转，抛出 2002
            throw BizException.of(ResultCode.ORDER_STATUS_INVALID,
                    "未知的工单状态：" + order.getStatus());
        }
        // 2. 查表得到目标状态；from + action 不在表中即为非法流转
        OrderStatusEnum to = resolve(from, action);
        // 统一取本次流转时间，供各时间戳字段复用
        LocalDateTime now = LocalDateTime.now();

        // 记录变更前受理人，用于后续负载重算（改派、超时回退、取消时需要扣减原师傅负载）
        Long previousAcceptedWorkerId = order.getAcceptedWorkerId();
        // 2. 修改状态并补充本次动作对应的时间戳/取消原因等字段
        order.setStatus(to.getCode());
        // 依据动作补齐时间戳、取消原因等动作特有字段
        fillActionFields(order, action, from, now);

        // 3. 带 version 条件更新；影响行数为 0 表示版本已被其它请求推进，触发乐观锁冲突
        int updated = workOrderMapper.updateById(order);
        // 影响行数为 0 说明工单已被其它请求改动
        if (updated == 0) {
            // 记录乐观锁冲突现场，便于排查并发问题
            log.warn("工单状态更新失败（乐观锁冲突）：orderId={}, {} -> {}", order.getId(), from.getCode(), to.getCode());
            // 抛 409 交由前端刷新后重试，绝不覆盖写入
            throw BizException.of(ResultCode.CONFLICT, "工单状态已被其它操作变更，请刷新后重试");
        }
        // 4. updateById 会忽略 null，需要在同一事务内用条件更新显式清空受理人
        clearFieldsIfNeeded(order, action, from);

        // 5. 记录流转日志，保留操作人、前后状态与备注，供工单轨迹与审计使用
        recordLog(order.getId(), operatorId, operatorType, action, from, to, remark);
        // 6. 结算师傅负载与接单/完工计数
        settleWorkers(order, action, to, previousAcceptedWorkerId);
        // 7. 投递领域事件（事件 ID 用于下游幂等），投递失败随事务回滚
        publishEvent(order, action, operatorId, to, from, operatorType, remark);
        // 输出本次流转摘要日志，含前后状态与操作人类型
        log.info("工单状态流转：orderId={}, action={}, {} -> {}, operatorType={}, remark={}",
                order.getId(), action.name(), from.getCode(), to.getCode(), operatorType.getCode(), remark);
    }

    /**
     * 判断某状态是否允许执行某事件。
     *
     * @param from   当前状态，允许为 null（返回 false）
     * @param action 待校验事件
     * @return 流转表中存在该组合时返回 true
     */
    public boolean allow(OrderStatusEnum from, OrderActionEnum action) {
        // 仅当流转表存在「当前状态 + 事件」组合时才允许
        return from != null && TRANSITIONS.getOrDefault(from, Collections.emptyMap()).containsKey(action);
    }

    /**
     * 解析目标状态，非法流转抛 2002。
     *
     * @param from   当前状态
     * @param action 本次事件
     * @return 目标状态
     * @throws BizException 当前状态不允许该事件时抛出
     */
    public OrderStatusEnum resolve(OrderStatusEnum from, OrderActionEnum action) {
        // 从流转表取出目标状态，取不到即为非法组合
        OrderStatusEnum to = TRANSITIONS.getOrDefault(from, Collections.emptyMap()).get(action);
        // 无目标状态说明该事件不得在当前状态执行
        if (to == null) {
            // 抛 2002，提示信息中带上前后状态语义
            throw BizException.of(ResultCode.ORDER_STATUS_INVALID,
                    "当前状态「" + from.getName() + "」不允许执行「" + action.getName() + "」");
        }
        // 返回解析出的目标状态
        return to;
    }

    /**
     * 为本次动作补充时间戳与动作特有字段。
     *
     * <p>约定：进入某阶段时记录对应时间列；取消必须保证原因非空；
     * 超时回退需要清掉上一轮派单/接单信息。暂停、恢复、转单申请与驳回没有独立时间列，
     * 其时间点由 work_order_log 的创建时间体现。</p>
     *
     * @param order  待更新的工单实体
     * @param action 本次事件
     * @param from   变更前状态
     * @param now    本次流转时间
     */
    private void fillActionFields(WorkOrder order, OrderActionEnum action, OrderStatusEnum from, LocalDateTime now) {
        // 按动作分支补充对应的字段
        switch (action) {
            // 派单/改派：记录派单时间
            case ASSIGN -> order.setAssignedTime(now);
            // 超时未接单：回退到待接单并清空派单/接单信息
            case ASSIGN_TIMEOUT -> {
                // 清空派单目标师傅
                order.setAssignedWorkerId(null);
                // 清空派单时间
                order.setAssignedTime(null);
                // 清空受理师傅
                order.setAcceptedWorkerId(null);
                // 清空接单时间
                order.setAcceptedTime(null);
            }
            // 抢单或接受派单：记录接单时间
            case GRAB, ACCEPT -> order.setAcceptedTime(now);
            // 到场签到：记录到场时间
            case ARRIVE -> order.setArrivedTime(now);
            // 提交完工：记录完工提交时间
            case SUBMIT -> order.setSubmittedTime(now);
            // 业主确认或超时自动确认：记录完工时间
            case CONFIRM, AUTO_CONFIRM -> order.setCompletedTime(now);
            // 评价关单：记录关单时间
            case EVALUATE -> order.setClosedTime(now);
            // 取消工单：必须保证取消原因非空
            case CANCEL -> {
                // 未填写原因时补默认文案
                if (order.getCancelReason() == null) {
                    // 写入默认取消原因
                    order.setCancelReason("未填写");
                }
            }
            // 暂停/恢复/转单申请/驳回无独立时间列，时间点由流转日志体现
            default -> {
                // PAUSE / RESUME / TRANSFER_APPLY / TRANSFER_REJECT 无独立时间列，备注写入流转日志
            }
        }
    }

    /**
     * 需要显式清空的字段：MyBatis-Plus 的 updateById 会忽略 null，
     * 因此改派、转单、超时回退时用条件更新显式置空受理师傅，保证状态与受理人一致。
     */
    private void clearFieldsIfNeeded(WorkOrder order, OrderActionEnum action, OrderStatusEnum from) {
        // 超时回退，或非首次派单（改派/转单）时，必须清空原受理人
        boolean clearAccepted = action == OrderActionEnum.ASSIGN_TIMEOUT
                || (action == OrderActionEnum.ASSIGN && from != OrderStatusEnum.PENDING);
        // 无需清空的动作直接返回
        if (!clearAccepted) {
            return;
        }
        // 同一事务内紧接在带 version 的更新之后执行，版本号已由乐观锁插件推进
        workOrderMapper.update(null, Wrappers.<WorkOrder>lambdaUpdate()
                .eq(WorkOrder::getId, order.getId())
                .set(WorkOrder::getAcceptedWorkerId, null)
                .set(WorkOrder::getAcceptedTime, null));
        // 同步清空内存实体的受理师傅
        order.setAcceptedWorkerId(null);
        // 同步清空内存实体的接单时间
        order.setAcceptedTime(null);
    }

    /**
     * 写入一条工单流转日志。
     *
     * <p>日志与状态更新在同一事务中，保证工单轨迹完整可追溯。</p>
     */
    private void recordLog(Long orderId, Long operatorId, OperatorTypeEnum operatorType, OrderActionEnum action,
                           OrderStatusEnum from, OrderStatusEnum to, String remark) {
        // 交由日志服务写入一条流转日志
        workOrderLogService.record(orderId, operatorId, operatorType, action, from, to, remark);
    }

    /**
     * 师傅负载与接单计数结算。
     *
     * <p>负载不做简单加减，而是按当前库内「已受理且未完结」的工单数重算，
     * 避免并发或重复事件导致计数漂移。先收集所有可能受影响的师傅：
     * 变更前后的受理人，以及派单/超时回退场景涉及的目标师傅，去重后逐个重算。</p>
     *
     * @param order                     当前工单
     * @param action                    本次事件
     * @param to                        目标状态
     * @param previousAcceptedWorkerId  变更前受理师傅 ID，可为 null
     */
    private void settleWorkers(WorkOrder order, OrderActionEnum action, OrderStatusEnum to,
                               Long previousAcceptedWorkerId) {
        // 去重收集需要重算负载的师傅：原受理人、新受理人、派单目标
        Set<Long> affected = new LinkedHashSet<>();
        // 纳入变更前受理人
        affected.add(previousAcceptedWorkerId);
        // 纳入变更后受理人
        affected.add(order.getAcceptedWorkerId());
        // 派单类动作还需纳入派单目标师傅
        if (action == OrderActionEnum.ASSIGN || action == OrderActionEnum.ASSIGN_TIMEOUT) {
            // 纳入派单目标师傅
            affected.add(order.getAssignedWorkerId());
        }
        // 抢单或接受派单：累计接单总数
        if ((action == OrderActionEnum.GRAB || action == OrderActionEnum.ACCEPT)
                && order.getAcceptedWorkerId() != null) {
            // 累计该师傅接单总数
            increaseCounter(order.getAcceptedWorkerId(), CounterType.TOTAL);
        }
        // 业主确认或超时自动确认：累计完工总数
        if ((action == OrderActionEnum.CONFIRM || action == OrderActionEnum.AUTO_CONFIRM)
                && order.getAcceptedWorkerId() != null) {
            // 累计该师傅完工总数
            increaseCounter(order.getAcceptedWorkerId(), CounterType.FINISH);
        }
        // 逐个按库内数据重算负载
        for (Long workerId : affected) {
            // 重算该师傅负载
            recountLoad(workerId);
        }
        // 取消工单时再次确保原受理人的负载被释放
        if (to == OrderStatusEnum.CANCELED && previousAcceptedWorkerId != null) {
            // 取消时释放原受理人负载
            recountLoad(previousAcceptedWorkerId);
        }
    }

    /**
     * 原子自增师傅的接单/完工计数。
     *
     * <p>使用 SQL 表达式 {@code column = column + 1} 在数据库侧完成自增，
     * 避免「先查后改」的并发丢失更新。</p>
     *
     * @param workerId 师傅 ID
     * @param type     计数类型：接单总数或完工总数
     */
    private void increaseCounter(Long workerId, CounterType type) {
        // 按计数类型选择目标列名
        String column = type == CounterType.TOTAL ? "total_orders" : "finish_orders";
        // 用 SQL 表达式在库侧自增，避免先查后改的丢失更新
        maintenanceWorkerMapper.update(null, Wrappers.<MaintenanceWorker>lambdaUpdate()
                .eq(MaintenanceWorker::getId, workerId)
                .setSql(column + " = " + column + " + 1"));
    }

    /**
     * 按当前库内数据重算负载，避免自增自减漂移。
     *
     * <p>统计口径：受理人为该师傅且状态属于 {@link #LOAD_STATUSES} 的工单数。</p>
     *
     * @param workerId 师傅 ID，为 null 时直接跳过
     */
    private void recountLoad(Long workerId) {
        // 无师傅 ID 时直接跳过
        if (workerId == null) {
            return;
        }
        // 统计该师傅名下「已受理且未完结」的工单数
        Long count = workOrderMapper.selectCount(Wrappers.<WorkOrder>lambdaQuery()
                .eq(WorkOrder::getAcceptedWorkerId, workerId)
                .in(WorkOrder::getStatus, LOAD_STATUSES));
        // 把重算结果写回 current_load
        maintenanceWorkerMapper.update(null, Wrappers.<MaintenanceWorker>lambdaUpdate()
                .eq(MaintenanceWorker::getId, workerId)
                .set(MaintenanceWorker::getCurrentLoad, count.intValue()));
    }

    /**
     * 组装并投递工单领域事件。
     *
     * <p>事件包含唯一 eventId（供消费端幂等）、事件 Tag、工单 ID、前后状态、操作人、
     * 时间戳以及通知/缓存更新所需的业务快照字段。投递在业务事务内完成，
     * 失败时由事务回滚，避免状态与事件不一致。</p>
     */
    private void publishEvent(WorkOrder order, OrderActionEnum action, Long operatorId, OrderStatusEnum to,
                              OrderStatusEnum from, OperatorTypeEnum operatorType, String remark) {
        // 组装事件业务快照，供下游通知与缓存更新使用
        Map<String, Object> data = new HashMap<>(8);
        // 工单号
        data.put("orderNo", order.getOrderNo());
        // 工单标题
        data.put("title", order.getTitle());
        // 业主 ID
        data.put("ownerId", order.getOwnerId());
        // 紧急程度
        data.put("urgency", order.getUrgency());
        // 派单目标师傅 ID
        data.put("assignedWorkerId", order.getAssignedWorkerId());
        // 受理师傅 ID
        data.put("acceptedWorkerId", order.getAcceptedWorkerId());
        // 备注
        data.put("remark", remark);
        // 组装事件体，eventId 唯一供消费端幂等
        WorkOrderEvent event = new WorkOrderEvent(UUID.randomUUID().toString(), action.getEventTag(),
                order.getId(), from == null ? null : from.getCode(), to.getCode(),
                operatorId, operatorType.getCode(), System.currentTimeMillis(), data);
        // 在业务事务内投递事件，失败随事务回滚
        orderEventProducer.publish(event);
    }

    /**
     * 构建合法流转表（当前状态 -&gt; 事件 -&gt; 目标状态）。
     *
     * <p>流转规则按生命周期分组，集中声明便于对照需求与答辩讲解：</p>
     * <pre>
     * PENDING          派单/抢单/取消
     * ASSIGNED         改派/超时回退/接单/取消
     * ACCEPTED         改派/到场/取消
     * GOING            暂停/申请转单/提交完工/取消
     * PAUSED           恢复
     * TRANSFER_PENDING 重新派单（同意转单）/驳回转单
     * SUBMITTED        业主确认/超时自动确认
     * COMPLETED        评价关单
     * </pre>
     *
     * <p>返回不可变 Map，防止运行期被意外修改；未列出的组合一律视为非法流转。</p>
     *
     * @return 不可变的流转表
     */
    private static Map<OrderStatusEnum, Map<OrderActionEnum, OrderStatusEnum>> buildTransitions() {
        // 用 EnumMap 存储流转表，保证键有序
        Map<OrderStatusEnum, Map<OrderActionEnum, OrderStatusEnum>> table = new EnumMap<>(OrderStatusEnum.class);
        // 待接单：物业派单 / 师傅抢单 / 业主取消
        put(table, OrderStatusEnum.PENDING, OrderActionEnum.ASSIGN, OrderStatusEnum.ASSIGNED);
        // 师傅抢单直接进入已接单
        put(table, OrderStatusEnum.PENDING, OrderActionEnum.GRAB, OrderStatusEnum.ACCEPTED);
        // 业主取消
        put(table, OrderStatusEnum.PENDING, OrderActionEnum.CANCEL, OrderStatusEnum.CANCELED);

        // 已派单：改派 / 超时未接单回退 / 师傅接受 / 取消
        put(table, OrderStatusEnum.ASSIGNED, OrderActionEnum.ASSIGN, OrderStatusEnum.ASSIGNED);
        // 超时未接单回退到待接单
        put(table, OrderStatusEnum.ASSIGNED, OrderActionEnum.ASSIGN_TIMEOUT, OrderStatusEnum.PENDING);
        // 师傅接受派单
        put(table, OrderStatusEnum.ASSIGNED, OrderActionEnum.ACCEPT, OrderStatusEnum.ACCEPTED);
        // 取消工单
        put(table, OrderStatusEnum.ASSIGNED, OrderActionEnum.CANCEL, OrderStatusEnum.CANCELED);

        // 已接单：改派 / 到场签到 / 取消
        put(table, OrderStatusEnum.ACCEPTED, OrderActionEnum.ASSIGN, OrderStatusEnum.ASSIGNED);
        // 到场签到进入处理中
        put(table, OrderStatusEnum.ACCEPTED, OrderActionEnum.ARRIVE, OrderStatusEnum.GOING);
        // 取消工单
        put(table, OrderStatusEnum.ACCEPTED, OrderActionEnum.CANCEL, OrderStatusEnum.CANCELED);

        // 处理中：暂停 / 申请转单 / 提交完工 / 取消
        put(table, OrderStatusEnum.GOING, OrderActionEnum.PAUSE, OrderStatusEnum.PAUSED);
        // 申请转单进入转单申请中
        put(table, OrderStatusEnum.GOING, OrderActionEnum.TRANSFER_APPLY, OrderStatusEnum.TRANSFER_PENDING);
        // 提交完工等待业主确认
        put(table, OrderStatusEnum.GOING, OrderActionEnum.SUBMIT, OrderStatusEnum.SUBMITTED);
        // 取消工单
        put(table, OrderStatusEnum.GOING, OrderActionEnum.CANCEL, OrderStatusEnum.CANCELED);

        // 已暂停：恢复处理
        put(table, OrderStatusEnum.PAUSED, OrderActionEnum.RESUME, OrderStatusEnum.GOING);

        // 转单申请中：同意转单后重新派单 / 驳回转单回到处理中
        put(table, OrderStatusEnum.TRANSFER_PENDING, OrderActionEnum.ASSIGN, OrderStatusEnum.ASSIGNED);
        // 驳回转单恢复到处理中
        put(table, OrderStatusEnum.TRANSFER_PENDING, OrderActionEnum.TRANSFER_REJECT, OrderStatusEnum.GOING);

        // 已提交完工：业主确认或超时自动确认
        put(table, OrderStatusEnum.SUBMITTED, OrderActionEnum.CONFIRM, OrderStatusEnum.COMPLETED);
        // 超时自动确认完工
        put(table, OrderStatusEnum.SUBMITTED, OrderActionEnum.AUTO_CONFIRM, OrderStatusEnum.COMPLETED);

        // 已完工：评价后关单
        put(table, OrderStatusEnum.COMPLETED, OrderActionEnum.EVALUATE, OrderStatusEnum.CLOSED);
        // 返回不可变流转表，防止运行期被意外修改
        return Collections.unmodifiableMap(table);
    }

    /** 向流转表添加一条规则，内层 Map 不存在时按需创建 */
    private static void put(Map<OrderStatusEnum, Map<OrderActionEnum, OrderStatusEnum>> table,
                            OrderStatusEnum from, OrderActionEnum action, OrderStatusEnum to) {
        // 内层 Map 不存在时按需创建，再写入一条流转规则
        table.computeIfAbsent(from, key -> new EnumMap<>(OrderActionEnum.class)).put(action, to);
    }

    /** 师傅计数类型 */
    private enum CounterType {
        /** 接单总数 */
        TOTAL,
        /** 完工总数 */
        FINISH
    }
}
