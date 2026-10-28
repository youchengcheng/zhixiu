package com.ycc.zhixiucloud.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 提交报修出参：业主手动报修（POST /api/owner/order）与 AI 草稿确认转工单
 * （POST /api/ai/session/{id}/confirm）共用，仅业主角色使用。
 * orderId 用于后续查询详情、评价；orderNo 为对外展示的工单号。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "提交报修结果")
public class OrderCreateResponseVO {

    @Schema(description = "工单 ID")
    private Long orderId;

    @Schema(description = "工单号")
    private String orderNo;
}