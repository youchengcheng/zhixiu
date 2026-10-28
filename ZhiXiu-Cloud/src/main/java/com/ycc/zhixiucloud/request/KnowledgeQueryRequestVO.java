package com.ycc.zhixiucloud.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识库列表查询入参（GET /api/admin/knowledge/list），提交方为管理员。
 * categoryId 精确匹配、keyword 模糊匹配标题或正文，两个条件可组合并按 AND 生效，
 * 结果按更新时间倒序；分页参数继承自 PageRequestVO。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "知识库列表查询入参")
public class KnowledgeQueryRequestVO extends PageRequestVO {

    @Schema(description = "关联报修类型 ID")
    private Long categoryId;

    @Schema(description = "标题或内容关键字")
    private String keyword;
}
