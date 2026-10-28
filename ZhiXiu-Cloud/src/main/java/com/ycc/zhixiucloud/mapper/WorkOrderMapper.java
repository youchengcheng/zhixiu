package com.ycc.zhixiucloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ycc.zhixiucloud.entity.WorkOrder;
import org.apache.ibatis.annotations.Param;

/**
 * work_order 工单表 Mapper。
 * <p>
 * 继承 BaseMapper 获得单表增删改查与分页能力，负责工单的创建、条件筛选、
 * 状态流转更新（配合乐观锁）以及师傅处理时长等统计查询。
 */
public interface WorkOrderMapper extends BaseMapper<WorkOrder> {

    /**
     * 师傅平均处理时长（分钟）：按接单时间到提交完工时间统计。
     *
     * @param workerId 师傅 ID（maintenance_worker.id）
     * @return 平均分钟数，无数据时返回 null
     */
    Long selectAvgHandleMinutes(@Param("workerId") Long workerId);
}