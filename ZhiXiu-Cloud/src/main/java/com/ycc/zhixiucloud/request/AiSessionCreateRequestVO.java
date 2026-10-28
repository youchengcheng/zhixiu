package com.ycc.zhixiucloud.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建 AI 会话入参（POST /api/ai/session），提交方为业主。
 * 请求体允许为空：为空时创建一个无标题的新会话，归属当前登录业主。
 */
@Data
@Schema(description = "创建 AI 会话入参")
public class AiSessionCreateRequestVO {

    @Size(max = 100, message = "会话标题不能超过 100 字")
    @Schema(description = "会话标题，不传则取首条消息摘要")
    private String title;
}
