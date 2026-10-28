package com.ycc.zhixiucloud.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发送 AI 消息入参（POST /api/ai/chat，SSE 流式），提交方为业主。
 * 服务端根据 sessionId 续接会话上下文，并以 text/event-stream 增量推送回复。
 */
@Data
@Schema(description = "发送 AI 消息入参")
public class AiChatRequestVO {

    @NotNull(message = "会话 ID 不能为空")
    @Schema(description = "会话 ID")
    private Long sessionId;

    @NotBlank(message = "对话动作不能为空")
    @Pattern(regexp = "CHAT|UNRESOLVED|GENERATE_DRAFT|RESOLVED", message = "对话动作不正确")
    @Schema(description = "CHAT 普通对话、UNRESOLVED 继续排查、GENERATE_DRAFT 生成草稿、RESOLVED 已解决")
    private String action = "CHAT";

    /** 普通对话必填；按钮动作由服务端生成反馈内容。 */
    @Size(max = 2000, message = "消息内容不能超过 2000 字")
    @Schema(description = "用户消息")
    private String content;

    @Schema(description = "按钮动作引用的最新 AI 回答 ID，除 CHAT 外必填")
    private Long messageId;
}
