package com.ycc.zhixiucloud.common.enums;

import lombok.Getter;

/**
 * 师傅入驻审核状态枚举。
 *
 * <p>对应师傅档案的审核状态字段，管理员审核通过后师傅才可接单；
 * 业务展示使用 name，持久化使用 code。</p>
 */
@Getter
public enum AuditStatusEnum {

    /** 0-待审核：师傅已提交资料，等待物业管理员处理 */
    PENDING(0, "待审核"),
    /** 1-已通过：资料审核通过，师傅可进入可接单状态 */
    PASSED(1, "已通过"),
    /** 2-已驳回：资料不符合要求，需修改后重新提交 */
    REJECTED(2, "已驳回");

    /** 落库编码：0/1/2 */
    private final int code;

    /** 前端展示文案 */
    private final String name;

    AuditStatusEnum(int code, String name) {
        // 落库编码
        this.code = code;
        // 展示文案
        this.name = name;
    }

    /**
     * 根据落库编码查找审核状态。
     *
     * @param code 编码，允许为 null
     * @return 匹配的枚举；入参为 null 或编码非法时返回 null
     */
    public static AuditStatusEnum of(Integer code) {
        // 编码为空直接返回 null
        if (code == null) {
            // 编码为空时返回空
            return null;
        }
        // 遍历所有枚举项匹配编码
        for (AuditStatusEnum item : values()) {
            // 编码一致即命中
            if (item.code == code) {
                // 返回匹配到的枚举
                return item;
            }
        }
        // 未匹配到返回 null
        return null;
    }
}