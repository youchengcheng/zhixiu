package com.ycc.zhixiucloud.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 登录 / 刷新 Token 出参（POST /api/auth/login、POST /api/auth/refresh），业主 / 师傅 / 管理员通用。
 * 两个接口都会返回新的双令牌与当前用户信息；userInfo 中师傅专属字段仅师傅角色有值。
 */
@Data
@Builder
@Schema(description = "登录结果")
public class LoginResponseVO {

    @Schema(description = "访问令牌")
    private String token;

    @Schema(description = "刷新令牌")
    private String refreshToken;

    /** 访问令牌有效期，单位秒；不含刷新令牌的有效期 */
    @Schema(description = "访问令牌有效期（秒）")
    private long expiresIn;

    /** 嵌套的当前登录用户信息，结构见 UserInfoResponseVO */
    @Schema(description = "当前用户信息")
    private UserInfoResponseVO userInfo;
}
