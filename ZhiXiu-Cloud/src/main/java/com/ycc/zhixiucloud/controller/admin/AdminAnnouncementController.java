package com.ycc.zhixiucloud.controller.admin;

import com.ycc.zhixiucloud.common.annotation.ApiLog;
import com.ycc.zhixiucloud.common.result.Result;
import com.ycc.zhixiucloud.request.AnnouncementPublishRequestVO;
import com.ycc.zhixiucloud.request.AnnouncementSaveRequestVO;
import com.ycc.zhixiucloud.service.AnnouncementService;
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
 * 公告写接口（管理端）：新增、修改、删除与发布/下架。
 * 类级 @PreAuthorize 限定 ADMIN；新增后为草稿态，需再调发布接口才会对业主/师傅可见。
 */
@Tag(name = "公告（管理员）")
@RestController
@RequestMapping("/api/admin/announcement")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAnnouncementController {

    // 公告服务
    private final AnnouncementService announcementService;

    @Operation(summary = "新增公告")
    @ApiLog(module = "公告", action = "新增公告")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody AnnouncementSaveRequestVO requestVO) {
        // 保存为草稿并返回新公告 ID
        return Result.ok(announcementService.create(requestVO), "草稿已保存");
    }

    @Operation(summary = "修改公告")
    @ApiLog(module = "公告", action = "修改公告")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody AnnouncementSaveRequestVO requestVO) {
        // 按 ID 更新公告内容，不改变当前发布状态
        announcementService.update(id, requestVO);
        // 返回成功提示
        return Result.<Void>ok(null, "公告已更新");
    }

    @Operation(summary = "删除公告")
    @ApiLog(module = "公告", action = "删除公告")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        // 按 ID 删除公告，删除后业主/师傅端不再可见
        announcementService.delete(id);
        // 返回成功提示
        return Result.<Void>ok(null, "公告已删除");
    }

    @Operation(summary = "发布/下架公告")
    @ApiLog(module = "公告", action = "发布或下架公告")
    @PutMapping("/{id}/publish")
    public Result<Void> publish(@PathVariable Long id, @Valid @RequestBody AnnouncementPublishRequestVO requestVO) {
        // 入参 status 决定发布或下架：首次发布记录发布时间，下架保留原发布时间
        announcementService.publish(id, requestVO.getStatus());
        // 返回成功提示
        return Result.<Void>ok(null, "状态已更新");
    }
}
