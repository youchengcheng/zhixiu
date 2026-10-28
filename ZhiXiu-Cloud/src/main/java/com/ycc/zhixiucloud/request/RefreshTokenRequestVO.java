package com.ycc.zhixiucloud.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 刷新 Token 入参（POST /api/auth/refresh），提交方为持有有效刷新令牌的登录用户。
 * 用于访问令牌过期后换取新的访问令牌，无需重新输入账号密码。
 */
@Data
@Schema(description = "刷新 Token 入参")
public class RefreshTokenRequestVO {

    @NotBlank(message = "refreshToken 不能为空")
    @Schema(description = "刷新令牌")
    private String refreshToken;
}
