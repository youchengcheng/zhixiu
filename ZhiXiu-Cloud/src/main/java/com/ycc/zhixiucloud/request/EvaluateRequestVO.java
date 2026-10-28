package com.ycc.zhixiucloud.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 提交评价入参（POST /api/owner/order/{id}/evaluate），提交方为该工单的业主。
 * 一单一评，仅已完工工单可评价；提交后工单由已完工推进到已关闭，
 * 并按 score 重新计算师傅平均分。
 */
@Data
@Schema(description = "提交评价入参")
public class EvaluateRequestVO {

    @NotNull(message = "请给出总分")
    @Min(value = 1, message = "评分取值 1-5")
    @Max(value = 5, message = "评分取值 1-5")
    @Schema(description = "总分 1-5")
    private Integer score;

    /** 以下三个子项评分均可不传，仅用于评价明细展示；师傅平均分只统计 score。 */
    @Min(value = 1, message = "评分取值 1-5")
    @Max(value = 5, message = "评分取值 1-5")
    @Schema(description = "响应速度分 1-5")
    private Integer speedScore;

    @Min(value = 1, message = "评分取值 1-5")
    @Max(value = 5, message = "评分取值 1-5")
    @Schema(description = "服务态度分 1-5")
    private Integer attitudeScore;

    @Min(value = 1, message = "评分取值 1-5")
    @Max(value = 5, message = "评分取值 1-5")
    @Schema(description = "维修质量分 1-5")
    private Integer qualityScore;

    @Size(max = 500, message = "评价内容不能超过 500 字")
    @Schema(description = "评价内容")
    private String content;

    @Size(max = 1000, message = "评价图片不能超过 1000 字")
    @Schema(description = "评价图片 URL，逗号分隔")
    private String images;
}