package com.ycc.zhixiucloud.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理员新增 / 修改房产入参（POST /api/admin/property、PUT /api/admin/property/{id}）。
 * 提交方为管理员；房产字段与业主端一致，额外指定归属业主，用于物业代业主登记房产。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "房产管理入参")
public class AdminPropertySaveRequestVO extends PropertySaveRequestVO {

    /** 对应 sys_user.id（业主账号主键），不是房产主键；服务端会校验该账号必须是业主角色。 */
    @NotNull(message = "请选择归属业主")
    @Schema(description = "归属业主 user_id")
    private Long ownerId;
}