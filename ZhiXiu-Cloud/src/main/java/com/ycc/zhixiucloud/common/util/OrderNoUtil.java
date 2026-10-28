package com.ycc.zhixiucloud.common.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 工单号生成工具。
 *
 * <p>格式为 WO + yyyyMMddHHmmss + 4 位序号，例如 WO202610181530250123。
 * 序号取雪花 ID 对 10000 取模，同一秒内基本不重复；工单号列有唯一索引，
 * 极端冲突时由业务层捕获并重试。</p>
 */
public final class OrderNoUtil {

    /** 工单号固定前缀 */
    private static final String PREFIX = "WO";

    /** 时间部分格式：精确到秒 */
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 序号取模基数，保证序号固定为 4 位 */
    private static final int SEQUENCE_MODULUS = 10000;

    private OrderNoUtil() {
    }

    /**
     * 生成工单号。
     *
     * @param id  工单雪花 ID（由 IdWorker 预生成，与入库主键一致）
     * @param now 生成时间
     * @return 形如 WO + 14 位时间 + 4 位序号的工单号
     */
    public static String generate(long id, LocalDateTime now) {
        // 前缀 + 秒级时间 + 四位序号拼接
        return PREFIX + now.format(FORMATTER)
                + String.format("%04d", Math.abs(id % SEQUENCE_MODULUS));
    }
}