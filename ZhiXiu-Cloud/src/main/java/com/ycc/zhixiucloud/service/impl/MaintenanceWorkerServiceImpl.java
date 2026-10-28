package com.ycc.zhixiucloud.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ycc.zhixiucloud.common.constant.RedisKeys;
import com.ycc.zhixiucloud.common.enums.AuditStatusEnum;
import com.ycc.zhixiucloud.common.enums.WorkStatusEnum;
import com.ycc.zhixiucloud.common.exception.BizException;
import com.ycc.zhixiucloud.common.result.PageResult;
import com.ycc.zhixiucloud.common.result.ResultCode;
import com.ycc.zhixiucloud.common.util.SecurityUtils;
import com.ycc.zhixiucloud.entity.MaintenanceWorker;
import com.ycc.zhixiucloud.entity.User;
import com.ycc.zhixiucloud.mapper.EvaluationMapper;
import com.ycc.zhixiucloud.mapper.MaintenanceWorkerMapper;
import com.ycc.zhixiucloud.mapper.UserMapper;
import com.ycc.zhixiucloud.mapper.WorkOrderMapper;
import com.ycc.zhixiucloud.request.WorkerAuditRequestVO;
import com.ycc.zhixiucloud.request.WorkerQueryRequestVO;
import com.ycc.zhixiucloud.response.WorkerDetailResponseVO;
import com.ycc.zhixiucloud.response.WorkerListResponseVO;
import com.ycc.zhixiucloud.response.WorkerLoadResponseVO;
import com.ycc.zhixiucloud.response.WorkerStatisticsResponseVO;
import com.ycc.zhixiucloud.service.MaintenanceWorkerService;
import com.ycc.zhixiucloud.service.WorkerPresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 师傅服务实现：审核、启停、负载、个人统计。
 *
 * <p>审核通过后师傅状态置为「离线」，由师傅登录后自行切换上线；
 * 驳回只改审核状态，登录仍被 1005 拦截，保留受理记录便于申诉。</p>
 *
 * <p>关键设计：</p>
 * <ul>
 *   <li>审核与状态变更方法均使用 Spring 事务，保证师傅档案更新与后续操作的一致性；</li>
 *   <li>在线状态采用「数据库为准、Redis 为参考」的双写：work_status 落库后同步维护
 *       worker:online:{workerId} 心跳，Redis 心跳只服务于派单时的在线筛选；</li>
 *   <li>师傅的平均评分在评价产生后通过 {@link #refreshScore(Long)} 回写，并同步到
 *       Redis 月度满意度榜，榜单设置过期时间以便自然滚动。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaintenanceWorkerServiceImpl implements MaintenanceWorkerService {

    /** 月度榜单保留天数 */
    private static final int RANK_TTL_DAYS = 60;

    /** 评分保留位数 */
    private static final int SCORE_SCALE = 1;

    /** 完工率保留位数 */
    private static final int RATE_SCALE = 3;

    /** 关键字查询命中的用户上限，避免 IN 条件过长 */
    private static final int KEYWORD_USER_LIMIT = 200;

    private final MaintenanceWorkerMapper maintenanceWorkerMapper;

    private final UserMapper userMapper;

    private final WorkOrderMapper workOrderMapper;

    private final EvaluationMapper evaluationMapper;

    private final WorkerPresenceService workerPresenceService;

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 分页查询师傅列表，支持按审核状态、工作状态与关键字筛选。
     *
     * <p>关键字同时匹配师傅工号、姓名和手机号：姓名/手机号先查询用户表得到用户 ID，
     * 再以 IN 条件回查师傅表，并限制最多 {@value #KEYWORD_USER_LIMIT} 个用户，
     * 防止拼接过长的 IN 条件。列表中的用户信息通过一次批量查询补齐，避免逐条查询。</p>
     *
     * @param requestVO 分页及筛选条件
     * @return 师傅分页数据，包含状态文本等展示字段
     */
    @Override
    public PageResult<WorkerListResponseVO> page(WorkerQueryRequestVO requestVO) {
        // 按审核/工作状态组装查询，按创建时间倒序
        LambdaQueryWrapper<MaintenanceWorker> wrapper = Wrappers.<MaintenanceWorker>lambdaQuery()
                .eq(requestVO.getAuditStatus() != null, MaintenanceWorker::getAuditStatus,
                        requestVO.getAuditStatus())
                .eq(requestVO.getWorkStatus() != null, MaintenanceWorker::getWorkStatus,
                        requestVO.getWorkStatus())
                .orderByDesc(MaintenanceWorker::getCreateTime);
        // 追加关键字检索条件
        appendKeyword(wrapper, requestVO.getKeyword());

        // 分页查询师傅档案
        Page<MaintenanceWorker> page = maintenanceWorkerMapper.selectPage(
                new Page<>(requestVO.getPageNum(), requestVO.getPageSize()), wrapper);
        // 批量加载本页关联用户
        Map<Long, User> users = loadUsers(page.getRecords());

        // 复用原页码、页大小与总数构造列表分页
        Page<WorkerListResponseVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        // 逐条映射为列表 VO
        voPage.setRecords(page.getRecords().stream().map(worker -> {
            // 取关联用户信息补齐姓名与手机号
            User user = users.get(worker.getUserId());
            // 装配师傅列表出参
            return WorkerListResponseVO.builder()
                    .id(worker.getId())
                    .userId(worker.getUserId())
                    .workerNo(worker.getWorkerNo())
                    .realName(user == null ? null : user.getRealName())
                    .phone(user == null ? null : user.getPhone())
                    .skillTags(worker.getSkillTags())
                    .workStatus(worker.getWorkStatus())
                    .workStatusText(workStatusText(worker.getWorkStatus()))
                    .auditStatus(worker.getAuditStatus())
                    .auditStatusText(auditStatusText(worker.getAuditStatus()))
                    .totalOrders(worker.getTotalOrders())
                    .finishOrders(worker.getFinishOrders())
                    .avgScore(worker.getAvgScore())
                    .currentLoad(worker.getCurrentLoad())
                    .createTime(worker.getCreateTime())
                    .build();
        }).toList());
        // 包装为统一分页结果
        return PageResult.of(voPage);
    }

    /**
     * 查询师傅详情。
     *
     * <p>师傅档案与关联用户账号的姓名、手机号、邮箱合并返回；身份证号输出前
     * 统一脱敏，避免在管理端页面暴露完整证件号码。</p>
     *
     * @param id 师傅记录 ID（maintenance_worker.id，并非用户 ID）
     * @return 师傅详情
     * @throws BizException 师傅记录不存在时抛出
     */
    @Override
    public WorkerDetailResponseVO getDetail(Long id) {
        // 读取师傅档案
        MaintenanceWorker worker = requireWorker(id);
        // 查询关联用户账号补齐姓名与联系方式
        User user = userMapper.selectById(worker.getUserId());
        // 装配详情出参（身份证脱敏后输出）
        return WorkerDetailResponseVO.builder()
                .id(worker.getId())
                .userId(worker.getUserId())
                .username(user == null ? null : user.getUsername())
                .workerNo(worker.getWorkerNo())
                .realName(user == null ? null : user.getRealName())
                .phone(user == null ? null : user.getPhone())
                .email(user == null ? null : user.getEmail())
                .skillTags(worker.getSkillTags())
                .idCard(maskIdCard(worker.getIdCard()))
                .workStatus(worker.getWorkStatus())
                .workStatusText(workStatusText(worker.getWorkStatus()))
                .auditStatus(worker.getAuditStatus())
                .auditStatusText(auditStatusText(worker.getAuditStatus()))
                .auditRemark(worker.getAuditRemark())
                .totalOrders(worker.getTotalOrders())
                .finishOrders(worker.getFinishOrders())
                .avgScore(worker.getAvgScore())
                .currentLoad(worker.getCurrentLoad())
                .createTime(worker.getCreateTime())
                .build();
    }

    /**
     * 审核师傅入驻申请。
     *
     * <p>只更新审核状态与审核备注。审核通过时强制把工作状态重置为「离线」，
     * 师傅需登录后自行上线，避免审批动作直接赋予在线接单资格；驳回时保留
     * 工作状态不变，但登录仍会被未审核校验拦截，受理记录可供重新提交或申诉。</p>
     *
     * @param id        师傅记录 ID
     * @param requestVO 审核结果，auditStatus 取 AuditStatusEnum 的枚举码
     * @throws BizException 师傅记录不存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id, WorkerAuditRequestVO requestVO) {
        // 读取师傅档案
        MaintenanceWorker worker = requireWorker(id);
        // 仅更新审核相关字段的实体
        MaintenanceWorker update = new MaintenanceWorker();
        // 指定师傅 ID
        update.setId(worker.getId());
        // 写入审核结果
        update.setAuditStatus(requestVO.getAuditStatus());
        // 写入审核备注
        update.setAuditRemark(requestVO.getAuditRemark());
        // 审核通过时把工作状态重置为离线
        if (Integer.valueOf(AuditStatusEnum.PASSED.getCode()).equals(requestVO.getAuditStatus())) {
            // 强制置为离线，上线由师傅登录后自行切换
            update.setWorkStatus(WorkStatusEnum.OFFLINE.getCode());
        }
        // 持久化审核结果
        maintenanceWorkerMapper.updateById(update);
        // 记录审核结果
        log.info("师傅审核完成：workerId={}, workerNo={}, auditStatus={}, remark={}",
                worker.getId(), worker.getWorkerNo(), requestVO.getAuditStatus(), requestVO.getAuditRemark());
    }

    /**
     * 管理端调整指定师傅的工作状态，仅允许「在线」和「禁用」两种操作。
     *
     * <p>状态落库后同步维护 Redis 在线心跳：禁用时删除心跳，置为在线时重新写入；
     * 在线状态以数据库 work_status 为准，Redis 心跳仅作为派单参考。禁用后该师傅
     * 再次调用师傅端接口会被 {@link #requireCurrentWorker()} 拦截。</p>
     *
     * @param id         师傅记录 ID
     * @param workStatus 目标状态：1 在线 / 4 禁用
     * @throws BizException 状态取值非法或师傅记录不存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateWorkStatus(Long id, Integer workStatus) {
        // 仅允许在线/禁用两种切换
        if (!isAdminSwitchable(workStatus)) {
            // 抛出参数错误
            throw BizException.of(ResultCode.PARAM_ERROR, "师傅状态取值 1 在线 / 4 禁用");
        }
        // 读取师傅档案
        MaintenanceWorker worker = requireWorker(id);
        // 更新库中工作状态并同步内存实体
        updateWorkStatus(worker, workStatus);
        // 禁用则清理在线心跳
        if (Integer.valueOf(WorkStatusEnum.DISABLED.getCode()).equals(workStatus)) {
            // 删除在线心跳
            workerPresenceService.offline(worker.getId());
        // 在线则重建心跳
        } else {
            // 写入在线心跳
            workerPresenceService.online(worker.getId(), workStatus, worker.getCurrentLoad());
        }
        // 记录状态调整
        log.info("师傅状态调整：workerId={}, workStatus={}", worker.getId(), workStatus);
    }

    /**
     * 查询全部已通过审核师傅的当前负载，按负载从高到低排序。
     *
     * <p>供管理端派单时观察人力分布；负载相同时按 ID 升序，保证排序结果稳定。
     * 用户姓名与手机号通过批量查询补齐，避免 N+1 查询。</p>
     *
     * @return 已审核师傅的负载列表
     */
    @Override
    public List<WorkerLoadResponseVO> listLoad() {
        // 取已审核通过的师傅，按负载倒序、ID 升序保证稳定
        List<MaintenanceWorker> workers = maintenanceWorkerMapper.selectList(
                Wrappers.<MaintenanceWorker>lambdaQuery()
                        .eq(MaintenanceWorker::getAuditStatus, AuditStatusEnum.PASSED.getCode())
                        .orderByDesc(MaintenanceWorker::getCurrentLoad)
                        .orderByAsc(MaintenanceWorker::getId));
        // 批量加载关联用户
        Map<Long, User> users = loadUsers(workers);
        // 逐条映射为负载出参
        return workers.stream().map(worker -> {
            // 取关联用户信息
            User user = users.get(worker.getUserId());
            // 装配负载出参
            return WorkerLoadResponseVO.builder()
                    .workerId(worker.getId())
                    .realName(user == null ? null : user.getRealName())
                    .phone(user == null ? null : user.getPhone())
                    .skillTags(worker.getSkillTags())
                    .workStatus(worker.getWorkStatus())
                    .workStatusText(workStatusText(worker.getWorkStatus()))
                    .currentLoad(worker.getCurrentLoad())
                    .avgScore(worker.getAvgScore())
                    .build();
        }).toList();
    }

    /**
     * 统计指定师傅的接单、完工、平均处理时长与评分等指标。
     *
     * <p>接单/完工数取师傅档案中由工单流程维护的累计字段；完工率 = 完工数 / 接单数，
     * 保留 3 位小数，分母为 0 时直接返回 0，避免除零异常。平均处理时长来自工单表
     * 的聚合查询，暂无已完成工单时按 0 返回；评分为空时同样按 0 处理。</p>
     *
     * @param workerId 师傅记录 ID
     * @return 师傅统计指标
     * @throws BizException 师傅记录不存在时抛出
     */
    @Override
    public WorkerStatisticsResponseVO statistics(Long workerId) {
        // 读取师傅档案
        MaintenanceWorker worker = requireWorker(workerId);
        // 累计接单数，空按 0
        int totalOrders = worker.getTotalOrders() == null ? 0 : worker.getTotalOrders();
        // 完工单数，空按 0
        int finishOrders = worker.getFinishOrders() == null ? 0 : worker.getFinishOrders();
        // 完工率=完工/接单，分母为 0 时按 0
        BigDecimal finishRate = totalOrders == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(finishOrders)
                        .divide(BigDecimal.valueOf(totalOrders), RATE_SCALE, RoundingMode.HALF_UP);
        // 聚合查询平均处理时长
        Long avgHandleMinutes = workOrderMapper.selectAvgHandleMinutes(workerId);
        // 装配统计出参
        return WorkerStatisticsResponseVO.builder()
                .totalOrders(totalOrders)
                .finishOrders(finishOrders)
                .finishRate(finishRate)
                .avgHandleMinutes(avgHandleMinutes == null ? 0L : avgHandleMinutes)
                .avgScore(worker.getAvgScore() == null ? BigDecimal.ZERO : worker.getAvgScore())
                .currentLoad(worker.getCurrentLoad() == null ? 0 : worker.getCurrentLoad())
                .build();
    }

    /**
     * 师傅自助切换在线/离线状态，只能操作自己的档案。
     *
     * <p>只允许 1 在线 / 2 离线；切换成功后同步 Redis 在线心跳（在线写入、离线删除）。
     * 禁用状态只能由管理员调整，师傅无法通过该接口把禁用状态覆盖掉。</p>
     *
     * @param workStatus 目标状态：1 在线 / 2 离线
     * @throws AccessDeniedException 当前账号不是维修师傅
     * @throws BizException          状态非法、未审核通过或账号已禁用
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void switchWorkStatus(Integer workStatus) {
        // 仅允许在线/离线两种切换
        if (workStatus == null || (!Integer.valueOf(WorkStatusEnum.ONLINE.getCode()).equals(workStatus)
                && !Integer.valueOf(WorkStatusEnum.OFFLINE.getCode()).equals(workStatus))) {
            // 抛出参数错误
            throw BizException.of(ResultCode.PARAM_ERROR, "在线状态取值 1 在线 / 2 离线");
        }
        // 取当前登录师傅档案
        MaintenanceWorker worker = requireCurrentWorker();
        // 更新库中工作状态并同步内存实体
        updateWorkStatus(worker, workStatus);
        // 在线则写入心跳
        if (Integer.valueOf(WorkStatusEnum.ONLINE.getCode()).equals(workStatus)) {
            // 写入在线心跳
            workerPresenceService.online(worker.getId(), workStatus, worker.getCurrentLoad());
        // 离线则删除心跳
        } else {
            // 删除在线心跳
            workerPresenceService.offline(worker.getId());
        }
        // 记录状态切换
        log.info("师傅切换在线状态：workerId={}, workStatus={}", worker.getId(), workStatus);
    }

    /**
     * 获取当前登录师傅对应的档案，并校验其可用性。
     *
     * <p>从安全上下文解析师傅 ID 后依次校验：档案存在、审核已通过、账号未被禁用。
     * 师傅端业务接口可统一调用该方法，确保未审核或已禁用的账号无法接单、查看工单。
     * 师傅 ID 与用户 ID 的映射由登录阶段写入安全上下文，此处无需再按用户查询。</p>
     *
     * @return 当前登录师傅的档案
     * @throws AccessDeniedException 当前账号不是维修师傅
     * @throws BizException          审核未通过或账号已被禁用
     */
    @Override
    public MaintenanceWorker requireCurrentWorker() {
        // 从安全上下文取师傅 ID
        Long workerId = SecurityUtils.currentWorkerId();
        // 非师傅账号则拒绝
        if (workerId == null) {
            // 抛出权限异常
            throw new AccessDeniedException("当前账号不是维修师傅");
        }
        // 读取师傅档案
        MaintenanceWorker worker = requireWorker(workerId);
        // 未审核通过则拒绝
        if (!Integer.valueOf(AuditStatusEnum.PASSED.getCode()).equals(worker.getAuditStatus())) {
            // 抛出未审核业务码
            throw BizException.of(ResultCode.WORKER_NOT_AUDITED);
        }
        // 已禁用则拒绝
        if (Integer.valueOf(WorkStatusEnum.DISABLED.getCode()).equals(worker.getWorkStatus())) {
            // 抛出账号禁用提示
            throw BizException.of(ResultCode.ACCOUNT_DISABLED, "师傅账号已被禁用，请联系物业管理员");
        }
        // 返回校验通过的师傅
        return worker;
    }

    /**
     * 重算师傅平均评分并回写数据库与 Redis 月度满意度榜。
     *
     * <p>评分取该师傅全部评价的平均值，保留 1 位小数；没有评价时按 0 处理。
     * 数据库更新与 ZSet 写入放在同一事务方法中，但 Redis 不参与数据库事务回滚，
     * 榜单属于旁路展示数据，允许与库中存在极短时间的不一致。榜单 Key 按月生成，
     * 并设置 {@value #RANK_TTL_DAYS} 天过期，实现历史榜单的自然清理。</p>
     *
     * @param workerId 师傅记录 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refreshScore(Long workerId) {
        // 查询该师傅的评价平均分
        BigDecimal avgScore = evaluationMapper.selectAvgScoreByWorker(workerId);
        // 空按 0，保留 1 位小数
        BigDecimal score = avgScore == null ? BigDecimal.ZERO : avgScore.setScale(SCORE_SCALE, RoundingMode.HALF_UP);
        // 回写数据库平均分
        maintenanceWorkerMapper.update(null, Wrappers.<MaintenanceWorker>lambdaUpdate()
                .eq(MaintenanceWorker::getId, workerId)
                .set(MaintenanceWorker::getAvgScore, score));
        // 取月度满意度榜 key
        String key = RedisKeys.of(RedisKeys.WORKER_SCORE_RANK, RedisKeys.monthSuffix(LocalDate.now()));
        // 写入 ZSet 满意度榜
        stringRedisTemplate.opsForZSet().add(key, String.valueOf(workerId), score.doubleValue());
        // 重置榜单过期时间
        stringRedisTemplate.expire(key, RANK_TTL_DAYS, TimeUnit.DAYS);
        // 记录评分回写结果
        log.info("师傅评分回写：workerId={}, avgScore={}", workerId, score);
    }

    /**
     * 更新数据库中的工作状态，并同步内存中的实体对象，确保调用方随后写心跳时
     * 拿到的是最新状态，而不是更新前的旧值。
     */
    private void updateWorkStatus(MaintenanceWorker worker, Integer workStatus) {
        // 仅更新工作状态的实体
        MaintenanceWorker update = new MaintenanceWorker();
        // 指定师傅 ID
        update.setId(worker.getId());
        // 设置目标状态
        update.setWorkStatus(workStatus);
        // 落库更新
        maintenanceWorkerMapper.updateById(update);
        // 同步内存实体，保证心跳取到最新值
        worker.setWorkStatus(workStatus);
    }

    /** 管理端可切换的状态白名单：只允许上线或禁用，避免管理员代替师傅置为离线 */
    private boolean isAdminSwitchable(Integer workStatus) {
        // 仅在线或禁用算合法
        return Integer.valueOf(WorkStatusEnum.ONLINE.getCode()).equals(workStatus)
                || Integer.valueOf(WorkStatusEnum.DISABLED.getCode()).equals(workStatus);
    }

    /**
     * 追加关键字条件：工号直接匹配师傅表，姓名/手机号先反查用户 ID 再匹配师傅表。
     *
     * <p>用户表结果通过 limit 限制最多 {@value #KEYWORD_USER_LIMIT} 条，控制 IN 条件规模；
     * 用 and(...) 包住 OR 条件，避免与上层审核状态、工作状态条件发生优先级错误。</p>
     */
    private void appendKeyword(LambdaQueryWrapper<MaintenanceWorker> wrapper, String keyword) {
        // 关键字为空直接返回
        if (!StringUtils.hasText(keyword)) {
            return;
        }
        // 先按姓名/手机号反查候选用户 ID（限 200 条）
        List<Long> userIds = userMapper.selectList(Wrappers.<User>lambdaQuery()
                        .like(User::getRealName, keyword)
                        .or()
                        .like(User::getPhone, keyword)
                        .last("limit " + KEYWORD_USER_LIMIT)).stream()
                .map(User::getId)
                .toList();
        // OR 条件包一层 and，避免与上层过滤条件优先级歧义
        wrapper.and(w -> {
            // 拼师傅工号模糊匹配条件
            w.like(MaintenanceWorker::getWorkerNo, keyword);
            // 命中用户时追加用户 ID 过滤
            if (!userIds.isEmpty()) {
                // 命中候选用户时追加 OR 用户 ID 条件
                w.or().in(MaintenanceWorker::getUserId, userIds);
            }
        });
    }

    /** 按 ID 查询师傅档案，不存在时统一抛出业务异常 */
    private MaintenanceWorker requireWorker(Long id) {
        // 按主键读取师傅档案
        MaintenanceWorker worker = maintenanceWorkerMapper.selectById(id);
        // 不存在则抛业务异常
        if (worker == null) {
            // 抛出不存在业务码
            throw BizException.of(ResultCode.NOT_FOUND, "师傅不存在");
        }
        // 返回师傅档案
        return worker;
    }

    /**
     * 按用户 ID 批量加载用户信息并组装为 Map，供列表转换时 O(1) 取用。
     * 一次 selectBatchIds 代替逐条查询，避免分页列表出现 N+1 查询。
     */
    private Map<Long, User> loadUsers(List<MaintenanceWorker> workers) {
        // 收集非空用户 ID
        List<Long> userIds = workers.stream().map(MaintenanceWorker::getUserId).filter(Objects::nonNull).toList();
        // 空集合直接返回空 Map
        if (userIds.isEmpty()) {
            // 返回空 Map
            return Collections.emptyMap();
        }
        // 用户 ID 到用户对象的映射
        Map<Long, User> users = new HashMap<>(userIds.size());
        // 批量查询并写入映射
        for (User user : userMapper.selectBatchIds(userIds)) {
            // 记录用户 ID 与对象
            users.put(user.getId(), user);
        }
        // 返回映射
        return users;
    }

    private String workStatusText(Integer workStatus) {
        // 解析工作状态枚举
        WorkStatusEnum status = WorkStatusEnum.of(workStatus);
        // 无效值返回 null，否则返回中文名
        return status == null ? null : status.getName();
    }

    private String auditStatusText(Integer auditStatus) {
        // 解析审核状态枚举
        AuditStatusEnum status = AuditStatusEnum.of(auditStatus);
        // 无效值返回 null，否则返回中文名
        return status == null ? null : status.getName();
    }

    /** 身份证脱敏：仅保留前 6 位与后 4 位 */
    private String maskIdCard(String idCard) {
        // 空值或过短原样返回
        if (!StringUtils.hasText(idCard) || idCard.length() < 11) {
            // 原样返回
            return idCard;
        }
        // 保留前 6 位与后 4 位，中间以星号脱敏
        return idCard.substring(0, 6) + "********" + idCard.substring(idCard.length() - 4);
    }
}