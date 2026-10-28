package com.ycc.zhixiucloud.common.enums;

import lombok.Getter;

/**
 * 师傅工作状态枚举。
 *
 * <p>记录师傅当前是否可接单：审核通过后才能切换到 ONLINE；BUSY 表示手上有单
 * 但仍可继续接单；OFFLINE、DISABLED 不参与派单。业务展示使用 name，持久化使用 code。</p>
 */
@Getter
public enum WorkStatusEnum {

    /** 0-审核中：入驻资料待管理员审核，不能接单 */
    AUDITING(0, "审核中"),
    /** 1-在线：可被派单、可抢单 */
    ONLINE(1, "在线"),
    /** 2-离线：主动下线或心跳超时，不参与派单 */
    OFFLINE(2, "离线"),
    /** 3-忙碌：手上有进行中的工单，但仍允许继续接单 */
    BUSY(3, "忙碌"),
    /** 4-禁用：被管理员禁用，不能接单 */
    DISABLED(4, "禁用");

    /** 落库编码：0/1/2/3/4 */
    private final int code;

    /** 前端展示文案 */
    private final String name;

    WorkStatusEnum(int code, String name) {
        // 落库编码
        this.code = code;
        // 展示文案
        this.name = name;
    }

    /**
     * 根据编码查找师傅工作状态。
     *
     * @param code 编码，允许为 null
     * @return 匹配的枚举；入参为 null 或编码非法时返回 null
     */
    public static WorkStatusEnum of(Integer code) {
        // 编码为空直接返回 null
        if (code == null) {
            // 编码为空时返回空
            return null;
        }
        // 遍历所有枚举项匹配编码
        for (WorkStatusEnum item : values()) {
            // 编码一致即命中
            if (item.code == code) {
                // 返回匹配到的枚举
                return item;
            }
        }
        // 未匹配到返回 null
        return null;
    }

    /**
     * 判断当前状态是否允许接单。
     *
     * <p>在线和忙碌都表示师傅仍在服务中，可被派单或抢单；审核中、离线、
     * 禁用状态不参与派单。</p>
     *
     * @return 可接单返回 true
     */
    public boolean canTakeOrder() {
        // 在线或忙碌状态均可继续接单
        return this == ONLINE || this == BUSY;
    }
}