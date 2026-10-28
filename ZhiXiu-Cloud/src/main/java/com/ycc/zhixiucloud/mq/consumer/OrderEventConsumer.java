package com.ycc.zhixiucloud.mq.consumer;

import com.ycc.zhixiucloud.common.constant.MqConstants;
import com.ycc.zhixiucloud.common.enums.OrderStatusEnum;
import com.ycc.zhixiucloud.dto.WorkOrderEvent;
import com.ycc.zhixiucloud.mq.MqMessageCodec;
import com.ycc.zhixiucloud.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.annotation.RocketMQMessageListener;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 工单事件消费者：订阅 workorder-event-topic 全部 Tag，消费组 order-event-consumer-group。
 * 单线程消费保证同一队列内事件按序处理；按事件类型扇出通知。
 *
 * <p>顺序性：生产端以 orderId 作为消息组发送 FIFO 消息，同一工单的事件进入同一队列，
 * 再配合 consumptionThreadCount=1，保证单实例内串行处理，避免通知乱序。</p>
 *
 * <p>幂等性：本消费者只做通知扇出，不回写工单状态，重复消费不会破坏业务数据；
 * 但通知本身未按 eventId 去重，极端重试时可能重复提醒，eventId 可用于日志追踪，
 * 后续可增加幂等表或消息中心去重。</p>
 *
 * <p>失败处理：消费失败返回 FAILURE，交由 RocketMQ 重试，重试耗尽进
 * {@link MqConstants#DLQ_WORK_ORDER_EVENT}，可人工排查后补偿。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = MqConstants.TOPIC_WORK_ORDER_EVENT,
        consumerGroup = MqConstants.GROUP_ORDER_EVENT,
        tag = MqConstants.TAG_ALL,
        sslEnabled = false,
        consumptionThreadCount = 1)
public class OrderEventConsumer implements RocketMQListener {

    private final MqMessageCodec mqMessageCodec;

    private final NotificationService notificationService;

    /**
     * 消费单条工单事件：反序列化 → 打印流转信息 → dispatch 扇出通知 → 返回 SUCCESS。
     * 解码或通知过程出现异常时返回 FAILURE，由 MQ 重投。
     */
    @Override
    public ConsumeResult consume(MessageView messageView) {
        // 事件对象先置空，便于失败时定位
        WorkOrderEvent event = null;
        try {
            // 反序列化消息体为工单事件
            event = mqMessageCodec.decode(messageView.getBody(), WorkOrderEvent.class);
            // 打印事件流转信息，便于追踪
            log.info("收到工单事件：orderId={}, event={}, {} -> {}, operatorType={}",
                    event.orderId(), event.eventType(), event.fromStatus(), event.toStatus(),
                    event.operatorType());
            // 按事件类型扇出通知
            dispatch(event);
            // 处理成功，确认消息
            return ConsumeResult.SUCCESS;
        } catch (Exception e) {
            // 记录失败日志并返回 FAILURE 触发 MQ 重试
            log.error("工单事件消费失败：messageId={}, payload={}",
                    messageView.getMessageId(), mqMessageCodec.toText(messageView.getBody()), e);
            // 消费失败，交由 MQ 重试
            return ConsumeResult.FAILURE;
        }
    }

