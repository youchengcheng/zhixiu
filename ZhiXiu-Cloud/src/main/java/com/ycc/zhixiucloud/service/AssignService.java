package com.ycc.zhixiucloud.service;

import com.ycc.zhixiucloud.request.AssignOrderRequestVO;
import com.ycc.zhixiucloud.request.ReassignOrderRequestVO;
import com.ycc.zhixiucloud.response.RecommendWorkerResponseVO;

import java.util.List;

/**
 * 派单服务：抢单、接受派单、手动派单、改派与智能推荐。
 *
 * <p>职责边界：只负责“把工单交到某位师傅手上”及派单参考信息；
 * 状态合法性、流转日志、师傅负载结算和事件投递统一由
 * {@link com.ycc.zhixiucloud.statemachine.WorkOrderStateMachine} 完成。</p>
 *
 * <p>主要使用方：师傅端抢单/接单接口、管理端派单与改派接口。</p>
 *
 * <p>关键实现要点：</p>
 * <ul>
 *   <li>并发控制：抢单使用工单维度 Redisson 锁（GRAB_LOCK），派单/改派使用 ASSIGN_LOCK，
 *       保证“读状态 → 改状态”整段串行；状态更新本身还有数据库乐观锁兜底；</li>
 *   <li>缓存联动：接单/派单后把工单移出 Redis 待接单池并刷新状态缓存；
 *       抢单成功还会累加月度接单榜；</li>
 *   <li>心跳联动：接单/改派后刷新师傅在线心跳中的负载；</li>
 *   <li>推荐算法：技能匹配 50% + 当前负载 30% + 平均评分 20%，取 Top3。</li>
 * </ul>
 */
public interface AssignService {

    /**
     * 师傅抢单：PENDING/ASSIGNED -&gt; ACCEPTED。
     *
     * <p>先校验当前登录师傅可用，再获取工单锁串行处理。若工单已被他人接单抛 2003；
     * 其它不可抢状态抛 2002；ASSIGNED 状态仅允许被派单的师傅抢。</p>
     *
     * <p>副作用：状态机写流转日志、结算师傅负载并投递工单事件；
     * 工单移出待接单池、刷新状态缓存、月度接单榜 +1、刷新在线心跳。</p>
     *
     * @param orderId 工单 ID
     * @throws com.ycc.zhixiucloud.common.exception.BizException 工单不存在（2001）、
     *         状态不允许（2002）、已被抢（2003）、获取锁失败（409）或师傅未审核/被禁用
     */
    void grab(Long orderId);

    /**
     * 师傅接受管理员派单：ASSIGNED -&gt; ACCEPTED。
     *
     * <p>仅当工单处于 ASSIGNED 且 assignedWorkerId 为当前登录师傅时允许操作；
     * 该方法不额外加分布式锁，状态更新由状态机乐观锁保证一致性。</p>
     *
     * <p>副作用：同抢单成功（流转日志、负载结算、事件投递、缓存/榜单/心跳维护）。</p>
     *
     * @param orderId 工单 ID
     * @throws com.ycc.zhixiucloud.common.exception.BizException 工单不存在（2001）、
     *         未派给当前师傅或状态不符（2002）
     */
    void accept(Long orderId);

    /**
     * 管理员手动派单：PENDING -&gt; ASSIGNED。
     *
     * <p>目标师傅必须审核通过且未被禁用；获取 ASSIGN_LOCK 后再次校验工单仍为待接单，
     * 避免与抢单并发时覆盖结果。</p>
     *
     * <p>副作用：状态机写流转日志、结算负载并投递事件；工单移出待接单池并刷新状态缓存。</p>
     *
     * @param orderId   工单 ID
     * @param requestVO 派单参数，包含目标师傅 ID 与可选备注
     * @throws com.ycc.zhixiucloud.common.exception.BizException 师傅不存在/未审核/已禁用、
     *         工单不存在（2001）或状态不是待接单（2002）
     */
    void assign(Long orderId, AssignOrderRequestVO requestVO);

    /**
     * 管理员改派：ASSIGNED/ACCEPTED/TRANSFER_PENDING -&gt; ASSIGNED。
     *
     * <p>用于原师傅无法继续处理或转单申请的场景；获取 ASSIGN_LOCK 串行化同一工单的派单操作。
     * 状态机会清空原受理师傅并重算相关师傅负载。</p>
     *
     * <p>副作用：写流转日志、投递事件、刷新缓存，并同步刷新新师傅在线心跳中的负载。</p>
     *
     * @param orderId   工单 ID
     * @param requestVO 改派参数，包含新师傅 ID 与改派备注
     * @throws com.ycc.zhixiucloud.common.exception.BizException 新师傅不可派、
     *         工单不存在（2001）或当前状态不允许改派（2002）
     */
    void reassign(Long orderId, ReassignOrderRequestVO requestVO);

    /**
     * 智能派单推荐：为指定工单推荐最合适的师傅。
     *
     * <p>候选集为审核通过且未被禁用的师傅；匹配分 = 技能覆盖（50%）+
     * 当前负载（30%，越低越高）+ 平均评分（20%），按匹配分倒序取前 3。
     * 该方法只读，不修改工单或师傅状态。</p>
     *
     * @param orderId 工单 ID，用于读取报修类型以判断技能匹配
     * @return 推荐师傅列表（最多 3 条，含匹配分与推荐理由）；无可用师傅时返回空列表
     * @throws com.ycc.zhixiucloud.common.exception.BizException 工单不存在时抛出 2001
     */
    List<RecommendWorkerResponseVO> recommendWorkers(Long orderId);
}