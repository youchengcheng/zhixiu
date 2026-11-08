package com.ycc.zhixiucloud.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理端新增或修改报修类型入参。
 */
@Data
@Schema(description = "报修类型保存入参")
public class CategorySaveRequestVO {

    @NotBlank(message = "分类名称不能为空")
    @Size(max = 50, message = "分类名称不能超过 50 字")
    @Schema(description = "分类名称")
    private String name;

    @NotNull(message = "父分类不能为空")
    @Min(value = 0, message = "父分类 ID 不能小于 0")
    @Schema(description = "父分类 ID，0 表示一级分类")
    private Long parentId;

    @NotNull(message = "排序值不能为空")
    @Min(value = 0, message = "排序值不能小于 0")
    @Schema(description = "同级排序值，越小越靠前")
    private Integer sort;

    @Size(max = 255, message = "图标地址不能超过 255 字")
    @Schema(description = "图标名称或地址")
    private String icon;
}
