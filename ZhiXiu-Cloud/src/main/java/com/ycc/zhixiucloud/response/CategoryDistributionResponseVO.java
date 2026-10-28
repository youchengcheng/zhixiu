package com.ycc.zhixiucloud.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 工单类型分布出参（GET /api/admin/statistics/category-distribution，管理端）。
 * 统计口径：全量未删除工单按报修类型聚合，按工单数从多到少取前 10 类。
 */
@Data
@Builder
@Schema(description = "工单类型分布")
public class CategoryDistributionResponseVO {

    /** 与 counts 按下标一一对应，两个列表长度一致；无数据时为空列表 */
    @Schema(description = "报修类型名称列表")
    private List<String> categories;

    @Schema(description = "工单数列表，与类型一一对应")
    private List<Long> counts;
}
