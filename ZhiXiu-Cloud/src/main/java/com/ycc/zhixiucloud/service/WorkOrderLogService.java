package com.ycc.zhixiucloud.service;

import com.ycc.zhixiucloud.common.enums.OperatorTypeEnum;
import com.ycc.zhixiucloud.common.enums.OrderActionEnum;
import com.ycc.zhixiucloud.common.enums.OrderStatusEnum;
import com.ycc.zhixiucloud.response.WorkOrderLogResponseVO;

import java.util.List;

/**
 * 工单流转日志服务：状态机每次流转写一条，查询时按时间正序返回时间线。
 *
 * <p>职责边界：只负责日志写入与查询，不修改工单状态；写入动作由状态机在业务事务内调用，
 * 保证“状态变更 + 流转日志 + 事件投递”的原子性。</p>
 *
 * <p>主要使用方：{@link com.ycc.zhixiucloud.statemachine.WorkOrderStateMachine}（record）、
 * 三端工单详情页（listByOrder）。</p>
 *
 * <p>关键实现要点：查询时批量补充操作人姓名，并把状态/动作/操作人类型编码
 * 转换为中文文案。</p>
 */
public interface WorkOrderLogService {

    /**
     * 记录一次工单流转。
     *
     * <p>由状态机在业务事务内调用，因此日志与状态变更同生共死；
     * 日志中的 action 使用 {@link OrderActionEnum#getLogCode()} 对应的固定取值。</p>
     *
     * @param orderId      工单 ID
     * @param operatorId   操作人 ID，系统触发时可为 null
     * @param operatorType 操作人类型（业主/师傅/管理员/系统）
     * @param action       本次流转事件
     * @param from         流转前状态，创建时为 null
     * @param to           流转后状态
     * @param remark       备注，写入日志供时间线展示
     */
    void record(Long orderId, Long operatorId, OperatorTypeEnum operatorType, OrderActionEnum action,
                OrderStatusEnum from, OrderStatusEnum to, String remark);

    /**
     * 查询某工单的流转记录。
     *
     * <p>按发生时间、ID 正序返回，形成可读的时间线；每条记录补充操作人姓名、
     * 状态中文名与动作中文名。</p>
     *
     * @param orderId 工单 ID
     * @return 流转记录列表；无记录时返回空列表
     */
    List<WorkOrderLogResponseVO> listByOrder(Long orderId);
}