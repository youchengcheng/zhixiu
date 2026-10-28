package com.ycc.zhixiucloud.mq.producer;

import com.ycc.zhixiucloud.common.constant.MqConstants;
import com.ycc.zhixiucloud.common.exception.BizException;
import com.ycc.zhixiucloud.common.result.ResultCode;
import com.ycc.zhixiucloud.dto.SlaTask;
import com.ycc.zhixiucloud.mq.MqMessageCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.apis.producer.SendReceipt;
import org.apache.rocketmq.client.core.RocketMQClientTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * SLA 超时消息生产者：sla-timeout-topic。
 * Tag 为 RESPONSE / HANDLE，消费组 sla-consumer-group 订阅「RESPONSE || HANDLE」。
 * 使用 RocketMQ 5.x 的任意秒延迟消息（syncSendDelayMessage），不用定时轮询兜底；
 * 延迟时长由 SLA 规则算出的超时点决定，最小 1 秒，超时点已过则立即投递。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlaProducer {

    /** 已过期时的兜底延迟，避免 0 或负延迟 */
    private static final long MIN_DELAY_SECONDS = 1L;

    private final RocketMQClientTemplate rocketMQClientTemplate;

    private final MqMessageCodec mqMessageCodec;

    /**
     * 投递超时任务。
     * 按「超时点 - 当前时间」计算延迟秒数，不足 1 秒按 1 秒处理，避免 0/负延迟被 Broker 拒绝；
     * 同步发送，失败抛 BizException 触发建单事务回滚，保证工单与超时任务一致。
     *
     * @param orderId  工单 ID
     * @param type     RESPONSE / HANDLE
     * @param deadline 超时点
     */
    public void sendTimeoutTask(Long orderId, String type, LocalDateTime deadline) {
        // 延迟秒数：超时点已过或不足 1 秒时按最小 1 秒投递，等价于尽快消费
        long delaySeconds = Math.max(MIN_DELAY_SECONDS, Duration.between(LocalDateTime.now(), deadline).toSeconds());
        // 消息中同时保留 deadline（毫秒），便于消费端与日志核对实际超时点
        SlaTask task = new SlaTask(orderId, type, toEpochMilli(deadline), System.currentTimeMillis());
        // destination 形如 sla-timeout-topic:RESPONSE
        String destination = MqConstants.destination(MqConstants.TOPIC_SLA_TIMEOUT, type);
        // 同步发送延迟消息，失败抛异常回滚建单事务
        try {
            // 任意秒延迟消息：到期后由 Broker 投递给 SLA 消费组
            SendReceipt receipt = rocketMQClientTemplate.syncSendDelayMessage(destination,
                    mqMessageCodec.encode(task), Duration.ofSeconds(delaySeconds));
            // 记录投递成功与延迟时长
            log.info("SLA 超时消息投递成功：orderId={}, type={}, delay={}s, messageId={}",
                    orderId, type, delaySeconds, receipt.getMessageId());
        } catch (Exception e) {
            // 超时任务属于 SLA 保障的一部分：投递失败向上抛，触发建单事务回滚
            log.error("SLA 超时消息投递失败：orderId={}, type={}", orderId, type, e);
            // 向上抛出，触发建单事务回滚
            throw BizException.of(ResultCode.SYSTEM_ERROR, "SLA 超时消息投递失败");
        }
    }

    /** LocalDateTime -> 毫秒时间戳；使用系统默认时区，与 LocalDateTime.now() 的时区保持一致 */
    private long toEpochMilli(LocalDateTime time) {
        // 按系统默认时区换算为毫秒时间戳
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}