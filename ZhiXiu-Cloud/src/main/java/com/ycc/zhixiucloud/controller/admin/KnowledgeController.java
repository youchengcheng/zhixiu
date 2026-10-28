package com.ycc.zhixiucloud.controller.admin;

import com.ycc.zhixiucloud.common.annotation.ApiLog;
import com.ycc.zhixiucloud.common.result.PageResult;
import com.ycc.zhixiucloud.common.result.Result;
import com.ycc.zhixiucloud.request.KnowledgeFileRequestVO;
import com.ycc.zhixiucloud.request.KnowledgeQueryRequestVO;
import com.ycc.zhixiucloud.request.KnowledgeSaveRequestVO;
import com.ycc.zhixiucloud.request.KnowledgeTestSearchRequestVO;
import com.ycc.zhixiucloud.response.KnowledgeResponseVO;
import com.ycc.zhixiucloud.response.KnowledgeSearchResultResponseVO;
import com.ycc.zhixiucloud.service.RepairKnowledgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 知识库接口（管理端）：列表、CRUD、重新向量化与检索测试。
 * 类级 @PreAuthorize 限定 ADMIN，知识条目直接影响 AI 回答质量，仅管理员可维护。
 * 新增/修改保存后异步向量化，删除同步清理向量库，检索测试依赖已配置的 AI 向量库。
 */
@Tag(name = "知识库")
@RestController
@RequestMapping("/api/admin/knowledge")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
public class KnowledgeController {

    // 知识库服务
    private final RepairKnowledgeService repairKnowledgeService;

    @Operation(summary = "知识库列表")
    @GetMapping("/list")
    public Result<PageResult<KnowledgeResponseVO>> list(@Valid KnowledgeQueryRequestVO requestVO) {
        // 按条件分页查询，统一包装为成功响应
        return Result.ok(repairKnowledgeService.page(requestVO));
    }

    @Operation(summary = "新增知识")
    @ApiLog(module = "知识库", action = "新增知识")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public Result<Long> create(@Valid @RequestBody KnowledgeSaveRequestVO requestVO) {
        // 保存知识后投递 RocketMQ 异步向量化，接口立即返回，避免阻塞管理员操作
        Long id = repairKnowledgeService.create(requestVO);
        // 返回成功提示（已投递异步向量化）
        return Result.ok(id, "知识已保存，正在向量化");
    }

    @Operation(summary = "上传知识文件")
    @ApiLog(module = "知识库", action = "上传知识文件")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Long> createFromFile(@Valid @ModelAttribute KnowledgeFileRequestVO requestVO) {
        Long id = repairKnowledgeService.createFromFile(requestVO);
        return Result.ok(id, "知识文件已导入，已提交分片向量化任务");
    }

    @Operation(summary = "修改知识")
    @ApiLog(module = "知识库", action = "修改知识")
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody KnowledgeSaveRequestVO requestVO) {
        // 按 ID 更新内容并标记重新向量化，旧向量在新向量生成前可能短时命中
        repairKnowledgeService.update(id, requestVO);
        // 返回成功提示
        return Result.<Void>ok(null, "知识已更新，正在重新向量化");
    }

    @Operation(summary = "修改知识或替换知识文件")
    @ApiLog(module = "知识库", action = "修改知识文件")
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Void> updateFromFile(@PathVariable Long id,
                                       @Valid @ModelAttribute KnowledgeFileRequestVO requestVO) {
        repairKnowledgeService.updateFromFile(id, requestVO);
        return Result.<Void>ok(null, "知识已更新，已提交重新分片向量化任务");
    }

    @Operation(summary = "删除知识")
    @ApiLog(module = "知识库", action = "删除知识")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        // 同步删除向量库中的对应向量，避免已删除知识继续被 AI 检索命中
        repairKnowledgeService.delete(id);
        // 返回成功提示
        return Result.<Void>ok(null, "知识已删除");
    }

    @Operation(summary = "重新向量化")
    @ApiLog(module = "知识库", action = "重新向量化")
    @PostMapping("/{id}/revectorize")
    public Result<Void> revectorize(@PathVariable Long id) {
        // 投递向量化任务重新生成向量
        repairKnowledgeService.revectorize(id);
        // 返回成功提示
        return Result.<Void>ok(null, "已触发重新向量化");
    }

    @Operation(summary = "检索测试")
    @GetMapping("/test-search")
    public Result<List<KnowledgeSearchResultResponseVO>> testSearch(@Valid KnowledgeTestSearchRequestVO requestVO) {
        // 按查询语句做向量相似度检索，返回命中的知识条目与分数，用于验证知识库召回效果
        return Result.ok(repairKnowledgeService.testSearch(requestVO));
    }
}
