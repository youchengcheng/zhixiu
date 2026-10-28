package com.ycc.zhixiucloud.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 启用 / 禁用业主入参（PUT /api/admin/owner/{id}/status），提交方为管理员。
 * 禁用后该业主账号在登录时会被拒绝，重新启用后方可恢复登录。
 */
@Data
@Schema(description = "业主人启停入参")
public class OwnerStatusRequestVO {

    @NotNull(message = "请选择账号状态")
    @Schema(description = "0 禁用 1 正常")
    private Integer status;
}