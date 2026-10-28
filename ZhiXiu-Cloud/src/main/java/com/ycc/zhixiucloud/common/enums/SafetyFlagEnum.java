package com.ycc.zhixiucloud.common.enums;

import lombok.Getter;

/**
 * 安全隐患标志枚举。
 *
 * <p>隐患支持多选，落库时用逗号分隔的中文 value 拼接成字符串（如“涉电,高空”），
 * 读取时通过 {@link #contains(String, SafetyFlagEnum)} 判断是否包含某个隐患。</p>
 */
@Getter
public enum SafetyFlagEnum {

    /** 涉电：带电作业或电气设备故障，需要断电并做好防护 */
    ELECTRIC("涉电"),
    /** 涉水：水管、排水等涉水作业，注意漏水与防滑 */
    WATER("涉水"),
    /** 涉气：燃气管道或燃气设备相关作业，存在泄漏风险 */
    GAS("涉气"),
    /** 高空：高处作业，需系安全带并设置警戒区域 */
    HIGH_ALTITUDE("高空");

    /** 落库使用的中文标识，也是逗号分隔字符串中的单项 */
    private final String value;

    SafetyFlagEnum(String value) {
        // 落库使用的中文标识
        this.value = value;
    }

    /**
     * 判断逗号分隔的隐患字符串中是否包含指定隐患。
     *
     * @param flags  数据库中的隐患字符串，如“涉电,高空”
     * @param target 待判断的隐患
     * @return 包含返回 true；flags 为空或 null 时返回 false
     */
    public static boolean contains(String flags, SafetyFlagEnum target) {
        // 为空或空串视为不含任何隐患
        if (flags == null || flags.isBlank()) {
            // 隐患串为空视为不含该隐患
            return false;
        }
        // 按逗号拆分成单项逐个比对
        for (String item : flags.split(",")) {
            // 去掉空白后与目标隐患比较
            if (item.trim().equals(target.value)) {
                // 命中即包含
                return true;
            }
        }
        // 遍历完仍未命中
        return false;
    }
}