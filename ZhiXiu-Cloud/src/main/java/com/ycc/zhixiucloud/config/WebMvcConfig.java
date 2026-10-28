package com.ycc.zhixiucloud.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Web MVC 配置：跨域（供 Security 的 cors() 使用）+ 字符串消息转换器 UTF-8。
 *
 * <p>SecurityConfig 中通过 http.cors(Customizer.withDefaults()) 启用跨域，
 * Spring Security 会自动从容器查找 CorsConfigurationSource Bean，
 * 本类提供的 corsConfigurationSource() 即其数据来源，跨域规则只需在此维护一份。</p>
 *
 * <p>前端（ZhiXiu-Vue3）与后端不同源，预检 OPTIONS 请求由 SecurityConfig 放行，
 * 实际响应头则由本配置补充。</p>
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /** 允许的前端来源，逗号分隔；默认放开全部（生产用 CORS_ALLOWED_ORIGINS 收紧） */
    @Value("${repair.cors.allowed-origin-patterns:*}")
    private String allowedOriginPatterns;

    /**
     * 跨域规则：供 Spring Security 的 cors() 自动装配使用。
     * 注意 allowCredentials(true) 时不能直接用 "*" 作为 allowedOrigins，
     * 因此使用 allowedOriginPatterns 支持通配（默认 *，生产可按环境变量收紧）。
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        // 创建跨域配置对象
        CorsConfiguration configuration = new CorsConfiguration();
        // 解析逗号分隔的来源白名单，忽略空白项，便于用环境变量配置多个地址
        for (String pattern : allowedOriginPatterns.split(",")) {
            // 只处理非空白的来源模式
            if (StringUtils.hasText(pattern)) {
                // 把该来源模式加入白名单
                configuration.addAllowedOriginPattern(pattern.trim());
            }
        }
        // 允许所有请求头（含 Authorization、Content-Type 等）
        configuration.addAllowedHeader("*");
        // 允许所有 HTTP 方法（GET/POST/PUT/DELETE 等）
        configuration.addAllowedMethod("*");
        // 允许携带 Cookie / Authorization 等凭证
        configuration.setAllowCredentials(true);
        // 暴露给前端 JS 可读的响应头：Authorization 用于刷新 Token，Content-Disposition 用于文件下载文件名
        configuration.setExposedHeaders(List.of("Authorization", "Content-Disposition"));
        // 预检请求结果缓存 1 小时，减少 OPTIONS 往返
        configuration.setMaxAge(3600L);

        // 基于 URL 路径匹配的跨域配置源
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // 对所有路径生效
        source.registerCorsConfiguration("/**", configuration);
        // 返回基于 URL 匹配的跨域配置源，供 Security 的 cors() 使用
        return source;
    }

    /**
     * 统一用 UTF-8，避免中文响应乱码。
     * 移除默认的 StringHttpMessageConverter 后在列表首位放入 UTF-8 版本，
     * 确保返回 String 的接口优先使用正确的字符集。
     */
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        // 移除默认的字符串转换器（字符集不是 UTF-8）
        converters.removeIf(converter -> converter instanceof StringHttpMessageConverter);
        // 在列表首位插入 UTF-8 字符串转换器，使其优先生效
        converters.add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
    }
}
