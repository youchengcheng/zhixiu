package com.ycc.zhixiucloud.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志行出参（GET /api/admin/log/list，管理端只读）。
 * 日志由 @ApiLog 切面采集并经 MQ 异步落库，列表按发生时间倒序；
 * 系统自动操作时 userId、username 为 null。
 */
@Data
@Builder
@Schema(description = "操作日志行")
public class OperationLogResponseVO {

    @Schema(description = "日志 ID")
    private Long id;

    @Schema(description = "操作人 ID，系统操作为空")
    private Long userId;

    @Schema(description = "操作人登录名，系统操作为空")
    private String username;

    @Schema(description = "操作人角色：OWNER/WORKER/ADMIN/SYSTEM")
    private String role;

    @Schema(description = "模块")
    private String module;

    @Schema(description = "操作")
    private String action;

    @Schema(description = "HTTP 方法")
    private String method;

    @Schema(description = "请求地址")
    private String url;

    @Schema(description = "请求参数（已脱敏）")
    private String params;

    @Schema(description = "客户端 IP")
    private String ip;

    @Schema(description = "结果：0失败 1成功")
    private Integer result;

    @Schema(description = "错误信息，成功时为空")
    private String errorMsg;

    @Schema(description = "耗时(ms)")
    private Integer costTime;

    @Schema(description = "发生时间")
    private LocalDateTime createTime;
}
