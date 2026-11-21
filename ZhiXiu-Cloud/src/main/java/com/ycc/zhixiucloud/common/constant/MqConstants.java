package com.ycc.zhixiucloud.common.constant;

/**
 * RabbitMQ Exchange / Routing Key / Queue 常量。
 *
 * <p>与开发文档「后端开发文档 · 六、RabbitMQ 使用清单」一一对应。Exchange 保留原主题名，
 * Queue 保留原消费组名，Routing Key 保留原事件值，集中定义避免生产端与消费端不一致。</p>
 *
 * <p>本类是纯常量工具类，禁止实例化；内部按业务域用静态嵌套类
 * （OrderEvent、SlaTimeout、Notify 等）对 Routing Key 分组。</p>
 */
public final class MqConstants {

    /** 各业务队列共享的死信 Exchange */
    public static final String EXCHANGE_DEAD_LETTER = "zhixiu.dead-letter";

    /** 工单状态变更事件 */
    public static final String TOPIC_WORK_ORDER_EVENT = "workorder-event-topic";

    /** SLA 超时延迟消息 */
    public static final String TOPIC_SLA_TIMEOUT = "sla-timeout-topic";

    /** 通知消息，首期启用 SITE 站内信 */
    public static final String TOPIC_NOTIFICATION = "notification-topic";

    /** 操作日志异步落库 */
    public static final String TOPIC_OPERATION_LOG = "operation-log-topic";

    /** 知识向量化、工单总结 */
    public static final String TOPIC_AI_TASK = "ai-task-topic";

    /** 工单事件队列（单活串行消费） */
    public static final String GROUP_ORDER_EVENT = "order-event-consumer-group";

    /** SLA 超时队列 */
    public static final String GROUP_SLA_TIMEOUT = "sla-consumer-group";

    /** 站内信队列 */
    public static final String GROUP_NOTIFY_SITE = "notify-site-consumer-group";

    /** 短信队列 */
    public static final String GROUP_NOTIFY_SMS = "notify-sms-consumer-group";

    /** 推送队列 */
    public static final String GROUP_NOTIFY_PUSH = "notify-push-consumer-group";

    /** 操作日志队列 */
    public static final String GROUP_OPERATION_LOG = "operation-log-consumer-group";

    /** AI 任务队列 */
    public static final String GROUP_AI_TASK = "ai-task-consumer-group";

    /** 工单事件独立死信队列 */
    public static final String DLQ_WORK_ORDER_EVENT = GROUP_ORDER_EVENT + ".dlq";

    /** 站内信独立死信队列 */
    public static final String DLQ_NOTIFY_SITE = GROUP_NOTIFY_SITE + ".dlq";

    private MqConstants() {
    }

    /**
     * 得到业务队列对应的独立死信队列名称。
     *
     * @param group 业务队列名称
     * @return 形如 {@code group.dlq} 的队列名称
     */
    public static String dlq(String group) {
        return group + ".dlq";
    }

    /**
     * 工单状态变更事件 Routing Key。
     *
     * <p>与 {@link com.ycc.zhixiucloud.common.enums.OrderActionEnum#getEventTag()}
     * 的取值保持一致，生产端通常由状态机根据动作选择对应 Routing Key。</p>
     */
    public static final class OrderEvent {

        // 工单创建
        public static final String CREATED = "CREATED";

        // 工单派单
        public static final String ASSIGNED = "ASSIGNED";

        // 工单接单
        public static final String ACCEPTED = "ACCEPTED";

        // 维修工到场
        public static final String ARRIVE = "ARRIVE";

        // 工单挂起
        public static final String PAUSE = "PAUSE";

        // 工单恢复
        public static final String RESUME = "RESUME";

        // 工单转派
        public static final String TRANSFER = "TRANSFER";

        // 工单提交
        public static final String SUBMITTED = "SUBMITTED";

        // 工单完成
        public static final String COMPLETED = "COMPLETED";

        // 工单取消
        public static final String CANCELED = "CANCELED";

        private OrderEvent() {
        }
    }

    /** SLA 超时延迟消息 Routing Key */
    public static final class SlaTimeout {

        /** 响应超时 */
        public static final String RESPONSE = "RESPONSE";

        /** 处理超时 */
        public static final String HANDLE = "HANDLE";

        private SlaTimeout() {
        }
    }

    /** 通知通道 Routing Key */
    public static final class Notify {

        /** 站内信 */
        public static final String SITE = "SITE";

        /** 短信 */
        public static final String SMS = "SMS";

        /** App 推送 */
        public static final String PUSH = "PUSH";

        private Notify() {
        }
    }

    /** 操作日志 Routing Key */
    public static final class OperationLog {

        /** 接口访问日志 */
        public static final String ACCESS = "ACCESS";

        private OperationLog() {
        }
    }

    /** AI 任务 Routing Key */
    public static final class AiTask {

        /** 知识向量化 */
        public static final String VECTORIZE = "VECTORIZE";

        /** 工单总结 */
        public static final String SUMMARIZE = "SUMMARIZE";

        private AiTask() {
        }
    }
}
