package com.ycc.zhixiucloud.common.constant;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Redis Key 统一出口。
 *
 * <p>与开发文档「后端开发文档 · 五、Redis 使用清单」保持一致，业务代码禁止手写
 * Redis Key 字符串，避免前缀冲突和拼写错误。命名遵循“业务域:用途:后缀”的冒号分层
 * 约定；动态部分通过 {@link #of(String, Object)} 拼接，月度榜单通过
 * {@link #monthSuffix(LocalDate)} 生成 yyyyMM 后缀。</p>
 *
 * <p>本类为纯常量工具类，禁止实例化。</p>
 */
public final class RedisKeys {

    private RedisKeys() {
    }

    /** Token 映射：value = userId，TTL 与 Token 有效期一致 */
    public static final String TOKEN = "auth:token:";

    /** Token 黑名单：登出后加入，TTL 为 Token 剩余有效期 */
    public static final String TOKEN_BLACKLIST = "auth:blacklist:";

    /** 师傅在线状态：Hash，30s 心跳 */
    public static final String WORKER_ONLINE = "worker:online:";

    /** 待接单池：ZSet，score = 紧急权重 + 时间因子 */
    public static final String PENDING_POOL = "workorder:pending:pool";

    /** 工单状态缓存：String(JSON)，3600s */
    public static final String ORDER_STATUS = "workorder:status:";

    /** 抢单分布式锁：Redisson RLock，10s 自动释放 */
    public static final String GRAB_LOCK = "lock:workorder:grab:";

    /** 派单分布式锁 */
    public static final String ASSIGN_LOCK = "lock:workorder:assign:";

    /** 师傅接单榜：ZSet，{yyyyMM} */
    public static final String WORKER_ORDER_RANK = "rank:worker:orders:";

    /** 师傅满意度榜：ZSet，{yyyyMM} */
    public static final String WORKER_SCORE_RANK = "rank:worker:score:";

    /** 接口限流：{userId}:{api} */
    public static final String RATE_LIMIT = "rate:limit:";

    /** 热门问答缓存：{md5(问题)} */
    public static final String AI_HOT_QUESTION = "ai:hotq:";

    /** 知识分片 ID 清单，持久保存，删除知识时清理 */
    public static final String KNOWLEDGE_CHUNKS = "ai:knowledge:chunks:";

    /** 会话上下文：List */
    public static final String AI_SESSION = "ai:session:";

    /** AI 会话操作锁：看门狗续期，短事务提交后释放 */
    public static final String AI_SESSION_LOCK = "lock:ai:session:";

    /** 操作日志缓冲：List，LPUSH + BRPOP */
    public static final String OPERATION_LOG_BUFFER = "log:operation:buffer";

    /** 月度榜 key 的月份后缀，格式 yyyyMM */
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    /**
     * 拼接“前缀 + 后缀”形式的 Redis Key。
     *
     * @param prefix 常量前缀，如 {@link #TOKEN}
     * @param suffix 动态后缀，如用户 ID、yyyyMM
     * @return 完整 Redis Key
     */
    public static String of(String prefix, Object suffix) {
        // 前缀直接拼接动态后缀
        return prefix + suffix;
    }

    /**
     * 生成月度榜单后缀。
     *
     * @param date 业务日期
     * @return yyyyMM 格式的月份字符串，用于接单榜 / 满意度榜 Key
     */
    public static String monthSuffix(LocalDate date) {
        // 按 yyyyMM 格式格式化月份
        return date.format(MONTH_FORMATTER);
    }
}
