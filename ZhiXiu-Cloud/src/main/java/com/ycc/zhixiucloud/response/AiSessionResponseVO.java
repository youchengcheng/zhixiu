package com.ycc.zhixiucloud.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 会话行出参（GET /api/ai/session/list，业主端）。
 * 仅返回当前登录业主自己的会话，按更新时间倒序排列。
 */
@Data
@Builder
@Schema(description = "AI 会话行")
public class AiSessionResponseVO {

    @Schema(description = "会话 ID")
    private Long sessionId;

    @Schema(description = "会话标题")
    private String title;

    /** 会话内用户消息与 AI 回复的总条数 */
    @Schema(description = "消息数")
    private Integer messageCount;

    /** 0进行中 1自助解决 2转工单 */
    @Schema(description = "解决状态：0进行中 1自助解决 2转工单")
    private Integer resolved;

    /** 会话通过 AI 草稿生成的工单 ID，未生成时为 null，供前端展示「查看工单」 */
    @Schema(description = "关联工单 ID，未生成工单时为空")
    private Long orderId;

    /** 关联工单号，未生成时为 null，可直接展示给业主 */
    @Schema(description = "关联工单号，未生成工单时为空")
    private String orderNo;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
