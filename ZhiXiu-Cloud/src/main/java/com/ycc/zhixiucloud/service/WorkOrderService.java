package com.ycc.zhixiucloud.service;

import com.ycc.zhixiucloud.common.result.PageResult;
import com.ycc.zhixiucloud.request.AdminOrderQueryRequestVO;
import com.ycc.zhixiucloud.request.ArriveOrderRequestVO;
import com.ycc.zhixiucloud.request.CancelOrderRequestVO;
import com.ycc.zhixiucloud.request.CreateOrderRequestVO;
import com.ycc.zhixiucloud.request.OrderQueryRequestVO;
import com.ycc.zhixiucloud.request.PauseOrderRequestVO;
import com.ycc.zhixiucloud.request.SubmitOrderRequestVO;
import com.ycc.zhixiucloud.request.TransferOrderRequestVO;
import com.ycc.zhixiucloud.response.OrderCreateResponseVO;
import com.ycc.zhixiucloud.response.OrderDetailResponseVO;
import com.ycc.zhixiucloud.response.OrderListResponseVO;
import com.ycc.zhixiucloud.response.WorkOrderLogResponseVO;

import java.util.List;

/**
 * 工单服务：创建、三端查询、师傅现场操作、业主取消/确认与管理端取消。
 *
 * <p>职责边界：负责业务编排与查询组装，所有状态变更一律经
 * {@link com.ycc.zhixiucloud.statemachine.WorkOrderStateMachine}；状态机负责合法流转校验、
 * 乐观锁更新、流转日志、师傅负载/计数结算与 RocketMQ 事件投递。</p>
 *
 * <p>主要使用方：业主端、师傅端、管理端工单接口；AI 助手确认草稿时也会调用
 * {@link #createFromAiDraft} 创建工单。</p>
 *
 * <p>关键实现要点：</p>
 * <ul>
 *   <li>创建工单：校验房产归属与二级报修类型 → 匹配 SLA → 写入超时点 → 落库 →
 *       状态机写初始日志/投递事件 → 加入 Redis 待接单池 → 投递 SLA 超时延迟消息；</li>
 *   <li>状态变更：数据库事务由状态机保证；Redis 侧（待接单池、状态缓存、师傅心跳）
 *       在事务之外维护，属于可自愈的读加速数据；</li>
 *   <li>待接单列表：数据源为 Redis ZSet，保持池内优先级顺序，
 *       并会剔除已不在待接单状态的脏数据；</li>
 *   <li>权限：业主只能操作本人工单，师傅只能操作自己名下的工单（待接单详情除外）。</li>
 * </ul>
 */
public interface WorkOrderService {

    /**
     * 业主手动提交报修。
     *
     * <p>创建流程与 AI 草稿确认共用：校验房产归属与二级报修类型、匹配 SLA、
     * 生成工单号并落库，随后由状态机写入 CREATED 日志、加入待接单池、安排超时任务。
     * 来源记为 MANUAL。</p>
     *
     * @param requestVO 报修内容（房产、类型、标题、描述、紧急度、影响范围、图片等）
     * @return 新工单 ID 与工单号
     * @throws com.ycc.zhixiucloud.common.exception.BizException 房产不存在/不属于本人、
     *         报修类型不存在或未选择二级类型等
     */
    OrderCreateResponseVO createByOwner(CreateOrderRequestVO requestVO);

    /**
     * AI 助手草稿确认后创建工单。
     *
     * <p>与手动报修共用创建流程，区别是来源记为 AI，并把来源草稿消息 ID 写入工单，
     * 便于追溯“哪一轮对话生成了该工单”。调用方为
     * {@link AiAssistantService#confirmDraft}。</p>
     *
     * @param requestVO 合并后的报修内容
     * @param aiDraftId 来源 AI 草稿消息 ID
     * @return 新工单 ID 与工单号
     * @throws com.ycc.zhixiucloud.common.exception.BizException 房产/类型校验失败等，
     *         同 {@link #createByOwner}
     */
    OrderCreateResponseVO createFromAiDraft(CreateOrderRequestVO requestVO, Long aiDraftId);

