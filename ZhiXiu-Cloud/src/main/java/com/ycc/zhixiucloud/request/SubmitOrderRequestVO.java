package com.ycc.zhixiucloud.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 提交完工入参（POST /api/worker/order/{id}/submit），提交方为当前接单的维修师傅。
 * 提交后工单由处理中推进到已提交完工，等待业主确认或系统自动确认。
 */
@Data
@Schema(description = "提交完工入参")
public class SubmitOrderRequestVO {

    @NotBlank(message = "处理过程记录不能为空")
    @Size(max = 1000, message = "处理过程记录不能超过 1000 字")
    @Schema(description = "处理过程记录")
    private String processNote;

    @NotBlank(message = "请上传完工照片")
    @Size(max = 1000, message = "完工照片地址不能超过 1000 字")
    @Schema(description = "完工照片 URL，逗号分隔")
    private String finishImages;
}