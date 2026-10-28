package com.ycc.zhixiucloud.controller;

import com.ycc.zhixiucloud.common.result.PageResult;
import com.ycc.zhixiucloud.common.result.Result;
import com.ycc.zhixiucloud.common.util.SecurityUtils;
import com.ycc.zhixiucloud.request.PageRequestVO;
import com.ycc.zhixiucloud.response.AnnouncementResponseVO;
import com.ycc.zhixiucloud.service.AnnouncementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公告读接口：登录用户按自己的角色查看投放范围内的公告，写操作统一在管理端。
 * 未标注 @PreAuthorize，业主、师傅、管理员登录后均可访问；管理员列表可看到草稿。
 */
@Tag(name = "公告")
@RestController
@RequestMapping("/api/announcement")
@RequiredArgsConstructor
@Validated
public class AnnouncementController {

    // 公告服务
    private final AnnouncementService announcementService;

    @Operation(summary = "公告列表")
    @GetMapping("/list")
    public Result<PageResult<AnnouncementResponseVO>> list(@Valid PageRequestVO requestVO) {
        // 角色编码取自登录态：管理员可见全部（含草稿），业主/师傅仅见已发布且投放范围命中自己的公告
        return Result.ok(announcementService.page(requestVO, SecurityUtils.currentRoleCode()));
    }

    @Operation(summary = "公告详情")
    @GetMapping("/{id}")
    public Result<AnnouncementResponseVO> detail(@PathVariable Long id) {
        // 读取详情的同时累加阅读量，阅读量写入是该查询接口的副作用
        return Result.ok(announcementService.detail(id));
    }
}
