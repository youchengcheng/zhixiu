package com.ycc.zhixiucloud.mq;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 每次发布独立等待确认，逻辑 messageId 在 SLA 续投时保持不变。
 * messageId 用于串联同一逻辑任务，Broker 不会据此自动去重；每次发送另建 CorrelationData 关联确认。
 */
@Component
public class RabbitMessagePublisher {

    // 24 小时只限制插件单段等待时间，业务绝对 deadline 保持不变且不新增 SLA 时限上限。
    public static final long MAX_DELAY_MILLIS = 86_400_000L;
    private final RabbitTemplate ordinaryTemplate;
    private final RabbitTemplate delayedTemplate;
    private volatile boolean ready;

    public RabbitMessagePublisher(@Qualifier("ordinaryRabbitTemplate") RabbitTemplate ordinaryTemplate,
                                  @Qualifier("delayedRabbitTemplate") RabbitTemplate delayedTemplate) {
        this.ordinaryTemplate = ordinaryTemplate;
        this.delayedTemplate = delayedTemplate;
    }

    public void markReady() {
        ready = true;
    }

    public void publish(String exchange, String routingKey, byte[] body, String messageId) {
        send(ordinaryTemplate, exchange, routingKey, message(body, messageId), true);
    }

    public void publishDelayed(String exchange, String routingKey, byte[] body, String messageId, long deadline) {
        Message message = message(body, messageId);
        // 不继承插件到期投递的负数 x-delay；每一段从绝对期限重新计算。
        long delay = Math.max(1L, Math.min(deadline - System.currentTimeMillis(), MAX_DELAY_MILLIS));
        message.getMessageProperties().setHeader("x-delay", (int) delay);
        send(delayedTemplate, exchange, routingKey, message, false);
    }

    private Message message(byte[] body, String messageId) {
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("MQ 逻辑 messageId 不能为空");
        }
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setMessageId(messageId);
        return new Message(body, properties);
    }

    private void send(RabbitTemplate template, String exchange, String routingKey, Message message, boolean mandatory) {
        // 声明拓扑并核验实际生效 policy 前拒绝生产，避免启动期间消息进入不完整的拓扑。
        if (!ready) {
            throw new IllegalStateException("RabbitMQ 拓扑及死信 policy 尚未通过启动门卫");
        }
        CorrelationData correlation = new CorrelationData(UUID.randomUUID().toString());
        try {
            template.send(exchange, routingKey, message, correlation);
            // 超时只能认定结果不确定；调用方重试可能重复投递，不能据此认定 Broker 未接收。
            CorrelationData.Confirm confirm = correlation.getFuture().get(3, TimeUnit.SECONDS);
            // 普通消息要求 ACK 且没有 Return；SLA ACK 仅表示插件接收，不保证未来一定成功入队。
            if (!confirm.isAck() || (mandatory && correlation.getReturned() != null)) {
                throw new IllegalStateException("RabbitMQ 发布未确认或消息不可路由");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("RabbitMQ 等待确认被中断", e);
        } catch (Exception e) {
            throw new IllegalStateException("RabbitMQ 发布失败：" + exchange + "/" + routingKey, e);
        }
    }
}
