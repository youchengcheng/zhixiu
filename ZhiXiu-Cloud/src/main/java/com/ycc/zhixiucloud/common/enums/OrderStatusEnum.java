package com.ycc.zhixiucloud.common.enums;

import lombok.Getter;

/**
 * 工单状态枚举。
 *
 * <p>对应 work_order.status 字段，状态流转必须经 WorkOrderStateMachine，
 * 禁止在业务代码中直接更新 work_order.status。业务展示使用 name，持久化使用 code。</p>
 */
@Getter
public enum OrderStatusEnum {

    /** 待接单：工单已创建，等待派单或师傅抢单 */
    PENDING("PENDING", "待接单"),
    /** 已派单：管理员已指定师傅，等待师傅接单 */
    ASSIGNED("ASSIGNED", "已派单"),
    /** 已接单：师傅确认接单，尚未到场 */
    ACCEPTED("ACCEPTED", "已接单"),
    /** 处理中：师傅已到场并开始维修 */
    GOING("GOING", "处理中"),
    /** 已暂停：维修因缺料、业主不在等原因临时中断 */
    PAUSED("PAUSED", "已暂停"),
    /** 转单申请中：师傅申请转单，等待管理员处理 */
    TRANSFER_PENDING("TRANSFER_PENDING", "转单申请中"),
    /** 已提交完工：师傅提交维修结果，等待业主确认 */
    SUBMITTED("SUBMITTED", "已提交完工"),
    /** 已完工：业主已确认（或超时自动确认），等待评价 */
    COMPLETED("COMPLETED", "已完工"),
    /** 已关闭：业主已评价，工单流程结束 */
    CLOSED("CLOSED", "已关闭"),
    /** 已取消：工单在流程中被取消 */
    CANCELED("CANCELED", "已取消");

    /** 落库编码 */
    private final String code;

    /** 前端展示文案 */
    private final String name;

    OrderStatusEnum(String code, String name) {
        // 落库编码
        this.code = code;
        // 展示文案
        this.name = name;
    }

    /**
     * 根据编码查找工单状态。
     *
     * @param code 状态编码，允许为 null
     * @return 匹配的枚举；入参为 null 或编码非法时返回 null
     */
    public static OrderStatusEnum of(String code) {
        // 遍历所有枚举项匹配编码
        for (OrderStatusEnum item : values()) {
            // 编码一致即命中
            if (item.code.equals(code)) {
                // 返回匹配到的枚举
                return item;
            }
        }
        // 未匹配到返回 null
        return null;
    }

    /**
     * 根据编码取中文状态名，用于日志与前端展示。
     *
     * @param code 状态编码
     * @return 中文状态名；编码无法识别时原样返回 code
     */
    public static String nameOf(String code) {
        // 先按编码查找枚举
        OrderStatusEnum item = of(code);
        // 查不到时回退原编码
        return item == null ? code : item.name;
    }

    /**
     * 判断是否为终态。
     *
     * <p>终态的工单不再接受任何状态流转与 SLA 超时处理。</p>
     *
     * @return CLOSED 或 CANCELED 时返回 true
     */
    public boolean isFinal() {
        // 已关闭或已取消均为终态
        return this == CLOSED || this == CANCELED;
    }
}