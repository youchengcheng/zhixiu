package com.ycc.zhixiucloud.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 反馈列表查询入参（GET /api/admin/feedback/list），提交方为管理员。
 * status 与 type 均为可选筛选条件，同时传入时按 AND 组合，结果按提交时间倒序；
 * 分页参数继承自 PageRequestVO。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "反馈列表查询入参")
public class FeedbackQueryRequestVO extends PageRequestVO {

    @Schema(description = "处理状态：0待处理 1处理中 2已处理 3关闭")
    private Integer status;

    @Schema(description = "类型：BUG / SUGGESTION / COMPLAINT")
    private String type;
}
