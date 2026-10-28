package com.ycc.zhixiucloud.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库条目出参：管理端列表（GET /api/admin/knowledge/list）与详情共用，仅管理员角色使用。
 * categoryName 为关联查询的展示字段；内容修改后 version 自增并触发重新向量化。
 */
@Data
@Builder
@Schema(description = "知识库条目")
public class KnowledgeResponseVO {

    @Schema(description = "知识 ID")
    private Long id;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "关联报修类型 ID")
    private Long categoryId;

    /** 展示用关联字段：取 repair_category.name，非 repair_knowledge 表字段；未关联类型时为 null */
    @Schema(description = "关联报修类型名称")
    private String categoryName;

    /** 知识正文，同时作为向量化的文本来源 */
    @Schema(description = "正文内容")
    private String content;

    /** 用于检索与分类的标签，无标签时为空字符串或 null */
    @Schema(description = "标签，逗号分隔")
    private String tags;

    @Schema(description = "来源：MANUAL人工录入 / IMPORT批量导入")
    private String sourceType;

    @Schema(description = "状态：0禁用 1启用")
    private Integer status;

    @Schema(description = "是否已向量化：0否 1是")
    private Integer vectorized;

    @Schema(description = "版本号，修改后自增")
    private Integer version;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
