package com.ycc.zhixiucloud.dto;

/**
 * SLA 超时延迟消息体（开发文档 6.3）。
 * 工单创建后回读数据库保存的超时点，通过 RabbitMQ 延迟插件分段投递，每段最多 24 小时。
 *
 * <p>延迟消息只是「到点提醒」：消费端仍会重新检查工单当前期限、状态和里程碑，
 * 若业主/师傅已在时限内完成响应或提交，则忽略该消息并确认消费，避免误报超时。
 * 未到期时保持载荷续投。业务类型作为 Routing Key（RESPONSE / HANDLE），绑定同一 SLA 队列。</p>
 *
 * @param orderId   工单 ID
 * @param type      RESPONSE 响应超时 / HANDLE 处理超时，取值见 MqConstants.SlaTimeout
 * @param deadline  数据库原超时点（Asia/Shanghai 转换的毫秒时间戳），用于核对当前期限
 * @param timestamp 消息创建时间（毫秒）
 */
public record SlaTask(Long orderId, String type, Long deadline, Long timestamp) {
}
