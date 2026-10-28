package com.ycc.zhixiucloud.controller.admin;

import com.ycc.zhixiucloud.common.result.PageResult;
import com.ycc.zhixiucloud.common.result.Result;
import com.ycc.zhixiucloud.request.OperationLogQueryRequestVO;
import com.ycc.zhixiucloud.response.OperationLogResponseVO;
import com.ycc.zhixiucloud.service.OperationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作日志接口（管理端）：按模块、操作人、结果与时间范围分页查询。
 * 类级 @PreAuthorize 限定 ADMIN，日志只读，不提供修改与删除入口。
 * 日志由 @ApiLog 切面采集、MQ 异步落库，这里只读。
 */
@Tag(name = "操作日志")
@RestController
@RequestMapping("/api/admin/log")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
public class AdminLogController {

    /** 操作日志查询服务：日志由 @ApiLog 切面采集、经 MQ 异步落库，此处只读 */
    private final OperationLogService operationLogService;

    @Operation(summary = "操作日志列表")
    @GetMapping("/list")
    public Result<PageResult<OperationLogResponseVO>> list(@Valid OperationLogQueryRequestVO requestVO) {
        // 支持模块、操作人、结果与时间范围组合筛选，按条件分页查询
        return Result.ok(operationLogService.page(requestVO));
    }
}