    /**
     * 业主工单分页列表。
     *
     * <p>仅返回当前登录业主的工单，支持状态、报修类型与关键字（工单号/标题）筛选，
     * 按创建时间倒序。</p>
     *
     * @param requestVO 分页与筛选参数
     * @return 工单列表分页结果（含类型、地址、师傅、SLA 超时提示等展示字段）
     */
    PageResult<OrderListResponseVO> pageForOwner(OrderQueryRequestVO requestVO);

    /**
     * 业主工单详情。
     *
     * @param id 工单 ID
     * @return 工单详情（含评价、师傅、SLA 与各节点时间）
     * @throws com.ycc.zhixiucloud.common.exception.BizException 工单不存在（2001）
     * @throws org.springframework.security.access.AccessDeniedException 工单不属于当前业主
     */
    OrderDetailResponseVO getDetailForOwner(Long id);

    /**
     * 业主查询本人工单的流转记录。
     *
     * @param id 工单 ID
     * @return 按时间正序的流转记录
     * @throws com.ycc.zhixiucloud.common.exception.BizException 工单不存在（2001）
     * @throws org.springframework.security.access.AccessDeniedException 工单不属于当前业主
     */
    List<WorkOrderLogResponseVO> listLogsForOwner(Long id);

    /**
     * 业主取消工单。
     *
     * <p>可取消状态由状态机限制为 PENDING / ASSIGNED / ACCEPTED；取消原因写入工单与流转日志。
     * 状态机会结算师傅负载并投递取消事件，随后刷新待接单池、状态缓存与师傅心跳。</p>
     *
     * @param id        工单 ID
     * @param requestVO 取消原因
     * @throws com.ycc.zhixiucloud.common.exception.BizException 工单不存在（2001）、
     *         当前状态不可取消（2002）或并发冲突（409）
     * @throws org.springframework.security.access.AccessDeniedException 工单不属于当前业主
     */
    void cancelByOwner(Long id, CancelOrderRequestVO requestVO);

    /**
     * 业主确认完工：SUBMITTED -&gt; COMPLETED。
     *
     * <p>确认后状态机记录完工时间、增加师傅完工计数并重算负载，随后刷新缓存与心跳。
     * 工单进入已完工状态后才可以评价。</p>
     *
     * @param id 工单 ID
     * @throws com.ycc.zhixiucloud.common.exception.BizException 工单不存在（2001）
     *         或当前状态不允许确认（2002）
     * @throws org.springframework.security.access.AccessDeniedException 工单不属于当前业主
     */
    void confirmByOwner(Long id);

    /**
     * 师傅待接单列表。
     *
     * <p>先校验当前登录师傅可用，再从 Redis 待接单池按优先级读取 ID，回表校验状态后
     * 内存分页，保证列表顺序仍为池内优先级（紧急度高、SLA 权重高、创建早）。
     * 池中已不在待接单状态的脏数据会被就地清理。</p>
     *
     * @param requestVO 分页与报修类型筛选参数
     * @return 待接单工单分页结果；池为空时返回空分页
     * @throws com.ycc.zhixiucloud.common.exception.BizException 师傅未审核或被禁用
     */
    PageResult<OrderListResponseVO> pagePending(OrderQueryRequestVO requestVO);

    /**
     * 师傅“我的工单”列表。
     *
     * <p>查询受理师傅或派单师傅为当前师傅的工单，支持状态、类型筛选，按创建时间倒序。</p>
     *
     * @param workerId  师傅 ID（maintenance_worker.id）
     * @param requestVO 分页与筛选参数
     * @return 工单列表分页结果
     */
    PageResult<OrderListResponseVO> pageForWorker(Long workerId, OrderQueryRequestVO requestVO);

    /**
     * 师傅工单详情。
     *
     * <p>可见范围：仍处于待接单的工单，或受理师傅/派单师傅为当前师傅的工单。</p>
     *
     * @param id 工单 ID
     * @return 工单详情
     * @throws com.ycc.zhixiucloud.common.exception.BizException 工单不存在（2001）
     * @throws org.springframework.security.access.AccessDeniedException 工单不在当前师傅名下
     */
    OrderDetailResponseVO getDetailForWorker(Long id);

