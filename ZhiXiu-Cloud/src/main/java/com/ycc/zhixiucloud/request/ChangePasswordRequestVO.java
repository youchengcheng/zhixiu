package com.ycc.zhixiucloud.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改密码入参（PUT /api/user/password），提交方为已登录用户（业主 / 师傅 / 管理员）。
 * 服务端会校验原密码、两次新密码一致，以及新密码不能与原密码相同。
 */
@Data
@Schema(description = "修改密码入参")
public class ChangePasswordRequestVO {

    @NotBlank(message = "原密码不能为空")
    @Schema(description = "原密码")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 20, message = "新密码长度需 6-20 位")
    @Schema(description = "新密码")
    private String newPassword;

    /** 必须与 newPassword 完全一致，由服务端做一致性校验。 */
    @NotBlank(message = "确认密码不能为空")
    @Schema(description = "确认新密码")
    private String confirmPassword;
}
