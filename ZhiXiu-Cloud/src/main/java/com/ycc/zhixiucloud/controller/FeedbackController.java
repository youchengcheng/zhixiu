package com.ycc.zhixiucloud.controller;

import com.ycc.zhixiucloud.common.annotation.ApiLog;
import com.ycc.zhixiucloud.common.result.PageResult;
import com.ycc.zhixiucloud.common.result.Result;
import com.ycc.zhixiucloud.request.FeedbackCreateRequestVO;
import com.ycc.zhixiucloud.request.PageRequestVO;
import com.ycc.zhixiucloud.response.FeedbackResponseVO;
import com.ycc.zhixiucloud.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 问题反馈接口（登录用户）：业主、师傅提交反馈并查看自己的反馈，管理端回复。
 * 未标注 @PreAuthorize，登录即可访问，但数据范围由服务层按当前登录人过滤。
 */
@Tag(name = "问题反馈")
@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
@Validated
public class FeedbackController {

    // 反馈业务服务
    private final FeedbackService feedbackService;

    @Operation(summary = "提交反馈")
    @ApiLog(module = "反馈", action = "提交反馈")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody FeedbackCreateRequestVO requestVO) {
        // 提交人与角色取自登录态，初始状态为待处理，落库后返回新反馈 ID
        return Result.ok(feedbackService.create(requestVO), "反馈已提交，我们会尽快处理");
    }

    @Operation(summary = "我的反馈")
    @GetMapping("/my")
    public Result<PageResult<FeedbackResponseVO>> my(@Valid PageRequestVO requestVO) {
        // 分页参数由 PageRequestVO 校验；服务层按当前登录人过滤，避免看到他人反馈
        return Result.ok(feedbackService.pageMine(requestVO));
    }
}
