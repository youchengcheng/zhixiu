package com.ycc.zhixiucloud.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ycc.zhixiucloud.common.enums.AffectScopeEnum;
import com.ycc.zhixiucloud.common.enums.OperatorTypeEnum;
import com.ycc.zhixiucloud.common.enums.OrderActionEnum;
import com.ycc.zhixiucloud.common.enums.OrderSourceEnum;
import com.ycc.zhixiucloud.common.enums.OrderStatusEnum;
import com.ycc.zhixiucloud.common.enums.UrgencyEnum;
import com.ycc.zhixiucloud.common.exception.BizException;
import com.ycc.zhixiucloud.common.result.PageResult;
import com.ycc.zhixiucloud.common.result.ResultCode;
import com.ycc.zhixiucloud.common.util.OrderNoUtil;
import com.ycc.zhixiucloud.common.util.SecurityUtils;
import com.ycc.zhixiucloud.entity.Evaluation;
import com.ycc.zhixiucloud.entity.MaintenanceWorker;
import com.ycc.zhixiucloud.entity.Property;
import com.ycc.zhixiucloud.entity.RepairCategory;
import com.ycc.zhixiucloud.entity.SlaConfig;
import com.ycc.zhixiucloud.entity.User;
import com.ycc.zhixiucloud.entity.WorkOrder;
import com.ycc.zhixiucloud.mapper.EvaluationMapper;
import com.ycc.zhixiucloud.mapper.MaintenanceWorkerMapper;
import com.ycc.zhixiucloud.mapper.PropertyMapper;
import com.ycc.zhixiucloud.mapper.RepairCategoryMapper;
import com.ycc.zhixiucloud.mapper.UserMapper;
import com.ycc.zhixiucloud.mapper.WorkOrderMapper;
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
import com.ycc.zhixiucloud.response.OrderEvaluationResponseVO;
import com.ycc.zhixiucloud.response.OrderListResponseVO;
import com.ycc.zhixiucloud.response.OrderWorkerResponseVO;
import com.ycc.zhixiucloud.response.WorkOrderLogResponseVO;
import com.ycc.zhixiucloud.service.MaintenanceWorkerService;
import com.ycc.zhixiucloud.service.OrderCacheService;
import com.ycc.zhixiucloud.service.SlaService;
import com.ycc.zhixiucloud.service.WorkOrderLogService;
import com.ycc.zhixiucloud.service.WorkOrderService;
import com.ycc.zhixiucloud.service.WorkerPresenceService;
import com.ycc.zhixiucloud.statemachine.WorkOrderStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工单服务实现：工单全生命周期的编排入口，覆盖业主报修、三端查询、师傅现场操作与管理端取消。
 *
 * <p>职责边界与协作：
 * <ul>
 *   <li>状态机：所有状态变更统一委托 {@link WorkOrderStateMachine}，由它校验流转合法性、完成乐观锁更新、
 *       写入流转日志并投递 RocketMQ 事件；本类只做操作人身份/工单归属校验和本次动作附加字段的赋值。</li>
 *   <li>并发控制：本类不直接持有分布式锁。抢单、派单等竞争路径由 AssignService 基于 Redisson 按工单加锁，
 *       状态落库再由状态机的 version 乐观锁兜底，防止并发请求互相覆盖。</li>
 *   <li>SLA：创建工单时由 {@link SlaService} 按「类型 + 紧急度 + 安全隐患 + 影响范围」匹配规则，
 *       计算响应/处理截止时间并投递超时延迟任务。</li>
 *   <li>缓存：Redis 侧（待接单池、状态缓存、师傅心跳）在数据库事务之外维护，属于可自愈的读加速数据；
 *       待接单池读取时会剔除已不在待接单状态的脏数据。</li>
 *   <li>在线状态：状态变更后通过 {@link WorkerPresenceService} 刷新接单师傅心跳，供派单推荐参考。</li>
 *   <li>幂等：状态动作由「当前状态 + 动作」决定，重复提交同一动作会因状态已推进被状态机拒绝；
 *       待接单池的加入/移除本身幂等。创建接口未做请求级去重，每次调用都会生成新工单。</li>
 * </ul>
 *
 * <p>事务边界：创建与写操作方法均声明 {@code rollbackFor = Exception.class}，保证工单数据、流转日志
 * 与 MQ 事件在状态机事务内保持一致；Redis 维护是事务方法内的非事务副作用，只影响读加速数据。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkOrderServiceImpl implements WorkOrderService {

    /** 一级类型的 parent_id */
    private static final long ROOT_PARENT_ID = 0L;

    /** 师傅状态：禁用 */
    private static final int WORK_STATUS_DISABLED = 4;

    /** 审核状态：通过 */
    private static final int AUDIT_PASSED = 1;

    /** 关键字查询命中的业主上限，避免 IN 条件过长 */
    private static final int KEYWORD_OWNER_LIMIT = 200;

    /** 期望上门时段的默认值：手动报修与 AI 报修都未填写时统一按此处理 */
    private static final String DEFAULT_EXPECTED_VISIT_TIME = "一个工作日内";

    private final WorkOrderMapper workOrderMapper;

    private final PropertyMapper propertyMapper;

    private final RepairCategoryMapper repairCategoryMapper;

    private final MaintenanceWorkerMapper maintenanceWorkerMapper;

    private final UserMapper userMapper;

    private final EvaluationMapper evaluationMapper;

    private final WorkOrderStateMachine workOrderStateMachine;

    private final WorkOrderLogService workOrderLogService;

    private final SlaService slaService;

    private final OrderCacheService orderCacheService;

    private final WorkerPresenceService workerPresenceService;

    private final MaintenanceWorkerService maintenanceWorkerService;

    // ------------------------------------------------------------------ 业主端

    /**
     * 业主手动提交报修：来源记为 MANUAL，复用统一的创建流程。
     *
     * <p>副作用：工单落库；状态机写入 CREATED 日志并投递 MQ 事件；加入待接单池、写状态缓存、
     * 安排 SLA 超时任务。上述数据库写入在同一事务内完成。</p>
     *
     * @param createOrderRequestVO 报修表单，须包含本人房产、二级报修类型、紧急度等
     * @return 新工单的 ID 与工单号
     * @throws BizException 房产不存在、房产不属于当前业主、报修类型不存在或未选择二级类型
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderCreateResponseVO createByOwner(CreateOrderRequestVO createOrderRequestVO) {
        // 手动报修：来源 MANUAL，无 AI 草稿
        return doCreate(createOrderRequestVO, OrderSourceEnum.MANUAL, null, "业主提交报修");
    }

    /**
     * 智能助手草稿确认后提交报修：来源记为 AI，并保存草稿消息 ID 以便溯源。
     *
     * @param requestVO 业主确认后的报修表单
     * @param aiDraftId AI 草稿消息 ID，可为 null
     * @return 新工单的 ID 与工单号
     * @throws BizException 校验失败时抛出对应业务错误码（同 {@link #createByOwner(CreateOrderRequestVO)}）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderCreateResponseVO createFromAiDraft(CreateOrderRequestVO requestVO, Long aiDraftId) {
        // 智能助手报修：来源 AI，并记录来源草稿消息 ID
        return doCreate(requestVO, OrderSourceEnum.AI, aiDraftId, "智能助手生成工单草稿");
    }

    /**
     * 创建工单公共流程：手动报修与 AI 草稿确认共用，仅来源、草稿 ID 与流转备注不同。
     *
     * <p>依次完成：业主/房产/类型校验 → SLA 规则匹配 → 组装工单并计算超时点 → 落库 →
     * 状态机写 CREATED 日志与事件 → 维护待接单池、状态缓存、SLA 延迟任务。
     * 数据库写入依赖调用方事务；Redis 与 MQ 副作用见各步骤注释。</p>
     *
     * @param createOrderRequestVO 报修表单
     * @param source    工单来源（MANUAL / AI）
     * @param aiDraftId AI 草稿消息 ID，手动报修为 null
     * @param remark    写入创建流转日志的备注
     * @return 新工单 ID 与工单号
     * @throws BizException 房产/类型校验失败时抛出对应业务错误码
     */
    private OrderCreateResponseVO doCreate(CreateOrderRequestVO createOrderRequestVO, OrderSourceEnum source,
                                           Long aiDraftId, String remark) {
        // 当前登录业主 ID
        Long ownerId = SecurityUtils.currentUserId();
        // 校验房产存在
        Property property = propertyMapper.selectById(createOrderRequestVO.getPropertyId());
        if (property == null) {
            // 房产不存在，直接拒绝报修
            throw BizException.of(ResultCode.NOT_FOUND, "报修房产不存在");
        }

        // 校验房产归属
        if (!property.getOwnerId().equals(ownerId)) {
            // 房产不属于当前业主，抛出越权业务码
            throw BizException.of(ResultCode.PROPERTY_NOT_OWNED);
        }

        // 校验报修类型存在
        RepairCategory category = repairCategoryMapper.selectById(createOrderRequestVO.getCategoryId());

        // 类型不存在则拒绝
        if (category == null) {
            // 报修类型不存在
            throw BizException.of(ResultCode.NOT_FOUND, "报修类型不存在");
        }
        // 必须选择二级报修类型
        if (category.getParentId() == null || category.getParentId() == ROOT_PARENT_ID) {
            // 仅允许选择二级类型，否则拒绝
            throw BizException.of(ResultCode.PARAM_ERROR, "请选择二级报修类型");
        }

        // 按类型/紧急度/隐患/影响范围匹配 SLA 配置
        SlaConfig slaConfig = slaService.matchSla(category.getId(), createOrderRequestVO.getUrgency(),
                createOrderRequestVO.getSafetyFlag(), createOrderRequestVO.getAffectScope());

        // 组装工单实体
        WorkOrder order = new WorkOrder();
        // 预生成雪花 ID 作为主键
        long orderId = IdWorker.getId();
        // 统一取一次当前时间，作为创建时间基准
        LocalDateTime now = LocalDateTime.now();
        // 设置工单主键
        order.setId(orderId);
        // 生成工单号，规则为 WO + 时间 + 序列
        order.setOrderNo(OrderNoUtil.generate(orderId, now));
        // 关联报修房产 ID
        order.setPropertyId(property.getId());
        // 记录报修业主 ID
        order.setOwnerId(ownerId);
        // 关联二级报修类型
        order.setCategoryId(category.getId());
        // 报修标题
        order.setTitle(createOrderRequestVO.getTitle());
        // 故障详细描述
        order.setDescription(createOrderRequestVO.getDescription());
        // 故障持续时长
        order.setFaultDuration(createOrderRequestVO.getFaultDuration());
        // 影响范围缺省为「本户」
        order.setAffectScope(createOrderRequestVO.getAffectScope() == null
                ? AffectScopeEnum.HOUSEHOLD.getCode() : createOrderRequestVO.getAffectScope());
        // 是否存在安全隐患
        order.setSafetyFlag(createOrderRequestVO.getSafetyFlag());
        // 设备信息
        order.setDeviceInfo(createOrderRequestVO.getDeviceInfo());
        // 期望上门时间：未填写时统一按「一个工作日内」处理，手动报修与 AI 报修一致
        order.setExpectedVisitTime(StringUtils.hasText(createOrderRequestVO.getExpectedVisitTime())
                ? createOrderRequestVO.getExpectedVisitTime() : DEFAULT_EXPECTED_VISIT_TIME);
        // 联系电话
        order.setContactPhone(createOrderRequestVO.getContactPhone());
        // 紧急度
        order.setUrgency(createOrderRequestVO.getUrgency());
        // 报修图片（逗号分隔 URL）
        order.setImages(createOrderRequestVO.getImages());
        // 新工单统一进入待接单状态
        order.setStatus(OrderStatusEnum.PENDING.getCode());
        // 来源按调用方传入（MANUAL 手填 / AI 智能助手）
        order.setSource(source.getCode());
        // AI 来源时记录草稿消息 ID，便于溯源
        order.setAiDraftId(aiDraftId);
        // 超时点以工单创建时间为基准，因此先确定创建时间再写入（与自动填充保持一致）
        order.setCreateTime(now);
        // 依据 SLA 配置写入响应/处理超时点
        slaService.applyDeadlines(order, slaConfig);

        // 工单落库
        workOrderMapper.insert(order);
        // 走状态机写入 CREATED 流转日志并投递事件
        workOrderStateMachine.onCreated(order, OperatorTypeEnum.OWNER, remark);
        // 加入待接单池并按 SLA 优先级权重排序
        orderCacheService.addToPendingPool(order, slaConfig == null ? null : slaConfig.getPriorityWeight());
        // 缓存工单状态
        orderCacheService.cacheStatus(order);
        // 安排 SLA 超时延迟任务
        slaService.scheduleTimeoutTasks(order);
        // 记录报修提交关键信息，便于排查
        log.info("提交报修：orderId={}, orderNo={}, ownerId={}, source={}, categoryId={}, urgency={}",
                order.getId(), order.getOrderNo(), ownerId, source.getCode(), category.getId(), order.getUrgency());
        // 返回工单 ID 与工单号
        return new OrderCreateResponseVO(order.getId(), order.getOrderNo());
    }

    /**
     * 业主工单分页列表：数据范围强制限定为当前登录业主，支持状态、类型与关键字过滤。
     *
     * @param requestVO 分页与筛选条件；关键字同时匹配工单号与标题
     * @return 按创建时间倒序的工单分页结果（含分类名、地址、师傅等展示字段）
     */
    @Override
    public PageResult<OrderListResponseVO> pageForOwner(OrderQueryRequestVO requestVO) {
        // 当前登录业主 ID，作为数据范围
        Long ownerId = SecurityUtils.currentUserId();
        // 组装业主端查询条件：锁定本人 + 可选状态/类型/关键字
        LambdaQueryWrapper<WorkOrder> wrapper = Wrappers.<WorkOrder>lambdaQuery()
                .eq(WorkOrder::getOwnerId, ownerId)
                .eq(StringUtils.hasText(requestVO.getStatus()), WorkOrder::getStatus, requestVO.getStatus())
                .eq(requestVO.getCategoryId() != null, WorkOrder::getCategoryId, requestVO.getCategoryId())
                .and(StringUtils.hasText(requestVO.getKeyword()), w -> w
                        .like(WorkOrder::getOrderNo, requestVO.getKeyword())
                        .or()
                        .like(WorkOrder::getTitle, requestVO.getKeyword()))
                .orderByDesc(WorkOrder::getCreateTime);
        // 转成列表分页返回
        return toPage(selectPage(requestVO, wrapper));
    }

    /**
     * 业主工单详情：仅允许查看本人名下工单。
     *
     * @param id 工单 ID
     * @return 工单详情（含房产、分类、业主、师傅与评价信息）
     * @throws BizException 工单不存在
     * @throws AccessDeniedException 工单不属于当前业主
     */
    @Override
    public OrderDetailResponseVO getDetailForOwner(Long id) {
        // 校验归属后组装详情返回
        return toDetailVO(requireOwnedOrder(id));
    }

    /**
     * 业主查看本人工单的流转记录：先做归属校验，再按时间正序返回。
     *
     * @param id 工单 ID
     * @return 流转日志列表（已补操作人姓名与状态/动作中文文案）
     * @throws BizException 工单不存在
     * @throws AccessDeniedException 工单不属于当前业主
     */
    @Override
    public List<WorkOrderLogResponseVO> listLogsForOwner(Long id) {
        // 先校验工单归属当前业主
        requireOwnedOrder(id);
        // 按时间正序返回流转日志
        return workOrderLogService.listByOrder(id);
    }

    /**
     * 业主取消工单：仅本人名下、且处于允许取消的状态（PENDING / ASSIGNED / ACCEPTED）时可执行。
     * 状态合法性由状态机校验，非法流转抛出业务异常。
     *
     * <p>副作用：状态机更新状态、写日志、投递 MQ 事件；随后刷新状态缓存、移出待接单池并更新师傅心跳。</p>
     *
     * @param id        工单 ID
     * @param requestVO 取消原因
     * @throws BizException 工单不存在、状态不允许取消或并发更新冲突
     * @throws AccessDeniedException 工单不属于当前业主
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelByOwner(Long id, CancelOrderRequestVO requestVO) {
        // 校验归属并读取工单
        WorkOrder order = requireOwnedOrder(id);
        // 写入取消原因，随状态机一并落库
        order.setCancelReason(requestVO.getCancelReason());
        // 走状态机执行取消流转，非法状态由其拦截
        workOrderStateMachine.transition(order, OrderActionEnum.CANCEL, SecurityUtils.currentUserId(),
                OperatorTypeEnum.OWNER, requestVO.getCancelReason());
        // 同步刷新状态缓存、待接单池与师傅心跳
        afterStatusChanged(order);
    }

    /**
     * 业主确认完工：SUBMITTED → COMPLETED，合法性与完成时间由状态机处理。
     *
     * @param id 工单 ID
     * @throws BizException 工单不存在、当前状态不允许确认或并发更新冲突
     * @throws AccessDeniedException 工单不属于当前业主
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmByOwner(Long id) {
        // 校验归属并读取工单
        WorkOrder order = requireOwnedOrder(id);
        // 走状态机确认完工，写入完成时间
        workOrderStateMachine.transition(order, OrderActionEnum.CONFIRM, SecurityUtils.currentUserId(),
                OperatorTypeEnum.OWNER, "业主确认完工");
        // 刷新状态缓存与师傅心跳
        afterStatusChanged(order);
    }

    // ------------------------------------------------------------------ 师傅端

    /**
     * 师傅待接单分页列表：数据源为 Redis 待接单池（按紧急度、创建时间与 SLA 权重排好序），
     * 因此采用内存分页保持池内优先级顺序。
     *
     * <p>读到池成员后批量查询数据库，并在遍历中就地剔除已不在 PENDING 的脏成员，
     * 保证列表不会展示已被接单/取消的工单，同时让缓存逐步自愈。</p>
     *
     * @param requestVO 分页与报修类型筛选条件
     * @return 待接单工单分页；池为空时直接返回空分页
     * @throws AccessDeniedException 当前账号不是维修师傅
     * @throws BizException 师傅档案未审核或账号被禁用
     */
    @Override
    public PageResult<OrderListResponseVO> pagePending(OrderQueryRequestVO requestVO) {
        // 校验当前账号为可用维修师傅
        maintenanceWorkerService.requireCurrentWorker();
        // 读取待接单池的工单 ID 顺序（已按优先级排好）
        List<Long> pooledIds = orderCacheService.listPendingOrderIds();
        // 池为空直接返回空分页
        if (pooledIds.isEmpty()) {
            // 记录空池日志便于排查
            log.info("待接单池为空，返回空分页：pageNum={}", requestVO.getPageNum());
            // 返回空分页
            return PageResult.empty(requestVO.getPageNum(), requestVO.getPageSize());
        }
        // 批量查询池内工单，避免循环内 N+1
        Map<Long, WorkOrder> orderMap = byId(pooledIds);
        // 按池内顺序收集有效待接单工单
        List<WorkOrder> pending = new ArrayList<>(pooledIds.size());
        // 逐条校验池成员的当前状态
        for (Long orderId : pooledIds) {
            // 取出对应工单实体
            WorkOrder order = orderMap.get(orderId);
            // 池中残留的非待接单数据就地清理，保证列表不出现已完成工单
            if (order == null || !OrderStatusEnum.PENDING.getCode().equals(order.getStatus())) {
                // 移除池中的脏成员
                orderCacheService.removeFromPendingPool(orderId);
                continue;
            }
            // 类型筛选不匹配则跳过
            if (requestVO.getCategoryId() != null && !requestVO.getCategoryId().equals(order.getCategoryId())) {
                continue;
            }
            // 纳入本页结果
            pending.add(order);
        }
        // 内存分页保持池内优先级顺序后返回
        return toPage(inMemoryPage(pending, requestVO.getPageNum(), requestVO.getPageSize()));
    }

    /**
     * 师傅「我的工单」分页列表：同时覆盖已接单（accepted_worker_id）与已分配（assigned_worker_id）
     * 两种归属关系，避免派单后师傅尚未接单时列表为空。
     *
     * @param workerId  师傅记录 ID（由调用方从安全上下文取得）
     * @param requestVO 分页、状态与类型筛选条件
     * @return 按创建时间倒序的工单分页
     */
    @Override
    public PageResult<OrderListResponseVO> pageForWorker(Long workerId, OrderQueryRequestVO requestVO) {
        // 覆盖已接单与已分配两种归属，避免派单后列表为空
        LambdaQueryWrapper<WorkOrder> wrapper = Wrappers.<WorkOrder>lambdaQuery()
                .and(w -> w.eq(WorkOrder::getAcceptedWorkerId, workerId)
                        .or()
                        .eq(WorkOrder::getAssignedWorkerId, workerId))
                .eq(StringUtils.hasText(requestVO.getStatus()), WorkOrder::getStatus, requestVO.getStatus())
                .eq(requestVO.getCategoryId() != null, WorkOrder::getCategoryId, requestVO.getCategoryId())
                .orderByDesc(WorkOrder::getCreateTime);
        // 转成分页列表返回
        return toPage(selectPage(requestVO, wrapper));
    }

    /**
     * 师傅工单详情：可见范围为「仍待接单」或「本人已接单/已分配」的工单。
     *
     * @param id 工单 ID
     * @return 工单详情
     * @throws BizException 工单不存在
     * @throws AccessDeniedException 工单不在当前师傅可见范围内
     */
    @Override
    public OrderDetailResponseVO getDetailForWorker(Long id) {
        // 取当前登录师傅记录 ID
        Long workerId = SecurityUtils.requireWorkerId();
        // 读取工单，不存在则抛异常
        WorkOrder order = requireOrder(id);
        // 可见范围：仍待接单，或本人已接单/被派单
        boolean visible = OrderStatusEnum.PENDING.getCode().equals(order.getStatus())
                || workerId.equals(order.getAcceptedWorkerId())
                || workerId.equals(order.getAssignedWorkerId());
        // 不在可见范围则拒绝访问
        if (!visible) {
            // 越权访问拦截
            throw new AccessDeniedException("该工单不在您名下");
        }
        // 组装并返回工单详情
        return toDetailVO(order);
    }

    /**
     * 师傅到场签到：ACCEPTED → GOING，到场时间由状态机写入。
     * 流转日志备注会附带上报的经纬度，便于后续追溯签到位置。
     *
     * @param id        工单 ID
     * @param requestVO 到场定位信息，经纬度可为空
     * @throws BizException 工单不存在或当前状态不允许到场
     * @throws AccessDeniedException 工单不在当前师傅名下
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void arrive(Long id, ArriveOrderRequestVO requestVO) {
        // 校验当前账号为可用师傅
        MaintenanceWorker worker = maintenanceWorkerService.requireCurrentWorker();
        // 校验工单归属当前师傅
        WorkOrder order = requireAcceptedOrder(id, worker.getId());
        // 走状态机执行到场流转，备注附带上报定位
        workOrderStateMachine.transition(order, OrderActionEnum.ARRIVE, SecurityUtils.currentUserId(),
                OperatorTypeEnum.WORKER, buildArriveRemark(requestVO));
        // 刷新缓存与师傅心跳
        afterStatusChanged(order);
    }

    /**
     * 师傅暂停工单：GOING → PAUSED，暂停备注写入流转日志。
     *
     * @param id        工单 ID
     * @param requestVO 暂停原因/备注
     * @throws BizException 工单不存在或当前状态不允许暂停
     * @throws AccessDeniedException 工单不在当前师傅名下
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pause(Long id, PauseOrderRequestVO requestVO) {
        // 校验当前账号为可用师傅
        MaintenanceWorker worker = maintenanceWorkerService.requireCurrentWorker();
        // 校验工单归属当前师傅
        WorkOrder order = requireAcceptedOrder(id, worker.getId());
        // 走状态机执行暂停流转，备注写入日志
        workOrderStateMachine.transition(order, OrderActionEnum.PAUSE, SecurityUtils.currentUserId(),
                OperatorTypeEnum.WORKER, requestVO.getRemark());
        // 刷新缓存与师傅心跳
        afterStatusChanged(order);
    }

    /**
     * 师傅恢复工单：PAUSED → GOING，流转日志备注为「配件到位，恢复处理」。
     *
     * @param id 工单 ID
     * @throws BizException 工单不存在或当前状态不允许恢复
     * @throws AccessDeniedException 工单不在当前师傅名下
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resume(Long id) {
        // 校验当前账号为可用师傅
        MaintenanceWorker worker = maintenanceWorkerService.requireCurrentWorker();
        // 校验工单归属当前师傅
        WorkOrder order = requireAcceptedOrder(id, worker.getId());
        // 走状态机恢复处理，备注固定为配件到位
        workOrderStateMachine.transition(order, OrderActionEnum.RESUME, SecurityUtils.currentUserId(),
                OperatorTypeEnum.WORKER, "配件到位，恢复处理");
        // 刷新缓存与师傅心跳
        afterStatusChanged(order);
    }

    /**
     * 师傅申请转单：GOING → TRANSFER_PENDING。转单原因同时写入工单字段与流转日志，
     * 之后由管理端重新派单或驳回（驳回回到 GOING）。
     *
     * @param id        工单 ID
     * @param requestVO 转单原因
     * @throws BizException 工单不存在或当前状态不允许转单
     * @throws AccessDeniedException 工单不在当前师傅名下
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transfer(Long id, TransferOrderRequestVO requestVO) {
        // 校验当前账号为可用师傅
        MaintenanceWorker worker = maintenanceWorkerService.requireCurrentWorker();
        // 校验工单归属当前师傅
        WorkOrder order = requireAcceptedOrder(id, worker.getId());
        // 写入转单原因，同时进工单字段与流转日志
        order.setTransferReason(requestVO.getTransferReason());
        // 走状态机提交转单申请，等待管理端处理
        workOrderStateMachine.transition(order, OrderActionEnum.TRANSFER_APPLY, SecurityUtils.currentUserId(),
                OperatorTypeEnum.WORKER, requestVO.getTransferReason());
        // 刷新缓存与师傅心跳
        afterStatusChanged(order);
    }

    /**
     * 师傅提交完工：GOING → SUBMITTED，保存处理说明与完工照片，
     * 流转备注中记录照片数量；随后等待业主确认或系统自动确认。
     *
     * @param id        工单 ID
     * @param requestVO 处理说明与完工照片（逗号分隔 URL）
     * @throws BizException 工单不存在或当前状态不允许提交完工
     * @throws AccessDeniedException 工单不在当前师傅名下
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id, SubmitOrderRequestVO requestVO) {
        // 校验当前账号为可用师傅
        MaintenanceWorker worker = maintenanceWorkerService.requireCurrentWorker();
        // 校验工单归属当前师傅
        WorkOrder order = requireAcceptedOrder(id, worker.getId());
        // 保存处理说明
        order.setProcessNote(requestVO.getProcessNote());
        // 保存完工照片（逗号分隔 URL）
        order.setFinishImages(requestVO.getFinishImages());
        // 走状态机提交完工，日志记录照片数量
        workOrderStateMachine.transition(order, OrderActionEnum.SUBMIT, SecurityUtils.currentUserId(),
                OperatorTypeEnum.WORKER, "提交完工：已上传 " + countImages(requestVO.getFinishImages()) + " 张完工照片");
        // 刷新缓存与师傅心跳
        afterStatusChanged(order);
    }

    // ------------------------------------------------------------------ 管理端

    /**
     * 管理端工单分页：支持状态、紧急度、类型、是否已 SLA 升级等条件筛选。
     * 关键字除工单号/标题外，还会匹配业主姓名或手机号（先查业主 ID 再用 IN 条件，最多 200 条）。
     *
     * @param requestVO 分页与筛选条件
     * @return 按创建时间倒序的工单分页
     */
    @Override
    public PageResult<OrderListResponseVO> pageForAdmin(AdminOrderQueryRequestVO requestVO) {
        // 按管理端条件组装查询
        LambdaQueryWrapper<WorkOrder> wrapper = Wrappers.<WorkOrder>lambdaQuery()
                .eq(StringUtils.hasText(requestVO.getStatus()), WorkOrder::getStatus, requestVO.getStatus())
                .eq(requestVO.getUrgency() != null, WorkOrder::getUrgency, requestVO.getUrgency())
                .eq(requestVO.getCategoryId() != null, WorkOrder::getCategoryId, requestVO.getCategoryId())
                .eq(requestVO.getEscalated() != null, WorkOrder::getSlaEscalated, requestVO.getEscalated())
                .orderByDesc(WorkOrder::getCreateTime);
        // 追加关键字检索（工单号/标题/业主姓名手机号）
        appendAdminKeyword(wrapper, requestVO.getKeyword());
        // 转分页列表返回
        return toPage(selectPage(requestVO, wrapper));
    }

    /**
     * 管理员工单详情：管理端可查看任意工单，不做归属限制。
     *
     * @param id 工单 ID
     * @return 工单详情（含评价、SLA 时间点与超时标记）
     * @throws BizException 工单不存在
     */
    @Override
    public OrderDetailResponseVO getDetailForAdmin(Long id) {
        // 管理端不做归属限制，校验存在后组装详情
        return toDetailVO(requireOrder(id));
    }

    /**
     * 管理员查看任意工单的流转记录。
     *
     * @param id 工单 ID
     * @return 按时间正序的流转日志（含操作人姓名与中文文案）
     * @throws BizException 工单不存在
     */
    @Override
    public List<WorkOrderLogResponseVO> listLogsForAdmin(Long id) {
        // 校验工单存在
        requireOrder(id);
        // 返回时间正序的流转日志
        return workOrderLogService.listByOrder(id);
    }

    /**
     * 管理员取消工单：不做归属限制，取消原因为空时由状态机兜底写入「未填写」。
     * 当前状态是否允许取消仍由状态机统一校验。
     *
     * <p>副作用：状态机更新状态、写日志、投递 MQ 事件；随后刷新状态缓存、移出待接单池并更新师傅心跳。</p>
     *
     * @param id        工单 ID
     * @param requestVO 取消原因
     * @throws BizException 工单不存在、状态不允许取消或并发更新冲突
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelByAdmin(Long id, CancelOrderRequestVO requestVO) {
        // 管理端不做归属限制，读取工单
        WorkOrder order = requireOrder(id);
        // 写入取消原因，为空时由状态机兜底
        order.setCancelReason(requestVO.getCancelReason());
        // 走状态机执行取消流转
        workOrderStateMachine.transition(order, OrderActionEnum.CANCEL, SecurityUtils.currentUserId(),
                OperatorTypeEnum.ADMIN, requestVO.getCancelReason());
        // 同步刷新状态缓存、待接单池与师傅心跳
        afterStatusChanged(order);
    }

    // ------------------------------------------------------------------ 内部方法

    /**
     * 状态变更后的缓存与心跳维护：缓存属于可自愈数据，失败不影响主流程。
     *
     * <p>工单仍为 PENDING 时重新加入待接单池（由池内 score 决定排序），否则从池中移除；
     * 随后刷新工单状态缓存。若已有接单师傅，再按数据库中的最新负载刷新其在线心跳，
     * 供派单推荐参考。</p>
     *
     * @param order 已完成状态流转、状态字段已更新的工单实体
     */
    private void afterStatusChanged(WorkOrder order) {
        // 仍为待接单则重新加入待接单池
        if (OrderStatusEnum.PENDING.getCode().equals(order.getStatus())) {
            // 重新加入待接单池，由池内 score 决定排序
            orderCacheService.addToPendingPool(order, null);
            // 其余状态一律移出待接单池
        } else {
            // 从待接单池移除
            orderCacheService.removeFromPendingPool(order.getId());
        }
        // 刷新工单状态缓存
        orderCacheService.cacheStatus(order);
        // 按数据库最新负载刷新接单师傅在线心跳
        workerPresenceService.touch(order.getAcceptedWorkerId(), workerLoadOf(order.getAcceptedWorkerId()));
    }

    /** 查询师傅当前负载；无接单师傅或档案不存在时返回 null，心跳侧会按 0 处理 */
    private Integer workerLoadOf(Long workerId) {
        // 无接单师傅直接返回空
        if (workerId == null) {
            // 返回空，心跳侧会按 0 处理
            return null;
        }
        // 查询师傅档案取当前负载
        MaintenanceWorker worker = maintenanceWorkerMapper.selectById(workerId);
        // 档案缺失时返回空
        return worker == null ? null : worker.getCurrentLoad();
    }

    private IPage<WorkOrder> selectPage(OrderQueryRequestVO requestVO, LambdaQueryWrapper<WorkOrder> wrapper) {
        // 按条件分页查询工单
        return workOrderMapper.selectPage(new Page<>(requestVO.getPageNum(), requestVO.getPageSize()), wrapper);
    }

    /**
     * 待接单池是内存顺序，用内存分页保持池内优先级顺序。
     * 手动计算 from/to 并向集合长度收敛，避免页码越界时 subList 抛异常。
     */
    private Page<WorkOrder> inMemoryPage(List<WorkOrder> all, int pageNum, int pageSize) {
        // 构造内存分页对象
        Page<WorkOrder> page = new Page<>(pageNum, pageSize);
        // 总数为集合大小
        page.setTotal(all.size());
        // 起始下标收敛到集合长度，避免越界
        int from = (int) Math.min((long) (pageNum - 1) * pageSize, all.size());
        // 结束下标同样收敛
        int to = (int) Math.min((long) from + pageSize, all.size());
        // 截取当前页数据
        page.setRecords(all.subList(from, to));
        // 返回内存分页结果
        return page;
    }

    /** 将 MyBatis-Plus 分页结果转换为列表 VO 分页，保留当前页、页大小与总数 */
    private PageResult<OrderListResponseVO> toPage(IPage<WorkOrder> page) {
        // 复用原页码、页大小与总数构造列表 VO 分页
        Page<OrderListResponseVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        // 逐条转换为列表 VO
        voPage.setRecords(toListVO(page.getRecords()));
        // 包装为统一分页结果
        return PageResult.of(voPage);
    }

    /**
     * 管理端关键字检索：工单号/标题直接模糊匹配；业主姓名/手机号先查出业主 ID 集合，
     * 再用 IN 匹配工单。业主查询最多取 {@value #KEYWORD_OWNER_LIMIT} 条，避免 IN 列表过长。
     * 三类条件为 OR 关系，统一包在一层 and(...) 中，防止与其它 AND 条件发生优先级歧义。
     */
    private void appendAdminKeyword(LambdaQueryWrapper<WorkOrder> wrapper, String keyword) {
        // 关键字为空直接返回
        if (!StringUtils.hasText(keyword)) {
            return;
        }
        // 先按姓名/手机号查出候选业主 ID（限 200 条）
        List<Long> ownerIds = userMapper.selectList(Wrappers.<User>lambdaQuery()
                        .like(User::getRealName, keyword)
                        .or()
                        .like(User::getPhone, keyword)
                        .last("limit " + KEYWORD_OWNER_LIMIT)).stream()
                .map(User::getId)
                .toList();
        // 三层 OR 条件包一层 and，避免与其它 AND 条件优先级歧义
        wrapper.and(w -> {
            // 工单号/标题模糊匹配
            w.like(WorkOrder::getOrderNo, keyword).or().like(WorkOrder::getTitle, keyword);
            // 命中业主时补充 IN 条件
            if (!ownerIds.isEmpty()) {
                // 追加业主 ID 过滤
                w.or().in(WorkOrder::getOwnerId, ownerIds);
            }
        });
    }

    /** 按 ID 读取工单，不存在时抛 ORDER_NOT_FOUND */
    private WorkOrder requireOrder(Long id) {
        // 按主键读取工单
        WorkOrder order = workOrderMapper.selectById(id);
        // 不存在则抛业务异常
        if (order == null) {
            // 工单不存在错误码
            throw BizException.of(ResultCode.ORDER_NOT_FOUND);
        }
        // 返回存在的工单
        return order;
    }

    /**
     * 读取工单并校验归属当前登录业主，用于业主端越权拦截。
     *
     * @throws BizException 工单不存在
     * @throws AccessDeniedException 工单存在但不属于当前业主
     */
    private WorkOrder requireOwnedOrder(Long id) {
        // 读取工单（含存在性校验）
        WorkOrder order = requireOrder(id);
        // 非当前业主则拒绝
        if (!order.getOwnerId().equals(SecurityUtils.currentUserId())) {
            // 抛出越权访问拦截
            throw new AccessDeniedException("该工单不属于当前业主");
        }
        // 校验通过返回工单
        return order;
    }

    /**
     * 读取工单并校验当前师傅是否为接单师傅，作为师傅现场操作前的越权拦截。
     *
     * @throws BizException 工单不存在
     * @throws AccessDeniedException 工单存在但接单师傅不是当前师傅
     */
    private WorkOrder requireAcceptedOrder(Long id, Long workerId) {
        // 读取工单（含存在性校验）
        WorkOrder order = requireOrder(id);
        // 接单师傅不是本人则拒绝
        if (!workerId.equals(order.getAcceptedWorkerId())) {
            // 抛出越权访问拦截
            throw new AccessDeniedException("该工单不在您名下");
        }
        // 校验通过返回工单
        return order;
    }

    /** 组装到场日志备注：有经纬度时附带上报定位，否则使用通用文案 */
    private String buildArriveRemark(ArriveOrderRequestVO requestVO) {
        // 无定位信息时用通用文案
        if (requestVO == null || requestVO.getLongitude() == null || requestVO.getLatitude() == null) {
            // 返回通用签到备注
            return "师傅到场签到";
        }
        // 有定位则附带经纬度，便于追溯签到位置
        return "师傅到场签到，定位：" + requestVO.getLongitude() + "," + requestVO.getLatitude();
    }

    private long countImages(String images) {
        // 逗号分隔的图片按数量计数
        return splitUrls(images).size();
    }

    /** 批量按 ID 查询工单并转为 Map，避免在循环中逐条查询造成 N+1 */
    private Map<Long, WorkOrder> byId(Collection<Long> ids) {
        // 批量查询工单，避免循环内逐条查询
        List<WorkOrder> orders = workOrderMapper.selectBatchIds(ids);
        // 预留容量的结果 Map
        Map<Long, WorkOrder> map = new HashMap<>(orders.size());
        // 逐条放入 Map
        for (WorkOrder order : orders) {
            // 以主键为键存入
            map.put(order.getId(), order);
        }
        // 返回 ID 到工单的映射
        return map;
    }

    // ------------------------------------------------------------------ 出参组装

    /**
     * 列表出参组装：先按当前页需要的 ID 集合批量加载分类、房产地址、业主姓名与师傅信息，
     * 再逐条映射 VO，避免逐行查询；SLA 超时字段按当前时间实时计算。
     */
    private List<OrderListResponseVO> toListVO(List<WorkOrder> orders) {
        // 空列表直接返回空集合
        if (orders == null || orders.isEmpty()) {
            // 返回空列表
            return Collections.emptyList();
        }
        // 批量加载本页分类名
        Map<Long, String> categoryNames = loadCategoryNames(orders);
        // 批量加载本页房产完整地址
        Map<Long, String> addresses = loadPropertyAddresses(orders);
        // 批量加载业主姓名
        Map<Long, String> ownerNames = loadUserNames(orders.stream().map(WorkOrder::getOwnerId).toList());
        // 批量加载处理师傅摘要
        Map<Long, WorkerBrief> workers = loadWorkers(workerIdsOf(orders));
        // 统一当前时间，用于超时判定
        LocalDateTime now = LocalDateTime.now();
        // 逐条映射为列表 VO
        return orders.stream().map(order -> {
            // 取当前处理师傅 ID
            Long workerId = currentWorkerIdOf(order);
            // 取师傅摘要，无则置空
            WorkerBrief worker = workerId == null ? null : workers.get(workerId);
            // 装配列表出参
            return OrderListResponseVO.builder()
                    .id(order.getId())
                    .orderNo(order.getOrderNo())
                    .title(order.getTitle())
                    .categoryId(order.getCategoryId())
                    .categoryName(categoryNames.get(order.getCategoryId()))
                    .urgency(order.getUrgency())
                    .urgencyText(UrgencyEnum.nameOf(order.getUrgency()))
                    .status(order.getStatus())
                    .statusText(OrderStatusEnum.nameOf(order.getStatus()))
                    .source(order.getSource())
                    .affectScope(order.getAffectScope())
                    .safetyFlag(order.getSafetyFlag())
                    .fullAddress(addresses.get(order.getPropertyId()))
                    .ownerId(order.getOwnerId())
                    .ownerName(ownerNames.get(order.getOwnerId()))
                    .contactPhone(order.getContactPhone())
                    .workerId(workerId)
                    .workerName(worker == null ? null : worker.realName())
                    .slaResponseDeadline(order.getSlaResponseDeadline())
                    .slaHandleDeadline(order.getSlaHandleDeadline())
                    .slaEscalated(order.getSlaEscalated())
                    .slaOverdue(isOverdue(order, now))
                    .createTime(order.getCreateTime())
                    .acceptedTime(order.getAcceptedTime())
                    .submittedTime(order.getSubmittedTime())
                    .build();
        }).toList();
    }

    /**
     * 详情出参组装：单条记录按需查询房产、分类、业主与评价，并映射当前处理师傅摘要、
     * 各阶段时间点与 SLA 超时标记。
     */
    private OrderDetailResponseVO toDetailVO(WorkOrder order) {
        // 待填充的处理师傅摘要
        WorkerBrief worker = null;
        // 取当前处理师傅 ID
        Long workerId = currentWorkerIdOf(order);
        // 有师傅则加载摘要
        if (workerId != null) {
            // 复用批量接口取单条师傅摘要
            worker = loadWorkers(List.of(workerId)).get(workerId);
        }
        // 查询房产信息
        Property property = propertyMapper.selectById(order.getPropertyId());
        // 查询报修分类
        RepairCategory category = repairCategoryMapper.selectById(order.getCategoryId());
        // 查询业主信息
        User owner = userMapper.selectById(order.getOwnerId());
        // 装配详情出参
        return OrderDetailResponseVO.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .title(order.getTitle())
                .categoryId(order.getCategoryId())
                .categoryName(category == null ? null : category.getName())
                .description(order.getDescription())
                .faultDuration(order.getFaultDuration())
                .urgency(order.getUrgency())
                .urgencyText(UrgencyEnum.nameOf(order.getUrgency()))
                .status(order.getStatus())
                .statusText(OrderStatusEnum.nameOf(order.getStatus()))
                .source(order.getSource())
                .affectScope(order.getAffectScope())
                .affectScopeText(affectScopeText(order.getAffectScope()))
                .safetyFlag(order.getSafetyFlag())
                .deviceInfo(order.getDeviceInfo())
                .expectedVisitTime(order.getExpectedVisitTime())
                .contactPhone(order.getContactPhone())
                .propertyId(order.getPropertyId())
                .fullAddress(property == null ? null : property.getFullAddress())
                .ownerId(order.getOwnerId())
                .ownerName(owner == null ? null : owner.getRealName())
                .images(splitUrls(order.getImages()))
                .finishImages(splitUrls(order.getFinishImages()))
                .processNote(order.getProcessNote())
                .worker(worker == null ? null : OrderWorkerResponseVO.builder()
                        .workerId(worker.workerId())
                        .realName(worker.realName())
                        .phone(worker.phone())
                        .avgScore(worker.avgScore())
                        .skillTags(worker.skillTags())
                        .build())
                .cancelReason(order.getCancelReason())
                .transferReason(order.getTransferReason())
                .slaResponseDeadline(order.getSlaResponseDeadline())
                .slaHandleDeadline(order.getSlaHandleDeadline())
                .slaEscalated(order.getSlaEscalated())
                .slaResponseBreached(order.getSlaResponseBreached())
                .slaHandleBreached(order.getSlaHandleBreached())
                .assignedTime(order.getAssignedTime())
                .acceptedTime(order.getAcceptedTime())
                .arrivedTime(order.getArrivedTime())
                .submittedTime(order.getSubmittedTime())
                .completedTime(order.getCompletedTime())
                .closedTime(order.getClosedTime())
                .createTime(order.getCreateTime())
                .evaluation(loadEvaluation(order.getId()))
                .build();
    }

    /** 查询工单评价并转为出参；未评价时返回 null */
    private OrderEvaluationResponseVO loadEvaluation(Long orderId) {
        // 按工单 ID 查询评价
        Evaluation evaluation = evaluationMapper.selectOne(Wrappers.<Evaluation>lambdaQuery()
                .eq(Evaluation::getOrderId, orderId));
        // 未评价返回空
        if (evaluation == null) {
            // 无评价信息
            return null;
        }
        // 装配评价出参
        return OrderEvaluationResponseVO.builder()
                .score(evaluation.getScore())
                .speedScore(evaluation.getSpeedScore())
                .attitudeScore(evaluation.getAttitudeScore())
                .qualityScore(evaluation.getQualityScore())
                .content(evaluation.getContent())
                .images(splitUrls(evaluation.getImages()))
                .createTime(evaluation.getCreateTime())
                .build();
    }

    /**
     * 列表超时判定：接单前（PENDING / ASSIGNED）看响应时限，接单后看处理时限，
     * 终态或缺少截止时间时不再提示。该字段仅用于展示，实时计算、不落库。
     */
    private boolean isOverdue(WorkOrder order, LocalDateTime now) {
        // 解析工单状态枚举
        OrderStatusEnum status = OrderStatusEnum.of(order.getStatus());
        // 终态或无状态不提示超时
        if (status == null || status.isFinal()) {
            // 不判定为超时
            return false;
        }
        // 按阶段选择响应或处理时限
        LocalDateTime deadline = switch (status) {
            // 接单前看响应时限
            case PENDING, ASSIGNED -> order.getSlaResponseDeadline();
            // 接单后看处理时限
            default -> order.getSlaHandleDeadline();
        };
        // 有截止时间且已过期即为超时
        return deadline != null && now.isAfter(deadline);
    }

    /** 收集列表中所有当前处理师傅的 ID（去重、保持顺序），供一次批量加载师傅信息 */
    private Set<Long> workerIdsOf(List<WorkOrder> orders) {
        // 去重且保持顺序的 ID 集合
        Set<Long> ids = new LinkedHashSet<>();
        // 遍历收集处理师傅 ID
        for (WorkOrder order : orders) {
            // 取当前处理师傅
            Long workerId = currentWorkerIdOf(order);
            // 有则加入集合
            if (workerId != null) {
                // 收集师傅 ID 供一次批量加载
                ids.add(workerId);
            }
        }
        // 返回师傅 ID 集合
        return ids;
    }

    /** 当前处理师傅：优先取实际接单师傅；仅派单未接单时回退为被分配师傅 */
    private Long currentWorkerIdOf(WorkOrder order) {
        // 优先实际接单师傅，仅派单未接单时回退被派单师傅
        return order.getAcceptedWorkerId() == null ? order.getAssignedWorkerId() : order.getAcceptedWorkerId();
    }

    /** 批量加载本页工单的报修分类名称；无有效分类 ID 时返回空 Map */
    private Map<Long, String> loadCategoryNames(List<WorkOrder> orders) {
        // 收集本页非空分类 ID
        Set<Long> ids = orders.stream().map(WorkOrder::getCategoryId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        // 无分类直接返回空 Map
        if (ids.isEmpty()) {
            // 返回空 Map
            return Collections.emptyMap();
        }
        // 分类 ID 到名称的映射
        Map<Long, String> names = new HashMap<>(ids.size());
        // 逐条写入映射
        for (RepairCategory category : repairCategoryMapper.selectBatchIds(ids)) {
            // 记录分类 ID 与名称
            names.put(category.getId(), category.getName());
        }
        // 返回名称映射
        return names;
    }

    /** 批量加载本页工单的房产完整地址；无有效房产 ID 时返回空 Map */
    private Map<Long, String> loadPropertyAddresses(List<WorkOrder> orders) {
        // 收集本页非空房产 ID
        Set<Long> ids = orders.stream().map(WorkOrder::getPropertyId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        // 无房产直接返回空 Map
        if (ids.isEmpty()) {
            // 返回空 Map
            return Collections.emptyMap();
        }
        // 房产 ID 到完整地址的映射
        Map<Long, String> addresses = new HashMap<>(ids.size());
        // 逐条写入映射
        for (Property property : propertyMapper.selectBatchIds(ids)) {
            // 记录房产 ID 与地址
            addresses.put(property.getId(), property.getFullAddress());
        }
        // 返回地址映射
        return addresses;
    }

    /** 批量加载用户姓名（业主/操作人展示用）；空集合直接返回空 Map */
    private Map<Long, String> loadUserNames(Collection<Long> userIds) {
        // 过滤空 ID 并去重
        Set<Long> ids = userIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        // 空集合直接返回空 Map
        if (ids.isEmpty()) {
            // 返回空 Map
            return Collections.emptyMap();
        }
        // 用户 ID 到姓名的映射
        Map<Long, String> names = new HashMap<>(ids.size());
        // 逐条写入映射
        for (User user : userMapper.selectBatchIds(ids)) {
            // 记录用户 ID 与姓名
            names.put(user.getId(), user.getRealName());
        }
        // 返回姓名映射
        return names;
    }

    /**
     * 批量加载师傅摘要：先查 maintenance_worker，再批量查关联 user 补姓名与手机号。
     * 返回以师傅记录 ID 为键的 Map，避免列表渲染时逐条查询。
     */
    private Map<Long, WorkerBrief> loadWorkers(Collection<Long> workerIds) {
        // 空入参直接返回空 Map
        if (workerIds == null || workerIds.isEmpty()) {
            // 返回空 Map
            return Collections.emptyMap();
        }
        // 批量查询师傅档案
        List<MaintenanceWorker> workers = maintenanceWorkerMapper.selectBatchIds(workerIds);
        // 无档案直接返回空 Map
        if (workers.isEmpty()) {
            // 返回空 Map
            return Collections.emptyMap();
        }
        // 用户 ID 到用户对象的映射
        Map<Long, User> users = new HashMap<>(workers.size());
        // 批量查询关联用户补姓名与手机号
        for (User user : userMapper.selectBatchIds(workers.stream().map(MaintenanceWorker::getUserId).toList())) {
            // 记录用户 ID 与对象
            users.put(user.getId(), user);
        }
        // 师傅记录 ID 到摘要的映射
        Map<Long, WorkerBrief> result = new HashMap<>(workers.size());
        // 逐条组装师傅摘要
        for (MaintenanceWorker worker : workers) {
            // 取关联用户补姓名手机号
            User user = users.get(worker.getUserId());
            // 写入师傅摘要
            result.put(worker.getId(), new WorkerBrief(worker.getId(), worker.getSkillTags(),
                    user == null ? null : user.getRealName(), user == null ? null : user.getPhone(),
                    worker.getAvgScore()));
        }
        // 返回师傅摘要映射
        return result;
    }

    private String affectScopeText(Integer affectScope) {
        // 解析影响范围枚举
        AffectScopeEnum scope = AffectScopeEnum.of(affectScope);
        // 无效值返回 null，否则返回中文名
        return scope == null ? null : scope.getName();
    }

    /** 逗号分隔的图片地址转列表 */
    private List<String> splitUrls(String urls) {
        // 空串直接返回空列表
        if (!StringUtils.hasText(urls)) {
            // 返回空列表
            return Collections.emptyList();
        }
        // 结果列表
        List<String> result = new ArrayList<>();
        // 按逗号拆分逐个处理
        for (String url : urls.split(",")) {
            // 跳过空白片段
            if (StringUtils.hasText(url)) {
                // 去空格后加入结果
                result.add(url.trim());
            }
        }
        // 返回图片地址列表
        return result;
    }

    /** 工人摘要：避免为出参组装额外定义实体转换类 */
    private record WorkerBrief(Long workerId, String skillTags, String realName, String phone,
                               BigDecimal avgScore) {
    }
}