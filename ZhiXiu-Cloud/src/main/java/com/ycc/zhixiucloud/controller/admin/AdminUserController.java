package com.ycc.zhixiucloud.controller.admin;

import com.ycc.zhixiucloud.common.annotation.ApiLog;
import com.ycc.zhixiucloud.common.result.Result;
import com.ycc.zhixiucloud.request.ResetPasswordRequestVO;
import com.ycc.zhixiucloud.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户账号管理接口。
 */
@Tag(name = "用户管理")
@RestController
@RequestMapping("/api/admin/user")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @Operation(summary = "重置用户密码")
    @ApiLog(module = "用户管理", action = "重置用户密码")
    @PutMapping("/{id}/reset-password")
    public Result<Void> resetPassword(@PathVariable Long id,
                                      @Valid @RequestBody ResetPasswordRequestVO requestVO) {
        adminUserService.resetPassword(id, requestVO);
        return Result.<Void>ok(null, "密码重置成功");
    }
}
