package com.ycc.zhixiucloud.config;

import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Redisson 分布式锁配置（单机模式）。
 *
 * <p>本项目同时使用两套 Redis 客户端，职责分离：</p>
 * <ul>
 *   <li>Spring Data Redis（RedisTemplate / StringRedisTemplate）负责缓存、计数器、榜单等普通读写；</li>
 *   <li>Redisson 负责分布式锁。连接参数来自 application.yml 的 redisson.*，
 *       其中 database 默认 1，与业务 Redis（默认 0 库）隔离，避免锁 Key 与业务 Key 混杂。</li>
 * </ul>
 *
 * <p>使用方：AssignServiceImpl 在抢单 / 派单 / 改派时按工单维度获取 RLock，
 * 将「读状态 → 改状态」串行化。锁的等待时间与租约时间由 redisson.lock-wait-time、
 * redisson.lock-lease-time 配置，在业务层读取，本类不设置。</p>
 */
@Slf4j
@Configuration
public class RedissonConfig {

    /** Redis 地址，格式 redis://host:port，由 application.yml 拼接环境变量得到 */
    @Value("${redisson.address}")
    private String address;

    /** 密码，允许为空；为空时不启用认证 */
    @Value("${redisson.password:}")
    private String password;

    /**
     * 使用的 Redis 库号：application.yml 中默认配置为 1，与业务 Redis 的 0 库隔离；
     * 冒号后的 0 仅作为配置缺失时的兜底值。
     */
    @Value("${redisson.database:0}")
    private int database;

    /**
     * 创建 RedissonClient 单例。
     * destroyMethod = "shutdown"：应用关闭时由 Spring 自动调用，释放 Netty 连接与后台线程。
     */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        // 创建 Redisson 配置对象
        Config config = new Config();
        // 单机部署模式；后续若扩展主从/集群，可在此切换 useMasterSlaveServers / useClusterServers
        config.useSingleServer()
                // 指定 Redis 地址
                .setAddress(address)
                // 密码为空时不设置认证信息，避免向无密码 Redis 发送 AUTH 导致连接失败
                .setPassword(StringUtils.hasText(password) ? password : null)
                // 使用独立库号，与业务 Redis 的 0 库隔离
                .setDatabase(database)
                // 最小空闲连接数：保持 4 条常驻连接，降低首次加锁的建连延迟
                .setConnectionMinimumIdleSize(4)
                // 连接池上限：16 条，覆盖并发抢单 / 派单请求
                .setConnectionPoolSize(16)
                // 命令等待超时（毫秒）：锁操作不应长期占用请求线程
                .setTimeout(3000)
                // 建连超时（毫秒）：Redis 不可用时尽快失败，避免请求堆积
                .setConnectTimeout(5000);
        // 记录初始化信息
        log.info("初始化 Redisson，地址：{}，库：{}", address, database);
        // 返回 Redisson 客户端实例
        return Redisson.create(config);
    }
}
