package com.ycc.zhixiucloud.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内通知列表行出参。
 */
@Data
@Builder
@Schema(description = "站内通知")
public class NotificationResponseVO {

    @Schema(description = "通知 ID")
    private Long id;

    @Schema(description = "通知标题")
    private String title;

    @Schema(description = "通知正文")
    private String content;

    @Schema(description = "关联工单 ID")
    private Long orderId;

    @Schema(description = "阅读状态：0未读 1已读")
    private Integer isRead;

    @Schema(description = "阅读时间")
    private LocalDateTime readTime;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
