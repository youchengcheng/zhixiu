package com.ycc.zhixiucloud.dto;

/**
 * 通知消息体：notification-topic 的载荷，按 channel 路由到对应队列。
 *
 * <p>首期只启用 SITE 站内信通道。SITE 的 eventId 由原业务事件、接收人和通道共同派生，
 * 消费端以 notification 表唯一索引去重；同次投递处理三次仍失败后进入独立 DLQ。</p>
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
