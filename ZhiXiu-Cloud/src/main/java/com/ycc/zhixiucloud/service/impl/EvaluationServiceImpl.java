package com.ycc.zhixiucloud.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ycc.zhixiucloud.common.enums.OperatorTypeEnum;
import com.ycc.zhixiucloud.common.enums.OrderActionEnum;
import com.ycc.zhixiucloud.common.exception.BizException;
import com.ycc.zhixiucloud.common.result.ResultCode;
import com.ycc.zhixiucloud.common.util.SecurityUtils;
import com.ycc.zhixiucloud.entity.Evaluation;
import com.ycc.zhixiucloud.entity.WorkOrder;
import com.ycc.zhixiucloud.mapper.EvaluationMapper;
import com.ycc.zhixiucloud.mapper.WorkOrderMapper;
import com.ycc.zhixiucloud.request.EvaluateRequestVO;
import com.ycc.zhixiucloud.service.EvaluationService;
import com.ycc.zhixiucloud.service.MaintenanceWorkerService;
import com.ycc.zhixiucloud.service.OrderCacheService;
import com.ycc.zhixiucloud.statemachine.WorkOrderStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 评价服务实现。
 *
 * <p>业务规则：一单一评；仅工单所属业主可评价；仅「已完工」工单可评价（由状态机 EVALUATE 动作校验）。
 * 评价落库后工单由已完工推进到已关闭，同时重算师傅平均分并刷新状态缓存。</p>
 *
 * <p>并发控制：提交前先做存在性预检，数据库唯一约束兜底，捕获 DuplicateKeyException 转为业务冲突，
 * 保证并发重复提交也只有一条评价生效；整个方法在一个事务内，评价写入、状态流转、评分回写
 * 任一步失败都会回滚。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationServiceImpl implements EvaluationService {

    private final EvaluationMapper evaluationMapper;

    private final WorkOrderMapper workOrderMapper;

    private final WorkOrderStateMachine workOrderStateMachine;

    private final MaintenanceWorkerService maintenanceWorkerService;

    private final OrderCacheService orderCacheService;

    /**
     * 提交工单评价。
     *
     * <p>流程：校验工单存在且属于当前业主 → 预检是否已评价 → 写入评价 → 状态机校验并完成
     * 「已完工 → 已关闭」流转 → 刷新工单状态缓存 → 重算师傅平均分（含评分榜）。</p>
     *
     * <p>状态机在 transition 中校验动作合法性，因此非「已完工」工单会在写评价后被拒绝，
     * 事务回滚不会留下脏评价。</p>
     *
     * @param orderId   工单 ID
     * @param requestVO 评分与评价内容
     * @throws BizException          工单不存在（ORDER_NOT_FOUND）、已评价（CONFLICT）或状态不允许评价时抛出
     * @throws AccessDeniedException 工单不属于当前业主时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long orderId, EvaluateRequestVO requestVO) {
        // 取当前登录业主 ID，用于归属校验
        Long ownerId = SecurityUtils.currentUserId();
        // 查询工单作为后续校验与装配的依据
        WorkOrder order = workOrderMapper.selectById(orderId);
        // 查不到工单则直接终止
        if (order == null) {
            // 工单不存在直接返回 404
            throw BizException.of(ResultCode.ORDER_NOT_FOUND);
        }
        // 归属校验：非本人工单不可评价
        if (!order.getOwnerId().equals(ownerId)) {
            // 仅工单所属业主可评价，越权抛 403
            throw new AccessDeniedException("该工单不属于当前业主");
        }
        // 预检重复评价：正常路径下提前返回友好提示，并发场景仍由唯一约束兜底
        if (evaluationMapper.selectCount(Wrappers.<Evaluation>lambdaQuery()
                .eq(Evaluation::getOrderId, orderId)) > 0) {
            // 已有评价记录，返回冲突提示
            throw BizException.of(ResultCode.CONFLICT, "该工单已评价，不能重复提交");
        }

        // 组装待落库的评价实体
        Evaluation evaluation = new Evaluation();
        // 关联工单 ID
        evaluation.setOrderId(orderId);
        // 记录评价人（业主）
        evaluation.setOwnerId(ownerId);
        // 被评师傅取工单的接单师傅
        evaluation.setWorkerId(order.getAcceptedWorkerId());
        // 总评分
        evaluation.setScore(requestVO.getScore());
        // 响应速度分
        evaluation.setSpeedScore(requestVO.getSpeedScore());
        // 服务态度分
        evaluation.setAttitudeScore(requestVO.getAttitudeScore());
        // 维修质量分
        evaluation.setQualityScore(requestVO.getQualityScore());
        // 评价文字内容
        evaluation.setContent(requestVO.getContent());
        // 评价图片
        evaluation.setImages(requestVO.getImages());
        // 写入评价，唯一索引兜底并发重复提交
        try {
            // 执行插入落库
            evaluationMapper.insert(evaluation);
        } catch (DuplicateKeyException e) {
            // 并发重复提交时唯一索引冲突，统一转换为业务冲突提示
            throw BizException.of(ResultCode.CONFLICT, "该工单已评价，不能重复提交");
        }

        // COMPLETED -> CLOSED，状态合法性由状态机校验
        workOrderStateMachine.transition(order, OrderActionEnum.EVALUATE, ownerId,
                OperatorTypeEnum.OWNER, "业主评价 " + requestVO.getScore() + " 星");
        // 状态已变更，刷新工单状态缓存
        orderCacheService.cacheStatus(order);
        // 有接单师傅时同步重算评分
        if (order.getAcceptedWorkerId() != null) {
            // 评价影响师傅平均分：重算并同步更新评分榜单
            maintenanceWorkerService.refreshScore(order.getAcceptedWorkerId());
        }
        // 记录评价提交日志
        log.info("业主提交评价：orderId={}, workerId={}, score={}",
                orderId, order.getAcceptedWorkerId(), requestVO.getScore());
    }
}