package com.ycc.zhixiucloud.controller;

import com.ycc.zhixiucloud.common.result.Result;
import com.ycc.zhixiucloud.common.util.JwtUtil;
import com.ycc.zhixiucloud.request.LoginRequestVO;
import com.ycc.zhixiucloud.request.OwnerRegisterRequestVO;
import com.ycc.zhixiucloud.request.RefreshTokenRequestVO;
import com.ycc.zhixiucloud.request.WorkerRegisterRequestVO;
import com.ycc.zhixiucloud.response.LoginResponseVO;
import com.ycc.zhixiucloud.response.RegisterResponseVO;
import com.ycc.zhixiucloud.response.UserInfoResponseVO;
import com.ycc.zhixiucloud.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口。
 * /api/auth/** 整体位于安全白名单，可匿名访问；登出与 /me 仍依赖有效访问令牌。
 * 注意：本控制器不标注 @ApiLog，避免密码明文进入日志。
 */
@Tag(name = "认证")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    /** 认证业务服务：注册、登录、双令牌续期与登出 */
    private final AuthService authService;
    /** JWT 工具：从 Authorization 头解析访问令牌，供登出时拉黑使用 */
    private final JwtUtil jwtUtil;

    @Operation(summary = "业主注册")
    @PostMapping("/register/owner")
    public Result<RegisterResponseVO> registerOwner(@Valid @RequestBody OwnerRegisterRequestVO requestVO) {
        // 直接创建正常状态的业主账号，密码在服务层加密后落库，返回新用户 ID
        return Result.ok(authService.registerOwner(requestVO), "注册成功");
    }

    @Operation(summary = "师傅注册（注册后需管理员审核）")
    @PostMapping("/register/worker")
    public Result<RegisterResponseVO> registerWorker(@Valid @RequestBody WorkerRegisterRequestVO requestVO) {
        // 同时写入 user 与 maintenance_worker，审核状态为待审，审核通过前不能接单
        return Result.ok(authService.registerWorker(requestVO), "注册成功，等待管理员审核");
    }

    @Operation(summary = "登录")
    @PostMapping("/login")
    public Result<LoginResponseVO> login(@Valid @RequestBody LoginRequestVO requestVO) {
        // 校验账号密码与启用状态，签发 access/refresh 双令牌并写入 Redis 登记表
        return Result.ok(authService.login(requestVO), "登录成功");
    }

    @Operation(summary = "登出")
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        // 从请求头解析当前访问令牌，加入黑名单并删除 Redis 中的令牌登记
        authService.logout(jwtUtil.resolveToken(request));
        // 登出完成，返回统一成功响应
        return Result.<Void>ok(null, "登出成功");
    }

    @Operation(summary = "刷新 Token")
    @PostMapping("/refresh")
    public Result<LoginResponseVO> refresh(@Valid @RequestBody RefreshTokenRequestVO requestVO) {
        // 旧 refresh token 一次性作废，校验通过后返回新的双令牌
        return Result.ok(authService.refresh(requestVO), "刷新成功");
    }

    @Operation(summary = "获取当前登录用户")
    @GetMapping("/me")
    public Result<UserInfoResponseVO> me() {
        // 从安全上下文取当前登录用户，返回角色与基础资料，供前端刷新登录态
        return Result.ok(authService.me());
    }
}
