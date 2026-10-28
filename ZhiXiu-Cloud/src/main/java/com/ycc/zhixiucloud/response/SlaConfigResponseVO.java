package com.ycc.zhixiucloud.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SLA 规则出参（GET /api/admin/sla/list，管理端）。
 * 规则按 priorityWeight 降序、ID 升序返回；categoryId / urgency / safetyFlag / affectScope
 * 为空表示该维度匹配所有场景。
 */
@Data
@Builder
@Schema(description = "SLA 规则")
public class SlaConfigResponseVO {

    @Schema(description = "规则 ID")
    private Long id;

    @Schema(description = "报修类型 ID，空=全局")
    private Long categoryId;

    @Schema(description = "紧急程度，空=所有")
    private Integer urgency;

    @Schema(description = "安全隐患标志，空=所有")
    private String safetyFlag;

    @Schema(description = "影响范围，空=所有")
    private Integer affectScope;

    @Schema(description = "响应超时(分钟)")
    private Integer responseTimeout;

    @Schema(description = "处理超时(分钟)")
    private Integer handleTimeout;

    @Schema(description = "升级通知角色")
    private String escalateTo;

    /** 数值越大越优先参与规则匹配 */
    @Schema(description = "派单优先级权重")
    private Integer priorityWeight;

    @Schema(description = "0禁用 1启用")
    private Integer enabled;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}