    /**
     * 事件 -> 通知扇出。
     * 接收人按事件语义选择：业主关注接单、到场、暂停/恢复、提交进度；
     * 师傅关注派单、完工与取消；管理员关注新单、超时回退、转单与取消等需要介入的节点。
     */
    private void dispatch(WorkOrderEvent event) {
        // 提取工单 ID
        Long orderId = event.orderId();
        // 取工单号附加数据
        String orderNo = text(event, "orderNo");
        // 取工单标题附加数据
        String orderTitle = text(event, "title");
        // 拼接通知摘要，后续复用
        String summary = "工单 " + orderNo + "（" + orderTitle + "）";
        // 按事件类型选择接收人并扇出通知
        switch (event.eventType()) {
            // 工单创建：提醒管理员及时派单
            case MqConstants.OrderEvent.CREATED -> notificationService.notifyAdmins(
                    "新报修工单待派单", summary + "已提交，请及时派单。", orderId);
            // 派单相关事件：可能是超时回退，也可能是新派单
            case MqConstants.OrderEvent.ASSIGNED -> {
                // 同一 Tag 覆盖「派单」与「超时未接单回退」，按目标状态区分
                if (OrderStatusEnum.PENDING.getCode().equals(event.toStatus())) {
                    // 已回退待接单池：提醒管理员
                    notificationService.notifyAdmins("派单超时未接单",
                            summary + "超过接单时限，已回退待接单池。", orderId);
                } else {
                    // 正常派单：通知被指派的师傅接单
                    notificationService.notifyWorker(longValue(event, "assignedWorkerId"),
                            "您有新的维修派单", summary + "已派给您，请及时接单。", orderId);
                }
            }
            // 师傅接单：告知业主，无需再等待派单
            case MqConstants.OrderEvent.ACCEPTED -> notificationService.notifyOwner(
                    longValue(event, "ownerId"), "师傅已接单",
                    summary + "已由师傅接单，请留意上门时间。", orderId);
            // 师傅到场：告知业主维修即将开始
            case MqConstants.OrderEvent.ARRIVE -> notificationService.notifyOwner(
                    longValue(event, "ownerId"), "师傅已到场",
                    summary + "师傅已到场开始处理。", orderId);
            // 维修暂停：把挂起原因同步给业主
            case MqConstants.OrderEvent.PAUSE -> notificationService.notifyOwner(
                    longValue(event, "ownerId"), "维修已暂停",
                    summary + "暂时挂起，原因：" + text(event, "remark"), orderId);
            // 维修恢复：告知业主继续处理
            case MqConstants.OrderEvent.RESUME -> notificationService.notifyOwner(
                    longValue(event, "ownerId"), "维修已恢复",
                    summary + "已恢复处理。", orderId);
            // 转单申请：需要管理员人工介入
            case MqConstants.OrderEvent.TRANSFER -> notificationService.notifyAdmins(
                    "师傅申请转单", summary + "师傅申请转单，原因：" + text(event, "remark"), orderId);
            // 完工提交：提醒业主确认并评价
            case MqConstants.OrderEvent.SUBMITTED -> notificationService.notifyOwner(
                    longValue(event, "ownerId"), "师傅已提交完工",
                    summary + "已提交完工，请确认并评价。", orderId);
            // 完工确认：告知实际接单师傅
            case MqConstants.OrderEvent.COMPLETED -> notificationService.notifyWorker(
                    longValue(event, "acceptedWorkerId"), "工单已完工",
                    summary + "已确认完工。", orderId);
            // 工单取消：管理员与已接单师傅都需要知晓，避免继续上门
            case MqConstants.OrderEvent.CANCELED -> {
                // 工单取消：一并通知管理员
                notificationService.notifyAdmins("工单已取消",
                        summary + "已取消，原因：" + text(event, "remark"), orderId);
                // 通知已接单师傅无需继续处理
                notificationService.notifyWorker(longValue(event, "acceptedWorkerId"),
                        "工单已取消", summary + "已取消，无需继续处理。", orderId);
            }
            // 未配置通知的事件类型仅记录 debug 日志
            default -> log.debug("未配置通知的事件类型：{}", event.eventType());
        }
    }

    /**
     * 事件附加数据取值：全局 Jackson 会把 Long 序列化为字符串，而手工构造的 data 也可能是数字，
     * 因此同时兼容 Number 与 String。解析失败返回 null 而不是抛异常，
     * 避免个别附加字段异常阻断整条通知事件。
     */
    private Long longValue(WorkOrderEvent event, String key) {
        // 取附加数据，data 为空时直接视为缺省
        Object value = event.data() == null ? null : event.data().get(key);
        // 值为空直接返回 null
        if (value == null) {
            // 无附加数据时返回空
            return null;
        }
        // 数字类型直接转 Long
        if (value instanceof Number number) {
            // 数值声明直接取整数值
            return number.longValue();
        }
        try {
            // 字符串类型按十进制解析
            return Long.valueOf(value.toString());
        } catch (NumberFormatException e) {
            // 解析失败只告警并返回 null，不阻断整条通知
            log.warn("工单事件数据字段无法解析：key={}, value={}", key, value);
            // 解析失败返回空，不阻断通知
            return null;
        }
    }

    /** 取文本附加数据；字段缺失时用 "-" 占位，避免通知正文出现 null */
    private String text(WorkOrderEvent event, String key) {
        // 取附加数据，data 为空时视为缺省
        Object value = event.data() == null ? null : event.data().get(key);
        // 缺失字段用 "-" 占位，避免通知正文出现 null
        return value == null ? "-" : value.toString();
    }
}