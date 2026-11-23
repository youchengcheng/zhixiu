package com.ycc.zhixiucloud.mq.consumer;

import com.rabbitmq.client.Channel;
import com.ycc.zhixiucloud.common.constant.MqConstants;
import com.ycc.zhixiucloud.dto.NotifyMessage;
import com.ycc.zhixiucloud.mq.MqMessageCodec;
import com.ycc.zhixiucloud.mq.RabbitConsumerHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * App 推送消费者：监听 notify-push-consumer-group。
 * 当前未接入推送服务，仅打印日志；同一次投递失败共处理三次，耗尽后进入独立死信队列。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotifyPushConsumer {

    private final MqMessageCodec mqMessageCodec;

    private final RabbitConsumerHandler rabbitConsumerHandler;

    @RabbitListener(queues = MqConstants.GROUP_NOTIFY_PUSH)
    public void consume(Message message, Channel channel) {
        rabbitConsumerHandler.handle(message, channel, () ->
                handle(mqMessageCodec.decode(message.getBody(), NotifyMessage.class)));
    }

    /** 推送服务尚未接入，保留原有日志占位。 */
    private void handle(NotifyMessage message) {
        log.info("推送待发送：receiverId={}, receiverType={}, orderId={}, title={}",
                message.receiverId(), message.receiverType(), message.orderId(), message.title());
    }
}
