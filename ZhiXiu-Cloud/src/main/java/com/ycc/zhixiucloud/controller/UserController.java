package com.ycc.zhixiucloud.controller;

import com.ycc.zhixiucloud.common.annotation.ApiLog;
import com.ycc.zhixiucloud.common.result.Result;
import com.ycc.zhixiucloud.request.ChangePasswordRequestVO;
import com.ycc.zhixiucloud.request.UpdateProfileRequestVO;
import com.ycc.zhixiucloud.response.UserProfileResponseVO;
import com.ycc.zhixiucloud.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户中心接口：业主、师傅、管理员三角色通用，操作对象始终是当前登录用户。
 * 未标注 @PreAuthorize，认证由全局安全配置保证；资料修改不涉及角色等敏感字段。
 */
@Tag(name = "用户中心")
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "获取个人信息")
    @GetMapping("/profile")
    public Result<UserProfileResponseVO> profile() {
        // 返回当前登录用户的基础资料与角色信息，用户中心页面初始化时调用
        return Result.ok(userService.getProfile());
    }

    @Operation(summary = "修改个人信息")
    @ApiLog(module = "用户中心", action = "修改个人信息")
    @PutMapping("/profile")
    public Result<Void> updateProfile(@Valid @RequestBody UpdateProfileRequestVO requestVO) {
        // 仅允许修改真实姓名、邮箱、性别、头像，角色与账号等敏感字段由服务层忽略
        userService.updateProfile(requestVO);
        // 返回成功并提示保存成功
        return Result.<Void>ok(null, "保存成功");
    }

    @Operation(summary = "修改密码")
    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequestVO requestVO) {
        // 服务层校验原密码并确认两次新密码一致，通过后更新密码哈希
        userService.changePassword(requestVO);
        // 返回成功并提示密码修改成功
        return Result.<Void>ok(null, "密码修改成功");
    }
}
