package com.ycc.zhixiucloud.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建 AI 会话出参（POST /api/ai/session，业主端）。
 * 仅返回新建会话 ID，标题等其余信息需通过会话列表接口获取。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "创建 AI 会话结果")
public class AiSessionCreateResponseVO {

    @Schema(description = "会话 ID")
    private Long sessionId;
}
