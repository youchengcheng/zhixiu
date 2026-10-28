package com.ycc.zhixiucloud.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 报修类型树节点出参（GET /api/category/tree），业主 / 师傅 / 管理员三端共用。
 * 仅一级节点携带 children；二级节点的 children 为 null，前端据此停止递归。
 */
@Data
@Builder
@Schema(description = "报修类型节点")
public class CategoryTreeResponseVO {

    @Schema(description = "类型 ID")
    private Long id;

    @Schema(description = "类型名称")
    private String name;

    /** 一级节点为二级类型列表（无子类型时为空列表）；二级节点该字段为 null */
    @Schema(description = "子类型")
    private List<CategoryTreeResponseVO> children;
}
