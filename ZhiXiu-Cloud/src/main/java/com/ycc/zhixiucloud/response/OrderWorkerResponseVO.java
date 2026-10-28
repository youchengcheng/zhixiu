package com.ycc.zhixiucloud.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 工单关联的维修师傅摘要：嵌套于工单详情（OrderDetailResponseVO.worker），三端共用。
 * 仅包含展示所需字段；未派单 / 未接单时外层 worker 为 null。
 */
@Data
@Builder
@Schema(description = "维修师傅摘要")
public class OrderWorkerResponseVO {

    @Schema(description = "师傅 ID")
    private Long workerId;

    @Schema(description = "姓名")
    private String realName;

    @Schema(description = "联系电话")
    private String phone;

    /** 历史评价均分；尚无评价时可能为 null */
    @Schema(description = "平均评分")
    private BigDecimal avgScore;

    /** 逗号分隔的技能标签，用于展示与派单匹配 */
    @Schema(description = "技能标签，逗号分隔")
    private String skillTags;
}