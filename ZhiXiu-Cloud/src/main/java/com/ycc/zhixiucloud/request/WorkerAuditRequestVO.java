package com.ycc.zhixiucloud.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 师傅审核入参（POST /api/admin/worker/{id}/audit），提交方为管理员。
 * 审核通过后师傅才可被派单 / 抢单；驳回时建议填写备注说明原因。
 */
@Data
@Schema(description = "师傅审核入参")
public class WorkerAuditRequestVO {

    @NotNull(message = "请选择审核结果")
    @Min(value = 1, message = "审核结果取值 1 通过 / 2 驳回")
    @Max(value = 2, message = "审核结果取值 1 通过 / 2 驳回")
    @Schema(description = "1 通过 2 驳回")
    private Integer auditStatus;

    @Size(max = 200, message = "审核备注不能超过 200 字")
    @Schema(description = "审核备注")
    private String auditRemark;
}