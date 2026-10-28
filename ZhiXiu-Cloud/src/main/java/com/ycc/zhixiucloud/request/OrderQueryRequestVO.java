package com.ycc.zhixiucloud.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工单列表查询入参基类，业主端与师傅端的工单列表共用（待接单池也复用本类做分页），
 * 管理端在此基础上扩展 AdminOrderQueryRequestVO。服务端会按当前登录人附加数据范围：
 * 业主仅本人报修工单，师傅为派给本人或本人接单的工单。status、categoryId、keyword
 * 为可选筛选条件，同时传入时按 AND 组合；列表默认按创建时间倒序，
 * 分页参数继承自 PageRequestVO。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "工单列表查询入参")
public class OrderQueryRequestVO extends PageRequestVO {

    /**
     * 精确匹配工单状态码，取值见 OrderStatusEnum：
     * PENDING 待接单、ASSIGNED 已派单、ACCEPTED 已接单、GOING 处理中、PAUSED 已暂停、
     * TRANSFER_PENDING 转单申请中、SUBMITTED 已提交完工、COMPLETED 已完工、
     * CLOSED 已关闭、CANCELED 已取消；不传表示不过滤状态。
     */
    @Schema(description = "工单状态，见 OrderStatusEnum")
    private String status;

    @Schema(description = "关键字：标题 / 工单号")
    private String keyword;

    @Schema(description = "报修类型 ID")
    private Long categoryId;
}