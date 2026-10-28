package com.ycc.zhixiucloud.service;

/**
 * 师傅在线状态服务：维护 Redis 在线心跳 worker:online:{workerId}（Hash，30 秒过期）。
 *
 * <p>职责边界：在线心跳只用于派单/负载参考，师傅是否可接单的真值仍以
 * maintenance_worker.work_status 与审核状态为准。</p>
 *
 * <p>主要使用方：师傅上线/下线开关、接单/改派后的负载刷新、管理端启停师傅。</p>
 *
 * <p>关键实现要点：心跳包含 status、lastActive、load 三个字段；30 秒内无操作自动过期，
 * 以此近似判断“在线”；touch 只在心跳已存在时续期，不会凭空把离线师傅置为在线。</p>
 */
public interface WorkerPresenceService {

    /**
     * 师傅上线：写入状态、最后活跃时间与当前负载，并刷新 30 秒过期时间。
     *
     * @param workerId    师傅 ID
     * @param workStatus  工作状态（1 在线等）
     * @param currentLoad 当前负载，可为 null（按 0 处理）
     */
    void online(Long workerId, Integer workStatus, Integer currentLoad);

    /**
     * 师傅下线：直接删除在线心跳。
     *
     * @param workerId 师傅 ID
     */
    void offline(Long workerId);

    /**
     * 续期在线心跳并刷新负载。
     *
     * <p>师傅每次处理工单或负载变化时调用；若心跳已过期（key 不存在）则静默返回，
     * 避免把已下线的师傅错误唤醒。</p>
     *
     * @param workerId    师傅 ID
     * @param currentLoad 当前负载，可为 null（按 0 处理）
     */
    void touch(Long workerId, Integer currentLoad);
}