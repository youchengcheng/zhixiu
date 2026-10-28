package com.ycc.zhixiucloud.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ycc.zhixiucloud.common.constant.RedisKeys;
import com.ycc.zhixiucloud.common.enums.AuditStatusEnum;
import com.ycc.zhixiucloud.common.enums.RoleEnum;
import com.ycc.zhixiucloud.common.enums.WorkStatusEnum;
import com.ycc.zhixiucloud.common.exception.BizException;
import com.ycc.zhixiucloud.common.result.ResultCode;
import com.ycc.zhixiucloud.common.util.JwtUtil;
import com.ycc.zhixiucloud.common.util.SecurityUtils;
import com.ycc.zhixiucloud.entity.MaintenanceWorker;
import com.ycc.zhixiucloud.entity.Role;
import com.ycc.zhixiucloud.entity.User;
import com.ycc.zhixiucloud.mapper.MaintenanceWorkerMapper;
import com.ycc.zhixiucloud.mapper.RoleMapper;
import com.ycc.zhixiucloud.mapper.UserMapper;
import com.ycc.zhixiucloud.request.LoginRequestVO;
import com.ycc.zhixiucloud.request.OwnerRegisterRequestVO;
import com.ycc.zhixiucloud.request.RefreshTokenRequestVO;
import com.ycc.zhixiucloud.request.WorkerRegisterRequestVO;
import com.ycc.zhixiucloud.response.LoginResponseVO;
import com.ycc.zhixiucloud.response.RegisterResponseVO;
import com.ycc.zhixiucloud.response.UserInfoResponseVO;
import com.ycc.zhixiucloud.service.AuthService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 认证服务实现。
 *
 * <p>负责业主/师傅注册、登录、登出、刷新令牌与当前用户信息查询。关键设计：</p>
 * <ul>
 *   <li>双令牌：access token 用于接口鉴权，refresh token 仅用于换取新令牌；</li>
 *   <li>Redis 登记：签发时把令牌写入登记表（auth:token:{token}），服务端据此判断令牌是否仍有效；</li>
 *   <li>登出黑名单：未过期的令牌写入 auth:blacklist:{token} 并保留到自然过期，同时删除登记项；</li>
 *   <li>刷新一次性：刷新成功后立即删除旧 refresh token 的登记项，防止重放；</li>
 *   <li>事务：涉及多表写入的注册方法使用事务，任一步失败整体回滚。</li>
 * </ul>
 * 登录态存两份：JWT 本体给前端持有，Redis 登记表（auth:token:{token}）给服务端校验与登出作废。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final MaintenanceWorkerMapper maintenanceWorkerMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 业主注册。
     *
     * <p>先校验两次密码一致、登录名与手机号未被占用，再构造用户并写入 user 表。
     * 业主无需人工审核，注册成功后即可登录。</p>
     *
     * @param requestVO 注册请求，包含登录名、密码、确认密码、真实姓名、手机号
     * @return 新用户 ID
     * @throws BizException 密码不一致、登录名或手机号已存在、并发插入触发唯一索引冲突时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RegisterResponseVO registerOwner(OwnerRegisterRequestVO requestVO) {
        // 先做业务校验以便尽早返回明确提示；并发注册时仍由数据库唯一索引兜底
        assertPasswordConsistent(requestVO.getPassword(), requestVO.getConfirmPassword());
        // 预检登录名是否已被占用
        assertUsernameAvailable(requestVO.getUsername());
        // 预检手机号是否已被占用
        assertPhoneAvailable(requestVO.getPhone());

        // 业主无需人工审核，账号创建后即可直接登录
        User user = buildUser(requestVO.getUsername(), requestVO.getPassword(), requestVO.getRealName(),
                requestVO.getPhone(), RoleEnum.OWNER);
        // 写入用户表
        insertUser(user);
        // 记录注册结果
        log.info("业主注册成功：userId={}, username={}", user.getId(), user.getUsername());
        // 返回新用户 ID
        return new RegisterResponseVO(user.getId());
    }

    /**
     * 师傅注册。
     *
     * <p>先做与业主注册相同的通用校验并预检工号唯一性，再在同一事务内写入 user 与
     * maintenance_worker 两张表。新师傅初始为待审核状态（PENDING/AUDITING），
     * 管理员审核通过后才能登录接单。</p>
     *
     * @param requestVO 注册请求，额外包含工号、技能标签、身份证号
     * @return 新用户 ID
     * @throws BizException 参数校验失败、登录名/手机号/工号重复时抛出；事务保证两表写入原子性
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RegisterResponseVO registerWorker(WorkerRegisterRequestVO requestVO) {
        // 校验两次密码一致
        assertPasswordConsistent(requestVO.getPassword(), requestVO.getConfirmPassword());
        // 预检登录名是否已被占用
        assertUsernameAvailable(requestVO.getUsername());
        // 预检手机号是否已被占用
        assertPhoneAvailable(requestVO.getPhone());
        // 工号唯一性预检，便于给出友好提示
        if (maintenanceWorkerMapper.selectCount(Wrappers.<MaintenanceWorker>lambdaQuery()
                .eq(MaintenanceWorker::getWorkerNo, requestVO.getWorkerNo())) > 0) {
            // 工号已存在直接拒绝
            throw BizException.of(ResultCode.CONFLICT, "工号已存在");
        }

        // 师傅注册同时写入 user 和 maintenance_worker 两张表，由事务保证原子性
        User user = buildUser(requestVO.getUsername(), requestVO.getPassword(), requestVO.getRealName(),
                requestVO.getPhone(), RoleEnum.WORKER);
        // 写入用户表
        insertUser(user);

        // 组装师傅档案
        MaintenanceWorker worker = new MaintenanceWorker();
        // 关联用户 ID
        worker.setUserId(user.getId());
        // 工号
        worker.setWorkerNo(requestVO.getWorkerNo());
        // 技能标签
        worker.setSkillTags(requestVO.getSkillTags());
        // 身份证号，未填则存 null
        worker.setIdCard(StringUtils.hasText(requestVO.getIdCard()) ? requestVO.getIdCard() : null);
        // 注册即进入待审状态，管理员审核通过后才能登录接单
        worker.setAuditStatus(AuditStatusEnum.PENDING.getCode());
        // 工作状态标记为待审核
        worker.setWorkStatus(WorkStatusEnum.AUDITING.getCode());
        // 累计接单数初始 0
        worker.setTotalOrders(0);
        // 完成单数初始 0
        worker.setFinishOrders(0);
        // 平均评分初始 0
        worker.setAvgScore(BigDecimal.ZERO);
        // 当前负载初始 0
        worker.setCurrentLoad(0);
        // 插入时捕获唯一索引冲突
        try {
            // 写入师傅档案
            maintenanceWorkerMapper.insert(worker);
        // 并发下唯一索引冲突兜底
        } catch (DuplicateKeyException e) {
            // 并发提交时预检查可能同时通过，此处依赖唯一索引兜底；事务会一并回滚已插入的 user
            throw BizException.of(ResultCode.CONFLICT, "工号已存在");
        }
        // 记录注册结果
        log.info("师傅注册成功：userId={}, workerId={}, workerNo={}",
                user.getId(), worker.getId(), worker.getWorkerNo());
        // 返回新用户 ID
        return new RegisterResponseVO(user.getId());
    }

    /**
     * 账号登录。
     *
     * <p>校验顺序：用户名密码 → 账号状态 → 角色 → 师傅审核状态。用户名不存在与密码错误
     * 返回同一提示，避免账号枚举；全部校验通过后才更新最近登录时间，并签发双令牌。</p>
     *
     * @param requestVO 登录请求（用户名、密码）
     * @return 双令牌、access token 有效期及用户信息
     * @throws BizException 凭证错误、账号被禁用、师傅未通过审核时抛出
     */
    @Override
    public LoginResponseVO login(LoginRequestVO requestVO) {
        // 按登录名查询用户
        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery()
                .eq(User::getUsername, requestVO.getUsername()));
        // 用户不存在与密码错误返回同一提示，避免泄露账号是否注册
        if (user == null || !passwordEncoder.matches(requestVO.getPassword(), user.getPassword())) {
            // 凭证错误统一提示，避免账号枚举
            throw BizException.of(ResultCode.USERNAME_OR_PASSWORD_ERROR);
        }
        // 账号被禁用则拒绝登录
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            // 抛出账号禁用业务码
            throw BizException.of(ResultCode.ACCOUNT_DISABLED);
        }
        // 查询用户角色
        Role role = requireRole(user.getRoleId());
        // 师傅额外校验审核状态：待审核、审核不通过或被禁用时不允许登录接单
        MaintenanceWorker worker = null;
        if (RoleEnum.WORKER.getCode().equals(role.getRoleCode())) {
            // 查询师傅档案
            worker = maintenanceWorkerMapper.selectOne(Wrappers.<MaintenanceWorker>lambdaQuery()
                    .eq(MaintenanceWorker::getUserId, user.getId()));
            // 校验师傅可登录
            assertWorkerUsable(worker);
        }

        // 全部校验通过后再记录登录时间，失败的登录尝试不会刷新该字段
        User lastLogin = new User();
        // 指定用户 ID
        lastLogin.setId(user.getId());
        // 刷新最近登录时间
        lastLogin.setLastLoginTime(LocalDateTime.now());
        // 持久化登录时间
        userMapper.updateById(lastLogin);

        // 记录登录结果
        log.info("登录成功：userId={}, roleCode={}", user.getId(), role.getRoleCode());
        // 签发双令牌并返回
        return issueTokens(user, role, worker);
    }

    /**
     * 登出。
     *
     * <p>对仍有效的令牌写入黑名单（TTL 为剩余有效期），并删除 Redis 登记项，
     * 使网关与服务端都不再接受该令牌。令牌已过期或格式非法时不报错，保证接口幂等。</p>
     *
     * @param token 待作废的 access token，可为空；为空时直接返回
     */
    @Override
    public void logout(String token) {
        // 空令牌直接返回，接口保持幂等
        if (!StringUtils.hasText(token)) {
            return;
        }
        // 解析令牌并处理失效情况
        try {
            // 解析令牌声明
            Claims claims = jwtUtil.parse(token);
            // 计算剩余有效期
            long remaining = jwtUtil.getRemainingSeconds(claims);
            // JWT 无状态，只能加入黑名单并保留到其自然过期，网关据此拒绝已登出的令牌
            if (remaining > 0) {
                // 写入黑名单，TTL 为剩余有效期
                stringRedisTemplate.opsForValue().set(
                        RedisKeys.of(RedisKeys.TOKEN_BLACKLIST, token), "1", remaining, TimeUnit.SECONDS);
            }
        // 令牌已失效则跳过拉黑
        } catch (JwtException | IllegalArgumentException e) {
            // 令牌已过期或格式非法时无需拉黑，继续清理登记表，保证登出接口幂等
            log.debug("登出时 Token 已失效：{}", e.getMessage());
        }
        // 同时删除登记表，使该令牌无法再通过服务端的有效性校验
        stringRedisTemplate.delete(RedisKeys.of(RedisKeys.TOKEN, token));
        // 记录登出结果
        log.info("登出成功，Token 已作废");
    }

    /**
     * 刷新令牌。
     *
     * <p>依次校验刷新令牌的签名、类型与 Redis 登记状态，并重新确认用户状态和师傅审核状态
     * （令牌有效期内管理端可能已禁用账号）。旧刷新令牌在签发新令牌前删除，实现一次性使用。</p>
     *
     * @param requestVO 刷新请求，携带 refresh token
     * @return 新的双令牌与用户信息
     * @throws BizException 刷新令牌无效/过期/类型不符/已被使用，或账号已不可用时抛出
     */
    @Override
    public LoginResponseVO refresh(RefreshTokenRequestVO requestVO) {
        // 取出刷新令牌
        String refreshToken = requestVO.getRefreshToken();
        // 解析后的令牌声明
        Claims claims;
        // 解析失败即视为令牌无效
        try {
            // 解析刷新令牌
            claims = jwtUtil.parse(refreshToken);
        // 解析异常转业务码
        } catch (JwtException | IllegalArgumentException e) {
            // 令牌无效或过期，要求前端重新登录
            throw BizException.of(ResultCode.UNAUTHORIZED, "刷新令牌无效或已过期，请重新登录");
        }
        // 校验令牌类型，防止拿 access token 冒充刷新令牌
        if (!jwtUtil.isRefreshToken(claims)) {
            // 类型不符则拒绝
            throw BizException.of(ResultCode.UNAUTHORIZED, "非法的刷新令牌");
        }
        // 登记表能识别已登出、已被替换的令牌，比单纯验签更严格
        String registered = stringRedisTemplate.opsForValue().get(RedisKeys.of(RedisKeys.TOKEN, refreshToken));
        // 登记表无记录视为已失效
        if (!StringUtils.hasText(registered)) {
            // 抛出令牌失效业务码
            throw BizException.of(ResultCode.UNAUTHORIZED, "刷新令牌已失效，请重新登录");
        }

        // 用户状态与师傅审核状态可能在令牌有效期内被管理端修改，刷新时需重新校验
        User user = userMapper.selectById(Long.valueOf(claims.getSubject()));
        // 用户不存在或被禁用则拒绝
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            // 抛出账号禁用业务码
            throw BizException.of(ResultCode.ACCOUNT_DISABLED);
        }
        // 查询用户角色
        Role role = requireRole(user.getRoleId());
        // 师傅档案占位
        MaintenanceWorker worker = null;
        if (RoleEnum.WORKER.getCode().equals(role.getRoleCode())) {
            // 查询师傅档案
            worker = maintenanceWorkerMapper.selectOne(Wrappers.<MaintenanceWorker>lambdaQuery()
                    .eq(MaintenanceWorker::getUserId, user.getId()));
            // 重新校验师傅可用性
            assertWorkerUsable(worker);
        }
        // 刷新令牌一次性使用：先作废旧令牌再签发新令牌，避免同一令牌被重复利用
        stringRedisTemplate.delete(RedisKeys.of(RedisKeys.TOKEN, refreshToken));
        // 签发新双令牌
        return issueTokens(user, role, worker);
    }

    /**
     * 查询当前登录用户信息。
     *
     * <p>用户 ID 由 JWT 过滤器写入安全上下文，此处回查数据库以返回最新资料；
     * 师傅额外返回工号、技能标签及审核/工作状态。</p>
     *
     * @return 当前用户信息
     * @throws BizException 用户不存在或角色数据缺失时抛出
     */
    @Override
    public UserInfoResponseVO me() {
        // 当前用户 ID 由 JWT 过滤器写入上下文，这里回查数据库获取最新资料
        User user = userMapper.selectById(SecurityUtils.currentUserId());
        // 用户不存在则拒绝
        if (user == null) {
            // 抛出不存在业务码
            throw BizException.of(ResultCode.NOT_FOUND, "用户不存在");
        }
        // 查询用户角色
        Role role = requireRole(user.getRoleId());
        // 师傅档案占位
        MaintenanceWorker worker = null;
        if (RoleEnum.WORKER.getCode().equals(role.getRoleCode())) {
            // 查询师傅档案
            worker = maintenanceWorkerMapper.selectOne(Wrappers.<MaintenanceWorker>lambdaQuery()
                    .eq(MaintenanceWorker::getUserId, user.getId()));
        }
        // 组装并返回用户信息
        return buildUserInfo(user, role, worker);
    }

    /**
     * 签发双令牌并登记 Redis。
     * access token 用于日常接口鉴权，refresh token 仅用于换取新令牌；
     * 登记项的 TTL 与 JWT 有效期一致，到期后自动清理，无需额外回收任务。
     *
     * @param user   登录用户
     * @param role   用户角色，角色编码会写入 JWT 供鉴权使用
     * @param worker 师傅档案，非师傅为 null
     * @return 双令牌及用户信息
     */
    private LoginResponseVO issueTokens(User user, Role role, MaintenanceWorker worker) {
        // 取师傅记录 ID，非师傅为空
        Long workerId = worker == null ? null : worker.getId();
        // 生成访问令牌
        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getUsername(),
                role.getRoleCode(), workerId);
        // 生成刷新令牌
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getUsername(),
                role.getRoleCode(), workerId);

        // value 存用户 ID，便于鉴权时快速定位身份；Redis 中不存在即视为令牌已失效
        stringRedisTemplate.opsForValue().set(RedisKeys.of(RedisKeys.TOKEN, accessToken),
                String.valueOf(user.getId()), jwtUtil.getExpireSeconds(), TimeUnit.SECONDS);
        // 登记刷新令牌，TTL 与刷新有效期一致
        stringRedisTemplate.opsForValue().set(RedisKeys.of(RedisKeys.TOKEN, refreshToken),
                String.valueOf(user.getId()), jwtUtil.getRefreshExpireSeconds(), TimeUnit.SECONDS);

        // 组装登录出参
        return LoginResponseVO.builder()
                .token(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtUtil.getExpireSeconds())
                .userInfo(buildUserInfo(user, role, worker))
                .build();
    }

    /**
     * 组装前端所需的用户信息；师傅额外附带工号、技能标签与审核/工作状态。
     *
     * @param user   用户实体
     * @param role   用户角色
     * @param worker 师傅档案，非师傅为 null
     * @return 前端展示用的用户信息
     */
    private UserInfoResponseVO buildUserInfo(User user, Role role, MaintenanceWorker worker) {
        // 取角色编码
        String roleCode = role.getRoleCode();
        // 装配基础用户信息
        UserInfoResponseVO userInfo = UserInfoResponseVO.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .phone(user.getPhone())
                .email(user.getEmail())
                .gender(user.getGender())
                .avatar(user.getAvatar())
                .roleCode(roleCode)
                .roleName(StringUtils.hasText(role.getRoleName()) ? role.getRoleName()
                        : RoleEnum.nameOf(roleCode))
                .build();
        // 师傅补充档案字段
        if (worker != null) {
            // 关联师傅记录 ID
            userInfo.setWorkerId(worker.getId());
            // 工号
            userInfo.setWorkerNo(worker.getWorkerNo());
            // 技能标签
            userInfo.setSkillTags(worker.getSkillTags());
            // 审核状态
            userInfo.setAuditStatus(worker.getAuditStatus());
            // 工作状态
            userInfo.setWorkStatus(worker.getWorkStatus());
        }
        // 返回用户信息
        return userInfo;
    }

    /**
     * 师傅必须审核通过且未被禁用才能登录。
     *
     * @param worker 师傅档案，可能为 null（数据缺失）
     * @throws BizException 档案缺失、审核未通过或工作状态为禁用时抛出
     */
    private void assertWorkerUsable(MaintenanceWorker worker) {
        // 档案缺失视为未审核
        if (worker == null) {
            // 抛出未审核业务码
            throw BizException.of(ResultCode.WORKER_NOT_AUDITED);
        }
        // 审核未通过则拒绝登录
        if (!Integer.valueOf(AuditStatusEnum.PASSED.getCode()).equals(worker.getAuditStatus())) {
            // 抛出未审核业务码
            throw BizException.of(ResultCode.WORKER_NOT_AUDITED);
        }
        // 工作状态为禁用则拒绝登录
        if (Integer.valueOf(WorkStatusEnum.DISABLED.getCode()).equals(worker.getWorkStatus())) {
            // 抛出账号禁用提示
            throw BizException.of(ResultCode.ACCOUNT_DISABLED, "师傅账号已被禁用，请联系物业管理员");
        }
    }

    /**
     * 构造待插入的用户实体，完成密码加密与默认值填充。
     *
     * @param username    登录名
     * @param rawPassword 明文密码，仅在此处加密，不会落库
     * @param realName    真实姓名，为空时以登录名兜底
     * @param phone       手机号
     * @param roleEnum    角色枚举
     * @return 用户实体
     */
    private User buildUser(String username, String rawPassword, String realName, String phone, RoleEnum roleEnum) {
        // 按枚举查询角色
        Role role = requireRole(roleEnum);
        // 组装用户实体
        User user = new User();
        // 登录名
        user.setUsername(username);
        // 密码只保存 BCrypt 摘要，任何场景都不落明文
        user.setPassword(passwordEncoder.encode(rawPassword));
        // 未填写真实姓名时以登录名兜底，保证展示字段非空
        user.setRealName(StringUtils.hasText(realName) ? realName : username);
        // 手机号
        user.setPhone(phone);
        // 注册时性别未知（0=未知），账号状态默认为正常（1）
        user.setGender(0);
        // 绑定角色 ID
        user.setRoleId(role.getId());
        // 账号状态默认正常
        user.setStatus(1);
        // 返回待插入用户
        return user;
    }

    /**
     * 写入用户表，并把唯一索引冲突转换为业务异常。
     *
     * @param user 待插入的用户
     * @throws BizException 登录名或手机号重复时抛出
     */
    private void insertUser(User user) {
        // 插入并转换唯一索引冲突
        try {
            // 写入用户表
            userMapper.insert(user);
        // 唯一索引冲突兜底
        } catch (DuplicateKeyException e) {
            // 预检查无法覆盖并发注册，唯一索引冲突统一转换为业务异常
            throw BizException.of(ResultCode.CONFLICT, "登录名或手机号已被占用");
        }
    }

    /**
     * 按角色编码查询角色。
     *
     * @param roleEnum 角色枚举
     * @return 角色实体
     * @throws BizException 角色未初始化时抛出（视为系统错误）
     */
    private Role requireRole(RoleEnum roleEnum) {
        // 角色属于系统初始化数据，缺失说明数据初始化异常
        Role role = roleMapper.selectOne(Wrappers.<Role>lambdaQuery()
                .eq(Role::getRoleCode, roleEnum.getCode()));
        // 角色缺失视为系统异常
        if (role == null) {
            // 抛出系统错误
            throw BizException.of(ResultCode.SYSTEM_ERROR, "角色未初始化：" + roleEnum.getCode());
        }
        // 返回角色
        return role;
    }

    /**
     * 按角色 ID 查询用户所属角色。
     *
     * @param roleId 角色 ID
     * @return 角色实体
     * @throws BizException 角色不存在时抛出（视为系统错误）
     */
    private Role requireRole(Long roleId) {
        // 按 ID 查询用户所属角色，缺失同样视为系统错误
        Role role = roleMapper.selectById(roleId);
        // 角色缺失视为系统异常
        if (role == null) {
            // 抛出系统错误
            throw BizException.of(ResultCode.SYSTEM_ERROR, "角色不存在：" + roleId);
        }
        // 返回角色
        return role;
    }

    /**
     * 校验密码非空且两次输入一致。
     *
     * @param password        密码
     * @param confirmPassword 确认密码
     * @throws BizException 密码为空或两次输入不一致时抛出
     */
    private void assertPasswordConsistent(String password, String confirmPassword) {
        // 空密码或两次输入不一致，统一按参数错误处理
        if (!StringUtils.hasText(password) || !password.equals(confirmPassword)) {
            // 抛出参数错误
            throw BizException.of(ResultCode.PARAM_ERROR, "两次输入的密码不一致");
        }
    }

    /**
     * 登录名唯一性预检。
     *
     * @param username 登录名
     * @throws BizException 登录名已存在时抛出
     */
    private void assertUsernameAvailable(String username) {
        // 提前拦截重复注册，给出明确提示；并发场景由唯一索引兜底
        if (userMapper.selectCount(Wrappers.<User>lambdaQuery().eq(User::getUsername, username)) > 0) {
            // 抛出登录名已存在
            throw BizException.of(ResultCode.USERNAME_EXISTS);
        }
    }

    /**
     * 手机号唯一性预检。
     *
     * @param phone 手机号
     * @throws BizException 手机号已存在时抛出
     */
    private void assertPhoneAvailable(String phone) {
        // 手机号作为登录辅助凭证，同样要求全局唯一
        if (userMapper.selectCount(Wrappers.<User>lambdaQuery().eq(User::getPhone, phone)) > 0) {
            // 抛出手机号已存在
            throw BizException.of(ResultCode.PHONE_EXISTS);
        }
    }
}
