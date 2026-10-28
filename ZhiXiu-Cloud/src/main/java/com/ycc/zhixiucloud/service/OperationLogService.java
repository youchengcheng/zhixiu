package com.ycc.zhixiucloud.service;

import com.ycc.zhixiucloud.common.result.PageResult;
import com.ycc.zhixiucloud.dto.OperationLogMessage;
import com.ycc.zhixiucloud.request.OperationLogQueryRequestVO;
import com.ycc.zhixiucloud.response.OperationLogResponseVO;

/**
 * 操作日志服务：接口访问日志的采集、异步落库与查询。
 *
 * <p>职责边界：采集端只负责把日志消息投递到 MQ，消费端负责落库，查询端只读；
 * 日志链路是旁路能力，不能影响业务请求的成功与否。</p>
 *
 * <p>主要使用方：操作日志切面/过滤器（record）、OperationLogConsumer（save）、
 * 管理端日志页面（page）。</p>
 *
 * <p>关键实现要点：MQ 投递采用同步发送，但异常被吞掉并记录 warn；
 * 落库使用数据库事务；查询兼容 yyyy-MM-dd 与 yyyy-MM-dd HH:mm:ss 两种时间格式。</p>
 */
public interface OperationLogService {

    /**
     * 采集一条接口访问日志并投递到 MQ（topic: operation-log-topic，tag: ACCESS）。
     *
     * <p>属于旁路动作：MQ 不可用或发送失败只记录 warn 日志，不影响当前业务请求，
     * 也不会向调用方抛异常。</p>
     *
     * @param message 日志消息（操作人、模块、请求参数、结果、耗时等，参数已脱敏）
     */
    void record(OperationLogMessage message);

    /**
     * 将 MQ 中的日志消息落库。
     *
     * <p>由 OperationLogConsumer 消费后调用；方法带事务，字段逐项从消息复制到实体。</p>
     *
     * @param message 日志消息
     */
    void save(OperationLogMessage message);

    /**
     * 管理端分页查询操作日志。
     *
     * <p>支持模块、操作人、结果与时间范围筛选；结果按发生时间倒序。
     * 时间参数为纯日期时，开始时间取当天 00:00:00、结束时间取当天 23:59:59。</p>
     *
     * @param requestVO 分页与筛选参数
     * @return 操作日志分页结果
     * @throws com.ycc.zhixiucloud.common.exception.BizException 时间参数格式非法时抛出 400
     */
    PageResult<OperationLogResponseVO> page(OperationLogQueryRequestVO requestVO);
}
