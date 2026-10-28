package com.ycc.zhixiucloud.dto;

/**
 * 操作日志消息体：由 @ApiLog 切面采集，经 operation-log-topic 异步落库。
 * params 在采集侧已脱敏（密码、身份证、手机号不落明文）。
 *
 * <p>异步落库的目的：日志写入与业务请求解耦，MQ 不可用或消费延迟不会拖慢接口；
 * 消费端按至少一次语义处理，极端重试可能产生重复日志，属于可容忍的审计流水。</p>
 */
public record OperationLogMessage(
        // 操作人 ID，系统操作为空
        Long userId,
        // 操作人登录名，系统操作为空
        String username,
        // 操作人角色编码
        String role,
        // 业务模块
        String module,
        // 操作动作描述
        String action,
        // HTTP 方法
        String method,
        // 请求地址
        String url,
        // 请求参数（已脱敏）
        String params,
        // 客户端 IP
        String ip,
        // 结果：0失败 1成功
        Integer result,
        // 错误信息，成功时为空
        String errorMsg,
        // 耗时（毫秒）
        Integer costTime,
        // 采集时间戳（毫秒）
        Long timestamp
) {
}
