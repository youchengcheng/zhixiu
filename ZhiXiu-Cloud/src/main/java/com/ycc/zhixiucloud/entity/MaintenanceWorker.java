package com.ycc.zhixiucloud.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 维修师傅扩展表 maintenance_worker。
 * <p>
 * 在 user 表基础上保存维修师傅的业务属性（工号、技能、审核状态、工作状态、
 * 接单统计与当前负载），用于审核、派单和师傅排行等场景。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("maintenance_worker")
public class MaintenanceWorker extends BaseEntity {

    /** 关联 user.id */
    private Long userId;

    /** 工号（业务唯一） */
    private String workerNo;

    /** 技能标签，逗号分隔（对应报修类型/专长） */
    private String skillTags;

    /** 身份证号，用于实名审核 */
    private String idCard;

    /** 工作状态：0=审核中 1=在线 2=离线 3=忙碌 4=禁用 */
    private Integer workStatus;

    /** 审核状态：0=待审 1=通过 2=驳回 */
    private Integer auditStatus;

    /** 审核意见或驳回原因 */
    private String auditRemark;

    /** 累计接单数 */
    private Integer totalOrders;

    /** 累计完成单数 */
    private Integer finishOrders;

    /** 平均评分（由评价汇总计算） */
    private BigDecimal avgScore;

    /** 当前进行中工单数，用于派单负载均衡 */
    private Integer currentLoad;
}
