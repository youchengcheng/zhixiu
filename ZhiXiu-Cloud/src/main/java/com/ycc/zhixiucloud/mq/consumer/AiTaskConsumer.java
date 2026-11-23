package com.ycc.zhixiucloud.mq.consumer;

import com.rabbitmq.client.Channel;
import com.ycc.zhixiucloud.common.constant.MqConstants;
import com.ycc.zhixiucloud.dto.AiTaskMessage;
import com.ycc.zhixiucloud.mq.MqMessageCodec;
import com.ycc.zhixiucloud.mq.RabbitConsumerHandler;
import com.ycc.zhixiucloud.service.RepairKnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * AI 任务消费者：监听 ai-task-consumer-group，按收到的 Routing Key 执行业务。
 * 向量化保留删除旧分片后写入的幂等行为；工单总结仍只记录日志。
 * 同一次投递失败共处理三次，耗尽后进入独立死信队列，未知路由键保持告警后确认。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiTaskConsumer {

    private final MqMessageCodec mqMessageCodec;

    private final RabbitConsumerHandler rabbitConsumerHandler;

    private final RepairKnowledgeService repairKnowledgeService;

    @RabbitListener(queues = MqConstants.GROUP_AI_TASK)
    public void consume(Message message, Channel channel) {
        rabbitConsumerHandler.handle(message, channel, () -> {
            String routingKey = message.getMessageProperties().getReceivedRoutingKey();
            AiTaskMessage task = mqMessageCodec.decode(message.getBody(), AiTaskMessage.class);
            if (MqConstants.AiTask.VECTORIZE.equals(routingKey)) {
                repairKnowledgeService.vectorize(task.knowledgeId());
            } else if (MqConstants.AiTask.SUMMARIZE.equals(routingKey)) {
                log.info("收到工单总结任务：orderId={}", task.orderId());
            } else {
                log.warn("未知的 AI 任务 Routing Key：{}", routingKey);
            }
        });
    }
}
