package com.ycc.zhixiucloud.controller.admin;

import com.ycc.zhixiucloud.common.annotation.ApiLog;
import com.ycc.zhixiucloud.common.result.PageResult;
import com.ycc.zhixiucloud.common.result.Result;
import com.ycc.zhixiucloud.request.AdminOrderQueryRequestVO;
import com.ycc.zhixiucloud.request.AssignOrderRequestVO;
import com.ycc.zhixiucloud.request.CancelOrderRequestVO;
import com.ycc.zhixiucloud.request.ReassignOrderRequestVO;
import com.ycc.zhixiucloud.response.OrderDetailResponseVO;
import com.ycc.zhixiucloud.response.OrderListResponseVO;
import com.ycc.zhixiucloud.response.RecommendWorkerResponseVO;
import com.ycc.zhixiucloud.response.WorkOrderLogResponseVO;
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

import java.util.List;

/**
 * 工单接口（管理端）：全量工单查询、详情、流转记录、派单、改派、取消与智能推荐师傅。
 * 类级 @PreAuthorize 限定 ADMIN；管理员不受业主/师傅的数据归属限制。
 */
@Tag(name = "工单（管理员）")
@RestController
@RequestMapping("/api/admin/order")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrderController {

    /** 工单服务：查询、详情与状态流转，状态变更统一经状态机校验 */
    private final WorkOrderService workOrderService;

    /** 派单服务：抢单/派单使用 Redisson 分布式锁，推荐按技能、负载与评分排序 */
    private final AssignService assignService;

    @Operation(summary = "全部工单列表")
    @GetMapping("/list")
    public Result<PageResult<OrderListResponseVO>> list(@Valid AdminOrderQueryRequestVO requestVO) {
        // 管理员可查看全量工单，支持状态、类型、时间等条件分页筛选
        return Result.ok(workOrderService.pageForAdmin(requestVO));
    }

    @Operation(summary = "工单详情")
    @GetMapping("/{id}")
    public Result<OrderDetailResponseVO> detail(@PathVariable Long id) {
        // 管理端详情不做归属校验，可查看任意业主的工单及师傅信息
        return Result.ok(workOrderService.getDetailForAdmin(id));
    }

    @Operation(summary = "工单流转记录")
    @GetMapping("/{id}/logs")
    public Result<List<WorkOrderLogResponseVO>> logs(@PathVariable Long id) {
        // 返回状态流转轨迹，用于追溯派单、接单、到场、完工等关键节点
        return Result.ok(workOrderService.listLogsForAdmin(id));
    }

    @Operation(summary = "手动派单")
    @ApiLog(module = "工单", action = "管理员派单")
    @PostMapping("/{id}/assign")
    public Result<Void> assign(@PathVariable Long id, @Valid @RequestBody AssignOrderRequestVO requestVO) {
        // PENDING -> ASSIGNED：指定师傅并写入流转记录，师傅端随即出现待接单任务
        assignService.assign(id, requestVO);
        // 返回成功并提示派单成功
        return Result.<Void>ok(null, "派单成功");
    }

    @Operation(summary = "调整师傅（改派）")
    @ApiLog(module = "工单", action = "管理员改派")
    @PostMapping("/{id}/reassign")
    public Result<Void> reassign(@PathVariable Long id, @Valid @RequestBody ReassignOrderRequestVO requestVO) {
        // ASSIGNED/ACCEPTED -> ASSIGNED：更换接单师傅，重新写入指派关系与流转记录
        assignService.reassign(id, requestVO);
        // 返回成功并提示改派成功
        return Result.<Void>ok(null, "改派成功");
    }

    @Operation(summary = "管理员取消工单")
    @ApiLog(module = "工单", action = "管理员取消工单")
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id, @Valid @RequestBody CancelOrderRequestVO requestVO) {
        // 管理员取消非终态工单，写入取消原因并释放待接单缓存
        workOrderService.cancelByAdmin(id, requestVO);
        // 返回成功并提示已取消
        return Result.<Void>ok(null, "已取消");
    }

    @Operation(summary = "智能派单推荐（AI 可选）")
    @GetMapping("/{id}/recommend-workers")
    public Result<List<RecommendWorkerResponseVO>> recommendWorkers(@PathVariable Long id) {
        // 按技能匹配、当前负载与评分排序返回 Top3，仅作派单参考，不修改工单
        return Result.ok(assignService.recommendWorkers(id));
    }
}
