package com.ycc.zhixiucloud.common.util;

import com.ycc.zhixiucloud.common.exception.BizException;
import com.ycc.zhixiucloud.common.result.ResultCode;
import com.ycc.zhixiucloud.security.LoginUser;
import com.ycc.zhixiucloud.security.UserIdHolder;
import org.springframework.util.StringUtils;

/**
 * 登录态工具。
 *
 * <p>Service 层获取当前登录用户统一走这里，底层读取 {@link UserIdHolder} 中由 JWT
 * 过滤器写入的 ThreadLocal。业务代码不要在 Controller 之外直接访问 ThreadLocal，
 * 也不要信任前端传入的用户 ID。</p>
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * 获取当前登录用户。
     *
     * @return 登录用户信息；未登录返回 null
     */
    public static LoginUser currentUser() {
        // 直接读取当前线程绑定的登录用户
        return UserIdHolder.get();
    }

    /**
     * 获取当前登录用户 ID。
     *
     * @return 用户 ID
     * @throws BizException 未登录时抛出 401
     */
    public static Long currentUserId() {
        // 从 ThreadLocal 取当前用户 ID
        Long userId = UserIdHolder.currentUserId();
        // 未登录时 userId 为空
        if (userId == null) {
            // 抛 401 提示未登录
            throw BizException.of(ResultCode.UNAUTHORIZED);
        }
        // 返回校验通过的用户 ID
        return userId;
    }

    /**
     * 获取当前登录用户角色编码。
     *
     * @return 角色编码，如 OWNER/WORKER/ADMIN
     * @throws BizException 未登录或角色缺失时抛出 401
     */
    public static String currentRoleCode() {
        // 从 ThreadLocal 取当前用户角色编码
        String roleCode = UserIdHolder.currentRoleCode();
        // 角色缺失说明未登录
        if (!StringUtils.hasText(roleCode)) {
            // 抛 401
            throw BizException.of(ResultCode.UNAUTHORIZED);
        }
        // 返回角色编码
        return roleCode;
    }

    /**
     * 获取当前登录师傅 ID。
     *
     * @return 师傅 ID；非师傅角色或未登录返回 null
     */
    public static Long currentWorkerId() {
        // 取当前登录用户对象
        LoginUser loginUser = UserIdHolder.get();
        // 未登录或非师傅角色时返回 null
        return loginUser == null ? null : loginUser.getWorkerId();
    }

    /**
     * 获取当前登录师傅 ID，要求当前账号必须是维修师傅。
     *
     * @return 师傅 ID
     * @throws BizException 非师傅或未登录时抛出 403
     */
    public static Long requireWorkerId() {
        // 取当前登录师傅 ID
        Long workerId = currentWorkerId();
        // 非师傅角色时师傅 ID 为空
        if (workerId == null) {
            // 抛 403 提示账号不是师傅
            throw BizException.of(ResultCode.FORBIDDEN, "当前账号不是维修师傅");
        }
        // 返回师傅 ID
        return workerId;
    }

    /**
     * 判断当前用户是否为业主。
     *
     * @return 是业主返回 true；未登录返回 false
     */
    public static boolean isOwner() {
        // 角色编码为 OWNER 即视为业主
        return "OWNER".equals(UserIdHolder.currentRoleCode());
    }

    /**
     * 判断当前用户是否为维修师傅。
     *
     * @return 是师傅返回 true；未登录返回 false
     */
    public static boolean isWorker() {
        // 角色编码为 WORKER 即视为维修师傅
        return "WORKER".equals(UserIdHolder.currentRoleCode());
    }

    /**
     * 判断当前用户是否为物业管理员。
     *
     * @return 是管理员返回 true；未登录返回 false
     */
    public static boolean isAdmin() {
        // 角色编码为 ADMIN 即视为物业管理员
        return "ADMIN".equals(UserIdHolder.currentRoleCode());
    }
}
