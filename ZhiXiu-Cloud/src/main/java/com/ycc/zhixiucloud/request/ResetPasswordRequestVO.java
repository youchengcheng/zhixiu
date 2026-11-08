package com.ycc.zhixiucloud.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员重置业主或师傅密码入参。
 */
@Data
@Schema(description = "管理员重置密码入参")
public class ResetPasswordRequestVO {

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 20, message = "新密码长度需 6-20 位")
    @Schema(description = "新密码")
    private String newPassword;

    @NotBlank(message = "确认密码不能为空")
    @Schema(description = "确认新密码")
    private String confirmPassword;
}
