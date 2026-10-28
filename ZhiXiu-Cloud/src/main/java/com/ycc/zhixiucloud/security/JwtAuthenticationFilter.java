package com.ycc.zhixiucloud.security;

import com.ycc.zhixiucloud.common.constant.RedisKeys;
import com.ycc.zhixiucloud.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器：校验请求头 Token，写入 SecurityContext 与 UserIdHolder。
 *
 * <p>在整体架构中的位置：由 {@link SecurityConfig} 注册到 Spring Security 过滤器链中，
 * 位于 {@code UsernamePasswordAuthenticationFilter} 之前。项目采用无状态（STATELESS）
 * 认证，不创建 HttpSession，每个请求都依赖本过滤器重新建立登录态。</p>
 *
 * <p>认证流程（详见 {@link #authenticate(HttpServletRequest)}）：</p>
 * <ol>
 *   <li>从请求头解析 Token，没有 Token 则直接放行（匿名请求由后续授权规则决定是否拦截）；</li>
 *   <li>检查 Redis 黑名单，实现登出后 Token 立即失效；</li>
 *   <li>检查 Redis 中的 Token 登记，实现服务端可控的会话有效期；</li>
 *   <li>验签并解析 JWT 声明，只接受访问令牌（access），拒绝刷新令牌；</li>
 *   <li>构造 {@link LoginUser} 作为 Authentication 的 principal 写入 SecurityContext，
 *       同时写入 {@link UserIdHolder} 供业务层获取当前用户。</li>
 * </ol>
 *
 * <p>失败策略：校验不通过时不会建立登录态，通常只记录 debug 日志并继续过滤链，不在此处抛异常。
 * 这样匿名请求仍可访问白名单接口；当匿名请求访问受保护资源时，由
 * {@code AuthenticationEntryPointImpl} 统一返回 401，避免过滤器内直接写响应造成响应体不一致。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** JWT 签发/解析工具，负责验签、取声明与从请求头提取 Token */
    private final JwtUtil jwtUtil;

    /** Redis 客户端：读取 Token 登记与黑名单，支撑登出失效和服务端会话控制 */
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 过滤器入口：先尝试认证，再放行到后续过滤器/控制器。
     *
     * <p>{@code finally} 中的清理是关键：Servlet 容器会复用线程，若不清理 ThreadLocal，
     * 下一个复用该线程的请求可能读到上一个用户的身份信息，造成越权。</p>
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            // 先尝试认证并建立登录态
            authenticate(request);
            // 认证完成，放行到后续过滤器与控制器
            filterChain.doFilter(request, response);
        } finally {
            // 请求结束务必清理 ThreadLocal，防止线程复用导致身份串号
            UserIdHolder.clear();
        }
    }

    /**
     * 校验请求头中的 Token，并在校验通过后建立登录态。
     *
     * <p>注意：本方法只做「认证」（你是谁），不做「授权」（你能做什么）。
     * 权限判断交给 SecurityConfig 的请求匹配规则与方法级 {@code @PreAuthorize}。</p>
     *
     * @param request 当前 HTTP 请求
     */
    private void authenticate(HttpServletRequest request) {
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            return;
        }
        // 从请求头解析 Token
        String token = jwtUtil.resolveToken(request);
        if (!StringUtils.hasText(token)) {
            return;
        }
        // 1. 登出后 Token 进入黑名单
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(RedisKeys.of(RedisKeys.TOKEN_BLACKLIST, token)))) {
            // 命中黑名单，记录 debug 后不建立登录态
            log.debug("Token 已失效（黑名单）：{}", request.getRequestURI());
            return;
        }
        // 2. 必须仍在 Redis 登记的有效期内
        // 读取服务端登记的会话归属用户
        String registeredUserId = stringRedisTemplate.opsForValue().get(RedisKeys.of(RedisKeys.TOKEN, token));
        if (!StringUtils.hasText(registeredUserId)) {
            // 未登记或已过期，记录 debug 后不建立登录态
            log.debug("Token 未登记或已过期：{}", request.getRequestURI());
            return;
        }
        // 3. 解析声明
        // 声明容器，先声明后赋值以便 catch 中判断
        Claims claims;
        try {
            // 验签并解析 JWT 声明
            claims = jwtUtil.parse(token);
        } catch (JwtException | IllegalArgumentException e) {
            // 解析失败（过期/篡改）不建立登录态
            log.debug("Token 解析失败：{}", e.getMessage());
            return;
        }
        if (!jwtUtil.isAccessToken(claims)) {
            // 拒绝刷新令牌，只接受访问令牌
            log.debug("非访问令牌，拒绝认证：{}", request.getRequestURI());
            return;
        }
        // 4. 组装登录主体：subject 存 userId，其余身份信息取自自定义声明
        // 取出角色编码声明
        String roleCode = claims.get(JwtUtil.CLAIM_ROLE_CODE, String.class);
        // 用 JWT 声明字段构建登录主体
        LoginUser loginUser = LoginUser.builder()
                .userId(Long.valueOf(claims.getSubject()))
                .username(claims.get(JwtUtil.CLAIM_USERNAME, String.class))
                .roleCode(roleCode)
                .workerId(jwtUtil.getWorkerId(claims))
                .build();

        // 5. 写入 Spring Security 上下文：password 置 null，权限使用 ROLE_xxx 形式
        // 构造 Authentication，principal 为登录主体
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                loginUser, null, List.of(new SimpleGrantedAuthority(loginUser.authority())));
        // 写入 SecurityContext，后续授权判断据此进行
        SecurityContextHolder.getContext().setAuthentication(authentication);
        // 同步到业务线程上下文，方便 Service 层直接取当前用户，无需层层传参
        UserIdHolder.set(loginUser);
    }

    /** 跨域预检请求不带 Token，直接放行 */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // OPTIONS 预检请求不做认证，直接跳过本过滤器
        return HttpMethod.OPTIONS.matches(request.getMethod());
    }
}
