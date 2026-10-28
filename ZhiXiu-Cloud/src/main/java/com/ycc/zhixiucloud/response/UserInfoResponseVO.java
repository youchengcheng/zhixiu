package com.ycc.zhixiucloud.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 登录用户信息出参：登录（POST /api/auth/login）、刷新 Token（POST /api/auth/refresh）
 * 与获取当前用户（GET /api/auth/me）共用，业主 / 师傅 / 管理员通用。
 * 师傅专属字段（workerId、auditStatus、workerNo、skillTags、workStatus）对非师傅角色为 null。
 */
@Data
@Builder
@Schema(description = "登录用户信息")
public class UserInfoResponseVO {

    @Schema(description = "用户 ID")
    private Long userId;

    @Schema(description = "登录名")
    private String username;

    @Schema(description = "真实姓名")
    private String realName;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "0未知 1男 2女")
    private Integer gender;

    @Schema(description = "头像 URL")
    private String avatar;

    /** 角色编码：OWNER / WORKER / ADMIN，前端据此控制菜单与路由 */
    @Schema(description = "角色编码")
    private String roleCode;

    @Schema(description = "角色名称")
    private String roleName;

    @Schema(description = "师傅 ID（仅师傅角色）")
    private Long workerId;

    @Schema(description = "审核状态：0待审 1通过 2驳回（仅师傅角色）")
    private Integer auditStatus;

    @Schema(description = "工号（仅师傅角色）")
    private String workerNo;

    @Schema(description = "技能标签（仅师傅角色）")
    private String skillTags;

    @Schema(description = "工作状态：0审核中 1在线 2离线 3忙碌 4禁用（仅师傅角色）")
    private Integer workStatus;
}
