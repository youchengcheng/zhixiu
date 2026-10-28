package com.ycc.zhixiucloud.common.enums;

import lombok.Getter;

/**
 * 操作人类型枚举。
 *
 * <p>用于 work_order_log.operator_type 与 operation_log 的操作人角色字段，
 * 区分一条记录是业主、师傅、管理员还是系统自动任务产生的。</p>
 */
@Getter
public enum OperatorTypeEnum {

    /** 业主：提交报修、确认完工、评价等操作 */
    OWNER("OWNER"),
    /** 维修师傅：接单、到场、暂停、提交完工等操作 */
    WORKER("WORKER"),
    /** 物业管理员：派单、审核、后台管理等操作 */
    ADMIN("ADMIN"),
    /** 系统：定时任务、MQ 消费者、超时自动处理等无登录用户的场景 */
    SYSTEM("SYSTEM");

    /** 落库编码，与角色编码保持一致 */
    private final String code;

    OperatorTypeEnum(String code) {
        // 落库编码，与角色编码一致
        this.code = code;
    }
}