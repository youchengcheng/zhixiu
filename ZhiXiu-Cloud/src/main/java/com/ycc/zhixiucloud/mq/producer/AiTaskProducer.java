package com.ycc.zhixiucloud.mq.producer;

import com.ycc.zhixiucloud.common.constant.MqConstants;
import com.ycc.zhixiucloud.dto.AiTaskMessage;
import com.ycc.zhixiucloud.mq.MqMessageCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.core.RocketMQClientTemplate;
import org.springframework.stereotype.Component;

/**
 * AI 任务生产者：向 ai-task-topic 投递知识向量化 / 工单总结任务。
 * Tag 分别为 VECTORIZE / SUMMARIZE，对应消费组 ai-task-consumer-group。
 *
 * <p>发送方式：同步普通消息，发送线程会等待 Broker 确认，但耗时通常很短；
 * AI 任务属于异步旁路能力，投递失败只记警告日志，不影响主业务——
 * 知识条目保持未向量化状态，可再次触发；工单总结缺失也不影响工单流转。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiTaskProducer {

    private final RocketMQClientTemplate rocketMQClientTemplate;

    private final MqMessageCodec mqMessageCodec;

    /**
     * 投递知识向量化任务：知识新增/修改后调用，由消费端异步写入向量库。
     *
     * @param knowledgeId 知识条目 ID
     */
    public void sendVectorize(Long knowledgeId) {
        // 拼接 destination：ai-task-topic:VECTORIZE
        String destination = MqConstants.destination(MqConstants.TOPIC_AI_TASK, MqConstants.AiTask.VECTORIZE);
        // 组装任务消息，只带知识条目 ID
        AiTaskMessage message = new AiTaskMessage(MqConstants.AiTask.VECTORIZE, knowledgeId, null,
                System.currentTimeMillis());
        // 同步投递，失败只告警不阻断主流程
        try {
            // 编码消息体后同步投递
            rocketMQClientTemplate.syncSendNormalMessage(destination, mqMessageCodec.encode(message));
            // 记录投递成功
            log.info("投递知识向量化任务：knowledgeId={}", knowledgeId);
        } catch (Exception e) {
            // 异步任务投递失败不阻断主流程，留待人工重新向量化
            log.warn("知识向量化任务投递失败：knowledgeId={}", knowledgeId, e);
        }
    }

    /**
     * 投递工单总结任务：预留给工单完工/总结场景调用。
     * 当前业务侧尚未接入调用点，消费端也只记录任务日志，后续可扩展为生成工单总结并落库。
     *
     * @param orderId 工单 ID
     */
    public void sendSummarize(Long orderId) {
        // 拼接 destination：ai-task-topic:SUMMARIZE
        String destination = MqConstants.destination(MqConstants.TOPIC_AI_TASK, MqConstants.AiTask.SUMMARIZE);
        // 组装任务消息，只带工单 ID
        AiTaskMessage message = new AiTaskMessage(MqConstants.AiTask.SUMMARIZE, null, orderId,
                System.currentTimeMillis());
        // 同步投递，失败只告警不阻断主流程
        try {
            // 编码消息体后同步投递
            rocketMQClientTemplate.syncSendNormalMessage(destination, mqMessageCodec.encode(message));
            // 记录投递成功
            log.info("投递工单总结任务：orderId={}", orderId);
        } catch (Exception e) {
            // 异步任务投递失败不阻断主流程
            log.warn("工单总结任务投递失败：orderId={}", orderId, e);
        }
    }
}
