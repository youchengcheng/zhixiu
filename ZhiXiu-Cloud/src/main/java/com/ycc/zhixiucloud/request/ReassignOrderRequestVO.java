package com.ycc.zhixiucloud.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 改派入参（POST /api/admin/order/{id}/reassign），提交方为管理员。
 * 仅已派单、已接单、转单申请中的工单可改派；新师傅须审核通过且未被禁用。
 */
@Data
@Schema(description = "改派入参")
public class ReassignOrderRequestVO {

    /** 对应 maintenance_worker.id，不是 sys_user.id。 */
    @NotNull(message = "请选择新的维修师傅")
    @Schema(description = "新师傅 ID（maintenance_worker.id）")
    private Long workerId;

    @NotBlank(message = "改派原因不能为空")
    @Size(max = 200, message = "改派原因不能超过 200 字")
    @Schema(description = "改派原因")
    private String remark;
}