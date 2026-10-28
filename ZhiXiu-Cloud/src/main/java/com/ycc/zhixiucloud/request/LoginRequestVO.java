package com.ycc.zhixiucloud.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录入参（POST /api/auth/login），适用于业主、维修师傅、管理员等全部角色。
 * 登录名在当前系统内唯一，角色由服务端根据账号信息返回，无需前端指定。
 */
@Data
@Schema(description = "登录入参")
public class LoginRequestVO {

    @NotBlank(message = "登录名不能为空")
    @Schema(description = "登录名")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Schema(description = "密码")
    private String password;
}
