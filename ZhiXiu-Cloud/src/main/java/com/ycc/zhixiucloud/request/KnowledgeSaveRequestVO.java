package com.ycc.zhixiucloud.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 知识库新增 / 修改入参（POST /api/admin/knowledge、PUT /api/admin/knowledge/{id}），
 * 提交方为管理员。知识条目用于 AI 助手的 RAG 检索，保存后需向量化才能参与检索。
 */
@Data
@Schema(description = "知识库新增或修改入参")
public class KnowledgeSaveRequestVO {

    @NotBlank(message = "请填写知识标题")
    @Size(max = 200, message = "标题不超过 200 个字")
    @Schema(description = "标题", example = "马桶水箱漏水如何处理")
    private String title;

    @Schema(description = "关联报修类型 ID")
    private Long categoryId;

    @NotBlank(message = "请填写知识正文")
    @Schema(description = "正文内容")
    private String content;

    @Size(max = 255, message = "标签不超过 255 个字")
    @Schema(description = "标签，逗号分隔", example = "水暖,马桶")
    private String tags;
}
