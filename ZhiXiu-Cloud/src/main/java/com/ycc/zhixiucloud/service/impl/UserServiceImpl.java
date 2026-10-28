package com.ycc.zhixiucloud.service.impl;

import com.ycc.zhixiucloud.common.enums.RoleEnum;
import com.ycc.zhixiucloud.common.exception.BizException;
import com.ycc.zhixiucloud.common.result.ResultCode;
import com.ycc.zhixiucloud.common.util.SecurityUtils;
import com.ycc.zhixiucloud.entity.Role;
import com.ycc.zhixiucloud.entity.User;
import com.ycc.zhixiucloud.mapper.RoleMapper;
import com.ycc.zhixiucloud.mapper.UserMapper;
import com.ycc.zhixiucloud.request.ChangePasswordRequestVO;
import com.ycc.zhixiucloud.request.UpdateProfileRequestVO;
import com.ycc.zhixiucloud.response.UserProfileResponseVO;
import com.ycc.zhixiucloud.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 用户中心服务实现。
 *
 * <p>提供当前登录用户的资料查询、资料修改与密码修改。当前用户身份统一从安全上下文
 * （JWT 过滤器写入）获取，接口不接受前端传入的用户 ID，避免越权修改他人资料。</p>
 *
 * <p>资料修改采用白名单字段：仅真实姓名、邮箱、性别、头像可自助更新，
 * 用户名、手机号、角色等敏感字段不在此接口开放。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 查询当前登录用户的资料。
     *
     * @return 用户资料（含角色编码、注册时间）
     * @throws BizException 用户不存在或身份信息非法时抛出
     */
    @Override
    public UserProfileResponseVO getProfile() {
        // 取当前登录用户，用户 ID 来自安全上下文
        User user = requireCurrentUser();
        // 查询用户绑定的角色
        Role role = roleMapper.selectById(user.getRoleId());
        // 角色可能已删除，缺失时返回空编码
        String roleCode = role == null ? null : role.getRoleCode();
        // 装配用户资料展示对象返回
        return UserProfileResponseVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .phone(user.getPhone())
                .email(user.getEmail())
                .avatar(user.getAvatar())
                .gender(user.getGender())
                .roleCode(roleCode)
                .createTime(user.getCreateTime())
                .lastLoginTime(user.getLastLoginTime())
                .build();
    }

    /**
     * 修改当前登录用户的资料。
     *
     * <p>仅允许修改真实姓名、邮箱、性别、头像；字段为空时保留原值，实现局部更新。
     * 用户 ID 取自安全上下文，前端无法借此接口修改他人资料。</p>
     *
     * @param requestVO 资料修改请求
     * @throws BizException 用户不存在或身份信息非法时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProfile(UpdateProfileRequestVO requestVO) {
        // 取当前登录用户
        User user = requireCurrentUser();
        // 只组装主键与白名单字段，防止越权修改
        User update = new User();
        // 指定当前用户主键
        update.setId(user.getId());
        // 只允许修改这几项，用户名 / 手机号 / 角色不可自助修改
        update.setRealName(StringUtils.hasText(requestVO.getRealName())
                ? requestVO.getRealName() : user.getRealName());
        // 入参为空时保留原邮箱
        update.setEmail(StringUtils.hasText(requestVO.getEmail())
                ? requestVO.getEmail() : user.getEmail());
        // 性别为空时保留原值
        update.setGender(requestVO.getGender() == null ? user.getGender() : requestVO.getGender());
        // 入参为空时保留原头像
        update.setAvatar(StringUtils.hasText(requestVO.getAvatar())
                ? requestVO.getAvatar() : user.getAvatar());
        // 按主键更新非空字段
        userMapper.updateById(update);
        // 记录资料更新日志
        log.info("用户资料更新：userId={}", user.getId());
    }

    /**
     * 修改当前登录用户的密码。
     *
     * <p>依次校验原密码、两次新密码一致、新旧密码不同，全部通过后使用 BCrypt
     * 重新生成密码摘要并更新，数据库中不保存明文。</p>
     *
     * @param requestVO 改密请求（原密码、新密码、确认密码）
     * @throws BizException 原密码错误、两次新密码不一致、新旧密码相同或用户不存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(ChangePasswordRequestVO requestVO) {
        // 取当前登录用户，改密对象固定为登录人
        User user = requireCurrentUser();
        // 先校验原密码是否匹配
        if (!passwordEncoder.matches(requestVO.getOldPassword(), user.getPassword())) {
            // 原密码不匹配，拒绝改密
            throw BizException.of(ResultCode.PARAM_ERROR, "原密码不正确");
        }
        // 两次输入的新密码必须一致
        if (!requestVO.getNewPassword().equals(requestVO.getConfirmPassword())) {
            // 两次新密码必须一致
            throw BizException.of(ResultCode.PARAM_ERROR, "两次输入的新密码不一致");
        }
        // 新密码不允许与原密码相同
        if (requestVO.getNewPassword().equals(requestVO.getOldPassword())) {
            // 新旧密码不允许相同
            throw BizException.of(ResultCode.PARAM_ERROR, "新密码不能与原密码相同");
        }
        // 只更新主键与密码字段
        User update = new User();
        // 指定主键
        update.setId(user.getId());
        // 用 BCrypt 重新生成摘要，数据库不存明文
        update.setPassword(passwordEncoder.encode(requestVO.getNewPassword()));
        // 按主键更新密码字段
        userMapper.updateById(update);
        // 记录改密日志
        log.info("用户修改密码：userId={}", user.getId());
    }

    /**
     * 获取当前登录用户并校验身份。
     *
     * <p>用户 ID 来自 JWT 上下文；同时校验角色编码合法，防止伪造或失效的角色信息
     * 进入业务层。校验失败直接抛出受控业务异常。</p>
     *
     * @return 当前用户实体
     * @throws BizException 用户不存在或角色非法时抛出
     */
    private User requireCurrentUser() {
        // 用户 ID 来自 JWT 上下文，接口不接受前端传入
        User user = userMapper.selectById(SecurityUtils.currentUserId());
        // 账号为空说明用户不存在
        if (user == null) {
            // 用户不存在或无对应记录
            throw BizException.of(ResultCode.NOT_FOUND, "用户不存在");
        }
        // 角色编码需为已知枚举，防止伪造身份
        if (RoleEnum.of(SecurityUtils.currentRoleCode()) == null) {
            // 角色编码非法，视为无权限
            throw BizException.of(ResultCode.FORBIDDEN);
        }
        // 校验通过，返回当前用户实体
        return user;
    }
}
