package com.ycc.zhixiucloud.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 手动派单入参（POST /api/admin/order/{id}/assign），提交方为管理员。
 * 仅待接单状态的工单可派单，且目标师傅须审核通过、未被禁用。
 */
@Data
@Schema(description = "派单入参")
public class AssignOrderRequestVO {

    /** 对应 maintenance_worker.id，不是 sys_user.id。 */
    @NotNull(message = "请选择维修师傅")
    @Schema(description = "师傅 ID（maintenance_worker.id）")
    private Long workerId;

    /** 不传时服务端自动生成「派单给 {工号}」作为流转日志备注。 */
    @Size(max = 200, message = "派单备注不能超过 200 字")
    @Schema(description = "派单备注")
    private String remark;
}