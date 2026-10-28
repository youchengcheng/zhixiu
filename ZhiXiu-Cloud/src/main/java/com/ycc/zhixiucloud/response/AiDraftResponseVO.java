package com.ycc.zhixiucloud.response;

import com.ycc.zhixiucloud.dto.AiFormDraft;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 当前工单草稿出参（GET /api/ai/session/{id}/draft，业主端 AI 助手专用）。
 * 无有效草稿时服务层直接抛业务异常（AI_DRAFT_EMPTY），不会返回本对象。
 */
@Data
@Builder
@Schema(description = "AI 工单草稿")
public class AiDraftResponseVO {

    /** 承载草稿的消息 ID，对应最新一条带 formDraft 的会话消息 */
    @Schema(description = "草稿消息 ID")
    private Long draftId;

    /** 由草稿消息中的 JSON 反序列化得到，字段尚未经用户确认，确认后才生成正式工单 */
    @Schema(description = "草稿表单")
    private AiFormDraft form;

    @Schema(description = "草稿失效时间，成功生成后 15 分钟")
    private LocalDateTime expiresAt;

    @Schema(description = "草稿剩余有效秒数，查看不续期")
    private long expiresIn;
}
