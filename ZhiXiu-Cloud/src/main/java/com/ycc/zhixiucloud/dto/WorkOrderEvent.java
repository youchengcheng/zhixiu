package com.ycc.zhixiucloud.dto;

import java.util.Map;

/**
 * 工单状态变更事件消息体（开发文档 6.2）。
 * 由 WorkOrderStateMachine 在与状态落库同一事务内投递到 workorder-event-topic。
 *
 * <p>生产端以 orderId 作为 FIFO 消息组键保证同一工单事件有序，Tag 取 eventType；
 * 消费端按事件类型扇出通知，因此 data 中携带了通知文案与接收人解析所需的业务字段。</p>
 *
 * <p>data 常用键：orderNo（工单号）、title（标题）、ownerId（业主）、assignedWorkerId（被派单师傅）、
 * acceptedWorkerId（实际接单师傅）、remark（备注/原因）、urgency（紧急程度）。</p>
 *
 * @param eventId      事件 ID（全局唯一，便于幂等）
 * @param eventType    事件类型，取值见 MqConstants.OrderEvent
 * @param orderId      工单 ID
 * @param fromStatus   原状态
 * @param toStatus     新状态
 * @param operatorId   操作人 ID，系统触发为 null
 * @param operatorType OWNER / WORKER / ADMIN / SYSTEM
 * @param timestamp    事件发生时间（毫秒）
 * @param data         附加数据（工单号、标题、业主/师傅 ID、紧急程度、备注等，供通知与异步处理使用）
 */
public record WorkOrderEvent(String eventId, String eventType, Long orderId, String fromStatus,
                             String toStatus, Long operatorId, String operatorType,
                             Long timestamp, Map<String, Object> data) {
}