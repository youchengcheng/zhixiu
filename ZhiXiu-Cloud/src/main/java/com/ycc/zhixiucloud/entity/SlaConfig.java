package com.ycc.zhixiucloud.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * SLA 配置表 sla_config：字段为 NULL 表示该维度不参与匹配。
 * <p>
 * 按报修类型、紧急程度、安全隐患标志、影响范围等维度配置响应/处理超时阈值
 * 与升级策略，工单创建时据此计算 SLA 截止时间。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sla_config")
public class SlaConfig extends BaseEntity {

    /** 报修类型 ID，NULL=全局 */
    private Long categoryId;

    /** 紧急程度，NULL=所有 */
    private Integer urgency;

    /** 安全隐患标志，NULL=所有 */
    private String safetyFlag;

    /** 影响范围，NULL=所有 */
    private Integer affectScope;

    /** 响应超时(分钟) */
    private Integer responseTimeout;

    /** 处理超时(分钟) */
    private Integer handleTimeout;

    /** 升级通知角色：ADMIN */
    private String escalateTo;

    /** 派单优先级权重 */
    private Integer priorityWeight;

    /** 0禁用 1启用 */
    private Integer enabled;
}
