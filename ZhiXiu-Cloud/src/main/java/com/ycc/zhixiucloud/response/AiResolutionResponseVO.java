package com.ycc.zhixiucloud.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * AI 自助解决率出参（GET /api/admin/statistics/ai-resolution，管理端）。
 * 统计口径：全量 AI 会话，不按时间筛选；resolutionRate = resolvedSessions / totalSessions，
 * 保留 2 位小数，分母为 0 时为 0；Token 为全部会话累计消耗。
 */
@Data
@Builder
@Schema(description = "AI 自助解决率")
public class AiResolutionResponseVO {

    @Schema(description = "会话总数")
    private Long totalSessions;

    @Schema(description = "自助解决会话数")
    private Long resolvedSessions;

    @Schema(description = "转工单会话数")
    private Long transferSessions;

    /** 比率取值 0-1，保留 2 位小数；会话总数为 0 时返回 0 */
    @Schema(description = "自助解决率")
    private BigDecimal resolutionRate;

    /** 对应大模型请求的 prompt token 累计值 */
    @Schema(description = "累计输入 Token 数")
    private Long totalPromptTokens;

    /** 对应大模型回复的 completion token 累计值 */
    @Schema(description = "累计输出 Token 数")
    private Long totalCompletionTokens;
}
