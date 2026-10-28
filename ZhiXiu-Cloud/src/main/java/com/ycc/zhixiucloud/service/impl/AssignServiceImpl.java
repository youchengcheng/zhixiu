package com.ycc.zhixiucloud.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ycc.zhixiucloud.common.constant.RedisKeys;
import com.ycc.zhixiucloud.common.enums.OperatorTypeEnum;
import com.ycc.zhixiucloud.common.enums.OrderActionEnum;
import com.ycc.zhixiucloud.common.enums.OrderStatusEnum;
import com.ycc.zhixiucloud.common.exception.BizException;
import com.ycc.zhixiucloud.common.result.ResultCode;
import com.ycc.zhixiucloud.common.util.SecurityUtils;
import com.ycc.zhixiucloud.entity.MaintenanceWorker;
import com.ycc.zhixiucloud.entity.RepairCategory;
import com.ycc.zhixiucloud.entity.User;
import com.ycc.zhixiucloud.entity.WorkOrder;
import com.ycc.zhixiucloud.mapper.MaintenanceWorkerMapper;
import com.ycc.zhixiucloud.mapper.RepairCategoryMapper;
import com.ycc.zhixiucloud.mapper.UserMapper;
import com.ycc.zhixiucloud.mapper.WorkOrderMapper;
import com.ycc.zhixiucloud.request.AssignOrderRequestVO;
import com.ycc.zhixiucloud.request.ReassignOrderRequestVO;
import com.ycc.zhixiucloud.response.RecommendWorkerResponseVO;
import com.ycc.zhixiucloud.service.AssignService;
import com.ycc.zhixiucloud.service.MaintenanceWorkerService;
import com.ycc.zhixiucloud.service.OrderCacheService;
import com.ycc.zhixiucloud.service.WorkerPresenceService;
import com.ycc.zhixiucloud.statemachine.WorkOrderStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 派单服务实现。
 *
 * <p>并发控制：抢单与派单都先取 Redisson 锁（key 按工单维度），锁在事务之外获取并在 finally 中释放，
 * 保证「读状态 → 改状态」整段串行，且不会出现锁先于事务提交释放的窗口；
 * 状态更新本身仍由 state machine 用乐观锁兜底。</p>
 *
 * <p>职责：师傅抢单/接单、管理员派单/改派、派单推荐，并在状态变更后同步待接单池缓存、
 * 工单状态缓存、月度接单榜与在线负载。抢单使用 GRAB_LOCK，派单与改派共用 ASSIGN_LOCK；
 * 接单（accept）只允许被派单师傅本人操作，依靠状态机乐观锁兜底，未额外加分布式锁。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssignServiceImpl implements AssignService {

    /** 审核状态：通过 */
    private static final int AUDIT_PASSED = 1;

    /** 师傅状态：禁用 */
    private static final int WORK_STATUS_DISABLED = 4;

    /** 月度榜单保留天数 */
    private static final int RANK_TTL_DAYS = 60;

    /** 推荐条数 */
    private static final int TOP_RECOMMEND = 3;

    private final WorkOrderMapper workOrderMapper;

    private final MaintenanceWorkerMapper maintenanceWorkerMapper;

    private final UserMapper userMapper;

    private final RepairCategoryMapper repairCategoryMapper;

    private final WorkOrderStateMachine workOrderStateMachine;

    private final OrderCacheService orderCacheService;

    private final WorkerPresenceService workerPresenceService;

    private final MaintenanceWorkerService maintenanceWorkerService;

    private final RedissonClient redissonClient;

    private final StringRedisTemplate stringRedisTemplate;

    /** 抢锁等待秒数，0 表示立即返回 */
    @Value("${redisson.lock-wait-time:0}")
    private long lockWaitSeconds;

    /** 锁租约秒数 */
    @Value("${redisson.lock-lease-time:10}")
    private long lockLeaseSeconds;

    /**
     * 师傅抢单。
     *
     * <p>允许「待接单」直接抢单，也允许已被派给当前师傅的「已派单」工单确认抢单；
     * 若已被其他师傅接单返回 ORDER_GRABBED，其他状态返回 ORDER_STATUS_INVALID。
     * 抢单成功后触发状态机流转，并刷新待接单池、状态缓存、月度接单榜和在线负载。</p>
     *
     * @param orderId 工单 ID
     * @throws BizException 当前账号不是可用师傅、工单不存在、已被抢或状态不允许时抛出
     */
    @Override
    public void grab(Long orderId) {
        // 校验当前账号为可用师傅
        MaintenanceWorker worker = maintenanceWorkerService.requireCurrentWorker();
        // 按工单维度取抢单锁
        RLock lock = redissonClient.getLock(RedisKeys.of(RedisKeys.GRAB_LOCK, orderId));
        // 立即拿锁，拿不到即返回冲突
        lockOrThrow(lock, "抢单请求处理中，请稍后重试");
        // 加锁后串行执行「读状态 → 改状态」
        try {
            // 读取工单
            WorkOrder order = requireOrder(orderId);
            // 解析当前状态枚举
            OrderStatusEnum status = OrderStatusEnum.of(order.getStatus());
            // 已被他人接单返回 2003；其余状态（处理中、已完工、已取消等）本就不可抢，返回 2002
            if (status == OrderStatusEnum.ACCEPTED) {
                // 已被他人接单
                throw BizException.of(ResultCode.ORDER_GRABBED);
            }
            // 其余状态不可抢单
            if (status != OrderStatusEnum.PENDING && status != OrderStatusEnum.ASSIGNED) {
                // 抛出状态非法业务码
                throw BizException.of(ResultCode.ORDER_STATUS_INVALID, "当前状态「" + status.getName() + "」不可抢单");
            }
            // 已派给其他师傅则拒绝
            if (status == OrderStatusEnum.ASSIGNED && order.getAssignedWorkerId() != null
                    && !order.getAssignedWorkerId().equals(worker.getId())) {
                // 抛出已被抢业务码
                throw BizException.of(ResultCode.ORDER_GRABBED);
            }
            // 记录接单师傅
            order.setAcceptedWorkerId(worker.getId());
            // 走状态机执行抢单流转
            workOrderStateMachine.transition(order, OrderActionEnum.GRAB, SecurityUtils.currentUserId(),
                    OperatorTypeEnum.WORKER, "师傅抢单");
            // 收尾：清理缓存、加榜、刷心跳
            afterAccepted(order, worker.getId());
        // 释放抢单锁
        } finally {
            // 释放锁
            unlock(lock);
        }
    }

    /**
     * 师傅接受管理员派单。
     *
     * <p>只有被派单的师傅本人可接单，接单后工单由「已派单」流转为「已接单」；
     * 并发冲突由状态机的版本号乐观锁兜底。</p>
     *
     * @param orderId 工单 ID
     * @throws BizException 工单未派给当前师傅或状态不允许时抛出
     */
    @Override
    public void accept(Long orderId) {
        // 校验当前账号为可用师傅
        MaintenanceWorker worker = maintenanceWorkerService.requireCurrentWorker();
        // 读取工单
        WorkOrder order = requireOrder(orderId);
        // 仅被派单师傅本人可接单
        if (!OrderStatusEnum.ASSIGNED.getCode().equals(order.getStatus())
                || !worker.getId().equals(order.getAssignedWorkerId())) {
            // 抛出状态非法业务码
            throw BizException.of(ResultCode.ORDER_STATUS_INVALID, "该工单未派给您，无法接单");
        }
        // 记录接单师傅
        order.setAcceptedWorkerId(worker.getId());
        // 走状态机执行接单流转
        workOrderStateMachine.transition(order, OrderActionEnum.ACCEPT, SecurityUtils.currentUserId(),
                OperatorTypeEnum.WORKER, "师傅接受派单");
        // 收尾：清理缓存、加榜、刷心跳
        afterAccepted(order, worker.getId());
    }

    /**
     * 管理员派单。
     *
     * <p>先校验师傅审核通过且未禁用，再按工单加 ASSIGN_LOCK，确保同一工单同时只有一个
     * 派单/改派操作；仅「待接单」工单可派单。派单成功后更新待接单池与状态缓存。</p>
     *
     * @param orderId   工单 ID
     * @param requestVO 目标师傅与备注，备注为空时自动生成
     * @throws BizException 师傅不存在/未审核/已禁用、工单不存在或状态不允许时抛出
     */
    @Override
    public void assign(Long orderId, AssignOrderRequestVO requestVO) {
        // 校验目标师傅可派单
        MaintenanceWorker worker = requireAssignableWorker(requestVO.getWorkerId());
        // 按工单维度取派单锁
        RLock lock = redissonClient.getLock(RedisKeys.of(RedisKeys.ASSIGN_LOCK, orderId));
        // 立即拿锁，拿不到即返回冲突
        lockOrThrow(lock, "该工单正在派单中，请稍后重试");
        // 加锁后串行执行派单
        try {
            // 读取工单
            WorkOrder order = requireOrder(orderId);
            // 仅待接单工单可派单
            if (!OrderStatusEnum.PENDING.getCode().equals(order.getStatus())) {
                // 抛出状态非法业务码
                throw BizException.of(ResultCode.ORDER_STATUS_INVALID, "工单已不在待接单状态，无法派单");
            }
            // 记录被派单师傅
            order.setAssignedWorkerId(worker.getId());
            // 备注为空时用派单文案兜底
            String remark = StringUtils.hasText(requestVO.getRemark())
                    ? requestVO.getRemark() : "派单给 " + worker.getWorkerNo();
            // 走状态机执行派单流转
            workOrderStateMachine.transition(order, OrderActionEnum.ASSIGN, SecurityUtils.currentUserId(),
                    OperatorTypeEnum.ADMIN, remark);
            // 收尾：清理缓存与状态缓存
            afterAssigned(order);
        // 释放派单锁
        } finally {
            // 释放锁
            unlock(lock);
        }
    }

    /**
     * 管理员改派。
     *
     * <p>仅「已派单、已接单、改派待确认」三种状态允许改派；与派单共用 ASSIGN_LOCK，
     * 避免两个操作交错。改派后状态机内部会重算原师傅负载，这里再同步刷新在线心跳中的负载。</p>
     *
     * @param orderId   工单 ID
     * @param requestVO 新目标师傅与改派备注
     * @throws BizException 师傅不可派、工单不存在或当前状态不允许改派时抛出
     */
    @Override
    public void reassign(Long orderId, ReassignOrderRequestVO requestVO) {
        // 校验新目标师傅可派单
        MaintenanceWorker worker = requireAssignableWorker(requestVO.getWorkerId());
        // 与派单共用同一工单锁
        RLock lock = redissonClient.getLock(RedisKeys.of(RedisKeys.ASSIGN_LOCK, orderId));
        // 立即拿锁，拿不到即返回冲突
        lockOrThrow(lock, "该工单正在派单中，请稍后重试");
        // 加锁后串行执行改派
        try {
            // 读取工单
            WorkOrder order = requireOrder(orderId);
            // 解析当前状态枚举
            OrderStatusEnum status = OrderStatusEnum.of(order.getStatus());
            // 仅已派单/已接单/改派待确认可改派
            if (status != OrderStatusEnum.ASSIGNED && status != OrderStatusEnum.ACCEPTED
                    && status != OrderStatusEnum.TRANSFER_PENDING) {
                // 抛出状态非法业务码
                throw BizException.of(ResultCode.ORDER_STATUS_INVALID, "当前状态不允许改派");
            }
            // 记录新目标师傅
            order.setAssignedWorkerId(worker.getId());
            // 走状态机执行改派流转
            workOrderStateMachine.transition(order, OrderActionEnum.ASSIGN, SecurityUtils.currentUserId(),
                    OperatorTypeEnum.ADMIN, requestVO.getRemark());
            // 收尾：清理缓存与状态缓存
            afterAssigned(order);
            // 原接单师傅的负载已在状态机内重算，这里同步清理在线心跳中的负载数字
            workerPresenceService.touch(worker.getId(), worker.getCurrentLoad());
        // 释放改派锁
        } finally {
            // 释放锁
            unlock(lock);
        }
    }

    /**
     * 推荐派单师傅。
     *
     * <p>候选集为审核通过且未禁用的师傅，综合技能匹配（50%）、当前负载（30%，按 1/(1+load)
     * 归一）、平均评分（20%，按满分 5 分归一）计算匹配分，取前 3 名并给出推荐理由。
     * 当前为内存计算，适合师傅规模较小的场景。</p>
     *
     * @param orderId 工单 ID
     * @return 按匹配分倒序的推荐列表；无可用师傅时返回空列表
     * @throws BizException 工单不存在时抛出
     */
    @Override
    public List<RecommendWorkerResponseVO> recommendWorkers(Long orderId) {
        // 读取工单
        WorkOrder order = requireOrder(orderId);
        // 查询工单分类
        RepairCategory category = repairCategoryMapper.selectById(order.getCategoryId());
        // 取二级分类名，缺失为空
        String categoryName = category == null ? null : category.getName();
        // 取一级分类名
        String parentName = parentCategoryName(category);
        // 取审核通过且未禁用的候选师傅
        List<MaintenanceWorker> workers = maintenanceWorkerMapper.selectList(
                Wrappers.<MaintenanceWorker>lambdaQuery()
                        .eq(MaintenanceWorker::getAuditStatus, AUDIT_PASSED)
                        .ne(MaintenanceWorker::getWorkStatus, WORK_STATUS_DISABLED));
        // 无候选师傅直接返回空
        if (workers.isEmpty()) {
            // 记录无候选日志
            log.info("暂无审核通过的师傅，派单推荐为空：orderId={}", orderId);
            // 返回空列表
            return Collections.emptyList();
        }
        // 批量加载关联用户，避免 N+1
        Map<Long, User> users = loadUsers(workers.stream().map(MaintenanceWorker::getUserId).toList());
        // 结果列表
        List<RecommendWorkerResponseVO> result = new ArrayList<>(workers.size());
        // 逐个构建推荐项
        for (MaintenanceWorker worker : workers) {
            // 计算匹配分并加入结果
            result.add(buildRecommendation(worker, users.get(worker.getUserId()), categoryName, parentName));
        }
        // 按匹配分倒序排序
        result.sort(Comparator.comparing(RecommendWorkerResponseVO::getMatchScore).reversed());
        // 截取前 N 名返回
        return result.size() > TOP_RECOMMEND ? result.subList(0, TOP_RECOMMEND) : result;
    }

    // ------------------------------------------------------------------ 内部方法

    /**
     * 师傅接单/抢单成功后的统一收尾。
     *
     * <p>移除待接单池缓存、回写最新状态、累加月度接单榜，并刷新在线心跳中的负载，
     * 保证列表页、榜单与在线状态及时反映变化。</p>
     *
     * @param order    已流转的工单（状态字段已更新）
     * @param workerId 接单师傅 ID
     */
    private void afterAccepted(WorkOrder order, Long workerId) {
        // 移出待接单池
        orderCacheService.removeFromPendingPool(order.getId());
        // 回写最新状态缓存
        orderCacheService.cacheStatus(order);
        // 月度接单榜加分
        increaseMonthlyOrderRank(workerId);
        // 查询师傅最新负载
        MaintenanceWorker worker = maintenanceWorkerMapper.selectById(workerId);
        // 刷新在线心跳中的负载
        workerPresenceService.touch(workerId, worker == null ? null : worker.getCurrentLoad());
        // 记录接单结果
        log.info("师傅接单成功：orderId={}, workerId={}, status={}", order.getId(), workerId, order.getStatus());
    }

    /**
     * 派单/改派成功后的缓存收尾。
     *
     * @param order 已流转的工单
     */
    private void afterAssigned(WorkOrder order) {
        // 移出待接单池
        orderCacheService.removeFromPendingPool(order.getId());
        // 回写最新状态缓存
        orderCacheService.cacheStatus(order);
    }

    /**
     * 月度接单榜加分。
     *
     * <p>使用 Redis ZSet 按自然月累计：key 带 yyyyMM 后缀，score 为接单数；
     * 每次写入重置过期时间，过期后旧榜单自动清理。</p>
     *
     * @param workerId 师傅 ID
     */
    private void increaseMonthlyOrderRank(Long workerId) {
        // 按自然月取榜单 key
        String key = RedisKeys.of(RedisKeys.WORKER_ORDER_RANK, RedisKeys.monthSuffix(LocalDate.now()));
        // 师傅维度接单数自增 1
        stringRedisTemplate.opsForZSet().incrementScore(key, String.valueOf(workerId), 1D);
        // 重置榜单过期时间
        stringRedisTemplate.expire(key, RANK_TTL_DAYS, TimeUnit.DAYS);
    }

    /**
     * 构建单个师傅的推荐结果。
     *
     * <p>匹配分 = 0.5 × 技能系数（命中 1，未命中 0.4）+ 0.3 × 1/(1+负载)
     * + 0.2 × 平均分/5。未命中技能不直接淘汰，而是降为备选，避免技能标签不全导致无人可派。</p>
     *
     * @param worker       师傅实体
     * @param user         关联用户，可能为 null
     * @param categoryName 工单二级分类名
     * @param parentName   工单一级分类名
     * @return 推荐项，匹配分保留两位小数
     */
    private RecommendWorkerResponseVO buildRecommendation(MaintenanceWorker worker, User user,
                                                         String categoryName, String parentName) {
        // 判断技能标签是否覆盖分类
        boolean skillMatched = skillMatched(worker.getSkillTags(), categoryName, parentName);
        // 取当前负载，为空按 0
        int load = worker.getCurrentLoad() == null ? 0 : worker.getCurrentLoad();
        // 取平均分，为空按 0
        BigDecimal avgScore = worker.getAvgScore() == null ? BigDecimal.ZERO : worker.getAvgScore();
        // 按技能 50% + 负载 30% + 评分 20% 计算匹配分
        double matchScore = 0.5D * (skillMatched ? 1D : 0.4D)
                + 0.3D * (1D / (1 + load))
                + 0.2D * (avgScore.doubleValue() / 5D);
        // 拼接推荐理由
        String reason = (skillMatched ? "技能覆盖「" + categoryName + "」" : "技能标签未覆盖，可作备选")
                + "；当前负载 " + load + " 单；平均评分 " + avgScore;
        // 装配推荐项
        return RecommendWorkerResponseVO.builder()
                .workerId(worker.getId())
                .realName(user == null ? null : user.getRealName())
                .skillTags(worker.getSkillTags())
                .currentLoad(load)
                .matchScore(BigDecimal.valueOf(matchScore).setScale(2, RoundingMode.HALF_UP))
                .reason(reason)
                .build();
    }

    /**
     * 判断师傅技能标签是否覆盖工单分类。
     *
     * <p>技能标签以逗号分隔，兼容中文逗号；二级分类或一级分类任一命中即算覆盖。</p>
     *
     * @param skillTags    师傅技能标签串
     * @param categoryName 二级分类名
     * @param parentName   一级分类名
     * @return 是否覆盖
     */
    private boolean skillMatched(String skillTags, String categoryName, String parentName) {
        // 无技能标签视为不覆盖
        if (!StringUtils.hasText(skillTags)) {
            // 返回未覆盖
            return false;
        }
        // 兼容中文逗号后统一为英文逗号
        String tags = skillTags.replace("，", ",");
        // 二级或一级分类任一命中即算覆盖
        return (StringUtils.hasText(categoryName) && tags.contains(categoryName))
                || (StringUtils.hasText(parentName) && tags.contains(parentName));
    }

    /**
     * 查询一级分类名称。
     *
     * @param category 二级分类，可为 null
     * @return 父分类名称；已是根分类或父分类不存在时返回 null
     */
    private String parentCategoryName(RepairCategory category) {
        // 无分类或已是根分类直接返回空
        if (category == null || category.getParentId() == null || category.getParentId() == 0L) {
            // 返回空
            return null;
        }
        // 查询父分类
        RepairCategory parent = repairCategoryMapper.selectById(category.getParentId());
        // 返回父分类名，缺失为空
        return parent == null ? null : parent.getName();
    }

    /**
     * 校验派单目标师傅：必须存在、审核通过且未被禁用。
     *
     * @param workerId 师傅 ID
     * @return 师傅实体
     * @throws BizException 师傅不存在、未审核或已禁用时抛出
     */
    private MaintenanceWorker requireAssignableWorker(Long workerId) {
        // 按 ID 查询师傅档案
        MaintenanceWorker worker = maintenanceWorkerMapper.selectById(workerId);
        // 档案不存在则拒绝
        if (worker == null) {
            // 抛出不存在业务码
            throw BizException.of(ResultCode.NOT_FOUND, "师傅不存在");
        }
        // 未审核通过则拒绝
        if (!Integer.valueOf(AUDIT_PASSED).equals(worker.getAuditStatus())) {
            // 抛出未审核业务码
            throw BizException.of(ResultCode.WORKER_NOT_AUDITED);
        }
        // 已禁用则拒绝派单
        if (Integer.valueOf(WORK_STATUS_DISABLED).equals(worker.getWorkStatus())) {
            // 抛出禁用提示
            throw BizException.of(ResultCode.PARAM_ERROR, "该师傅已被禁用，无法派单");
        }
        // 返回校验通过的师傅
        return worker;
    }

    /**
     * 查询工单，不存在时抛业务异常。
     *
     * @param orderId 工单 ID
     * @return 工单实体
     * @throws BizException 工单不存在（ORDER_NOT_FOUND）时抛出
     */
    private WorkOrder requireOrder(Long orderId) {
        // 按主键读取工单
        WorkOrder order = workOrderMapper.selectById(orderId);
        // 不存在则抛业务异常
        if (order == null) {
            // 抛出工单不存在
            throw BizException.of(ResultCode.ORDER_NOT_FOUND);
        }
        // 返回工单
        return order;
    }

    /**
     * 批量加载用户并转为 ID 到 User 的映射。
     *
     * <p>避免推荐列表中逐条查询用户姓名造成 N+1 查询。</p>
     *
     * @param userIds 用户 ID 列表
     * @return ID 到用户的映射；入参为空时返回空 Map
     */
    private Map<Long, User> loadUsers(List<Long> userIds) {
        // 去重用户 ID
        Set<Long> ids = new HashSet<>(userIds);
        // 空集合直接返回空 Map
        if (ids.isEmpty()) {
            // 返回空 Map
            return Collections.emptyMap();
        }
        // 用户 ID 到用户的映射
        Map<Long, User> users = new HashMap<>(ids.size());
        // 批量查询并写入映射
        for (User user : userMapper.selectBatchIds(ids)) {
            // 记录用户 ID 与对象
            users.put(user.getId(), user);
        }
        // 返回映射
        return users;
    }

    /**
     * 尝试获取分布式锁，失败立即转为业务异常。
     *
     * <p>默认 waitTime=0，即拿不到锁立刻返回冲突，避免请求线程长时间阻塞；
     * leaseTime 用于防止持锁线程异常退出导致锁长期无法释放。</p>
     *
     * @param lock    分布式锁
     * @param message 获取失败时的提示语
     * @throws BizException 获取失败或被中断时抛出
     */
    private void lockOrThrow(RLock lock, String message) {
        // 尝试限时拿锁
        try {
            // 未拿到锁立即冲突返回
            if (!lock.tryLock(lockWaitSeconds, lockLeaseSeconds, TimeUnit.SECONDS)) {
                // 抛出冲突业务码
                throw BizException.of(ResultCode.CONFLICT, message);
            }
        // 中断时恢复中断标志
        } catch (InterruptedException e) {
            // 恢复中断标志
            Thread.currentThread().interrupt();
            // 抛出系统错误
            throw BizException.of(ResultCode.SYSTEM_ERROR, "获取分布式锁被中断");
        }
    }

    /**
     * 释放当前线程持有的锁。
     *
     * <p>先判断持有者再解锁，避免租约过期后锁已被其他线程获取时误释放。</p>
     *
     * @param lock 分布式锁
     */
    private void unlock(RLock lock) {
        // 仅当前线程持有时才释放，避免误释放他人锁
        if (lock.isHeldByCurrentThread()) {
            // 释放锁
            lock.unlock();
        }
    }
}