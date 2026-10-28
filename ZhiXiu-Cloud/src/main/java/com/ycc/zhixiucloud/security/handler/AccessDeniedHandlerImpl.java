package com.ycc.zhixiucloud.security.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ycc.zhixiucloud.common.result.Result;
import com.ycc.zhixiucloud.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 无权限处理器：返回真实 HTTP 403 + 统一响应体。
 *
 * <p>在整体架构中的位置：由 {@link com.ycc.zhixiucloud.security.SecurityConfig} 注册为
 * Spring Security 的 {@link AccessDeniedHandler}。它处理的是「已认证但权限不足」的场景，
 * 例如业主访问管理员接口；与处理「未认证」的
 * {@link AuthenticationEntryPointImpl}（401）职责严格区分。</p>
 *
 * <p>关键设计：HTTP 状态码与业务响应体同时表达错误语义（403 + code=403），
 * 前端既可依赖状态码统一拦截，也可读取响应体展示后端提示；二者都通过 ObjectMapper
 * 序列化，保证与正常接口的响应格式一致。</p>
 */
@Component
@RequiredArgsConstructor
public class AccessDeniedHandlerImpl implements AccessDeniedHandler {

    /** Spring 全局 JSON 序列化器，复用统一的对象映射配置 */
    private final ObjectMapper objectMapper;

    /**
     * 处理权限不足异常：写入 403 状态码与统一失败响应体。
     *
     * @param request               当前请求
     * @param response              当前响应
     * @param accessDeniedException 权限不足异常
     * @throws IOException 写出响应体失败
     */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        // 先设置状态码与编码，再写响应体，避免流被提前提交后设置失效
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        // 声明响应类型为 JSON
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        // 指定 UTF-8 字符集
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        // 写出统一失败响应体
        objectMapper.writeValue(response.getWriter(), Result.fail(ResultCode.FORBIDDEN));
    }
}
