package com.ycc.zhixiucloud.mq.consumer;

import com.ycc.zhixiucloud.common.constant.MqConstants;
import com.ycc.zhixiucloud.dto.OperationLogMessage;
import com.ycc.zhixiucloud.mq.MqMessageCodec;
import com.ycc.zhixiucloud.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.annotation.RocketMQMessageListener;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 操作日志消费者：订阅 operation-log-topic 的 ACCESS Tag，消费组 operation-log-consumer-group。
 * 把切面采集到的访问日志落库 operation_log，避免日志写库拖慢正常的业务请求。
 *
 * <p>消费流程：反序列化 → 调用 OperationLogService#save 插入日志表 → 返回 SUCCESS。
 * 幂等说明：RocketMQ 为至少一次投递，日志表没有业务唯一键，失败重试可能产生重复流水，
 * 这里按「可重复的审计记录」容忍（统计与分析不依赖单条唯一性）。
 * 失败处理：异常返回 FAILURE 交由 MQ 重试，重试耗尽后进入死信队列。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = MqConstants.TOPIC_OPERATION_LOG,
        consumerGroup = MqConstants.GROUP_OPERATION_LOG,
        tag = MqConstants.OperationLog.ACCESS,
        sslEnabled = false)
public class OperationLogConsumer implements RocketMQListener {

    private final MqMessageCodec mqMessageCodec;

    private final OperationLogService operationLogService;

    /**
     * 消费单条操作日志：落库成功返回 SUCCESS；落库或解码异常返回 FAILURE 触发重试。
     */
    @Override
    public ConsumeResult consume(MessageView messageView) {
        // 落库成功确认消息，异常统一返回 FAILURE 触发重试
        try {
            // 反序列化消息体为日志对象
            OperationLogMessage message = mqMessageCodec.decode(messageView.getBody(), OperationLogMessage.class);
            // 调用服务层写入 operation_log
            operationLogService.save(message);
            // 消费成功，MQ 不再重投
            return ConsumeResult.SUCCESS;
        } catch (Exception e) {
            // 落库失败记错误日志，附带 messageId 与原始报文
            log.error("操作日志落库失败：messageId={}, payload={}",
                    messageView.getMessageId(), mqMessageCodec.toText(messageView.getBody()), e);
            // 返回失败，交由 MQ 重试
            return ConsumeResult.FAILURE;
        }
    }
}
