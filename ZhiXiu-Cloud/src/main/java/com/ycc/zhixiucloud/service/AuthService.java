package com.ycc.zhixiucloud.service;

import com.ycc.zhixiucloud.request.LoginRequestVO;
import com.ycc.zhixiucloud.request.OwnerRegisterRequestVO;
import com.ycc.zhixiucloud.request.RefreshTokenRequestVO;
import com.ycc.zhixiucloud.request.WorkerRegisterRequestVO;
import com.ycc.zhixiucloud.response.LoginResponseVO;
import com.ycc.zhixiucloud.response.RegisterResponseVO;
import com.ycc.zhixiucloud.response.UserInfoResponseVO;

/**
 * 认证服务：业主/师傅注册、登录、登出、刷新 Token 与当前用户信息查询。
 *
 * <p>职责边界：只负责账号身份与登录态，不处理具体业务权限；接口鉴权由 JWT 过滤器完成。</p>
 *
 * <p>主要使用方：AuthController、JWT 认证过滤器。</p>
 *
 * <p>关键实现要点：</p>
 * <ul>
 *   <li>密码使用 BCrypt 摘要存储，任何场景都不落明文；</li>
 *   <li>登录态双份保存：JWT 交给前端，Redis 登记表 auth:token:{token} 供服务端校验与作废；</li>
 *   <li>登出把 access token 写入黑名单（保留至自然过期）并删除登记；</li>
 *   <li>刷新令牌一次性使用，刷新时重新校验用户状态与师傅审核状态；</li>
 *   <li>注册涉及多表写入（师傅注册写 user + maintenance_worker），使用事务保证原子性。</li>
 * </ul>
 */
public interface AuthService {

    /**
     * 业主注册。
     *
     * <p>校验两次密码一致、登录名与手机号唯一；密码经 BCrypt 加密后写入 user 表。
     * 业主无需人工审核，账号创建后即可直接登录。</p>
     *
     * @param requestVO 注册信息（登录名、密码、确认密码、姓名、手机号）
     * @return 新用户 ID
     * @throws com.ycc.zhixiucloud.common.exception.BizException 两次密码不一致（400）、
     *         登录名已存在（1001）、手机号已注册（1002）、并发注册触发唯一索引冲突（409）
     */
    RegisterResponseVO registerOwner(OwnerRegisterRequestVO requestVO);

    /**
     * 师傅注册。
     *
     * <p>在同一事务内写入 user 与 maintenance_worker 两张表：user 提供登录凭证，
     * maintenance_worker 保存工号、技能标签与审核状态。注册后审核状态为待审核、
     * 工作状态为审核中，管理员审核通过前无法登录接单。</p>
     *
     * @param requestVO 注册信息（账号信息、工号、技能标签、身份证号等）
     * @return 新用户 ID（注意不是 maintenance_worker.id）
     * @throws com.ycc.zhixiucloud.common.exception.BizException 密码不一致（400）、
     *         登录名/手机号重复（1001/1002）、工号已存在（409）
     */
    RegisterResponseVO registerWorker(WorkerRegisterRequestVO requestVO);

    /**
     * 用户名密码登录。
     *
     * <p>校验顺序：账号存在且密码正确 → 账号未被禁用 → 角色存在 → 师傅审核通过且未被禁用。
     * 全部通过后更新最后登录时间，签发 access/refresh 双令牌并写入 Redis 登记表。</p>
     *
     * @param requestVO 登录请求（username、password）
     * @return 双令牌、access token 有效期与用户信息（师傅额外带工号、技能、审核/工作状态）
     * @throws com.ycc.zhixiucloud.common.exception.BizException 用户名或密码错误（1003）、
     *         账号被禁用（1004）、师傅未通过审核（1005）
     */
    LoginResponseVO login(LoginRequestVO requestVO);

    /**
     * 登出。
     *
     * <p>将 access token 加入 Redis 黑名单（TTL 为令牌剩余有效期），并删除其登记项；
     * 令牌已过期或格式非法时只清理登记，保证接口幂等。</p>
     *
     * @param token 当前请求携带的 access token，允许为空（为空时直接返回）
     */
    void logout(String token);

    /**
     * 刷新登录态。
     *
     * <p>校验刷新令牌签名、类型与 Redis 登记有效性，并重新检查用户状态和师傅审核状态
     * （防止令牌有效期内被管理端禁用）。刷新令牌一次性使用：旧令牌先作废，再签发新双令牌。</p>
     *
     * @param requestVO 刷新请求，包含 refreshToken
     * @return 新的双令牌与用户信息
     * @throws com.ycc.zhixiucloud.common.exception.BizException 刷新令牌无效/已过期/已被使用（401）、
     *         账号被禁用（1004）、师傅未通过审核（1005）
     */
    LoginResponseVO refresh(RefreshTokenRequestVO requestVO);

    /**
     * 查询当前登录用户的实时信息。
     *
     * <p>用户 ID 取自 JWT 过滤器写入的安全上下文，随后回查数据库获取最新资料，
     * 因此角色或审核状态变更后能立即反映。</p>
     *
     * @return 当前用户信息；师傅额外附带工号、技能标签与审核/工作状态
     * @throws com.ycc.zhixiucloud.common.exception.BizException 用户不存在（404）
     *         或角色数据缺失（500）
     */
    UserInfoResponseVO me();
}
