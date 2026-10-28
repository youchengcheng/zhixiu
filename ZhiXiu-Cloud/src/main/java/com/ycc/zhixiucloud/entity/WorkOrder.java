package com.ycc.zhixiucloud.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 工单表 work_order（核心）。
 * <p>
 * 报修业务主表，记录工单从创建、派单、接单、上门、提交完工到关闭的全流程信息，
 * 同时保存 SLA 超时判定结果与 AI 草稿来源。status 只允许经状态机变更；
 * version 为乐观锁字段，防止并发状态更新冲突。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("work_order")
public class WorkOrder extends BaseEntity {

    /** 工单编号（业务唯一，用于展示与检索） */
    private String orderNo;

    /** 报修房产 ID（property.id） */
    private Long propertyId;

    /** 业主 user_id */
    private Long ownerId;

    /** 报修类型 ID（repair_category.id，二级类型） */
    private Long categoryId;

    /** 工单标题/简要描述 */
    private String title;

    /** 故障现象描述 */
    private String description;

    /** 故障持续时间，如「约1天」 */
    private String faultDuration;

    /** 影响范围：1=本户 2=本层 3=整栋 */
    private Integer affectScope;

    /** 安全隐患标志，逗号分隔：涉电/涉水/涉气/高空 */
    private String safetyFlag;

    /** 设备品牌型号 */
    private String deviceInfo;

    /** 期望上门时段 */
    private String expectedVisitTime;

    /** 联系电话 */
    private String contactPhone;

    /** 紧急程度：1=低 2=中 3=高 4=紧急 */
    private Integer urgency;

    /** 报修图片 URL，逗号分隔 */
    private String images;

    /** 工单状态，取值见 OrderStatusEnum */
    private String status;

    /** 工单来源，见 OrderSourceEnum：MANUAL=手动报修 / AI=AI 草稿转单 */
    private String source;

    /** 派单给谁（maintenance_worker.id） */
    private Long assignedWorkerId;

    /** 实际接单的师傅（maintenance_worker.id，转派后可能与派单对象不同） */
    private Long acceptedWorkerId;

    /** 派单时间 */
    private LocalDateTime assignedTime;

    /** 接单时间 */
    private LocalDateTime acceptedTime;

    /** 到达现场时间 */
    private LocalDateTime arrivedTime;

    /** 提交完工时间 */
    private LocalDateTime submittedTime;

    /** 完工确认时间 */
    private LocalDateTime completedTime;

    /** 工单关闭时间 */
    private LocalDateTime closedTime;

    /** 取消原因，非取消状态时为空 */
    private String cancelReason;

    /** 转派原因，未转派时为空 */
    private String transferReason;

    /** 处理过程记录 */
    private String processNote;

    /** 完工照片 URL，逗号分隔 */
    private String finishImages;

    /** SLA 响应超时点（创建时按配置计算） */
    private LocalDateTime slaResponseDeadline;

    /** SLA 处理超时点（创建时按配置计算） */
    private LocalDateTime slaHandleDeadline;

    /** SLA 升级标记：0=未升级 1=已升级 */
    private Integer slaEscalated;

    /** 响应是否超时：0=否 1=是 */
    private Integer slaResponseBreached;

    /** 处理是否超时：0=否 1=是 */
    private Integer slaHandleBreached;

    /** 来源 AI 草稿 ID（ai_chat_message.id），手动创建时为空 */
    private Long aiDraftId;

    /** 乐观锁版本号，插入时自动填充 */
    @Version
    @TableField(fill = FieldFill.INSERT)
    private Integer version;
}
