package com.ycc.zhixiucloud.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 工单趋势出参（GET /api/admin/statistics/order-trend，管理端）。
 * 统计口径：按天聚合近 N 天（默认 7 天，含今天）创建的工单数，缺失日期补 0；
 * dates 与 counts 按下标一一对应，长度相同。
 */
@Data
@Builder
@Schema(description = "工单趋势")
public class OrderTrendResponseVO {

    /** 按时间正序排列，如 10-18 */
    @Schema(description = "日期列表，格式 MM-dd")
    private List<String> dates;

    /** 与 dates 按下标一一对应，缺失日期的数量为 0 */
    @Schema(description = "工单数列表，与日期一一对应")
    private List<Long> counts;
}
