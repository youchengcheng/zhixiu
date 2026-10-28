package com.ycc.zhixiucloud.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工单流转日志表 work_order_log：每次状态变更由状态机写入一条。
 * <p>
 * 记录工单状态变更的操作人、动作、前后状态与备注，形成完整的流转轨迹，
 * 供工单详情时间线和审计追溯使用。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("work_order_log")
public class WorkOrderLog extends BaseEntity {

    /** 所属工单 ID（work_order.id） */
    private Long orderId;

    /** 操作人 ID（user.id），系统自动操作时可为空 */
    private Long operatorId;

    /** 操作人类型：OWNER=业主 / WORKER=师傅 / ADMIN=管理员 / SYSTEM=系统 */
    private String operatorType;

    /** 流转动作：CREATE / ASSIGN / ACCEPT / ARRIVE / PAUSE / RESUME / TRANSFER / SYSTEM_SUBMIT / COMPLETE / CANCEL */
    private String action;

    /** 变更前状态，创建工单时为空 */
    private String fromStatus;

    /** 变更后状态 */
    private String toStatus;

    /** 流转备注/说明 */
    private String remark;
}
