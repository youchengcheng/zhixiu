package com.ycc.zhixiucloud.mq.consumer;

import com.rabbitmq.client.Channel;
import com.ycc.zhixiucloud.common.constant.MqConstants;
import com.ycc.zhixiucloud.dto.NotifyMessage;
import com.ycc.zhixiucloud.mq.MqMessageCodec;
import com.ycc.zhixiucloud.mq.RabbitConsumerHandler;
import com.ycc.zhixiucloud.service.NotificationInboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 站内信消费者：监听 notify-site-consumer-group。
 * NotificationInboxService 调用现有服务幂等落库，notification.event_id 唯一索引保证重复投递幂等。
 * 同一次投递共处理三次，耗尽后进入独立死信队列。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotifySiteConsumer {

    private final MqMessageCodec mqMessageCodec;

    private final RabbitConsumerHandler rabbitConsumerHandler;

    private final NotificationInboxService notificationInboxService;

    @RabbitListener(queues = MqConstants.GROUP_NOTIFY_SITE)
    public void consume(Message message, Channel channel) {
        rabbitConsumerHandler.handle(message, channel, () -> {
            NotifyMessage notification = mqMessageCodec.decode(message.getBody(), NotifyMessage.class);
            notificationInboxService.save(notification);
            log.info("站内通知落库成功：eventId={}, receiverId={}, orderId={}",
                    notification.eventId(), notification.receiverId(), notification.orderId());
        });
    }
}
