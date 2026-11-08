package com.ycc.zhixiucloud.controller;

import com.ycc.zhixiucloud.common.result.PageResult;
import com.ycc.zhixiucloud.common.result.Result;
import com.ycc.zhixiucloud.request.PageRequestVO;
import com.ycc.zhixiucloud.response.NotificationResponseVO;
import com.ycc.zhixiucloud.response.NotificationUnreadResponseVO;
import com.ycc.zhixiucloud.service.NotificationInboxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消息中心接口：三角色登录后均可访问，只能查看和操作自己的通知。
 */
@Tag(name = "消息中心")
@RestController
@RequestMapping("/api/notification")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationInboxService notificationInboxService;

    @Operation(summary = "通知列表")
    @GetMapping("/list")
    public Result<PageResult<NotificationResponseVO>> list(@Valid PageRequestVO requestVO) {
        return Result.ok(notificationInboxService.page(requestVO));
    }

    @Operation(summary = "未读通知数量")
    @GetMapping("/unread-count")
    public Result<NotificationUnreadResponseVO> unreadCount() {
        return Result.ok(notificationInboxService.unreadCount());
    }

    @Operation(summary = "标记单条通知已读")
    @PutMapping("/{id}/read")
    public Result<Void> read(@PathVariable Long id) {
        notificationInboxService.read(id);
        return Result.<Void>ok(null, "已标记为已读");
    }

    @Operation(summary = "全部标记已读")
    @PutMapping("/read-all")
    public Result<Void> readAll() {
        notificationInboxService.readAll();
        return Result.<Void>ok(null, "全部通知已读");
    }
}
