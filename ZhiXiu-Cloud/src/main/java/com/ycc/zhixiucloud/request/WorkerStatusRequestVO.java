package com.ycc.zhixiucloud.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 启用 / 禁用师傅入参（PUT /api/admin/worker/{id}/status），提交方为管理员。
 * 管理员仅可在 1 在线 / 4 禁用之间切换，不能代替师傅设置为离线或忙碌。
 */
@Data
@Schema(description = "师傅启停入参")
public class WorkerStatusRequestVO {

    /** 仅允许 1 或 4；禁用后该师傅无法接单、抢单。 */
    @NotNull(message = "请选择师傅状态")
    @Schema(description = "1 在线 4 禁用")
    private Integer workStatus;
}