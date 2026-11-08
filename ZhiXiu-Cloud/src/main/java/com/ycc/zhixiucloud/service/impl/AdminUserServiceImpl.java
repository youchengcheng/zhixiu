package com.ycc.zhixiucloud.service.impl;

import com.ycc.zhixiucloud.common.enums.RoleEnum;
import com.ycc.zhixiucloud.common.exception.BizException;
import com.ycc.zhixiucloud.common.result.ResultCode;
import com.ycc.zhixiucloud.common.util.SecurityUtils;
import com.ycc.zhixiucloud.entity.Role;
import com.ycc.zhixiucloud.entity.User;
import com.ycc.zhixiucloud.mapper.RoleMapper;
import com.ycc.zhixiucloud.mapper.UserMapper;
import com.ycc.zhixiucloud.request.ResetPasswordRequestVO;
import com.ycc.zhixiucloud.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理端用户账号服务实现。
 *
 * <p>只允许重置业主和维修师傅密码，禁止管理员之间互相重置。明文密码仅在当前调用栈中
 * 交给 BCrypt 编码，不写数据库、不写文件日志和操作审计日志。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final UserMapper userMapper;

    private final RoleMapper roleMapper;

    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long userId, ResetPasswordRequestVO requestVO) {
        // 两次输入必须完全一致
        if (!requestVO.getNewPassword().equals(requestVO.getConfirmPassword())) {
            throw BizException.of(ResultCode.PARAM_ERROR, "两次输入的密码不一致");
        }
        // 接口路径中的 ID 明确定义为 user.id
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.of(ResultCode.NOT_FOUND, "用户不存在");
        }
        // 角色用于限制管理员账号不可被重置
        Role role = roleMapper.selectById(user.getRoleId());
        if (role == null) {
            throw BizException.of(ResultCode.SYSTEM_ERROR, "用户角色不存在");
        }
        RoleEnum roleEnum = RoleEnum.of(role.getRoleCode());
        if (roleEnum == RoleEnum.ADMIN) {
            throw BizException.of(ResultCode.FORBIDDEN, "不能重置其他管理员密码");
        }
        if (roleEnum != RoleEnum.OWNER && roleEnum != RoleEnum.WORKER) {
            throw BizException.of(ResultCode.FORBIDDEN, "只能重置业主或维修师傅密码");
        }
        // 防止所谓重置实际仍使用原密码，确保旧密码立即失效
        if (passwordEncoder.matches(requestVO.getNewPassword(), user.getPassword())) {
            throw BizException.of(ResultCode.CONFLICT, "新密码不能与原密码相同");
        }
        // 只更新主键与 BCrypt 密文，避免覆盖用户资料和状态
        User update = new User();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(requestVO.getNewPassword()));
        userMapper.updateById(update);
        // 审计日志只记录操作人与目标用户，不输出任何密码内容
        log.info("管理员重置用户密码：operatorId={}, targetUserId={}, role={}",
                SecurityUtils.currentUserId(), userId, role.getRoleCode());
    }
}
