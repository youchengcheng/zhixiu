package com.ycc.zhixiucloud.controller.admin;

import com.ycc.zhixiucloud.common.annotation.ApiLog;
import com.ycc.zhixiucloud.common.result.PageResult;
import com.ycc.zhixiucloud.common.result.Result;
import com.ycc.zhixiucloud.request.WorkerAuditRequestVO;
import com.ycc.zhixiucloud.request.WorkerQueryRequestVO;
import com.ycc.zhixiucloud.request.WorkerStatusRequestVO;
import com.ycc.zhixiucloud.response.WorkerDetailResponseVO;
import com.ycc.zhixiucloud.response.WorkerListResponseVO;
import com.ycc.zhixiucloud.response.WorkerLoadResponseVO;
import com.ycc.zhixiucloud.service.MaintenanceWorkerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 师傅管理接口（管理端）：列表、详情、审核、启用禁用与负载查询。
 * 类级 @PreAuthorize 限定 ADMIN，审核与启停直接影响师傅能否登录接单。
 */
@Tag(name = "师傅管理")
@RestController
@RequestMapping("/api/admin/worker")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
public class WorkerController {

    /** 师傅服务：审核、启停、负载与个人统计 */
    private final MaintenanceWorkerService maintenanceWorkerService;

    @Operation(summary = "师傅列表")
    @GetMapping("/list")
    public Result<PageResult<WorkerListResponseVO>> list(@Valid WorkerQueryRequestVO requestVO) {
        // 支持审核状态、工种、关键词等条件分页查询师傅档案
        return Result.ok(maintenanceWorkerService.page(requestVO));
    }

    @Operation(summary = "师傅负载列表")
    @GetMapping("/load")
    public Result<List<WorkerLoadResponseVO>> load() {
        // 返回各师傅当前进行中的工单数，供管理员人工派单时参考
        return Result.ok(maintenanceWorkerService.listLoad());
    }

    @Operation(summary = "师傅详情")
    @GetMapping("/{id}")
    public Result<WorkerDetailResponseVO> detail(@PathVariable Long id) {
        // 返回资质、技能与统计数据，供审核和人工派单决策
        return Result.ok(maintenanceWorkerService.getDetail(id));
    }

    @Operation(summary = "师傅审核")
    @ApiLog(module = "师傅管理", action = "师傅审核")
    @PostMapping("/{id}/audit")
    public Result<Void> audit(@PathVariable Long id, @Valid @RequestBody WorkerAuditRequestVO requestVO) {
        // 1 通过 2 驳回：通过后师傅才能接单，驳回时可填写审核备注说明原因
        maintenanceWorkerService.audit(id, requestVO);
        // 返回成功并提示审核完成
        return Result.<Void>ok(null, "审核完成");
    }

    @Operation(summary = "启用/禁用师傅")
    @ApiLog(module = "师傅管理", action = "启用或禁用师傅")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody WorkerStatusRequestVO requestVO) {
        // 1 在线 4 禁用；禁用后师傅无法接单，并会从派单推荐中排除
        maintenanceWorkerService.updateWorkStatus(id, requestVO.getWorkStatus());
        // 返回成功并提示状态已更新
        return Result.<Void>ok(null, "状态已更新");
    }
}
