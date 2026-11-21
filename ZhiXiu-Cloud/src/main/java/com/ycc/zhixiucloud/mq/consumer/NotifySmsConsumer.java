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
 * 短信消费者：监听 notify-sms-consumer-group。
 * 当前未接入短信服务商，仅打印日志；同一次投递失败共处理三次，耗尽后进入独立死信队列。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotifySmsConsumer {

    private final MqMessageCodec mqMessageCodec;

    private final RabbitConsumerHandler rabbitConsumerHandler;

    @RabbitListener(queues = MqConstants.GROUP_NOTIFY_SMS)
    public void consume(Message message, Channel channel) {
        rabbitConsumerHandler.handle(message, channel, () ->
                handle(mqMessageCodec.decode(message.getBody(), NotifyMessage.class)));
    }

    /** 短信服务商尚未接入，保留原有日志占位。 */
    private void handle(NotifyMessage message) {
        log.info("短信待发送：receiverId={}, receiverType={}, orderId={}, content={}",
                message.receiverId(), message.receiverType(), message.orderId(), message.content());
    }
}
