package com.ycc.zhixiucloud.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SLA 规则新增 / 修改入参（POST /api/admin/sla、PUT /api/admin/sla/{id}），提交方为管理员。
 * 匹配维度留空表示不参与匹配，四个维度全空即全局兜底规则；
 * 超时时间单位为分钟，派单优先级权重越大越优先。
 */
@Data
@Schema(description = "SLA 规则入参")
public class SlaConfigRequestVO {

    @Schema(description = "报修类型 ID，空=全局")
    private Long categoryId;

    @Min(value = 1, message = "紧急程度取值 1-4")
    @Max(value = 4, message = "紧急程度取值 1-4")
    @Schema(description = "紧急程度，空=所有")
    private Integer urgency;

    @Size(max = 50, message = "安全隐患标志不能超过 50 字")
    @Schema(description = "安全隐患标志，空=所有")
    private String safetyFlag;

    @Min(value = 1, message = "影响范围取值 1-3")
    @Max(value = 3, message = "影响范围取值 1-3")
    @Schema(description = "影响范围，空=所有")
    private Integer affectScope;

    @NotNull(message = "响应超时不能为空")
    @Min(value = 1, message = "响应超时至少 1 分钟")
    @Schema(description = "响应超时(分钟)")
    private Integer responseTimeout;

    @NotNull(message = "处理超时不能为空")
    @Min(value = 1, message = "处理超时至少 1 分钟")
    @Schema(description = "处理超时(分钟)")
    private Integer handleTimeout;

    /** 升级通知的接收角色（如 ADMIN），随规则保存，供升级事件通知使用。 */
    @Size(max = 20, message = "升级通知角色不能超过 20 字")
    @Schema(description = "升级通知角色", example = "ADMIN")
    private String escalateTo;

    /** 不传时服务端按 0 处理。 */
    @Schema(description = "派单优先级权重，越大越优先")
    private Integer priorityWeight;
}