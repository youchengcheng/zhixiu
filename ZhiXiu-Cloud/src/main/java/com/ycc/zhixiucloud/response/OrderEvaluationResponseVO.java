package com.ycc.zhixiucloud.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工单评价详情：作为 OrderDetailResponseVO.evaluation 嵌套返回，业主 / 师傅 / 管理员查看详情时均可获取。
 * 总分与三个分项均为 1-5 分；未评价时外层 evaluation 为 null。
 */
@Data
@Builder
@Schema(description = "工单评价")
public class OrderEvaluationResponseVO {

    @Schema(description = "总分 1-5")
    private Integer score;

    @Schema(description = "响应速度分")
    private Integer speedScore;

    @Schema(description = "服务态度分")
    private Integer attitudeScore;

    @Schema(description = "维修质量分")
    private Integer qualityScore;

    @Schema(description = "评价内容")
    private String content;

    /** 评价图片 URL 列表，无图为空列表 */
    @Schema(description = "评价图片")
    private List<String> images;

    @Schema(description = "评价时间")
    private LocalDateTime createTime;
}