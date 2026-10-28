package com.ycc.zhixiucloud.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 业主列表行出参（GET /api/admin/owner/list，管理端），按注册时间倒序分页。
 * 仅包含业主角色用户；propertyCount 为该业主名下房产数量，无房产时为 0。
 */
@Data
@Builder
@Schema(description = "业主列表行")
public class OwnerListResponseVO {

    @Schema(description = "用户 ID")
    private Long id;

    @Schema(description = "登录名")
    private String username;

    @Schema(description = "姓名")
    private String realName;

    @Schema(description = "头像地址，未上传时为空")
    private String avatar;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "账号状态：0禁用 1正常")
    private Integer status;

    @Schema(description = "名下房产数")
    private Long propertyCount;

    /** 从注册至今从未登录时为 null */
    @Schema(description = "最近登录时间")
    private LocalDateTime lastLoginTime;

    @Schema(description = "注册时间")
    private LocalDateTime createTime;
}