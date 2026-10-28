package com.ycc.zhixiucloud.controller.admin;

import com.ycc.zhixiucloud.common.annotation.ApiLog;
import com.ycc.zhixiucloud.common.result.PageResult;
import com.ycc.zhixiucloud.common.result.Result;
import com.ycc.zhixiucloud.request.OwnerQueryRequestVO;
import com.ycc.zhixiucloud.request.OwnerStatusRequestVO;
import com.ycc.zhixiucloud.response.OwnerListResponseVO;
import com.ycc.zhixiucloud.response.PropertyResponseVO;
import com.ycc.zhixiucloud.service.OwnerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 业主管理接口（管理端）：业主列表、启用禁用、名下房产。
 * 类级 @PreAuthorize 限定 ADMIN，可按条件检索全部业主并管理其账号状态。
 */
@Tag(name = "业主管理")
@RestController
@RequestMapping("/api/admin/owner")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
public class OwnerController {

    /** 业主管理服务：列表、启停与名下房产查询 */
    private final OwnerService ownerService;

    @Operation(summary = "业主列表")
    @GetMapping("/list")
    public Result<PageResult<OwnerListResponseVO>> list(@Valid OwnerQueryRequestVO requestVO) {
        // 支持账号、姓名等条件分页查询，禁用状态的业主也会出现在结果中
        return Result.ok(ownerService.page(requestVO));
    }

    @Operation(summary = "启用/禁用业主")
    @ApiLog(module = "业主管理", action = "启用或禁用业主")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody OwnerStatusRequestVO requestVO) {
        // 1 正常 0 禁用；禁用后该业主账号无法登录，但其历史工单数据保留
        ownerService.updateStatus(id, requestVO.getStatus());
        // 返回成功并提示状态已更新
        return Result.<Void>ok(null, "状态已更新");
    }

    @Operation(summary = "查看某业主房产")
    @GetMapping("/{id}/properties")
    public Result<List<PropertyResponseVO>> properties(@PathVariable Long id) {
        // 管理员查看指定业主名下房产，不受当前登录人的归属限制
        return Result.ok(ownerService.listProperties(id));
    }
}
