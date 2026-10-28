package com.ycc.zhixiucloud.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * 业务 Redis 序列化配置。
 *
 * <p>Spring Boot 只自动提供 StringRedisTemplate；本类额外提供 RedisTemplate&lt;String, Object&gt;，
 * 用于缓存对象（如 OrderCacheServiceImpl 缓存的 WorkOrderStatusDTO）。</p>
 *
 * <p>序列化策略：</p>
 * <ul>
 *   <li>Key / HashKey 使用 StringRedisSerializer，Redis 中可直接阅读，便于按前缀排查；</li>
 *   <li>Value / HashValue 使用 GenericJackson2JsonRedisSerializer + 自定义 ObjectMapper，
 *       以 JSON 存储并写入 @class 类型信息，反序列化时能还原为原始类型（取出即为 DTO，
 *       而不是失去类型的 LinkedHashMap）。</li>
 * </ul>
 *
 * <p>注意：这里使用独立的 ObjectMapper，不复用 JacksonConfig 的 Web 实例 ——
 * Web 层把 Long 转成字符串供前端使用，而缓存需要按 Java 类型完整还原。
 * 分布式锁由 RedissonConfig 的 RedissonClient 负责（默认 database=1），与本模板互不影响。</p>
 */
@Configuration
public class RedisConfig {

    /**
     * 通用对象缓存模板。
     * 同时设置普通 KV 与 Hash 结构的两套序列化器，保证 opsForValue / opsForHash 行为一致。
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        // 创建可缓存任意对象类型的缓存模板
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        // 绑定 Redis 连接工厂
        template.setConnectionFactory(connectionFactory);

        // Key 用纯字符串，便于 redis-cli 按前缀检索
        StringRedisSerializer keySerializer = new StringRedisSerializer();
        // Value 用带类型信息的 JSON，兼容 LocalDateTime 等 Java 8 时间类型
        GenericJackson2JsonRedisSerializer valueSerializer =
                new GenericJackson2JsonRedisSerializer(redisObjectMapper());

        // 普通 KV 的 Key 序列化器
        template.setKeySerializer(keySerializer);
        // Hash 结构的 field 序列化器（与普通 Key 保持一致）
        template.setHashKeySerializer(keySerializer);
        // 普通 KV 的 Value 序列化器
        template.setValueSerializer(valueSerializer);
        // Hash 结构的 value 序列化器（与普通 Value 保持一致）
        template.setHashValueSerializer(valueSerializer);
        // 校验序列化器配置并完成模板初始化
        template.afterPropertiesSet();
        // 返回初始化完成的缓存模板
        return template;
    }

    /**
     * 缓存专用 ObjectMapper。
     * 与 Web 层 ObjectMapper 隔离，核心差异是开启默认类型信息，保证对象可原样还原。
     */
    private ObjectMapper redisObjectMapper() {
        // 新建缓存专用 ObjectMapper，与 Web 层实例隔离
        ObjectMapper objectMapper = new ObjectMapper();
        // 支持 LocalDateTime / LocalDate 等 Java 8 时间类型
        objectMapper.registerModule(new JavaTimeModule());
        // 日期以 ISO 字符串而非时间戳写入，缓存内容便于人工查看
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // 忽略 getter/setter 可见性限制，直接访问字段，避免无 getter 的 DTO 序列化失败
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        // 写入 @class 类型标识；NON_FINAL 覆盖大多数 DTO，反序列化时据此还原具体类型
        objectMapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
        // 返回配置好类型信息与时间处理的 ObjectMapper
        return objectMapper;
    }
}
