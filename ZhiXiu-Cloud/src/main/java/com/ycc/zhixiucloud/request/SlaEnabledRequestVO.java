package com.ycc.zhixiucloud.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * SLA 启用 / 禁用入参（PUT /api/admin/sla/{id}/enabled），提交方为管理员。
 * 仅允许 0 / 1，禁用后的规则不参与 SLA 匹配与超时计算。
 */
@Data
@Schema(description = "SLA 启停入参")
public class SlaEnabledRequestVO {

    @NotNull(message = "请选择启用状态")
    @Schema(description = "0 禁用 1 启用")
    private Integer enabled;
}