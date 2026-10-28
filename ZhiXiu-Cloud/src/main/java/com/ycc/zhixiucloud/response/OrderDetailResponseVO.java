package com.ycc.zhixiucloud.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工单详情：业主（GET /api/owner/order/{id}）、师傅（GET /api/worker/order/{id}）、
 * 管理员（GET /api/admin/order/{id}）三端共用同一结构。
 * 未分配师傅时 worker 为 null；未评价时 evaluation 为 null；图片字段均为 URL 列表。
 */
@Data
@Builder
@Schema(description = "工单详情")
public class OrderDetailResponseVO {

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

    @Schema(description = "故障现象描述")
    private String description;

    @Schema(description = "故障持续时间")
    private String faultDuration;

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

    /** 由 affectScope 翻译得到的中文展示文本 */
    @Schema(description = "影响范围文本")
    private String affectScopeText;

    /** 多选，逗号分隔的中文标识：涉电 / 涉水 / 涉气 / 高空；无隐患时为空 */
    @Schema(description = "安全隐患标志")
    private String safetyFlag;

    @Schema(description = "设备品牌型号")
    private String deviceInfo;

    @Schema(description = "期望上门时段")
    private String expectedVisitTime;

    @Schema(description = "联系电话")
    private String contactPhone;

    @Schema(description = "报修房产 ID")
    private Long propertyId;

    @Schema(description = "报修房产地址")
    private String fullAddress;

    @Schema(description = "业主 ID")
    private Long ownerId;

    @Schema(description = "业主姓名")
    private String ownerName;



    /** 故障图片 URL 列表，由库中逗号分隔字符串拆分而来，无图为空列表 */
    @Schema(description = "故障图片")
    private List<String> images;

    /** 完工照片 URL 列表，师傅提交完工时上传，未完成为空列表 */
    @Schema(description = "完工照片")
    private List<String> finishImages;

    @Schema(description = "处理过程记录")
    private String processNote;

    /** 嵌套的维修师傅摘要；未派单 / 未接单时为 null */
    @Schema(description = "受理师傅；未接单时为 null")
    private OrderWorkerResponseVO worker;

    @Schema(description = "取消原因")
    private String cancelReason;

    @Schema(description = "转单原因")
    private String transferReason;

    @Schema(description = "响应超时点")
    private LocalDateTime slaResponseDeadline;

    @Schema(description = "处理超时点")
    private LocalDateTime slaHandleDeadline;

    @Schema(description = "0未升级 1已升级")
    private Integer slaEscalated;

    @Schema(description = "响应是否超时：0否 1是")
    private Integer slaResponseBreached;

    @Schema(description = "处理是否超时：0否 1是")
    private Integer slaHandleBreached;

    @Schema(description = "派单时间")
    private LocalDateTime assignedTime;

    @Schema(description = "接单时间")
    private LocalDateTime acceptedTime;

    @Schema(description = "到场时间")
    private LocalDateTime arrivedTime;

    @Schema(description = "提交完工时间")
    private LocalDateTime submittedTime;

    @Schema(description = "完工确认时间")
    private LocalDateTime completedTime;

    @Schema(description = "关闭时间")
    private LocalDateTime closedTime;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /** 嵌套的业主评价；工单未评价时为 null */
    @Schema(description = "评价信息；未评价时为 null")
    private OrderEvaluationResponseVO evaluation;
}