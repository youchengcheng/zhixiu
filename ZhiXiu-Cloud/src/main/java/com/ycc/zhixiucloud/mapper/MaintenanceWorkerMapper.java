package com.ycc.zhixiucloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ycc.zhixiucloud.entity.MaintenanceWorker;

/**
 * maintenance_worker 维修师傅扩展表 Mapper。
 * <p>
 * 继承 BaseMapper 获得单表增删改查能力，负责师傅档案查询、审核状态与工作状态更新，
 * 以及接单数、完成数和当前负载等统计字段的维护。
 */
public interface MaintenanceWorkerMapper extends BaseMapper<MaintenanceWorker> {
}
