package com.ycc.zhixiucloud.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ycc.zhixiucloud.common.constant.MqConstants;
import com.ycc.zhixiucloud.common.exception.BizException;
import com.ycc.zhixiucloud.common.result.PageResult;
import com.ycc.zhixiucloud.common.result.ResultCode;
import com.ycc.zhixiucloud.dto.OperationLogMessage;
import com.ycc.zhixiucloud.entity.OperationLog;
import com.ycc.zhixiucloud.mapper.OperationLogMapper;
import com.ycc.zhixiucloud.mq.MqMessageCodec;
import com.ycc.zhixiucloud.request.OperationLogQueryRequestVO;
import com.ycc.zhixiucloud.response.OperationLogResponseVO;
import com.ycc.zhixiucloud.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.core.RocketMQClientTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 操作日志实现：采集端投递 MQ，消费端落库，查询端只读。
 *
 * <p>整体链路：AOP 切面采集请求信息 → {@link #record} 投递 RocketMQ →
 * 消费者调用 {@link #save} 写入 operation_log 表 → {@link #page} 提供后台分页查询。</p>
 *
 * <p>关键设计：日志属于旁路能力，MQ 投递失败仅记录告警，不阻断正常业务请求；
 * 落库方法使用事务，保证单条日志写入的一致性。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationLogServiceImpl implements OperationLogService {

    /** 完整时间格式 */
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 仅日期格式 */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** yyyy-MM-dd 的长度 */
    private static final int DATE_LENGTH = 10;

    private final OperationLogMapper operationLogMapper;

    private final RocketMQClientTemplate rocketMQClientTemplate;

    private final MqMessageCodec mqMessageCodec;

    /**
     * 投递一条操作日志消息到 MQ。
     *
     * <p>日志采集与主业务解耦：即使 RocketMQ 不可用也只记录告警日志，
     * 不向调用方抛出异常，避免影响正常请求。</p>
     *
     * @param message 操作日志消息（请求信息、结果、耗时等）
     */
    @Override
    public void record(OperationLogMessage message) {
        // 拼接 destination：operation-log-topic:ACCESS
        String destination = MqConstants.destination(MqConstants.TOPIC_OPERATION_LOG, MqConstants.OperationLog.ACCESS);
        // 投递失败仅告警，不影响主业务流程
        try {
            // 编码消息体后同步投递到 MQ
            rocketMQClientTemplate.syncSendNormalMessage(destination, mqMessageCodec.encode(message));
        // 捕获投递异常，避免影响正常请求
        } catch (Exception e) {
            // 日志采集属于旁路能力，MQ 不可用时不能影响正常业务请求
            log.warn("操作日志投递失败：url={}, action={}", message.url(), message.action(), e);
        }
    }

    /**
     * 将 MQ 消息落库为操作日志。
     *
     * <p>由 MQ 消费者调用；在事务中把消息逐字段复制到实体并插入 operation_log 表。</p>
     *
     * @param message 操作日志消息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(OperationLogMessage message) {
        // 新建日志实体，逐字段从消息拷贝
        OperationLog entity = new OperationLog();
        // 操作人 ID
        entity.setUserId(message.userId());
        // 操作人登录名
        entity.setUsername(message.username());
        // 操作人角色
        entity.setRole(message.role());
        // 业务模块
        entity.setModule(message.module());
        // 操作动作
        entity.setAction(message.action());
        // HTTP 方法
        entity.setMethod(message.method());
        // 请求地址
        entity.setUrl(message.url());
        // 请求参数（已脱敏）
        entity.setParams(message.params());
        // 客户端 IP
        entity.setIp(message.ip());
        // 结果：0失败 1成功
        entity.setResult(message.result());
        // 错误信息
        entity.setErrorMsg(message.errorMsg());
        // 耗时（毫秒）
        entity.setCostTime(message.costTime());
        // 插入 operation_log 表
        operationLogMapper.insert(entity);
    }

    /**
     * 分页查询操作日志。
     *
     * <p>支持按模块、操作人（模糊）、结果、时间区间组合过滤，结果按发生时间倒序。
     * 时间参数兼容 {@code yyyy-MM-dd} 与 {@code yyyy-MM-dd HH:mm:ss}，
     * 仅日期时开始取 00:00:00、结束取 23:59:59。</p>
     *
     * @param requestVO 查询条件与分页参数
     * @return 当前页日志列表及总数
     * @throws BizException 时间参数格式非法时抛出
     */
    @Override
    public PageResult<OperationLogResponseVO> page(OperationLogQueryRequestVO requestVO) {
        // 解析开始时间，纯日期取当天 00:00:00
        LocalDateTime start = parseTime(requestVO.getStartTime(), true);
        // 解析结束时间，纯日期取当天 23:59:59
        LocalDateTime end = parseTime(requestVO.getEndTime(), false);

        // 按条件分页查询日志表
        Page<OperationLog> page = operationLogMapper.selectPage(
                // 按入参页码与每页条数分页
                new Page<>(requestVO.getPageNum(), requestVO.getPageSize()),
                // 组装日志查询条件
                Wrappers.<OperationLog>lambdaQuery()
                        // 模块精确匹配（非空才拼接）
                        .eq(StringUtils.hasText(requestVO.getModule()), OperationLog::getModule, requestVO.getModule())
                        // 操作人模糊匹配
                        .like(StringUtils.hasText(requestVO.getUsername()), OperationLog::getUsername,
                                requestVO.getUsername())
                        // 结果精确匹配
                        .eq(requestVO.getResult() != null, OperationLog::getResult, requestVO.getResult())
                        // 发生时间下界
                        .ge(start != null, OperationLog::getCreateTime, start)
                        // 发生时间上界
                        .le(end != null, OperationLog::getCreateTime, end)
                        // 按发生时间倒序
                        .orderByDesc(OperationLog::getCreateTime));

        // 新建出参分页，沿用当前页/每页/总数
        Page<OperationLogResponseVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        // 实体列表逐条转换为 VO
        voPage.setRecords(page.getRecords().stream().map(this::toVO).toList());
        // 包装为统一分页结果
        return PageResult.of(voPage);
    }

    /**
     * 兼容 yyyy-MM-dd：开始时间取当天 00:00:00，结束时间取当天 23:59:59。
     *
     * @param text       时间字符串，为空返回 null
     * @param startOfDay true 表示开始边界，false 表示结束边界
     * @return 解析后的时间，未传值时返回 null
     * @throws BizException 格式不符合两种约定格式时抛出
     */
    private LocalDateTime parseTime(String text, boolean startOfDay) {
        // 空值返回 null，交由查询条件跳过该边界
        if (!StringUtils.hasText(text)) {
            // 未传时间时返回空，查询时跳过该边界
            return null;
        }
        // 去掉首尾空格再解析
        String value = text.trim();
        // 解析时间字符串，格式非法由下方捕获
        try {
            // 长度 10 判定为纯日期格式
            if (value.length() == DATE_LENGTH) {
                // 解析为日期
                LocalDate date = LocalDate.parse(value, DATE_FORMATTER);
                // 开始时间取 00:00:00，结束时间取 23:59:59
                return startOfDay ? date.atStartOfDay() : date.atTime(23, 59, 59);
            }
            // 否则按完整时间格式解析
            return LocalDateTime.parse(value, DATETIME_FORMATTER);
        // 捕获解析异常并转为业务参数错误
        } catch (DateTimeParseException e) {
            // 格式非法转为参数异常提示
            throw BizException.of(ResultCode.PARAM_ERROR, "时间参数格式应为 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss");
        }
    }

    /**
     * 将日志实体转换为查询出参。
     *
     * @param entity 日志实体
     * @return 日志出参 VO
     */
    private OperationLogResponseVO toVO(OperationLog entity) {
        // 构建出参 VO，逐字段从实体拷贝
        return OperationLogResponseVO.builder()
                // 日志 ID
                .id(entity.getId())
                // 操作人 ID
                .userId(entity.getUserId())
                // 操作人登录名
                .username(entity.getUsername())
                // 操作人角色
                .role(entity.getRole())
                // 模块
                .module(entity.getModule())
                // 操作动作
                .action(entity.getAction())
                // HTTP 方法
                .method(entity.getMethod())
                // 请求地址
                .url(entity.getUrl())
                // 请求参数（已脱敏）
                .params(entity.getParams())
                // 客户端 IP
                .ip(entity.getIp())
                // 结果：0失败 1成功
                .result(entity.getResult())
                // 错误信息
                .errorMsg(entity.getErrorMsg())
                // 耗时（毫秒）
                .costTime(entity.getCostTime())
                // 发生时间
                .createTime(entity.getCreateTime())
                .build();
    }
}
