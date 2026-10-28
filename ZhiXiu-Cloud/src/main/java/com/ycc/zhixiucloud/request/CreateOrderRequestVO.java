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
 * 业主手动提交报修入参（POST /api/owner/order），提交方为业主。
 * 房产必须属于当前登录业主；报修类型须为二级分类，提交后工单进入待接单状态。
 */
@Data
@Schema(description = "提交报修入参")
public class CreateOrderRequestVO {

    /** 必须是当前登录业主名下的房产 ID。 */
    @NotNull(message = "请选择报修房产")
    @Schema(description = "房产 ID")
    private Long propertyId;

    @NotNull(message = "请选择报修类型")
    @Schema(description = "报修类型 ID（二级）")
    private Long categoryId;

    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题不能超过 100 字")
    @Schema(description = "工单标题")
    private String title;

    @Size(max = 1000, message = "故障描述不能超过 1000 字")
    @Schema(description = "故障现象描述")
    private String description;

    @NotNull(message = "请选择紧急程度")
    @Min(value = 1, message = "紧急程度取值 1-4")
    @Max(value = 4, message = "紧急程度取值 1-4")
    @Schema(description = "紧急程度：1低 2中 3高 4紧急")
    private Integer urgency;

    @Size(max = 50, message = "故障持续时间不能超过 50 字")
    @Schema(description = "故障持续时间，如「约1天」")
    private String faultDuration;

    /** 不传时服务端默认按「本户」处理。 */
    @Min(value = 1, message = "影响范围取值 1-3")
    @Max(value = 3, message = "影响范围取值 1-3")
    @Schema(description = "影响范围：1本户 2本层 3整栋", example = "1")
    private Integer affectScope;

    @Size(max = 50, message = "安全隐患标志不能超过 50 字")
    @Schema(description = "安全隐患标志：涉电/涉水/涉气/高空，多选逗号分隔")
    private String safetyFlag;

    @Size(max = 100, message = "设备信息不能超过 100 字")
    @Schema(description = "设备品牌型号")
    private String deviceInfo;

    @Size(max = 50, message = "期望上门时段不能超过 50 字")
    @Schema(description = "期望上门时段，如「今天下午」")
    private String expectedVisitTime;

    @NotBlank(message = "联系电话不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "联系电话格式不正确")
    @Schema(description = "联系电话")
    private String contactPhone;

    @Size(max = 1000, message = "图片地址不能超过 1000 字")
    @Schema(description = "图片 URL，逗号分隔")
    private String images;
}