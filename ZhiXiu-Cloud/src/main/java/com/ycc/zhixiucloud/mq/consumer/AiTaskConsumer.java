package com.ycc.zhixiucloud.mq.consumer;

import com.ycc.zhixiucloud.common.constant.MqConstants;
import com.ycc.zhixiucloud.dto.AiTaskMessage;
import com.ycc.zhixiucloud.mq.MqMessageCodec;
import com.ycc.zhixiucloud.service.RepairKnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.annotation.RocketMQMessageListener;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * AI 任务消费者：订阅 ai-task-topic 全部 Tag，消费组 ai-task-consumer-group。
 * VECTORIZE 触发知识向量化；SUMMARIZE 触发工单总结。
 *
 * <p>消费流程：读取 Tag → 反序列化 AiTaskMessage → 按 Tag 分支执行 → 返回 SUCCESS。
 * 幂等策略：向量化按分片 ID 清单删除全部旧向量，再写入分片并回写 vectorized 标记，
 * 重复消费结果一致；工单总结当前只记日志，天然可重入。
 * 失败处理：异常返回 FAILURE，由 RocketMQ 按重试策略重投；未知 Tag 直接确认成功，
 * 避免无法识别的消息无意义地重试并占用消费能力。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = MqConstants.TOPIC_AI_TASK,
        consumerGroup = MqConstants.GROUP_AI_TASK,
        // 订阅该 Topic 全部 Tag，按消息 Tag 分支处理
        tag = MqConstants.TAG_ALL,
        sslEnabled = false)
public class AiTaskConsumer implements RocketMQListener {

    private final MqMessageCodec mqMessageCodec;

    private final RepairKnowledgeService repairKnowledgeService;

    /**
     * 消费单条 AI 任务消息。
     * 成功/未知 Tag 返回 SUCCESS；任何异常返回 FAILURE，由 MQ 重投，重试耗尽后进入死信。
     */
    @Override
    public ConsumeResult consume(MessageView messageView) {
        try {
            // 读取消息 Tag 用于区分任务类型
            String tag = messageView.getTag().orElse(null);
            // 反序列化任务消息
            AiTaskMessage task = mqMessageCodec.decode(messageView.getBody(), AiTaskMessage.class);
            // 向量化任务：写入向量库并回写 vectorized
            if (MqConstants.AiTask.VECTORIZE.equals(tag)) {
                // 知识向量化：写入向量库并回写 vectorized
                repairKnowledgeService.vectorize(task.knowledgeId());
            // 工单总结任务：当前仅记录
            } else if (MqConstants.AiTask.SUMMARIZE.equals(tag)) {
                // 工单总结：当前 work_order 无对应存储列，仅记录任务以便后续扩展
                log.info("收到工单总结任务：orderId={}", task.orderId());
            } else {
                // 未知 Tag 记警告，按成功处理避免无意义重试
                log.warn("未知的 AI 任务 Tag：{}", tag);
            }
            // 处理成功，MQ 不再重投
            return ConsumeResult.SUCCESS;
        } catch (Exception e) {
            // 处理失败记错误日志并返回失败，交由 MQ 重试
            log.error("AI 任务处理失败：messageId={}, payload={}",
                    messageView.getMessageId(), mqMessageCodec.toText(messageView.getBody()), e);
            // 消费失败，交由 MQ 重试
            return ConsumeResult.FAILURE;
        }
    }
}
