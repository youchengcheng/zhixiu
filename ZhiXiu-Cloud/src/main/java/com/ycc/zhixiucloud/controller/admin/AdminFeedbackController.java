package com.ycc.zhixiucloud.controller.admin;

import com.ycc.zhixiucloud.common.annotation.ApiLog;
import com.ycc.zhixiucloud.common.result.PageResult;
import com.ycc.zhixiucloud.common.result.Result;
import com.ycc.zhixiucloud.request.FeedbackQueryRequestVO;
import com.ycc.zhixiucloud.request.FeedbackReplyRequestVO;
import com.ycc.zhixiucloud.response.FeedbackResponseVO;
import com.ycc.zhixiucloud.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 问题反馈接口（管理端）：按状态与类型筛选，并回复反馈。
 * 类级 @PreAuthorize 限定 ADMIN，可查看并回复全部用户的反馈。
 */
@Tag(name = "问题反馈（管理员）")
@RestController
@RequestMapping("/api/admin/feedback")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
public class AdminFeedbackController {

    // 反馈业务服务
    private final FeedbackService feedbackService;

    @Operation(summary = "反馈列表（管理）")
    @GetMapping("/list")
    public Result<PageResult<FeedbackResponseVO>> list(@Valid FeedbackQueryRequestVO requestVO) {
        // 支持状态与类型筛选，分页返回全量反馈，供管理员跟进处理
        return Result.ok(feedbackService.pageForAdmin(requestVO));
    }

    @Operation(summary = "回复反馈")
    @ApiLog(module = "反馈", action = "回复反馈")
    @PostMapping("/{id}/reply")
    public Result<Void> reply(@PathVariable Long id, @Valid @RequestBody FeedbackReplyRequestVO requestVO) {
        // 写入回复内容与回复人；未指定状态时服务层默认置为「处理中」
        feedbackService.reply(id, requestVO);
        // 无返回数据，仅提示回复成功
        return Result.<Void>ok(null, "回复已发送");
    }
}
