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
 * 首期业务只投递 SITE 站内信；SMS / PUSH 方法保留为后续供应商接入占位。
 *
 * <p>通知属于「尽力而为」的旁路动作：同步发送，投递失败只记日志，不影响工单主流程；
 * SITE 消息使用原业务事件 ID + 接收人 + 通道派生稳定 eventId，保证上游事件重投时
 * 仍能由通知表唯一索引去重；占位通道继续生成独立 UUID。
 * receiverId 为空时直接跳过，避免产生无接收人的无效消息。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotifyProducer {

    private final RocketMQClientTemplate rocketMQClientTemplate;

    private final MqMessageCodec mqMessageCodec;

    /** 站内信 */
    public void sendSite(String sourceEventId, Long receiverId, String receiverType,
                         String title, String content, Long orderId) {
        // 同一原始事件投递给同一接收人的 SITE 消息始终得到相同 eventId
        String eventId = sourceEventId + ":" + receiverId + ":" + MqConstants.Notify.SITE;
        // 站内信通道：委托统一发送入口
        send(eventId, MqConstants.Notify.SITE, receiverId, receiverType, title, content, orderId);
    }

    /** 短信 */
    public void sendSms(Long receiverId, String receiverType, String title, String content, Long orderId) {
        // 短信通道：委托统一发送入口
        send(UUID.randomUUID().toString(), MqConstants.Notify.SMS,
                receiverId, receiverType, title, content, orderId);
    }

    /** App 推送 */
    public void sendPush(Long receiverId, String receiverType, String title, String content, Long orderId) {
        // App 推送通道：委托统一发送入口
        send(UUID.randomUUID().toString(), MqConstants.Notify.PUSH,
                receiverId, receiverType, title, content, orderId);
    }

    /**
     * 统一发送入口：组装消息体后按「topic:channel」同步投递。
     * 无接收人直接返回；发送异常被吞掉并记录错误日志，保证通知失败不反向影响业务事务。
     */
    private void send(String eventId, String channel, Long receiverId, String receiverType,
                      String title, String content, Long orderId) {
        // 没有接收人（例如未分配师傅）时无需发送
        if (receiverId == null) {
            return;
        }
        // eventId 作为消费端追踪和通知表幂等标识
        NotifyMessage message = new NotifyMessage(eventId, channel, receiverId,
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
