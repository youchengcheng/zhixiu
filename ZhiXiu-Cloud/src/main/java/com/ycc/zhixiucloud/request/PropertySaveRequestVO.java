package com.ycc.zhixiucloud.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 房产新增 / 修改入参（POST /api/property、PUT /api/property/{id}），提交方为业主。
 * 新增时若该业主尚无房产，第一套会自动设为默认；管理员代录房产见
 * AdminPropertySaveRequestVO（额外指定归属业主）。
 */
@Data
@Schema(description = "房产保存入参")
public class PropertySaveRequestVO {

    @NotBlank(message = "小区不能为空")
    @Size(max = 100, message = "小区名称不能超过 100 字")
    @Schema(description = "小区")
    private String community;

    @NotBlank(message = "栋不能为空")
    @Size(max = 20, message = "栋不能超过 20 位")
    @Schema(description = "栋", example = "3")
    private String building;

    @NotBlank(message = "单元不能为空")
    @Size(max = 20, message = "单元不能超过 20 位")
    @Schema(description = "单元", example = "2")
    private String unit;

    @NotBlank(message = "房号不能为空")
    @Size(max = 20, message = "房号不能超过 20 位")
    @Schema(description = "房号", example = "501")
    private String room;

    @DecimalMin(value = "0.01", message = "面积需大于 0")
    @DecimalMax(value = "99999.99", message = "面积超出取值范围")
    @Schema(description = "面积(㎡)", example = "89.5")
    private BigDecimal areaSize;

    /**
     * 0 否 1 默认；同一业主仅允许一套默认房产，设为 1 时服务端会清除其它房产的默认标记。
     * 编辑时只能通过本字段把当前房产设为默认，不能直接取消默认。
     */
    @Min(value = 0, message = "默认标记取值 0/1")
    @Max(value = 1, message = "默认标记取值 0/1")
    @Schema(description = "0否 1默认")
    private Integer isDefault;
}
