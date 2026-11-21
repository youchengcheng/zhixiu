package com.ycc.zhixiucloud.mq.producer;

import com.ycc.zhixiucloud.common.constant.MqConstants;
import com.ycc.zhixiucloud.common.exception.BizException;
import com.ycc.zhixiucloud.common.result.ResultCode;
import com.ycc.zhixiucloud.dto.SlaTask;
import com.ycc.zhixiucloud.mq.MqMessageCodec;
import com.ycc.zhixiucloud.mq.RabbitMessagePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * SLA 超时消息生产者：sla-timeout-topic。
 * Routing Key 为 RESPONSE / HANDLE，队列 sla-consumer-group 绑定这两类任务。
 * 使用 RabbitMQ 延迟插件，按数据库原期限分段投递，每段不超过 24 小时。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlaProducer {

    private static final ZoneId SLA_ZONE = ZoneId.of("Asia/Shanghai");

    private final RabbitMessagePublisher rabbitMessagePublisher;

    private final MqMessageCodec mqMessageCodec;

    /**
     * 投递超时任务。
     * 发送器按绝对期限计算正数毫秒延迟；同步等待确认，失败触发原建单事务回滚。
     *
     * @param orderId  工单 ID
     * @param type     RESPONSE / HANDLE
     * @param deadline 超时点
     */
    public void sendTimeoutTask(Long orderId, String type, LocalDateTime deadline) {
        // 同步发送延迟消息，失败抛异常回滚建单事务
        try {
            if (orderId == null || deadline == null) {
                throw new IllegalArgumentException("SLA 工单和期限不能为空");
            }
            SlaTask task = new SlaTask(orderId, type, toEpochMilli(deadline), System.currentTimeMillis());
            String messageId = UUID.randomUUID().toString();
            rabbitMessagePublisher.publishDelayed(MqConstants.TOPIC_SLA_TIMEOUT, type,
                    mqMessageCodec.encode(task), messageId, task.deadline());
            log.info("SLA 超时消息投递成功：orderId={}, type={}, deadline={}, messageId={}",
                    orderId, type, task.deadline(), messageId);
        } catch (Exception e) {
            // 超时任务属于 SLA 保障的一部分：投递失败向上抛，触发建单事务回滚
            log.error("SLA 超时消息投递失败：orderId={}, type={}", orderId, type, e);
            // 向上抛出，触发建单事务回滚
            throw BizException.of(ResultCode.SYSTEM_ERROR, "SLA 超时消息投递失败");
        }
    }

    /** 数据库期限按 Asia/Shanghai 转换为毫秒时间戳。 */
    private long toEpochMilli(LocalDateTime time) {
        return time.atZone(SLA_ZONE).toInstant().toEpochMilli();
    }
}
