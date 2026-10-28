package com.ycc.zhixiucloud.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 师傅切换在线状态入参（POST /api/worker/status），提交方为维修师傅本人。
 * 仅允许在 1 在线 / 2 离线之间切换；忙碌、禁用等状态由系统或管理员维护。
 */
@Data
@Schema(description = "在线状态入参")
public class WorkStatusRequestVO {

    @NotNull(message = "请选择在线状态")
    @Schema(description = "1 在线 2 离线")
    private Integer workStatus;
}