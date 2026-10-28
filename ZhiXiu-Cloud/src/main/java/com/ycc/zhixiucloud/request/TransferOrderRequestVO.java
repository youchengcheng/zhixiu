package com.ycc.zhixiucloud.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 申请转单入参（POST /api/worker/order/{id}/transfer），提交方为当前接单的维修师傅。
 * 申请后工单进入 TRANSFER_PENDING，需由管理员同意改派或驳回后回到处理中。
 */
@Data
@Schema(description = "申请转单入参")
public class TransferOrderRequestVO {

    @NotBlank(message = "转单原因不能为空")
    @Size(max = 200, message = "转单原因不能超过 200 字")
    @Schema(description = "转单原因")
    private String transferReason;
}