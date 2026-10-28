package com.ycc.zhixiucloud.security;

import com.ycc.zhixiucloud.common.enums.RoleEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 登录用户主体：由 JwtAuthenticationFilter 解析 Token 得到，存入 SecurityContext。
 *
 * <p>在整体架构中的位置：本类就是 Spring Security {@code Authentication#getPrincipal()} 的返回值，
 * 控制器可通过 {@code @AuthenticationPrincipal LoginUser loginUser} 直接获取当前用户；
 * 过滤器同时把同一对象放入 {@link UserIdHolder}，供非控制器层读取。</p>
 *
 * <p>关键设计：登录态不查库，所有身份字段都来自 JWT 声明，因此本类只保存鉴权与业务定位
 * 所必需的最小信息（用户 ID、登录名、角色、师傅 ID），不包含密码等敏感数据。
 * 实现 {@link Serializable} 便于后续缓存或异步传递。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser implements Serializable {

    /** 用户主键（user.id），对应 JWT 的 subject */
    private Long userId;

    /** 登录名 */
    private String username;

    /** 真实姓名（Token 中未携带，通常为 null，需要时按 userId 查库） */
    private String realName;

    /**
     * 角色编码：OWNER / WORKER / ADMIN。
     * 对应 JWT 的 roleCode 声明，是后续 {@link #authority()} 的取值来源。
     */
    private String roleCode;

    /** 师傅 ID（maintenance_worker.id），仅师傅角色有值 */
    private Long workerId;

    /**
     * 转换为 Spring Security 角色名，如 ROLE_OWNER。
     *
     * <p>未知角色返回空串而非抛异常，使请求仍能通过认证，但不会命中任何角色约束；
     * 这样既避免伪造角色导致 500，也保留 403 的语义。</p>
     *
     * @return 形如 {@code ROLE_XXX} 的角色名，角色无法识别时返回空串
     */
    public String authority() {
        // 解析角色编码为枚举
        RoleEnum role = RoleEnum.of(roleCode);
        // 未知角色返回空串而非抛异常
        return role == null ? "" : role.authority();
    }
}
