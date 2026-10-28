package com.ycc.zhixiucloud.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 提交反馈入参（POST /api/feedback），提交方为已登录用户（业主、师傅；接口未限定角色）。
 * 用于收集系统使用过程中的问题、建议与投诉，由管理端统一处理。
 */
@Data
@Schema(description = "提交反馈入参")
public class FeedbackCreateRequestVO {

    @NotBlank(message = "请选择反馈类型")
    @Schema(description = "类型：BUG功能异常 / SUGGESTION优化建议 / COMPLAINT投诉")
    private String type;

    @NotBlank(message = "请填写标题")
    @Size(max = 200, message = "标题不超过 200 个字")
    @Schema(description = "标题")
    private String title;

    @NotBlank(message = "请填写反馈内容")
    @Schema(description = "详细内容")
    private String content;

    @Schema(description = "联系方式，选填")
    private String contact;
}
