package com.ycc.zhixiucloud.controller;

import com.ycc.zhixiucloud.common.result.Result;
import com.ycc.zhixiucloud.response.CategoryTreeResponseVO;
import com.ycc.zhixiucloud.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 报修类型接口：业主 / 师傅 / 管理员均可访问。
 */
@Tag(name = "报修类型")
@RestController
@RequestMapping("/api/category")
@RequiredArgsConstructor
public class CategoryController {

    /** 报修类型服务：读取两级启用中的类型树 */
    private final CategoryService categoryService;

    @Operation(summary = "报修类型树")
    @GetMapping("/tree")
    public Result<List<CategoryTreeResponseVO>> tree() {
        // 返回两级启用中的类型树，供业主报修选择与 AI 助手槽位匹配复用
        return Result.ok(categoryService.tree());
    }
}
