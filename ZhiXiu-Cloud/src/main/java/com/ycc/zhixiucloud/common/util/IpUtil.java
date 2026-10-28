package com.ycc.zhixiucloud.common.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/**
 * 客户端 IP 工具。
 *
 * <p>服务部署在 Nginx / 网关之后时，{@code request.getRemoteAddr()} 拿到的是代理地址，
 * 因此优先按 X-Forwarded-For、X-Real-IP 等请求头获取真实客户端 IP；X-Forwarded-For
 * 可能是“客户端, 代理1, 代理2”形式，只取第一个地址。所有头都缺失时回退到 remoteAddr。</p>
 */
public final class IpUtil {

    /** 无法获取 IP 时的占位值 */
    private static final String UNKNOWN = "unknown";

    /** 按优先级依次尝试的代理请求头 */
    private static final String[] IP_HEADERS = {
            "X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP"
    };

    private IpUtil() {
    }

    /**
     * 获取客户端真实 IP。
     *
     * @param request 当前 HTTP 请求，允许为 null
     * @return 客户端 IP；请求为空或无法识别时返回 "unknown"
     */
    public static String getClientIp(HttpServletRequest request) {
        // 请求为空时直接返回占位值
        if (request == null) {
            // 无法读取请求，返回 unknown
            return UNKNOWN;
        }
        // 按优先级遍历各代理请求头
        for (String header : IP_HEADERS) {
            // 读取当前请求头的值
            String ip = request.getHeader(header);
            // 头存在且不为 unknown 才算有效
            if (StringUtils.hasText(ip) && !UNKNOWN.equalsIgnoreCase(ip)) {
                // 代理链会追加多个地址，第一个才是最初的客户端 IP
                int index = ip.indexOf(',');
                // 多地址时只取第一个并去空格
                return index > 0 ? ip.substring(0, index).trim() : ip.trim();
            }
        }
        // 所有代理头都取不到时回退到 remoteAddr
        String remote = request.getRemoteAddr();
        // remoteAddr 为空则返回 unknown
        return StringUtils.hasText(remote) ? remote : UNKNOWN;
    }
}
