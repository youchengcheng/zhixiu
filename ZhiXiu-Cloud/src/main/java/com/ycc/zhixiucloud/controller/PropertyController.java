package com.ycc.zhixiucloud.controller;

import com.ycc.zhixiucloud.common.annotation.ApiLog;
import com.ycc.zhixiucloud.common.result.Result;
import com.ycc.zhixiucloud.request.PropertySaveRequestVO;
import com.ycc.zhixiucloud.response.PropertyResponseVO;
import com.ycc.zhixiucloud.service.PropertyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 房产接口：类级 @PreAuthorize 限定 OWNER，仅业主可用，且只能操作本人名下房产。
 * 归属校验收敛在 PropertyService，避免通过篡改房产 ID 越权修改或删除。
 */
@Tag(name = "房产（业主）")
@RestController
@RequestMapping("/api/property")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OWNER')")
public class PropertyController {

    private final PropertyService propertyService;

    @Operation(summary = "我的房产列表")
    @GetMapping("/list")
    public Result<List<PropertyResponseVO>> list() {
        // 仅返回当前登录业主名下房产，默认房产排在最前
        return Result.ok(propertyService.listMine());
    }

    @Operation(summary = "新增房产")
    @ApiLog(module = "房产", action = "新增房产")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody PropertySaveRequestVO requestVO) {
        // 归属业主取自登录态，不接收 ownerId，防止替他人建档
        return Result.ok(propertyService.create(requestVO), "新增成功");
    }

    @Operation(summary = "修改房产")
    @ApiLog(module = "房产", action = "修改房产")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody PropertySaveRequestVO requestVO) {
        // 服务层校验房产归属，防止通过 ID 越权修改他人房产
        propertyService.update(id, requestVO);
        // 返回成功并提示保存成功
        return Result.<Void>ok(null, "保存成功");
    }

    @Operation(summary = "删除房产")
    @ApiLog(module = "房产", action = "删除房产")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        // 服务层校验归属后再删除，已关联工单的房产是否允许删除由服务层决定
        propertyService.delete(id);
        // 返回成功并提示删除成功
        return Result.<Void>ok(null, "删除成功");
    }

    @Operation(summary = "设为默认房产")
    @ApiLog(module = "房产", action = "设为默认房产")
    @PutMapping("/{id}/default")
    public Result<Void> setDefault(@PathVariable Long id) {
        // 同一业主只保留一套默认房产，服务层会把其余房产的默认标记清除
        propertyService.setDefault(id);
        // 返回成功并提示设置成功
        return Result.<Void>ok(null, "设置成功");
    }
}
