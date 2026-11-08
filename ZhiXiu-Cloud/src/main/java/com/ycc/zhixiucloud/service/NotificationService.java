package com.ycc.zhixiucloud.service;

/**
 * 通知服务：解析接收人并投递通知消息。
 *
 * <p>职责边界：只负责解析接收人并投递 MQ 消息，真正的发送由对应消费组完成；
 * 通知属于旁路动作，不参与业务事务，失败只记录日志，不向调用方抛异常。</p>
 *
 * <p>主要使用方：工单状态机事件消费、SLA 超时处理、AI 转工单等需要触达用户的业务。</p>
 *
 * <p>首期只启用 SITE 站内信通道；短信和推送消费者保留为后续扩展占位。
 * 师傅通知需要先把 maintenance_worker.id 解析为 user_id。sourceEventId
 * 来自原始工单或 SLA 事件，用于派生稳定的站内通知 eventId。</p>
 */
public interface NotificationService {

    /**
     * 通知业主（首期站内信）。
     *
     * @param ownerId 业主 user_id
     * @param title   通知标题
     * @param content 通知内容
     * @param orderId 关联工单 ID，可为 null（非工单类通知）
     */
    void notifyOwner(String sourceEventId, Long ownerId, String title, String content, Long orderId);

    /**
     * 通知师傅（首期站内信）。
     *
     * <p>入参是 maintenance_worker.id，内部会解析为对应的 user_id 再投递；
     * 师傅不存在时只记 warn 日志并返回，不抛异常。</p>
     *
     * @param workerId 师傅 ID（maintenance_worker.id），可为 null
     * @param title    通知标题
     * @param content  通知内容
     * @param orderId  关联工单 ID，可为 null
     */
    void notifyWorker(String sourceEventId, Long workerId, String title, String content, Long orderId);

    /**
     * 通知全部状态正常的 admin 角色用户（每人一条站内信）。
     *
     * <p>管理员角色未初始化或没有可用管理员时只记 warn 日志，不抛异常。</p>
     *
     * @param title   通知标题
     * @param content 通知内容
     * @param orderId 关联工单 ID，可为 null
     */
    void notifyAdmins(String sourceEventId, String title, String content, Long orderId);
}
