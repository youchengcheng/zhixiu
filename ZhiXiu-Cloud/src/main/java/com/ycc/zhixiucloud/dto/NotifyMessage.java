package com.ycc.zhixiucloud.dto;

/**
 * 通知消息体：notification-topic 的载荷，按 channel 扇出到站内信 / 短信 / 推送三个消费组。
 *
 * <p>一次业务通知会由 NotifyProducer 为每个通道各发一条消息（Tag=channel），
 * 三条消息各自生成 eventId；消费端只做对应通道的发送，失败由 RocketMQ 重试。
 * eventId 当前用于日志追踪，并预留给后续消息中心/推送回执去重。</p>
 *
 * @param eventId      事件 ID（全局唯一，便于幂等）
 * @param channel      SITE / SMS / PUSH，取值见 MqConstants.Notify
 * @param receiverId   接收人 user_id
 * @param receiverType 接收人角色：OWNER / WORKER / ADMIN
 * @param title        标题
 * @param content      正文
 * @param orderId      关联工单 ID，无关联为 null
 * @param timestamp    消息创建时间（毫秒）
 */
public record NotifyMessage(String eventId, String channel, Long receiverId, String receiverType,
                            String title, String content, Long orderId, Long timestamp) {
}