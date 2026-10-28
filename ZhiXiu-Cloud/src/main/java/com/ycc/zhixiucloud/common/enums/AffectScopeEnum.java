package com.ycc.zhixiucloud.common.enums;

import lombok.Getter;

/**
 * 影响范围枚举。
 *
 * <p>用于报修工单描述故障波及的范围，业务展示使用 name，持久化使用 code。
 * 该枚举不参与工单状态机，只作为工单的静态属性。</p>
 */
@Getter
public enum AffectScopeEnum {

    /** 1-本户：只有报修人所在住户受影响 */
    HOUSEHOLD(1, "本户"),
    /** 2-本层：整层住户均受影响 */
    FLOOR(2, "本层"),
    /** 3-整栋：整栋楼均受影响，通常对应较严重的公共设施故障 */
    BUILDING(3, "整栋");

    /** 落库编码：1/2/3 */
    private final int code;

    /** 前端展示文案 */
    private final String name;

    AffectScopeEnum(int code, String name) {
        // 落库编码
        this.code = code;
        // 展示文案
        this.name = name;
    }

    /**
     * 根据落库编码查找影响范围。
     *
     * @param code 编码，允许为 null
     * @return 匹配的枚举；入参为 null 或编码非法时返回 null
     */
    public static AffectScopeEnum of(Integer code) {
        // 编码为空直接返回 null
        if (code == null) {
            // 编码为空时返回空
            return null;
        }
        // 遍历所有枚举项匹配编码
        for (AffectScopeEnum item : values()) {
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