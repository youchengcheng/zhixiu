package com.ycc.zhixiucloud.security;

/**
 * 当前登录用户上下文：由 JwtAuthenticationFilter 写入、请求结束清理。
 * 异步线程（AI 任务、通知）需显式传递用户，不能依赖本 ThreadLocal。
 *
 * <p>在整体架构中的位置：SecurityContextHolder 的轻量补充。Service、Mapper 调用层
 * 往往拿不到 HttpServletRequest，通过本类可直接读取当前用户，用于数据权限过滤
 * （例如只允许业主查看自己的工单）和操作日志记录。</p>
 *
 * <p>生命周期：{@link JwtAuthenticationFilter#doFilterInternal} 在认证成功后调用
 * {@link #set(LoginUser)}，并在 {@code finally} 中调用 {@link #clear()}。
 * ThreadLocal 绑定的是 Servlet 工作线程，线程池复用线程时若不清理会造成身份串号，
 * 因此「谁写入、谁清理」必须成对出现。</p>
 *
 * <p>异步限制：{@link ThreadLocal} 不会自动传播到线程池中的新线程，异步任务需要
 * 在提交任务时把 userId 作为参数显式传入，或在任务内部重新绑定。</p>
 */
public final class UserIdHolder {

    /** 保存当前线程的登录用户；没有登录态时为 null */
    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    /** 工具类禁止实例化 */
    private UserIdHolder() {
    }

    /**
     * 绑定当前线程的登录用户。
     *
     * @param loginUser 认证通过后的登录主体
     */
    public static void set(LoginUser loginUser) {
        // 绑定到当前线程
        HOLDER.set(loginUser);
    }

    /**
     * 获取当前线程的完整登录用户。
     *
     * @return 登录用户；未认证或异步线程中返回 null
     */
    public static LoginUser get() {
        // 读取当前线程的登录用户
        return HOLDER.get();
    }

    /**
     * 获取当前登录用户 ID。
     *
     * @return 用户 ID；无登录态时返回 null
     */
    public static Long currentUserId() {
        // 取当前线程登录用户
        LoginUser loginUser = HOLDER.get();
        // 无登录态返回 null
        return loginUser == null ? null : loginUser.getUserId();
    }

    /**
     * 获取当前登录用户的角色编码。
     *
     * @return OWNER / WORKER / ADMIN；无登录态时返回 null
     */
    public static String currentRoleCode() {
        // 取当前线程登录用户
        LoginUser loginUser = HOLDER.get();
        // 无登录态返回 null
        return loginUser == null ? null : loginUser.getRoleCode();
    }

    /**
     * 清除当前线程的登录用户。
     *
     * <p>必须在请求结束时调用，防止线程被复用后残留上一个请求的身份。</p>
     */
    public static void clear() {
        // 移除当前线程绑定，防止线程被复用后身份串号
        HOLDER.remove();
    }
}
