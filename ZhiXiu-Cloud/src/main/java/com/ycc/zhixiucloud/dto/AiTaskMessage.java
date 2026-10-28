package com.ycc.zhixiucloud.dto;

/**
 * AI 异步任务消息体：经 ai-task-topic 投递，由 AiTaskConsumer 消费。
 * 知识向量化与工单总结共用同一结构，用 taskType 区分。
 *
 * <p>投递侧使用同步普通消息；消费组为 ai-task-consumer-group，订阅全部 Tag。
 * 消息体经 MqMessageCodec 编码为 UTF-8 JSON。两个业务字段互斥：
 * taskType=VECTORIZE 时只填 knowledgeId，taskType=SUMMARIZE 时只填 orderId，
 * 消费端按 Tag 分支读取，避免 AI 任务失败阻塞工单主流程。</p>
 */
public record AiTaskMessage(
        // 任务类型：VECTORIZE 知识向量化 / SUMMARIZE 工单总结
        String taskType,
        // 知识条目 ID，仅 VECTORIZE 使用
        Long knowledgeId,
        // 工单 ID，仅 SUMMARIZE 使用
        Long orderId,
        // 投递时间戳（毫秒）
        Long timestamp
) {
}
