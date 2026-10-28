package com.ycc.zhixiucloud.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 操作日志表 operation_log。
 * <p>
 * 由日志切面统一记录后台请求的操作人、模块、动作、请求参数、执行结果与耗时，
 * 用于行为审计和异常排查。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("operation_log")
public class OperationLog extends BaseEntity {

    /** 操作人 user_id，系统自动任务时可为空 */
    private Long userId;

    /** 操作人用户名快照，避免用户改名后日志失真 */
    private String username;

    /** 操作人角色快照：OWNER / WORKER / ADMIN */
    private String role;

    /** 所属功能模块，如「工单管理」 */
    private String module;

    /** 具体操作名称，如「派单」「取消工单」 */
    private String action;

    /** HTTP 请求方法：GET / POST / PUT / DELETE */
    private String method;

    /** 请求 URL */
    private String url;

    /** 请求参数（已脱敏，避免记录密码等敏感信息） */
    private String params;

    /** 客户端 IP 地址 */
    private String ip;

    /** 执行结果：0=失败 1=成功 */
    private Integer result;

    /** 失败时的异常信息，成功时为空 */
    private String errorMsg;

    /** 请求耗时(ms) */
    private Integer costTime;
}
