package com.ycc.zhixiucloud.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 工单趋势聚合行：按天分组的工单数量。
 *
 * <p>统计投影：由 StatisticsMapper 按日期分组统计后映射，
 * 供 StatisticsServiceImpl 转换为趋势图 VO；不落库、不参与 MQ 传输。
 * 查询区间内没有数据的日期不会出现在结果中，由服务层补零。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyCountDTO implements Serializable {

    /** 日期，格式 MM-dd */
    private String day;

    /** 当日工单数 */
    private Long count;
}
