package com.ycc.zhixiucloud.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 确认工单草稿入参（POST /api/ai/session/{id}/confirm），提交方为业主。
 * 服务端将 AI 草稿槽位与本次确认值合并后创建正式工单：
 * propertyId、contactPhone 必填，expectedVisitTime、images 直接采用本次提交值；
 * categoryId、title、description、urgency 等 AI 提取字段为空时沿用草稿值，
 * 草稿仍无紧急程度时默认「中」、无影响范围时默认「本户」。
 */
@Data
@Schema(description = "确认工单草稿入参")
public class AiDraftConfirmRequestVO {

    @NotNull(message = "草稿 ID 不能为空")
    @Schema(description = "当前有效草稿的消息 ID")
    private Long draftId;

    /** 必须是当前登录业主名下的房产 ID。 */
    @NotNull(message = "请选择报修房产")
    @Schema(description = "报修房产 ID（用户确认）")
    private Long propertyId;

    @Size(max = 50, message = "期望上门时段不能超过 50 字")
    @Schema(description = "期望上门时段")
    private String expectedVisitTime;

    @NotBlank(message = "联系电话不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "联系电话格式不正确")
    @Schema(description = "联系人电话")
    private String contactPhone;

    @Size(max = 1000, message = "图片地址不能超过 1000 字")
    @Schema(description = "图片 URL，逗号分隔")
    private String images;

    /** 以下 categoryId 至 deviceInfo 为可选的覆盖项，留空时使用 AI 草稿中的对应值。 */
    @Schema(description = "覆盖 AI 结果：报修类型 ID")
    private Long categoryId;

    @Size(max = 100, message = "标题不能超过 100 字")
    @Schema(description = "覆盖 AI 结果：工单标题")
    private String title;

    @Size(max = 1000, message = "故障描述不能超过 1000 字")
    @Schema(description = "覆盖 AI 结果：故障描述")
    private String description;

    @Min(value = 1, message = "紧急程度取值 1-4")
    @Max(value = 4, message = "紧急程度取值 1-4")
    @Schema(description = "覆盖 AI 结果：紧急程度 1低 2中 3高 4紧急")
    private Integer urgency;

    @Size(max = 50, message = "故障持续时间不能超过 50 字")
    @Schema(description = "覆盖 AI 结果：故障持续时间")
    private String faultDuration;

    @Min(value = 1, message = "影响范围取值 1-3")
    @Max(value = 3, message = "影响范围取值 1-3")
    @Schema(description = "覆盖 AI 结果：影响范围 1本户 2本层 3整栋")
    private Integer affectScope;

    @Size(max = 50, message = "安全隐患标志不能超过 50 字")
    @Schema(description = "覆盖 AI 结果：安全隐患标志，逗号分隔")
    private String safetyFlag;

    @Size(max = 100, message = "设备信息不能超过 100 字")
    @Schema(description = "覆盖 AI 结果：设备品牌型号")
    private String deviceInfo;
}
