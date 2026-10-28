package com.ycc.zhixiucloud.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 暂停工单入参（POST /api/worker/order/{id}/pause），提交方为当前接单的维修师傅。
 * 暂停后工单进入 PAUSED 状态，后续需调用 /resume 恢复处理。
 */
@Data
@Schema(description = "暂停工单入参")
public class PauseOrderRequestVO {

    @NotBlank(message = "暂停原因不能为空")
    @Size(max = 200, message = "暂停原因不能超过 200 字")
    @Schema(description = "暂停原因，如缺配件")
    private String remark;
}