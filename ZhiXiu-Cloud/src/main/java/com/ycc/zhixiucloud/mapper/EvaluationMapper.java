package com.ycc.zhixiucloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ycc.zhixiucloud.entity.Evaluation;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

/**
 * evaluation 评价表 Mapper。
 * <p>
 * 继承 BaseMapper 获得单表增删改查能力，负责评价记录的写入与查询，
 * 并汇总维修师傅的平均评分用于师傅档案展示。
 */
public interface EvaluationMapper extends BaseMapper<Evaluation> {

    /**
     * 师傅平均评分（保留 1 位小数）。
     *
     * @param workerId 师傅 ID（maintenance_worker.id）
     * @return 平均分，无评价时返回 null
     */
    BigDecimal selectAvgScoreByWorker(@Param("workerId") Long workerId);
}