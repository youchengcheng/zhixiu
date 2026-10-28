package com.ycc.zhixiucloud.controller.worker;

import com.ycc.zhixiucloud.common.annotation.ApiLog;
import com.ycc.zhixiucloud.common.result.Result;
import com.ycc.zhixiucloud.common.util.SecurityUtils;
import com.ycc.zhixiucloud.request.WorkStatusRequestVO;
import com.ycc.zhixiucloud.response.WorkerStatisticsResponseVO;
import com.ycc.zhixiucloud.service.MaintenanceWorkerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 师傅个人接口：个人统计与在线状态切换。
 * 类级 @PreAuthorize 限定 WORKER，师傅 ID 一律从登录态解析，不接受前端传入。
 */
@Tag(name = "师傅个人")
@RestController
@RequestMapping("/api/worker")
@RequiredArgsConstructor
@PreAuthorize("hasRole('WORKER')")
public class WorkerProfileController {

    /** 师傅服务：个人统计与在线状态维护 */
    private final MaintenanceWorkerService maintenanceWorkerService;

    @Operation(summary = "个人统计")
    @GetMapping("/statistics")
    public Result<WorkerStatisticsResponseVO> statistics() {
        // 按当前登录师傅 ID 统计接单量、完工量与评分，未绑定师傅档案时抛 1005
        return Result.ok(maintenanceWorkerService.statistics(SecurityUtils.requireWorkerId()));
    }

    @Operation(summary = "切换在线状态")
    @ApiLog(module = "师傅", action = "切换在线状态")
    @PostMapping("/status")
    public Result<Void> switchStatus(@Valid @RequestBody WorkStatusRequestVO requestVO) {
        // 1 在线 2 离线：状态写入师傅档案并同步 Redis 在线心跳，供派单负载参考
        maintenanceWorkerService.switchWorkStatus(requestVO.getWorkStatus());
        // 返回成功并提示状态已更新
        return Result.<Void>ok(null, "状态已更新");
    }
}
