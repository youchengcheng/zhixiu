package com.ycc.zhixiucloud.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 分页入参基类：统一 pageNum / pageSize，pageNum 从 1 开始；
 * 单个分页请求条数由 @Max(200) 与分页插件共同限制，避免一次拉取过多数据。
 */
@Data
@Schema(description = "分页入参")
public class PageRequestVO {

    /** 默认第 1 页，最小为 1。 */
    @Min(value = 1, message = "页码从 1 开始")
    @Schema(description = "页码", example = "1")
    private Integer pageNum = 1;

    /** 默认每页 10 条，取值范围 1-200。 */
    @Min(value = 1, message = "每页条数至少 1 条")
    @Max(value = 200, message = "每页最多 200 条")
    @Schema(description = "每页条数", example = "10")
    private Integer pageSize = 10;
}