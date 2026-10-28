package com.ycc.zhixiucloud.security;

import com.ycc.zhixiucloud.security.handler.AccessDeniedHandlerImpl;
import com.ycc.zhixiucloud.security.handler.AuthenticationEntryPointImpl;
import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 安全配置：无状态 Token 认证 + 方法级角色鉴权。
 * 放行白名单与开发文档「配置类与配置文件 · 6.7 SecurityConfig」一致。
 *
 * <p>在整体架构中的位置：本类是 Spring Security 的总装配入口，负责组装过滤器链、授权规则、
 * 异常处理器与密码编码器；具体的 Token 解析由 {@link JwtAuthenticationFilter} 完成，
 * 业务方法上的角色约束由 {@code @EnableMethodSecurity} 开启的 {@code @PreAuthorize} 完成。</p>
 *
 * <p>关键设计：</p>
 * <ul>
 *   <li>前后端分离：禁用 CSRF、表单登录和 HTTP Basic，会话策略为 STATELESS，不使用 Session；</li>
 *   <li>白名单只放行登录/注册、公开文件、在线接口文档和错误页，其余请求必须先认证；</li>
 *   <li>401（未认证）与 403（无权限）分别交给两个自定义处理器，保证返回统一的 JSON 响应体；</li>
 *   <li>CORS 交给全局 CorsConfigurationSource（{@code Customizer.withDefaults()}）。</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /** 免认证路径：认证接口、公开文件、接口文档页（springdoc）、错误页 */
    private static final String[] WHITE_LIST = {
            "/api/auth/**",
            "/api/file/public/**",
            "/doc.html",
            "/webjars/**",
            "/img/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/error",
            "/favicon.ico"
    };

    /** JWT 认证过滤器：在授权判断前解析 Token 并建立登录态 */
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /** 未认证处理器：匿名访问受保护资源时返回 401 */
    private final AuthenticationEntryPointImpl authenticationEntryPoint;

    /** 权限不足处理器：已登录但角色不满足时返回 403 */
    private final AccessDeniedHandlerImpl accessDeniedHandler;

    /**
     * 密码编码器：BCrypt。
     *
     * <p>BCrypt 每次加密都会随机加盐，数据库中只保存哈希值；登录时通过
     * {@code PasswordEncoder#matches} 比对，不能直接比较字符串。</p>
     *
     * @return BCrypt 密码编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        // 返回 BCrypt 密码编码器
        return new BCryptPasswordEncoder();
    }

    /**
     * 构建安全过滤器链。
     *
     * <p>授权规则按声明顺序从上到下匹配，先命中先生效：</p>
     * <ol>
     *   <li>ASYNC / ERROR 派发直接放行，避免 SSE 异步收尾阶段被误判为越权；</li>
     *   <li>CORS 预检 OPTIONS 请求放行；</li>
     *   <li>白名单路径放行；</li>
     *   <li>其余请求一律要求已认证，具体角色由方法级注解进一步限制。</li>
     * </ol>
     *
     * @param http Spring Security 的过滤器链构建器
     * @return 构建完成的安全过滤器链
     * @throws Exception 构建过滤器链失败
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // 前后端分离 + Token 认证：关闭 CSRF、表单登录与 Basic 认证，不创建 Session
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // SSE 等异步响应在 emitter.complete() 后由容器发起 ASYNC 派发，
                        // 此时响应已提交且异步线程内没有登录态，必须放行，否则收尾阶段会抛 AccessDenied
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        // 浏览器跨域预检请求不携带 Token
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // 认证接口、公开文件、接口文档等白名单
                        .requestMatchers(WHITE_LIST).permitAll()
                        // 其余接口必须携带有效 Token
                        .anyRequest().authenticated())
                // 认证失败返回 401，授权失败返回 403，均输出统一 JSON
                .exceptionHandling(handler -> handler
                        // 未认证返回 401
                        .authenticationEntryPoint(authenticationEntryPoint)
                        // 权限不足返回 403
                        .accessDeniedHandler(accessDeniedHandler))
                // JWT 过滤器先于用户名密码过滤器执行，在授权判断前建立登录态
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        // 返回构建完成的安全过滤器链
        return http.build();
    }
}
