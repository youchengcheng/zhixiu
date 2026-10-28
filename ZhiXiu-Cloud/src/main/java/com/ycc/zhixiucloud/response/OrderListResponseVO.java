package com.ycc.zhixiucloud.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单列表行：业主（GET /api/owner/order/list）、师傅（GET /api/worker/order/pending、/list）、
 * 管理员（GET /api/admin/order/list）三端共用，字段为三端并集，前端按列取用。
 * 未分配师傅时 workerId、workerName 为 null。
 */
@Data
@Builder
@Schema(description = "工单列表行")
public class OrderListResponseVO {

    @Schema(description = "工单 ID")
    private Long id;

    @Schema(description = "工单号")
    private String orderNo;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "报修类型 ID")
    private Long categoryId;

    @Schema(description = "报修类型名称")
    private String categoryName;

    /** 1低 / 2中 / 3高 / 4紧急，数值越大越紧急 */
    @Schema(description = "紧急程度")
    private Integer urgency;

    /** 由 urgency 翻译得到的中文展示文本 */
    @Schema(description = "紧急程度文本")
    private String urgencyText;

    /** 取值见 OrderStatusEnum：PENDING/ASSIGNED/ACCEPTED/GOING/PAUSED/TRANSFER_PENDING/SUBMITTED/COMPLETED/CLOSED/CANCELED */
    @Schema(description = "状态编码")
    private String status;

    @Schema(description = "状态文本")
    private String statusText;

    @Schema(description = "来源：MANUAL / AI")
    private String source;

    /** 1本户 / 2本层 / 3整栋 */
    @Schema(description = "影响范围")
    private Integer affectScope;

    /** 多选，逗号分隔的中文标识：涉电 / 涉水 / 涉气 / 高空；无隐患时为空 */
    @Schema(description = "安全隐患标志")
    private String safetyFlag;

    @Schema(description = "报修房产地址")
    private String fullAddress;

    @Schema(description = "业主 ID")
    private Long ownerId;

    @Schema(description = "业主姓名")
    private String ownerName;

    @Schema(description = "联系电话")
    private String contactPhone;

    /** 当前处理师傅；未派单 / 未接单时为 null */
    @Schema(description = "当前处理师傅 ID")
    private Long workerId;

    /** 关联查询字段，随 workerId 为空时为 null */
    @Schema(description = "当前处理师傅姓名")
    private String workerName;

    @Schema(description = "响应超时点")
    private LocalDateTime slaResponseDeadline;

    @Schema(description = "处理超时点")
    private LocalDateTime slaHandleDeadline;

    @Schema(description = "0未升级 1已升级")
    private Integer slaEscalated;

    /** 由服务层按当前时间与所处阶段时限实时计算，非数据库字段 */
    @Schema(description = "是否已超出当前阶段时限")
    private Boolean slaOverdue;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "接单时间")
    private LocalDateTime acceptedTime;

    @Schema(description = "提交完工时间")
    private LocalDateTime submittedTime;
}