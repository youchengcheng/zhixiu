package com.ycc.zhixiucloud.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改个人信息入参（PUT /api/user/profile），提交方为已登录用户（业主 / 师傅 / 管理员）。
 * 采用部分更新语义：字段为空或空白字符串时保持原值；用户名、手机号、角色不可在此修改。
 */
@Data
@Schema(description = "修改个人信息入参")
public class UpdateProfileRequestVO {

    @Size(max = 50, message = "真实姓名不能超过 50 字")
    @Schema(description = "真实姓名")
    private String realName;

    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱不能超过 100 位")
    @Schema(description = "邮箱")
    private String email;

    /** 0 未知 1 男 2 女；不传则保持原性别。 */
    @Min(value = 0, message = "性别取值 0/1/2")
    @Max(value = 2, message = "性别取值 0/1/2")
    @Schema(description = "0未知 1男 2女")
    private Integer gender;

    @Size(max = 255, message = "头像地址不能超过 255 位")
    @Schema(description = "头像 URL")
    private String avatar;
}
