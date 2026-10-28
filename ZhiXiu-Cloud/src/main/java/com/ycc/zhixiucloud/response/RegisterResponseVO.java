package com.ycc.zhixiucloud.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 注册出参（POST /api/auth/register/owner、POST /api/auth/register/worker）。
 * 业主注册后可直接登录；师傅注册后需管理员审核，此处仅返回新用户 ID。
 */
@Data
@AllArgsConstructor
@Schema(description = "注册结果")
public class RegisterResponseVO {

    @Schema(description = "新用户 ID")
    private Long userId;
}
