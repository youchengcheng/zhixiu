package com.ycc.zhixiucloud.mq.producer;

import com.ycc.zhixiucloud.common.constant.MqConstants;
import com.ycc.zhixiucloud.common.exception.BizException;
import com.ycc.zhixiucloud.common.result.ResultCode;
import com.ycc.zhixiucloud.dto.WorkOrderEvent;
import com.ycc.zhixiucloud.mq.MqMessageCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.apis.producer.SendReceipt;
import org.apache.rocketmq.client.core.RocketMQClientTemplate;
import org.springframework.stereotype.Component;

/**
 * 工单事件生产者：workorder-event-topic。
 * 按 orderId 作为消息组发送顺序消息，保证同一工单的事件按发生顺序消费，
 * Tag 取事件类型（CREATED / ASSIGNED / ACCEPTED 等）。
 *
 * <p>由 WorkOrderStateMachine 在状态落库的本地事务内调用，投递失败抛出 BizException
 * 使本地事务回滚。这里使用的是事务内同步发送，并非 RocketMQ 事务消息：
 * 极端情况下仍可能出现「消息已发送、本地事务却回滚」的不一致，
 * 因此消费端只做通知扇出、不回写工单，避免脏事件扩大影响。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventProducer {

    private final RocketMQClientTemplate rocketMQClientTemplate;

    private final MqMessageCodec mqMessageCodec;

    /**
     * 投递工单状态变更事件。
     * 同步发送 FIFO 消息：同一 orderId 使用同一消息组键，落到同一队列以保证局部顺序；
     * 发送失败抛 BizException，让调用方本地事务回滚，避免状态落库而事件丢失。
     *
     * @param event 状态机生成的变更事件（含全局唯一 eventId、前后状态与通知附加数据）
     */
    public void publish(WorkOrderEvent event) {
        // destination 形如 workorder-event-topic:CREATED
        String destination = MqConstants.destination(MqConstants.TOPIC_WORK_ORDER_EVENT, event.eventType());
        // 同步发送 FIFO 消息，失败抛异常让本地事务回滚
        try {
            // 第三个参数为消息组键：按工单 ID 分区，保证同一工单事件有序
            SendReceipt receipt = rocketMQClientTemplate.syncSendFifoMessage(destination,
                    mqMessageCodec.encode(event), String.valueOf(event.orderId()));
            // 记录投递成功的消息 ID
            log.info("工单事件投递成功：orderId={}, event={}, messageId={}",
                    event.orderId(), event.eventType(), receipt.getMessageId());
        // 发送失败：抛异常触发调用方本地事务回滚
        } catch (Exception e) {
            // 事件是状态流转的一部分：发送失败必须向上抛，触发本地事务回滚
            log.error("工单事件投递失败：orderId={}, event={}", event.orderId(), event.eventType(), e);
            // 投递失败上抛异常，触发调用方本地事务回滚
            throw BizException.of(ResultCode.SYSTEM_ERROR, "工单事件投递失败");
        }
    }
}