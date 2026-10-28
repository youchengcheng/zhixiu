package com.ycc.zhixiucloud.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 业主列表查询入参（GET /api/admin/owner/list），提交方为管理员。
 * status 与 keyword 均为可选条件，同时传入时按 AND 组合，结果按注册时间倒序；
 * 分页参数继承自 PageRequestVO。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "业主列表查询入参")
public class OwnerQueryRequestVO extends PageRequestVO {

    @Schema(description = "账号状态：0禁用 1正常")
    private Integer status;

    /** 模糊匹配；实际同时匹配真实姓名、手机号与登录名。 */
    @Schema(description = "关键字：姓名 / 手机号")
    private String keyword;
}