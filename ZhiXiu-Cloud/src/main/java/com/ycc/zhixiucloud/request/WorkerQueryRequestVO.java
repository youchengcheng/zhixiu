package com.ycc.zhixiucloud.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 师傅列表查询入参（GET /api/admin/worker/list），提交方为管理员。
 * auditStatus、workStatus、keyword 均为可选条件，同时传入时按 AND 组合，
 * 结果按入驻时间倒序；分页参数继承自 PageRequestVO。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "师傅列表查询入参")
public class WorkerQueryRequestVO extends PageRequestVO {

    /** 0 待审 1 通过 2 驳回；不传表示不限审核状态。 */
    @Schema(description = "审核状态：0待审 1通过 2驳回")
    private Integer auditStatus;

    /** 取值 0-4，对应 WorkStatusEnum；不传表示不限工作状态。 */
    @Schema(description = "师傅状态：0审核中 1在线 2离线 3忙碌 4禁用")
    private Integer workStatus;

    @Schema(description = "关键字：姓名 / 工号 / 手机号")
    private String keyword;
}