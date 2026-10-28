package com.ycc.zhixiucloud.mq.consumer;

import com.ycc.zhixiucloud.common.constant.MqConstants;
import com.ycc.zhixiucloud.dto.SlaTask;
import com.ycc.zhixiucloud.mq.MqMessageCodec;
import com.ycc.zhixiucloud.service.NotificationService;
import com.ycc.zhixiucloud.service.SlaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.annotation.RocketMQMessageListener;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * SLA 超时消费者：订阅 sla-timeout-topic 的 RESPONSE || HANDLE 两个 Tag，消费组 sla-consumer-group。
 * 延迟消息到期后判断工单是否已在时限内推进：已推进则忽略，否则标记超时并升级通知管理员。
 *
 * <p>幂等策略：SlaService 在更新前会校验工单是否已推进、是否已标记超时，
 * 重复消费直接返回 false，因此不会重复升级或重复通知管理员。</p>
 *
 * <p>失败处理：未知类型或处理异常返回 FAILURE，由 RocketMQ 重试，重试耗尽后进入死信队列；
 * 消息中的 deadline 用于排查延迟消息是否晚于超时点到达。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = MqConstants.TOPIC_SLA_TIMEOUT,
        consumerGroup = MqConstants.GROUP_SLA_TIMEOUT,
        tag = MqConstants.SlaTimeout.TAG_EXPRESSION,
        sslEnabled = false)
public class SlaTimeoutConsumer implements RocketMQListener {

    private final MqMessageCodec mqMessageCodec;

    private final SlaService slaService;

    private final NotificationService notificationService;

    /**
     * 消费单条 SLA 超时消息。
     * 按类型调用响应/处理超时标记；只有真正发生“由未超时变为超时”时才通知管理员，
     * 否则说明业务已在时限内推进，直接确认消息即可。
     */
    @Override
    public ConsumeResult consume(MessageView messageView) {
        try {
            // 解码延迟消息（工单 ID、超时类型、超时点）
            SlaTask task = mqMessageCodec.decode(messageView.getBody(), SlaTask.class);
            // 打印超时消息关键信息，便于排查到期情况
            log.info("收到 SLA 超时消息：orderId={}, type={}, deadline={}",
                    task.orderId(), task.type(), task.deadline());
            // 按超时类型执行标记；markXxxBreached 内部完成幂等与达标判断
            boolean breached = switch (task.type()) {
                // 响应超时：标记响应未达标
                case MqConstants.SlaTimeout.RESPONSE -> slaService.markResponseBreached(task.orderId());
                // 处理超时：标记处理未达标
                case MqConstants.SlaTimeout.HANDLE -> slaService.markHandleBreached(task.orderId());
                // 未知类型属于脏消息，抛出异常交给重试/死信，避免静默忽略
                default -> throw new IllegalArgumentException("未知的 SLA 超时类型：" + task.type());
            };
            // 仅首次由未超时变为超时时才通知管理员
            if (breached) {
                // 首次判定超时：升级并通知管理员介入
                notificationService.notifyAdmins("工单 SLA 超时",
                        "工单 " + task.orderId() + " " + typeName(task.type()) + "，请立即介入处理。",
                        task.orderId());
            } else {
                // 已推进或已升级过：无需重复通知
                log.info("SLA 未超时，忽略该消息：orderId={}, type={}", task.orderId(), task.type());
            }
            // 正常处理完成，确认消息
            return ConsumeResult.SUCCESS;
        } catch (Exception e) {
            // 处理失败：保留原始报文便于排查，并返回 FAILURE 触发 MQ 重试
            log.error("SLA 超时消息消费失败：messageId={}, payload={}",
                    messageView.getMessageId(), mqMessageCodec.toText(messageView.getBody()), e);
            // 消费失败，交由 MQ 重试
            return ConsumeResult.FAILURE;
        }
    }

    /** 超时类型 -> 中文名，用于通知文案 */
    private String typeName(String type) {
        // 按超时类型返回中文名
        return MqConstants.SlaTimeout.RESPONSE.equals(type) ? "响应超时" : "处理超时";
    }
}