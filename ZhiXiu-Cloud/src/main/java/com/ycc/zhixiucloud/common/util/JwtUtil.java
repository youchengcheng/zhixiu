package com.ycc.zhixiucloud.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具。
 *
 * <p>负责访问令牌 / 刷新令牌的签发、校验与解析，以及从请求头提取 Token。
 * 密钥来自配置项 {@code jwt.secret}（禁止硬编码），有效期配置单位均为秒。
 * 令牌通过 type 声明区分 access / refresh，防止刷新令牌被当作访问令牌使用。</p>
 *
 * <p>本类注册为 Spring Bean，以便注入配置；解析失败由调用方决定是返回 null
 * 还是抛出业务异常。</p>
 */
@Slf4j
@Component
public class JwtUtil {

    /** 角色编码声明 */
    public static final String CLAIM_ROLE_CODE = "roleCode";

    /** 登录名声明 */
    public static final String CLAIM_USERNAME = "username";

    /** 师傅 ID 声明（仅师傅角色存在） */
    public static final String CLAIM_WORKER_ID = "workerId";

    /** Token 类型声明 */
    public static final String CLAIM_TYPE = "type";

    /** 访问令牌 */
    public static final String TYPE_ACCESS = "access";

    /** 刷新令牌 */
    public static final String TYPE_REFRESH = "refresh";

    /** HMAC-SHA 签名密钥，由配置项 jwt.secret 生成 */
    private final SecretKey secretKey;

    /** 访问令牌有效期（秒） */
    private final long expireSeconds;

    /** 刷新令牌有效期（秒） */
    private final long refreshExpireSeconds;

    /** 携带 Token 的请求头名称，默认如 Authorization */
    private final String header;

    /** Token 前缀，默认如 "Bearer " */
    private final String prefix;

