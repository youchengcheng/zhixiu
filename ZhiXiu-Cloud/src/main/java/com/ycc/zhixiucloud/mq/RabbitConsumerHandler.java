package com.ycc.zhixiucloud.mq;

import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.RabbitUtils;
import org.springframework.stereotype.Component;

/**
 * 重试包在原业务事务代理之外；业务及对应事务提交后单独 ACK。
 * ACK/Reject 异常不触发本次投递的本地业务重试，但关闭通道后的 Broker 重投仍可能重复已提交业务。
 */
@Slf4j
@Component
public class RabbitConsumerHandler {

    @FunctionalInterface
    public interface CheckedRunnable {
        void run() throws Exception;
    }

    public void handle(Message message, Channel channel, CheckedRunnable business) {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        boolean succeeded = false;
        // 三次限制只针对当前 deliveryTag；连接中断后重新投递会重新计数。
        for (int attempt = 1; attempt <= 3; attempt++) {
            if (!channel.isOpen() || Thread.currentThread().isInterrupted()) {
                // 失效通道或线程中断时不确认消息，交给 Broker 在通道关闭后重新投递。
                closeChannel(channel);
                return;
            }
            try {
                // 调用方传入原业务代理调用，每次重试重新进入其事务，不复用已失败的事务。
                business.run();
                succeeded = true;
                break;
            } catch (Exception e) {
                log.error("RabbitMQ 消费失败：queue={}, messageId={}, attempt={}/3",
                        message.getMessageProperties().getConsumerQueue(),
                        message.getMessageProperties().getMessageId(), attempt, e);
                if (attempt < 3) {
                    try {
                        // 原位等待 1 秒、2 秒；保持 prefetch=1 下当前队列的串行处理顺序。
                        Thread.sleep(attempt * 1000L);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        closeChannel(channel);
                        return;
                    }
                }
            }
        }
        try {
            if (succeeded) {
                channel.basicAck(deliveryTag, false);
            } else {
                // 不 requeue；启动门卫已核验每个业务队列有独立、至少一次的 DLQ policy。
                channel.basicReject(deliveryTag, false);
                log.warn("RabbitMQ 本次投递重试耗尽，转入独立 DLQ：queue={}, messageId={}",
                        message.getMessageProperties().getConsumerQueue(),
                        message.getMessageProperties().getMessageId());
            }
        } catch (Exception e) {
            log.error("RabbitMQ ACK/Reject 通道异常，关闭通道等待 Broker 重新投递", e);
            closeChannel(channel);
        }
    }

    private void closeChannel(Channel channel) {
        try {
            // 缓存通道必须物理关闭，不能仅归还缓存，否则未确认消息不会重新投递。
            RabbitUtils.setPhysicalCloseRequired(channel, true);
            channel.abort();
        } catch (Exception e) {
            log.warn("RabbitMQ 通道关闭失败", e);
        } finally {
            RabbitUtils.clearPhysicalCloseRequired();
        }
    }
}