    /**
     * 师傅到场签到：ACCEPTED -&gt; GOING。
     *
     * <p>仅受理师傅本人可操作；若传了经纬度会写入流转备注，否则使用默认备注。
     * 状态机记录到场时间、投递事件并重算负载，随后刷新缓存与师傅心跳。</p>
     *
     * @param id        工单 ID
     * @param requestVO 签到参数（可选经纬度）
     * @throws com.ycc.zhixiucloud.common.exception.BizException 工单不存在（2001）
     *         或当前状态不允许签到（2002）
     * @throws org.springframework.security.access.AccessDeniedException 工单不在当前师傅名下
     */
    void arrive(Long id, ArriveOrderRequestVO requestVO);

    /**
     * 师傅暂停工单：GOING -&gt; PAUSED。
     *
     * <p>用于缺料、业主不在等临时中断场景，暂停原因写入流转日志。</p>
     *
     * @param id        工单 ID
     * @param requestVO 暂停原因
     * @throws com.ycc.zhixiucloud.common.exception.BizException 工单不存在（2001）
     *         或当前状态不允许暂停（2002）
     * @throws org.springframework.security.access.AccessDeniedException 工单不在当前师傅名下
     */
    void pause(Long id, PauseOrderRequestVO requestVO);

    /**
     * 师傅恢复工单：PAUSED -&gt; GOING。
     *
     * @param id 工单 ID
     * @throws com.ycc.zhixiucloud.common.exception.BizException 工单不存在（2001）
     *         或当前状态不允许恢复（2002）
     * @throws org.springframework.security.access.AccessDeniedException 工单不在当前师傅名下
     */
    void resume(Long id);

    /**
     * 师傅申请转单：GOING -&gt; TRANSFER_PENDING。
     *
     * <p>转单原因写入工单与流转日志，之后由管理员改派或驳回
     * （驳回逻辑由管理端处理）。</p>
     *
     * @param id        工单 ID
     * @param requestVO 转单原因
     * @throws com.ycc.zhixiucloud.common.exception.BizException 工单不存在（2001）
     *         或当前状态不允许转单（2002）
     * @throws org.springframework.security.access.AccessDeniedException 工单不在当前师傅名下
     */
    void transfer(Long id, TransferOrderRequestVO requestVO);

    /**
     * 师傅提交完工：GOING -&gt; SUBMITTED。
     *
     * <p>写入处理说明与完工照片，状态机记录提交时间、增加相关计数并投递事件；
     * 之后等待业主确认（或超时自动确认）。</p>
     *
     * @param id        工单 ID
     * @param requestVO 处理说明与完工图片
     * @throws com.ycc.zhixiucloud.common.exception.BizException 工单不存在（2001）
     *         或当前状态不允许提交（2002）
     * @throws org.springframework.security.access.AccessDeniedException 工单不在当前师傅名下
     */
    void submit(Long id, SubmitOrderRequestVO requestVO);

    /**
     * 管理端工单分页列表。
     *
     * <p>支持状态、紧急度、报修类型、是否 SLA 升级与关键字（工单号/标题/业主姓名/手机号）
     * 筛选，按创建时间倒序。</p>
     *
     * @param requestVO 分页与筛选参数
     * @return 工单列表分页结果
     */
    PageResult<OrderListResponseVO> pageForAdmin(AdminOrderQueryRequestVO requestVO);

    /**
     * 管理端工单详情，不限制工单归属。
     *
     * @param id 工单 ID
     * @return 工单详情（含评价与各节点时间）
     * @throws com.ycc.zhixiucloud.common.exception.BizException 工单不存在（2001）
     */
    OrderDetailResponseVO getDetailForAdmin(Long id);

    /**
     * 管理端查询工单流转记录。
     *
     * @param id 工单 ID
     * @return 按时间正序的流转记录
     * @throws com.ycc.zhixiucloud.common.exception.BizException 工单不存在（2001）
     */
    List<WorkOrderLogResponseVO> listLogsForAdmin(Long id);

    /**
     * 管理员取消工单。
     *
     * <p>可取消状态由状态机限制；取消原因写入工单与流转日志，状态机会按当前工单集合
     * 重算受影响师傅的负载并投递取消事件。</p>
     *
     * @param id        工单 ID
     * @param requestVO 取消原因
     * @throws com.ycc.zhixiucloud.common.exception.BizException 工单不存在（2001）、
     *         当前状态不可取消（2002）或并发冲突（409）
     */
    void cancelByAdmin(Long id, CancelOrderRequestVO requestVO);
}