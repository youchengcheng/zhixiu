package com.ycc.zhixiucloud.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 操作日志查询入参（GET /api/admin/log/list），提交方为管理员。
 * 各筛选条件均可选，同时传入时按 AND 组合；startTime / endTime 构成闭区间时间范围，
 * 只传一端时按单边条件查询，结果按发生时间倒序，分页参数继承自 PageRequestVO。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "操作日志查询入参")
public class OperationLogQueryRequestVO extends PageRequestVO {

    @Schema(description = "模块，如「工单」")
    private String module;

    /** 模糊匹配操作人登录名。 */
    @Schema(description = "操作人登录名")
    private String username;

    @Schema(description = "结果：0失败 1成功")
    private Integer result;

    /** 时间范围下界（含），与 endTime 配合使用；为空表示不限开始时间。 */
    @Schema(description = "开始时间，支持 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss")
    private String startTime;

    /** 时间范围上界（含），应不早于 startTime；为空表示不限结束时间。 */
    @Schema(description = "结束时间，支持 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss")
    private String endTime;
}
