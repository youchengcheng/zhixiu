package com.ycc.zhixiucloud.mq.consumer;

import com.rabbitmq.client.Channel;
import com.ycc.zhixiucloud.common.constant.MqConstants;
import com.ycc.zhixiucloud.common.enums.OrderStatusEnum;
import com.ycc.zhixiucloud.dto.SlaTask;
import com.ycc.zhixiucloud.entity.WorkOrder;
import com.ycc.zhixiucloud.mapper.WorkOrderMapper;
import com.ycc.zhixiucloud.mq.MqMessageCodec;
import com.ycc.zhixiucloud.mq.RabbitConsumerHandler;
import com.ycc.zhixiucloud.mq.RabbitMessagePublisher;
import com.ycc.zhixiucloud.service.NotificationService;
import com.ycc.zhixiucloud.service.SlaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;

/**
 * SLA 超时消费者：先核对数据库原期限和对应里程碑，再续投或标记超时。
 *
 * <p>每段延迟最多 24 小时，尚未到期时保留载荷和逻辑 messageId 续投；
 * 续投确认成功后才确认当前消息。失败由统一处理器执行三次处理并转入独立 DLQ。</p>
 *
 * <p>超时标记仍经过 SlaService 的事务代理；仅首次标记成功后通知管理员。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlaTimeoutConsumer {

    private static final ZoneId SLA_ZONE = ZoneId.of("Asia/Shanghai");

    private static final Set<OrderStatusEnum> RESPONSE_PENDING_STATUSES = Set.of(
            OrderStatusEnum.PENDING, OrderStatusEnum.ASSIGNED);

    private static final Set<OrderStatusEnum> HANDLE_PENDING_STATUSES = Set.of(
            OrderStatusEnum.PENDING, OrderStatusEnum.ASSIGNED, OrderStatusEnum.ACCEPTED,
            OrderStatusEnum.GOING, OrderStatusEnum.PAUSED, OrderStatusEnum.TRANSFER_PENDING);

    private final MqMessageCodec mqMessageCodec;

    private final RabbitConsumerHandler rabbitConsumerHandler;

    private final RabbitMessagePublisher rabbitMessagePublisher;

    private final WorkOrderMapper workOrderMapper;

    private final SlaService slaService;

    private final NotificationService notificationService;

    @RabbitListener(queues = MqConstants.GROUP_SLA_TIMEOUT)
    public void consume(Message message, Channel channel) {
        rabbitConsumerHandler.handle(message, channel, () -> process(message));
    }

    private void process(Message message) {
        SlaTask task = mqMessageCodec.decode(message.getBody(), SlaTask.class);
        if (task == null || task.orderId() == null || task.deadline() == null) {
            throw new IllegalArgumentException("SLA 消息缺少工单 ID 或期限");
        }
        boolean response = MqConstants.SlaTimeout.RESPONSE.equals(task.type());
        if (!response && !MqConstants.SlaTimeout.HANDLE.equals(task.type())) {
            throw new IllegalArgumentException("未知的 SLA 超时类型：" + task.type());
        }

        WorkOrder order = workOrderMapper.selectById(task.orderId());
        if (order == null || Integer.valueOf(1).equals(order.getDeleted())) {
            log.info("SLA 工单不存在或已删除，忽略消息：orderId={}, type={}", task.orderId(), task.type());
            return;
        }
        OrderStatusEnum status = OrderStatusEnum.of(order.getStatus());
        Set<OrderStatusEnum> pendingStatuses = response ? RESPONSE_PENDING_STATUSES : HANDLE_PENDING_STATUSES;
        Integer breachedFlag = response ? order.getSlaResponseBreached() : order.getSlaHandleBreached();
        LocalDateTime milestone = response ? order.getAcceptedTime() : order.getSubmittedTime();
        if (status == null || !pendingStatuses.contains(status) || milestone != null
                || Integer.valueOf(1).equals(breachedFlag)) {
            log.info("SLA 已达标、终止或已标记，忽略消息：orderId={}, type={}", task.orderId(), task.type());
            return;
        }

        LocalDateTime currentDeadline = response ? order.getSlaResponseDeadline() : order.getSlaHandleDeadline();
        if (currentDeadline == null || task.deadline() != currentDeadline.atZone(SLA_ZONE).toInstant().toEpochMilli()) {
            log.info("SLA 期限已变化，忽略旧任务：orderId={}, type={}, deadline={}",
                    task.orderId(), task.type(), task.deadline());
            return;
        }
        if (task.deadline() > System.currentTimeMillis()) {
            // 原体、期限、批次 timestamp 和 messageId 不变，发送器覆盖旧负数 x-delay。
            rabbitMessagePublisher.publishDelayed(MqConstants.TOPIC_SLA_TIMEOUT, task.type(), message.getBody(),
                    message.getMessageProperties().getMessageId(), task.deadline());
            log.info("SLA 尚未到期，续投下一段：orderId={}, type={}, deadline={}",
                    task.orderId(), task.type(), task.deadline());
            return;
        }

        boolean breached = response ? slaService.markResponseBreached(task.orderId())
                : slaService.markHandleBreached(task.orderId());
        if (breached) {
            String sourceEventId = "SLA:" + task.orderId() + ":" + task.type();
            notificationService.notifyAdmins(sourceEventId, "工单 SLA 超时",
                    "工单 " + task.orderId() + " " + typeName(task.type()) + "，请立即介入处理。", task.orderId());
        } else {
            log.info("SLA 已推进或已标记，忽略消息：orderId={}, type={}", task.orderId(), task.type());
        }
    }

    /** 超时类型对应的通知文案。 */
    private String typeName(String type) {
        return MqConstants.SlaTimeout.RESPONSE.equals(type) ? "响应超时" : "处理超时";
    }
}
