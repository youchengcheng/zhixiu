package com.ycc.zhixiucloud.controller.admin;

import com.ycc.zhixiucloud.common.annotation.ApiLog;
import com.ycc.zhixiucloud.common.result.Result;
import com.ycc.zhixiucloud.request.AdminPropertySaveRequestVO;
import com.ycc.zhixiucloud.service.PropertyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 房产管理接口（管理端）：代业主新增、修改、删除房产。
 * 类级 @PreAuthorize 限定 ADMIN，操作时不校验当前登录人的房产归属。
 */
@Tag(name = "房产管理")
@RestController
@RequestMapping("/api/admin/property")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPropertyController {

    private final PropertyService propertyService;

    @Operation(summary = "新增房产")
    @ApiLog(module = "房产管理", action = "管理员新增房产")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody AdminPropertySaveRequestVO requestVO) {
        // 归属业主由入参 ownerId 指定，管理员代业主建档
        return Result.ok(propertyService.createForOwner(requestVO), "新增成功");
    }

    @Operation(summary = "修改房产")
    @ApiLog(module = "房产管理", action = "管理员修改房产")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody AdminPropertySaveRequestVO requestVO) {
        // 不校验当前登录人归属，可修改任意业主的房产
        propertyService.updateByAdmin(id, requestVO);
        // 返回成功并提示保存成功
        return Result.<Void>ok(null, "保存成功");
    }

    @Operation(summary = "删除房产")
    @ApiLog(module = "房产管理", action = "管理员删除房产")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        // 管理员可删除任意业主房产，关联数据校验由服务层完成
        propertyService.deleteByAdmin(id);
        // 返回成功并提示删除成功
        return Result.<Void>ok(null, "删除成功");
    }
}
