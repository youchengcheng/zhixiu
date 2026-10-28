package com.ycc.zhixiucloud.common.enums;

import lombok.Getter;

/**
 * 紧急程度枚举。
 *
 * <p>用于工单优先级展示与待接单池排序，code 越大越紧急；业务展示使用 name，
 * 持久化使用 code。</p>
 */
@Getter
public enum UrgencyEnum {

    /** 1-低：不影响正常生活，可安排时间处理 */
    LOW(1, "低"),
    /** 2-中：影响部分使用体验，按常规顺序处理 */
    MEDIUM(2, "中"),
    /** 3-高：明显影响生活，需要优先安排 */
    HIGH(3, "高"),
    /** 4-紧急：存在安全风险或严重影响生活，需立即处理 */
    URGENT(4, "紧急");

    /** 落库编码：1/2/3/4，数值越大越紧急 */
    private final int code;

    /** 前端展示文案 */
    private final String name;

    UrgencyEnum(int code, String name) {
        // 落库编码
        this.code = code;
        // 展示文案
        this.name = name;
    }

    /**
     * 根据编码查找紧急程度。
     *
     * @param code 编码，允许为 null
     * @return 匹配的枚举；入参为 null 或编码非法时返回 null
     */
    public static UrgencyEnum of(Integer code) {
        // 编码为空直接返回 null
        if (code == null) {
            // 编码为空时返回空
            return null;
        }
        // 遍历所有枚举项匹配编码
        for (UrgencyEnum item : values()) {
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
     * 根据编码取中文紧急程度，用于日志与前端展示。
     *
     * @param code 紧急程度编码
     * @return 中文名称；编码无法识别时返回空串
     */
    public static String nameOf(Integer code) {
        // 先按编码查找枚举
        UrgencyEnum item = of(code);
        // 查不到时返回空串
        return item == null ? "" : item.name;
    }
}