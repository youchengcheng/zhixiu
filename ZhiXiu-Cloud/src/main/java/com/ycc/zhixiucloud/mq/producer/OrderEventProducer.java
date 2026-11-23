package com.ycc.zhixiucloud.mq.producer;

import com.ycc.zhixiucloud.common.constant.MqConstants;
import com.ycc.zhixiucloud.common.exception.BizException;
import com.ycc.zhixiucloud.common.result.ResultCode;
import com.ycc.zhixiucloud.dto.WorkOrderEvent;
import com.ycc.zhixiucloud.mq.MqMessageCodec;
import com.ycc.zhixiucloud.mq.RabbitMessagePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 工单事件生产者：workorder-event-topic。
 * Routing Key 取事件类型（CREATED / ASSIGNED / ACCEPTED 等）。
 * 消费端使用单队列、单活跃消费者、并发 1 和 prefetch 1 串行处理已收到的事件。
 *
 * <p>由 WorkOrderStateMachine 在状态落库的本地事务内调用，投递失败抛出 BizException
 * 使本地事务回滚。数据库与消息队列仍存在非原子窗口：
 * 极端情况下仍可能出现「消息已发送、本地事务却回滚」的不一致，
 * 因此消费端只做通知扇出、不回写工单，避免脏事件扩大影响。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventProducer {

    private final RabbitMessagePublisher rabbitMessagePublisher;

    private final MqMessageCodec mqMessageCodec;

    /**
     * 投递工单状态变更事件。
     * 同步发送并等待 Broker 确认；
     * 发送失败抛 BizException，让调用方本地事务回滚，避免状态落库而事件丢失。
     *
     * @param event 状态机生成的变更事件（含全局唯一 eventId、前后状态与通知附加数据）
     */
    public void publish(WorkOrderEvent event) {
        // 同步发送，失败抛异常让本地事务回滚
        try {
            rabbitMessagePublisher.publish(MqConstants.TOPIC_WORK_ORDER_EVENT, event.eventType(),
                    mqMessageCodec.encode(event), event.eventId());
            // 记录投递成功的消息 ID
            log.info("工单事件投递成功：orderId={}, event={}, messageId={}",
                    event.orderId(), event.eventType(), event.eventId());
        // 发送失败：抛异常触发调用方本地事务回滚
        } catch (Exception e) {
            // 事件是状态流转的一部分：发送失败必须向上抛，触发本地事务回滚
            log.error("工单事件投递失败：orderId={}, event={}", event.orderId(), event.eventType(), e);
            // 投递失败上抛异常，触发调用方本地事务回滚
            throw BizException.of(ResultCode.SYSTEM_ERROR, "工单事件投递失败");
        }
    }
}
