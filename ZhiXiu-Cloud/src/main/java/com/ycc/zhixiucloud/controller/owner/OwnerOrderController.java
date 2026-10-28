package com.ycc.zhixiucloud.controller.owner;

import com.ycc.zhixiucloud.common.annotation.ApiLog;
import com.ycc.zhixiucloud.common.result.PageResult;
import com.ycc.zhixiucloud.common.result.Result;
import com.ycc.zhixiucloud.request.CancelOrderRequestVO;
import com.ycc.zhixiucloud.request.CreateOrderRequestVO;
import com.ycc.zhixiucloud.request.EvaluateRequestVO;
import com.ycc.zhixiucloud.request.OrderQueryRequestVO;
import com.ycc.zhixiucloud.response.OrderCreateResponseVO;
import com.ycc.zhixiucloud.response.OrderDetailResponseVO;
import com.ycc.zhixiucloud.response.OrderListResponseVO;
import com.ycc.zhixiucloud.response.WorkOrderLogResponseVO;
import com.ycc.zhixiucloud.service.EvaluationService;
import com.ycc.zhixiucloud.service.WorkOrderService;
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

import java.util.List;

/**
 * 工单接口（业主端）：提交报修、我的工单、详情、流转记录、取消、确认完工、评价。
 * 类级 @PreAuthorize 限定 OWNER；列表与详情均在服务层校验工单归属，防止查看他人工单。
 */
@Tag(name = "工单（业主）")
@RestController
@RequestMapping("/api/owner/order")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('OWNER')")
public class OwnerOrderController {

    /** 工单服务：创建、查询、取消与确认完工，状态变更统一经状态机校验 */
    private final WorkOrderService workOrderService;

    /** 评价服务：仅完工工单可评价，评价后回写师傅评分并把工单置为关闭 */
    private final EvaluationService evaluationService;

    @Operation(summary = "手动提交报修")
    @ApiLog(module = "工单", action = "业主提交报修")
    @PostMapping
    public Result<OrderCreateResponseVO> create(@Valid @RequestBody CreateOrderRequestVO requestVO) {
        // 业主手动报修，来源记为 MANUAL；创建后进入待派单/待接单流程
        return Result.ok(workOrderService.createByOwner(requestVO), "提交成功");
    }

    @Operation(summary = "我的工单列表")
    @GetMapping("/list")
    public Result<PageResult<OrderListResponseVO>> list(@Valid OrderQueryRequestVO requestVO) {
        // 分页查询本人名下工单，支持状态与时间筛选，不返回他人工单
        return Result.ok(workOrderService.pageForOwner(requestVO));
    }

    @Operation(summary = "工单详情")
    @GetMapping("/{id}")
    public Result<OrderDetailResponseVO> detail(@PathVariable Long id) {
        // 服务层校验工单归属，非本人工单直接抛业务异常
        return Result.ok(workOrderService.getDetailForOwner(id));
    }

    @Operation(summary = "工单流转记录")
    @GetMapping("/{id}/logs")
    public Result<List<WorkOrderLogResponseVO>> logs(@PathVariable Long id) {
        // 展示状态流转与操作记录，供业主跟踪报修进度
        return Result.ok(workOrderService.listLogsForOwner(id));
    }

    @Operation(summary = "取消工单")
    @ApiLog(module = "工单", action = "业主取消工单")
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id, @Valid @RequestBody CancelOrderRequestVO requestVO) {
        // 仅 PENDING/ASSIGNED/ACCEPTED 可取消；服务层校验归属并记录取消原因
        workOrderService.cancelByOwner(id, requestVO);
        // 返回成功并提示已取消
        return Result.<Void>ok(null, "已取消");
    }

    @Operation(summary = "确认完工")
    @ApiLog(module = "工单", action = "业主确认完工")
    @PostMapping("/{id}/confirm")
    public Result<Void> confirm(@PathVariable Long id) {
        // SUBMITTED -> COMPLETED：业主确认完工后工单进入可评价状态
        workOrderService.confirmByOwner(id);
        // 返回成功并提示已确认完工
        return Result.<Void>ok(null, "已确认完工");
    }

    @Operation(summary = "提交评价")
    @ApiLog(module = "工单", action = "业主提交评价")
    @PostMapping("/{id}/evaluate")
    public Result<Void> evaluate(@PathVariable Long id, @Valid @RequestBody EvaluateRequestVO requestVO) {
        // 仅 COMPLETED 可评价，评价后工单置为 CLOSED 并重算师傅平均分
        evaluationService.submit(id, requestVO);
        // 返回成功并提示评价成功
        return Result.<Void>ok(null, "评价成功");
    }
}
