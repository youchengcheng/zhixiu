package com.ycc.zhixiucloud.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 业主注册入参（POST /api/auth/register/owner），提交方为未登录的访客。
 * 注册成功后账号状态正常、角色为业主，可直接登录；confirmPassword 仅用于
 * 服务端做两次密码一致性校验，不落库。
 */
@Data
@Schema(description = "业主注册入参")
public class OwnerRegisterRequestVO {

    @NotBlank(message = "登录名不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9_]{4,20}$", message = "登录名需 4-20 位字母、数字或下划线")
    @Schema(description = "登录名", example = "owner01")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度需 6-20 位")
    @Schema(description = "密码", example = "123456")
    private String password;

    /** 必须与 password 一致，仅参与校验、不持久化。 */
    @NotBlank(message = "确认密码不能为空")
    @Schema(description = "确认密码")
    private String confirmPassword;

    @Size(max = 50, message = "真实姓名不能超过 50 字")
    @Schema(description = "真实姓名")
    private String realName;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Schema(description = "手机号")
    private String phone;
}
