package com.ycc.zhixiucloud.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ycc.zhixiucloud.common.enums.OperatorTypeEnum;
import com.ycc.zhixiucloud.common.enums.OrderActionEnum;
import com.ycc.zhixiucloud.common.enums.OrderStatusEnum;
import com.ycc.zhixiucloud.entity.User;
import com.ycc.zhixiucloud.entity.WorkOrderLog;
import com.ycc.zhixiucloud.mapper.UserMapper;
import com.ycc.zhixiucloud.mapper.WorkOrderLogMapper;
import com.ycc.zhixiucloud.response.WorkOrderLogResponseVO;
import com.ycc.zhixiucloud.service.WorkOrderLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工单流转日志实现：日志由状态机在状态变更的同一事务内写入，保证「状态与日志」原子一致。
 *
 * <p>落库时机与字段：状态机创建工单或执行状态流转时调用 {@link #record}，一次动作写一条记录，
 * 包含工单 ID、操作人、操作人类型、动作、前后状态与备注；创建场景没有前置状态，from 为 null。</p>
 *
 * <p>查询侧：按创建时间、ID 升序还原流转时间线，并批量补齐操作人姓名，再把状态码、动作码、
 * 操作人类型翻译为中文文案。日志表是只追加的审计数据，本类不提供修改与删除。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkOrderLogServiceImpl implements WorkOrderLogService {

    private final WorkOrderLogMapper workOrderLogMapper;

    private final UserMapper userMapper;

    /**
     * 记录一条工单流转日志，只追加、不修改、不去重。
     *
     * <p>由状态机在创建工单或状态流转的同一事务内调用，因此日志与工单状态同成同败。
     * action 保存日志动作码，前后状态保存状态码；创建场景的前状态为 null。</p>
     *
     * @param orderId      工单 ID
     * @param operatorId   操作人 ID，系统触发时为 null
     * @param operatorType 操作人类型（业主 / 师傅 / 管理员 / 系统）
     * @param action       本次动作
     * @param from         变更前状态，创建时为 null
     * @param to           变更后状态
     * @param remark       备注或原因，可为 null
     */
    @Override
    public void record(Long orderId, Long operatorId, OperatorTypeEnum operatorType, OrderActionEnum action,
                       OrderStatusEnum from, OrderStatusEnum to, String remark) {
        // 组装一条流转日志记录
        WorkOrderLog orderLog = new WorkOrderLog();
        // 指定工单主键
        orderLog.setOrderId(orderId);
        // 操作人 ID 可为空（系统触发）
        orderLog.setOperatorId(operatorId);
        // 枚举统一落库为码值，避免中文文案调整影响历史数据
        orderLog.setOperatorType(operatorType.getCode());
        // 动作使用日志码：多个业务动作可能共用一个时间线文案
        orderLog.setAction(action.getLogCode());
        // 创建工单没有前置状态，from 保持 null
        orderLog.setFromStatus(from == null ? null : from.getCode());
        // 变更后状态码
        orderLog.setToStatus(to == null ? null : to.getCode());
        // 备注/原因原样保存，可为 null
        orderLog.setRemark(remark);
        // 只追加写入日志表，与状态变更同事务
        workOrderLogMapper.insert(orderLog);
    }

    /**
     * 查询工单的完整流转记录：按创建时间、ID 升序还原时间线。
     *
     * <p>先一次取出全部日志，再批量加载操作人姓名，避免逐条查询用户；
     * 状态、动作、操作人类型统一翻译为展示文案。无记录时返回空列表。</p>
     *
     * @param orderId 工单 ID
     * @return 流转日志出参列表（含操作人姓名与中文文案）
     */
    @Override
    public List<WorkOrderLogResponseVO> listByOrder(Long orderId) {
        // 一次取出全部日志，按时间与 id 升序还原时间线
        List<WorkOrderLog> logs = workOrderLogMapper.selectList(Wrappers.<WorkOrderLog>lambdaQuery()
                .eq(WorkOrderLog::getOrderId, orderId)
                .orderByAsc(WorkOrderLog::getCreateTime)
                .orderByAsc(WorkOrderLog::getId));
        // 无日志记录时提前返回
        if (logs.isEmpty()) {
            // 无流转记录直接返回空列表
            return Collections.emptyList();
        }
        // 批量加载操作人姓名，避免逐条查询用户
        Map<Long, String> operatorNames = loadOperatorNames(logs);
        // 逐条装配展示对象并把状态、动作、操作人类型翻译为文案
        return logs.stream().map(item -> WorkOrderLogResponseVO.builder()
                .id(item.getId())
                .orderId(item.getOrderId())
                .operatorId(item.getOperatorId())
                .operatorType(item.getOperatorType())
                .operatorTypeText(operatorTypeText(item.getOperatorType()))
                .operatorName(operatorNames.get(item.getOperatorId()))
                .action(item.getAction())
                .actionText(actionText(item.getAction()))
                .fromStatus(item.getFromStatus())
                .fromStatusText(statusText(item.getFromStatus()))
                .toStatus(item.getToStatus())
                .toStatusText(statusText(item.getToStatus()))
                .remark(item.getRemark())
                .createTime(item.getCreateTime())
                .build()).toList();
    }

    /** 批量加载操作人姓名；系统操作没有 operatorId 时过滤掉，空集合返回空 Map */
    private Map<Long, String> loadOperatorNames(List<WorkOrderLog> logs) {
        // 收集日志中的操作人 ID，系统操作 null 会被过滤
        Set<Long> operatorIds = logs.stream()
                .map(WorkOrderLog::getOperatorId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        // 无操作人时无需回库查询
        if (operatorIds.isEmpty()) {
            // 无操作人时返回空 Map，避免非法查询
            return Collections.emptyMap();
        }
        // 按 ID 数量预分配 Map 容量
        Map<Long, String> names = new HashMap<>(operatorIds.size());
        // 建立用户 ID 到真实姓名的映射
        for (User user : userMapper.selectBatchIds(operatorIds)) {
            // 记录用户 ID 到真实姓名映射
            names.put(user.getId(), user.getRealName());
        }
        // 返回操作人姓名映射
        return names;
    }

    /** 日志 action 是文档 DDL 列举的固定集合，多个事件可能共用一个取值，这里给出通用中文名 */
    private String actionText(String action) {
        // 动作码为空时无法翻译
        if (action == null) {
            // 空动作直接返回 null
            return null;
        }
        // 动作码为固定集合，多个事件可共用文案，未知取值原样返回
        return switch (action) {
            case "CREATE" -> "创建工单";
            case "ASSIGN" -> "派单 / 改派";
            case "ACCEPT" -> "接单 / 抢单";
            case "ARRIVE" -> "到场签到";
            case "PAUSE" -> "暂停";
            case "RESUME" -> "恢复";
            case "TRANSFER" -> "转单";
            case "SYSTEM_SUBMIT" -> "提交完工";
            case "COMPLETE" -> "完工 / 评价";
            case "CANCEL" -> "取消工单";
            default -> action;
        };
    }

    /** 状态码转中文名；空值直接返回 null */
    private String statusText(String status) {
        // 空值返回 null，否则取状态枚举中文名
        return status == null ? null : OrderStatusEnum.nameOf(status);
    }

    /** 操作人类型码转中文；未知取值原样返回，保证前端仍有可展示内容 */
    private String operatorTypeText(String operatorType) {
        // 操作人类型为空时无法翻译
        if (operatorType == null) {
            // 空类型直接返回 null
            return null;
        }
        // 未知取值原样返回，保证前端仍有可展示内容
        return switch (operatorType) {
            case "OWNER" -> "业主";
            case "WORKER" -> "维修师傅";
            case "ADMIN" -> "物业管理员";
            case "SYSTEM" -> "系统";
            default -> operatorType;
        };
    }
}