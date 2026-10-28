package com.ycc.zhixiucloud.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理端工单列表查询入参（GET /api/admin/order/list），提交方为管理员。
 * 在业主 / 师傅通用查询条件上增加紧急程度、SLA 升级筛选；
 * 各条件均可选，传值时按 AND 组合，分页参数继承自 PageRequestVO。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "管理端工单列表查询入参")
public class AdminOrderQueryRequestVO extends OrderQueryRequestVO {

    /** 精确匹配；不传表示不限紧急程度。 */
    @Schema(description = "紧急程度：1低 2中 3高 4紧急")
    private Integer urgency;

    /** 精确匹配 SLA 升级标记：1 只看已升级工单，0 只看未升级工单，不传则不限。 */
    @Schema(description = "1 只看 SLA 升级工单")
    private Integer escalated;
}