package com.ycc.zhixiucloud.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 取消工单入参，提交方为业主或管理员：业主端为 POST /api/owner/order/{id}/cancel，
 * 管理端为 POST /api/admin/order/{id}/cancel。
 * 仅待接单、已派单、已接单、处理中的工单可取消；取消后进入终态，不可再恢复。
 */
@Data
@Schema(description = "取消工单入参")
public class CancelOrderRequestVO {

    @NotBlank(message = "取消原因不能为空")
    @Size(max = 200, message = "取消原因不能超过 200 字")
    @Schema(description = "取消原因")
    private String cancelReason;
}