    /**
     * 构造 JWT 工具并初始化签名密钥。
     *
     * @param secret               签名密钥，长度需满足 HMAC-SHA 算法要求
     * @param expireSeconds        访问令牌有效期（秒）
     * @param refreshExpireSeconds 刷新令牌有效期（秒）
     * @param header               携带 Token 的请求头名称
     * @param prefix               Token 前缀，如 "Bearer "
     */
    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.expire}") long expireSeconds,
                   @Value("${jwt.refresh-expire}") long refreshExpireSeconds,
                   @Value("${jwt.header}") String header,
                   @Value("${jwt.prefix}") String prefix) {
        // 由配置密钥生成 HMAC-SHA 签名密钥
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        // 访问令牌有效期（秒）
        this.expireSeconds = expireSeconds;
        // 刷新令牌有效期（秒）
        this.refreshExpireSeconds = refreshExpireSeconds;
        // 携带 Token 的请求头名称
        this.header = header;
        // Token 前缀
        this.prefix = prefix;
    }

    /** 访问令牌有效期（秒） */
    public long getExpireSeconds() {
        // 返回访问令牌有效期
        return expireSeconds;
    }

    /** 刷新令牌有效期（秒） */
    public long getRefreshExpireSeconds() {
        // 返回刷新令牌有效期
        return refreshExpireSeconds;
    }

    /**
     * 签发访问令牌。
     *
     * @param userId   用户 ID
     * @param username 登录名
     * @param roleCode 角色编码
     * @param workerId 师傅 ID，非师傅传 null
     * @return 访问令牌字符串
     */
    public String generateAccessToken(Long userId, String username, String roleCode, Long workerId) {
        // 以 access 类型与访问令牌有效期构建
        return buildToken(userId, username, roleCode, workerId, TYPE_ACCESS, expireSeconds);
    }

    /**
     * 签发刷新令牌。
     *
     * @param userId   用户 ID
     * @param username 登录名
     * @param roleCode 角色编码
     * @param workerId 师傅 ID，非师傅传 null
     * @return 刷新令牌字符串
     */
    public String generateRefreshToken(Long userId, String username, String roleCode, Long workerId) {
        // 以 refresh 类型与刷新令牌有效期构建
        return buildToken(userId, username, roleCode, workerId, TYPE_REFRESH, refreshExpireSeconds);
    }

    /**
     * 构建 JWT 字符串，访问令牌与刷新令牌共用此逻辑。
     *
     * @param userId     用户 ID，写入 subject
     * @param username   登录名
     * @param roleCode   角色编码，用于安全框架鉴权
     * @param workerId   师傅 ID，非师傅为 null
     * @param type       令牌类型：access / refresh
     * @param ttlSeconds 有效期（秒）
     * @return 紧凑格式的 JWT 字符串
     */
    private String buildToken(Long userId, String username, String roleCode, Long workerId,
                              String type, long ttlSeconds) {
        // 取当前时间戳作为签发时间基准
        long now = System.currentTimeMillis();
        // 逐个写入 subject 与自定义声明，最后签名并生成紧凑串
        return Jwts.builder()
                // subject 固定存用户 ID，解析时可据此还原登录用户
                .subject(String.valueOf(userId))
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_ROLE_CODE, roleCode)
                .claim(CLAIM_WORKER_ID, workerId)
                .claim(CLAIM_TYPE, type)
                .issuedAt(new Date(now))
                // 配置有效期单位是秒，这里转换为毫秒
                .expiration(new Date(now + ttlSeconds * 1000L))
                .signWith(secretKey)
                .compact();
    }

    /**
     * 解析并校验令牌（验签 + 过期时间校验）。
     *
     * @param token JWT 字符串
     * @return 校验通过的令牌载荷
     * @throws JwtException 签名不合法、已过期或格式错误
     */
    public Claims parse(String token) {
        // 验签并校验过期时间后取出载荷
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 从 Token 中取用户 ID。
     *
     * @param token JWT 字符串
     * @return 用户 ID；解析失败或 subject 非法时返回 null
     */
    public Long getUserId(String token) {
        try {
            // subject 存的是用户 ID，解析后转为 Long
            return Long.valueOf(parse(token).getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            // 解析失败仅记录 debug 日志
            log.debug("解析 Token 失败：{}", e.getMessage());
            // 返回 null 交由调用方判定未登录
            return null;
        }
    }

    /**
     * 从载荷中取师傅 ID。
     *
     * @param claims 已解析的令牌载荷
     * @return 师傅 ID；非师傅角色或声明不存在时返回 null
     */
    public Long getWorkerId(Claims claims) {
        // 取出师傅 ID 声明
        Object value = claims.get(CLAIM_WORKER_ID);
        // 非师傅角色没有该声明
        if (value == null) {
            // 声明缺失时无师傅 ID，返回空
            return null;
        }
        // 数字类型直接取 long 值
        if (value instanceof Number number) {
            // 数值声明直接取整数值
            return number.longValue();
        }
        // 其它类型按字符串兜底转换
        return Long.valueOf(String.valueOf(value));
    }

    /**
     * 判断是否为访问令牌。
     *
     * @param claims 令牌载荷
     * @return type=access 时返回 true
     */
    public boolean isAccessToken(Claims claims) {
        // type 声明为 access 即为访问令牌
        return TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class));
    }

    /**
     * 判断是否为刷新令牌。
     *
     * @param claims 令牌载荷
     * @return type=refresh 时返回 true
     */
    public boolean isRefreshToken(Claims claims) {
        // type 声明为 refresh 即为刷新令牌
        return TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class));
    }

    /**
     * 计算令牌剩余有效秒数。
     *
     * @param claims 令牌载荷
     * @return 剩余秒数；已过期或无过期时间时返回 0
     */
    public long getRemainingSeconds(Claims claims) {
        // 取过期时间声明
        Date expiration = claims.getExpiration();
        // 无过期时间的令牌视为已失效
        if (expiration == null) {
            // 无过期时间视为已失效，返回 0
            return 0L;
        }
        // 用毫秒差换算剩余秒数
        long remaining = (expiration.getTime() - System.currentTimeMillis()) / 1000L;
        // 时钟误差等原因可能算出负数，统一归零
        return Math.max(remaining, 0L);
    }

    /**
     * 从请求头中提取 Token。
     *
     * <p>配置了前缀时先去掉前缀（如 "Bearer "）；未配置前缀则直接使用头值。
     * 请求头缺失或为空时返回 null。</p>
     *
     * @param request 当前 HTTP 请求
     * @return 去除前缀后的 Token 字符串；无法提取时返回 null
     */
    public String resolveToken(HttpServletRequest request) {
        // 读取配置的请求头
        String value = request.getHeader(header);
        // 请求头缺失或为空时无法提取
        if (!StringUtils.hasText(value)) {
            // 头值为空，返回空令牌
            return null;
        }
        // 配置了前缀且头值匹配时去掉前缀
        if (StringUtils.hasText(prefix) && value.startsWith(prefix)) {
            // 去掉前缀并去除首尾空白
            return value.substring(prefix.length()).trim();
        }
        // 未配置前缀则直接使用头值
        return value.trim();
    }
}
