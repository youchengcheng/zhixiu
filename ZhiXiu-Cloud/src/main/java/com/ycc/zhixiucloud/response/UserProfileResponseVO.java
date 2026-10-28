package com.ycc.zhixiucloud.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 个人信息出参（GET /api/user/profile），业主 / 师傅 / 管理员三角色通用。
 * 仅返回基础资料，不含密码等敏感字段；roleCode 用于前端判断可访问的功能模块。
 */
@Data
@Builder
@Schema(description = "个人信息")
public class UserProfileResponseVO {

    @Schema(description = "用户 ID")
    private Long id;

    @Schema(description = "登录名")
    private String username;

    @Schema(description = "真实姓名")
    private String realName;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "头像 URL")
    private String avatar;

    @Schema(description = "0未知 1男 2女")
    private Integer gender;

    /** 角色编码：OWNER / WORKER / ADMIN */
    @Schema(description = "角色编码")
    private String roleCode;

    @Schema(description = "注册时间")
    private LocalDateTime createTime;

    //上次登录时间
    @Schema(description = "最近登录时间")
    private LocalDateTime lastLoginTime;
}
