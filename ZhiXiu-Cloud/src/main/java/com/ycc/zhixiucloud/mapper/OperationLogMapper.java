package com.ycc.zhixiucloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ycc.zhixiucloud.entity.OperationLog;

/**
 * operation_log 操作日志表 Mapper。
 * <p>
 * 继承 BaseMapper 获得单表增删改查与分页能力，负责操作日志的写入，
 * 以及按操作人、模块、时间范围等条件进行审计查询。
 */
public interface OperationLogMapper extends BaseMapper<OperationLog> {
}
