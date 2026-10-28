package com.ycc.zhixiucloud.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 智能派单推荐出参（GET /api/admin/order/{id}/recommend-workers，管理端）。
 * 仅从审核通过且未禁用的师傅中筛选，按 matchScore 降序取前若干名；
 * matchScore = 技能匹配 50% + 当前负载 30% + 历史评分 20%。
 */
@Data
@Builder
@Schema(description = "派单推荐师傅")
public class RecommendWorkerResponseVO {

    @Schema(description = "师傅 ID")
    private Long workerId;

    @Schema(description = "姓名")
    private String realName;

    /** 逗号分隔；技能标签命中报修类型或其父类型时获得更高匹配系数 */
    @Schema(description = "技能标签")
    private String skillTags;

    /** 当前进行中的工单数，负载越高匹配分越低 */
    @Schema(description = "当前进行中工单数")
    private Integer currentLoad;

    /** 综合匹配度，取值 0-1，保留 2 位小数；越大越靠前 */
    @Schema(description = "匹配度 0-1")
    private BigDecimal matchScore;

    /** 面向管理员的推荐说明，包含技能覆盖情况、当前负载与平均评分 */
    @Schema(description = "推荐理由")
    private String reason;
}