package com.ycc.zhixiucloud.service.impl;

import com.ycc.zhixiucloud.common.constant.RedisKeys;
import com.ycc.zhixiucloud.service.WorkerPresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 师傅在线状态实现：基于 Redis Hash 维护「是否在线 + 最近心跳 + 当前负载」三要素。
 *
 * <p>存储结构：key 为 worker:online:{workerId}，Hash 字段为 status（工作状态码）、
 * lastActive（最近心跳时间）、load（当前负载）。每次写入或续期都会重置 30s TTL，
 * 超过 30s 没有心跳 key 自动过期，等价于「掉线」，无需定时任务清理。</p>
 *
 * <p>定位：在线状态只作为派单推荐的参考信号，真值仍以 maintenance_worker.work_status
 * 与 current_load 为准。touch 不会为已过期的 key 续命，避免离线师傅被一次工单操作误判为在线。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkerPresenceServiceImpl implements WorkerPresenceService {

    /** 心跳有效期（秒）：超过该时长无续期，key 由 Redis 自动过期，即视为离线 */
    private static final long HEARTBEAT_SECONDS = 30L;

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 师傅上线/心跳：写入或覆盖在线 Hash，并重置 30s 过期时间。
     *
     * <p>副作用：覆盖 status / lastActive / load 三个字段。同一 key 重复调用即刷新心跳，天然幂等。</p>
     *
     * @param workerId    师傅记录 ID
     * @param workStatus  工作状态码
     * @param currentLoad 当前负载，null 按 0 处理
     */
    @Override
    public void online(Long workerId, Integer workStatus, Integer currentLoad) {
        // 在线状态 Hash 的 key
        String key = RedisKeys.of(RedisKeys.WORKER_ONLINE, workerId);
        // 组装「状态 + 心跳 + 负载」三要素
        Map<String, String> heartbeat = new HashMap<>(4);
        // 工作状态码
        heartbeat.put("status", String.valueOf(workStatus));
        // 以当前时间作为最近心跳
        heartbeat.put("lastActive", LocalDateTime.now().toString());
        // 负载为空按 0 处理
        heartbeat.put("load", String.valueOf(currentLoad == null ? 0 : currentLoad));
        // 一次写入三个字段，覆盖旧值
        stringRedisTemplate.opsForHash().putAll(key, heartbeat);
        // 每次心跳都重新计时，形成 30s 滑动过期窗口
        stringRedisTemplate.expire(key, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
        // 记录上线心跳日志
        log.info("师傅上线心跳：workerId={}, workStatus={}, load={}", workerId, workStatus, currentLoad);
    }

    /**
     * 师傅下线：直接删除在线 Hash，使其立即从派单参考中消失。
     *
     * <p>副作用：删除 Redis key；key 不存在时删除为空操作，可重复调用。</p>
     *
     * @param workerId 师傅记录 ID
     */
    @Override
    public void offline(Long workerId) {
        // 删除在线 key，使师傅立即从派单参考中消失
        stringRedisTemplate.delete(RedisKeys.of(RedisKeys.WORKER_ONLINE, workerId));
        // 记录下线日志
        log.info("师傅下线：workerId={}", workerId);
    }

    /**
     * 心跳续期：仅当在线 key 仍存在时更新 lastActive 与 load，并重置 30s TTL。
     *
     * <p>先检查 key 是否存在，避免师傅已离线（key 过期或被删除）后因处理工单而被重新写活；
     * 只有仍然在线的师傅才会更新负载与心跳，未命中时静默返回。</p>
     *
     * @param workerId    师傅记录 ID
     * @param currentLoad 当前负载，null 按 0 处理
     */
    @Override
    public void touch(Long workerId, Integer currentLoad) {
        // 在线状态 Hash 的 key
        String key = RedisKeys.of(RedisKeys.WORKER_ONLINE, workerId);
        // key 已过期说明师傅已离线，这里不能重新写活
        if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(key))) {
            return;
        }
        // 更新最近心跳时间
        stringRedisTemplate.opsForHash().put(key, "lastActive", LocalDateTime.now().toString());
        // 更新当前负载
        stringRedisTemplate.opsForHash().put(key, "load", String.valueOf(currentLoad == null ? 0 : currentLoad));
        // 续期即再给 30s 窗口，停止心跳后自动过期
        stringRedisTemplate.expire(key, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
    }
}