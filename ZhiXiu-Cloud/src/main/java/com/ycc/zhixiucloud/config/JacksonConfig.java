package com.ycc.zhixiucloud.config;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

/**
 * Jackson 序列化配置（Spring MVC 全局 ObjectMapper）。
 *
 * <p>通过 Jackson2ObjectMapperBuilderCustomizer 在 Spring Boot 自动配置的 ObjectMapper
 * 基础上追加规则，因此对全部 @RestController 的返回值与 @RequestBody 入参统一生效。</p>
 *
 * <p>设计要点：</p>
 * <ol>
 *   <li>包装类型 Long（雪花 ID）序列化为字符串，避免 JavaScript Number 的 2^53 精度上限
 *       导致前端 ID 末位失真；基本类型 long（total / pages 等计数）保持数字，前端可直接计算。</li>
 *   <li>LocalDateTime / LocalDate 统一为 yyyy-MM-dd HH:mm:ss / yyyy-MM-dd，
 *       与 application.yml 的 spring.jackson.date-format 及前端展示格式保持一致。</li>
 * </ol>
 *
 * <p>注意：本配置面向浏览器；Redis 缓存使用 RedisConfig 中独立的 ObjectMapper
 * （需要额外保留 Java 类型信息），两者互不影响。</p>
 */
@Configuration
public class JacksonConfig {

    /** 日期时间统一格式，序列化与反序列化共用 */
    public static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /** 日期统一格式 */
    public static final String DATE_PATTERN = "yyyy-MM-dd";

    /** 预构建的格式化器线程安全，可复用，避免每次序列化重复创建 */
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);

    /** 日期格式化器，同上 */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern(DATE_PATTERN);

    /**
     * 定制全局 ObjectMapper 的序列化/反序列化规则。
     * 返回 Customizer 而非直接替换 ObjectMapper，以保留 Spring Boot 自动配置的其余默认行为。
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        // 返回定制器，在 Spring Boot 自动配置的 ObjectMapper 上追加规则
        return builder -> {
            // 仅包装类型 Long 转字符串（雪花 ID 精度保护）；long 基本类型不受影响
            builder.serializerByType(Long.class, ToStringSerializer.instance);
            // 时间序列化：LocalDateTime → "yyyy-MM-dd HH:mm:ss"
            builder.serializerByType(LocalDateTime.class, new LocalDateTimeSerializer(DATE_TIME_FORMATTER));
            // 日期序列化：LocalDate → "yyyy-MM-dd"
            builder.serializerByType(LocalDate.class, new LocalDateSerializer(DATE_FORMATTER));
            // 对应反序列化规则，保证前端回传同格式字符串时也能正确解析
            builder.deserializerByType(LocalDateTime.class, new LocalDateTimeDeserializer(DATE_TIME_FORMATTER));
            // 日期反序列化：字符串 → LocalDate
            builder.deserializerByType(LocalDate.class, new LocalDateDeserializer(DATE_FORMATTER));
            // 禁用“日期写为时间戳数字”，改为可读字符串，前后端联调更直观
            builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            // 固定东八区，避免服务器时区不同导致时间偏移
            builder.timeZone(TimeZone.getTimeZone("GMT+8"));
        };
    }
}
