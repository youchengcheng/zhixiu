package com.ycc.zhixiucloud.common.constant;

/**
 * RocketMQ Topic / Tag / 消费组常量。
 *
 * <p>与开发文档「后端开发文档 · 六、RocketMQ 使用清单」一一对应，集中定义可避免
 * 生产端与消费端字符串不一致。发送时 destination 形如 topic + ":" + tag
 * （v5 gRPC 客户端按第一个冒号拆分），因此 Topic 名称本身不能包含冒号。</p>
 *
 * <p>本类是纯常量工具类，禁止实例化；内部按业务域用静态嵌套类
 * （OrderEvent、SlaTimeout、Notify 等）对 Tag 分组。</p>
 */
public final class MqConstants {

    /** Topic 与 Tag 的分隔符 */
    public static final String DESTINATION_SEPARATOR = ":";

    /** 消费全部 Tag */
    public static final String TAG_ALL = "*";

    /** 工单状态变更事件 */
    public static final String TOPIC_WORK_ORDER_EVENT = "workorder-event-topic";

    /** SLA 超时延迟消息 */
    public static final String TOPIC_SLA_TIMEOUT = "sla-timeout-topic";

    /** 多通道通知扇出 */
    public static final String TOPIC_NOTIFICATION = "notification-topic";

    /** 操作日志异步落库 */
    public static final String TOPIC_OPERATION_LOG = "operation-log-topic";

    /** 知识向量化、工单总结 */
    public static final String TOPIC_AI_TASK = "ai-task-topic";

    /** 工单事件死信队列（重试耗尽后由 RocketMQ 自动投递） */
    public static final String DLQ_WORK_ORDER_EVENT = "%DLQ%" + TOPIC_WORK_ORDER_EVENT;

    /** 工单事件消费组（顺序消费） */
    public static final String GROUP_ORDER_EVENT = "order-event-consumer-group";

    /** SLA 超时消费组 */
    public static final String GROUP_SLA_TIMEOUT = "sla-consumer-group";

    /** 站内信消费组 */
    public static final String GROUP_NOTIFY_SITE = "notify-site-consumer-group";

    /** 短信消费组 */
    public static final String GROUP_NOTIFY_SMS = "notify-sms-consumer-group";

    /** 推送消费组 */
    public static final String GROUP_NOTIFY_PUSH = "notify-push-consumer-group";

    /** 操作日志消费组 */
    public static final String GROUP_OPERATION_LOG = "operation-log-consumer-group";

    /** AI 任务消费组 */
    public static final String GROUP_AI_TASK = "ai-task-consumer-group";

    private MqConstants() {
    }

    /**
     * 拼接 RocketMQ destination。
     *
     * @param topic 消息主题，不能为空且不能包含冒号
     * @param tag   消息标签，可为 {@link #TAG_ALL}
     * @return 形如 {@code topic:tag} 的 destination
     */
    public static String destination(String topic, String tag) {
        // 以第一个冒号分隔 topic 与 tag
        return topic + DESTINATION_SEPARATOR + tag;
    }

    /**
     * 工单状态变更事件 Tag。
     *
     * <p>与 {@link com.ycc.zhixiucloud.common.enums.OrderActionEnum#getEventTag()}
     * 的取值保持一致，生产端通常由状态机根据动作选择对应 Tag。</p>
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

    /** SLA 超时延迟消息 Tag */
    public static final class SlaTimeout {

        /** 响应超时 */
        public static final String RESPONSE = "RESPONSE";

        /** 处理超时 */
        public static final String HANDLE = "HANDLE";

        /** 订阅表达式：同时消费两类超时 */
        public static final String TAG_EXPRESSION = RESPONSE + " || " + HANDLE;

        private SlaTimeout() {
        }
    }

    /** 通知通道 Tag */
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

    /** 操作日志 Tag */
    public static final class OperationLog {

        /** 接口访问日志 */
        public static final String ACCESS = "ACCESS";

        private OperationLog() {
        }
    }

    /** AI 任务 Tag */
    public static final class AiTask {

        /** 知识向量化 */
        public static final String VECTORIZE = "VECTORIZE";

        /** 工单总结 */
        public static final String SUMMARIZE = "SUMMARIZE";

        private AiTask() {
        }
    }
}