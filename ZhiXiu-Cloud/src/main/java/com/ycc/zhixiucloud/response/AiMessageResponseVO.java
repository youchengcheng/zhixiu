package com.ycc.zhixiucloud.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 会话历史消息行出参（GET /api/ai/session/{id}/messages，业主端 AI 助手专用）。
 * 结果按 createTime 正序排列；用户消息的 intent、citations 通常为 null。
 */
@Data
@Builder
@Schema(description = "AI 会话消息行")
public class AiMessageResponseVO {

    @Schema(description = "消息 ID")
    private Long id;

    /** USER / AI */
    @Schema(description = "发送方：USER / AI")
    private String sender;

    @Schema(description = "消息内容（Markdown）")
    private String content;

    /** 仅 AI 回复消息携带，用户消息一般为 null */
    @Schema(description = "意图：QA问答 / TROUBLESHOOT排查 / FILL_FORM收集信息")
    private String intent;

    /** 无知识库引用时为空字符串或 null；多项时以英文逗号拼接 */
    @Schema(description = "引用的知识库 ID，逗号分隔")
    private String citations;

    @Schema(description = "引用知识的 ID 和标题")
    private List<CitationItem> citationItems;

    @Schema(description = "引用是否经过核验")
    private boolean citationsVerified;

    @Schema(description = "发送时间")
    private LocalDateTime createTime;
}
