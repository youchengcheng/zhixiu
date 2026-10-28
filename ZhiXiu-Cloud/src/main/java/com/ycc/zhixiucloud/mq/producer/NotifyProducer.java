package com.ycc.zhixiucloud.mq.producer;

import com.ycc.zhixiucloud.common.constant.MqConstants;
import com.ycc.zhixiucloud.dto.NotifyMessage;
import com.ycc.zhixiucloud.mq.MqMessageCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.core.RocketMQClientTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 通知生产者：notification-topic。
 * 一次业务动作按通道扇出 SITE / SMS / PUSH 三条消息，Tag 与通道同名，
 * 分别由 notify-site-consumer-group、notify-sms-consumer-group、notify-push-consumer-group 处理。
 *
 * <p>通知属于「尽力而为」的旁路动作：同步发送，投递失败只记日志，不影响工单主流程；
 * 每条消息生成独立 eventId（UUID），便于消费端日志追踪与后续去重。
 * receiverId 为空时直接跳过，避免产生无接收人的无效消息。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotifyProducer {

    private final RocketMQClientTemplate rocketMQClientTemplate;

    private final MqMessageCodec mqMessageCodec;

    /** 站内信 */
    public void sendSite(Long receiverId, String receiverType, String title, String content, Long orderId) {
        // 站内信通道：委托统一发送入口
        send(MqConstants.Notify.SITE, receiverId, receiverType, title, content, orderId);
    }

    /** 短信 */
    public void sendSms(Long receiverId, String receiverType, String title, String content, Long orderId) {
        // 短信通道：委托统一发送入口
        send(MqConstants.Notify.SMS, receiverId, receiverType, title, content, orderId);
    }

    /** App 推送 */
    public void sendPush(Long receiverId, String receiverType, String title, String content, Long orderId) {
        // App 推送通道：委托统一发送入口
        send(MqConstants.Notify.PUSH, receiverId, receiverType, title, content, orderId);
    }

    /**
     * 统一发送入口：组装消息体后按「topic:channel」同步投递。
     * 无接收人直接返回；发送异常被吞掉并记录错误日志，保证通知失败不反向影响业务事务。
     */
    private void send(String channel, Long receiverId, String receiverType, String title, String content, Long orderId) {
        // 没有接收人（例如未分配师傅）时无需发送
        if (receiverId == null) {
            return;
        }
        // eventId 每条消息独立生成，作为消费端追踪/去重的业务标识
        NotifyMessage message = new NotifyMessage(UUID.randomUUID().toString(), channel, receiverId,
                receiverType, title, content, orderId, System.currentTimeMillis());
        // destination 形如 notification-topic:SITE
        String destination = MqConstants.destination(MqConstants.TOPIC_NOTIFICATION, channel);
        // 同步发送，异常由下面的 catch 吞掉不影响业务
        try {
            // 同步发送：通知量小且需要尽快反馈，失败也不重试
            rocketMQClientTemplate.syncSendNormalMessage(destination, mqMessageCodec.encode(message));
            // 记录投递成功信息
            log.info("通知投递成功：channel={}, receiverId={}, orderId={}, title={}",
                    channel, receiverId, orderId, title);
        } catch (Exception e) {
            // 通知是旁路动作：这里不抛出，避免影响已提交的工单业务
            log.error("通知投递失败：channel={}, receiverId={}, orderId={}", channel, receiverId, orderId, e);
        }
    }
}