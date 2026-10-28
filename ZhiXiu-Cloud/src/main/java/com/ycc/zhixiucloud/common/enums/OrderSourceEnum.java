package com.ycc.zhixiucloud.common.enums;

import lombok.Getter;

/**
 * 工单来源枚举。
 *
 * <p>用于区分工单由业主手动填写表单创建，还是由 AI 智能助手对话后生成，
 * 便于后续统计两类入口的使用情况。业务展示使用 name，持久化使用 code。</p>
 */
@Getter
public enum OrderSourceEnum {

    /** 手动报修：业主在 App 中填写报修表单生成 */
    MANUAL("MANUAL", "手动报修"),
    /** 智能助手：AI 对话收集槽位信息后生成草稿并落单 */
    AI("AI", "智能助手");

    /** 落库编码：MANUAL/AI */
    private final String code;

    /** 前端展示文案 */
    private final String name;

    OrderSourceEnum(String code, String name) {
        // 落库编码
        this.code = code;
        // 展示文案
        this.name = name;
    }

    /**
     * 根据编码查找工单来源。
     *
     * @param code 来源编码，允许为 null
     * @return 匹配的枚举；入参为 null 或编码非法时返回 null
     */
    public static OrderSourceEnum of(String code) {
        // 遍历所有枚举项匹配编码
        for (OrderSourceEnum item : values()) {
            // 编码一致即命中
            if (item.code.equals(code)) {
                // 返回匹配到的枚举
                return item;
            }
        }
        // 未匹配到返回 null
        return null;
    }
}