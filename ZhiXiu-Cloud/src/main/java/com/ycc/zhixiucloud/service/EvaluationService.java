package com.ycc.zhixiucloud.service;

import com.ycc.zhixiucloud.request.EvaluateRequestVO;

/**
 * 评价服务：业主对已完工工单提交评价，并触发师傅评分回写。
 *
 * <p>职责边界：只负责评价数据写入与工单“已完工 → 已关闭”的推进；
 * 平均分重算与满意度榜更新委托 {@link MaintenanceWorkerService#refreshScore}。</p>
 *
 * <p>主要使用方：业主端工单详情页的评价入口。</p>
 *
 * <p>关键实现要点：一单一评（数据库唯一索引兜底）；评价、状态流转、师傅评分回写
 * 在同一个事务内完成；状态变更经状态机并写入流转日志、投递工单事件。</p>
 */
public interface EvaluationService {

    /**
     * 提交评价。
     *
     * <p>仅工单所属业主可评价，且工单必须已完工（COMPLETED）；同一工单只能评价一次。
     * 评价落库后由状态机执行 EVALUATE（COMPLETED -&gt; CLOSED），再刷新状态缓存，
     * 并按评价表重算该师傅平均分、更新满意度榜。</p>
     *
     * <p>副作用：写 evaluation 表、work_order_log，投递工单事件，
     * 刷新 Redis 状态缓存与月度评分榜单，写业务日志。</p>
     *
     * @param orderId   工单 ID
     * @param requestVO 评价内容（总分、速度/态度/质量分、文字与图片）
     * @throws com.ycc.zhixiucloud.common.exception.BizException 工单不存在（2001）、
     *         重复评价/并发冲突（409）、状态不允许评价（2002，由状态机抛出）
     * @throws org.springframework.security.access.AccessDeniedException 工单不属于当前业主
     */
    void submit(Long orderId, EvaluateRequestVO requestVO);
}