package com.ycc.zhixiucloud.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 管理端报修类型树节点出参，包含停用节点及维护字段。
 */
@Data
@Builder
@Schema(description = "管理端报修类型节点")
public class AdminCategoryResponseVO {

    @Schema(description = "分类 ID")
    private Long id;

    @Schema(description = "分类名称")
    private String name;

    @Schema(description = "父分类 ID，0 表示一级分类")
    private Long parentId;

    @Schema(description = "排序值")
    private Integer sort;

    @Schema(description = "图标名称或地址")
    private String icon;

    @Schema(description = "状态：0停用 1启用")
    private Integer status;

    @Schema(description = "子分类")
    private List<AdminCategoryResponseVO> children;
}
