package com.ycc.zhixiucloud.security.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ycc.zhixiucloud.common.result.Result;
import com.ycc.zhixiucloud.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 未认证处理器：返回真实 HTTP 401 + 统一响应体。
 *
 * <p>在整体架构中的位置：由 {@link com.ycc.zhixiucloud.security.SecurityConfig} 注册为
 * Spring Security 的 {@link AuthenticationEntryPoint}。当匿名请求（未携带 Token、
 * Token 无效/过期/已登出）访问受保护资源时，Spring Security 会调用本处理器，
 * 而不是返回默认的登录页或空响应。</p>
 *
 * <p>关键设计：JWT 过滤器对无效 Token 只做「不建立登录态」处理，不直接写响应；
 * 是否返回 401 由授权阶段决定。这样白名单接口即使带了失效 Token 也能正常访问，
 * 同时所有认证失败都经过本类，响应格式保持一致。</p>
 */
@Component
@RequiredArgsConstructor
public class AuthenticationEntryPointImpl implements AuthenticationEntryPoint {

    /** Spring 全局 JSON 序列化器，复用统一的对象映射配置 */
    private final ObjectMapper objectMapper;

    /**
     * 处理未认证访问：写入 401 状态码与统一失败响应体。
     *
     * @param request       当前请求
     * @param response      当前响应
     * @param authException 认证异常（如凭证缺失或无效）
     * @throws IOException 写出响应体失败
     */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        // 先设置状态码与编码，再写响应体，避免流被提前提交后设置失效
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        // 声明响应类型为 JSON
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        // 指定 UTF-8 字符集
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        // 写出统一失败响应体
        objectMapper.writeValue(response.getWriter(), Result.fail(ResultCode.UNAUTHORIZED));
    }
}
