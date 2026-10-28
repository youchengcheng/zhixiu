package com.ycc.zhixiucloud.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 回复反馈入参（POST /api/admin/feedback/{id}/reply），提交方为管理员。
 * 回复内容会落库并记录回复时间，同时可按 status 推进反馈处理状态。
 */
@Data
@Schema(description = "回复反馈入参")
public class FeedbackReplyRequestVO {

    @NotBlank(message = "请填写回复内容")
    @Schema(description = "回复内容")
    private String reply;

    @Schema(description = "处理状态：1处理中 2已处理 3关闭；不传则默认置为处理中")
    private Integer status;
}
