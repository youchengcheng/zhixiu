package com.ycc.zhixiucloud.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 知识库检索测试出参（GET /api/admin/knowledge/test-search，管理端）。
 * 由向量库做相似度检索，通常按 score 从高到低返回；无命中时为空列表。
 */
@Data
@Builder
@Schema(description = "知识库检索结果")
public class KnowledgeSearchResultResponseVO {

    @Schema(description = "知识 ID")
    private Long id;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "正文内容")
    private String content;

    /** 向量相似度分数，通常取值 0-1，越大代表越相似；向量库未返回分数时为 null */
    @Schema(description = "相似度分数，越大越相似")
    private Double score;
}
