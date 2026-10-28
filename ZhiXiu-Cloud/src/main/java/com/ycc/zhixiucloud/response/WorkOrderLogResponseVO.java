package com.ycc.zhixiucloud.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单流转记录行（GET /api/owner/order/{id}/logs、GET /api/admin/order/{id}/logs），
 * 业主端与管理端共用，按发生时间正序排列。
 * 系统自动操作时 operatorId、operatorName 为 null；创建工单时 fromStatus 为 null。
 */
@Data
@Builder
@Schema(description = "工单流转记录")
public class WorkOrderLogResponseVO {

    @Schema(description = "日志 ID")
    private Long id;

    @Schema(description = "工单 ID")
    private Long orderId;

    @Schema(description = "操作人 ID")
    private Long operatorId;

    @Schema(description = "操作人类型：OWNER / WORKER / ADMIN / SYSTEM")
    private String operatorType;

    @Schema(description = "操作人类型文本")
    private String operatorTypeText;

    @Schema(description = "操作人姓名")
    private String operatorName;

    /** 动作编码：CREATE / ASSIGN / ACCEPT / ARRIVE / PAUSE / RESUME / TRANSFER / SYSTEM_SUBMIT / COMPLETE / CANCEL */
    @Schema(description = "动作编码")
    private String action;

    @Schema(description = "动作文本")
    private String actionText;

    /** 创建工单的首条记录为 null；其余记录为流转前状态编码 */
    @Schema(description = "原状态")
    private String fromStatus;

    @Schema(description = "原状态文本")
    private String fromStatusText;

    @Schema(description = "新状态")
    private String toStatus;

    @Schema(description = "新状态文本")
    private String toStatusText;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "发生时间")
    private LocalDateTime createTime;
}