package com.ycc.zhixiucloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ycc.zhixiucloud.entity.WorkOrderLog;

/**
 * work_order_log 工单流转日志表 Mapper。
 * <p>
 * 继承 BaseMapper 获得单表增删改查能力，负责状态机流转日志的写入，
 * 以及按工单 ID 按时间顺序查询完整流转轨迹。
 */
public interface WorkOrderLogMapper extends BaseMapper<WorkOrderLog> {
}
