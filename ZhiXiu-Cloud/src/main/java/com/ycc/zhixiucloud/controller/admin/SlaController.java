package com.ycc.zhixiucloud.controller.admin;

import com.ycc.zhixiucloud.common.annotation.ApiLog;
import com.ycc.zhixiucloud.common.result.Result;
import com.ycc.zhixiucloud.request.SlaConfigRequestVO;
import com.ycc.zhixiucloud.request.SlaEnabledRequestVO;
import com.ycc.zhixiucloud.response.SlaConfigResponseVO;
import com.ycc.zhixiucloud.service.SlaConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * SLA 配置接口（管理端）：规则列表、新增、修改、启停与删除。
 * 类级 @PreAuthorize 限定 ADMIN；全局兜底规则不允许删除，避免无匹配规则时失去默认 SLA。
 */
@Tag(name = "SLA 配置")
@RestController
@RequestMapping("/api/admin/sla")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SlaController {

    private final SlaConfigService slaConfigService;

    @Operation(summary = "SLA 规则列表")
    @GetMapping("/list")
    public Result<List<SlaConfigResponseVO>> list() {
        // 按派单权重降序返回全部规则，供派单与超时判定统一读取
        return Result.ok(slaConfigService.list());
    }

    @Operation(summary = "新增 SLA 规则")
    @ApiLog(module = "SLA", action = "新增 SLA 规则")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody SlaConfigRequestVO requestVO) {
        // 新规则按入参的启用状态与权重参与后续派单和超时计算
        return Result.ok(slaConfigService.create(requestVO), "新增成功");
    }

    @Operation(summary = "修改 SLA 规则")
    @ApiLog(module = "SLA", action = "修改 SLA 规则")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody SlaConfigRequestVO requestVO) {
        // 修改后立即影响新工单的 SLA 判定，已创建工单是否重算由服务层处理
        slaConfigService.update(id, requestVO);
        // 返回成功并提示保存成功
        return Result.<Void>ok(null, "保存成功");
    }

    @Operation(summary = "启用/禁用 SLA 规则")
    @ApiLog(module = "SLA", action = "启用或禁用 SLA 规则")
    @PutMapping("/{id}/enabled")
    public Result<Void> updateEnabled(@PathVariable Long id, @Valid @RequestBody SlaEnabledRequestVO requestVO) {
        // 禁用后该规则不再参与派单权重与超时计算，重新启用即可恢复
        slaConfigService.updateEnabled(id, requestVO.getEnabled());
        // 返回成功并提示状态已更新
        return Result.<Void>ok(null, "状态已更新");
    }

    @Operation(summary = "删除 SLA 规则")
    @ApiLog(module = "SLA", action = "删除 SLA 规则")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        // 全局兜底规则由服务层拒绝删除，防止无匹配规则时 SLA 失效
        slaConfigService.delete(id);
        // 返回成功并提示删除成功
        return Result.<Void>ok(null, "删除成功");
    }
}
