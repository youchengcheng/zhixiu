package com.ycc.zhixiucloud.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 知识库检索测试入参（GET /api/admin/knowledge/test-search），提交方为管理员。
 * 用于在管理端验证向量检索效果，不会修改知识库数据。
 */
@Data
@Schema(description = "知识库检索测试入参")
public class KnowledgeTestSearchRequestVO {

    @NotBlank(message = "请填写查询语句")
    @Schema(description = "查询语句", example = "水龙头一直滴水")
    private String query;

    /** 默认 3，取值 1-20；越大返回的相关知识片段越多。 */
    @Min(value = 1, message = "topK 至少 1")
    @Max(value = 20, message = "topK 最多 20")
    @Schema(description = "返回条数", example = "3")
    private Integer topK = 3;
}
