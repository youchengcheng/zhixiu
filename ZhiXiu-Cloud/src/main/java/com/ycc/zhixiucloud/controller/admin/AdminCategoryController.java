package com.ycc.zhixiucloud.controller.admin;

import com.ycc.zhixiucloud.common.annotation.ApiLog;
import com.ycc.zhixiucloud.common.result.Result;
import com.ycc.zhixiucloud.request.CategorySaveRequestVO;
import com.ycc.zhixiucloud.request.CategoryStatusRequestVO;
import com.ycc.zhixiucloud.response.AdminCategoryResponseVO;
import com.ycc.zhixiucloud.service.CategoryService;
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
 * 报修类型管理接口：完整树、新增、修改、启停与安全删除。
 */
@Tag(name = "报修类型管理")
@RestController
@RequestMapping("/api/admin/category")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "管理端报修类型树")
    @GetMapping("/tree")
    public Result<List<AdminCategoryResponseVO>> tree() {
        return Result.ok(categoryService.adminTree());
    }

    @Operation(summary = "新增报修类型")
    @ApiLog(module = "报修类型", action = "新增报修类型")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody CategorySaveRequestVO requestVO) {
        return Result.ok(categoryService.create(requestVO), "新增成功");
    }

    @Operation(summary = "修改报修类型")
    @ApiLog(module = "报修类型", action = "修改报修类型")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id,
                               @Valid @RequestBody CategorySaveRequestVO requestVO) {
        categoryService.update(id, requestVO);
        return Result.<Void>ok(null, "保存成功");
    }

    @Operation(summary = "启用或停用报修类型")
    @ApiLog(module = "报修类型", action = "启用或停用报修类型")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id,
                                     @Valid @RequestBody CategoryStatusRequestVO requestVO) {
        categoryService.updateStatus(id, requestVO.getStatus());
        return Result.<Void>ok(null, "状态已更新");
    }

    @Operation(summary = "删除报修类型")
    @ApiLog(module = "报修类型", action = "删除报修类型")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return Result.<Void>ok(null, "删除成功");
    }
}
