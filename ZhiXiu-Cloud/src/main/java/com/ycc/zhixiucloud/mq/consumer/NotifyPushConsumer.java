package com.ycc.zhixiucloud.mq.consumer;

import com.ycc.zhixiucloud.common.constant.MqConstants;
import com.ycc.zhixiucloud.dto.NotifyMessage;
import com.ycc.zhixiucloud.mq.MqMessageCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.annotation.RocketMQMessageListener;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * App 推送消费者：订阅 notification-topic 的 PUSH Tag，消费组 notify-push-consumer-group。
 *
 * <p>通知消息由 NotifyProducer 按通道扇出，本消费者只处理 App 推送通道。
 * 当前为占位实现（未接入推送服务），仅打印日志、无外部副作用，
 * 因此重复消费不会造成重复推送；eventId 预留给后续推送回执对账/去重。
 * 消费失败返回 FAILURE，由 RocketMQ 重试；通知属于旁路能力，重试耗尽后进入死信队列，
 * 不影响工单主流程。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = MqConstants.TOPIC_NOTIFICATION,
        consumerGroup = MqConstants.GROUP_NOTIFY_PUSH,
        tag = MqConstants.Notify.PUSH,
        sslEnabled = false)
public class NotifyPushConsumer implements RocketMQListener {

    private final MqMessageCodec mqMessageCodec;

    /**
     * 消费单条推送消息：反序列化 → 交给 handle 处理 → 确认成功；
     * 任何异常记录 messageId 与原始报文后返回 FAILURE 触发重试。
     */
    @Override
    public ConsumeResult consume(MessageView messageView) {
        // 解码与处理异常统一转为 FAILURE 交由 MQ 重试
        try {
            // 按 NotifyMessage 结构解码通知载荷
            NotifyMessage message = mqMessageCodec.decode(messageView.getBody(), NotifyMessage.class);
            // 推送发送逻辑（当前为日志占位）
            handle(message);
            // 处理完成，确认消费
            return ConsumeResult.SUCCESS;
        } catch (Exception e) {
            // 解码或处理失败：打印原始报文便于排查，并让 MQ 重投
            log.error("推送消费失败：messageId={}, payload={}",
                    messageView.getMessageId(), mqMessageCodec.toText(messageView.getBody()), e);
            // 返回失败交由 MQ 重投
            return ConsumeResult.FAILURE;
        }
    }

    /**
     * App 推送：未接入推送服务，先记录待推送内容，接入后在此处调用推送 SDK。
     */
    private void handle(NotifyMessage message) {
        // 当前仅记录待推送内容（占位实现）
        log.info("推送待发送：receiverId={}, receiverType={}, orderId={}, title={}",
                message.receiverId(), message.receiverType(), message.orderId(), message.title());
    }
}
