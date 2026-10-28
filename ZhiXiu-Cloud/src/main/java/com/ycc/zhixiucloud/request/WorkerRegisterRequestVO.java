package com.ycc.zhixiucloud.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 师傅注册入参（POST /api/auth/register/worker），提交方为未登录的维修师傅申请人。
 * 注册成功后 auditStatus 为待审，需管理员审核通过后才能接单；
 * confirmPassword 仅用于服务端做两次密码一致性校验，不落库。
 */
@Data
@Schema(description = "师傅注册入参")
public class WorkerRegisterRequestVO {

    @NotBlank(message = "登录名不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9_]{4,20}$", message = "登录名需 4-20 位字母、数字或下划线")
    @Schema(description = "登录名", example = "worker01")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度需 6-20 位")
    @Schema(description = "密码")
    private String password;

    /** 必须与 password 一致，仅参与校验、不持久化。 */
    @NotBlank(message = "确认密码不能为空")
    @Schema(description = "确认密码")
    private String confirmPassword;

    @NotBlank(message = "真实姓名不能为空")
    @Size(max = 50, message = "真实姓名不能超过 50 字")
    @Schema(description = "真实姓名")
    private String realName;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Schema(description = "手机号")
    private String phone;

    @NotBlank(message = "工号不能为空")
    @Size(max = 50, message = "工号不能超过 50 位")
    @Schema(description = "工号（唯一）")
    private String workerNo;

    @NotBlank(message = "技能标签不能为空")
    @Size(max = 255, message = "技能标签不能超过 255 位")
    @Schema(description = "技能标签，逗号分隔", example = "水管维修,电器维修")
    private String skillTags;

    /** 可为空；非空时须为 15 位或 18 位（末位可为 X/x）身份证号。 */
    @Pattern(regexp = "^$|(^\\d{15}$)|(^\\d{17}([0-9]|X|x)$)", message = "身份证号格式不正确")
    @Schema(description = "身份证号")
    private String idCard;
}
