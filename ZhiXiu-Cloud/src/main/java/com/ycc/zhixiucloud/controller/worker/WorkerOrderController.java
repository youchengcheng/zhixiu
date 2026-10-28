package com.ycc.zhixiucloud.controller.worker;

import com.ycc.zhixiucloud.common.annotation.ApiLog;
import com.ycc.zhixiucloud.common.result.PageResult;
import com.ycc.zhixiucloud.common.result.Result;
import com.ycc.zhixiucloud.common.util.SecurityUtils;
import com.ycc.zhixiucloud.request.ArriveOrderRequestVO;
import com.ycc.zhixiucloud.request.OrderQueryRequestVO;
import com.ycc.zhixiucloud.request.PauseOrderRequestVO;
import com.ycc.zhixiucloud.request.SubmitOrderRequestVO;
import com.ycc.zhixiucloud.request.TransferOrderRequestVO;
import com.ycc.zhixiucloud.response.OrderDetailResponseVO;
import com.ycc.zhixiucloud.response.OrderListResponseVO;
import com.ycc.zhixiucloud.service.AssignService;
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

/**
 * 工单接口（师傅端）：待接单池、抢单、接受派单、我的工单、现场作业与提交完工。
 * 类级 @PreAuthorize 限定 WORKER；状态流转一律经状态机，抢单与接单走 Redisson 分布式锁。
 */
@Tag(name = "工单（师傅）")
@RestController
@RequestMapping("/api/worker/order")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('WORKER')")
public class WorkerOrderController {

    /** 工单服务：待接单查询、现场状态流转与完工提交 */
    private final WorkOrderService workOrderService;

    /** 派单服务：抢单/接单使用分布式锁，避免同一工单被并发抢走 */
    private final AssignService assignService;

    @Operation(summary = "待接单工单列表")
    @GetMapping("/pending")
    public Result<PageResult<OrderListResponseVO>> pending(@Valid OrderQueryRequestVO requestVO) {
        // 数据源为 Redis 待接单池，按紧急程度与时间排序，避免扫全表
        return Result.ok(workOrderService.pagePending(requestVO));
    }

    @Operation(summary = "抢单")
    @ApiLog(module = "工单", action = "师傅抢单")
    @PostMapping("/{id}/grab")
    public Result<Void> grab(@PathVariable Long id) {
        // PENDING/ASSIGNED -> ACCEPTED，分布式锁串行化争抢，已被他人抢走时返回 2003
        assignService.grab(id);
        // 返回成功并提示抢单结果
        return Result.<Void>ok(null, "抢单成功");
    }

    @Operation(summary = "接受派单")
    @ApiLog(module = "工单", action = "师傅接受派单")
    @PostMapping("/{id}/accept")
    public Result<Void> accept(@PathVariable Long id) {
        // ASSIGNED -> ACCEPTED，仅被指派的师傅本人可接单
        assignService.accept(id);
        // 返回成功并提示已接单
        return Result.<Void>ok(null, "已接单");
    }

    @Operation(summary = "我的工单列表")
    @GetMapping("/list")
    public Result<PageResult<OrderListResponseVO>> list(@Valid OrderQueryRequestVO requestVO) {
        // 先取当前登录师傅 ID，再分页查询其名下工单，避免越权查看他人任务
        return Result.ok(workOrderService.pageForWorker(SecurityUtils.requireWorkerId(), requestVO));
    }

    @Operation(summary = "工单详情")
    @GetMapping("/{id}")
    public Result<OrderDetailResponseVO> detail(@PathVariable Long id) {
        // 服务层校验是否为接单师傅，或工单仍处于待接单池
        return Result.ok(workOrderService.getDetailForWorker(id));
    }

    @Operation(summary = "到场签到")
    @ApiLog(module = "工单", action = "师傅到场签到")
    @PostMapping("/{id}/arrive")
    public Result<Void> arrive(@PathVariable Long id, @RequestBody(required = false) ArriveOrderRequestVO requestVO) {
        // ACCEPTED -> GOING：到场签到，可附带现场信息，标志现场作业开始
        workOrderService.arrive(id, requestVO);
        // 返回成功并提示签到结果
        return Result.<Void>ok(null, "签到成功");
    }

    @Operation(summary = "暂停工单")
    @ApiLog(module = "工单", action = "师傅暂停工单")
    @PostMapping("/{id}/pause")
    public Result<Void> pause(@PathVariable Long id, @Valid @RequestBody PauseOrderRequestVO requestVO) {
        // GOING -> PAUSED：需填写暂停原因，便于业主在进度中看到停滞说明
        workOrderService.pause(id, requestVO);
        // 返回成功并提示已暂停
        return Result.<Void>ok(null, "已暂停");
    }

    @Operation(summary = "恢复工单")
    @ApiLog(module = "工单", action = "师傅恢复工单")
    @PostMapping("/{id}/resume")
    public Result<Void> resume(@PathVariable Long id) {
        // PAUSED -> GOING：恢复现场作业，继续后续流程
        workOrderService.resume(id);
        // 返回成功并提示已恢复
        return Result.<Void>ok(null, "已恢复");
    }

    @Operation(summary = "申请转单")
    @ApiLog(module = "工单", action = "师傅申请转单")
    @PostMapping("/{id}/transfer")
    public Result<Void> transfer(@PathVariable Long id, @Valid @RequestBody TransferOrderRequestVO requestVO) {
        // GOING -> TRANSFER_PENDING：提交转单申请，等待管理员改派
        workOrderService.transfer(id, requestVO);
        // 返回成功并提示转单申请已提交
        return Result.<Void>ok(null, "转单申请已提交");
    }

    @Operation(summary = "提交完工")
    @ApiLog(module = "工单", action = "师傅提交完工")
    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id, @Valid @RequestBody SubmitOrderRequestVO requestVO) {
        // GOING -> SUBMITTED：提交完工说明与照片，等待业主确认完工
        workOrderService.submit(id, requestVO);
        // 返回成功并提示完工已提交
        return Result.<Void>ok(null, "已提交完工，等待业主确认");
    }
}
