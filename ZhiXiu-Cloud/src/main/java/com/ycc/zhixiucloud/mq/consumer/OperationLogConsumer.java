package com.ycc.zhixiucloud.mq.consumer;

import com.rabbitmq.client.Channel;
import com.ycc.zhixiucloud.common.constant.MqConstants;
import com.ycc.zhixiucloud.dto.OperationLogMessage;
import com.ycc.zhixiucloud.mq.MqMessageCodec;
import com.ycc.zhixiucloud.mq.RabbitConsumerHandler;
import com.ycc.zhixiucloud.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 操作日志消费者：监听 operation-log-consumer-group，经服务事务代理写入 operation_log。
 * RabbitMQ 至少一次投递可能产生重复流水，保留现有允许重复审计记录的处理方式。
 */
@Component
@RequiredArgsConstructor
public class OperationLogConsumer {

    private final MqMessageCodec mqMessageCodec;

    private final RabbitConsumerHandler rabbitConsumerHandler;

    private final OperationLogService operationLogService;

    @RabbitListener(queues = MqConstants.GROUP_OPERATION_LOG)
    public void consume(Message message, Channel channel) {
        rabbitConsumerHandler.handle(message, channel, () ->
                operationLogService.save(mqMessageCodec.decode(message.getBody(), OperationLogMessage.class)));
    }
}
