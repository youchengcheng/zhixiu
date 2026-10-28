package com.ycc.zhixiucloud.dto;

/**
 * SLA 超时延迟消息体（开发文档 6.3）。
 * 工单创建时按 SLA 规则算出超时点，用任意秒延迟消息投递，到期后由 SlaTimeoutConsumer 处理。
 *
 * <p>延迟消息只是「到点提醒」：消费端仍会重新检查工单当前状态，
 * 若业主/师傅已在时限内完成响应或提交，则忽略该消息并确认消费，避免误报超时。
 * 消息按业务类型作为 Tag（RESPONSE / HANDLE），消费组订阅两个 Tag。</p>
 *
 * @param orderId   工单 ID
 * @param type      RESPONSE 响应超时 / HANDLE 处理超时，取值见 MqConstants.SlaTimeout
 * @param deadline  超时点（毫秒），便于消费端判断是否已被业务提前满足
 * @param timestamp 消息创建时间（毫秒）
 */
public record SlaTask(Long orderId, String type, Long deadline, Long timestamp) {
}